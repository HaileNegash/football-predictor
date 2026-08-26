package com.example.keymanager

import android.content.Context
import android.util.Log
import com.example.firebase.FirebaseDatabaseProvider
import com.google.firebase.firestore.DocumentSnapshot
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
        FirebaseDatabaseProvider.getFirestore(context, useVaultDb = true)
    }

    val isFirebaseAvailable: Boolean
        get() = firestore != null

    private fun parseDocument(doc: DocumentSnapshot): ManagedApiKey? {
        val id = doc.getString("id") ?: doc.id
        val roleStr = doc.getString("role") ?: "API_FOOTBALL"
        val key = doc.getString("key") ?: doc.getString("apiKey") ?: doc.getString("token") ?: return null
        if (key.isBlank()) return null

        val label = doc.getString("label") ?: doc.getString("name") ?: "Cloud Key"
        val status = doc.getString("status") ?: KeyStatus.ACTIVE.name
        val usageCount = doc.getLong("usageCount")?.toInt() ?: 0
        val errorCount = doc.getLong("errorCount")?.toInt() ?: 0
        val lastUsedTimestamp = doc.getLong("lastUsedTimestamp") ?: 0L
        val rateLimitedUntil = doc.getLong("rateLimitedUntil") ?: 0L
        val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
        
        // Parse models array if present (e.g. agnes, claude, deepseek)
        val availableModels: List<String> = try {
            (doc.get("availableModels") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val lastTestMessage = doc.getString("lastTestMessage")
        val lastTestStatus = doc.getString("lastTestStatus")
        val lastTestedAt = doc.getLong("lastTestedAt") ?: 0L

        val endpointUrl = doc.getString("endpointUrl") ?: when {
            roleStr.contains("FIRECRAWL", ignoreCase = true) -> "https://api.firecrawl.dev/v1/search"
            roleStr.contains("GEMINI", ignoreCase = true) -> "https://generativelanguage.googleapis.com/v1beta/"
            availableModels.any { it.contains("claude", ignoreCase = true) || it.contains("agnes", ignoreCase = true) || it.contains("deepseek", ignoreCase = true) } -> "https://openrouter.ai/api/v1/"
            else -> "https://api.openai.com/v1/"
        }

        val modelName = doc.getString("modelName") ?: if (availableModels.isNotEmpty()) {
            availableModels.first()
        } else {
            "gpt-4o-mini"
        }

        return ManagedApiKey(
            id = id,
            role = roleStr,
            key = key,
            label = label,
            status = status,
            usageCount = usageCount,
            errorCount = errorCount,
            lastUsedTimestamp = lastUsedTimestamp,
            rateLimitedUntil = rateLimitedUntil,
            createdAt = createdAt,
            endpointUrl = endpointUrl,
            modelName = modelName,
            lastTestMessage = lastTestMessage,
            lastTestStatus = lastTestStatus,
            lastTestedAt = lastTestedAt,
            availableModels = availableModels
        )
    }

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
                "lastTestMessage" to (apiKey.lastTestMessage ?: "Key stored in Firestore"),
                "lastTestStatus" to (apiKey.lastTestStatus ?: "ACTIVE"),
                "lastTestedAt" to if (apiKey.lastTestedAt > 0) apiKey.lastTestedAt else System.currentTimeMillis(),
                "availableModels" to apiKey.availableModels,
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
            val keys = snapshot.documents.mapNotNull { doc -> parseDocument(doc) }
            Log.i(tag, "Fetched ${keys.size} keys from Cloud Firestore collection '$collectionName'")
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
                    val keys = snapshot.documents.mapNotNull { doc -> parseDocument(doc) }
                    Log.i(tag, "Live Firestore snapshot: received ${keys.size} keys")
                    trySend(keys)
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }
}
