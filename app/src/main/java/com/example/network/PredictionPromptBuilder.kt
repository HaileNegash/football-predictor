package com.example.network

import com.example.models.MatchContext
import java.util.Locale

/**
 * Builds the model prompt. Kept separate from the HTTP call so it can be unit
 * tested without a network, and so the analytical instructions live in one
 * readable place rather than being spliced into a request builder.
 *
 * Design decisions that matter for accuracy:
 *  - Real fetched numbers are rendered explicitly; the model is told to reason
 *    from them and is forbidden from inventing results it wasn't given.
 *  - Where bookmaker odds exist they are shown as de-vigged probabilities, and
 *    the model is asked to disagree with the market only when it can say why.
 *    A prediction that merely restates the favourite has no value; one that
 *    contradicts the market without justification is noise.
 *  - Confidence is capped by how much evidence actually arrived, which is what
 *    stops the "everything is 78%" behaviour.
 *  - Output is a fixed JSON schema with an explicit probability, so expected
 *    value can be computed downstream instead of trusting a vibe score.
 */
object PredictionPromptBuilder {

    const val SYSTEM_PROMPT = """
You are a quantitative football analyst. You price match outcomes from data, the way a trading desk does.

METHOD — follow in order:
1. Establish each side's scoring and conceding rate per game, preferring the venue-specific split (home team's home record, away team's away record) over the overall record.
2. Adjust for schedule strength using league position and points-per-game, and for the specific matchup shape (a high-scoring attack against a leaky defence raises goal expectancy; two low-block sides lower it).
3. Weight recent form, but do not overweight it. A five-game streak is roughly 5 matches of evidence against a season's 20-30. Regress toward the season baseline.
4. Apply the injury report only where a named absence plausibly changes the outcome (first-choice keeper, main striker, multiple defenders). Do not treat a squad player's absence as material.
5. If bookmaker probabilities are supplied, treat them as a strong prior — they aggregate more information than you have. Deviate only when you can name the specific factor the market appears to be underweighting. If you cannot name it, agree with the market.
6. Choose the pick with the best combination of probability and price, restricted to the allowed markets. A high-probability pick at a poor price may be worse than a moderate one at a good price.

CALIBRATION — this is the part that matters:
- "probability" is your honest estimate that the pick lands, 0-100. It is not a confidence-in-yourself score and not a sales number.
- Well-calibrated means: across every prediction you label 70%, about 70% should win. Most football markets are genuinely uncertain; probabilities above 85 are rare and should be reserved for heavy mismatches with confirming data.
- If the data given to you is thin, say so in the rationale and lower the probability. Do not fill gaps with invention.

HARD RULES:
- Use ONLY the data provided below. Never state a past result, injury, transfer, or statistic that does not appear in the supplied context.
- If a needed input is missing, reason from what is present and note the limitation in the rationale.
- Respond with a single JSON object and nothing else. No markdown fences, no prose before or after.
"""

    fun buildUserPrompt(
        ctx: MatchContext,
        allowedBetTypes: List<String>
    ): String = buildString {
        appendLine("FIXTURE")
        appendLine("  ${ctx.homeTeam} (home) vs ${ctx.awayTeam} (away)")
        appendLine("  Competition: ${ctx.leagueName}, ${ctx.country}${ctx.round?.let { " — $it" }.orEmpty()}")
        appendLine()

        val home = ctx.homeForm
        val away = ctx.awayForm
        if (home != null || away != null) {
            appendLine("SEASON DATA")
            home?.let { appendLine(renderForm(ctx.homeTeam, it, "home")) }
            away?.let { appendLine(renderForm(ctx.awayTeam, it, "away")) }
            appendLine()
        }

        if (ctx.headToHead.isNotEmpty()) {
            appendLine("HEAD-TO-HEAD (most recent first)")
            ctx.headToHead.take(6).forEach { appendLine("  $it") }
            appendLine()
        }

        if (ctx.injuries.isNotEmpty()) {
            appendLine("REPORTED ABSENCES")
            ctx.injuries.forEach { appendLine("  $it") }
            appendLine()
        }

        ctx.odds?.let { odds ->
            appendLine("BOOKMAKER MARKET (median of ${odds.bookmakerCount} books, margin removed)")
            val ih = odds.impliedHome
            val id = odds.impliedDraw
            val ia = odds.impliedAway
            if (ih != null && id != null && ia != null) {
                appendLine("  1X2 implied: ${ctx.homeTeam} ${pct(ih)}, draw ${pct(id)}, ${ctx.awayTeam} ${pct(ia)}")
            }
            odds.homeWin?.let { appendLine("  Decimal odds — home ${dec(it)}, draw ${dec(odds.draw)}, away ${dec(odds.awayWin)}") }
            odds.over25?.let { appendLine("  Over 2.5 ${dec(it)} / Under 2.5 ${dec(odds.under25)}") }
            odds.bttsYes?.let { appendLine("  BTTS yes ${dec(it)} / no ${dec(odds.bttsNo)}") }
            appendLine()
        }

        ctx.webIntel?.takeIf { it.isNotBlank() }?.let {
            appendLine("SCRAPED TEAM NEWS (unverified, weight accordingly)")
            appendLine("  ${it.take(600)}")
            appendLine()
        }

        appendLine("ALLOWED MARKETS — your pick must be one of these:")
        val markets = if (allowedBetTypes.isNotEmpty()) allowedBetTypes else DEFAULT_MARKETS
        markets.forEach { appendLine("  - $it") }
        appendLine()

        appendLine("EVIDENCE LEVEL: ${ctx.evidenceLevel}")
        if (ctx.sources.isNotEmpty()) {
            appendLine("Data supplied: ${ctx.sources.joinToString(", ")}")
        } else {
            appendLine("No external data was retrieved for this fixture. Reason from general knowledge of these teams only, keep the probability low, and say in the rationale that the estimate is unsupported.")
        }
        appendLine("Given that evidence level, your \"probability\" must not exceed ${ctx.confidenceCeiling}.")
        appendLine()

        appendLine(
            """
            Return exactly this JSON shape:
            {
              "recommendedBet": "<specific pick, e.g. 'Over 2.5 Goals' or 'Double Chance (1X)'>",
              "betType": "<one of: 1X2, Over/Under, BTTS, Double Chance, Draw No Bet, Handicap, Combo>",
              "probability": <integer 1-${ctx.confidenceCeiling}, your honest chance this lands>,
              "fairOdds": <decimal number, 100/probability, i.e. the price at which this bet is break-even>,
              "rationale": "<2-3 sentences citing the specific numbers above that drove this>",
              "keyFactor": "<the single most decisive input, max 12 words>",
              "marketDisagreement": "<if you deviated from the bookmaker probabilities, the specific factor they appear to underweight; otherwise 'none'>",
              "dataGaps": "<what you lacked that would have improved this, or 'none'>"
            }
            """.trimIndent()
        )
    }

    private val DEFAULT_MARKETS = listOf(
        "1X2 (Home Win / Draw / Away Win)",
        "Over/Under 2.5 Goals",
        "Both Teams to Score (BTTS)",
        "Double Chance (1X, 12, X2)",
        "Draw No Bet (DNB)"
    )

    private fun renderForm(team: String, f: com.example.models.TeamForm, venue: String): String = buildString {
        append("  $team")
        f.rank?.let { append(" — position $it") }
        f.pointsPerGame?.let { append(", ${fmt(it)} pts/game") }
        if (f.played > 0) {
            append(" (${f.wins ?: 0}W-${f.draws ?: 0}D-${f.losses ?: 0}L in ${f.played})")
        }
        f.goalsForPerGame?.let { gf ->
            append("; scored ${fmt(gf)}/game")
            f.goalsAgainstPerGame?.let { ga -> append(", conceded ${fmt(ga)}/game") }
        }
        val vGf = f.venueGoalsForPerGame
        val vGa = f.venueGoalsAgainstPerGame
        if (vGf != null || vGa != null) {
            append("; $venue only:")
            vGf?.let { append(" scored ${fmt(it)}/game") }
            vGa?.let { append(", conceded ${fmt(it)}/game") }
            f.venuePlayed?.let { append(" over $it matches") }
        }
        f.recentForm?.takeIf { it.isNotBlank() }?.let { append("; recent form $it (oldest to newest)") }
    }

    private fun fmt(d: Double) = String.format(Locale.US, "%.2f", d)
    private fun pct(d: Double) = String.format(Locale.US, "%.0f%%", d * 100)
    private fun dec(d: Double?) = d?.let { String.format(Locale.US, "%.2f", it) } ?: "n/a"
}
