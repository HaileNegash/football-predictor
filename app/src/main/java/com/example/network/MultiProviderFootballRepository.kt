package com.example.network

import android.content.Context
import android.util.Log
import com.example.keymanager.ApiRole
import com.example.keymanager.KeyRotationManager
import com.example.models.Country
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MultiProviderFootballRepository(
    private val context: Context,
    private val keyManager: KeyRotationManager
) {
    private val tag = "MultiProviderFootball"

    suspend fun fetchFixtures(dateStr: String, dateToStr: String? = null, forceRefresh: Boolean = false): Result<List<Country>> = withContext(Dispatchers.IO) {
        // Check which keys are available in rotation / cloud vault
        val footballDataKey = keyManager.getActiveKey(ApiRole.FOOTBALL_DATA_ORG)
        val apiFootballKey = keyManager.getActiveKey(ApiRole.API_FOOTBALL)
        val sportmonksKey = keyManager.getActiveKey(ApiRole.SPORTMONKS)
        val theSportsDbKey = keyManager.getActiveKey(ApiRole.THE_SPORTS_DB)

        val targetDateTo = dateToStr ?: dateStr
        Log.i(tag, "Checking football providers: FootballData=$footballDataKey, ApiFootball=$apiFootballKey, Sportmonks=$sportmonksKey, SportsDb=$theSportsDbKey for range $dateStr to $targetDateTo")

        var lastError: Exception? = null

        // 1. Try Football-Data.org (Best free tier for 12 top leagues, supports dateFrom/dateTo up to 10 days!)
        if (!footballDataKey.isNullOrBlank()) {
            try {
                val service = NetworkClient.createRetrofit("https://api.football-data.org/v4/").create(FootballDataOrgApi::class.java)
                val response = service.getMatches(footballDataKey, dateFrom = dateStr, dateTo = targetDateTo)
                if (response.matches.isNotEmpty()) {
                    val countries = FootballDataOrgMapper.mapToCountries(response)
                    keyManager.reportKeySuccess(ApiRole.FOOTBALL_DATA_ORG, footballDataKey)
                    // Optionally enrich with The Odds API if available
                    val enriched = enrichWithTheOddsApi(countries)
                    return@withContext Result.success(enriched)
                }
            } catch (e: Exception) {
                Log.w(tag, "Football-Data.org fetch failed, trying next provider: ${e.message}")
                lastError = e
                if (e.message?.contains("429") == true || e.message?.contains("limit", ignoreCase = true) == true) {
                    keyManager.reportKeyRateLimited(ApiRole.FOOTBALL_DATA_ORG, footballDataKey)
                }
            }
        }

        // 2. Try API-Football (API-Sports)
        if (!apiFootballKey.isNullOrBlank()) {
            try {
                val response = NetworkClient.apiFootballService.getFixtures(apiFootballKey, dateStr)
                if (response.response.isNotEmpty()) {
                    val bigCountries = listOf("England", "Spain", "Germany", "Italy", "France", "World")
                    val mappedCountries = response.response.groupBy { it.league.country }
                        .map { (countryName, fixturesForCountry) ->
                            val firstFixture = fixturesForCountry.firstOrNull()
                            val countryFlag = firstFixture?.league?.flag
                            
                            val leagues = fixturesForCountry.groupBy { it.league.id }
                                .map { (leagueId, fixturesForLeague) ->
                                    val leagueInfo = fixturesForLeague.first().league
                                    val matches = fixturesForLeague.map { apiFixture ->
                                        val timeStr = apiFixture.fixture.date.takeLast(14).take(5)
                                        val matchDateStr = try {
                                            apiFixture.fixture.date.take(10)
                                        } catch (e: Exception) {
                                            dateStr
                                        }
                                        com.example.models.Match(
                                            id = apiFixture.fixture.id,
                                            homeTeam = apiFixture.teams.home.name,
                                            awayTeam = apiFixture.teams.away.name,
                                            homeLogo = apiFixture.teams.home.logo,
                                            awayLogo = apiFixture.teams.away.logo,
                                            startTime = if (timeStr.contains(":")) timeStr else "--:--",
                                            status = apiFixture.fixture.status.short,
                                            homeScore = apiFixture.goals.home,
                                            awayScore = apiFixture.goals.away,
                                            matchDate = matchDateStr
                                        )
                                    }.sortedBy { it.startTime }

                                    com.example.models.League(
                                        id = leagueId,
                                        name = leagueInfo.name,
                                        logoUrl = leagueInfo.logo,
                                        matches = matches
                                    )
                                }
                            Country(
                                name = countryName,
                                flagUrl = countryFlag,
                                leagues = leagues
                            )
                        }
                    keyManager.reportKeySuccess(ApiRole.API_FOOTBALL, apiFootballKey)
                    val enriched = enrichWithTheOddsApi(mappedCountries)
                    return@withContext Result.success(enriched)
                }
            } catch (e: Exception) {
                Log.w(tag, "API-Football fetch failed: ${e.message}")
                lastError = e
            }
        }

        // 3. Try Sportmonks
        if (!sportmonksKey.isNullOrBlank()) {
            try {
                val service = NetworkClient.createRetrofit("https://api.sportmonks.com/v3/football/").create(SportmonksApi::class.java)
                val response = service.getFixturesByDate(dateStr, sportmonksKey)
                if (response.data.isNotEmpty()) {
                    val countries = SportmonksMapper.mapToCountries(response)
                    keyManager.reportKeySuccess(ApiRole.SPORTMONKS, sportmonksKey)
                    return@withContext Result.success(enrichWithTheOddsApi(countries))
                }
            } catch (e: Exception) {
                Log.w(tag, "Sportmonks fetch failed: ${e.message}")
                lastError = e
            }
        }

        // 4. Try TheSportsDB
        if (!theSportsDbKey.isNullOrBlank() || theSportsDbKey == null) {
            val dbKey = theSportsDbKey?.ifBlank { "3" } ?: "3"
            try {
                val service = NetworkClient.createRetrofit("https://www.thesportsdb.com/api/v1/json/").create(TheSportsDbApi::class.java)
                val response = service.getEventsByDay(dbKey, dateStr)
                if (!response.events.isNullOrEmpty()) {
                    val countries = TheSportsDbMapper.mapToCountries(response)
                    if (theSportsDbKey != null) keyManager.reportKeySuccess(ApiRole.THE_SPORTS_DB, theSportsDbKey)
                    return@withContext Result.success(enrichWithTheOddsApi(countries))
                }
            } catch (e: Exception) {
                Log.w(tag, "TheSportsDB fetch failed: ${e.message}")
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("No football provider configured or all providers rate-limited. Add a key in Settings."))
    }

    private suspend fun enrichWithTheOddsApi(countries: List<Country>): List<Country> {
        val oddsKey = keyManager.getActiveKey(ApiRole.THE_ODDS_API) ?: return countries
        return try {
            val service = NetworkClient.createRetrofit("https://api.the-odds-api.com/v4/").create(TheOddsApi::class.java)
            val oddsEvents = service.getSoccerOdds(oddsKey)
            if (oddsEvents.isEmpty()) return countries

            countries.map { country ->
                country.copy(
                    leagues = country.leagues.map { league ->
                        league.copy(
                            matches = league.matches.map { match ->
                                val odds = TheOddsApiMatcher.findOddsForMatch(match.homeTeam, match.awayTeam, oddsEvents)
                                if (odds.isNotEmpty() && match.prediction == null) {
                                    val bestBet = when {
                                        (odds["Home"] ?: 99.0) < 1.65 -> "${match.homeTeam} to Win"
                                        (odds["Over 2.5"] ?: 99.0) < 1.70 -> "Over 2.5 Goals"
                                        (odds["Away"] ?: 99.0) < 1.65 -> "${match.awayTeam} to Win"
                                        else -> "Double Chance 1X / 12"
                                    }
                                    val price = odds.values.minOrNull() ?: 1.75
                                    match.copy(
                                        prediction = com.example.models.PredictionResult(
                                            recommendedBet = bestBet,
                                            confidence = 82,
                                            rationale = "Live Bookmaker market consensus via The Odds API.",
                                            odds = String.format(java.util.Locale.US, "%.2f", price),
                                            betType = "Real Bookmaker Line"
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
        } catch (e: Exception) {
            Log.d(tag, "The Odds API enrichment skipped: ${e.message}")
            countries
        }
    }
}
