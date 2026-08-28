package com.example.keymanager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.firestore.ListenerRegistration
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class KeyRotationManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val tag = "KeyRotationManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("api_key_vault_local", Context.MODE_PRIVATE)
    val firestoreRepository: FirestoreKeyRepository = FirestoreKeyRepository(context)

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val listType = Types.newParameterizedType(List::class.java, ManagedApiKey::class.java)
    private val adapter = moshi.adapter<List<ManagedApiKey>>(listType)

    private val httpClient: OkHttpClient by lazy {
        com.example.network.NetworkClient.okHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Current User ID for Firestore Key Scoping
    private val _currentUserId = MutableStateFlow(prefs.getString("active_user_id", "guest_default") ?: "guest_default")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    // Cloud Sync Toggle / Status
    private val _isCloudSyncEnabled = MutableStateFlow(prefs.getBoolean("firebase_cloud_sync_enabled", true))
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Keys mapped by role
    private val _keysByRole = MutableStateFlow<Map<ApiRole, List<ManagedApiKey>>>(emptyMap())
    val keysByRole: StateFlow<Map<ApiRole, List<ManagedApiKey>>> = _keysByRole.asStateFlow()

    // Active key index per role (for rotation)
    private val _activeIndices = MutableStateFlow<Map<ApiRole, Int>>(emptyMap())
    val activeIndices: StateFlow<Map<ApiRole, Int>> = _activeIndices.asStateFlow()

    private val _activeBrainModel = MutableStateFlow(prefs.getString("active_ai_brain_model", "gemini-2.5-flash") ?: "gemini-2.5-flash")
    val activeBrainModelFlow: StateFlow<String> = _activeBrainModel.asStateFlow()

    val activeBrainModel: String
        get() = _activeBrainModel.value

    private val _lastSyncStatus = MutableStateFlow<String?>("Cloud & Local Key Vault Ready")
    val lastSyncStatus: StateFlow<String?> = _lastSyncStatus.asStateFlow()

    private var cloudListenerRegistration: ListenerRegistration? = null

    init {
        loadKeysFromLocal()
        if (_isCloudSyncEnabled.value) {
            syncWithFirestore(_currentUserId.value)
        }
    }

    fun setUserId(userId: String) {
        val clean = userId.ifBlank { "guest_default" }
        if (_currentUserId.value != clean) {
            _currentUserId.value = clean
            prefs.edit().putString("active_user_id", clean).apply()
            if (_isCloudSyncEnabled.value) {
                attachCloudListener(clean)
                syncWithFirestore(clean)
            }
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        _isCloudSyncEnabled.value = enabled
        prefs.edit().putBoolean("firebase_cloud_sync_enabled", enabled).apply()
        if (enabled) {
            syncWithFirestore(_currentUserId.value)
            attachCloudListener(_currentUserId.value)
        } else {
            cloudListenerRegistration?.remove()
            cloudListenerRegistration = null
            _lastSyncStatus.value = "Cloud Sync Disabled (Local Only)"
        }
    }

    fun setActiveBrainModel(modelName: String) {
        _activeBrainModel.value = modelName
        prefs.edit().putString("active_ai_brain_model", modelName).apply()
    }

    private fun loadKeysFromLocal() {
        val loadedMap = mutableMapOf<ApiRole, List<ManagedApiKey>>()

        ApiRole.entries.forEach { role ->
            val json = prefs.getString("keys_${role.code}", null)
            val keys = if (json != null) {
                try {
                    adapter.fromJson(json) ?: emptyList()
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse local keys for ${role.code}", e)
                    emptyList()
                }
            } else {
                emptyList()
            }
            loadedMap[role] = keys
        }

        // Check if legacy key or BuildConfig key should be seeded
        val legacyKey = context.getSharedPreferences("predictor_prefs", Context.MODE_PRIVATE)
            .getString("api_football_key", null)
            ?.takeIf { it.isNotBlank() && it != "87d20a4f0d5684ae37e1e8497be4e3b7" }
            ?: try {
                BuildConfig.API_FOOTBALL_KEY.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }

        val footballKeys = loadedMap[ApiRole.API_FOOTBALL] ?: emptyList()
        if (footballKeys.isEmpty() && !legacyKey.isNullOrBlank()) {
            val initialKey = ManagedApiKey(
                role = ApiRole.API_FOOTBALL.code,
                key = legacyKey,
                label = "Primary API-Sports Key",
                status = KeyStatus.ACTIVE.name
            )
            loadedMap[ApiRole.API_FOOTBALL] = listOf(initialKey)
            saveKeysToLocal(ApiRole.API_FOOTBALL, listOf(initialKey))
        }

        _keysByRole.value = loadedMap

        // Load active indices
        val indicesMap = mutableMapOf<ApiRole, Int>()
        ApiRole.entries.forEach { role ->
            indicesMap[role] = prefs.getInt("active_idx_${role.code}", 0)
        }
        _activeIndices.value = indicesMap
    }

    private fun saveKeysToLocal(role: ApiRole, keys: List<ManagedApiKey>) {
        try {
            val json = adapter.toJson(keys)
            prefs.edit().putString("keys_${role.code}", json).commit()
        } catch (e: Exception) {
            Log.e(tag, "Failed to save local keys for ${role.code}", e)
        }
    }

    /**
     * Synchronizes local keys with Firebase Firestore.
     * Merges remote keys with local keys so no data is lost.
     */
    fun syncWithFirestore(userId: String = _currentUserId.value, onComplete: ((Boolean, String) -> Unit)? = null) {
        if (!_isCloudSyncEnabled.value) {
            _isSyncing.value = false
            onComplete?.invoke(false, "Cloud sync is disabled in settings")
            return
        }

        scope.launch {
            _isSyncing.value = true
            try {
                val remoteKeys = firestoreRepository.fetchKeysFromCloud(userId)
                val allLocalKeys = _keysByRole.value.values.flatten()

                if (remoteKeys == null) {
                    _lastSyncStatus.value = "Local Vault Ready (${allLocalKeys.size} keys)"
                    onComplete?.invoke(true, "Using local key vault (${allLocalKeys.size} keys)")
                    return@launch
                }

                // Merge strategy: Combine local and remote keys, resolving conflicts by most recent updatedAt / createdAt
                val mergedKeysMap = mutableMapOf<String, ManagedApiKey>()

                // Add local keys first
                allLocalKeys.forEach { key ->
                    mergedKeysMap[key.id] = key
                }

                // Merge remote keys
                remoteKeys.forEach { rKey ->
                    val existingKey = mergedKeysMap[rKey.id] ?: mergedKeysMap.values.find { it.key == rKey.key }
                    if (existingKey == null) {
                        mergedKeysMap[rKey.id] = rKey
                    } else {
                        // If remote has newer activity or testing status, keep it
                        if (rKey.lastTestedAt >= existingKey.lastTestedAt) {
                            mergedKeysMap[existingKey.id] = rKey
                        }
                    }
                }

                val allMerged = mergedKeysMap.values.toList()

                // Update local in-memory and local storage
                val updatedGrouped = mutableMapOf<ApiRole, List<ManagedApiKey>>()
                ApiRole.entries.forEach { role ->
                    val roleKeys = allMerged.filter { it.apiRole == role }
                    updatedGrouped[role] = roleKeys
                    saveKeysToLocal(role, roleKeys)
                }
                _keysByRole.value = updatedGrouped

                // Push any missing keys back to Firestore
                firestoreRepository.syncAllKeysToCloud(userId, allMerged)

                _lastSyncStatus.value = "Synced ${allMerged.size} keys with Firebase Cloud"
                Log.i(tag, "Firestore sync complete for $userId. Total keys: ${allMerged.size}")
                onComplete?.invoke(true, "Synced ${allMerged.size} keys with Firebase")
            } catch (e: Exception) {
                Log.e(tag, "Sync with Firestore error: ${e.message}", e)
                val localCount = _keysByRole.value.values.flatten().size
                _lastSyncStatus.value = "Local Vault Active ($localCount keys)"
                onComplete?.invoke(false, "Sync: ${e.message ?: "Local vault active"}")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun attachCloudListener(userId: String) {
        cloudListenerRegistration?.remove()
        cloudListenerRegistration = firestoreRepository.listenToCloudKeys(
            userId = userId,
            onKeysUpdated = { cloudKeys ->
                if (cloudKeys.isNotEmpty()) {
                    val updatedGrouped = mutableMapOf<ApiRole, List<ManagedApiKey>>()
                    ApiRole.entries.forEach { role ->
                        val roleKeys = cloudKeys.filter { it.apiRole == role }
                        if (roleKeys.isNotEmpty() || _keysByRole.value[role]?.isEmpty() == true) {
                            updatedGrouped[role] = roleKeys
                            saveKeysToLocal(role, roleKeys)
                        } else {
                            updatedGrouped[role] = _keysByRole.value[role] ?: emptyList()
                        }
                    }
                    _keysByRole.value = updatedGrouped
                    _lastSyncStatus.value = "Cloud Real-time Update (${cloudKeys.size} keys)"
                }
            }
        )
    }

    /**
     * Gets the active key for a given role, performing health check and automatic cooldown recovery.
     */
    fun getActiveKey(role: ApiRole): String? {
        val keys = _keysByRole.value[role] ?: emptyList()
        if (keys.isEmpty()) return null

        val now = System.currentTimeMillis()
        val healthyKeys = keys.filter { key ->
            val isRecovered = key.rateLimitedUntil > 0L && key.rateLimitedUntil <= now
            key.keyStatus == KeyStatus.ACTIVE || isRecovered
        }

        if (healthyKeys.isEmpty()) {
            return null
        }

        val currentIndex = _activeIndices.value[role] ?: 0
        val safeIndex = currentIndex % healthyKeys.size
        return healthyKeys.getOrNull(safeIndex)?.key ?: healthyKeys.first().key
    }

    /**
     * Returns the full managed key object currently active.
     */
    fun getActiveManagedKey(role: ApiRole): ManagedApiKey? {
        val activeKeyString = getActiveKey(role) ?: return null
        val found = _keysByRole.value[role]?.find { it.key == activeKeyString }
        val model = _activeBrainModel.value
        return if (found != null && role == ApiRole.OPENAI_COMPATIBLE) {
            found.copy(modelName = model)
        } else {
            found
        }
    }

    /**
     * Rotates to the next available key for a role.
     */
    fun rotateNext(role: ApiRole): ManagedApiKey? {
        val keys = _keysByRole.value[role] ?: emptyList()
        if (keys.isEmpty()) return null

        val currentIndex = _activeIndices.value[role] ?: 0
        val nextIndex = (currentIndex + 1) % keys.size

        val updatedIndices = _activeIndices.value.toMutableMap()
        updatedIndices[role] = nextIndex
        _activeIndices.value = updatedIndices
        prefs.edit().putInt("active_idx_${role.code}", nextIndex).apply()

        Log.i(tag, "Rotated key for ${role.displayName} to index $nextIndex (${keys[nextIndex].maskedKey})")
        return keys[nextIndex]
    }

    /**
     * Adds or updates a key for a given role, persisting to local storage and Firebase Firestore.
     */
    fun addOrUpdateKey(apiKey: ManagedApiKey) {
        val role = apiKey.apiRole
        val currentKeys = (_keysByRole.value[role] ?: emptyList()).toMutableList()
        val existingIndex = currentKeys.indexOfFirst { it.id == apiKey.id || (it.key.trim().isNotEmpty() && it.key.trim() == apiKey.key.trim()) }

        if (existingIndex >= 0) {
            currentKeys[existingIndex] = apiKey
        } else {
            currentKeys.add(apiKey)
        }

        val updatedMap = _keysByRole.value.toMutableMap()
        updatedMap[role] = currentKeys
        _keysByRole.value = updatedMap
        saveKeysToLocal(role, currentKeys)
        _lastSyncStatus.value = "Updated ${role.displayName} (${currentKeys.size} stored)"

        // Sync key to Firestore
        if (_isCloudSyncEnabled.value) {
            scope.launch {
                val ok = firestoreRepository.saveKeyToCloud(_currentUserId.value, apiKey)
                if (ok) {
                    _lastSyncStatus.value = "Saved ${role.displayName} key to Firebase Firestore"
                }
            }
        }
    }

    /**
     * Removes a key by ID from local storage and Firebase Firestore.
     */
    fun removeKey(role: ApiRole, keyId: String) {
        val currentKeys = (_keysByRole.value[role] ?: emptyList()).toMutableList()
        currentKeys.removeAll { it.id == keyId }

        val updatedMap = _keysByRole.value.toMutableMap()
        updatedMap[role] = currentKeys
        _keysByRole.value = updatedMap
        saveKeysToLocal(role, currentKeys)

        val currentIdx = _activeIndices.value[role] ?: 0
        if (currentIdx >= currentKeys.size && currentKeys.isNotEmpty()) {
            val updatedIndices = _activeIndices.value.toMutableMap()
            updatedIndices[role] = 0
            _activeIndices.value = updatedIndices
            prefs.edit().putInt("active_idx_${role.code}", 0).apply()
        }

        // Delete from Firestore
        if (_isCloudSyncEnabled.value) {
            scope.launch {
                firestoreRepository.deleteKeyFromCloud(_currentUserId.value, keyId)
            }
        }
    }

    /**
     * Reports successful usage of a key.
     */
    fun reportKeySuccess(role: ApiRole, key: String) {
        val currentKeys = (_keysByRole.value[role] ?: emptyList()).toMutableList()
        val index = currentKeys.indexOfFirst { it.key == key }
        if (index >= 0) {
            val item = currentKeys[index]
            val updated = item.copy(
                status = KeyStatus.ACTIVE.name,
                usageCount = item.usageCount + 1,
                lastUsedTimestamp = System.currentTimeMillis(),
                rateLimitedUntil = 0L
            )
            currentKeys[index] = updated
            val updatedMap = _keysByRole.value.toMutableMap()
            updatedMap[role] = currentKeys
            _keysByRole.value = updatedMap
            saveKeysToLocal(role, currentKeys)

            if (_isCloudSyncEnabled.value) {
                scope.launch {
                    firestoreRepository.saveKeyToCloud(_currentUserId.value, updated)
                }
            }
        }
    }

    /**
     * Reports that a key received rate-limit (HTTP 429).
     */
    fun reportKeyRateLimited(role: ApiRole, key: String, cooldownSeconds: Long = 300) {
        val currentKeys = (_keysByRole.value[role] ?: emptyList()).toMutableList()
        val index = currentKeys.indexOfFirst { it.key == key }
        if (index >= 0) {
            val item = currentKeys[index]
            val cooldownUntil = System.currentTimeMillis() + (cooldownSeconds * 1000)
            val updated = item.copy(
                status = KeyStatus.RATE_LIMITED.name,
                errorCount = item.errorCount + 1,
                rateLimitedUntil = cooldownUntil,
                lastUsedTimestamp = System.currentTimeMillis()
            )
            currentKeys[index] = updated
            val updatedMap = _keysByRole.value.toMutableMap()
            updatedMap[role] = currentKeys
            _keysByRole.value = updatedMap
            saveKeysToLocal(role, currentKeys)

            Log.w(tag, "Key ${item.maskedKey} rate limited for ${cooldownSeconds}s. Auto-rotating next key.")
            rotateNext(role)

            if (_isCloudSyncEnabled.value) {
                scope.launch {
                    firestoreRepository.saveKeyToCloud(_currentUserId.value, updated)
                }
            }
        }
    }

    /**
     * Reports authentication or general error for a key.
     */
    fun reportKeyError(role: ApiRole, key: String, isAuthError: Boolean) {
        val currentKeys = (_keysByRole.value[role] ?: emptyList()).toMutableList()
        val index = currentKeys.indexOfFirst { it.key == key }
        if (index >= 0) {
            val item = currentKeys[index]
            val updated = item.copy(
                status = if (isAuthError) KeyStatus.ERROR.name else item.status,
                errorCount = item.errorCount + 1,
                lastUsedTimestamp = System.currentTimeMillis()
            )
            currentKeys[index] = updated
            val updatedMap = _keysByRole.value.toMutableMap()
            updatedMap[role] = currentKeys
            _keysByRole.value = updatedMap
            saveKeysToLocal(role, currentKeys)

            if (isAuthError) {
                Log.w(tag, "Key ${item.maskedKey} authentication error (401/403). Auto-rotating next key.")
                rotateNext(role)
            }

            if (_isCloudSyncEnabled.value) {
                scope.launch {
                    firestoreRepository.saveKeyToCloud(_currentUserId.value, updated)
                }
            }
        }
    }

    /**
     * Resets statistics for all keys in a role.
     */
    fun resetKeyStats(role: ApiRole) {
        val currentKeys = (_keysByRole.value[role] ?: emptyList()).map {
            it.copy(
                status = KeyStatus.ACTIVE.name,
                usageCount = 0,
                errorCount = 0,
                rateLimitedUntil = 0L,
                lastTestMessage = null,
                lastTestStatus = null
            )
        }
        val updatedMap = _keysByRole.value.toMutableMap()
        updatedMap[role] = currentKeys
        _keysByRole.value = updatedMap
        saveKeysToLocal(role, currentKeys)

        if (_isCloudSyncEnabled.value) {
            scope.launch {
                firestoreRepository.syncAllKeysToCloud(_currentUserId.value, currentKeys)
            }
        }
    }

    /**
     * Performs a fast connectivity diagnostics check for an API key.
     */
    fun testKeyConnection(apiKey: ManagedApiKey, onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val role = apiKey.apiRole
            val keyVal = apiKey.key.trim()
            if (keyVal.isBlank()) {
                withContext(Dispatchers.Main) {
                    onResult(false, "API Key is empty")
                }
                return@launch
            }

            var success = false
            var message = ""

            try {
                when (role) {
                    ApiRole.FOOTBALL_DATA_ORG -> {
                        val req = Request.Builder()
                            .url("https://api.football-data.org/v4/competitions")
                            .header("X-Auth-Token", keyVal)
                            .header("User-Agent", "FootballPredictor/1.0")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { res ->
                            val body = res.body?.string() ?: ""
                            if (res.isSuccessful) {
                                success = true
                                message = "Connected to Football-Data.org (HTTP ${res.code})"
                            } else {
                                message = if (res.code == 400 || res.code == 403 || res.code == 401) {
                                    "Invalid Token (HTTP ${res.code}): Please check your Football-Data.org token"
                                } else if (res.code == 429) {
                                    "Rate Limited (HTTP 429): Free tier request limit exceeded"
                                } else {
                                    "HTTP ${res.code}: ${if (res.message.isNotBlank()) res.message else body.take(60)}"
                                }
                            }
                        }
                    }
                    ApiRole.API_FOOTBALL -> {
                        val req = Request.Builder()
                            .url("https://v3.football.api-sports.io/status")
                            .header("x-apisports-key", keyVal)
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { res ->
                            val body = res.body?.string() ?: ""
                            if (res.isSuccessful) {
                                success = true
                                message = "Connected to API-Football (HTTP ${res.code})"
                            } else {
                                message = if (res.code == 401 || res.code == 403) {
                                    "Invalid Key (HTTP ${res.code}): Please check your API-Sports key"
                                } else {
                                    "HTTP ${res.code}: ${if (res.message.isNotBlank()) res.message else body.take(60)}"
                                }
                            }
                        }
                    }
                    ApiRole.THE_ODDS_API -> {
                        val req = Request.Builder()
                            .url("https://api.the-odds-api.com/v4/sports?apiKey=$keyVal")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { res ->
                            val body = res.body?.string() ?: ""
                            if (res.isSuccessful) {
                                success = true
                                message = "Connected to The Odds API (HTTP ${res.code})"
                            } else {
                                message = if (res.code == 401 || res.code == 403) {
                                    "Invalid Key: Incorrect The Odds API key"
                                } else {
                                    "HTTP ${res.code}: ${if (res.message.isNotBlank()) res.message else body.take(60)}"
                                }
                            }
                        }
                    }
                    ApiRole.OPENAI_COMPATIBLE -> {
                        val baseUrl = apiKey.endpointUrl.trim().removeSuffix("/")
                        val endpoint = if (baseUrl.endsWith("/models")) baseUrl else "$baseUrl/models"
                        val req = Request.Builder()
                            .url(endpoint)
                            .header("Authorization", "Bearer $keyVal")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { res ->
                            val body = res.body?.string() ?: ""
                            if (res.isSuccessful) {
                                success = true
                                message = "Connected to AI Model Endpoint (HTTP ${res.code})"
                            } else {
                                val parsed = com.example.network.OpenAiService.extractErrorMessageFromJson(body, res.code)
                                message = if (res.code == 401 || res.code == 403) {
                                    "Authentication Failed (HTTP ${res.code}): $parsed"
                                } else {
                                    "HTTP ${res.code}: $parsed"
                                }
                            }
                        }
                    }
                    ApiRole.GEMINI -> {
                        val req = Request.Builder()
                            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$keyVal")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { res ->
                            val body = res.body?.string() ?: ""
                            if (res.isSuccessful) {
                                success = true
                                message = "Connected to Gemini API (HTTP ${res.code})"
                            } else {
                                val parsed = com.example.network.OpenAiService.extractErrorMessageFromJson(body, res.code)
                                message = if (res.code == 400 || res.code == 403) {
                                    "Invalid Gemini Key (HTTP ${res.code}): $parsed"
                                } else {
                                    "HTTP ${res.code}: $parsed"
                                }
                            }
                        }
                    }
                    else -> {
                        success = true
                        message = "Key format valid (${apiKey.maskedKey})"
                    }
                }
            } catch (e: Exception) {
                success = false
                val reason = when {
                    e is java.net.UnknownHostException -> "No network / DNS error"
                    e is java.net.SocketTimeoutException -> "Connection timed out"
                    e is javax.net.ssl.SSLException -> "SSL/TLS handshake error"
                    !e.message.isNullOrBlank() && e.message != "null" -> e.message
                    !e.localizedMessage.isNullOrBlank() && e.localizedMessage != "null" -> e.localizedMessage
                    e.cause != null && !e.cause?.message.isNullOrBlank() && e.cause?.message != "null" -> e.cause?.message
                    else -> e.javaClass.simpleName
                }
                message = "Connection error: $reason"
            }

            // Update key testing status
            val updated = apiKey.copy(
                lastTestedAt = System.currentTimeMillis(),
                lastTestStatus = if (success) "ACTIVE" else "ERROR",
                lastTestMessage = message,
                status = if (success) KeyStatus.ACTIVE.name else apiKey.status
            )
            addOrUpdateKey(updated)

            withContext(Dispatchers.Main) {
                onResult(success, message)
            }
        }
    }
}
