package com.example.network

import com.example.models.Country
import com.example.models.League
import com.example.models.Match
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class SportsDbEventsResponse(
    @Json(name = "events") val events: List<SportsDbEvent>? = null
)

@JsonClass(generateAdapter = true)
data class SportsDbEvent(
    @Json(name = "idEvent") val idEvent: String,
    @Json(name = "strEvent") val strEvent: String? = null,
    @Json(name = "strLeague") val strLeague: String? = null,
    @Json(name = "strHomeTeam") val strHomeTeam: String,
    @Json(name = "strAwayTeam") val strAwayTeam: String,
    @Json(name = "strThumb") val strThumb: String? = null,
    @Json(name = "strTime") val strTime: String? = null,
    @Json(name = "dateEvent") val dateEvent: String? = null,
    @Json(name = "strStatus") val strStatus: String? = null,
    @Json(name = "intHomeScore") val intHomeScore: String? = null,
    @Json(name = "intAwayScore") val intAwayScore: String? = null,
    @Json(name = "strCountry") val strCountry: String? = null
)

interface TheSportsDbApi {
    @GET("{apiKey}/eventsday.php")
    suspend fun getEventsByDay(
        @Path("apiKey") apiKey: String,
        @Query("d") date: String,
        @Query("s") sport: String = "Soccer"
    ): SportsDbEventsResponse
}

object TheSportsDbMapper {
    fun mapToCountries(response: SportsDbEventsResponse): List<Country> {
        val events = response.events ?: return emptyList()

        return events.groupBy { it.strCountry ?: "World" }
            .map { (countryName, eventsForCountry) ->
                val leagues = eventsForCountry.groupBy { it.strLeague ?: "General League" }
                    .map { (leagueName, eventsForLeague) ->
                        val matches = eventsForLeague.map { ev ->
                            val timeStr = ev.strTime?.take(5) ?: "--:--"
                            Match(
                                id = ev.idEvent.hashCode(),
                                homeTeam = ev.strHomeTeam,
                                awayTeam = ev.strAwayTeam,
                                homeLogo = null,
                                awayLogo = null,
                                startTime = timeStr,
                                status = ev.strStatus ?: "NS",
                                homeScore = ev.intHomeScore?.toIntOrNull(),
                                awayScore = ev.intAwayScore?.toIntOrNull(),
                                matchDate = ev.dateEvent ?: ""
                            )
                        }
                        League(
                            id = leagueName.hashCode(),
                            name = leagueName,
                            logoUrl = null,
                            matches = matches
                        )
                    }

                Country(
                    name = countryName,
                    flagUrl = null,
                    leagues = leagues
                )
            }
    }
}
