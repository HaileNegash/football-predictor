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
    private val configCollectionName = "app_config"

    private val firestore: FirebaseFirestore? by lazy {
        FirebaseDatabaseProvider.getFirestore(context, useVaultDb = true)
    }

    val isFirebaseAvailable: Boolean
        get() = firestore != null

    // Cache latest dashboard global config
    @Volatile var activeBrainModel: String? = "qwen-3.8-max-free"
        private set
    @Volatile var activeBrainProviderId: String? = null
        private set

    private fun parseDocument(
        doc: DocumentSnapshot,
        globalActiveModel: String? = null,
        targetActiveProviderId: String? = null
    ): ManagedApiKey? {
        val id = doc.getString("id") ?: doc.id
        val key = doc.getString("key") ?: doc.getString("apiKey") ?: doc.getString("token") ?: doc.getString("bearerToken") ?: return null
        if (key.isBlank()) return null

        val isTargetProvider = (targetActiveProviderId != null && (id == targetActiveProviderId || doc.id == targetActiveProviderId))
        val roleStr = if (isTargetProvider) {
            "OPENAI_COMPATIBLE"
        } else {
            doc.getString("role") ?: "OPENAI_COMPATIBLE"
        }

        val label = doc.getString("label") ?: doc.getString("name") ?: doc.getString("providerName") ?: "Cloud Key"
        val status = if (isTargetProvider) KeyStatus.ACTIVE.name else (doc.getString("status") ?: KeyStatus.ACTIVE.name)
        val usageCount = doc.getLong("usageCount")?.toInt() ?: 0
        val errorCount = doc.getLong("errorCount")?.toInt() ?: 0
        val lastUsedTimestamp = doc.getLong("lastUsedTimestamp") ?: 0L
        val rateLimitedUntil = doc.getLong("rateLimitedUntil") ?: 0L
        val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
        
        // Parse models array if present (e.g. 55 models catalog)
        val availableModels: List<String> = try {
            val raw = doc.get("availableModels") ?: doc.get("models")
            (raw as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val lastTestMessage = doc.getString("lastTestMessage") ?: doc.getString("statusMessage")
        val lastTestStatus = doc.getString("lastTestStatus")
        val lastTestedAt = doc.getLong("lastTestedAt") ?: 0L

        val rawEndpoint = doc.getString("endpointUrl")
            ?: doc.getString("baseUrl")
            ?: doc.getString("base_url")
            ?: doc.getString("url")
            ?: doc.getString("apiUrl")

        val endpointUrl = when {
            !rawEndpoint.isNullOrBlank() -> rawEndpoint
            roleStr.contains("FOOTBALL_DATA", ignoreCase = true) || roleStr.contains("FOOTBALL-DATA", ignoreCase = true) -> "https://api.football-data.org/v4/"
            roleStr.contains("THE_ODDS", ignoreCase = true) || roleStr.contains("ODDS", ignoreCase = true) -> "https://api.the-odds-api.com/v4/"
            roleStr.contains("SPORTMONK", ignoreCase = true) -> "https://api.sportmonks.com/v3/football/"
            roleStr.contains("SPORTSDB", ignoreCase = true) -> "https://www.thesportsdb.com/api/v1/json/"
            roleStr.contains("API_FOOTBALL", ignoreCase = true) || roleStr.contains("API-SPORTS", ignoreCase = true) -> "https://v3.football.api-sports.io/"
            roleStr.contains("FIRECRAWL", ignoreCase = true) -> "https://api.firecrawl.dev/v1/search"
            roleStr.contains("GEMINI", ignoreCase = true) -> "https://generativelanguage.googleapis.com/v1beta/"
            label.contains("nara", ignoreCase = true) || id.contains("nara", ignoreCase = true) || id.contains("ovyn8", ignoreCase = true) -> "https://router.bynara.id/v1"
            availableModels.any { it.contains("claude", ignoreCase = true) || it.contains("agnes", ignoreCase = true) || it.contains("deepseek", ignoreCase = true) || it.contains("qwen", ignoreCase = true) } -> "https://router.bynara.id/v1"
            else -> "https://router.bynara.id/v1"
        }

        val docModel = doc.getString("modelName")
            ?: doc.getString("activeModel")
            ?: doc.getString("selectedModel")
            ?: doc.getString("model")
            ?: doc.getString("defaultModel")

        val modelName = when {
            !globalActiveModel.isNullOrBlank() && (isTargetProvider || docModel.isNullOrBlank()) -> globalActiveModel
            !docModel.isNullOrBlank() -> docModel
            !globalActiveModel.isNullOrBlank() -> globalActiveModel
            availableModels.any { it.contains("qwen-3.8-max-free", ignoreCase = true) } -> "qwen-3.8-max-free"
            availableModels.isNotEmpty() -> availableModels.first()
            else -> "qwen-3.8-max-free"
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
                "baseUrl" to apiKey.endpointUrl,
                "modelName" to apiKey.modelName,
                "activeModel" to apiKey.modelName,
                "activeBrainModel" to apiKey.modelName,
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
            // Read global app_config settings
            try {
                val configSnapshot = db.collection(configCollectionName).get().await()
                for (configDoc in configSnapshot.documents) {
                    val candidateModel = configDoc.getString("activeBrainModel")
                        ?: configDoc.getString("activeModel")
                        ?: configDoc.getString("selectedModel")
                        ?: configDoc.getString("model")
                    if (!candidateModel.isNullOrBlank()) {
                        activeBrainModel = candidateModel
                    }

                    val candidateProvider = configDoc.getString("activeBrainProviderId")
                        ?: configDoc.getString("activeProviderId")
                        ?: configDoc.getString("activeKeyId")
                    if (!candidateProvider.isNullOrBlank()) {
                        activeBrainProviderId = candidateProvider
                    }
                }
            } catch (e: Exception) {
                Log.d(tag, "Optional app_config read: ${e.message}")
            }

            val snapshot = db.collection(collectionName).get().await()
            val keys = snapshot.documents.mapNotNull { doc ->
                parseDocument(doc, activeBrainModel, activeBrainProviderId)
            }
            Log.i(tag, "Fetched ${keys.size} keys from Cloud Firestore. Active Model: $activeBrainModel, Provider: $activeBrainProviderId")
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

        // 1. Observe app_config for real-time dashboard model/provider changes
        val configListener = db.collection(configCollectionName)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Listen failed for app_config: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        val m = doc.getString("activeBrainModel")
                            ?: doc.getString("activeModel")
                            ?: doc.getString("selectedModel")
                        if (!m.isNullOrBlank()) activeBrainModel = m

                        val p = doc.getString("activeBrainProviderId")
                            ?: doc.getString("activeProviderId")
                            ?: doc.getString("activeKeyId")
                        if (!p.isNullOrBlank()) activeBrainProviderId = p
                    }
                    Log.i(tag, "Live app_config updated: activeBrainModel=$activeBrainModel, provider=$activeBrainProviderId")
                }
            }

        // 2. Observe api_keys collection
        val keysListener = db.collection(collectionName)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Listen failed for api_keys: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val keys = snapshot.documents.mapNotNull { doc ->
                        parseDocument(doc, activeBrainModel, activeBrainProviderId)
                    }
                    Log.i(tag, "Live Firestore snapshot: received ${keys.size} keys (Active Model: $activeBrainModel)")
                    trySend(keys)
                }
            }

        awaitClose {
            configListener.remove()
            keysListener.remove()
        }
    }
}
