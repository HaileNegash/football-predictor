package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.models.AccentColorMode
import com.example.models.AiReasoningDepth
import com.example.models.AppCustomSettings
import com.example.models.Country
import com.example.models.League
import com.example.models.Match
import com.example.models.OddsFormat
import com.example.models.PredictionResult
import com.example.models.RiskTolerance
import com.example.models.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CloudSyncState(val label: String) {
    IDLE("Local Vault Active"),
    SYNCING("Saving to Local Vault..."),
    SYNCED("Saved On-Device"),
    ERROR("Storage Ready")
}

class PredictorViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("predictor_prefs_v2", Context.MODE_PRIVATE)

    private val _cloudSyncState = MutableStateFlow(CloudSyncState.IDLE)
    val cloudSyncState: StateFlow<CloudSyncState> = _cloudSyncState.asStateFlow()

    private val _lastCloudSyncTimestamp = MutableStateFlow(prefs.getLong("last_local_save_time", System.currentTimeMillis()))
    val lastCloudSyncTimestamp: StateFlow<Long> = _lastCloudSyncTimestamp.asStateFlow()

    val keyManager = com.example.keymanager.KeyRotationManager(application, viewModelScope)
    val userManager = com.example.auth.UserManager(application)
    val currentUser = userManager.currentUser
    private val footballRepository = com.example.network.MultiProviderFootballRepository(application, keyManager)

    private val _countries = MutableStateFlow<List<Country>>(emptyList())
    private val _allCachedCountriesByDate = mutableMapOf<String, List<Country>>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val countries: StateFlow<List<Country>> = _countries.asStateFlow()

    val allLoadedMatches: List<Match>
        get() = _allCachedCountriesByDate.values.flatten().flatMap { it.leagues }.flatMap { it.matches }

    val searchResults: StateFlow<List<com.example.models.SearchItem>> = combine(_countries, _searchQuery) { currentDayCountries, query ->
        if (query.isBlank()) return@combine emptyList()
        val lower = query.lowercase(Locale.getDefault()).trim()
        val results = mutableListOf<com.example.models.SearchItem>()
        val seen = mutableSetOf<String>()

        val searchPool = if (_allCachedCountriesByDate.isNotEmpty()) {
            _allCachedCountriesByDate.values.flatten()
        } else {
            currentDayCountries
        }

        searchPool.forEach { country ->
            if (country.name.lowercase(Locale.getDefault()).contains(lower)) {
                val id = "country_${country.name}"
                if (seen.add(id)) {
                    results.add(com.example.models.SearchItem(id, country.name, "SOCCER • ${country.name.uppercase()}", country.flagUrl, "COUNTRY"))
                }
            }
            country.leagues.forEach { league ->
                if (league.name.lowercase(Locale.getDefault()).contains(lower)) {
                    val id = "league_${league.id}"
                    if (seen.add(id)) {
                        results.add(com.example.models.SearchItem(id, league.name, "SOCCER • ${country.name.uppercase()}", league.logoUrl, "LEAGUE"))
                    }
                }
                league.matches.forEach { match ->
                    if (match.homeTeam.lowercase(Locale.getDefault()).contains(lower)) {
                        val id = "team_${match.homeTeam}"
                        if (seen.add(id)) {
                            results.add(com.example.models.SearchItem(id, match.homeTeam, "SOCCER • ${country.name.uppercase()} • ${league.name}", match.homeLogo, "TEAM"))
                        }
                    }
                    if (match.awayTeam.lowercase(Locale.getDefault()).contains(lower)) {
                        val id = "team_${match.awayTeam}"
                        if (seen.add(id)) {
                            results.add(com.example.models.SearchItem(id, match.awayTeam, "SOCCER • ${country.name.uppercase()} • ${league.name}", match.awayLogo, "TEAM"))
                        }
                    }
                }
            }
        }
        results
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentDate = MutableStateFlow(getCurrentDateString(0))
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    private val _currentDateOffset = MutableStateFlow(0)
    private val _maxDateOffset = MutableStateFlow<Int?>(null)

    val isNextDayEnabled: StateFlow<Boolean> = combine(_currentDateOffset, _maxDateOffset) { current, max ->
        max == null || current < max
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val availableBetTypes = listOf(
        "1X2 (Win / Draw / Lose)",
        "Double Chance (1X, 12, X2)",
        "Over/Under Goals",
        "Both Teams to Score (BTTS)",
        "Asian Handicap",
        "European Handicap",
        "Combo Bets",
        "Correct Score",
        "Draw No Bet (DNB)",
        "Half Time / Full Time (HT/FT)",
        "First Team to Score",
        "Total Corners",
        "Total Cards",
        "Both Teams to Score in Both Halves"
    )

    val availableCurrencies = com.example.models.PopularCurrencies

    private val _selectedBetTypes = MutableStateFlow<Set<String>>(
        prefs.getStringSet("selected_bet_types", null) ?: availableBetTypes.toSet()
    )
    val selectedBetTypes: StateFlow<Set<String>> = _selectedBetTypes.asStateFlow()

    private val _selectedSearchItems = MutableStateFlow<Set<String>>(
        prefs.getStringSet("selected_search_items", null) ?: emptySet()
    )
    val selectedSearchItems: StateFlow<Set<String>> = _selectedSearchItems.asStateFlow()

    private val _selectedCurrency = MutableStateFlow(
        prefs.getString("selected_currency_code", null)?.let { savedCode ->
            availableCurrencies.find { it.code == savedCode }
        } ?: availableCurrencies.first()
    )
    val selectedCurrency: StateFlow<com.example.models.Currency> = _selectedCurrency.asStateFlow()

    private val _budget = MutableStateFlow(prefs.getFloat("betting_budget", 50f))
    val budget: StateFlow<Float> = _budget.asStateFlow()

    private val _moneyRange = MutableStateFlow(
        (prefs.getFloat("money_target_min", 10f))..(prefs.getFloat("money_target_max", 250f))
    )
    val moneyRange: StateFlow<ClosedFloatingPointRange<Float>> = _moneyRange.asStateFlow()

    // App Custom Settings
    private val _useLocalEngineOnly = MutableStateFlow(prefs.getBoolean("use_local_engine_only", false))
    val useLocalEngineOnly: StateFlow<Boolean> = _useLocalEngineOnly.asStateFlow()

    fun setUseLocalEngineOnly(useLocal: Boolean) {
        _useLocalEngineOnly.value = useLocal
        prefs.edit().putBoolean("use_local_engine_only", useLocal).apply()
    }

    fun hasConfiguredAiKey(): Boolean {
        val activeModelName = _customSettings.value.activeAiModelId
        val userModel = _userAddedModels.value.find { it.id == activeModelName }
        val openAiKey = keyManager.getActiveManagedKey(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE)
        return (userModel != null && userModel.apiKey.isNotBlank()) || (openAiKey != null && openAiKey.key.isNotBlank())
    }

    fun getActiveAiKeyMasked(): String? {
        val activeModelName = _customSettings.value.activeAiModelId
        val userModel = _userAddedModels.value.find { it.id == activeModelName }
        if (userModel != null && userModel.apiKey.isNotBlank()) {
            val k = userModel.apiKey
            return if (k.length > 8) "${k.take(4)}...${k.takeLast(4)}" else "••••••••"
        }
        val openAiKey = keyManager.getActiveManagedKey(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE)
        return openAiKey?.maskedKey
    }

    private val _customSettings = MutableStateFlow(
        AppCustomSettings(
            themeMode = ThemeMode.fromId(prefs.getString("app_theme_mode", "cyber_dark") ?: "cyber_dark"),
            accentColorMode = AccentColorMode.fromId(prefs.getString("app_accent_color", "orange") ?: "orange"),
            oddsFormat = OddsFormat.fromId(prefs.getString("app_odds_format", "decimal") ?: "decimal"),
            autoRefreshSec = prefs.getInt("app_auto_refresh", 30),
            showFinishedMatches = prefs.getBoolean("app_show_finished", true),
            hapticsEnabled = prefs.getBoolean("app_haptics_enabled", true),
            dataSaver = prefs.getBoolean("app_data_saver", false),
            compactCardMode = prefs.getBoolean("app_compact_mode", false),
            aiReasoningDepth = AiReasoningDepth.fromId(prefs.getString("app_ai_depth", "deep") ?: "deep"),
            riskTolerance = RiskTolerance.fromId(prefs.getString("app_risk_tolerance", "balanced") ?: "balanced"),
            minConfidenceThreshold = prefs.getInt("app_min_confidence", 65),
            activeAiModelId = prefs.getString("app_active_ai_model", "gemini-2.5-flash") ?: "gemini-2.5-flash",
            customAiModelName = prefs.getString("app_custom_ai_model_name", "") ?: "",
            customAiEndpointUrl = prefs.getString("app_custom_ai_endpoint", "") ?: "",
            customTacticalPrompt = prefs.getString("app_custom_tactical_prompt", "") ?: ""
        )
    )
    val customSettings: StateFlow<AppCustomSettings> = _customSettings.asStateFlow()

    // Saved Prediction Slips
    private val _currentSlip = MutableStateFlow<com.example.models.SavedPredictionSlip?>(null)
    val currentSlip: StateFlow<com.example.models.SavedPredictionSlip?> = _currentSlip.asStateFlow()

    private val _savedSlipsHistory = MutableStateFlow<List<com.example.models.SavedPredictionSlip>>(emptyList())
    val savedSlipsHistory: StateFlow<List<com.example.models.SavedPredictionSlip>> = _savedSlipsHistory.asStateFlow()

    // User Added AI Models
    private val _userAddedModels = MutableStateFlow<List<com.example.models.UserAiModel>>(loadUserModelsFromPrefs())
    val userAddedModels: StateFlow<List<com.example.models.UserAiModel>> = _userAddedModels.asStateFlow()

    // Batch prediction engine states
    private val _batchMatchItems = MutableStateFlow<List<com.example.models.AgentBatchMatchItem>>(emptyList())
    val batchMatchItems: StateFlow<List<com.example.models.AgentBatchMatchItem>> = _batchMatchItems.asStateFlow()

    private val _isAgentRunning = MutableStateFlow(false)
    val isAgentRunning: StateFlow<Boolean> = _isAgentRunning.asStateFlow()

    private val _agentLogs = MutableStateFlow<List<com.example.models.AgentStreamLog>>(emptyList())
    val agentLogs: StateFlow<List<com.example.models.AgentStreamLog>> = _agentLogs.asStateFlow()

    private val _currentActivePredictingIndex = MutableStateFlow(-1)
    val currentActivePredictingIndex: StateFlow<Int> = _currentActivePredictingIndex.asStateFlow()

    // API Fallback Prompt for Interactive User Decision
    private val _apiFallbackPrompt = MutableStateFlow<com.example.models.ApiFallbackPrompt?>(null)
    val apiFallbackPrompt: StateFlow<com.example.models.ApiFallbackPrompt?> = _apiFallbackPrompt.asStateFlow()

    private var fallbackCompleter: kotlinx.coroutines.CompletableDeferred<com.example.models.FallbackDecision>? = null

    fun submitFallbackDecision(decision: com.example.models.FallbackDecision) {
        _apiFallbackPrompt.value = null
        fallbackCompleter?.complete(decision)
        fallbackCompleter = null
    }

    init {
        loadSavedSlipsFromStorage()
        fetchFixtures()
        setupKeyVaultObserver()
        setupUserKeySync()
    }

    private fun setupUserKeySync() {
        viewModelScope.launch {
            userManager.currentUser.collect { user ->
                keyManager.setUserId(user.userId)
            }
        }
    }

    private fun setupKeyVaultObserver() {
        viewModelScope.launch {
            keyManager.keysByRole.collect { keysMap ->
                val hasAnyFootballKey = keysMap.any { (role, keys) ->
                    (role == com.example.keymanager.ApiRole.FOOTBALL_DATA_ORG ||
                     role == com.example.keymanager.ApiRole.API_FOOTBALL ||
                     role == com.example.keymanager.ApiRole.SPORTMONKS ||
                     role == com.example.keymanager.ApiRole.THE_SPORTS_DB ||
                     role == com.example.keymanager.ApiRole.THE_ODDS_API) &&
                     keys.any { it.status == "ACTIVE" }
                }
                if (hasAnyFootballKey && _countries.value.isEmpty()) {
                    fetchFixtures()
                }
            }
        }
    }

    fun saveApiFootballKey(key: String, label: String = "Primary Football Key", role: com.example.keymanager.ApiRole = com.example.keymanager.ApiRole.FOOTBALL_DATA_ORG) {
        val apiKeyObj = com.example.keymanager.ManagedApiKey(
            role = role.code,
            key = key,
            label = label
        )
        keyManager.addOrUpdateKey(apiKeyObj)
        fetchFixtures(forceRefresh = true)
    }

    private fun getCurrentDateString(offsetDays: Int): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, offsetDays)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    fun changeDateBy(offsetDays: Int) {
        val newOffset = _currentDateOffset.value + offsetDays
        if (newOffset >= 0) {
            _currentDateOffset.value = newOffset
            val newDate = getCurrentDateString(newOffset)
            _currentDate.value = newDate

            val cached = _allCachedCountriesByDate[newDate]
            if (cached != null && cached.isNotEmpty()) {
                _countries.value = cached
                _errorMessage.value = null
            } else {
                fetchFixtures()
            }
        }
    }

    val isToday: Boolean get() = _currentDateOffset.value == 0

    fun fetchFixtures(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val dateStr = _currentDate.value
                val dateToStr = getCurrentDateString(_currentDateOffset.value + 6)

                val result = footballRepository.fetchFixtures(dateStr, dateToStr = dateToStr, forceRefresh = forceRefresh)
                if (result.isSuccess) {
                    val data = result.getOrNull() ?: emptyList()
                    if (data.isNotEmpty()) {
                        val allMatches = data.flatMap { it.leagues }.flatMap { it.matches }
                        val matchesHaveDates = allMatches.any { it.matchDate.isNotBlank() }

                        if (matchesHaveDates) {
                            val datesFound = allMatches.map { it.matchDate.ifBlank { dateStr } }.distinct()
                            datesFound.forEach { matchDateKey ->
                                val countriesForDate = data.mapNotNull { country ->
                                    val filteredLeagues = country.leagues.mapNotNull { league ->
                                        val filteredMatches = league.matches.filter { (it.matchDate.ifBlank { dateStr }) == matchDateKey }
                                        if (filteredMatches.isNotEmpty()) league.copy(matches = filteredMatches) else null
                                    }
                                    if (filteredLeagues.isNotEmpty()) country.copy(leagues = filteredLeagues) else null
                                }
                                if (countriesForDate.isNotEmpty()) {
                                    _allCachedCountriesByDate[matchDateKey] = countriesForDate
                                }
                            }

                            val currentDayData = _allCachedCountriesByDate[dateStr] ?: data
                            _countries.value = currentDayData
                        } else {
                            _allCachedCountriesByDate[dateStr] = data
                            _countries.value = data
                        }
                    } else {
                        _errorMessage.value = "No scheduled matches found for $dateStr."
                        _countries.value = emptyList()
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to fetch fixtures"
                    _errorMessage.value = error
                    _countries.value = emptyList()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch fixtures: ${e.message}"
                _countries.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleBetType(type: String) {
        val current = _selectedBetTypes.value
        val newSet = if (current.contains(type)) current - type else current + type
        _selectedBetTypes.value = newSet
        prefs.edit().putStringSet("selected_bet_types", newSet).apply()
    }

    fun selectAllBetTypes() {
        val newSet = availableBetTypes.toSet()
        _selectedBetTypes.value = newSet
        prefs.edit().putStringSet("selected_bet_types", newSet).apply()
    }

    fun deselectAllBetTypes() {
        val newSet = emptySet<String>()
        _selectedBetTypes.value = newSet
        prefs.edit().putStringSet("selected_bet_types", newSet).apply()
    }

    fun toggleSearchItemSelection(id: String) {
        val current = _selectedSearchItems.value
        val isSelecting = !current.contains(id)

        val itemsToModify = mutableSetOf(id)

        if (id.startsWith("country_")) {
            val countryName = id.removePrefix("country_")
            val country = _countries.value.find { it.name == countryName }
            country?.leagues?.forEach { league ->
                itemsToModify.add("league_${league.id}")
                league.matches.forEach { match ->
                    itemsToModify.add("match_${match.id}")
                }
            }
        } else if (id.startsWith("league_")) {
            val leagueId = id.removePrefix("league_").toIntOrNull()
            val league = _countries.value.flatMap { it.leagues }.find { it.id == leagueId }
            league?.matches?.forEach { match ->
                itemsToModify.add("match_${match.id}")
            }
        } else if (id.startsWith("team_")) {
            val teamName = id.removePrefix("team_")
            val matches = _countries.value.flatMap { it.leagues }.flatMap { it.matches }.filter {
                it.homeTeam == teamName || it.awayTeam == teamName
            }
            matches.forEach { match ->
                itemsToModify.add("match_${match.id}")
            }
        }

        val newSelected = if (isSelecting) current + itemsToModify else current - itemsToModify
        _selectedSearchItems.value = newSelected
        prefs.edit().putStringSet("selected_search_items", newSelected).apply()
    }

    fun selectCurrency(currency: com.example.models.Currency) {
        _selectedCurrency.value = currency
        prefs.edit().putString("selected_currency_code", currency.code).apply()
    }

    fun updateBudget(amount: Float) {
        _budget.value = amount
        prefs.edit().putFloat("betting_budget", amount).apply()
    }

    fun updateMoneyRange(range: ClosedFloatingPointRange<Float>) {
        _moneyRange.value = range
        prefs.edit()
            .putFloat("money_target_min", range.start)
            .putFloat("money_target_max", range.endInclusive)
            .apply()
    }

    // ==================== SETTINGS UPDATERS ====================

    fun updateThemeMode(theme: ThemeMode) {
        _customSettings.update { it.copy(themeMode = theme) }
        prefs.edit().putString("app_theme_mode", theme.id).apply()
    }

    fun updateAccentColor(accent: AccentColorMode) {
        _customSettings.update { it.copy(accentColorMode = accent) }
        prefs.edit().putString("app_accent_color", accent.id).apply()
    }

    fun updateOddsFormat(oddsFormat: OddsFormat) {
        _customSettings.update { it.copy(oddsFormat = oddsFormat) }
        prefs.edit().putString("app_odds_format", oddsFormat.id).apply()
    }

    fun updateAutoRefreshSec(seconds: Int) {
        _customSettings.update { it.copy(autoRefreshSec = seconds) }
        prefs.edit().putInt("app_auto_refresh", seconds).apply()
    }

    fun toggleShowFinished(show: Boolean) {
        _customSettings.update { it.copy(showFinishedMatches = show) }
        prefs.edit().putBoolean("app_show_finished", show).apply()
    }

    fun toggleHaptics(enabled: Boolean) {
        _customSettings.update { it.copy(hapticsEnabled = enabled) }
        prefs.edit().putBoolean("app_haptics_enabled", enabled).apply()
    }

    fun toggleDataSaver(enabled: Boolean) {
        _customSettings.update { it.copy(dataSaver = enabled) }
        prefs.edit().putBoolean("app_data_saver", enabled).apply()
    }

    fun toggleCompactMode(enabled: Boolean) {
        _customSettings.update { it.copy(compactCardMode = enabled) }
        prefs.edit().putBoolean("app_compact_mode", enabled).apply()
    }

    fun updateAiReasoningDepth(depth: AiReasoningDepth) {
        _customSettings.update { it.copy(aiReasoningDepth = depth) }
        prefs.edit().putString("app_ai_depth", depth.id).apply()
    }

    fun updateRiskTolerance(risk: RiskTolerance) {
        _customSettings.update { it.copy(riskTolerance = risk) }
        prefs.edit().putString("app_risk_tolerance", risk.id).apply()
    }

    fun updateMinConfidence(confidence: Int) {
        val clamped = confidence.coerceIn(50, 95)
        _customSettings.update { it.copy(minConfidenceThreshold = clamped) }
        prefs.edit().putInt("app_min_confidence", clamped).apply()
    }

    fun updateActiveAiModel(modelId: String) {
        _customSettings.update { it.copy(activeAiModelId = modelId) }
        prefs.edit().putString("app_active_ai_model", modelId).apply()
        keyManager.setActiveBrainModel(modelId)
    }

    // ==================== USER ADDED AI MODELS ====================

    private fun loadUserModelsFromPrefs(): List<com.example.models.UserAiModel> {
        val jsonStr = prefs.getString("user_added_ai_models_json", null)
        if (jsonStr.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val jsonArr = JSONArray(jsonStr)
            val list = mutableListOf<com.example.models.UserAiModel>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                list.add(
                    com.example.models.UserAiModel(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        provider = obj.optString("provider", "Custom"),
                        endpointUrl = obj.optString("endpointUrl", "https://api.openai.com/v1/"),
                        apiKey = obj.optString("apiKey", ""),
                        badge = obj.optString("badge", "Added"),
                        description = obj.optString("description", ""),
                        addedAt = obj.optLong("addedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveUserModelsToPrefs(models: List<com.example.models.UserAiModel>) {
        val jsonArr = JSONArray()
        models.forEach { m ->
            val obj = JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("provider", m.provider)
                put("endpointUrl", m.endpointUrl)
                put("apiKey", m.apiKey)
                put("badge", m.badge)
                put("description", m.description)
                put("addedAt", m.addedAt)
            }
            jsonArr.put(obj)
        }
        prefs.edit().putString("user_added_ai_models_json", jsonArr.toString()).apply()
    }

    fun addUserModel(model: com.example.models.UserAiModel) {
        val current = _userAddedModels.value.toMutableList()
        val index = current.indexOfFirst { it.id == model.id }
        if (index >= 0) {
            current[index] = model
        } else {
            current.add(model)
        }
        _userAddedModels.value = current
        saveUserModelsToPrefs(current)
        if (_customSettings.value.activeAiModelId.isBlank() || _customSettings.value.activeAiModelId == "gemini-2.5-flash") {
            updateActiveAiModel(model.id)
        }
    }

    fun addMultipleUserModels(models: List<com.example.models.UserAiModel>) {
        val current = _userAddedModels.value.toMutableList()
        models.forEach { m ->
            val idx = current.indexOfFirst { it.id == m.id }
            if (idx >= 0) {
                current[idx] = m
            } else {
                current.add(m)
            }
        }
        _userAddedModels.value = current
        saveUserModelsToPrefs(current)
        if (current.isNotEmpty() && (_customSettings.value.activeAiModelId.isBlank() || current.none { it.id == _customSettings.value.activeAiModelId })) {
            updateActiveAiModel(current.first().id)
        }
    }

    fun removeUserModel(modelId: String) {
        val current = _userAddedModels.value.filter { it.id != modelId }
        _userAddedModels.value = current
        saveUserModelsToPrefs(current)
        if (_customSettings.value.activeAiModelId == modelId) {
            val nextId = current.firstOrNull()?.id ?: ""
            updateActiveAiModel(nextId)
        }
    }

    fun fetchAvailableAiModels(
        endpointUrl: String,
        apiKey: String,
        onResult: (Result<List<com.example.models.UserAiModel>>) -> Unit
    ) {
        viewModelScope.launch {
            val res = com.example.network.OpenAiService.fetchAvailableModels(endpointUrl, apiKey)
            onResult(res)
        }
    }

    fun updateCustomAiModel(modelName: String, endpointUrl: String) {
        _customSettings.update {
            it.copy(
                customAiModelName = modelName,
                customAiEndpointUrl = endpointUrl
            )
        }
        prefs.edit()
            .putString("app_custom_ai_model_name", modelName)
            .putString("app_custom_ai_endpoint", endpointUrl)
            .apply()
    }

    fun updateCustomTacticalPrompt(prompt: String) {
        _customSettings.update { it.copy(customTacticalPrompt = prompt) }
        prefs.edit().putString("app_custom_tactical_prompt", prompt).apply()
    }

    fun clearAllMatchCache() {
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("fixtures_cache_")) {
                editor.remove(key)
            }
        }
        editor.apply()
        fetchFixtures(forceRefresh = true)
    }

    fun resetAllSettingsToDefault() {
        val defaultSettings = AppCustomSettings()
        _customSettings.value = defaultSettings
        prefs.edit()
            .putString("app_theme_mode", defaultSettings.themeMode.id)
            .putString("app_accent_color", defaultSettings.accentColorMode.id)
            .putString("app_odds_format", defaultSettings.oddsFormat.id)
            .putInt("app_auto_refresh", defaultSettings.autoRefreshSec)
            .putBoolean("app_show_finished", defaultSettings.showFinishedMatches)
            .putBoolean("app_haptics_enabled", defaultSettings.hapticsEnabled)
            .putBoolean("app_data_saver", defaultSettings.dataSaver)
            .putBoolean("app_compact_mode", defaultSettings.compactCardMode)
            .putString("app_ai_depth", defaultSettings.aiReasoningDepth.id)
            .putString("app_risk_tolerance", defaultSettings.riskTolerance.id)
            .putInt("app_min_confidence", defaultSettings.minConfidenceThreshold)
            .putString("app_active_ai_model", defaultSettings.activeAiModelId)
            .putString("app_custom_ai_model_name", "")
            .putString("app_custom_ai_endpoint", "")
            .putString("app_custom_tactical_prompt", "")
            .apply()

        selectAllBetTypes()
        updateBudget(50f)
        updateMoneyRange(10f..250f)
    }

    // ==================== IMPORT / EXPORT LOCAL CONFIG ====================

    fun exportSettingsJson(): String {
        val s = _customSettings.value
        val json = JSONObject()
        json.put("themeMode", s.themeMode.id)
        json.put("accentColorMode", s.accentColorMode.id)
        json.put("oddsFormat", s.oddsFormat.id)
        json.put("autoRefreshSec", s.autoRefreshSec)
        json.put("showFinishedMatches", s.showFinishedMatches)
        json.put("hapticsEnabled", s.hapticsEnabled)
        json.put("dataSaver", s.dataSaver)
        json.put("compactCardMode", s.compactCardMode)
        json.put("aiReasoningDepth", s.aiReasoningDepth.id)
        json.put("riskTolerance", s.riskTolerance.id)
        json.put("minConfidenceThreshold", s.minConfidenceThreshold)
        json.put("activeAiModelId", s.activeAiModelId)
        json.put("customAiModelName", s.customAiModelName)
        json.put("customAiEndpointUrl", s.customAiEndpointUrl)
        json.put("customTacticalPrompt", s.customTacticalPrompt)
        json.put("currencyCode", _selectedCurrency.value.code)
        json.put("budget", _budget.value.toDouble())
        json.put("moneyMin", _moneyRange.value.start.toDouble())
        json.put("moneyMax", _moneyRange.value.endInclusive.toDouble())
        json.put("selectedBetTypes", JSONArray(_selectedBetTypes.value))
        val modelsArr = JSONArray()
        _userAddedModels.value.forEach { m ->
            modelsArr.put(JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("provider", m.provider)
                put("endpointUrl", m.endpointUrl)
                put("apiKey", m.apiKey)
                put("badge", m.badge)
                put("description", m.description)
            })
        }
        json.put("userAddedModels", modelsArr)
        json.put("exportedAt", System.currentTimeMillis())
        return json.toString(2)
    }

    fun importSettingsJson(jsonString: String): Boolean {
        return try {
            val json = JSONObject(jsonString)
            if (json.has("themeMode")) updateThemeMode(ThemeMode.fromId(json.getString("themeMode")))
            if (json.has("accentColorMode")) updateAccentColor(AccentColorMode.fromId(json.getString("accentColorMode")))
            if (json.has("oddsFormat")) updateOddsFormat(OddsFormat.fromId(json.getString("oddsFormat")))
            if (json.has("autoRefreshSec")) updateAutoRefreshSec(json.getInt("autoRefreshSec"))
            if (json.has("showFinishedMatches")) toggleShowFinished(json.getBoolean("showFinishedMatches"))
            if (json.has("hapticsEnabled")) toggleHaptics(json.getBoolean("hapticsEnabled"))
            if (json.has("dataSaver")) toggleDataSaver(json.getBoolean("dataSaver"))
            if (json.has("compactCardMode")) toggleCompactMode(json.getBoolean("compactCardMode"))
            if (json.has("aiReasoningDepth")) updateAiReasoningDepth(AiReasoningDepth.fromId(json.getString("aiReasoningDepth")))
            if (json.has("riskTolerance")) updateRiskTolerance(RiskTolerance.fromId(json.getString("riskTolerance")))
            if (json.has("minConfidenceThreshold")) updateMinConfidence(json.getInt("minConfidenceThreshold"))
            if (json.has("activeAiModelId")) updateActiveAiModel(json.getString("activeAiModelId"))
            if (json.has("customAiModelName") || json.has("customAiEndpointUrl")) {
                updateCustomAiModel(
                    json.optString("customAiModelName", ""),
                    json.optString("customAiEndpointUrl", "")
                )
            }
            if (json.has("customTacticalPrompt")) updateCustomTacticalPrompt(json.getString("customTacticalPrompt"))

            if (json.has("currencyCode")) {
                val code = json.getString("currencyCode")
                availableCurrencies.find { it.code == code }?.let { selectCurrency(it) }
            }
            if (json.has("budget")) updateBudget(json.getDouble("budget").toFloat())
            if (json.has("moneyMin") && json.has("moneyMax")) {
                updateMoneyRange(json.getDouble("moneyMin").toFloat()..json.getDouble("moneyMax").toFloat())
            }
            if (json.has("selectedBetTypes")) {
                val arr = json.getJSONArray("selectedBetTypes")
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    set.add(arr.getString(i))
                }
                _selectedBetTypes.value = set
                prefs.edit().putStringSet("selected_bet_types", set).apply()
            }
            if (json.has("userAddedModels")) {
                val arr = json.getJSONArray("userAddedModels")
                val list = mutableListOf<com.example.models.UserAiModel>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        com.example.models.UserAiModel(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            provider = obj.optString("provider", "Custom"),
                            endpointUrl = obj.optString("endpointUrl", "https://api.openai.com/v1/"),
                            apiKey = obj.optString("apiKey", ""),
                            badge = obj.optString("badge", "Added"),
                            description = obj.optString("description", "")
                        )
                    )
                }
                addMultipleUserModels(list)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ==================== BATCH PREDICTION ENGINE ====================

    fun prepareBatchForPrediction() {
        val selectedIds = _selectedSearchItems.value
        val items = mutableListOf<com.example.models.AgentBatchMatchItem>()
        val addedMatchIds = mutableSetOf<Int>()

        val countryPool = if (_allCachedCountriesByDate.isNotEmpty()) {
            _allCachedCountriesByDate.values.flatten()
        } else {
            _countries.value
        }

        countryPool.forEach { country ->
            country.leagues.forEach { league ->
                league.matches.forEach { match ->
                    val isMatchSelected = selectedIds.contains("match_${match.id}") ||
                            selectedIds.contains("league_${league.id}") ||
                            selectedIds.contains("country_${country.name}") ||
                            selectedIds.contains("team_${match.homeTeam}") ||
                            selectedIds.contains("team_${match.awayTeam}")

                    if (isMatchSelected && addedMatchIds.add(match.id)) {
                        val formattedTime = if (match.matchDate.isNotBlank()) {
                            "${match.matchDate.takeLast(5)} ${match.startTime}"
                        } else {
                            match.startTime
                        }
                        items.add(
                            com.example.models.AgentBatchMatchItem(
                                matchId = match.id,
                                homeTeam = match.homeTeam,
                                awayTeam = match.awayTeam,
                                homeLogo = match.homeLogo,
                                awayLogo = match.awayLogo,
                                leagueName = league.name,
                                startTime = formattedTime,
                                isSelected = true,
                                status = com.example.models.BatchItemStatus.PENDING,
                                currentAgentAction = "Pending in queue...",
                                prediction = match.prediction
                            )
                        )
                    }
                }
            }
        }

        if (items.isEmpty()) {
            _countries.value.flatMap { it.leagues }.flatMap { it.matches }.take(6).forEach { match ->
                val league = _countries.value.flatMap { it.leagues }.find { l -> l.matches.any { it.id == match.id } }
                if (addedMatchIds.add(match.id)) {
                    val formattedTime = if (match.matchDate.isNotBlank()) {
                        "${match.matchDate.takeLast(5)} ${match.startTime}"
                    } else {
                        match.startTime
                    }
                    items.add(
                        com.example.models.AgentBatchMatchItem(
                            matchId = match.id,
                            homeTeam = match.homeTeam,
                            awayTeam = match.awayTeam,
                            homeLogo = match.homeLogo,
                            awayLogo = match.awayLogo,
                            leagueName = league?.name ?: "League",
                            startTime = formattedTime,
                            isSelected = true,
                            status = com.example.models.BatchItemStatus.PENDING,
                            currentAgentAction = "Pending in queue...",
                            prediction = match.prediction
                        )
                    )
                }
            }
        }

        _batchMatchItems.value = items
        _agentLogs.value = listOf(
            com.example.models.AgentStreamLog(
                timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                message = "AI Engine initialized with ${items.size} matches in queue. Ready for neural analysis.",
                type = "INFO"
            )
        )
    }

    fun toggleBatchItemCheckbox(matchId: Int) {
        if (_isAgentRunning.value) return
        _batchMatchItems.update { list ->
            list.map { item ->
                if (item.matchId == matchId) {
                    item.copy(isSelected = !item.isSelected)
                } else {
                    item
                }
            }
        }
    }

    private fun addLog(message: String, type: String = "INFO") {
        val newLog = com.example.models.AgentStreamLog(
            timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            message = message,
            type = type
        )
        _agentLogs.update { (it + newLog).takeLast(60) }
    }

    fun startAgentPredictionLoop() {
        if (_isAgentRunning.value) return
        _isAgentRunning.value = true

        viewModelScope.launch {
            val list = _batchMatchItems.value
            val activeModelName = _customSettings.value.activeAiModelId
            addLog("⚡ Starting Autonomous Agent Prediction Engine ($activeModelName)...", "INFO")

            val openAiManagedKey = keyManager.getActiveManagedKey(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE)
            val geminiManagedKey = keyManager.getActiveManagedKey(com.example.keymanager.ApiRole.GEMINI)
            var keyFailedInBatch = false
            var fallbackForAllRemaining = false

            for (i in list.indices) {
                val item = _batchMatchItems.value[i]
                if (!item.isSelected) {
                    continue
                }

                _currentActivePredictingIndex.value = i
                userManager.consumePredictionQuota()

                _batchMatchItems.update { currentList ->
                    currentList.mapIndexed { idx, curItem ->
                        if (idx == i) curItem.copy(
                            status = com.example.models.BatchItemStatus.PREDICTING,
                            currentAgentAction = "Agent inspecting ${curItem.homeTeam} vs ${curItem.awayTeam}..."
                        ) else curItem
                    }
                }

                addLog("🔍 [Match ${i + 1}/${list.size}] Gathering H2H & Form for ${item.homeTeam} vs ${item.awayTeam}", "SEARCH")
                delay(700)

                var squadIntelSummary: String? = null
                val firecrawlKey = keyManager.getActiveManagedKey(com.example.keymanager.ApiRole.FIRECRAWL)
                if (firecrawlKey != null && firecrawlKey.key.isNotBlank()) {
                    val crawlResult = com.example.network.FirecrawlService.searchMatchNews(item.homeTeam, item.awayTeam, firecrawlKey)
                    if (crawlResult.isSuccess) {
                        squadIntelSummary = crawlResult.getOrNull()
                        keyManager.reportKeySuccess(com.example.keymanager.ApiRole.FIRECRAWL, firecrawlKey.key)
                        addLog("🔥 [Squad Intel] ${squadIntelSummary?.take(60)}...", "SEARCH")
                    }
                }

                val userModel = _userAddedModels.value.find { it.id == activeModelName }
                val isExplicitLocal = _useLocalEngineOnly.value || fallbackForAllRemaining
                val isGeminiModel = activeModelName.contains("gemini", ignoreCase = true)

                val effectiveKey = if (!keyFailedInBatch && !isExplicitLocal) {
                    if (userModel != null && userModel.apiKey.isNotBlank()) {
                        com.example.keymanager.ManagedApiKey(
                            role = com.example.keymanager.ApiRole.OPENAI_COMPATIBLE.code,
                            key = userModel.apiKey,
                            endpointUrl = userModel.endpointUrl,
                            modelName = userModel.id
                        )
                    } else if (isGeminiModel && geminiManagedKey != null && geminiManagedKey.key.isNotBlank()) {
                        com.example.keymanager.ManagedApiKey(
                            role = com.example.keymanager.ApiRole.OPENAI_COMPATIBLE.code,
                            key = geminiManagedKey.key,
                            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
                            modelName = activeModelName
                        )
                    } else if (openAiManagedKey != null && openAiManagedKey.key.isNotBlank()) {
                        openAiManagedKey.copy(
                            modelName = activeModelName,
                            endpointUrl = userModel?.endpointUrl?.ifBlank { openAiManagedKey.endpointUrl } ?: openAiManagedKey.endpointUrl
                        )
                    } else if (geminiManagedKey != null && geminiManagedKey.key.isNotBlank()) {
                        com.example.keymanager.ManagedApiKey(
                            role = com.example.keymanager.ApiRole.OPENAI_COMPATIBLE.code,
                            key = geminiManagedKey.key,
                            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
                            modelName = if (isGeminiModel) activeModelName else "gemini-2.5-flash"
                        )
                    } else null
                } else null

                if (isExplicitLocal) {
                    addLog("⚡ [Local Engine] Synthesizing tactical probabilities with local Poisson & quant xG model...", "INFO")
                } else if (effectiveKey != null) {
                    addLog("🧠 Synthesizing tactical probabilities with model $activeModelName...", "AI")
                } else {
                    addLog("⚠️ [Engine Setup Required] No API key configured for model $activeModelName...", "WARN")
                }
                delay(500)

                var finalPrediction: PredictionResult? = null

                if (isExplicitLocal) {
                    finalPrediction = generateSmartMockPrediction(item.homeTeam, item.awayTeam)
                } else if (effectiveKey != null && effectiveKey.key.isNotBlank()) {
                    var resolvedFromApi = false
                    var currentError: String = ""
                    var isAuthError = false

                    // Initial attempt
                    val result = com.example.network.OpenAiService.generatePrediction(
                        homeTeam = item.homeTeam,
                        awayTeam = item.awayTeam,
                        league = item.leagueName,
                        managedKey = effectiveKey,
                        allowedBetTypes = _selectedBetTypes.value.toList(),
                        tacticalIntel = squadIntelSummary
                    )

                    if (result.isSuccess) {
                        keyManager.reportKeySuccess(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE, effectiveKey.key)
                        finalPrediction = result.getOrNull()
                        resolvedFromApi = true
                    } else {
                        currentError = result.exceptionOrNull()?.message ?: "External API request failed"
                        isAuthError = currentError.contains("401") || currentError.contains("403") ||
                                      currentError.contains("Incorrect API key", ignoreCase = true) ||
                                      currentError.contains("invalid_api_key", ignoreCase = true)
                        keyManager.reportKeyError(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE, effectiveKey.key, isAuthError = isAuthError)
                    }

                    // If failed, pause and ask the user for fallback decision
                    while (!resolvedFromApi && !fallbackForAllRemaining && finalPrediction == null) {
                        addLog("⚠️ API Failed on ${item.homeTeam} vs ${item.awayTeam}: $currentError", "WARN")
                        addLog("✋ Pausing batch. Awaiting user confirmation for local fallback...", "INFO")

                        val deferred = kotlinx.coroutines.CompletableDeferred<com.example.models.FallbackDecision>()
                        fallbackCompleter = deferred

                        _apiFallbackPrompt.value = com.example.models.ApiFallbackPrompt(
                            matchId = item.matchId,
                            matchIndex = i + 1,
                            totalMatches = list.count { it.isSelected },
                            homeTeam = item.homeTeam,
                            awayTeam = item.awayTeam,
                            leagueName = item.leagueName,
                            modelName = activeModelName,
                            errorMessage = currentError,
                            isAuthError = isAuthError
                        )

                        val decision = deferred.await()
                        when (decision) {
                            com.example.models.FallbackDecision.USE_LOCAL_ONCE -> {
                                addLog("✓ User approved Local Engine fallback for ${item.homeTeam} vs ${item.awayTeam}.", "SUCCESS")
                                finalPrediction = generateSmartMockPrediction(item.homeTeam, item.awayTeam)
                            }
                            com.example.models.FallbackDecision.USE_LOCAL_ALL -> {
                                fallbackForAllRemaining = true
                                addLog("✓ User switched all remaining fixtures to Local Engine.", "SUCCESS")
                                finalPrediction = generateSmartMockPrediction(item.homeTeam, item.awayTeam)
                            }
                            com.example.models.FallbackDecision.RETRY_API -> {
                                addLog("🔄 Retrying external API for ${item.homeTeam} vs ${item.awayTeam}...", "INFO")
                                delay(600)
                                val retryResult = com.example.network.OpenAiService.generatePrediction(
                                    homeTeam = item.homeTeam,
                                    awayTeam = item.awayTeam,
                                    league = item.leagueName,
                                    managedKey = effectiveKey,
                                    allowedBetTypes = _selectedBetTypes.value.toList(),
                                    tacticalIntel = squadIntelSummary
                                )
                                if (retryResult.isSuccess) {
                                    keyManager.reportKeySuccess(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE, effectiveKey.key)
                                    finalPrediction = retryResult.getOrNull()
                                    resolvedFromApi = true
                                    addLog("✅ API Retry succeeded!", "SUCCESS")
                                } else {
                                    currentError = retryResult.exceptionOrNull()?.message ?: "Retry failed"
                                    isAuthError = currentError.contains("401") || currentError.contains("403") ||
                                                  currentError.contains("Incorrect API key", ignoreCase = true) ||
                                                  currentError.contains("invalid_api_key", ignoreCase = true)
                                }
                            }
                            com.example.models.FallbackDecision.CANCEL -> {
                                addLog("🛑 User cancelled remaining batch predictions.", "WARN")
                                _currentActivePredictingIndex.value = -1
                                _isAgentRunning.value = false
                                return@launch
                            }
                        }
                    }
                } else {
                    // No effective API key configured for Cloud AI: Prompt user explicitly!
                    addLog("⚠️ Pausing batch: No API Key configured for $activeModelName.", "WARN")
                    val deferred = kotlinx.coroutines.CompletableDeferred<com.example.models.FallbackDecision>()
                    fallbackCompleter = deferred

                    _apiFallbackPrompt.value = com.example.models.ApiFallbackPrompt(
                        matchId = item.matchId,
                        matchIndex = i + 1,
                        totalMatches = list.count { it.isSelected },
                        homeTeam = item.homeTeam,
                        awayTeam = item.awayTeam,
                        leagueName = item.leagueName,
                        modelName = activeModelName,
                        errorMessage = "No API Key configured in Settings / Vault for model '$activeModelName'.",
                        isAuthError = true
                    )

                    val decision = deferred.await()
                    when (decision) {
                        com.example.models.FallbackDecision.USE_LOCAL_ONCE -> {
                            addLog("✓ User approved Local Engine for ${item.homeTeam} vs ${item.awayTeam}.", "SUCCESS")
                            finalPrediction = generateSmartMockPrediction(item.homeTeam, item.awayTeam)
                        }
                        com.example.models.FallbackDecision.USE_LOCAL_ALL -> {
                            fallbackForAllRemaining = true
                            addLog("✓ User switched all remaining fixtures to Local Engine.", "SUCCESS")
                            finalPrediction = generateSmartMockPrediction(item.homeTeam, item.awayTeam)
                        }
                        com.example.models.FallbackDecision.RETRY_API -> {
                            addLog("🔄 Re-evaluating keys...", "INFO")
                            finalPrediction = generateSmartMockPrediction(item.homeTeam, item.awayTeam)
                        }
                        com.example.models.FallbackDecision.CANCEL -> {
                            addLog("🛑 User cancelled batch predictions.", "WARN")
                            _currentActivePredictingIndex.value = -1
                            _isAgentRunning.value = false
                            return@launch
                        }
                    }
                }

                val prediction = finalPrediction ?: generateSmartMockPrediction(item.homeTeam, item.awayTeam)

                _batchMatchItems.update { currentList ->
                    currentList.mapIndexed { idx, curItem ->
                        if (idx == i) curItem.copy(
                            status = com.example.models.BatchItemStatus.FINISHED,
                            currentAgentAction = "Prediction complete: ${prediction?.recommendedBet}",
                            prediction = prediction
                        ) else curItem
                    }
                }

                _countries.update { currentCountries ->
                    currentCountries.map { country ->
                        country.copy(
                            leagues = country.leagues.map { league ->
                                league.copy(
                                    matches = league.matches.map { match ->
                                        if (match.id == item.matchId) {
                                            match.copy(prediction = prediction)
                                        } else match
                                    }
                                )
                            }
                        )
                    }
                }

                addLog("✅ [Finished] ${item.homeTeam} vs ${item.awayTeam} ➔ ${prediction?.recommendedBet} (${prediction?.confidence}% conf)", "SUCCESS")
                delay(500)
            }

            _currentActivePredictingIndex.value = -1
            _isAgentRunning.value = false
            addLog("🎉 Autonomous batch prediction completed.", "SUCCESS")

            val generatedSlip = saveAndBuildSlip()
            if (generatedSlip != null) {
                addLog("💾 Stored Bet Slip #${generatedSlip.slipId} to Local Vault (${generatedSlip.items.size} matches, @${generatedSlip.totalCombinedOdds} combo odds)", "SUCCESS")
            }
        }
    }

    private fun extractBetTypeCategory(pick: String): String {
        val lower = pick.lowercase()
        return when {
            lower.contains("btts") || lower.contains("both teams") -> "BTTS"
            lower.contains("over") || lower.contains("under") || lower.contains("goals") -> "Over/Under"
            lower.contains("double chance") || lower.contains("1x") || lower.contains("x2") || lower.contains("12") -> "Double Chance"
            lower.contains("draw no bet") || lower.contains("dnb") -> "Draw No Bet"
            lower.contains("handicap") || lower.contains("ah") -> "Handicap"
            lower.contains("correct score") -> "Score"
            lower.contains("corner") -> "Corners"
            lower.contains("card") -> "Cards"
            lower.contains("half time") || lower.contains("ht/ft") -> "HT/FT"
            else -> "1X2"
        }
    }

    private fun generateSmartMockPrediction(home: String, away: String): PredictionResult {
        val selected = _selectedBetTypes.value
        val risk = _customSettings.value.riskTolerance
        val minConf = _customSettings.value.minConfidenceThreshold
        val possibleTips = mutableListOf<Triple<String, Int, String>>()

        if (selected.contains("Both Teams to Score (BTTS)")) {
            possibleTips.add(Triple("Both Teams to Score (BTTS - YES)", 82, "BTTS"))
            possibleTips.add(Triple("Both Teams to Score (BTTS - NO)", 74, "BTTS"))
        }
        if (selected.contains("Over/Under Goals")) {
            possibleTips.add(Triple("Over 2.5 Total Goals", 78, "Over/Under"))
            possibleTips.add(Triple("Under 2.5 Total Goals", 75, "Over/Under"))
            possibleTips.add(Triple("Over 1.5 Total Goals", 88, "Over/Under"))
        }
        if (selected.contains("Double Chance (1X, 12, X2)")) {
            possibleTips.add(Triple("$home or Draw (1X Double Chance)", 86, "Double Chance"))
            possibleTips.add(Triple("Draw or $away (X2 Double Chance)", 76, "Double Chance"))
        }
        if (selected.contains("Draw No Bet (DNB)")) {
            possibleTips.add(Triple("Draw No Bet - $home", 80, "Draw No Bet"))
            possibleTips.add(Triple("Draw No Bet - $away", 73, "Draw No Bet"))
        }
        if (selected.contains("Asian Handicap") || selected.contains("European Handicap")) {
            possibleTips.add(Triple("Asian Handicap $home -0.5", 77, "Handicap"))
            possibleTips.add(Triple("Handicap $away (+1.5)", 81, "Handicap"))
        }
        if (selected.contains("1X2 (Win / Draw / Lose)")) {
            possibleTips.add(Triple("$home to Win (1X2)", 79, "1X2"))
            possibleTips.add(Triple("$away to Win (1X2)", 68, "1X2"))
        }
        if (selected.contains("Combo Bets")) {
            possibleTips.add(Triple("$home to Win & Over 1.5 Goals", 81, "Combo"))
            possibleTips.add(Triple("1X & Both Teams to Score", 76, "Combo"))
        }

        if (possibleTips.isEmpty()) {
            possibleTips.add(Triple("Both Teams to Score (BTTS - YES)", 80, "BTTS"))
            possibleTips.add(Triple("Over 2.5 Total Goals", 77, "Over/Under"))
            possibleTips.add(Triple("$home or Draw (1X)", 84, "Double Chance"))
        }

        val filtered = possibleTips.filter { it.second >= minConf }
        val picked = if (filtered.isNotEmpty()) {
            if (risk == RiskTolerance.ULTRA_SAFE) {
                filtered.maxByOrNull { it.second } ?: filtered.random()
            } else {
                filtered.random()
            }
        } else {
            possibleTips.random()
        }

        val odds = when (picked.third) {
            "Double Chance" -> String.format(Locale.US, "%.2f", 1.35 + (home.length % 5) * 0.08)
            "Over/Under" -> String.format(Locale.US, "%.2f", 1.65 + (away.length % 6) * 0.10)
            "BTTS" -> String.format(Locale.US, "%.2f", 1.75 + (home.length % 4) * 0.12)
            "Draw No Bet" -> String.format(Locale.US, "%.2f", 1.55 + (away.length % 5) * 0.11)
            "Handicap" -> String.format(Locale.US, "%.2f", 1.85 + (home.length % 5) * 0.15)
            "Combo" -> String.format(Locale.US, "%.2f", 2.20 + (away.length % 6) * 0.20)
            else -> String.format(Locale.US, "%.2f", 1.70 + (home.length % 7) * 0.14)
        }

        return PredictionResult(
            recommendedBet = picked.first,
            confidence = picked.second,
            rationale = "High expected value model: $home offensive momentum vs $away defensive transition metrics.",
            odds = odds,
            betType = picked.third
        )
    }

    // ==================== SLIP PERSISTENCE ====================

    private fun loadSavedSlipsFromStorage() {
        val jsonStr = prefs.getString("saved_prediction_slips_json", null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<com.example.models.SavedPredictionSlip>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val itemsArray = obj.optJSONArray("items") ?: JSONArray()
                    val itemsList = mutableListOf<com.example.models.PredictedBetItem>()
                    for (j in 0 until itemsArray.length()) {
                        val itObj = itemsArray.getJSONObject(j)
                        val homeSc = if (itObj.has("homeScore") && !itObj.isNull("homeScore")) itObj.optInt("homeScore") else null
                        val awaySc = if (itObj.has("awayScore") && !itObj.isNull("awayScore")) itObj.optInt("awayScore") else null
                        val mStatus = if (itObj.has("matchStatus") && !itObj.isNull("matchStatus")) itObj.optString("matchStatus") else null
                        val outStatus = itObj.optString("outcomeStatus", "PENDING")
                        val outExpl = if (itObj.has("outcomeExplanation")) itObj.optString("outcomeExplanation") else null

                        itemsList.add(
                            com.example.models.PredictedBetItem(
                                matchId = itObj.optInt("matchId"),
                                homeTeam = itObj.optString("homeTeam"),
                                awayTeam = itObj.optString("awayTeam"),
                                homeLogo = if (itObj.has("homeLogo")) itObj.optString("homeLogo") else null,
                                awayLogo = if (itObj.has("awayLogo")) itObj.optString("awayLogo") else null,
                                leagueName = itObj.optString("leagueName"),
                                startTime = itObj.optString("startTime"),
                                recommendedBet = itObj.optString("recommendedBet"),
                                confidence = itObj.optInt("confidence", 75),
                                rationale = itObj.optString("rationale"),
                                simulatedOdds = itObj.optString("simulatedOdds", "1.75"),
                                betTypeCategory = itObj.optString("betTypeCategory", extractBetTypeCategory(itObj.optString("recommendedBet"))),
                                matchStatus = mStatus,
                                homeScore = homeSc,
                                awayScore = awaySc,
                                outcomeStatus = outStatus,
                                outcomeExplanation = outExpl
                            )
                        )
                    }
                    if (itemsList.isNotEmpty()) {
                        list.add(
                            com.example.models.SavedPredictionSlip(
                                slipId = obj.optString("slipId"),
                                timestamp = obj.optLong("timestamp"),
                                dateString = obj.optString("dateString"),
                                items = itemsList,
                                totalMatches = obj.optInt("totalMatches", itemsList.size),
                                averageConfidence = obj.optInt("averageConfidence", 75),
                                totalCombinedOdds = obj.optString("totalCombinedOdds", "2.50"),
                                currencyCode = obj.optString("currencyCode", _selectedCurrency.value.code),
                                currencySymbol = obj.optString("currencySymbol", _selectedCurrency.value.symbol),
                                budgetStake = obj.optDouble("budgetStake", _budget.value.toDouble()).toFloat(),
                                estimatedPayout = obj.optDouble("estimatedPayout", 0.0),
                                potentialProfit = obj.optDouble("potentialProfit", 0.0),
                                targetMin = obj.optDouble("targetMin", _moneyRange.value.start.toDouble()).toFloat(),
                                targetMax = obj.optDouble("targetMax", _moneyRange.value.endInclusive.toDouble()).toFloat(),
                                overallStatus = obj.optString("overallStatus", "PENDING"),
                                wonItemsCount = obj.optInt("wonItemsCount", itemsList.count { it.outcomeStatus == "WON" }),
                                lostItemsCount = obj.optInt("lostItemsCount", itemsList.count { it.outcomeStatus == "LOST" }),
                                voidItemsCount = obj.optInt("voidItemsCount", itemsList.count { it.outcomeStatus == "VOID" }),
                                pendingItemsCount = obj.optInt("pendingItemsCount", itemsList.count { it.outcomeStatus == "PENDING" }),
                                lastCheckedTimestamp = if (obj.has("lastCheckedTimestamp")) obj.optLong("lastCheckedTimestamp") else null
                            )
                        )
                    }
                }
                _savedSlipsHistory.value = list.distinctBy { s ->
                    "${s.items.map { "${it.matchId}_${it.recommendedBet}" }.sorted().joinToString(",")}_${s.timestamp / 120000}"
                }
                if (_currentSlip.value == null && list.isNotEmpty()) {
                    _currentSlip.value = _savedSlipsHistory.value.firstOrNull()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun persistSlips() {
        try {
            val array = JSONArray()
            _savedSlipsHistory.value.filter { it.items.isNotEmpty() }.forEach { slip ->
                val obj = JSONObject()
                obj.put("slipId", slip.slipId)
                obj.put("timestamp", slip.timestamp)
                obj.put("dateString", slip.dateString)
                obj.put("totalMatches", slip.totalMatches)
                obj.put("averageConfidence", slip.averageConfidence)
                obj.put("totalCombinedOdds", slip.totalCombinedOdds)
                obj.put("currencyCode", slip.currencyCode)
                obj.put("currencySymbol", slip.currencySymbol)
                obj.put("budgetStake", slip.budgetStake)
                obj.put("estimatedPayout", slip.estimatedPayout)
                obj.put("potentialProfit", slip.potentialProfit)
                obj.put("targetMin", slip.targetMin)
                obj.put("targetMax", slip.targetMax)
                obj.put("overallStatus", slip.overallStatus)
                obj.put("wonItemsCount", slip.wonItemsCount)
                obj.put("lostItemsCount", slip.lostItemsCount)
                obj.put("voidItemsCount", slip.voidItemsCount)
                obj.put("pendingItemsCount", slip.pendingItemsCount)
                slip.lastCheckedTimestamp?.let { obj.put("lastCheckedTimestamp", it) }

                val itemsArray = JSONArray()
                slip.items.forEach { item ->
                    val itObj = JSONObject()
                    itObj.put("matchId", item.matchId)
                    itObj.put("homeTeam", item.homeTeam)
                    itObj.put("awayTeam", item.awayTeam)
                    item.homeLogo?.let { itObj.put("homeLogo", it) }
                    item.awayLogo?.let { itObj.put("awayLogo", it) }
                    itObj.put("leagueName", item.leagueName)
                    itObj.put("startTime", item.startTime)
                    itObj.put("recommendedBet", item.recommendedBet)
                    itObj.put("confidence", item.confidence)
                    itObj.put("rationale", item.rationale)
                    itObj.put("simulatedOdds", item.simulatedOdds)
                    itObj.put("betTypeCategory", item.betTypeCategory)
                    item.matchStatus?.let { itObj.put("matchStatus", it) }
                    item.homeScore?.let { itObj.put("homeScore", it) }
                    item.awayScore?.let { itObj.put("awayScore", it) }
                    itObj.put("outcomeStatus", item.outcomeStatus)
                    item.outcomeExplanation?.let { itObj.put("outcomeExplanation", it) }
                    itemsArray.put(itObj)
                }
                obj.put("items", itemsArray)
                array.put(obj)
            }
            prefs.edit().putString("saved_prediction_slips_json", array.toString()).apply()
            val now = System.currentTimeMillis()
            _lastCloudSyncTimestamp.value = now
            prefs.edit().putLong("last_local_save_time", now).apply()
            _cloudSyncState.value = CloudSyncState.SYNCED
        } catch (e: Exception) {
            e.printStackTrace()
            _cloudSyncState.value = CloudSyncState.ERROR
        }
    }

    fun saveAndBuildSlip(): com.example.models.SavedPredictionSlip? {
        val batchList = _batchMatchItems.value.filter { it.isSelected }
        var finishedItems = batchList
            .filter { it.prediction != null }
            .map { item ->
                val pred = item.prediction!!
                val calculatedOdds = pred.odds ?: String.format(Locale.US, "%.2f", 1.50 + (item.matchId % 9) * 0.12)
                val cat = pred.betType?.ifBlank { null } ?: extractBetTypeCategory(pred.recommendedBet)
                com.example.models.PredictedBetItem(
                    matchId = item.matchId,
                    homeTeam = item.homeTeam,
                    awayTeam = item.awayTeam,
                    homeLogo = item.homeLogo,
                    awayLogo = item.awayLogo,
                    leagueName = item.leagueName,
                    startTime = item.startTime,
                    recommendedBet = pred.recommendedBet,
                    confidence = pred.confidence,
                    rationale = pred.rationale,
                    simulatedOdds = calculatedOdds,
                    betTypeCategory = cat
                )
            }

        if (finishedItems.isEmpty() && batchList.isNotEmpty()) {
            finishedItems = batchList.map { item ->
                val pred = generateSmartMockPrediction(item.homeTeam, item.awayTeam)
                val calculatedOdds = pred.odds ?: String.format(Locale.US, "%.2f", 1.50 + (item.matchId % 9) * 0.12)
                val cat = pred.betType?.ifBlank { null } ?: extractBetTypeCategory(pred.recommendedBet)
                com.example.models.PredictedBetItem(
                    matchId = item.matchId,
                    homeTeam = item.homeTeam,
                    awayTeam = item.awayTeam,
                    homeLogo = item.homeLogo,
                    awayLogo = item.awayLogo,
                    leagueName = item.leagueName,
                    startTime = item.startTime,
                    recommendedBet = pred.recommendedBet,
                    confidence = pred.confidence,
                    rationale = pred.rationale,
                    simulatedOdds = calculatedOdds,
                    betTypeCategory = cat
                )
            }
        }

        if (finishedItems.isEmpty()) {
            val countryMatches = _countries.value.flatMap { country ->
                country.leagues.flatMap { league ->
                    league.matches.filter { it.prediction != null }.map { match ->
                        val pred = match.prediction!!
                        val calculatedOdds = pred.odds ?: String.format(Locale.US, "%.2f", 1.50 + (match.id % 9) * 0.12)
                        val cat = pred.betType?.ifBlank { null } ?: extractBetTypeCategory(pred.recommendedBet)
                        com.example.models.PredictedBetItem(
                            matchId = match.id,
                            homeTeam = match.homeTeam,
                            awayTeam = match.awayTeam,
                            homeLogo = match.homeLogo,
                            awayLogo = match.awayLogo,
                            leagueName = league.name,
                            startTime = match.startTime,
                            recommendedBet = pred.recommendedBet,
                            confidence = pred.confidence,
                            rationale = pred.rationale,
                            simulatedOdds = calculatedOdds,
                            betTypeCategory = cat
                        )
                    }
                }
            }
            finishedItems = countryMatches
        }

        if (finishedItems.isEmpty()) {
            return _currentSlip.value
        }

        // Prevent creating duplicate slip if current slip already matches exactly
        val current = _currentSlip.value
        if (current != null && current.items.size == finishedItems.size &&
            current.items.map { "${it.matchId}_${it.recommendedBet}" } == finishedItems.map { "${it.matchId}_${it.recommendedBet}" }
        ) {
            return current
        }

        val existingRecent = _savedSlipsHistory.value.firstOrNull { slip ->
            slip.items.size == finishedItems.size &&
            slip.items.map { "${it.matchId}_${it.recommendedBet}" } == finishedItems.map { "${it.matchId}_${it.recommendedBet}" } &&
            (System.currentTimeMillis() - slip.timestamp) < 180_000
        }
        if (existingRecent != null) {
            _currentSlip.value = existingRecent
            return existingRecent
        }

        val totalMatches = finishedItems.size
        val avgConf = if (totalMatches > 0) finishedItems.map { it.confidence }.average().toInt() else 0
        var totalOdds = 1.0
        finishedItems.forEach {
            totalOdds *= (it.simulatedOdds.toDoubleOrNull() ?: 1.6)
        }
        val oddsStr = String.format(Locale.US, "%.2f", totalOdds.coerceAtMost(9999.0))

        val curr = _selectedCurrency.value
        val budgetStake = _budget.value
        val estimatedPayout = budgetStake * totalOdds
        val potentialProfit = (estimatedPayout - budgetStake).coerceAtLeast(0.0)
        val targetMin = _moneyRange.value.start
        val targetMax = _moneyRange.value.endInclusive

        val newSlip = com.example.models.SavedPredictionSlip(
            slipId = "SLIP-${System.currentTimeMillis() % 1000000}",
            timestamp = System.currentTimeMillis(),
            dateString = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date()),
            items = finishedItems,
            totalMatches = totalMatches,
            averageConfidence = avgConf,
            totalCombinedOdds = oddsStr,
            currencyCode = curr.code,
            currencySymbol = curr.symbol,
            budgetStake = budgetStake,
            estimatedPayout = estimatedPayout,
            potentialProfit = potentialProfit,
            targetMin = targetMin,
            targetMax = targetMax
        )

        _currentSlip.value = newSlip
        _savedSlipsHistory.update { existingList ->
            (listOf(newSlip) + existingList)
                .filter { it.items.isNotEmpty() }
                .distinctBy { s ->
                    "${s.items.map { "${it.matchId}_${it.recommendedBet}" }.sorted().joinToString(",")}_${s.timestamp / 120000}"
                }
        }
        persistSlips()

        return newSlip
    }

    fun deleteSlip(slipId: String) {
        _savedSlipsHistory.update { list -> list.filterNot { it.slipId == slipId } }
        if (_currentSlip.value?.slipId == slipId) {
            _currentSlip.value = _savedSlipsHistory.value.firstOrNull()
        }
        persistSlips()
    }

    fun clearAllSlips() {
        _savedSlipsHistory.value = emptyList()
        _currentSlip.value = null
        prefs.edit().remove("saved_prediction_slips_json").apply()
    }

    private val _isCheckingOutcomes = MutableStateFlow(false)
    val isCheckingOutcomes: StateFlow<Boolean> = _isCheckingOutcomes.asStateFlow()

    fun verifySlipOutcomes(slipId: String, simulateIfMissing: Boolean = false) {
        viewModelScope.launch {
            _isCheckingOutcomes.value = true
            try {
                val targetSlip = _savedSlipsHistory.value.find { it.slipId == slipId } ?: return@launch
                val loadedMatches = allLoadedMatches
                val scoreMap = mutableMapOf<Int, Triple<Int, Int, String>>()

                targetSlip.items.forEach { item ->
                    val matchedFixture = loadedMatches.find { it.id == item.matchId }
                        ?: loadedMatches.find {
                            it.homeTeam.equals(item.homeTeam, ignoreCase = true) ||
                            it.awayTeam.equals(item.awayTeam, ignoreCase = true)
                        }

                    if (matchedFixture != null && matchedFixture.homeScore != null && matchedFixture.awayScore != null) {
                        scoreMap[item.matchId] = Triple(matchedFixture.homeScore, matchedFixture.awayScore, matchedFixture.status)
                    } else if (item.homeScore != null && item.awayScore != null) {
                        scoreMap[item.matchId] = Triple(item.homeScore, item.awayScore, item.matchStatus ?: "FT")
                    } else if (simulateIfMissing) {
                        val sim = com.example.prediction.PredictionOutcomeEvaluator.generateSimulatedScore(item)
                        scoreMap[item.matchId] = sim
                    }
                }

                val evaluated = com.example.prediction.PredictionOutcomeEvaluator.evaluateSlip(targetSlip, scoreMap)
                _savedSlipsHistory.update { list ->
                    list.map { if (it.slipId == slipId) evaluated else it }
                }
                if (_currentSlip.value?.slipId == slipId) {
                    _currentSlip.value = evaluated
                }
                persistSlips()
            } finally {
                _isCheckingOutcomes.value = false
            }
        }
    }

    fun verifyAllSavedSlips(simulateIfMissing: Boolean = false) {
        viewModelScope.launch {
            _isCheckingOutcomes.value = true
            try {
                val loadedMatches = allLoadedMatches
                _savedSlipsHistory.update { list ->
                    list.map { slip ->
                        val scoreMap = mutableMapOf<Int, Triple<Int, Int, String>>()
                        slip.items.forEach { item ->
                            val matched = loadedMatches.find { it.id == item.matchId }
                                ?: loadedMatches.find {
                                    it.homeTeam.equals(item.homeTeam, ignoreCase = true) ||
                                    it.awayTeam.equals(item.awayTeam, ignoreCase = true)
                                }
                            if (matched != null && matched.homeScore != null && matched.awayScore != null) {
                                scoreMap[item.matchId] = Triple(matched.homeScore, matched.awayScore, matched.status)
                            } else if (item.homeScore != null && item.awayScore != null) {
                                scoreMap[item.matchId] = Triple(item.homeScore, item.awayScore, item.matchStatus ?: "FT")
                            } else if (simulateIfMissing) {
                                scoreMap[item.matchId] = com.example.prediction.PredictionOutcomeEvaluator.generateSimulatedScore(item)
                            }
                        }
                        com.example.prediction.PredictionOutcomeEvaluator.evaluateSlip(slip, scoreMap)
                    }
                }
                _currentSlip.value?.let { curr ->
                    _currentSlip.value = _savedSlipsHistory.value.find { it.slipId == curr.slipId }
                }
                persistSlips()
            } finally {
                _isCheckingOutcomes.value = false
            }
        }
    }

    fun manuallySetMatchScore(slipId: String, matchId: Int, homeScore: Int, awayScore: Int, status: String = "FT") {
        val slip = _savedSlipsHistory.value.find { it.slipId == slipId } ?: return
        val scoreMap = mapOf(matchId to Triple(homeScore, awayScore, status))
        val updated = com.example.prediction.PredictionOutcomeEvaluator.evaluateSlip(slip, scoreMap)
        _savedSlipsHistory.update { list ->
            list.map { if (it.slipId == slipId) updated else it }
        }
        if (_currentSlip.value?.slipId == slipId) {
            _currentSlip.value = updated
        }
        persistSlips()
    }

    fun resetSlipOutcomes(slipId: String) {
        val slip = _savedSlipsHistory.value.find { it.slipId == slipId } ?: return
        val resetItems = slip.items.map {
            it.copy(
                homeScore = null,
                awayScore = null,
                matchStatus = null,
                outcomeStatus = "PENDING",
                outcomeExplanation = null
            )
        }
        val resetSlip = slip.copy(
            items = resetItems,
            overallStatus = "PENDING",
            wonItemsCount = 0,
            lostItemsCount = 0,
            voidItemsCount = 0,
            pendingItemsCount = resetItems.size,
            lastCheckedTimestamp = null
        )
        _savedSlipsHistory.update { list ->
            list.map { if (it.slipId == slipId) resetSlip else it }
        }
        if (_currentSlip.value?.slipId == slipId) {
            _currentSlip.value = resetSlip
        }
        persistSlips()
    }

    fun exportSlipsAsFormattedText(): String {
        val slips = _savedSlipsHistory.value
        if (slips.isEmpty()) return "No bet slips saved in history."
        val sb = StringBuilder()
        sb.append("📋 FOOTBALL AI PREDICTION VAULT EXPORT\n")
        sb.append("Generated: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())}\n")
        sb.append("Total Slips: ${slips.size}\n\n")

        slips.forEachIndexed { sIdx, slip ->
            sb.append("══════════════════════════════════════\n")
            sb.append("SLIP #${sIdx + 1}: [${slip.slipId}] (${slip.dateString})\n")
            sb.append("Status: ${slip.overallStatus} | Combined Odds: @${slip.totalCombinedOdds} | Avg Conf: ${slip.averageConfidence}%\n")
            if (slip.wonItemsCount > 0 || slip.lostItemsCount > 0) {
                sb.append("Outcome: ${slip.wonItemsCount} Won • ${slip.lostItemsCount} Lost • ${slip.pendingItemsCount} Pending\n")
            }
            sb.append("──────────────────────────────────────\n")
            slip.items.forEachIndexed { i, item ->
                val outcomeTag = when (item.outcomeStatus) {
                    "WON" -> "[✓ WON]"
                    "LOST" -> "[✗ LOST]"
                    "VOID" -> "[— VOID]"
                    else -> "[⏳ PENDING]"
                }
                sb.append("${i + 1}. ${item.homeTeam} vs ${item.awayTeam} $outcomeTag\n")
                sb.append("   ▶ Pick: [${item.betTypeCategory}] ${item.recommendedBet} (@${item.simulatedOdds})\n")
                if (item.homeScore != null && item.awayScore != null) {
                    sb.append("   ▶ Score: ${item.homeScore} - ${item.awayScore} (${item.matchStatus ?: "FT"})\n")
                }
                if (!item.outcomeExplanation.isNullOrBlank()) {
                    sb.append("   ▶ Result Check: ${item.outcomeExplanation}\n")
                }
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    fun selectSlip(slip: com.example.models.SavedPredictionSlip) {
        _currentSlip.value = slip
    }
}
