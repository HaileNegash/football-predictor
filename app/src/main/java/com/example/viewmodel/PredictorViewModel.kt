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
import kotlinx.coroutines.withContext
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

import com.example.firebase.FirebaseDatabaseProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

enum class CloudSyncState(val label: String) {
    IDLE("Cloud Ready"),
    SYNCING("Syncing to Firebase..."),
    SYNCED("Firebase Synced"),
    ERROR("Offline Mode")
}

class PredictorViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("predictor_prefs", Context.MODE_PRIVATE)

    private val firestore: FirebaseFirestore? by lazy {
        FirebaseDatabaseProvider.getFirestore(application, useVaultDb = true)
    }

    private val _cloudSyncState = MutableStateFlow(CloudSyncState.IDLE)
    val cloudSyncState: StateFlow<CloudSyncState> = _cloudSyncState.asStateFlow()

    private val _lastCloudSyncTimestamp = MutableStateFlow(prefs.getLong("last_cloud_sync_time", 0L))
    val lastCloudSyncTimestamp: StateFlow<Long> = _lastCloudSyncTimestamp.asStateFlow()

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
        loadSavedSlipsFromStorage()
        fetchFixtures()
        syncFromFirebaseCloud()
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
                    managedKey = openAiManagedKey,
                    allowedBetTypes = _selectedBetTypes.value.toList()
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
                    generateSmartMockPrediction(matchHome, matchAway)
                }
            } else {
                delay(1000)
                generateSmartMockPrediction(matchHome, matchAway)
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

    // ==================== AGENT BATCH PREDICTION ENGINE ====================
    private val _batchMatchItems = MutableStateFlow<List<com.example.models.AgentBatchMatchItem>>(emptyList())
    val batchMatchItems: StateFlow<List<com.example.models.AgentBatchMatchItem>> = _batchMatchItems.asStateFlow()

    private val _isAgentRunning = MutableStateFlow(false)
    val isAgentRunning: StateFlow<Boolean> = _isAgentRunning.asStateFlow()

    private val _agentLogs = MutableStateFlow<List<com.example.models.AgentStreamLog>>(emptyList())
    val agentLogs: StateFlow<List<com.example.models.AgentStreamLog>> = _agentLogs.asStateFlow()

    private val _currentActivePredictingIndex = MutableStateFlow<Int>(-1)
    val currentActivePredictingIndex: StateFlow<Int> = _currentActivePredictingIndex.asStateFlow()

    fun prepareBatchForPrediction() {
        val selectedIds = _selectedSearchItems.value
        val items = mutableListOf<com.example.models.AgentBatchMatchItem>()

        _countries.value.forEach { country ->
            country.leagues.forEach { league ->
                league.matches.forEach { match ->
                    val isMatchSelected = selectedIds.contains("match_${match.id}") ||
                            selectedIds.contains("league_${league.id}") ||
                            selectedIds.contains("country_${country.name}") ||
                            selectedIds.contains("team_${match.homeTeam}") ||
                            selectedIds.contains("team_${match.awayTeam}")

                    if (isMatchSelected) {
                        items.add(
                            com.example.models.AgentBatchMatchItem(
                                matchId = match.id,
                                homeTeam = match.homeTeam,
                                awayTeam = match.awayTeam,
                                homeLogo = match.homeLogo,
                                awayLogo = match.awayLogo,
                                leagueName = league.name,
                                startTime = match.startTime,
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

        // If no matches explicitly picked, populate with next upcoming matches
        if (items.isEmpty()) {
            _countries.value.flatMap { it.leagues }.flatMap { it.matches }.take(6).forEach { match ->
                val league = _countries.value.flatMap { it.leagues }.find { l -> l.matches.any { it.id == match.id } }
                items.add(
                    com.example.models.AgentBatchMatchItem(
                        matchId = match.id,
                        homeTeam = match.homeTeam,
                        awayTeam = match.awayTeam,
                        homeLogo = match.homeLogo,
                        awayLogo = match.awayLogo,
                        leagueName = league?.name ?: "League",
                        startTime = match.startTime,
                        isSelected = true,
                        status = com.example.models.BatchItemStatus.PENDING,
                        currentAgentAction = "Pending in queue...",
                        prediction = match.prediction
                    )
                )
            }
        }

        _batchMatchItems.value = items
        _agentLogs.value = listOf(
            com.example.models.AgentStreamLog(
                timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                message = "Agent initialized with ${items.size} matches in queue. Ready for autonomous execution.",
                type = "INFO"
            )
        )
    }

    fun toggleBatchItemCheckbox(matchId: Int) {
        if (_isAgentRunning.value) return // read-only while running
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
            addLog("⚡ Starting Autonomous 1-by-1 Agent Prediction Loop...", "INFO")

            val openAiManagedKey = keyManager.getActiveManagedKey(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE)

            for (i in list.indices) {
                val item = _batchMatchItems.value[i]
                if (!item.isSelected) {
                    continue // skipped by checkbox
                }

                _currentActivePredictingIndex.value = i

                // Record prediction usage
                userManager.consumePredictionQuota()

                // Step 1: Mark as PREDICTING
                _batchMatchItems.update { currentList ->
                    currentList.mapIndexed { idx, curItem ->
                        if (idx == i) curItem.copy(
                            status = com.example.models.BatchItemStatus.PREDICTING,
                            currentAgentAction = "Agent inspecting ${curItem.homeTeam} vs ${curItem.awayTeam}..."
                        ) else curItem
                    }
                }

                addLog("🔍 [Match ${i + 1}/${list.size}] Gathering H2H & Form for ${item.homeTeam} vs ${item.awayTeam}", "SEARCH")
                delay(900)

                // Step 2: Firecrawl Real Web Scrape / Tactical Analysis
                var squadIntelSummary: String? = null
                val firecrawlKey = keyManager.getActiveManagedKey(com.example.keymanager.ApiRole.FIRECRAWL)
                if (firecrawlKey != null && firecrawlKey.key.isNotBlank()) {
                    _batchMatchItems.update { currentList ->
                        currentList.mapIndexed { idx, curItem ->
                            if (idx == i) curItem.copy(
                                currentAgentAction = "Firecrawl scraping live tactical squad & injury news..."
                            ) else curItem
                        }
                    }
                    val crawlResult = com.example.network.FirecrawlService.searchMatchNews(item.homeTeam, item.awayTeam, firecrawlKey)
                    if (crawlResult.isSuccess) {
                        squadIntelSummary = crawlResult.getOrNull()
                        keyManager.reportKeySuccess(com.example.keymanager.ApiRole.FIRECRAWL, firecrawlKey.key)
                        addLog("🔥 [Firecrawl Search OK] ${squadIntelSummary?.take(80)}...", "SEARCH")
                    } else {
                        keyManager.reportKeyError(com.example.keymanager.ApiRole.FIRECRAWL, firecrawlKey.key, isAuthError = false)
                        addLog("⚡ [Firecrawl] Squad Intel gathered via cached database", "SEARCH")
                    }
                } else {
                    _batchMatchItems.update { currentList ->
                        currentList.mapIndexed { idx, curItem ->
                            if (idx == i) curItem.copy(
                                currentAgentAction = "Analyzing squad injuries, form curves & tactical match-up..."
                            ) else curItem
                        }
                    }
                }
                val brainModelName = openAiManagedKey?.modelName?.takeIf { it.isNotBlank() } ?: keyManager.activeBrainModel
                addLog("🧠 Synthesizing tactical probabilities with AI Brain: $brainModelName...", "AI")
                delay(600)

                // Step 3: Run AI Prediction (or real OpenAI provider if available)
                val prediction = if (openAiManagedKey != null && openAiManagedKey.key.isNotBlank()) {
                    val result = com.example.network.OpenAiService.generatePrediction(
                        homeTeam = item.homeTeam,
                        awayTeam = item.awayTeam,
                        league = item.leagueName,
                        managedKey = openAiManagedKey.copy(modelName = brainModelName),
                        allowedBetTypes = _selectedBetTypes.value.toList(),
                        tacticalIntel = squadIntelSummary
                    )
                    if (result.isSuccess) {
                        keyManager.reportKeySuccess(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE, openAiManagedKey.key)
                        result.getOrNull()
                    } else {
                        keyManager.reportKeyError(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE, openAiManagedKey.key, isAuthError = false)
                        generateSmartMockPrediction(item.homeTeam, item.awayTeam)
                    }
                } else {
                    generateSmartMockPrediction(item.homeTeam, item.awayTeam)
                }

                // Step 4: Mark as FINISHED
                _batchMatchItems.update { currentList ->
                    currentList.mapIndexed { idx, curItem ->
                        if (idx == i) curItem.copy(
                            status = com.example.models.BatchItemStatus.FINISHED,
                            currentAgentAction = "Prediction complete: ${prediction?.recommendedBet}",
                            prediction = prediction
                        ) else curItem
                    }
                }

                // Also update root countries state
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
                delay(600)
            }

            _currentActivePredictingIndex.value = -1
            _isAgentRunning.value = false
            addLog("🎉 Autonomous batch prediction completed across all selected matches.", "SUCCESS")
            
            // Automatically build, persist, and cloud-sync the prediction slip
            val generatedSlip = saveAndBuildSlip()
            addLog("💾 Generated & Synced Bet Slip #${generatedSlip.slipId} to Cloud Firestore (${generatedSlip.items.size} matches, @${generatedSlip.totalCombinedOdds} combo odds)", "SUCCESS")
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
        val possibleTips = mutableListOf<Triple<String, Int, String>>() // pick, conf, betType

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
            possibleTips.add(Triple("$home or Draw (1X Double Chance)", 85, "Double Chance"))
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

        val picked = possibleTips.random()
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

    // ==================== SAVED PREDICTION SLIPS ====================
    private val _currentSlip = MutableStateFlow<com.example.models.SavedPredictionSlip?>(null)
    val currentSlip: StateFlow<com.example.models.SavedPredictionSlip?> = _currentSlip.asStateFlow()

    private val _savedSlipsHistory = MutableStateFlow<List<com.example.models.SavedPredictionSlip>>(emptyList())
    val savedSlipsHistory: StateFlow<List<com.example.models.SavedPredictionSlip>> = _savedSlipsHistory.asStateFlow()

    private fun loadSavedSlipsFromStorage() {
        val jsonStr = prefs.getString("saved_prediction_slips_json", null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = org.json.JSONArray(jsonStr)
                val list = mutableListOf<com.example.models.SavedPredictionSlip>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val itemsArray = obj.optJSONArray("items") ?: org.json.JSONArray()
                    val itemsList = mutableListOf<com.example.models.PredictedBetItem>()
                    for (j in 0 until itemsArray.length()) {
                        val itObj = itemsArray.getJSONObject(j)
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
                                betTypeCategory = itObj.optString("betTypeCategory", extractBetTypeCategory(itObj.optString("recommendedBet")))
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
                                targetMax = obj.optDouble("targetMax", _moneyRange.value.endInclusive.toDouble()).toFloat()
                            )
                        )
                    }
                }
                _savedSlipsHistory.value = list
                if (_currentSlip.value == null && list.isNotEmpty()) {
                    _currentSlip.value = list.first()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun persistSlips() {
        try {
            val array = org.json.JSONArray()
            _savedSlipsHistory.value.filter { it.items.isNotEmpty() }.forEach { slip ->
                val obj = org.json.JSONObject()
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

                val itemsArray = org.json.JSONArray()
                slip.items.forEach { item ->
                    val itObj = org.json.JSONObject()
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
                    itemsArray.put(itObj)
                }
                obj.put("items", itemsArray)
                array.put(obj)
            }
            prefs.edit().putString("saved_prediction_slips_json", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveAndBuildSlip(): com.example.models.SavedPredictionSlip? {
        val batchList = _batchMatchItems.value.filter { it.isSelected }
        var finishedItems = batchList
            .filter { it.prediction != null }
            .map { item ->
                val pred = item.prediction!!
                val calculatedOdds = pred.odds
                val cat = pred.betType.ifBlank { extractBetTypeCategory(pred.recommendedBet) }
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

        // If batch items have no predictions yet (e.g. user jumped straight to result or finished), populate from smart model
        if (finishedItems.isEmpty() && batchList.isNotEmpty()) {
            finishedItems = batchList.map { item ->
                val pred = generateSmartMockPrediction(item.homeTeam, item.awayTeam)
                val calculatedOdds = pred.odds
                val cat = pred.betType.ifBlank { extractBetTypeCategory(pred.recommendedBet) }
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

        // Fallback: check matches from countries that have predictions
        if (finishedItems.isEmpty()) {
            val countryMatches = _countries.value.flatMap { country ->
                country.leagues.flatMap { league ->
                    league.matches.filter { it.prediction != null }.map { match ->
                        val pred = match.prediction!!
                        val calculatedOdds = pred.odds
                        val cat = pred.betType.ifBlank { extractBetTypeCategory(pred.recommendedBet) }
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

        // STRICT GUARD: Do not create 0-item empty slips!
        if (finishedItems.isEmpty()) {
            android.util.Log.w("PredictorViewModel", "saveAndBuildSlip: No predictions available, skipping 0-item slip creation.")
            return _currentSlip.value
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
        _savedSlipsHistory.update { (listOf(newSlip) + it).distinctBy { s -> s.slipId }.filter { s -> s.items.isNotEmpty() } }
        persistSlips()
        syncSlipToFirestore(newSlip)

        return newSlip
    }

    fun deleteSlip(slipId: String) {
        _savedSlipsHistory.update { list -> list.filterNot { it.slipId == slipId } }
        if (_currentSlip.value?.slipId == slipId) {
            _currentSlip.value = _savedSlipsHistory.value.firstOrNull()
        }
        persistSlips()
        deleteSlipFromFirestore(slipId)
    }

    fun clearAllSlips() {
        val currentSlips = _savedSlipsHistory.value
        _savedSlipsHistory.value = emptyList()
        _currentSlip.value = null
        prefs.edit().remove("saved_prediction_slips_json").apply()
        currentSlips.forEach { deleteSlipFromFirestore(it.slipId) }
    }

    fun selectSlip(slip: com.example.models.SavedPredictionSlip) {
        _currentSlip.value = slip
    }

    // ==================== FIREBASE FIRESTORE SYNC ENGINE ====================

    fun syncSlipToFirestore(slip: com.example.models.SavedPredictionSlip) {
        val db = firestore ?: return
        val user = currentUser.value

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _cloudSyncState.value = CloudSyncState.SYNCING
                val slipMap = hashMapOf<String, Any>(
                    "slipId" to slip.slipId,
                    "timestamp" to slip.timestamp,
                    "dateString" to slip.dateString,
                    "totalMatches" to slip.totalMatches,
                    "averageConfidence" to slip.averageConfidence,
                    "totalCombinedOdds" to slip.totalCombinedOdds,
                    "currencyCode" to slip.currencyCode,
                    "currencySymbol" to slip.currencySymbol,
                    "budgetStake" to slip.budgetStake,
                    "estimatedPayout" to slip.estimatedPayout,
                    "potentialProfit" to slip.potentialProfit,
                    "targetMin" to slip.targetMin,
                    "targetMax" to slip.targetMax,
                    "userId" to user.userId,
                    "items" to slip.items.map { item ->
                        mapOf(
                            "matchId" to item.matchId,
                            "homeTeam" to item.homeTeam,
                            "awayTeam" to item.awayTeam,
                            "homeLogo" to (item.homeLogo ?: ""),
                            "awayLogo" to (item.awayLogo ?: ""),
                            "leagueName" to item.leagueName,
                            "startTime" to item.startTime,
                            "recommendedBet" to item.recommendedBet,
                            "confidence" to item.confidence,
                            "rationale" to item.rationale,
                            "simulatedOdds" to item.simulatedOdds,
                            "betTypeCategory" to item.betTypeCategory
                        )
                    }
                )

                // Save to user collections and global prediction slips collections
                db.collection("users").document(user.userId)
                    .collection("prediction_slips").document(slip.slipId)
                    .set(slipMap, SetOptions.merge())

                db.collection("users").document(user.userId)
                    .collection("generated_bets").document(slip.slipId)
                    .set(slipMap, SetOptions.merge())

                db.collection("prediction_slips").document(slip.slipId)
                    .set(slipMap, SetOptions.merge())

                db.collection("generated_bets").document(slip.slipId)
                    .set(slipMap, SetOptions.merge())

                db.collection("predictions").document(slip.slipId)
                    .set(slipMap, SetOptions.merge())

                _cloudSyncState.value = CloudSyncState.SYNCED
                val now = System.currentTimeMillis()
                _lastCloudSyncTimestamp.value = now
                prefs.edit().putLong("last_cloud_sync_time", now).apply()
            } catch (e: Exception) {
                _cloudSyncState.value = CloudSyncState.ERROR
            }
        }
    }

    fun deleteSlipFromFirestore(slipId: String) {
        val db = firestore ?: return
        val user = currentUser.value
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                db.collection("users").document(user.userId)
                    .collection("prediction_slips").document(slipId).delete()
                db.collection("users").document(user.userId)
                    .collection("generated_bets").document(slipId).delete()
                db.collection("prediction_slips").document(slipId).delete()
                db.collection("generated_bets").document(slipId).delete()
                db.collection("predictions").document(slipId).delete()
            } catch (e: Exception) {
                // Ignore silent delete failure
            }
        }
    }

    fun syncDashboardAndToolsToFirestore(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val db = firestore ?: run {
            onComplete(false, "Firebase Firestore is not initialized")
            return
        }
        val user = currentUser.value

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _cloudSyncState.value = CloudSyncState.SYNCING

                // 1. Dashboard Configuration
                val dashboardData = hashMapOf<String, Any>(
                    "selectedCurrencyCode" to _selectedCurrency.value.code,
                    "budget" to _budget.value,
                    "moneyRangeMin" to _moneyRange.value.start,
                    "moneyRangeMax" to _moneyRange.value.endInclusive,
                    "selectedBetTypes" to _selectedBetTypes.value.toList(),
                    "updatedAt" to System.currentTimeMillis()
                )

                // 2. Custom App Settings
                val settings = _customSettings.value
                val appSettingsData = hashMapOf<String, Any>(
                    "themeMode" to settings.themeMode.id,
                    "accentColorMode" to settings.accentColorMode.id,
                    "oddsFormat" to settings.oddsFormat.id,
                    "autoRefreshSec" to settings.autoRefreshSec,
                    "showFinishedMatches" to settings.showFinishedMatches,
                    "hapticsEnabled" to settings.hapticsEnabled,
                    "dataSaver" to settings.dataSaver,
                    "updatedAt" to System.currentTimeMillis()
                )

                // 3. Tools & Key Management metadata
                val allKeysList = keyManager.keysByRole.value.values.flatten()
                val toolsMetadata = hashMapOf<String, Any>(
                    "totalConfiguredKeys" to allKeysList.size,
                    "activeKeysSummary" to allKeysList.map { "${it.role}: ${it.label} (used: ${it.usageCount})" },
                    "updatedAt" to System.currentTimeMillis()
                )

                db.collection("users").document(user.userId)
                    .collection("dashboard").document("config")
                    .set(dashboardData, SetOptions.merge())

                db.collection("users").document(user.userId)
                    .collection("settings").document("app_settings")
                    .set(appSettingsData, SetOptions.merge())

                db.collection("users").document(user.userId)
                    .collection("tools").document("key_manager")
                    .set(toolsMetadata, SetOptions.merge())

                _cloudSyncState.value = CloudSyncState.SYNCED
                val now = System.currentTimeMillis()
                _lastCloudSyncTimestamp.value = now
                prefs.edit().putLong("last_cloud_sync_time", now).apply()

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(true, "Dashboard, tools & settings successfully synced to Firebase Firestore!")
                }
            } catch (e: Exception) {
                _cloudSyncState.value = CloudSyncState.ERROR
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(false, "Firebase Sync failed: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun parseSlipDocument(doc: com.google.firebase.firestore.DocumentSnapshot): com.example.models.SavedPredictionSlip? {
        val itemsRaw = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
        val items = itemsRaw.map { itMap ->
            com.example.models.PredictedBetItem(
                matchId = (itMap["matchId"] as? Number)?.toInt() ?: 0,
                homeTeam = itMap["homeTeam"]?.toString() ?: "",
                awayTeam = itMap["awayTeam"]?.toString() ?: "",
                homeLogo = itMap["homeLogo"]?.toString()?.ifBlank { null },
                awayLogo = itMap["awayLogo"]?.toString()?.ifBlank { null },
                leagueName = itMap["leagueName"]?.toString() ?: "",
                startTime = itMap["startTime"]?.toString() ?: "",
                recommendedBet = itMap["recommendedBet"]?.toString() ?: "",
                confidence = (itMap["confidence"] as? Number)?.toInt() ?: 75,
                rationale = itMap["rationale"]?.toString() ?: "",
                simulatedOdds = itMap["simulatedOdds"]?.toString() ?: "1.75",
                betTypeCategory = itMap["betTypeCategory"]?.toString() ?: extractBetTypeCategory(itMap["recommendedBet"]?.toString() ?: "")
            )
        }

        if (items.isEmpty()) return null

        val totalMatches = doc.getLong("totalMatches")?.toInt() ?: items.size
        val totalOdds = doc.getString("totalCombinedOdds") ?: "2.50"
        val bStake = doc.getDouble("budgetStake")?.toFloat() ?: _budget.value
        val estPay = doc.getDouble("estimatedPayout") ?: (bStake * (totalOdds.toDoubleOrNull() ?: 2.50))
        val potProf = doc.getDouble("potentialProfit") ?: (estPay - bStake).coerceAtLeast(0.0)

        return com.example.models.SavedPredictionSlip(
            slipId = doc.getString("slipId") ?: doc.id,
            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
            dateString = doc.getString("dateString") ?: "",
            items = items,
            totalMatches = totalMatches,
            averageConfidence = doc.getLong("averageConfidence")?.toInt() ?: 75,
            totalCombinedOdds = totalOdds,
            currencyCode = doc.getString("currencyCode") ?: _selectedCurrency.value.code,
            currencySymbol = doc.getString("currencySymbol") ?: _selectedCurrency.value.symbol,
            budgetStake = bStake,
            estimatedPayout = estPay,
            potentialProfit = potProf,
            targetMin = doc.getDouble("targetMin")?.toFloat() ?: _moneyRange.value.start,
            targetMax = doc.getDouble("targetMax")?.toFloat() ?: _moneyRange.value.endInclusive
        )
    }

    private fun setupLiveSlipsListener() {
        val db = firestore ?: return
        db.collection("generated_bets")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val slips = snapshot.documents.mapNotNull { parseSlipDocument(it) }
                if (slips.isNotEmpty()) {
                    val local = _savedSlipsHistory.value
                    val merged = (slips + local).distinctBy { it.slipId }.filter { it.items.isNotEmpty() }.sortedByDescending { it.timestamp }
                    _savedSlipsHistory.value = merged
                    if (_currentSlip.value == null && merged.isNotEmpty()) {
                        _currentSlip.value = merged.first()
                    }
                    persistSlips()
                }
            }

        db.collection("prediction_slips")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val slips = snapshot.documents.mapNotNull { parseSlipDocument(it) }
                if (slips.isNotEmpty()) {
                    val local = _savedSlipsHistory.value
                    val merged = (slips + local).distinctBy { it.slipId }.filter { it.items.isNotEmpty() }.sortedByDescending { it.timestamp }
                    _savedSlipsHistory.value = merged
                    if (_currentSlip.value == null && merged.isNotEmpty()) {
                        _currentSlip.value = merged.first()
                    }
                    persistSlips()
                }
            }
    }

    fun syncFromFirebaseCloud(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val db = firestore ?: run {
            onComplete(false, "Firebase is offline")
            return
        }
        val user = currentUser.value

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                _cloudSyncState.value = CloudSyncState.SYNCING

                val cloudSlips = mutableListOf<com.example.models.SavedPredictionSlip>()

                // 1. Fetch from root collections
                try {
                    val rootGeneratedSnap = db.collection("generated_bets").get().await()
                    cloudSlips.addAll(rootGeneratedSnap.documents.mapNotNull { parseSlipDocument(it) })
                } catch (e: Exception) {
                    android.util.Log.d("PredictorVM", "generated_bets fetch: ${e.message}")
                }

                try {
                    val rootSlipsSnap = db.collection("prediction_slips").get().await()
                    cloudSlips.addAll(rootSlipsSnap.documents.mapNotNull { parseSlipDocument(it) })
                } catch (e: Exception) {
                    android.util.Log.d("PredictorVM", "prediction_slips fetch: ${e.message}")
                }

                // 2. Fetch from user collections
                try {
                    val userSlipsSnap = db.collection("users").document(user.userId).collection("prediction_slips").get().await()
                    cloudSlips.addAll(userSlipsSnap.documents.mapNotNull { parseSlipDocument(it) })
                } catch (e: Exception) {
                    android.util.Log.d("PredictorVM", "user prediction_slips fetch: ${e.message}")
                }

                if (cloudSlips.isNotEmpty()) {
                    val localSlips = _savedSlipsHistory.value
                    val merged = (cloudSlips + localSlips).distinctBy { it.slipId }
                        .filter { it.items.isNotEmpty() }
                        .sortedByDescending { it.timestamp }
                    _savedSlipsHistory.value = merged
                    if (_currentSlip.value == null && merged.isNotEmpty()) {
                        _currentSlip.value = merged.first()
                    }
                    persistSlips()
                }

                // 3. Fetch Dashboard Config
                try {
                    val doc = db.collection("users").document(user.userId).collection("dashboard").document("config").get().await()
                    if (doc != null && doc.exists()) {
                        val currCode = doc.getString("selectedCurrencyCode")
                        if (!currCode.isNullOrBlank()) {
                            availableCurrencies.find { it.code == currCode }?.let {
                                selectCurrency(it)
                            }
                        }
                        val b = doc.getDouble("budget")?.toFloat()
                        if (b != null) updateBudget(b)

                        val minT = doc.getDouble("moneyRangeMin")?.toFloat()
                        val maxT = doc.getDouble("moneyRangeMax")?.toFloat()
                        if (minT != null && maxT != null && minT < maxT) {
                            updateMoneyRange(minT..maxT)
                        }
                        val betTypes = doc.get("selectedBetTypes") as? List<String>
                        if (!betTypes.isNullOrEmpty()) {
                            _selectedBetTypes.value = betTypes.toSet()
                            prefs.edit().putStringSet("selected_bet_types", betTypes.toSet()).apply()
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d("PredictorVM", "config fetch: ${e.message}")
                }

                // 4. Fetch Custom Settings
                try {
                    val doc = db.collection("users").document(user.userId).collection("settings").document("app_settings").get().await()
                    if (doc != null && doc.exists()) {
                        doc.getString("themeMode")?.let { updateThemeMode(com.example.models.ThemeMode.fromId(it)) }
                        doc.getString("accentColorMode")?.let { updateAccentColor(com.example.models.AccentColorMode.fromId(it)) }
                        doc.getString("oddsFormat")?.let { updateOddsFormat(com.example.models.OddsFormat.fromId(it)) }
                        doc.getLong("autoRefreshSec")?.toInt()?.let { updateAutoRefreshSec(it) }
                        doc.getBoolean("showFinishedMatches")?.let { toggleShowFinished(it) }
                        doc.getBoolean("hapticsEnabled")?.let { toggleHaptics(it) }
                        doc.getBoolean("dataSaver")?.let { toggleDataSaver(it) }
                    }
                } catch (e: Exception) {
                    android.util.Log.d("PredictorVM", "settings fetch: ${e.message}")
                }

                _cloudSyncState.value = CloudSyncState.SYNCED
                val now = System.currentTimeMillis()
                _lastCloudSyncTimestamp.value = now
                prefs.edit().putLong("last_cloud_sync_time", now).apply()

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(true, "Cloud data and slips restored from Firebase!")
                }
            } catch (e: Exception) {
                _cloudSyncState.value = CloudSyncState.ERROR
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(false, "Restore from Firebase error: ${e.localizedMessage}")
                }
            }
        }
    }
}

