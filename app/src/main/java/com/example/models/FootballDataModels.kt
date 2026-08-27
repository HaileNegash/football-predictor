package com.example.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs for the API-Football endpoints that carry actual predictive signal:
 * standings (table position, goals for/against, home/away splits, form),
 * head-to-head history, injuries, and bookmaker odds.
 *
 * Every field is nullable or defaulted. API-Football returns `"response": []`
 * on error and omits fields on partial data, so a strict schema turns a
 * recoverable gap into a total fetch failure.
 */

// ---------------------------------------------------------------- standings

@JsonClass(generateAdapter = true)
data class StandingsResponse(
    @Json(name = "response") val response: List<StandingsLeagueWrapper> = emptyList(),
    @Json(name = "errors") val errors: Any? = null
)

@JsonClass(generateAdapter = true)
data class StandingsLeagueWrapper(
    @Json(name = "league") val league: StandingsLeague? = null
)

@JsonClass(generateAdapter = true)
data class StandingsLeague(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "season") val season: Int? = null,
    /** Outer list is one entry per group (e.g. Champions League groups). */
    @Json(name = "standings") val standings: List<List<StandingRow>> = emptyList()
)

@JsonClass(generateAdapter = true)
data class StandingRow(
    @Json(name = "rank") val rank: Int? = null,
    @Json(name = "team") val team: TeamInfo? = null,
    @Json(name = "points") val points: Int? = null,
    @Json(name = "goalsDiff") val goalsDiff: Int? = null,
    /** Most-recent-last form string, e.g. "WWDLW". */
    @Json(name = "form") val form: String? = null,
    @Json(name = "all") val all: StandingSplit? = null,
    @Json(name = "home") val home: StandingSplit? = null,
    @Json(name = "away") val away: StandingSplit? = null
)

@JsonClass(generateAdapter = true)
data class StandingSplit(
    @Json(name = "played") val played: Int? = null,
    @Json(name = "win") val win: Int? = null,
    @Json(name = "draw") val draw: Int? = null,
    @Json(name = "lose") val lose: Int? = null,
    @Json(name = "goals") val goals: StandingGoals? = null
)

@JsonClass(generateAdapter = true)
data class StandingGoals(
    // `for` is a Kotlin keyword; remap it.
    @Json(name = "for") val scored: Int? = null,
    @Json(name = "against") val conceded: Int? = null
)

// ---------------------------------------------------------------- injuries

@JsonClass(generateAdapter = true)
data class InjuriesResponse(
    @Json(name = "response") val response: List<InjuryEntry> = emptyList(),
    @Json(name = "errors") val errors: Any? = null
)

@JsonClass(generateAdapter = true)
data class InjuryEntry(
    @Json(name = "player") val player: InjuryPlayer? = null,
    @Json(name = "team") val team: TeamInfo? = null
)

@JsonClass(generateAdapter = true)
data class InjuryPlayer(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String? = null,
    /** e.g. "Missing Fixture" vs "Questionable". */
    @Json(name = "type") val type: String? = null,
    @Json(name = "reason") val reason: String? = null
)

// ---------------------------------------------------------------- odds

@JsonClass(generateAdapter = true)
data class OddsResponse(
    @Json(name = "response") val response: List<OddsFixtureEntry> = emptyList(),
    @Json(name = "errors") val errors: Any? = null
)

@JsonClass(generateAdapter = true)
data class OddsFixtureEntry(
    @Json(name = "fixture") val fixture: OddsFixtureRef? = null,
    @Json(name = "bookmakers") val bookmakers: List<OddsBookmaker> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OddsFixtureRef(
    @Json(name = "id") val id: Int? = null
)

@JsonClass(generateAdapter = true)
data class OddsBookmaker(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "bets") val bets: List<OddsBet> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OddsBet(
    @Json(name = "id") val id: Int? = null,
    /** e.g. "Match Winner", "Goals Over/Under", "Both Teams Score". */
    @Json(name = "name") val name: String? = null,
    @Json(name = "values") val values: List<OddsValue> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OddsValue(
    @Json(name = "value") val value: String? = null,
    @Json(name = "odd") val odd: String? = null
)
