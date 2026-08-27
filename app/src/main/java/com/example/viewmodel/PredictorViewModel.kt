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

    /** Fetches the real evidence a prediction is grounded in, under a request budget. */
    private val contextRepository = com.example.network.MatchContextRepository(application)

    /** Fetches final scores so predictions can actually be graded. */
    private val resultsRepository = com.example.network.ResultsRepository()

    /**
     * Predictions that survive process death. Without this every app restart re-billed
     * a full context fetch + Firecrawl scrape + model completion per fixture to
     * regenerate answers already paid for.
     */
    private val predictionStore = com.example.network.PredictionStore(application)

    /** In-memory mirror of [predictionStore], keyed by fixture id. */
    private var cachedPredictions: MutableMap<Int, com.example.models.PredictionResult> =
        mutableMapOf()

    private companion object {
        /** Today's fixtures change (goals, status); past dates are final. */
        const val FIXTURE_TTL_TODAY_MS = 3 * 60 * 1000L
        const val FIXTURE_TTL_OTHER_MS = 12 * 60 * 60 * 1000L

        /**
         * API-Football calls allowed for one manual prediction: standings, H2H,
         * injuries, odds.
         */
        const val SINGLE_PREDICTION_BUDGET = 4

        /**
         * Ceiling for a whole batch run. A free plan is ~100 requests/day, and
         * fixtures already consumed some, so a batch is capped well below that.
         * Standings memoization means most fixtures in a run cost far less than 4.
         */
        const val BATCH_REQUEST_BUDGET = 40

        /** Fixture-result lookups per settlement pass; 20 ids per request. */
        const val SETTLEMENT_REQUEST_BUDGET = 2

        /**
         * How long after kickoff a fixture is worth asking about. 90 minutes plus
         * half-time and stoppage; 2h is comfortably past full time for a normal match
         * without waiting so long that same-evening results go unsettled.
         */
        const val MIN_SECONDS_AFTER_KICKOFF = 2 * 60 * 60L

        /** Legs kept when building the value-ranked slip. */
        const val MAX_SLIP_LEGS = 8
    }

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
        // Load persisted predictions before fixtures, so the first mapping pass can
        // reattach them and no already-paid-for prediction is regenerated.
        cachedPredictions = predictionStore.load().toMutableMap()
        loadSavedSlipsFromStorage()
        recomputeAccuracy()
        fetchFixtures()
        syncFromFirebaseCloud()
        // Grade anything that finished while the app was closed, so the hit rate is
        // current on launch rather than only after a manual refresh.
        settlePendingPredictions()
    }

    /** Records a prediction in memory and on disk, keyed by fixture id. */
    private fun rememberPrediction(fixtureId: Int, prediction: com.example.models.PredictionResult) {
        cachedPredictions[fixtureId] = prediction
        predictionStore.save(mapOf(fixtureId to prediction))
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
                val cacheStampKey = "fixtures_cache_at_$dateStr"

                // The old code cached forever, so live scores for today never
                // updated once a payload had been written. Today's fixtures move
                // (kickoffs, goals, status changes) so they get a short TTL;
                // past dates are final and can be kept much longer.
                val ageMs = System.currentTimeMillis() - prefs.getLong(cacheStampKey, 0L)
                val ttlMs = if (_currentDateOffset.value == 0) FIXTURE_TTL_TODAY_MS else FIXTURE_TTL_OTHER_MS
                val cacheFresh = ageMs in 0 until ttlMs

                val cachedJson = if (!forceRefresh && cacheFresh) prefs.getString(cacheKey, null) else null
                val cachedResponse: ApiFootballResponse? = if (cachedJson != null) {
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
                        prefs.edit()
                            .putString(cacheKey, json)
                            .putLong(cacheStampKey, System.currentTimeMillis())
                            .apply()
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
                                        awayScore = apiFixture.goals.away,
                                        // Identifiers below are what MatchContextRepository
                                        // queries with. Without them there is no standings,
                                        // H2H, injury or odds lookup possible and the model
                                        // is back to guessing from two team names.
                                        homeTeamId = apiFixture.teams.home.id,
                                        awayTeamId = apiFixture.teams.away.id,
                                        leagueId = leagueInfo.id,
                                        leagueName = leagueInfo.name,
                                        season = leagueInfo.season,
                                        round = leagueInfo.round,
                                        countryName = countryName,
                                        kickoffEpoch = apiFixture.fixture.timestamp,
                                        // Reattach a previously generated prediction for
                                        // this fixture. Fixtures are refetched on every
                                        // date change and TTL expiry, and without this
                                        // the prediction was dropped on the floor and
                                        // had to be paid for again.
                                        prediction = cachedPredictions[apiFixture.fixture.id]
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
        // A prediction already paid for is not regenerated. This check precedes the
        // quota consumption below: charging the user a prediction to redisplay one
        // they already have is wrong twice over.
        cachedPredictions[matchId]?.let { cached ->
            applyPredictionToMatch(matchId, cached)
            return
        }

        if (!userManager.consumePredictionQuota()) {
            _errorMessage.value = "Daily free prediction limit reached! Please sign in or upgrade to PRO VIP for unlimited predictions."
            return
        }

        viewModelScope.launch {
            // Locate the fixture. Bail out rather than predicting for the old
            // "Home Team vs Away Team" placeholders, which produced a real-looking
            // prediction about teams that don't exist.
            var target: Match? = null
            var targetLeagueName: String? = null
            var targetCountry: String? = null
            _countries.value.forEach { country ->
                country.leagues.forEach { league ->
                    league.matches.find { it.id == matchId }?.let {
                        target = it
                        targetLeagueName = league.name
                        targetCountry = country.name
                    }
                }
            }
            val match = target
            if (match == null) {
                _errorMessage.value = "Could not find that fixture — try refreshing."
                return@launch
            }

            val openAiManagedKey = keyManager.getActiveManagedKey(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE)
            if (openAiManagedKey == null || openAiManagedKey.key.isBlank()) {
                _errorMessage.value = "Add an AI provider key in Settings to generate predictions."
                return@launch
            }

            val footballKey = keyManager.getActiveKey(com.example.keymanager.ApiRole.API_FOOTBALL)
            contextRepository.beginBatch()
            val ctx = if (!footballKey.isNullOrBlank()) {
                contextRepository.buildContext(
                    fixtureId = match.id,
                    homeTeam = match.homeTeam,
                    awayTeam = match.awayTeam,
                    homeTeamId = match.homeTeamId,
                    awayTeamId = match.awayTeamId,
                    leagueId = match.leagueId,
                    leagueName = match.leagueName ?: targetLeagueName ?: "Unknown league",
                    country = match.countryName ?: targetCountry ?: "",
                    season = match.season,
                    round = match.round,
                    apiKey = footballKey,
                    budget = SINGLE_PREDICTION_BUDGET
                )
            } else {
                // No football key: still predict, but with an empty context so the
                // evidence tier caps confidence and the prompt says it is unsupported.
                com.example.models.MatchContext(
                    fixtureId = match.id,
                    homeTeam = match.homeTeam,
                    awayTeam = match.awayTeam,
                    leagueName = match.leagueName ?: targetLeagueName ?: "Unknown league",
                    country = match.countryName ?: targetCountry ?: "",
                    round = match.round
                )
            }

            val enriched = withFirecrawlIntel(ctx)

            val result = com.example.network.OpenAiService.generatePrediction(
                ctx = enriched,
                managedKey = openAiManagedKey,
                allowedBetTypes = _selectedBetTypes.value.toList()
            )

            val prediction = result.getOrNull()
            if (prediction != null) {
                keyManager.reportKeySuccess(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE, openAiManagedKey.key)
                rememberPrediction(matchId, prediction)
            } else {
                reportPredictionFailure(openAiManagedKey, result.exceptionOrNull())
                // Deliberately no fabricated fallback: showing "75% — 1.85" built
                // from an error string is worse than telling the user it failed.
                _errorMessage.value = "Prediction failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
                return@launch
            }

            applyPredictionToMatch(matchId, prediction)
        }
    }

    /** Writes a prediction onto its fixture in the country tree. */
    private fun applyPredictionToMatch(
        matchId: Int,
        prediction: com.example.models.PredictionResult
    ) {
        _countries.update { currentCountries ->
            currentCountries.map { country ->
                country.copy(
                    leagues = country.leagues.map { league ->
                        league.copy(
                            matches = league.matches.map { m ->
                                if (m.id == matchId) m.copy(prediction = prediction) else m
                            }
                        )
                    }
                )
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
        // Never rebuild while a run is in flight: `runAgentBatch` iterates a snapshot
        // and writes back by match id, so replacing the list mid-run would discard
        // every status it had already written.
        if (_isAgentRunning.value) return

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
                                prediction = match.prediction,
                                homeTeamId = match.homeTeamId,
                                awayTeamId = match.awayTeamId,
                                leagueId = match.leagueId ?: league.id,
                                season = match.season,
                                countryName = match.countryName ?: country.name,
                                round = match.round,
                                kickoffEpoch = match.kickoffEpoch
                            )
                        )
                    }
                }
            }
        }

        // If no matches explicitly picked, populate with the next upcoming matches.
        // Sorted by kickoff so "next" is actually next, rather than whatever order
        // the country grouping happened to produce.
        if (items.isEmpty()) {
            _countries.value.forEach { country ->
                country.leagues.forEach { league ->
                    league.matches.forEach { match ->
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
                                prediction = match.prediction,
                                homeTeamId = match.homeTeamId,
                                awayTeamId = match.awayTeamId,
                                leagueId = match.leagueId ?: league.id,
                                season = match.season,
                                countryName = match.countryName ?: country.name,
                                round = match.round,
                                kickoffEpoch = match.kickoffEpoch
                            )
                        )
                    }
                }
            }
            // Sort on kickoff epoch, not the "HH:mm" display string — that string
            // sorts "09:00 tomorrow" before "21:00 today", so "next 6 matches" was
            // whatever happened to look smallest as text.
            val upcoming = items
                .sortedBy { it.kickoffEpoch ?: Long.MAX_VALUE }
                .take(6)
            items.clear()
            items.addAll(upcoming)
        }

        // Carry over anything already predicted or already failed. This runs from a
        // LaunchedEffect, so navigating away from the agent screen and back re-entered
        // it with every row reset to PENDING — the predictions were still in
        // `_countries`, but the queue looked untouched and the run appeared to have
        // never happened.
        val previous = _batchMatchItems.value.associateBy { it.matchId }
        val merged = items.map { fresh ->
            val old = previous[fresh.matchId] ?: return@map fresh
            if (old.status == com.example.models.BatchItemStatus.PENDING) fresh
            else fresh.copy(
                status = old.status,
                currentAgentAction = old.currentAgentAction,
                prediction = old.prediction ?: fresh.prediction,
                failureReason = old.failureReason,
                isSelected = old.isSelected
            )
        }

        _batchMatchItems.value = merged
        _agentLogs.value = listOf(
            com.example.models.AgentStreamLog(
                timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                message = "Agent initialized with ${merged.size} matches in queue. Ready for autonomous execution.",
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

    /**
     * Adds scraped team news to [ctx] only when Firecrawl genuinely returned
     * something. The service used to synthesise a success string on an empty
     * result, which was then injected into the prompt as real intel.
     */
    private suspend fun withFirecrawlIntel(
        ctx: com.example.models.MatchContext
    ): com.example.models.MatchContext {
        val firecrawlKey = keyManager.getActiveManagedKey(com.example.keymanager.ApiRole.FIRECRAWL)
            ?: return ctx
        if (firecrawlKey.key.isBlank()) return ctx

        val result = com.example.network.FirecrawlService.searchMatchNews(
            ctx.homeTeam, ctx.awayTeam, firecrawlKey
        )
        val intel = result.getOrNull()
        return if (intel != null) {
            keyManager.reportKeySuccess(com.example.keymanager.ApiRole.FIRECRAWL, firecrawlKey.key)
            ctx.copy(webIntel = intel, sources = ctx.sources + "scraped team news")
        } else {
            reportFirecrawlFailure(firecrawlKey, result.exceptionOrNull())
            ctx
        }
    }

    /**
     * Routes a provider failure to the right key-manager action. The old code
     * always passed `isAuthError = false`, so a 429 never triggered a cooldown and
     * a 401 never retired a dead key — key rotation existed but did nothing.
     */
    private fun reportPredictionFailure(
        key: com.example.keymanager.ManagedApiKey,
        error: Throwable?
    ) {
        val http = error as? com.example.network.ApiHttpException
        when {
            http?.isRateLimit == true -> keyManager.reportKeyRateLimited(
                com.example.keymanager.ApiRole.OPENAI_COMPATIBLE, key.key, cooldownSeconds = 300
            )
            http?.isAuthError == true -> keyManager.reportKeyError(
                com.example.keymanager.ApiRole.OPENAI_COMPATIBLE, key.key, isAuthError = true
            )
            else -> keyManager.reportKeyError(
                com.example.keymanager.ApiRole.OPENAI_COMPATIBLE, key.key, isAuthError = false
            )
        }
    }

    private fun reportFirecrawlFailure(
        key: com.example.keymanager.ManagedApiKey,
        error: Throwable?
    ) {
        val http = error as? com.example.network.ApiHttpException
        when {
            http?.isRateLimit == true -> keyManager.reportKeyRateLimited(
                com.example.keymanager.ApiRole.FIRECRAWL, key.key, cooldownSeconds = 300
            )
            http?.isAuthError == true -> keyManager.reportKeyError(
                com.example.keymanager.ApiRole.FIRECRAWL, key.key, isAuthError = true
            )
            else -> keyManager.reportKeyError(
                com.example.keymanager.ApiRole.FIRECRAWL, key.key, isAuthError = false
            )
        }
    }

    fun startAgentPredictionLoop() {
        if (_isAgentRunning.value) return
        _isAgentRunning.value = true

        viewModelScope.launch {
            // try/finally: the old loop reset these flags only on the happy path, so
            // a single thrown exception left the UI permanently stuck on "running".
            try {
                runAgentBatch()
            } catch (e: Exception) {
                addLog("⛔ Agent run aborted: ${e.message}", "WARN")
            } finally {
                _currentActivePredictingIndex.value = -1
                _isAgentRunning.value = false
            }
        }
    }

    private suspend fun runAgentBatch() {
        // Iterate a snapshot. The old loop indexed into the live flow while also
        // updating it, which threw IndexOutOfBoundsException if the list shrank.
        val queue = _batchMatchItems.value
        val selected = queue.filter { it.isSelected }
        addLog("⚡ Starting agent run over ${selected.size} selected matches...", "INFO")

        val openAiManagedKey = keyManager.getActiveManagedKey(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE)
        if (openAiManagedKey == null || openAiManagedKey.key.isBlank()) {
            addLog("⛔ No AI provider key configured. Add one in Settings — predictions cannot be generated without it.", "WARN")
            return
        }

        val footballKey = keyManager.getActiveKey(com.example.keymanager.ApiRole.API_FOOTBALL)
        if (footballKey.isNullOrBlank()) {
            addLog("⚠️ No API-Football key: predicting without form, table, H2H or odds. Confidence will be capped accordingly.", "WARN")
        }

        val brainModelName = openAiManagedKey.modelName.takeIf { it.isNotBlank() }
            ?: keyManager.activeBrainModel
        val predictionKey = openAiManagedKey.copy(modelName = brainModelName)

        contextRepository.beginBatch()
        var succeeded = 0
        var failed = 0
        var reused = 0

        for ((position, item) in queue.withIndex()) {
            if (!item.isSelected) continue

            _currentActivePredictingIndex.value = position

            // Reuse a persisted prediction for this fixture rather than paying for it
            // again. This is checked before the quota is consumed and before any
            // network call, because the expensive part of a run is the per-fixture
            // context fetch + scrape + completion, and none of it is needed if we
            // already have the answer.
            val existing = item.prediction ?: cachedPredictions[item.matchId]
            if (existing != null) {
                reused++
                updateBatchItem(item.matchId) {
                    it.copy(
                        status = com.example.models.BatchItemStatus.FINISHED,
                        currentAgentAction = "${existing.recommendedBet} @ ${existing.odds ?: "no price"} (cached)",
                        prediction = existing,
                        failureReason = null
                    )
                }
                addLog("♻️ ${item.homeTeam} vs ${item.awayTeam} ➔ reusing stored prediction", "INFO")
                continue
            }

            userManager.consumePredictionQuota()

            updateBatchItem(item.matchId) {
                it.copy(
                    status = com.example.models.BatchItemStatus.PREDICTING,
                    currentAgentAction = "Gathering table, form, H2H and market for ${it.homeTeam} vs ${it.awayTeam}...",
                    failureReason = null
                )
            }

            // Step 1: real evidence, under the shared batch budget.
            val baseCtx = if (!footballKey.isNullOrBlank()) {
                contextRepository.buildContext(
                    fixtureId = item.matchId,
                    homeTeam = item.homeTeam,
                    awayTeam = item.awayTeam,
                    homeTeamId = item.homeTeamId,
                    awayTeamId = item.awayTeamId,
                    leagueId = item.leagueId,
                    leagueName = item.leagueName,
                    country = item.countryName ?: "",
                    season = item.season,
                    round = item.round,
                    apiKey = footballKey,
                    budget = BATCH_REQUEST_BUDGET
                )
            } else {
                com.example.models.MatchContext(
                    fixtureId = item.matchId,
                    homeTeam = item.homeTeam,
                    awayTeam = item.awayTeam,
                    leagueName = item.leagueName,
                    country = item.countryName ?: "",
                    round = item.round
                )
            }

            addLog(
                if (baseCtx.sources.isEmpty())
                    "⚠️ [${item.homeTeam} vs ${item.awayTeam}] No data retrieved — prediction will be marked unsupported"
                else
                    "📊 [${item.homeTeam} vs ${item.awayTeam}] ${baseCtx.sources.joinToString(", ")} (evidence: ${baseCtx.evidenceLevel})",
                "SEARCH"
            )

            // Step 2: optional scraped news, only if it actually came back.
            updateBatchItem(item.matchId) {
                it.copy(currentAgentAction = "Checking for late team news...")
            }
            val ctx = withFirecrawlIntel(baseCtx)

            // Step 3: the model call.
            updateBatchItem(item.matchId) {
                it.copy(currentAgentAction = "Pricing the match with $brainModelName...")
            }
            val result = com.example.network.OpenAiService.generatePrediction(
                ctx = ctx,
                managedKey = predictionKey,
                allowedBetTypes = _selectedBetTypes.value.toList()
            )
            val prediction = result.getOrNull()

            if (prediction == null) {
                failed++
                val err = result.exceptionOrNull()
                reportPredictionFailure(openAiManagedKey, err)
                // No fabricated fallback. A visible failure beats an invented pick
                // that the user cannot distinguish from real analysis.
                updateBatchItem(item.matchId) {
                    it.copy(
                        status = com.example.models.BatchItemStatus.FAILED,
                        currentAgentAction = "Prediction failed",
                        failureReason = err?.message ?: "unknown error"
                    )
                }
                addLog("❌ [${item.homeTeam} vs ${item.awayTeam}] ${err?.message ?: "prediction failed"}", "WARN")

                // A dead or rate-limited key will fail for every remaining fixture
                // too; stop rather than burning the whole queue against it.
                val http = err as? com.example.network.ApiHttpException
                if (http?.isAuthError == true) {
                    addLog("⛔ Provider rejected the key (HTTP ${http.code}). Stopping run.", "WARN")
                    break
                }
                delay(400)
                continue
            }

            succeeded++
            keyManager.reportKeySuccess(com.example.keymanager.ApiRole.OPENAI_COMPATIBLE, openAiManagedKey.key)
            // Persist immediately, per fixture, not at the end of the run. A run that
            // is killed halfway through has still paid for the predictions it made.
            rememberPrediction(item.matchId, prediction)

            updateBatchItem(item.matchId) {
                it.copy(
                    status = com.example.models.BatchItemStatus.FINISHED,
                    currentAgentAction = "${prediction.recommendedBet} @ ${prediction.odds ?: "no price"}",
                    prediction = prediction,
                    failureReason = null
                )
            }

            applyPredictionToMatch(item.matchId, prediction)

            val edgeNote = prediction.edgePercent?.let { String.format(Locale.US, ", edge %+.1f pts", it) }.orEmpty()
            addLog(
                "✅ ${item.homeTeam} vs ${item.awayTeam} ➔ ${prediction.recommendedBet} " +
                        "(${prediction.confidence}%$edgeNote)",
                "SUCCESS"
            )
            delay(300)
        }

        addLog(
            buildString {
                append("🎯 Run complete: $succeeded predicted")
                if (reused > 0) append(", $reused reused from cache")
                append(", $failed failed. ")
                append("API-Football requests used: ${contextRepository.requestsSpent}.")
            },
            if (failed == 0) "SUCCESS" else "INFO"
        )

        val generatedSlip = saveAndBuildSlip()
        if (generatedSlip == null) {
            addLog("ℹ️ No predictions succeeded, so no slip was created.", "WARN")
        } else {
            addLog(
                "💾 Slip ${generatedSlip.slipId}: ${generatedSlip.items.size} legs, " +
                        "combined odds ${generatedSlip.totalCombinedOdds}, " +
                        "joint chance ${generatedSlip.jointProbability}%",
                "SUCCESS"
            )
        }
    }

    /** Updates one batch row by match id rather than by index. */
    private fun updateBatchItem(
        matchId: Int,
        transform: (com.example.models.AgentBatchMatchItem) -> com.example.models.AgentBatchMatchItem
    ) {
        _batchMatchItems.update { list ->
            list.map { if (it.matchId == matchId) transform(it) else it }
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

    // `generateSmartMockPrediction` was removed. It picked a tip at random from a
    // hardcoded list, assigned a confidence that was a constant per bet type, and
    // derived odds from `homeTeam.length % 5` — then that output was persisted,
    // uploaded to Firestore, and displayed identically to a real model prediction.
    // There is no way for a user to tell the two apart, so the honest options are
    // a real prediction or a visible failure.

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
        // Stored predictions go too. Leaving them would make a "clear cache" that
        // silently kept serving the old picks against freshly fetched fixtures.
        predictionStore.clear()
        cachedPredictions.clear()
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
                        val pick = itObj.optString("recommendedBet")
                        itemsList.add(
                            com.example.models.PredictedBetItem(
                                matchId = itObj.optInt("matchId"),
                                homeTeam = itObj.optString("homeTeam"),
                                awayTeam = itObj.optString("awayTeam"),
                                homeLogo = if (itObj.has("homeLogo")) itObj.optString("homeLogo") else null,
                                awayLogo = if (itObj.has("awayLogo")) itObj.optString("awayLogo") else null,
                                leagueName = itObj.optString("leagueName"),
                                startTime = itObj.optString("startTime"),
                                recommendedBet = pick,
                                confidence = itObj.optInt("confidence", 0),
                                rationale = itObj.optString("rationale"),
                                // No "1.75" default: a leg saved without a price had
                                // no price, and inventing one corrupts the ROI history.
                                simulatedOdds = itObj.optString("simulatedOdds").takeIf { it.isNotBlank() },
                                betTypeCategory = itObj.optString("betTypeCategory")
                                    .takeIf { it.isNotBlank() } ?: extractBetTypeCategory(pick),
                                isModelBacked = itObj.optBoolean("isModelBacked", false),
                                edgePercent = if (itObj.has("edgePercent")) itObj.optDouble("edgePercent") else null,
                                isMarketPrice = itObj.optBoolean("isMarketPrice", false),
                                outcome = parseOutcome(itObj.optString("outcome")),
                                finalHomeScore = if (itObj.has("finalHomeScore")) itObj.optInt("finalHomeScore") else null,
                                finalAwayScore = if (itObj.has("finalAwayScore")) itObj.optInt("finalAwayScore") else null,
                                settledAt = itObj.optLong("settledAt", 0L),
                                kickoffEpoch = itObj.optLong("kickoffEpoch", 0L).takeIf { it > 0L }
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
                                averageConfidence = obj.optInt("averageConfidence", 0),
                                totalCombinedOdds = obj.optString("totalCombinedOdds", "—"),
                                currencyCode = obj.optString("currencyCode", _selectedCurrency.value.code),
                                currencySymbol = obj.optString("currencySymbol", _selectedCurrency.value.symbol),
                                budgetStake = obj.optDouble("budgetStake", _budget.value.toDouble()).toFloat(),
                                estimatedPayout = obj.optDouble("estimatedPayout", 0.0),
                                potentialProfit = obj.optDouble("potentialProfit", 0.0),
                                targetMin = obj.optDouble("targetMin", _moneyRange.value.start.toDouble()).toFloat(),
                                targetMax = obj.optDouble("targetMax", _moneyRange.value.endInclusive.toDouble()).toFloat(),
                                jointProbability = obj.optInt("jointProbability", 0),
                                outcome = parseOutcome(obj.optString("outcome")),
                                settledAt = obj.optLong("settledAt", 0L)
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

    private fun parseOutcome(raw: String?): com.example.models.BetOutcome =
        try {
            if (raw.isNullOrBlank()) com.example.models.BetOutcome.PENDING
            else com.example.models.BetOutcome.valueOf(raw)
        } catch (e: Exception) {
            com.example.models.BetOutcome.PENDING
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
                obj.put("jointProbability", slip.jointProbability)
                obj.put("outcome", slip.outcome.name)
                obj.put("settledAt", slip.settledAt)

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
                    item.simulatedOdds?.let { itObj.put("simulatedOdds", it) }
                    item.betTypeCategory?.let { itObj.put("betTypeCategory", it) }
                    itObj.put("isModelBacked", item.isModelBacked)
                    item.edgePercent?.let { itObj.put("edgePercent", it) }
                    itObj.put("isMarketPrice", item.isMarketPrice)
                    itObj.put("outcome", item.outcome.name)
                    item.finalHomeScore?.let { itObj.put("finalHomeScore", it) }
                    item.finalAwayScore?.let { itObj.put("finalAwayScore", it) }
                    itObj.put("settledAt", item.settledAt)
                    item.kickoffEpoch?.let { itObj.put("kickoffEpoch", it) }
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

    /**
     * Builds a slip from whatever real predictions exist.
     *
     * Differences from the previous version, all of which affected the numbers the
     * user sees:
     *  - No mock-prediction fallback. If nothing was predicted, no slip is created.
     *  - Legs are ranked by measured edge and expected value, not by confidence, and
     *    trimmed to [MAX_SLIP_LEGS]. Ranking on confidence alone selects exactly the
     *    short-priced favourites where the bookmaker margin is worst.
     *  - Combined odds are "—" when any leg has no real price, instead of silently
     *    substituting 1.6 per leg and printing a precise-looking payout.
     *  - Joint probability is the product of the legs, not their mean.
     */
    fun saveAndBuildSlip(): com.example.models.SavedPredictionSlip? {
        val batchList = _batchMatchItems.value.filter { it.isSelected }

        val fromBatch = batchList.mapNotNull { item ->
            item.prediction?.let {
                toBetItem(
                    item.matchId, item.homeTeam, item.awayTeam, item.homeLogo,
                    item.awayLogo, item.leagueName, item.startTime, it, item.kickoffEpoch
                )
            }
        }

        // Fall back to fixtures predicted individually outside the batch flow.
        val candidates = if (fromBatch.isNotEmpty()) fromBatch else {
            _countries.value.flatMap { country ->
                country.leagues.flatMap { league ->
                    league.matches.mapNotNull { match ->
                        match.prediction?.let {
                            toBetItem(
                                match.id, match.homeTeam, match.awayTeam, match.homeLogo,
                                match.awayLogo, league.name, match.startTime, it,
                                match.kickoffEpoch
                            )
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            android.util.Log.w("PredictorViewModel", "saveAndBuildSlip: no predictions available; not creating a slip.")
            return null
        }

        val finishedItems = com.example.network.SlipMath.selectBestLegs(candidates, MAX_SLIP_LEGS)

        val totalMatches = finishedItems.size
        val avgConf = finishedItems.map { it.confidence }.average().toInt()
        val jointP = com.example.network.SlipMath.jointProbability(finishedItems)
        val combined = com.example.network.SlipMath.combinedOdds(finishedItems)

        val oddsStr = combined?.let { String.format(Locale.US, "%.2f", it.coerceAtMost(9999.0)) } ?: "—"

        val curr = _selectedCurrency.value
        val budgetStake = _budget.value
        // Only a real combined price yields a real payout figure.
        val estimatedPayout = combined?.let { budgetStake * it } ?: 0.0
        val potentialProfit = (estimatedPayout - budgetStake).coerceAtLeast(0.0)
        val targetMin = _moneyRange.value.start
        val targetMax = _moneyRange.value.endInclusive

        val newSlip = com.example.models.SavedPredictionSlip(
            // UUID suffix: `currentTimeMillis() % 1000000` wraps every ~16.7 minutes,
            // and slips are de-duplicated by slipId, so a collision silently discarded
            // a slip.
            slipId = "SLIP-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(6)}",
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
            targetMax = targetMax,
            // Rounded, not truncated. A long slip's joint chance is often a fraction
            // of a percent and `toInt()` floors it to a flat 0%, which reads as
            // "impossible" rather than "very unlikely".
            jointProbability = jointP?.let { Math.round(it * 100).toInt() } ?: 0
        )

        _currentSlip.value = newSlip
        _savedSlipsHistory.update { (listOf(newSlip) + it).distinctBy { s -> s.slipId }.filter { s -> s.items.isNotEmpty() } }
        persistSlips()
        syncSlipToFirestore(newSlip)

        return newSlip
    }

    private fun toBetItem(
        matchId: Int,
        homeTeam: String,
        awayTeam: String,
        homeLogo: String?,
        awayLogo: String?,
        leagueName: String,
        startTime: String,
        pred: PredictionResult,
        kickoffEpoch: Long? = null
    ) = com.example.models.PredictedBetItem(
        matchId = matchId,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        homeLogo = homeLogo,
        awayLogo = awayLogo,
        leagueName = leagueName,
        startTime = startTime,
        recommendedBet = pred.recommendedBet,
        confidence = pred.confidence,
        rationale = pred.rationale,
        // Prefer the real market price; fall back to the model's fair odds, and
        // record which of the two this is so the UI can be honest about it.
        simulatedOdds = pred.marketOdds ?: pred.odds,
        betTypeCategory = pred.betType?.takeIf { it.isNotBlank() }
            ?: extractBetTypeCategory(pred.recommendedBet),
        isModelBacked = pred.isModelBacked,
        edgePercent = pred.edgePercent,
        isMarketPrice = pred.marketOdds != null,
        kickoffEpoch = kickoffEpoch
    )

    // ==================== SETTLEMENT & HIT-RATE TRACKING ====================

    private val _accuracyStats = MutableStateFlow(com.example.models.AccuracyStats())
    val accuracyStats: StateFlow<com.example.models.AccuracyStats> = _accuracyStats.asStateFlow()

    private val _isSettling = MutableStateFlow(false)
    val isSettling: StateFlow<Boolean> = _isSettling.asStateFlow()

    /**
     * Fetches final scores for unsettled legs and grades them.
     *
     * This is what makes the confidence numbers mean anything: until predictions are
     * checked against results, a claimed 80% is unfalsifiable and the model can be
     * arbitrarily overconfident without anything contradicting it.
     *
     * Cost is bounded — [ResultsRepository] batches 20 fixture ids per request and
     * this only ever issues [SETTLEMENT_REQUEST_BUDGET] of them, so a full pass is
     * ~2 API calls regardless of backlog size.
     */
    fun settlePendingPredictions() {
        if (_isSettling.value) return

        val apiKey = keyManager.getActiveKey(com.example.keymanager.ApiRole.API_FOOTBALL)
        if (apiKey.isNullOrBlank()) {
            _errorMessage.value = "An API-Football key is required to check results."
            return
        }

        // Only ask about fixtures that can plausibly have finished. A match needs
        // ~2h from kickoff, so anything more recent than that is guaranteed to come
        // back NS or in-play — and with only SETTLEMENT_REQUEST_BUDGET * 20 ids per
        // pass, one round of upcoming fixtures could consume the entire budget and
        // leave a genuinely settleable backlog ungraded.
        //
        // Legs with no recorded kickoff (saved before the field existed) are included
        // rather than dropped, since excluding them would make them permanently
        // unsettleable. Ordering is oldest-first so the truncation at the budget
        // boundary keeps the most likely-finished legs.
        val nowSeconds = System.currentTimeMillis() / 1000
        val settleableAfter = nowSeconds - MIN_SECONDS_AFTER_KICKOFF

        val pending = _savedSlipsHistory.value
            .flatMap { it.items }
            .filter { it.outcome == com.example.models.BetOutcome.PENDING }
            .filter { leg -> leg.kickoffEpoch?.let { it <= settleableAfter } ?: true }
            .sortedBy { it.kickoffEpoch ?: 0L }
            .map { it.matchId }
            .distinct()

        if (pending.isEmpty()) {
            recomputeAccuracy()
            return
        }

        viewModelScope.launch {
            _isSettling.value = true
            try {
                val results = resultsRepository.fetchResults(
                    fixtureIds = pending,
                    apiKey = apiKey,
                    maxRequests = SETTLEMENT_REQUEST_BUDGET
                )
                if (results.isEmpty()) return@launch

                applySettlement(results)
                keyManager.reportKeySuccess(com.example.keymanager.ApiRole.API_FOOTBALL, apiKey)
            } catch (e: Exception) {
                android.util.Log.w("PredictorViewModel", "settlement failed: ${e.message}")
            } finally {
                _isSettling.value = false
            }
        }
    }

    private fun applySettlement(results: Map<Int, com.example.network.FixtureResult>) {
        val now = System.currentTimeMillis()

        _savedSlipsHistory.update { slips ->
            slips.map { slip ->
                var changed = false
                val gradedItems = slip.items.map { leg ->
                    if (leg.outcome != com.example.models.BetOutcome.PENDING) return@map leg
                    val result = results[leg.matchId] ?: return@map leg

                    val outcome = com.example.network.BetSettlement.settle(
                        pick = leg.recommendedBet,
                        homeTeam = result.homeTeam.ifBlank { leg.homeTeam },
                        awayTeam = result.awayTeam.ifBlank { leg.awayTeam },
                        homeScore = result.homeScore,
                        awayScore = result.awayScore,
                        statusShort = result.statusShort,
                        halftimeHome = result.halftimeHome,
                        halftimeAway = result.halftimeAway
                    )
                    if (outcome == com.example.models.BetOutcome.PENDING) return@map leg

                    changed = true
                    leg.copy(
                        outcome = outcome,
                        finalHomeScore = result.homeScore,
                        finalAwayScore = result.awayScore,
                        settledAt = now,
                        // Backfill kickoff for legs saved before the field existed, so
                        // future passes can budget around them properly.
                        kickoffEpoch = leg.kickoffEpoch ?: result.kickoffEpoch
                    )
                }

                if (!changed) slip else {
                    val slipOutcome = com.example.network.SlipMath.slipOutcome(gradedItems)
                    slip.copy(
                        items = gradedItems,
                        outcome = slipOutcome,
                        settledAt = if (slipOutcome == com.example.models.BetOutcome.PENDING) slip.settledAt else now
                    )
                }
            }
        }

        // Keep the currently displayed slip in sync with its graded version.
        _currentSlip.value?.let { current ->
            _currentSlip.value = _savedSlipsHistory.value.firstOrNull { it.slipId == current.slipId } ?: current
        }

        persistSlips()
        recomputeAccuracy()
        _savedSlipsHistory.value.forEach { syncSlipToFirestore(it) }
    }

    /** Recomputes realised hit rate, ROI and calibration from settled legs. */
    fun recomputeAccuracy() {
        _accuracyStats.value = com.example.network.SlipMath.accuracyFrom(_savedSlipsHistory.value)
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
                    "jointProbability" to slip.jointProbability,
                    "outcome" to slip.outcome.name,
                    "settledAt" to slip.settledAt,
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
                            // Empty string rather than an invented price when unpriced;
                            // Firestore map values must be non-null.
                            "simulatedOdds" to (item.simulatedOdds ?: ""),
                            "betTypeCategory" to (item.betTypeCategory ?: ""),
                            "isModelBacked" to item.isModelBacked,
                            "edgePercent" to (item.edgePercent ?: 0.0),
                            "isMarketPrice" to item.isMarketPrice,
                            "outcome" to item.outcome.name,
                            "finalHomeScore" to (item.finalHomeScore ?: -1),
                            "finalAwayScore" to (item.finalAwayScore ?: -1),
                            "settledAt" to item.settledAt,
                            "kickoffEpoch" to (item.kickoffEpoch ?: 0L)
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
            val pick = itMap["recommendedBet"]?.toString() ?: ""
            com.example.models.PredictedBetItem(
                matchId = (itMap["matchId"] as? Number)?.toInt() ?: 0,
                homeTeam = itMap["homeTeam"]?.toString() ?: "",
                awayTeam = itMap["awayTeam"]?.toString() ?: "",
                homeLogo = itMap["homeLogo"]?.toString()?.ifBlank { null },
                awayLogo = itMap["awayLogo"]?.toString()?.ifBlank { null },
                leagueName = itMap["leagueName"]?.toString() ?: "",
                startTime = itMap["startTime"]?.toString() ?: "",
                recommendedBet = pick,
                confidence = (itMap["confidence"] as? Number)?.toInt() ?: 0,
                rationale = itMap["rationale"]?.toString() ?: "",
                simulatedOdds = itMap["simulatedOdds"]?.toString()?.ifBlank { null },
                betTypeCategory = itMap["betTypeCategory"]?.toString()?.ifBlank { null }
                    ?: extractBetTypeCategory(pick),
                isModelBacked = itMap["isModelBacked"] as? Boolean ?: false,
                edgePercent = (itMap["edgePercent"] as? Number)?.toDouble()?.takeIf { it != 0.0 },
                isMarketPrice = itMap["isMarketPrice"] as? Boolean ?: false,
                outcome = parseOutcome(itMap["outcome"]?.toString()),
                finalHomeScore = (itMap["finalHomeScore"] as? Number)?.toInt()?.takeIf { it >= 0 },
                finalAwayScore = (itMap["finalAwayScore"] as? Number)?.toInt()?.takeIf { it >= 0 },
                settledAt = (itMap["settledAt"] as? Number)?.toLong() ?: 0L,
                kickoffEpoch = (itMap["kickoffEpoch"] as? Number)?.toLong()?.takeIf { it > 0L }
            )
        }

        if (items.isEmpty()) return null

        val totalMatches = doc.getLong("totalMatches")?.toInt() ?: items.size
        val totalOdds = doc.getString("totalCombinedOdds") ?: "—"
        val bStake = doc.getDouble("budgetStake")?.toFloat() ?: _budget.value
        // No 2.50 stand-in: if the document has no payout, leave it at zero rather
        // than deriving one from a placeholder price.
        val estPay = doc.getDouble("estimatedPayout")
            ?: totalOdds.toDoubleOrNull()?.let { bStake * it } ?: 0.0
        val potProf = doc.getDouble("potentialProfit") ?: (estPay - bStake).coerceAtLeast(0.0)

        return com.example.models.SavedPredictionSlip(
            slipId = doc.getString("slipId") ?: doc.id,
            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
            dateString = doc.getString("dateString") ?: "",
            items = items,
            totalMatches = totalMatches,
            averageConfidence = doc.getLong("averageConfidence")?.toInt() ?: 0,
            totalCombinedOdds = totalOdds,
            currencyCode = doc.getString("currencyCode") ?: _selectedCurrency.value.code,
            currencySymbol = doc.getString("currencySymbol") ?: _selectedCurrency.value.symbol,
            budgetStake = bStake,
            estimatedPayout = estPay,
            potentialProfit = potProf,
            targetMin = doc.getDouble("targetMin")?.toFloat() ?: _moneyRange.value.start,
            targetMax = doc.getDouble("targetMax")?.toFloat() ?: _moneyRange.value.endInclusive,
            // Recompute rather than trusting a stored value written by an older build
            // that averaged the legs instead of multiplying them.
            jointProbability = doc.getLong("jointProbability")?.toInt()
                ?: com.example.network.SlipMath.jointProbability(items)?.let { (it * 100).toInt() }
                ?: 0,
            outcome = parseOutcome(doc.getString("outcome")),
            settledAt = doc.getLong("settledAt") ?: 0L
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

