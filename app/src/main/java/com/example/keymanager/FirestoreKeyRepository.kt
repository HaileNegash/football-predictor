package com.example.keymanager

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class FirestoreKeyRepository(
    private val context: Context
) {
    private val tag = "FirestoreKeyRepo"

    private val firestore: FirebaseFirestore?
        get() = try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                val db = FirebaseFirestore.getInstance()
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(true)
                        .build()
                    db.firestoreSettings = settings
                } catch (_: Exception) {}
                db
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(tag, "Firestore not available: ${e.message}")
            null
        }

    /**
     * Uploads or updates a single API key to Firestore for a specific user.
     */
    suspend fun saveKeyToCloud(userId: String, apiKey: ManagedApiKey): Boolean = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext false
        val cleanUserId = sanitizeUserId(userId)
        try {
            withTimeoutOrNull(5000L) {
                val keyDoc = hashMapOf(
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
                    "lastTestMessage" to (apiKey.lastTestMessage ?: ""),
                    "lastTestStatus" to (apiKey.lastTestStatus ?: ""),
                    "lastTestedAt" to apiKey.lastTestedAt,
                    "availableModels" to apiKey.availableModels,
                    "updatedAt" to System.currentTimeMillis()
                )

                db.collection("users")
                    .document(cleanUserId)
                    .collection("api_keys")
                    .document(apiKey.id)
                    .set(keyDoc, SetOptions.merge())
                    .await()
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(tag, "Failed to save key to Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Uploads a batch of API keys to Firestore.
     */
    suspend fun syncAllKeysToCloud(userId: String, keys: List<ManagedApiKey>): Boolean = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext false
        if (keys.isEmpty()) return@withContext true
        val cleanUserId = sanitizeUserId(userId)
        try {
            withTimeoutOrNull(6000L) {
                val batch = db.batch()
                keys.forEach { apiKey ->
                    val docRef = db.collection("users")
                        .document(cleanUserId)
                        .collection("api_keys")
                        .document(apiKey.id)

                    val keyDoc = hashMapOf(
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
                        "lastTestMessage" to (apiKey.lastTestMessage ?: ""),
                        "lastTestStatus" to (apiKey.lastTestStatus ?: ""),
                        "lastTestedAt" to apiKey.lastTestedAt,
                        "availableModels" to apiKey.availableModels,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    batch.set(docRef, keyDoc, SetOptions.merge())
                }
                batch.commit().await()
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(tag, "Batch sync failed: ${e.message}", e)
            false
        }
    }

    /**
     * Fetches all API keys stored in Firestore for a user.
     */
    suspend fun fetchKeysFromCloud(userId: String): List<ManagedApiKey>? = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext null
        val cleanUserId = sanitizeUserId(userId)
        try {
            withTimeoutOrNull(5000L) {
                val snapshot = db.collection("users")
                    .document(cleanUserId)
                    .collection("api_keys")
                    .get()
                    .await()

                val resultList = mutableListOf<ManagedApiKey>()
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val modelsList = (data["availableModels"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                    val keyItem = ManagedApiKey(
                        id = data["id"] as? String ?: doc.id,
                        role = data["role"] as? String ?: ApiRole.API_FOOTBALL.code,
                        key = data["key"] as? String ?: "",
                        label = data["label"] as? String ?: "Key",
                        status = data["status"] as? String ?: KeyStatus.ACTIVE.name,
                        usageCount = (data["usageCount"] as? Number)?.toInt() ?: 0,
                        errorCount = (data["errorCount"] as? Number)?.toInt() ?: 0,
                        lastUsedTimestamp = (data["lastUsedTimestamp"] as? Number)?.toLong() ?: 0L,
                        rateLimitedUntil = (data["rateLimitedUntil"] as? Number)?.toLong() ?: 0L,
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        endpointUrl = data["endpointUrl"] as? String ?: "https://api.openai.com/v1/",
                        modelName = data["modelName"] as? String ?: "gpt-4o-mini",
                        lastTestMessage = (data["lastTestMessage"] as? String)?.takeIf { it.isNotBlank() },
                        lastTestStatus = (data["lastTestStatus"] as? String)?.takeIf { it.isNotBlank() },
                        lastTestedAt = (data["lastTestedAt"] as? Number)?.toLong() ?: 0L,
                        availableModels = modelsList
                    )
                    resultList.add(keyItem)
                }
                resultList
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch keys from Firestore: ${e.message}", e)
            null
        }
    }

    /**
     * Deletes an API key document from Firestore.
     */
    suspend fun deleteKeyFromCloud(userId: String, keyId: String): Boolean = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext false
        val cleanUserId = sanitizeUserId(userId)
        try {
            withTimeoutOrNull(5000L) {
                db.collection("users")
                    .document(cleanUserId)
                    .collection("api_keys")
                    .document(keyId)
                    .delete()
                    .await()
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete key from Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * Listens to real-time updates for keys in Firestore.
     */
    fun listenToCloudKeys(
        userId: String,
        onKeysUpdated: (List<ManagedApiKey>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? {
        val db = firestore ?: return null
        val cleanUserId = sanitizeUserId(userId)
        return try {
            db.collection("users")
                .document(cleanUserId)
                .collection("api_keys")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(tag, "Snapshot listener warning: ${error.message}")
                        onError(error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val resultList = mutableListOf<ManagedApiKey>()
                        for (doc in snapshot.documents) {
                            val data = doc.data ?: continue
                            val modelsList = (data["availableModels"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                            val keyItem = ManagedApiKey(
                                id = data["id"] as? String ?: doc.id,
                                role = data["role"] as? String ?: ApiRole.API_FOOTBALL.code,
                                key = data["key"] as? String ?: "",
                                label = data["label"] as? String ?: "Key",
                                status = data["status"] as? String ?: KeyStatus.ACTIVE.name,
                                usageCount = (data["usageCount"] as? Number)?.toInt() ?: 0,
                                errorCount = (data["errorCount"] as? Number)?.toInt() ?: 0,
                                lastUsedTimestamp = (data["lastUsedTimestamp"] as? Number)?.toLong() ?: 0L,
                                rateLimitedUntil = (data["rateLimitedUntil"] as? Number)?.toLong() ?: 0L,
                                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                endpointUrl = data["endpointUrl"] as? String ?: "https://api.openai.com/v1/",
                                modelName = data["modelName"] as? String ?: "gpt-4o-mini",
                                lastTestMessage = (data["lastTestMessage"] as? String)?.takeIf { it.isNotBlank() },
                                lastTestStatus = (data["lastTestStatus"] as? String)?.takeIf { it.isNotBlank() },
                                lastTestedAt = (data["lastTestedAt"] as? Number)?.toLong() ?: 0L,
                                availableModels = modelsList
                            )
                            resultList.add(keyItem)
                        }
                        onKeysUpdated(resultList)
                    }
                }
        } catch (e: Exception) {
            Log.e(tag, "Failed to attach real-time listener: ${e.message}", e)
            null
        }
    }

    private fun sanitizeUserId(userId: String): String {
        return userId.ifBlank { "guest_default" }.replace("/", "_").replace(".", "_")
    }
}
