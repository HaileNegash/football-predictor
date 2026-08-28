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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class KeyRotationManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val tag = "KeyRotationManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("api_key_vault_local", Context.MODE_PRIVATE)

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val listType = Types.newParameterizedType(List::class.java, ManagedApiKey::class.java)
    private val adapter = moshi.adapter<List<ManagedApiKey>>(listType)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

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

    private val _lastSyncStatus = MutableStateFlow<String?>("Local Vault Active (On-Device Storage)")
    val lastSyncStatus: StateFlow<String?> = _lastSyncStatus.asStateFlow()

    init {
        loadKeysFromLocal()
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
            prefs.edit().putString("keys_${role.code}", json).apply()
        } catch (e: Exception) {
            Log.e(tag, "Failed to save local keys for ${role.code}", e)
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
            return keys.firstOrNull()?.key
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
     * Adds or updates a key for a given role.
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
    }

    /**
     * Removes a key by ID.
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
    }

    /**
     * Performs a fast connectivity diagnostics check for an API key.
     */
    fun testKeyConnection(apiKey: ManagedApiKey, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            val role = apiKey.apiRole
            val keyVal = apiKey.key.trim()
            if (keyVal.isBlank()) {
                onResult(false, "API Key is empty")
                return@launch
            }

            var success = false
            var message = ""

            try {
                when (role) {
                    ApiRole.FOOTBALL_DATA_ORG -> {
                        val req = Request.Builder()
                            .url("https://api.football-data.org/v4/competitions/PL")
                            .header("X-Auth-Token", keyVal)
                            .get()
                            .build()
                        val res = httpClient.newCall(req).execute()
                        if (res.isSuccessful) {
                            success = true
                            message = "Connected to Football-Data.org (HTTP ${res.code})"
                        } else {
                            message = "HTTP ${res.code}: ${res.message}"
                        }
                    }
                    ApiRole.API_FOOTBALL -> {
                        val req = Request.Builder()
                            .url("https://v3.football.api-sports.io/status")
                            .header("x-apisports-key", keyVal)
                            .get()
                            .build()
                        val res = httpClient.newCall(req).execute()
                        if (res.isSuccessful) {
                            success = true
                            message = "Connected to API-Football (HTTP ${res.code})"
                        } else {
                            message = "HTTP ${res.code}: ${res.message}"
                        }
                    }
                    ApiRole.THE_ODDS_API -> {
                        val req = Request.Builder()
                            .url("https://api.the-odds-api.com/v4/sports?apiKey=$keyVal")
                            .get()
                            .build()
                        val res = httpClient.newCall(req).execute()
                        if (res.isSuccessful) {
                            success = true
                            message = "Connected to The Odds API (HTTP ${res.code})"
                        } else {
                            message = "HTTP ${res.code}: ${res.message}"
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
                        val res = httpClient.newCall(req).execute()
                        if (res.isSuccessful) {
                            success = true
                            message = "Connected to AI Model Endpoint (HTTP ${res.code})"
                        } else {
                            message = "HTTP ${res.code}: ${res.message}"
                        }
                    }
                    ApiRole.GEMINI -> {
                        val req = Request.Builder()
                            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$keyVal")
                            .get()
                            .build()
                        val res = httpClient.newCall(req).execute()
                        if (res.isSuccessful) {
                            success = true
                            message = "Connected to Gemini API (HTTP ${res.code})"
                        } else {
                            message = "HTTP ${res.code}: ${res.message}"
                        }
                    }
                    else -> {
                        success = true
                        message = "Key format valid (${apiKey.maskedKey})"
                    }
                }
            } catch (e: Exception) {
                success = false
                message = "Connection error: ${e.localizedMessage ?: e.message}"
            }

            // Update key testing status
            val updated = apiKey.copy(
                lastTestedAt = System.currentTimeMillis(),
                lastTestStatus = if (success) "ACTIVE" else "ERROR",
                lastTestMessage = message,
                status = if (success) KeyStatus.ACTIVE.name else apiKey.status
            )
            addOrUpdateKey(updated)

            onResult(success, message)
        }
    }
}
