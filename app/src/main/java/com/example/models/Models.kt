package com.example.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Classes for API-Football response
 */
@JsonClass(generateAdapter = true)
data class ApiFootballResponse(
    // Defaulted: API-Football returns `"response": []` on error responses, and a
    // missing key used to throw JsonDataException and surface as a generic
    // "Failed to fetch fixtures".
    @Json(name = "response") val response: List<ApiFixture> = emptyList(),
    @Json(name = "errors") val errors: Any? = null
)

@JsonClass(generateAdapter = true)
data class ApiFixture(
    @Json(name = "fixture") val fixture: FixtureInfo,
    @Json(name = "league") val league: LeagueInfo,
    @Json(name = "teams") val teams: TeamsInfo,
    @Json(name = "goals") val goals: GoalsInfo,
    // Period breakdown. `goals` is the 90-minute score; this is what makes
    // half-time markets gradable at all, and it distinguishes an AET/PEN result
    // from the score the bet was actually settled on.
    @Json(name = "score") val score: ScoreInfo? = null
)

/**
 * Per-period scores. All nullable — API-Football omits periods that haven't been
 * played, so a first-half fixture has `fulltime: {null, null}`.
 */
@JsonClass(generateAdapter = true)
data class ScoreInfo(
    @Json(name = "halftime") val halftime: GoalsInfo? = null,
    @Json(name = "fulltime") val fulltime: GoalsInfo? = null,
    @Json(name = "extratime") val extratime: GoalsInfo? = null,
    @Json(name = "penalty") val penalty: GoalsInfo? = null
)

@JsonClass(generateAdapter = true)
data class FixtureInfo(
    @Json(name = "id") val id: Int,
    @Json(name = "date") val date: String,
    @Json(name = "timestamp") val timestamp: Long? = null,
    @Json(name = "status") val status: StatusInfo
)

@JsonClass(generateAdapter = true)
data class StatusInfo(
    @Json(name = "short") val short: String,
    @Json(name = "elapsed") val elapsed: Int?
)

@JsonClass(generateAdapter = true)
data class LeagueInfo(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "country") val country: String,
    @Json(name = "flag") val flag: String?,
    @Json(name = "logo") val logo: String?,
    // Needed to query /standings, which is keyed on (league, season).
    @Json(name = "season") val season: Int? = null,
    @Json(name = "round") val round: String? = null
)

@JsonClass(generateAdapter = true)
data class TeamsInfo(
    @Json(name = "home") val home: TeamInfo,
    @Json(name = "away") val away: TeamInfo
)

@JsonClass(generateAdapter = true)
data class TeamInfo(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "logo") val logo: String?
)

@JsonClass(generateAdapter = true)
data class GoalsInfo(
    @Json(name = "home") val home: Int?,
    @Json(name = "away") val away: Int?
)

/**
 * UI Models for Football Predictor mapping a nested fixture response.
 * Hierarchy: Country -> League -> Match
 */
data class Country(
    val name: String,
    val flagUrl: String?,
    val leagues: List<League>
)

data class League(
    val id: Int,
    val name: String,
    val logoUrl: String?,
    val matches: List<Match>
)

data class Match(
    val id: Int,
    val homeTeam: String,
    val awayTeam: String,
    val homeLogo: String?,
    val awayLogo: String?,
    val startTime: String,
    val status: String,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    // Team/league identifiers, needed to query standings, H2H and odds for this
    // fixture. Nullable so cached payloads written before these were added still parse.
    val homeTeamId: Int? = null,
    val awayTeamId: Int? = null,
    val leagueId: Int? = null,
    val leagueName: String? = null,
    val season: Int? = null,
    /** e.g. "Regular Season - 12"; passed to the model as competition context. */
    val round: String? = null,
    val countryName: String? = null,
    /** Kickoff epoch seconds; sort on this rather than the "HH:mm" display string. */
    val kickoffEpoch: Long? = null,
    // `val`, not `var`: this sits inside a StateFlow-held list, so in-place
    // mutation would bypass Compose recomposition. Callers use copy().
    val prediction: PredictionResult? = null
)

data class PredictionResult(
    val recommendedBet: String,
    val confidence: Int,
    val rationale: String,
    val odds: String? = null,
    val betType: String? = null,
    /**
     * True when this came from a real model call grounded in fetched data.
     * False for heuristic/offline output, so the UI can label it honestly
     * instead of presenting a guess as analysis.
     */
    val isModelBacked: Boolean = false,
    /** Bookmaker decimal odds for this pick, when the market was available. */
    val marketOdds: String? = null,
    /** Model probability minus market-implied probability, in percentage points. */
    val edgePercent: Double? = null,
    /** Which real inputs reached the model, for display and debugging. */
    val dataSources: List<String> = emptyList()
)

data class SearchItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val logoUrl: String?,
    val type: String
)

data class Currency(
    val code: String,
    val symbol: String,
    val name: String,
    val flagEmoji: String
)

val PopularCurrencies = listOf(
    Currency("ETB", "Br", "Ethiopian Birr", "🇪🇹"),
    Currency("USD", "$", "US Dollar", "🇺🇸"),
    Currency("EUR", "€", "Euro", "🇪🇺"),
    Currency("GBP", "£", "British Pound", "🇬🇧"),
    Currency("KES", "KSh", "Kenyan Shilling", "🇰🇪"),
    Currency("NGN", "₦", "Nigerian Naira", "🇳🇬"),
    Currency("ZAR", "R", "South African Rand", "🇿🇦"),
    Currency("GHS", "GH₵", "Ghanaian Cedi", "🇬🇭"),
    Currency("CAD", "CA$", "Canadian Dollar", "🇨🇦"),
    Currency("AUD", "AU$", "Australian Dollar", "🇦🇺"),
    Currency("JPY", "¥", "Japanese Yen", "🇯🇵"),
    Currency("INR", "₹", "Indian Rupee", "🇮🇳"),
    Currency("SAR", "SR", "Saudi Riyal", "🇸🇦"),
    Currency("AED", "AED", "UAE Dirham", "🇦🇪"),
    Currency("BRL", "R$", "Brazilian Real", "🇧🇷"),
    Currency("CHF", "CHF", "Swiss Franc", "🇨🇭"),
    Currency("CNY", "¥", "Chinese Yuan", "🇨🇳"),
    Currency("EGP", "E£", "Egyptian Pound", "🇪🇬"),
    Currency("UGX", "USh", "Ugandan Shilling", "🇺🇬"),
    Currency("TZS", "TSh", "Tanzanian Shilling", "🇹🇿")
)
