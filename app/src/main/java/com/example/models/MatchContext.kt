package com.example.models

import com.squareup.moshi.JsonClass

/**
 * The real, fetched evidence for one fixture. This is what gets rendered into the
 * model prompt in place of the previous three bare strings.
 */
data class MatchContext(
    val fixtureId: Int,
    val homeTeam: String,
    val awayTeam: String,
    val leagueName: String,
    val country: String,
    val round: String? = null,
    val homeForm: TeamForm? = null,
    val awayForm: TeamForm? = null,
    val headToHead: List<String> = emptyList(),
    val injuries: List<String> = emptyList(),
    val odds: MarketOdds? = null,
    /** Human-readable list of what was actually fetched, e.g. ["league table", "bookmaker odds"]. */
    val sources: List<String> = emptyList(),
    /** Optional free-text scraped intel (Firecrawl), only when genuinely retrieved. */
    val webIntel: String? = null
) {
    /** True when at least one real data source landed. Drives honest labelling. */
    val hasRealData: Boolean
        get() = homeForm != null || awayForm != null || headToHead.isNotEmpty() ||
                injuries.isNotEmpty() || odds != null || !webIntel.isNullOrBlank()

    /**
     * Rough evidence tier, used to cap how confident the model is allowed to be.
     * Nothing here is a substitute for data — it exists so a prediction made with
     * no inputs cannot claim 90% confidence.
     */
    val evidenceLevel: String
        get() = when {
            odds != null && (homeForm != null || awayForm != null) -> "STRONG"
            homeForm != null || awayForm != null || headToHead.size >= 3 -> "MODERATE"
            hasRealData -> "WEAK"
            else -> "NONE"
        }

    /** Upper bound on claimed confidence given the evidence actually available. */
    val confidenceCeiling: Int
        get() = when (evidenceLevel) {
            "STRONG" -> 88
            "MODERATE" -> 78
            "WEAK" -> 68
            else -> 58
        }
}

@JsonClass(generateAdapter = true)
data class TeamForm(
    val rank: Int? = null,
    val points: Int? = null,
    val played: Int = 0,
    val wins: Int? = null,
    val draws: Int? = null,
    val losses: Int? = null,
    val goalsFor: Int? = null,
    val goalsAgainst: Int? = null,
    /** Most-recent-last, e.g. "WWDLW". */
    val recentForm: String? = null,
    // Venue split: home rows for the home team, away rows for the away team.
    val venuePlayed: Int? = null,
    val venueWins: Int? = null,
    val venueGoalsFor: Int? = null,
    val venueGoalsAgainst: Int? = null
) {
    val goalsForPerGame: Double? get() = perGame(goalsFor)
    val goalsAgainstPerGame: Double? get() = perGame(goalsAgainst)

    private fun perGame(total: Int?): Double? =
        if (total != null && played > 0) total.toDouble() / played else null

    val venueGoalsForPerGame: Double?
        get() = if (venueGoalsFor != null && (venuePlayed ?: 0) > 0)
            venueGoalsFor.toDouble() / venuePlayed!! else null

    val venueGoalsAgainstPerGame: Double?
        get() = if (venueGoalsAgainst != null && (venuePlayed ?: 0) > 0)
            venueGoalsAgainst.toDouble() / venuePlayed!! else null

    /** Points-per-game over the whole season. */
    val pointsPerGame: Double?
        get() = if (points != null && played > 0) points.toDouble() / played else null
}

/**
 * Median bookmaker decimal odds. Treated as the market's probability estimate,
 * which is the calibration anchor the previous implementation lacked entirely.
 */
@JsonClass(generateAdapter = true)
data class MarketOdds(
    val homeWin: Double? = null,
    val draw: Double? = null,
    val awayWin: Double? = null,
    val over25: Double? = null,
    val under25: Double? = null,
    val bttsYes: Double? = null,
    val bttsNo: Double? = null,
    val bookmakerCount: Int = 0
) {
    val hasAny: Boolean
        get() = listOfNotNull(homeWin, draw, awayWin, over25, under25, bttsYes, bttsNo).isNotEmpty()

    /**
     * De-vigged 1X2 probabilities. Raw 1/odds sums above 1.0 because it includes
     * the bookmaker margin; normalising removes it so the numbers are comparable
     * to a model probability.
     */
    val impliedHome: Double? get() = normalised()?.first
    val impliedDraw: Double? get() = normalised()?.second
    val impliedAway: Double? get() = normalised()?.third

    private fun normalised(): Triple<Double, Double, Double>? {
        val h = homeWin ?: return null
        val d = draw ?: return null
        val a = awayWin ?: return null
        if (h <= 1.0 || d <= 1.0 || a <= 1.0) return null
        val rawH = 1.0 / h
        val rawD = 1.0 / d
        val rawA = 1.0 / a
        val overround = rawH + rawD + rawA
        if (overround <= 0.0) return null
        return Triple(rawH / overround, rawD / overround, rawA / overround)
    }

    /** Market-implied probability for a named pick, 0..1, or null if unpriced. */
    fun impliedFor(pick: String): Double? {
        val p = pick.lowercase()
        return when {
            p.contains("over 2.5") -> devig(over25, under25)
            p.contains("under 2.5") -> devig(under25, over25)
            p.contains("btts") && (p.contains("no") || p.contains("- no")) -> devig(bttsNo, bttsYes)
            p.contains("btts") || p.contains("both teams") -> devig(bttsYes, bttsNo)
            p.contains("home") && p.contains("draw") -> sumOf(impliedHome, impliedDraw)
            p.contains("draw") && p.contains("away") -> sumOf(impliedDraw, impliedAway)
            p.contains("home") -> impliedHome
            p.contains("away") -> impliedAway
            p.contains("draw") -> impliedDraw
            else -> null
        }
    }

    /** Decimal odds for a named pick, for expected-value maths. */
    fun oddsFor(pick: String): Double? {
        val p = pick.lowercase()
        return when {
            p.contains("over 2.5") -> over25
            p.contains("under 2.5") -> under25
            p.contains("btts") && (p.contains("no") || p.contains("- no")) -> bttsNo
            p.contains("btts") || p.contains("both teams") -> bttsYes
            p.contains("home") && !p.contains("draw") -> homeWin
            p.contains("away") && !p.contains("draw") -> awayWin
            p.contains("draw") && !p.contains("home") && !p.contains("away") -> draw
            else -> null
        }
    }

    private fun devig(side: Double?, other: Double?): Double? {
        if (side == null || side <= 1.0) return null
        val rawSide = 1.0 / side
        val rawOther = other?.takeIf { it > 1.0 }?.let { 1.0 / it }
        // Without the opposite side we can't remove the margin; return raw.
        return if (rawOther == null) rawSide else rawSide / (rawSide + rawOther)
    }

    private fun sumOf(a: Double?, b: Double?): Double? =
        if (a != null && b != null) (a + b).coerceAtMost(1.0) else null
}
