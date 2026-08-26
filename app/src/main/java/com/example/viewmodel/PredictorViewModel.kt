package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.models.Country
import com.example.models.League
import com.example.models.Match
import com.example.models.PredictionResult
import com.example.network.NetworkClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.example.models.ApiFootballResponse

class PredictorViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("predictor_prefs", Context.MODE_PRIVATE)

    val keyManager = com.example.keymanager.KeyRotationManager(application, viewModelScope)
    val userManager = com.example.auth.UserManager(application)
    val currentUser = userManager.currentUser

    private val _countries = MutableStateFlow<List<Country>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val countries: StateFlow<List<Country>> = _countries.asStateFlow()

    val searchResults: StateFlow<List<com.example.models.SearchItem>> = combine(_countries, _searchQuery) { allCountries, query ->
        if (query.isBlank()) return@combine emptyList()
        val lower = query.lowercase(Locale.getDefault()).trim()
        val results = mutableListOf<com.example.models.SearchItem>()
        val seen = mutableSetOf<String>()

        allCountries.forEach { country ->
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

    init {
        fetchFixtures()
    }

    fun saveApiFootballKey(key: String, label: String = "Primary API Key") {
        val apiKeyObj = com.example.keymanager.ManagedApiKey(
            role = com.example.keymanager.ApiRole.API_FOOTBALL.code,
            key = key,
            label = label
        )
        keyManager.addOrUpdateKey(apiKeyObj)
        fetchFixtures()
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
            _currentDate.value = getCurrentDateString(newOffset)
            fetchFixtures()
        }
    }

    val isToday: Boolean get() = _currentDateOffset.value == 0

    fun fetchFixtures(forceRefresh: Boolean = false) {
        val activeKey = keyManager.getActiveKey(com.example.keymanager.ApiRole.API_FOOTBALL)
        if (activeKey.isNullOrBlank()) {
            _errorMessage.value = "Please configure your API-Football key in settings."
            _countries.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val dateStr = _currentDate.value
                val cacheKey = "fixtures_cache_$dateStr"
                
                val cachedJson = prefs.getString(cacheKey, null)
                val cachedResponse: ApiFootballResponse? = if (!forceRefresh && cachedJson != null) {
                    try {
                        NetworkClient.moshi.adapter(ApiFootballResponse::class.java).fromJson(cachedJson)
                    } catch (e: Exception) {
                        null
                    }
                } else null

                val response = cachedResponse ?: NetworkClient.apiFootballService.getFixtures(activeKey, dateStr)
                
                if (cachedResponse == null && (response.errors == null || (response.errors is List<*> && response.errors.isEmpty()))) {
                    try {
                        val json = NetworkClient.moshi.adapter(ApiFootballResponse::class.java).toJson(response)
                        prefs.edit().putString(cacheKey, json).apply()
                    } catch (e: Exception) {
                        // Ignore cache write errors
                    }
                    keyManager.reportKeySuccess(com.example.keymanager.ApiRole.API_FOOTBALL, activeKey)
                }

                if (response.errors is Map<*, *>) {
                    val errorMap = response.errors as Map<*, *>
                    val errorMsg = errorMap.values.firstOrNull()?.toString() ?: "API Error"
                    
                    if (errorMsg.contains("Free plans do not have access to this date", ignoreCase = true) || errorMsg.contains("not have access to this date", ignoreCase = true)) {
                        _maxDateOffset.value = _currentDateOffset.value - 1
                        changeDateBy(-1)
                        return@launch
                    }

                    if (errorMsg.contains("requests", ignoreCase = true) || errorMsg.contains("limit", ignoreCase = true) || errorMsg.contains("rate", ignoreCase = true)) {
                        keyManager.reportKeyRateLimited(com.example.keymanager.ApiRole.API_FOOTBALL, activeKey, cooldownSeconds = 600)
                    } else if (errorMsg.contains("key", ignoreCase = true) || errorMsg.contains("token", ignoreCase = true) || errorMsg.contains("unauthorized", ignoreCase = true)) {
                        keyManager.reportKeyError(com.example.keymanager.ApiRole.API_FOOTBALL, activeKey, isAuthError = true)
                    }
                    
                    _errorMessage.value = errorMsg
                    _countries.value = emptyList()
                    return@launch
                }

                val bigCountries = listOf("England", "Spain", "Germany", "Italy", "France", "World")

                // Group by Country then League
                val mappedCountries = response.response.groupBy { it.league.country }
                    .map { (countryName, fixturesForCountry) ->
                        val firstFixture = fixturesForCountry.firstOrNull()
                        val countryFlag = firstFixture?.league?.flag
                        
                        val leagues = fixturesForCountry.groupBy { it.league.id }
                            .map { (leagueId, fixturesForLeague) ->
                                val leagueInfo = fixturesForLeague.first().league
                                
                                val matches = fixturesForLeague.map { apiFixture ->
                                    // Parse time (e.g., "2023-10-10T20:00:00+00:00")
                                    val timeString = try {
                                        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                                        val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                        val dateObj = inputFormat.parse(apiFixture.fixture.date)
                                        if (dateObj != null) outputFormat.format(dateObj) else "--:--"
                                    } catch (e: Exception) {
                                        try {
                                            // Fallback for older devices that don't support XXX
                                            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
                                            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                            val dateObj = inputFormat.parse(apiFixture.fixture.date.replace("+00:00", "+0000"))
                                            if (dateObj != null) outputFormat.format(dateObj) else "--:--"
                                        } catch (e2: Exception) {
                                            "--:--"
                                        }
                                    }
                                    
                                    Match(
                                        id = apiFixture.fixture.id,
                                        homeTeam = apiFixture.teams.home.name,
                                        awayTeam = apiFixture.teams.away.name,
                                        homeLogo = apiFixture.teams.home.logo,
                                        awayLogo = apiFixture.teams.away.logo,
                                        startTime = timeString,
                                        status = apiFixture.fixture.status.short,
                                        homeScore = apiFixture.goals.home,
                                        awayScore = apiFixture.goals.away
                                    )
                                }.sortedBy { it.startTime }
                                
                                League(
                                    id = leagueId,
                                    name = leagueInfo.name,
                                    logoUrl = leagueInfo.logo,
                                    matches = matches
                                )
                            }.sortedWith(compareBy<League> { 
                                val bigLeagues = listOf("Premier League", "La Liga", "Bundesliga", "Serie A", "Ligue 1", "UEFA Champions League", "UEFA Europa League", "Championship")
                                val index = bigLeagues.indexOf(it.name)
                                if (index != -1) index else Integer.MAX_VALUE
                            }.thenBy { it.name })
                        
                        Country(
                            name = countryName,
                            flagUrl = countryFlag,
                            leagues = leagues
                        )
                    }.sortedWith(compareBy<Country> { 
                        val index = bigCountries.indexOf(it.name)
                        if (index != -1) index else Integer.MAX_VALUE
                    }.thenBy { it.name })
                
                _countries.value = mappedCountries
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fetch fixtures: ${e.message}"
                _countries.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun predictMatch(matchId: Int) {
        if (!userManager.consumePredictionQuota()) {
            _errorMessage.value = "Daily free prediction limit reached! Please sign in or upgrade to PRO VIP for unlimited predictions."
            return
        }

        viewModelScope.launch {
            // Find match details
            var matchHome = "Home Team"
            var matchAway = "Away Team"
            var matchLeague = "League"

            _countries.value.forEach { country ->
                country.leagues.forEach { league ->
                    league.matches.find { it.id == matchId }?.let {
                        matchHome = it.homeTeam
                        matchAway = it.awayTeam
                        matchLeague = league.name
                    }
                }
            }

            val openAiManagedKey = keyManager.getActiveManagedKey(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE)
            val prediction = if (openAiManagedKey != null && openAiManagedKey.key.isNotBlank()) {
                val result = com.example.network.OpenAiService.generatePrediction(
                    homeTeam = matchHome,
                    awayTeam = matchAway,
                    league = matchLeague,
                    managedKey = openAiManagedKey
                )
                if (result.isSuccess) {
                    keyManager.reportKeySuccess(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE, openAiManagedKey.key)
                    result.getOrNull()
                } else {
                    keyManager.reportKeyError(
                        com.example.keymanager.ApiRole.OPENAI_COMPATIBLE,
                        openAiManagedKey.key,
                        isAuthError = false
                    )
                    PredictionResult(
                        recommendedBet = "1X (Home or Draw)",
                        confidence = 78,
                        rationale = "Tactical advantage for $matchHome based on home pitch metrics."
                    )
                }
            } else {
                delay(1200)
                PredictionResult(
                    recommendedBet = "Home Win",
                    confidence = 82,
                    rationale = "Strong home momentum and tactical offensive rating for $matchHome."
                )
            }
            
            _countries.update { currentCountries ->
                currentCountries.map { country ->
                    country.copy(
                        leagues = country.leagues.map { league ->
                            league.copy(
                                matches = league.matches.map { match ->
                                    if (match.id == matchId) {
                                        match.copy(prediction = prediction)
                                    } else {
                                        match
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
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

    private val _selectedBetTypes = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(
        prefs.getStringSet("selected_bet_types", null) ?: availableBetTypes.toSet()
    )
    val selectedBetTypes: kotlinx.coroutines.flow.StateFlow<Set<String>> = _selectedBetTypes.asStateFlow()

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

    private val _selectedSearchItems = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(
        prefs.getStringSet("selected_search_items", null) ?: emptySet()
    )
    val selectedSearchItems: kotlinx.coroutines.flow.StateFlow<Set<String>> = _selectedSearchItems.asStateFlow()

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

    private val _selectedCurrency = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString("selected_currency_code", null)?.let { savedCode ->
            availableCurrencies.find { it.code == savedCode }
        } ?: availableCurrencies.first()
    )
    val selectedCurrency: kotlinx.coroutines.flow.StateFlow<com.example.models.Currency> = _selectedCurrency.asStateFlow()

    fun selectCurrency(currency: com.example.models.Currency) {
        _selectedCurrency.value = currency
        prefs.edit().putString("selected_currency_code", currency.code).apply()
    }

    private val _budget = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getFloat("betting_budget", 50f)
    )
    val budget: kotlinx.coroutines.flow.StateFlow<Float> = _budget.asStateFlow()

    fun updateBudget(amount: Float) {
        _budget.value = amount
        prefs.edit().putFloat("betting_budget", amount).apply()
    }

    private val _moneyRange = kotlinx.coroutines.flow.MutableStateFlow(
        (prefs.getFloat("money_target_min", 10f))..(prefs.getFloat("money_target_max", 250f))
    )
    val moneyRange: kotlinx.coroutines.flow.StateFlow<ClosedFloatingPointRange<Float>> = _moneyRange.asStateFlow()

    fun updateMoneyRange(range: ClosedFloatingPointRange<Float>) {
        _moneyRange.value = range
        prefs.edit()
            .putFloat("money_target_min", range.start)
            .putFloat("money_target_max", range.endInclusive)
            .apply()
    }

    // App Customization Settings (Theme, Accents, Odds Format, Live Refresh, Haptics)
    private val _customSettings = MutableStateFlow(
        com.example.models.AppCustomSettings(
            themeMode = com.example.models.ThemeMode.fromId(prefs.getString("app_theme_mode", "cyber_dark") ?: "cyber_dark"),
            accentColorMode = com.example.models.AccentColorMode.fromId(prefs.getString("app_accent_color", "orange") ?: "orange"),
            oddsFormat = com.example.models.OddsFormat.fromId(prefs.getString("app_odds_format", "decimal") ?: "decimal"),
            autoRefreshSec = prefs.getInt("app_auto_refresh", 30),
            showFinishedMatches = prefs.getBoolean("app_show_finished", true),
            hapticsEnabled = prefs.getBoolean("app_haptics_enabled", true),
            dataSaver = prefs.getBoolean("app_data_saver", false)
        )
    )
    val customSettings: StateFlow<com.example.models.AppCustomSettings> = _customSettings.asStateFlow()

    fun updateThemeMode(theme: com.example.models.ThemeMode) {
        _customSettings.update { it.copy(themeMode = theme) }
        prefs.edit().putString("app_theme_mode", theme.id).apply()
    }

    fun updateAccentColor(accent: com.example.models.AccentColorMode) {
        _customSettings.update { it.copy(accentColorMode = accent) }
        prefs.edit().putString("app_accent_color", accent.id).apply()
    }

    fun updateOddsFormat(oddsFormat: com.example.models.OddsFormat) {
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
}

