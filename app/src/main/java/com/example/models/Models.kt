package com.example.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Classes for API-Football response
 */
@JsonClass(generateAdapter = true)
data class ApiFootballResponse(
    @Json(name = "response") val response: List<ApiFixture>,
    @Json(name = "errors") val errors: Any? = null
)

@JsonClass(generateAdapter = true)
data class ApiFixture(
    @Json(name = "fixture") val fixture: FixtureInfo,
    @Json(name = "league") val league: LeagueInfo,
    @Json(name = "teams") val teams: TeamsInfo,
    @Json(name = "goals") val goals: GoalsInfo
)

@JsonClass(generateAdapter = true)
data class FixtureInfo(
    @Json(name = "id") val id: Int,
    @Json(name = "date") val date: String,
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
    @Json(name = "logo") val logo: String?
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
    val matchDate: String = "",
    // When the user taps the predict button, we will store the result here
    var prediction: PredictionResult? = null
)

data class PredictionResult(
    val recommendedBet: String,
    val confidence: Int,
    val rationale: String,
    val odds: String? = null,
    val betType: String? = null
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
