package com.example.network

import android.util.Log
import com.example.models.ApiFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches final scores for fixtures that have already been predicted, so slips can
 * be settled and a real hit rate computed.
 *
 * Request cost is the reason this is a separate class: API-Football accepts up to
 * 20 dash-separated ids per `/fixtures?ids=` call, so settling a 40-leg backlog
 * costs 2 requests rather than 40. On a ~100/day free plan that is the difference
 * between settlement being viable and eating the whole budget.
 */
class ResultsRepository(
    private val service: ApiFootballService = NetworkClient.apiFootballService
) {
    private val tag = "ResultsRepository"

    companion object {
        /** API-Football's documented cap for the `ids` parameter. */
        const val MAX_IDS_PER_REQUEST = 20
    }

    /**
     * Returns final-score data keyed by fixture id for whichever of [fixtureIds]
     * the API returned. Ids that are missing from the response simply won't appear
     * in the map — the caller leaves those legs pending rather than guessing.
     *
     * [maxRequests] hard-caps how much of the daily quota this may consume.
     */
    suspend fun fetchResults(
        fixtureIds: Collection<Int>,
        apiKey: String,
        maxRequests: Int = 2
    ): Map<Int, FixtureResult> = withContext(Dispatchers.IO) {
        if (fixtureIds.isEmpty() || apiKey.isBlank() || maxRequests <= 0) {
            return@withContext emptyMap()
        }

        val out = mutableMapOf<Int, FixtureResult>()
        val chunks = fixtureIds.distinct().chunked(MAX_IDS_PER_REQUEST).take(maxRequests)

        for (chunk in chunks) {
            try {
                val res = service.getFixturesByIds(apiKey, chunk.joinToString("-"))
                if (!res.isSuccessful) {
                    Log.w(tag, "results HTTP ${res.code()} for ${chunk.size} ids")
                    continue
                }
                res.body()?.response.orEmpty().forEach { fixture ->
                    out[fixture.fixture.id] = fixture.toResult()
                }
            } catch (e: Exception) {
                Log.w(tag, "results fetch failed: ${e.message}")
            }
        }
        out
    }

    private fun ApiFixture.toResult() = FixtureResult(
        fixtureId = fixture.id,
        statusShort = fixture.status.short,
        homeScore = goals.home,
        awayScore = goals.away,
        homeTeam = teams.home.name,
        awayTeam = teams.away.name,
        // Half-time score, when the API supplied it. Without this, every HT/FT and
        // first-half market is ungradable.
        halftimeHome = score?.halftime?.home,
        halftimeAway = score?.halftime?.away,
        kickoffEpoch = fixture.timestamp
    )
}

data class FixtureResult(
    val fixtureId: Int,
    val statusShort: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val homeTeam: String,
    val awayTeam: String,
    val halftimeHome: Int? = null,
    val halftimeAway: Int? = null,
    val kickoffEpoch: Long? = null
) {
    val isFinished: Boolean get() = BetSettlement.isFinished(statusShort)
    val isVoid: Boolean get() = BetSettlement.isVoid(statusShort)
}
