package com.example.keymanager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KeyRotationManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val tag = "KeyRotationManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("api_key_vault", Context.MODE_PRIVATE)
    private val firebaseService = FirebaseKeyService(context.applicationContext)

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val listType = Types.newParameterizedType(List::class.java, ManagedApiKey::class.java)
    private val adapter = moshi.adapter<List<ManagedApiKey>>(listType)

    // Keys mapped by role
    private val _keysByRole = MutableStateFlow<Map<ApiRole, List<ManagedApiKey>>>(emptyMap())
    val keysByRole: StateFlow<Map<ApiRole, List<ManagedApiKey>>> = _keysByRole.asStateFlow()

    // Active key index per role (for rotation)
    private val _activeIndices = MutableStateFlow<Map<ApiRole, Int>>(emptyMap())
    val activeIndices: StateFlow<Map<ApiRole, Int>> = _activeIndices.asStateFlow()

    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing: StateFlow<Boolean> = _isCloudSyncing.asStateFlow()

    private val _lastSyncStatus = MutableStateFlow<String?>("Connecting to Cloud...")
    val lastSyncStatus: StateFlow<String?> = _lastSyncStatus.asStateFlow()

    init {
        loadKeysFromLocal()
        observeCloudUpdates()
        // Immediate cloud fetch on start
        if (firebaseService.isFirebaseAvailable) {
            scope.launch {
                val result = firebaseService.fetchAllKeys()
                if (result.isSuccess) {
                    val keys = result.getOrNull() ?: emptyList()
                    if (keys.isNotEmpty()) {
                        mergeCloudKeys(keys)
                        _lastSyncStatus.value = "Cloud Active (${keys.size} keys)"
                    }
                }
            }
        }
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

        // Check if legacy key or default BuildConfig key should be migrated
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
            prefs.edit().putString("keys_${role.code}", json).apply()
        } catch (e: Exception) {
            Log.e(tag, "Failed to save local keys for ${role.code}", e)
        }
    }

    private fun observeCloudUpdates() {
        if (!firebaseService.isFirebaseAvailable) {
            _lastSyncStatus.value = "Local Cache (Firebase offline/unconfigured)"
            return
        }

        scope.launch {
            try {
                firebaseService.observeKeys().collect { cloudKeys ->
                    if (cloudKeys.isNotEmpty()) {
                        mergeCloudKeys(cloudKeys)
                        _lastSyncStatus.value = "Cloud Active (${cloudKeys.size} keys)"
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Cloud observe error: ${e.message}")
            }
        }
    }

    private fun mergeCloudKeys(cloudKeys: List<ManagedApiKey>) {
        val currentMap = _keysByRole.value.toMutableMap()
        var hasChanges = false

        ApiRole.entries.forEach { role ->
            val roleCloudKeys = cloudKeys.filter { it.apiRole == role }
            if (roleCloudKeys.isNotEmpty()) {
                currentMap[role] = roleCloudKeys
                saveKeysToLocal(role, roleCloudKeys)
                hasChanges = true
            }
        }

        if (hasChanges) {
            _keysByRole.value = currentMap
        }
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
            // All keys in cooldown or error; return first key anyway or null
            return keys.firstOrNull()?.key
        }

        val currentIndex = _activeIndices.value[role] ?: 0
        val safeIndex = currentIndex % healthyKeys.size
        return healthyKeys.getOrNull(safeIndex)?.key ?: healthyKeys.first().key
    }

    val activeBrainModel: String
        get() = firebaseService.activeBrainModel ?: "qwen-3.8-max-free"

    /**
     * Returns the full managed key object currently active.
     */
    fun getActiveManagedKey(role: ApiRole): ManagedApiKey? {
        val activeKeyString = getActiveKey(role) ?: return null
        val found = _keysByRole.value[role]?.find { it.key == activeKeyString }
        val model = firebaseService.activeBrainModel ?: "qwen-3.8-max-free"
        return if (found != null && role == ApiRole.OPENAI_COMPATIBLE) {
            found.copy(modelName = model)
        } else {
            found
        }
    }

    /**
     * Rotates to the next available healthy key for a role.
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
     * Adds or updates a key for a given role.
     */
    fun addOrUpdateKey(apiKey: ManagedApiKey, syncToCloud: Boolean = true) {
        val role = apiKey.apiRole
        val currentKeys = (_keysByRole.value[role] ?: emptyList()).toMutableList()
        val existingIndex = currentKeys.indexOfFirst { it.id == apiKey.id || it.key.trim() == apiKey.key.trim() }

        if (existingIndex >= 0) {
            currentKeys[existingIndex] = apiKey
        } else {
            currentKeys.add(apiKey)
        }

        val updatedMap = _keysByRole.value.toMutableMap()
        updatedMap[role] = currentKeys
        _keysByRole.value = updatedMap
        saveKeysToLocal(role, currentKeys)

        if (syncToCloud && firebaseService.isFirebaseAvailable) {
            scope.launch {
                firebaseService.saveKey(apiKey)
            }
        }
    }

    /**
     * Removes a key by ID.
     */
    fun removeKey(role: ApiRole, keyId: String, syncToCloud: Boolean = true) {
        val currentKeys = (_keysByRole.value[role] ?: emptyList()).toMutableList()
        currentKeys.removeAll { it.id == keyId }

        val updatedMap = _keysByRole.value.toMutableMap()
        updatedMap[role] = currentKeys
        _keysByRole.value = updatedMap
        saveKeysToLocal(role, currentKeys)

        // Adjust index if out of bounds
        val currentIdx = _activeIndices.value[role] ?: 0
        if (currentIdx >= currentKeys.size && currentKeys.isNotEmpty()) {
            val updatedIndices = _activeIndices.value.toMutableMap()
            updatedIndices[role] = 0
            _activeIndices.value = updatedIndices
            prefs.edit().putInt("active_idx_${role.code}", 0).apply()
        }

        if (syncToCloud && firebaseService.isFirebaseAvailable) {
            scope.launch {
                firebaseService.deleteKey(keyId)
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
        }
    }

    /**
     * Reports that a key received rate-limit (HTTP 429) or quota exceeded.
     * Places the key on cooldown and automatically rotates to the next available healthy key.
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
        }
    }

    /**
     * Updates the active AI model name (e.g. "qwen-3.8-max-free") for a role.
     */
    fun setSelectedModel(role: ApiRole, modelName: String) {
        val currentKeys = (_keysByRole.value[role] ?: emptyList()).toMutableList()
        if (currentKeys.isEmpty()) return

        val currentIndex = _activeIndices.value[role] ?: 0
        val safeIndex = currentIndex % currentKeys.size
        val currentKey = currentKeys[safeIndex]

        val updated = currentKey.copy(modelName = modelName)
        currentKeys[safeIndex] = updated

        val updatedMap = _keysByRole.value.toMutableMap()
        updatedMap[role] = currentKeys
        _keysByRole.value = updatedMap
        saveKeysToLocal(role, currentKeys)

        if (firebaseService.isFirebaseAvailable) {
            scope.launch {
                firebaseService.saveKey(updated)
            }
        }
    }

    /**
     * Manual sync action with Firebase Firestore.
     */
    fun triggerCloudSync(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (!firebaseService.isFirebaseAvailable) {
            onResult(false, "Firebase is not configured or unavailable")
            return
        }

        scope.launch {
            _isCloudSyncing.value = true
            _lastSyncStatus.value = "Syncing with Firebase Firestore..."

            try {
                // 1. Upload local keys to Cloud
                val allLocalKeys = _keysByRole.value.values.flatten()
                allLocalKeys.forEach { key ->
                    firebaseService.saveKey(key)
                }

                // 2. Fetch all cloud keys
                val cloudResult = firebaseService.fetchAllKeys()
                if (cloudResult.isSuccess) {
                    val cloudKeys = cloudResult.getOrNull() ?: emptyList()
                    mergeCloudKeys(cloudKeys)
                    _lastSyncStatus.value = "Synced: ${cloudKeys.size} Cloud Keys Active"
                    onResult(true, "Successfully synced with Firebase Firestore (${cloudKeys.size} keys)")
                } else {
                    val errorMsg = cloudResult.exceptionOrNull()?.message ?: "Unknown error"
                    _lastSyncStatus.value = "Sync failed: $errorMsg"
                    onResult(false, "Sync failed: $errorMsg")
                }
            } catch (e: Exception) {
                _lastSyncStatus.value = "Sync error: ${e.message}"
                onResult(false, "Sync error: ${e.message}")
            } finally {
                _isCloudSyncing.value = false
            }
        }
    }
}
