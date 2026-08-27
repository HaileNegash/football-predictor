package com.example.network

import android.content.Context
import android.util.Log
import com.example.models.ApiFixture
import com.example.models.MatchContext
import com.example.models.MarketOdds
import com.example.models.StandingRow
import com.example.models.TeamForm
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Fetches the real match context that a prediction should be grounded in, under a
 * hard request budget.
 *
 * The point of this class is that the model previously received only three
 * strings — home name, away name, league name — and was asked for a confidence
 * number, which it had no basis to produce. This supplies league position, form,
 * goal rates, head-to-head history and (where the plan allows) injuries and
 * bookmaker odds.
 *
 * Budget discipline matters because a free API-Football plan is ~100 requests/day:
 *  - Standings are fetched **once per league** and shared by every fixture in it.
 *    A 10-match slip across 4 leagues costs 4 requests, not 40.
 *  - Standings are cached for 6h, H2H for 30 days (it only changes when the teams
 *    next meet), odds for 15 min.
 *  - Endpoints that are paid-only are probed once; on 403 they are disabled for
 *    the process rather than retried per fixture.
 */
class MatchContextRepository(
    context: Context,
    private val service: ApiFootballService = NetworkClient.apiFootballService,
    private val moshi: Moshi = NetworkClient.moshi
) {
    private val tag = "MatchContextRepo"
    private val prefs = context.getSharedPreferences("match_context_cache", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /** In-memory standings cache so N fixtures in one league cost one request. */
    private val standingsMemo = mutableMapOf<String, List<StandingRow>>()

    /** Set when an endpoint returns 403/404 — plan doesn't include it. Don't retry. */
    @Volatile private var oddsAvailable = true
    @Volatile private var injuriesAvailable = true

    /** Requests consumed by the current batch, to stay inside the caller's budget. */
    private var spent = 0

    fun beginBatch() {
        spent = 0
    }

    val requestsSpent: Int get() = spent

    companion object {
        private const val STANDINGS_TTL_MS = 6 * 60 * 60 * 1000L      // table moves per matchday
        private const val H2H_TTL_MS = 30L * 24 * 60 * 60 * 1000L     // only changes when they meet
        private const val ODDS_TTL_MS = 15 * 60 * 1000L               // market drifts
    }

    /**
     * Builds context for one fixture. Never throws: any missing piece is simply
     * absent from the result, and [MatchContext.sources] records what was actually
     * obtained so the prompt can state its own evidence level.
     */
    suspend fun buildContext(
        fixture: ApiFixture,
        apiKey: String,
        budget: Int
    ): MatchContext = buildContext(
        fixtureId = fixture.fixture.id,
        homeTeam = fixture.teams.home.name,
        awayTeam = fixture.teams.away.name,
        homeTeamId = fixture.teams.home.id,
        awayTeamId = fixture.teams.away.id,
        leagueId = fixture.league.id,
        leagueName = fixture.league.name,
        country = fixture.league.country,
        season = fixture.league.season,
        round = fixture.league.round,
        apiKey = apiKey,
        budget = budget
    )

    /**
     * Same as above but driven by the UI [com.example.models.Match] model, whose
     * identifier fields are nullable because they arrive from a cached payload.
     * A null id simply means that lookup is skipped rather than guessed at.
     */
    suspend fun buildContext(
        fixtureId: Int,
        homeTeam: String,
        awayTeam: String,
        homeTeamId: Int?,
        awayTeamId: Int?,
        leagueId: Int?,
        leagueName: String,
        country: String,
        season: Int?,
        round: String?,
        apiKey: String,
        budget: Int
    ): MatchContext = withContext(Dispatchers.IO) {
        val sources = mutableListOf<String>()

        var homeRow: StandingRow? = null
        var awayRow: StandingRow? = null

        if (leagueId != null && season != null && homeTeamId != null && spent < budget) {
            val rows = standingsFor(leagueId, season, apiKey)
            if (rows.isNotEmpty()) {
                homeRow = rows.firstOrNull { it.team?.id == homeTeamId }
                awayRow = rows.firstOrNull { it.team?.id == awayTeamId }
                if (homeRow != null || awayRow != null) sources += "league table"
            }
        }

        val h2h = if (homeTeamId != null && awayTeamId != null && spent < budget) {
            headToHead(homeTeamId, awayTeamId, apiKey).also {
                if (it.isNotEmpty()) sources += "head-to-head (${it.size})"
            }
        } else emptyList()

        val injuries = if (injuriesAvailable && spent < budget) {
            injuriesFor(fixtureId, apiKey).also {
                if (it.isNotEmpty()) sources += "injury report"
            }
        } else emptyList()

        val odds = if (oddsAvailable && spent < budget) {
            oddsFor(fixtureId, apiKey)?.also { sources += "bookmaker odds" }
        } else null

        MatchContext(
            fixtureId = fixtureId,
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            leagueName = leagueName,
            country = country,
            round = round,
            homeForm = homeRow?.let { toForm(it, homeSplit = true) },
            awayForm = awayRow?.let { toForm(it, homeSplit = false) },
            headToHead = h2h,
            injuries = injuries,
            odds = odds,
            sources = sources
        )
    }

    // -------------------------------------------------------------- standings

    private suspend fun standingsFor(leagueId: Int, season: Int, apiKey: String): List<StandingRow> {
        val memoKey = "$leagueId-$season"
        mutex.withLock { standingsMemo[memoKey] }?.let { return it }

        val cacheKey = "standings_$memoKey"
        readCached<List<StandingRow>>(cacheKey, STANDINGS_TTL_MS, listType(StandingRow::class.java))?.let { cached ->
            mutex.withLock { standingsMemo[memoKey] = cached }
            return cached
        }

        return try {
            spent++
            val res = service.getStandings(apiKey, leagueId, season)
            if (!res.isSuccessful) {
                Log.w(tag, "standings HTTP ${res.code()} for league $leagueId")
                return emptyList()
            }
            // Flatten groups: cup competitions split the table into several groups.
            val rows = res.body()?.response
                ?.firstOrNull()?.league?.standings
                ?.flatten()
                .orEmpty()
            if (rows.isNotEmpty()) {
                writeCached(cacheKey, rows, listType(StandingRow::class.java))
                mutex.withLock { standingsMemo[memoKey] = rows }
            }
            rows
        } catch (e: Exception) {
            Log.w(tag, "standings failed for league $leagueId: ${e.message}")
            emptyList()
        }
    }

    private fun toForm(row: StandingRow, homeSplit: Boolean): TeamForm {
        val split = if (homeSplit) row.home else row.away
        val all = row.all
        val played = all?.played ?: 0
        return TeamForm(
            rank = row.rank,
            points = row.points,
            played = played,
            wins = all?.win,
            draws = all?.draw,
            losses = all?.lose,
            goalsFor = all?.goals?.scored,
            goalsAgainst = all?.goals?.conceded,
            recentForm = row.form,
            venuePlayed = split?.played,
            venueWins = split?.win,
            venueGoalsFor = split?.goals?.scored,
            venueGoalsAgainst = split?.goals?.conceded
        )
    }

    // -------------------------------------------------------------- H2H

    private suspend fun headToHead(homeId: Int, awayId: Int, apiKey: String): List<String> {
        // Order-independent key so A-vs-B and B-vs-A share one cache entry.
        val pair = listOf(homeId, awayId).sorted()
        val cacheKey = "h2h_${pair[0]}_${pair[1]}"
        readCached<List<String>>(cacheKey, H2H_TTL_MS, listType(String::class.java))?.let { return it }

        return try {
            spent++
            val res = service.getHeadToHead(apiKey, "$homeId-$awayId", last = 8)
            if (!res.isSuccessful) return emptyList()
            val lines = res.body()?.response.orEmpty()
                .filter { it.fixture.status.short == "FT" }
                .map { f ->
                    val date = f.fixture.date.take(10)
                    "$date ${f.teams.home.name} ${f.goals.home ?: 0}-${f.goals.away ?: 0} ${f.teams.away.name}"
                }
            if (lines.isNotEmpty()) writeCached(cacheKey, lines, listType(String::class.java))
            lines
        } catch (e: Exception) {
            Log.w(tag, "h2h failed: ${e.message}")
            emptyList()
        }
    }

    // -------------------------------------------------------------- injuries

    private suspend fun injuriesFor(fixtureId: Int, apiKey: String): List<String> {
        return try {
            spent++
            val res = service.getInjuries(apiKey, fixtureId)
            if (res.code() == 403 || res.code() == 404) {
                // Not on this plan — stop asking for the rest of the batch.
                injuriesAvailable = false
                return emptyList()
            }
            if (!res.isSuccessful) return emptyList()
            res.body()?.response.orEmpty().mapNotNull { entry ->
                val name = entry.player?.name ?: return@mapNotNull null
                val team = entry.team?.name.orEmpty()
                val type = entry.player.type.orEmpty()
                "$name ($team) - $type${entry.player.reason?.let { ": $it" }.orEmpty()}"
            }.take(10)
        } catch (e: Exception) {
            Log.w(tag, "injuries failed: ${e.message}")
            emptyList()
        }
    }

    // -------------------------------------------------------------- odds

    private suspend fun oddsFor(fixtureId: Int, apiKey: String): MarketOdds? {
        val cacheKey = "odds_$fixtureId"
        readCached<MarketOdds>(cacheKey, ODDS_TTL_MS, MarketOdds::class.java)?.let { return it }

        return try {
            spent++
            val res = service.getOdds(apiKey, fixtureId)
            if (res.code() == 403 || res.code() == 404) {
                oddsAvailable = false
                return null
            }
            if (!res.isSuccessful) return null

            // Median across bookmakers is more robust than trusting whichever
            // happens to be listed first.
            val bets = res.body()?.response.orEmpty()
                .flatMap { it.bookmakers }
                .flatMap { it.bets }

            fun median(betName: String, valueLabel: String): Double? {
                val values = bets
                    .filter { it.name?.equals(betName, ignoreCase = true) == true }
                    .flatMap { it.values }
                    .filter { it.value?.equals(valueLabel, ignoreCase = true) == true }
                    .mapNotNull { it.odd?.toDoubleOrNull() }
                    .sorted()
                if (values.isEmpty()) return null
                return values[values.size / 2]
            }

            val odds = MarketOdds(
                homeWin = median("Match Winner", "Home"),
                draw = median("Match Winner", "Draw"),
                awayWin = median("Match Winner", "Away"),
                over25 = median("Goals Over/Under", "Over 2.5"),
                under25 = median("Goals Over/Under", "Under 2.5"),
                bttsYes = median("Both Teams Score", "Yes"),
                bttsNo = median("Both Teams Score", "No"),
                bookmakerCount = res.body()?.response.orEmpty().sumOf { it.bookmakers.size }
            )
            if (odds.hasAny) {
                writeCached(cacheKey, odds, MarketOdds::class.java)
                odds
            } else null
        } catch (e: Exception) {
            Log.w(tag, "odds failed: ${e.message}")
            null
        }
    }

    // -------------------------------------------------------------- cache I/O

    private fun listType(of: Class<*>) = Types.newParameterizedType(List::class.java, of)

    private fun <T> readCached(key: String, ttlMs: Long, type: java.lang.reflect.Type): T? {
        val stamp = prefs.getLong("${key}_at", 0L)
        if (stamp == 0L || System.currentTimeMillis() - stamp > ttlMs) return null
        val json = prefs.getString(key, null) ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            moshi.adapter<T>(type).fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun <T> writeCached(key: String, value: T, type: java.lang.reflect.Type) {
        try {
            val json = moshi.adapter<T>(type).toJson(value)
            prefs.edit().putString(key, json).putLong("${key}_at", System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            Log.w(tag, "cache write failed for $key: ${e.message}")
        }
    }

    /** Drops cached context. Standings/odds only; H2H is left since it rarely changes. */
    fun clearVolatileCache() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("standings_") || it.startsWith("odds_") }
            .forEach { editor.remove(it) }
        editor.apply()
        standingsMemo.clear()
    }
}
