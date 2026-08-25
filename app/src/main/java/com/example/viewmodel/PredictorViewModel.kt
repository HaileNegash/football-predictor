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

    private val _apiFootballKey = MutableStateFlow(
        prefs.getString("api_football_key", null).let { saved ->
            if (saved == "87d20a4f0d5684ae37e1e8497be4e3b7") {
                prefs.edit().remove("api_football_key").apply()
                null
            } else saved
        }?.takeIf { it.isNotBlank() } ?: BuildConfig.API_FOOTBALL_KEY
    )
    val apiFootballKey: StateFlow<String> = _apiFootballKey.asStateFlow()

    private val _countries = MutableStateFlow<List<Country>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val countries: StateFlow<List<Country>> = _countries.asStateFlow()

    val searchResults: StateFlow<List<com.example.models.SearchItem>> = combine(_countries, _searchQuery) { allCountries, query ->
        if (query.isBlank()) return@combine emptyList()
        val lower = query.lowercase(Locale.getDefault())
        val results = mutableListOf<com.example.models.SearchItem>()
        val seen = mutableSetOf<String>()

    val mockGlobalSearchData = listOf(
        com.example.models.SearchItem("m1", "Manchester Utd", "SOCCER, ENGLAND", "https://media.api-sports.io/football/teams/33.png", "TEAM"),
        com.example.models.SearchItem("m2", "Manchester Utd U19", "SOCCER, ENGLAND", "https://media.api-sports.io/football/teams/33.png", "TEAM"),
        com.example.models.SearchItem("m3", "Manchester Utd U23", "SOCCER, ENGLAND", "https://media.api-sports.io/football/teams/33.png", "TEAM"),
        com.example.models.SearchItem("m4", "Manchester Utd W", "SOCCER, ENGLAND", "https://media.api-sports.io/football/teams/33.png", "TEAM"),
        com.example.models.SearchItem("m5", "Manchester Utd U21", "SOCCER, ENGLAND", "https://media.api-sports.io/football/teams/33.png", "TEAM"),
        com.example.models.SearchItem("m6", "Manchester Utd Legends", "SOCCER, ENGLAND", "https://media.api-sports.io/football/teams/33.png", "TEAM"),
        
        com.example.models.SearchItem("e1", "England", "SOCCER, EUROPE", "https://media.api-sports.io/flags/gb.svg", "COUNTRY"),
        com.example.models.SearchItem("e2", "England U21", "SOCCER, EUROPE", "https://media.api-sports.io/flags/gb.svg", "TEAM"),
        com.example.models.SearchItem("e3", "England W", "SOCCER, EUROPE", "https://media.api-sports.io/flags/gb.svg", "TEAM"),
        com.example.models.SearchItem("e4", "England U19", "SOCCER, EUROPE", "https://media.api-sports.io/flags/gb.svg", "TEAM"),
        com.example.models.SearchItem("e5", "England U20 B", "SOCCER, EUROPE", "https://media.api-sports.io/flags/gb.svg", "TEAM"),
        com.example.models.SearchItem("e6", "England U20", "SOCCER, EUROPE", "https://media.api-sports.io/flags/gb.svg", "TEAM"),
        
        com.example.models.SearchItem("p1", "Premier League", "SOCCER, ENGLAND", "https://media.api-sports.io/football/leagues/39.png", "LEAGUE"),
        com.example.models.SearchItem("p2", "Premiership", "SOCCER, SCOTLAND", "https://media.api-sports.io/football/leagues/283.png", "LEAGUE"),
        com.example.models.SearchItem("p3", "Premier League Summer Series", "SOCCER, WORLD", null, "LEAGUE"),
        com.example.models.SearchItem("p4", "Premier Division", "SOCCER, IRELAND", "https://media.api-sports.io/football/leagues/357.png", "LEAGUE"),
        com.example.models.SearchItem("p5", "Premier League", "SOCCER, EGYPT", "https://media.api-sports.io/football/leagues/233.png", "LEAGUE"),
        com.example.models.SearchItem("p6", "Premier League", "SOCCER, RUSSIA", "https://media.api-sports.io/football/leagues/235.png", "LEAGUE")
    )

        mockGlobalSearchData.forEach { mockItem ->
            if (mockItem.name.lowercase(Locale.getDefault()).contains(lower)) {
                if (seen.add(mockItem.id)) {
                    results.add(mockItem)
                }
            }
        }

        allCountries.forEach { country ->
            if (country.name.lowercase(Locale.getDefault()).contains(lower)) {
                val id = "country_${country.name}"
                if (seen.add(id)) {
                    results.add(com.example.models.SearchItem(id, country.name, "SOCCER, EUROPE", country.flagUrl, "COUNTRY"))
                }
            }
            country.leagues.forEach { league ->
                if (league.name.lowercase(Locale.getDefault()).contains(lower)) {
                    val id = "league_${league.id}"
                    if (seen.add(id)) {
                        results.add(com.example.models.SearchItem(id, league.name, "SOCCER, ${country.name.uppercase()}", league.logoUrl, "LEAGUE"))
                    }
                }
                league.matches.forEach { match ->
                    if (match.homeTeam.lowercase(Locale.getDefault()).contains(lower)) {
                        val id = "team_${match.homeTeam}"
                        if (seen.add(id)) {
                            results.add(com.example.models.SearchItem(id, match.homeTeam, "SOCCER, ${country.name.uppercase()}", match.homeLogo, "TEAM"))
                        }
                    }
                    if (match.awayTeam.lowercase(Locale.getDefault()).contains(lower)) {
                        val id = "team_${match.awayTeam}"
                        if (seen.add(id)) {
                            results.add(com.example.models.SearchItem(id, match.awayTeam, "SOCCER, ${country.name.uppercase()}", match.awayLogo, "TEAM"))
                        }
                    }
                }
            }
        }
        results.take(30)
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

    fun saveApiFootballKey(key: String) {
        prefs.edit().putString("api_football_key", key).apply()
        _apiFootballKey.value = key
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
        val apiKey = _apiFootballKey.value
        if (apiKey.isBlank()) {
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

                val response = cachedResponse ?: NetworkClient.apiFootballService.getFixtures(apiKey, dateStr)
                
                if (cachedResponse == null && (response.errors == null || (response.errors is List<*> && response.errors.isEmpty()))) {
                    try {
                        val json = NetworkClient.moshi.adapter(ApiFootballResponse::class.java).toJson(response)
                        prefs.edit().putString(cacheKey, json).apply()
                    } catch (e: Exception) {
                        // Ignore cache write errors
                    }
                }

                if (response.errors is Map<*, *>) {
                    val errorMap = response.errors as Map<*, *>
                    val errorMsg = errorMap.values.firstOrNull()?.toString() ?: "API Error"
                    
                    if (errorMsg.contains("Free plans do not have access to this date", ignoreCase = true) || errorMsg.contains("not have access to this date", ignoreCase = true)) {
                        _maxDateOffset.value = _currentDateOffset.value - 1
                        changeDateBy(-1)
                        return@launch
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
        viewModelScope.launch {
            // Find match and update state to loading prediction (omitted for brevity, just updating result)
            delay(1500) // Simulate Gemini API REST call delay
            
            _countries.update { currentCountries ->
                currentCountries.map { country ->
                    country.copy(
                        leagues = country.leagues.map { league ->
                            league.copy(
                                matches = league.matches.map { match ->
                                    if (match.id == matchId) {
                                        match.copy(
                                            prediction = PredictionResult(
                                                recommendedBet = "Home Win",
                                                confidence = 85,
                                                rationale = "Strong home form and away team injuries."
                                            )
                                        )
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

    private val _selectedBetTypes = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(availableBetTypes.toSet())
    val selectedBetTypes: kotlinx.coroutines.flow.StateFlow<Set<String>> = _selectedBetTypes.asStateFlow()

    fun toggleBetType(type: String) {
        val current = _selectedBetTypes.value
        if (current.contains(type)) {
            _selectedBetTypes.value = current - type
        } else {
            _selectedBetTypes.value = current + type
        }
    }

    fun selectAllBetTypes() {
        _selectedBetTypes.value = availableBetTypes.toSet()
    }

    fun deselectAllBetTypes() {
        _selectedBetTypes.value = emptySet()
    }

    private val _selectedSearchItems = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
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
        
        if (isSelecting) {
            _selectedSearchItems.value = current + itemsToModify
        } else {
            _selectedSearchItems.value = current - itemsToModify
        }
    }
}
