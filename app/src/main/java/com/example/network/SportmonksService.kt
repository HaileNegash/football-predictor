package com.example.network

import com.example.models.Country
import com.example.models.League
import com.example.models.Match
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Locale

@JsonClass(generateAdapter = true)
data class SportmonksFixturesResponse(
    @Json(name = "data") val data: List<SportmonksFixture> = emptyList(),
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class SportmonksFixture(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String? = null,
    @Json(name = "starting_at") val startingAt: String? = null,
    @Json(name = "participants") val participants: List<SportmonksParticipant> = emptyList(),
    @Json(name = "scores") val scores: List<SportmonksScore> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SportmonksParticipant(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "short_code") val shortCode: String? = null,
    @Json(name = "image_path") val imagePath: String? = null,
    @Json(name = "meta") val meta: SportmonksMeta? = null
)

@JsonClass(generateAdapter = true)
data class SportmonksMeta(
    @Json(name = "location") val location: String? = null // "home" or "away"
)

@JsonClass(generateAdapter = true)
data class SportmonksScore(
    @Json(name = "description") val description: String? = null,
    @Json(name = "score") val score: SportmonksScoreValue? = null
)

@JsonClass(generateAdapter = true)
data class SportmonksScoreValue(
    @Json(name = "goals") val goals: Int? = null,
    @Json(name = "participant") val participant: String? = null
)

interface SportmonksApi {
    @GET("fixtures/date/{date}")
    suspend fun getFixturesByDate(
        @Path("date") date: String,
        @Query("api_token") apiToken: String,
        @Query("include") include: String = "participants;scores"
    ): SportmonksFixturesResponse
}

object SportmonksMapper {
    fun mapToCountries(response: SportmonksFixturesResponse): List<Country> {
        val matches = response.data.mapNotNull { item ->
            val home = item.participants.find { it.meta?.location == "home" } ?: item.participants.firstOrNull()
            val away = item.participants.find { it.meta?.location == "away" } ?: item.participants.getOrNull(1)

            if (home == null || away == null) return@mapNotNull null

            val timeStr = try {
                val input = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val output = SimpleDateFormat("HH:mm", Locale.getDefault())
                val d = input.parse(item.startingAt ?: "")
                if (d != null) output.format(d) else "--:--"
            } catch (e: Exception) {
                "--:--"
            }

            val matchDateStr = try {
                val input = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val output = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val d = input.parse(item.startingAt ?: "")
                if (d != null) output.format(d) else ""
            } catch (e: Exception) {
                ""
            }

            Match(
                id = item.id,
                homeTeam = home.name,
                awayTeam = away.name,
                homeLogo = home.imagePath,
                awayLogo = away.imagePath,
                startTime = timeStr,
                status = "NS",
                matchDate = matchDateStr
            )
        }

        if (matches.isEmpty()) return emptyList()

        val league = League(
            id = 1001,
            name = "Sportmonks Fixtures",
            logoUrl = null,
            matches = matches
        )

        return listOf(
            Country(
                name = "Sportmonks Pro Feed",
                flagUrl = null,
                leagues = listOf(league)
            )
        )
    }
}
