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
    val namedDatabaseId = "ai-studio-aibrainkeyvault-0cc24613-6377-406e-89e2-f84faa5463fa"

    /**
     * Returns all available Firestore instances (both the named AI Studio database and default database)
     * so that data is guaranteed to be saved and visible in the Firebase console.
     */
    private fun getFirestoreInstances(): List<FirebaseFirestore> {
        val instances = mutableListOf<FirebaseFirestore>()
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                return emptyList()
            }
            val app = FirebaseApp.getInstance()

            // 1. Specific Named Database created for AI Studio Key Vault
            try {
                val namedDb = FirebaseFirestore.getInstance(app, namedDatabaseId)
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(true)
                        .build()
                    namedDb.firestoreSettings = settings
                } catch (_: Exception) {}
                instances.add(namedDb)
            } catch (e: Exception) {
                Log.w(tag, "Named Firestore instance not initialized: ${e.message}")
            }

            // 2. Default Database
            try {
                val defaultDb = FirebaseFirestore.getInstance(app)
                try {
                    val settings = FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(true)
                        .build()
                    defaultDb.firestoreSettings = settings
                } catch (_: Exception) {}
                if (!instances.contains(defaultDb)) {
                    instances.add(defaultDb)
                }
            } catch (e: Exception) {
                Log.w(tag, "Default Firestore instance not initialized: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to resolve Firestore instances: ${e.message}")
        }
        return instances
    }

    private val firestore: FirebaseFirestore?
        get() = getFirestoreInstances().firstOrNull()

    private fun parseDocToKey(doc: com.google.firebase.firestore.DocumentSnapshot): ManagedApiKey? {
        val data = doc.data ?: return null
        val keyStr = (data["key"] as? String)?.trim() ?: return null
        if (keyStr.isBlank()) return null
        val role = data["role"] as? String ?: ApiRole.API_FOOTBALL.code
        val isOpenAi = role == ApiRole.OPENAI_COMPATIBLE.code
        val modelsList = (data["availableModels"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        return ManagedApiKey(
            id = data["id"] as? String ?: doc.id,
            role = role,
            key = keyStr,
            label = data["label"] as? String ?: "Key",
            status = data["status"] as? String ?: KeyStatus.ACTIVE.name,
            usageCount = (data["usageCount"] as? Number)?.toInt() ?: 0,
            errorCount = (data["errorCount"] as? Number)?.toInt() ?: 0,
            lastUsedTimestamp = (data["lastUsedTimestamp"] as? Number)?.toLong() ?: 0L,
            rateLimitedUntil = (data["rateLimitedUntil"] as? Number)?.toLong() ?: 0L,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            endpointUrl = if (isOpenAi) (data["endpointUrl"] as? String ?: "https://api.openai.com/v1/") else "",
            modelName = if (isOpenAi) (data["modelName"] as? String ?: "gpt-4o-mini") else "",
            lastTestMessage = (data["lastTestMessage"] as? String)?.takeIf { it.isNotBlank() },
            lastTestStatus = (data["lastTestStatus"] as? String)?.takeIf { it.isNotBlank() },
            lastTestedAt = (data["lastTestedAt"] as? Number)?.toLong() ?: 0L,
            availableModels = if (isOpenAi) modelsList else emptyList()
        )
    }

    private fun buildKeyDocumentMap(apiKey: ManagedApiKey): HashMap<String, Any?> {
        val isOpenAi = apiKey.role == ApiRole.OPENAI_COMPATIBLE.code
        val map = hashMapOf<String, Any?>(
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
            "lastTestMessage" to (apiKey.lastTestMessage ?: ""),
            "lastTestStatus" to (apiKey.lastTestStatus ?: ""),
            "lastTestedAt" to apiKey.lastTestedAt,
            "updatedAt" to System.currentTimeMillis()
        )
        if (isOpenAi) {
            map["endpointUrl"] = apiKey.endpointUrl.ifBlank { "https://api.openai.com/v1/" }
            map["modelName"] = apiKey.modelName.ifBlank { "gpt-4o-mini" }
            map["availableModels"] = apiKey.availableModels
        }
        return map
    }

    /**
     * Uploads or updates a single API key to Firestore across all available paths (user path and global vault).
     */
    suspend fun saveKeyToCloud(userId: String, apiKey: ManagedApiKey): Boolean = withContext(Dispatchers.IO) {
        val instances = getFirestoreInstances()
        if (instances.isEmpty()) return@withContext false
        val cleanUserId = sanitizeUserId(userId)
        var anySuccess = false

        val keyDoc = buildKeyDocumentMap(apiKey)

        for (db in instances) {
            try {
                withTimeoutOrNull(6000L) {
                    // 1. Save to current user's api_keys
                    db.collection("users")
                        .document(cleanUserId)
                        .collection("api_keys")
                        .document(apiKey.id)
                        .set(keyDoc, SetOptions.merge())
                        .await()

                    // 2. Also save to global_api_keys for instant recovery
                    try {
                        db.collection("global_api_keys")
                            .document(apiKey.id)
                            .set(keyDoc, SetOptions.merge())
                            .await()
                    } catch (_: Exception) {}

                    anySuccess = true
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed saving to a Firestore instance: ${e.message}")
            }
        }
        anySuccess
    }

    /**
     * Uploads a batch of API keys to Firestore.
     */
    suspend fun syncAllKeysToCloud(userId: String, keys: List<ManagedApiKey>): Boolean = withContext(Dispatchers.IO) {
        val instances = getFirestoreInstances()
        if (instances.isEmpty()) return@withContext false
        if (keys.isEmpty()) return@withContext true
        val cleanUserId = sanitizeUserId(userId)
        var anySuccess = false

        for (db in instances) {
            try {
                withTimeoutOrNull(8000L) {
                    val batch = db.batch()
                    keys.forEach { apiKey ->
                        val userDocRef = db.collection("users")
                            .document(cleanUserId)
                            .collection("api_keys")
                            .document(apiKey.id)

                        val globalDocRef = db.collection("global_api_keys")
                            .document(apiKey.id)

                        val keyDoc = buildKeyDocumentMap(apiKey)
                        batch.set(userDocRef, keyDoc, SetOptions.merge())
                        batch.set(globalDocRef, keyDoc, SetOptions.merge())
                    }
                    batch.commit().await()
                    anySuccess = true
                }
            } catch (e: Exception) {
                Log.w(tag, "Batch sync failed on instance: ${e.message}")
            }
        }
        anySuccess
    }

    /**
     * Fetches all API keys stored in Firestore, scanning collectionGroup("api_keys"), all users, and global vault.
     */
    suspend fun fetchKeysFromCloud(userId: String): List<ManagedApiKey>? = withContext(Dispatchers.IO) {
        val instances = getFirestoreInstances()
        if (instances.isEmpty()) return@withContext null
        val cleanUserId = sanitizeUserId(userId)
        val collectedKeysMap = mutableMapOf<String, ManagedApiKey>()

        for (db in instances) {
            try {
                withTimeoutOrNull(7000L) {
                    // Method 1: collectionGroup("api_keys") - gets all keys across ALL user documents
                    try {
                        val groupSnapshot = db.collectionGroup("api_keys").get().await()
                        for (doc in groupSnapshot.documents) {
                            val parsed = parseDocToKey(doc) ?: continue
                            val existing = collectedKeysMap[parsed.id] ?: collectedKeysMap.values.find { it.key == parsed.key }
                            if (existing == null || parsed.lastTestedAt >= existing.lastTestedAt) {
                                collectedKeysMap[parsed.id] = parsed
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(tag, "collectionGroup search skipped/failed: ${e.message}")
                    }

                    // Method 2: Global API keys collection
                    try {
                        val globalSnapshot = db.collection("global_api_keys").get().await()
                        for (doc in globalSnapshot.documents) {
                            val parsed = parseDocToKey(doc) ?: continue
                            val existing = collectedKeysMap[parsed.id] ?: collectedKeysMap.values.find { it.key == parsed.key }
                            if (existing == null || parsed.lastTestedAt >= existing.lastTestedAt) {
                                collectedKeysMap[parsed.id] = parsed
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(tag, "global_api_keys search skipped: ${e.message}")
                    }

                    // Method 3: Direct User Path
                    try {
                        val userSnapshot = db.collection("users")
                            .document(cleanUserId)
                            .collection("api_keys")
                            .get()
                            .await()
                        for (doc in userSnapshot.documents) {
                            val parsed = parseDocToKey(doc) ?: continue
                            val existing = collectedKeysMap[parsed.id] ?: collectedKeysMap.values.find { it.key == parsed.key }
                            if (existing == null || parsed.lastTestedAt >= existing.lastTestedAt) {
                                collectedKeysMap[parsed.id] = parsed
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(tag, "Direct user path search skipped: ${e.message}")
                    }

                    // Method 4: Scan all documents under users (e.g. user_3a6e031ae6, user_18256c8e6b, guest_default)
                    try {
                        val allUsers = db.collection("users").get().await()
                        for (uDoc in allUsers.documents) {
                            try {
                                val keysInUser = uDoc.reference.collection("api_keys").get().await()
                                for (doc in keysInUser.documents) {
                                    val parsed = parseDocToKey(doc) ?: continue
                                    val existing = collectedKeysMap[parsed.id] ?: collectedKeysMap.values.find { it.key == parsed.key }
                                    if (existing == null || parsed.lastTestedAt >= existing.lastTestedAt) {
                                        collectedKeysMap[parsed.id] = parsed
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed fetching from instance: ${e.message}")
            }
        }

        if (collectedKeysMap.isNotEmpty()) {
            return@withContext collectedKeysMap.values.toList()
        }
        emptyList()
    }

    /**
     * Deletes an API key document from Firestore across all instances.
     */
    suspend fun deleteKeyFromCloud(userId: String, keyId: String): Boolean = withContext(Dispatchers.IO) {
        val instances = getFirestoreInstances()
        if (instances.isEmpty()) return@withContext false
        val cleanUserId = sanitizeUserId(userId)
        var anySuccess = false

        for (db in instances) {
            try {
                withTimeoutOrNull(5000L) {
                    db.collection("users")
                        .document(cleanUserId)
                        .collection("api_keys")
                        .document(keyId)
                        .delete()
                        .await()
                    anySuccess = true
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed deleting from instance: ${e.message}")
            }
        }
        anySuccess
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
                            val parsed = parseDocToKey(doc) ?: continue
                            resultList.add(parsed)
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
