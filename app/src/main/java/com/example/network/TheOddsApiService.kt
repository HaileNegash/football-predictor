package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class OddsApiEvent(
    @Json(name = "id") val id: String,
    @Json(name = "sport_key") val sportKey: String,
    @Json(name = "sport_title") val sportTitle: String,
    @Json(name = "commence_time") val commenceTime: String,
    @Json(name = "home_team") val homeTeam: String,
    @Json(name = "away_team") val awayTeam: String,
    @Json(name = "bookmakers") val bookmakers: List<OddsApiBookmaker> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OddsApiBookmaker(
    @Json(name = "key") val key: String,
    @Json(name = "title") val title: String,
    @Json(name = "markets") val markets: List<OddsApiMarket> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OddsApiMarket(
    @Json(name = "key") val key: String, // "h2h", "totals", "spreads"
    @Json(name = "outcomes") val outcomes: List<OddsApiOutcome> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OddsApiOutcome(
    @Json(name = "name") val name: String,
    @Json(name = "price") val price: Double,
    @Json(name = "point") val point: Double? = null
)

interface TheOddsApi {
    @GET("sports/soccer/odds")
    suspend fun getSoccerOdds(
        @Query("apiKey") apiKey: String,
        @Query("regions") regions: String = "eu,uk,us",
        @Query("markets") markets: String = "h2h,totals",
        @Query("oddsFormat") oddsFormat: String = "decimal"
    ): List<OddsApiEvent>
}

object TheOddsApiMatcher {
    fun findOddsForMatch(
        homeTeam: String,
        awayTeam: String,
        events: List<OddsApiEvent>
    ): Map<String, Double> {
        val event = events.find { event ->
            (event.homeTeam.contains(homeTeam, ignoreCase = true) || homeTeam.contains(event.homeTeam, ignoreCase = true)) &&
            (event.awayTeam.contains(awayTeam, ignoreCase = true) || awayTeam.contains(event.awayTeam, ignoreCase = true))
        } ?: return emptyMap()

        val oddsMap = mutableMapOf<String, Double>()
        for (bm in event.bookmakers) {
            for (market in bm.markets) {
                if (market.key == "h2h") {
                    for (outcome in market.outcomes) {
                        when {
                            outcome.name.equals(event.homeTeam, ignoreCase = true) -> oddsMap.putIfAbsent("Home", outcome.price)
                            outcome.name.equals(event.awayTeam, ignoreCase = true) -> oddsMap.putIfAbsent("Away", outcome.price)
                            outcome.name.equals("Draw", ignoreCase = true) -> oddsMap.putIfAbsent("Draw", outcome.price)
                        }
                    }
                } else if (market.key == "totals") {
                    for (outcome in market.outcomes) {
                        if (outcome.point == 2.5) {
                            if (outcome.name.equals("Over", ignoreCase = true)) oddsMap.putIfAbsent("Over 2.5", outcome.price)
                            if (outcome.name.equals("Under", ignoreCase = true)) oddsMap.putIfAbsent("Under 2.5", outcome.price)
                        }
                    }
                }
            }
        }
        return oddsMap
    }
}
