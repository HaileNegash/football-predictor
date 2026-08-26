package com.example.keymanager

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseKeyService(private val context: Context) {
    private val tag = "FirebaseKeyService"
    private val collectionName = "api_keys"

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.w(tag, "Firebase Firestore initialization not active (${e.message ?: "No Google Services config"}). Vault is operating in local secure mode.")
            null
        }
    }

    val isFirebaseAvailable: Boolean
        get() = firestore != null

    suspend fun saveKey(apiKey: ManagedApiKey): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firebase Firestore is not initialized on this device"))
        return try {
            val keyMap = hashMapOf(
                "id" to apiKey.id,
                "role" to apiKey.role,
                "key" to apiKey.key,
                "label" to apiKey.label,
                "status" to apiKey.status,
                "usageCount" to apiKey.usageCount,
                "errorCount" to apiKey.errorCount,
                "lastUsedTimestamp" to apiKey.lastUsedTimestamp,
                "rateLimitedUntil" to apiKey.rateLimitedUntil,
                "createdAt" to apiKey.createdAt,
                "endpointUrl" to apiKey.endpointUrl,
                "modelName" to apiKey.modelName,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(collectionName).document(apiKey.id).set(keyMap, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to save key ${apiKey.id} to Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun deleteKey(keyId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firebase Firestore is not initialized"))
        return try {
            db.collection(collectionName).document(keyId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete key $keyId from Firestore", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAllKeys(): Result<List<ManagedApiKey>> {
        val db = firestore ?: return Result.failure(Exception("Firebase Firestore is not initialized"))
        return try {
            val snapshot = db.collection(collectionName).get().await()
            val keys = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val role = doc.getString("role") ?: ApiRole.API_FOOTBALL.code
                val key = doc.getString("key") ?: return@mapNotNull null
                val label = doc.getString("label") ?: "Key"
                val status = doc.getString("status") ?: KeyStatus.ACTIVE.name
                val usageCount = doc.getLong("usageCount")?.toInt() ?: 0
                val errorCount = doc.getLong("errorCount")?.toInt() ?: 0
                val lastUsedTimestamp = doc.getLong("lastUsedTimestamp") ?: 0L
                val rateLimitedUntil = doc.getLong("rateLimitedUntil") ?: 0L
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                val endpointUrl = doc.getString("endpointUrl") ?: "https://api.openai.com/v1/"
                val modelName = doc.getString("modelName") ?: "gpt-4o-mini"

                ManagedApiKey(
                    id = id,
                    role = role,
                    key = key,
                    label = label,
                    status = status,
                    usageCount = usageCount,
                    errorCount = errorCount,
                    lastUsedTimestamp = lastUsedTimestamp,
                    rateLimitedUntil = rateLimitedUntil,
                    createdAt = createdAt,
                    endpointUrl = endpointUrl,
                    modelName = modelName
                )
            }
            Result.success(keys)
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch keys from Firestore", e)
            Result.failure(e)
        }
    }

    fun observeKeys(): Flow<List<ManagedApiKey>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listenerRegistration = db.collection(collectionName)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Listen failed for api_keys: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val keys = snapshot.documents.mapNotNull { doc ->
                        val id = doc.getString("id") ?: doc.id
                        val role = doc.getString("role") ?: ApiRole.API_FOOTBALL.code
                        val key = doc.getString("key") ?: return@mapNotNull null
                        val label = doc.getString("label") ?: "Key"
                        val status = doc.getString("status") ?: KeyStatus.ACTIVE.name
                        val usageCount = doc.getLong("usageCount")?.toInt() ?: 0
                        val errorCount = doc.getLong("errorCount")?.toInt() ?: 0
                        val lastUsedTimestamp = doc.getLong("lastUsedTimestamp") ?: 0L
                        val rateLimitedUntil = doc.getLong("rateLimitedUntil") ?: 0L
                        val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        val endpointUrl = doc.getString("endpointUrl") ?: "https://api.openai.com/v1/"
                        val modelName = doc.getString("modelName") ?: "gpt-4o-mini"

                        ManagedApiKey(
                            id = id,
                            role = role,
                            key = key,
                            label = label,
                            status = status,
                            usageCount = usageCount,
                            errorCount = errorCount,
                            lastUsedTimestamp = lastUsedTimestamp,
                            rateLimitedUntil = rateLimitedUntil,
                            createdAt = createdAt,
                            endpointUrl = endpointUrl,
                            modelName = modelName
                        )
                    }
                    trySend(keys)
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }
}
