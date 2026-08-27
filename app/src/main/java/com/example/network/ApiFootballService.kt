package com.example.network

import com.example.models.ApiFootballResponse
import com.example.models.InjuriesResponse
import com.example.models.OddsResponse
import com.example.models.StandingsResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface ApiFootballService {

    /**
     * Returns the raw [Response] so callers can read the quota headers
     * (`x-ratelimit-requests-remaining`, `x-ratelimit-requests-limit`) instead of
     * only discovering exhaustion from an error body after the fact.
     */
    @GET("fixtures")
    suspend fun getFixturesResponse(
        @Header("x-apisports-key") apiKey: String,
        @Query("date") date: String
    ): Response<ApiFootballResponse>

    @GET("fixtures")
    suspend fun getFixtures(
        @Header("x-apisports-key") apiKey: String,
        @Query("date") date: String
    ): ApiFootballResponse

    /**
     * League table: rank, points, goal difference, home/away splits and a form
     * string per team. One request covers every fixture in that league, which
     * makes it by far the cheapest source of real signal on a metered plan.
     */
    @GET("standings")
    suspend fun getStandings(
        @Header("x-apisports-key") apiKey: String,
        @Query("league") leagueId: Int,
        @Query("season") season: Int
    ): Response<StandingsResponse>

    /** Recent completed meetings between two teams, newest first. */
    @GET("fixtures/headtohead")
    suspend fun getHeadToHead(
        @Header("x-apisports-key") apiKey: String,
        @Query("h2h") h2h: String,
        @Query("last") last: Int = 8
    ): Response<ApiFootballResponse>

    /** Last N completed fixtures for one team, for form when standings are absent (cups). */
    @GET("fixtures")
    suspend fun getTeamRecentFixtures(
        @Header("x-apisports-key") apiKey: String,
        @Query("team") teamId: Int,
        @Query("last") last: Int = 6
    ): Response<ApiFootballResponse>

    /** Paid-plan on most tiers; callers must degrade gracefully on 403/empty. */
    @GET("injuries")
    suspend fun getInjuries(
        @Header("x-apisports-key") apiKey: String,
        @Query("fixture") fixtureId: Int
    ): Response<InjuriesResponse>

    /**
     * Bookmaker odds. These are the market's implied probabilities — the single
     * most useful calibration anchor available, and what makes real expected-value
     * ranking possible. Paid-plan on most tiers.
     */
    @GET("odds")
    suspend fun getOdds(
        @Header("x-apisports-key") apiKey: String,
        @Query("fixture") fixtureId: Int,
        @Query("bookmaker") bookmaker: Int? = null
    ): Response<OddsResponse>

    /**
     * Final scores for specific fixtures, used by settlement. `ids` takes up to 20
     * dash-separated fixture ids in one request, so a whole slip settles for a
     * single call rather than one per leg — the difference between settlement being
     * affordable on a free plan and not.
     */
    @GET("fixtures")
    suspend fun getFixturesByIds(
        @Header("x-apisports-key") apiKey: String,
        @Query("ids") ids: String
    ): Response<ApiFootballResponse>
}
