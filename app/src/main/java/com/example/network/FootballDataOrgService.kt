package com.example.network

import com.example.models.Country
import com.example.models.League
import com.example.models.Match
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Locale

@JsonClass(generateAdapter = true)
data class FootballDataMatchesResponse(
    @Json(name = "matches") val matches: List<FootballDataMatch> = emptyList(),
    @Json(name = "message") val message: String? = null,
    @Json(name = "errorCode") val errorCode: Int? = null
)

@JsonClass(generateAdapter = true)
data class FootballDataMatch(
    @Json(name = "id") val id: Int,
    @Json(name = "utcDate") val utcDate: String,
    @Json(name = "status") val status: String,
    @Json(name = "competition") val competition: FootballDataCompetition,
    @Json(name = "area") val area: FootballDataArea? = null,
    @Json(name = "homeTeam") val homeTeam: FootballDataTeam,
    @Json(name = "awayTeam") val awayTeam: FootballDataTeam,
    @Json(name = "score") val score: FootballDataScore? = null
)

@JsonClass(generateAdapter = true)
data class FootballDataCompetition(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "code") val code: String? = null,
    @Json(name = "emblem") val emblem: String? = null
)

@JsonClass(generateAdapter = true)
data class FootballDataArea(
    @Json(name = "name") val name: String,
    @Json(name = "code") val code: String? = null,
    @Json(name = "flag") val flag: String? = null
)

@JsonClass(generateAdapter = true)
data class FootballDataTeam(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "shortName") val shortName: String? = null,
    @Json(name = "tla") val tla: String? = null,
    @Json(name = "crest") val crest: String? = null
)

@JsonClass(generateAdapter = true)
data class FootballDataScore(
    @Json(name = "winner") val winner: String? = null,
    @Json(name = "duration") val duration: String? = null,
    @Json(name = "fullTime") val fullTime: FootballDataGoals? = null
)

@JsonClass(generateAdapter = true)
data class FootballDataGoals(
    @Json(name = "home") val home: Int? = null,
    @Json(name = "away") val away: Int? = null
)

interface FootballDataOrgApi {
    @GET("matches")
    suspend fun getMatches(
        @Header("X-Auth-Token") apiKey: String,
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String
    ): FootballDataMatchesResponse
}

object FootballDataOrgMapper {
    fun mapToCountries(response: FootballDataMatchesResponse): List<Country> {
        val bigCountries = listOf("England", "Spain", "Germany", "Italy", "France", "Europe", "World")
        val bigLeagues = listOf(
            "Premier League", "Primera Division", "La Liga", "Bundesliga", "Serie A", "Ligue 1",
            "UEFA Champions League", "UEFA Europa League", "Championship", "Eredivisie", "Primeira Liga"
        )

        return response.matches.groupBy { it.area?.name ?: "International" }
            .map { (countryName, matchesForCountry) ->
                val countryFlag = matchesForCountry.firstOrNull()?.area?.flag

                val leagues = matchesForCountry.groupBy { it.competition.id }
                    .map { (leagueId, matchesForLeague) ->
                        val competition = matchesForLeague.first().competition

                        val mappedMatches = matchesForLeague.map { match ->
                            val timeStr = try {
                                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                                val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                val date = inputFormat.parse(match.utcDate)
                                if (date != null) outputFormat.format(date) else "--:--"
                            } catch (e: Exception) {
                                "--:--"
                            }

                            val shortStatus = when (match.status.uppercase()) {
                                "FINISHED" -> "FT"
                                "IN_PLAY" -> "LIVE"
                                "PAUSED" -> "HT"
                                "POSTPONED" -> "PST"
                                "CANCELLED" -> "CANC"
                                "SUSPENDED" -> "SUSP"
                                else -> "NS"
                            }

                            val matchDateStr = try {
                                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                                val outputDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val date = inputFormat.parse(match.utcDate)
                                if (date != null) outputDate.format(date) else ""
                            } catch (e: Exception) {
                                ""
                            }

                            Match(
                                id = match.id,
                                homeTeam = match.homeTeam.shortName ?: match.homeTeam.name,
                                awayTeam = match.awayTeam.shortName ?: match.awayTeam.name,
                                homeLogo = match.homeTeam.crest,
                                awayLogo = match.awayTeam.crest,
                                startTime = timeStr,
                                status = shortStatus,
                                homeScore = match.score?.fullTime?.home,
                                awayScore = match.score?.fullTime?.away,
                                matchDate = matchDateStr
                            )
                        }.sortedBy { it.startTime }

                        League(
                            id = leagueId,
                            name = competition.name,
                            logoUrl = competition.emblem,
                            matches = mappedMatches
                        )
                    }.sortedWith(compareBy<League> { league ->
                        val idx = bigLeagues.indexOfFirst { league.name.contains(it, ignoreCase = true) }
                        if (idx != -1) idx else Int.MAX_VALUE
                    }.thenBy { it.name })

                Country(
                    name = countryName,
                    flagUrl = countryFlag,
                    leagues = leagues
                )
            }.sortedWith(compareBy<Country> { country ->
                val idx = bigCountries.indexOfFirst { country.name.contains(it, ignoreCase = true) }
                if (idx != -1) idx else Int.MAX_VALUE
            }.thenBy { it.name })
    }
}
