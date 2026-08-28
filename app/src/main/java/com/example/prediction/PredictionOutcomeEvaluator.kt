package com.example.prediction

import com.example.models.PredictedBetItem
import com.example.models.SavedPredictionSlip
import java.util.Locale

data class EvaluationResult(
    val status: String, // "WON", "LOST", "VOID", "PENDING"
    val explanation: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val isFinished: Boolean
)

object PredictionOutcomeEvaluator {

    /**
     * Evaluates a single predicted bet item against actual or simulated match scores.
     */
    fun evaluateItem(
        item: PredictedBetItem,
        homeScore: Int?,
        awayScore: Int?,
        status: String? = null
    ): EvaluationResult {
        if (homeScore == null || awayScore == null) {
            return EvaluationResult(
                status = "PENDING",
                explanation = "Match not started or awaiting final score (${status ?: "NS"})",
                homeScore = null,
                awayScore = null,
                isFinished = false
            )
        }

        val totalGoals = homeScore + awayScore
        val homeWon = homeScore > awayScore
        val draw = homeScore == awayScore
        val awayWon = awayScore > homeScore

        val rec = item.recommendedBet.lowercase(Locale.ROOT).trim()
        val cat = item.betTypeCategory.lowercase(Locale.ROOT).trim()
        val home = item.homeTeam.lowercase(Locale.ROOT).trim()
        val away = item.awayTeam.lowercase(Locale.ROOT).trim()

        val isFinished = status?.uppercase(Locale.ROOT) in listOf("FT", "AET", "PEN", "FINISHED", "FINAL", "POST-MATCH") || status == null

        // 1. Both Teams to Score (BTTS)
        if (cat.contains("btts") || rec.contains("both teams to score") || rec.contains("btts")) {
            val bothScored = homeScore > 0 && awayScore > 0
            val predictedYes = rec.contains("yes") || !rec.contains("no")
            val won = if (predictedYes) bothScored else !bothScored
            val expl = "Final: $homeScore-$awayScore (${if (bothScored) "Both teams scored" else "Clean sheet kept"}) → ${if (won) "Prediction HIT" else "Prediction MISSED"}"
            return EvaluationResult(if (won) "WON" else "LOST", expl, homeScore, awayScore, isFinished)
        }

        // 2. Over / Under Goals
        if (cat.contains("over") || cat.contains("under") || rec.contains("over ") || rec.contains("under ")) {
            val targetGoals = when {
                rec.contains("0.5") -> 0.5
                rec.contains("1.5") -> 1.5
                rec.contains("2.5") -> 2.5
                rec.contains("3.5") -> 3.5
                rec.contains("4.5") -> 4.5
                else -> 2.5
            }
            val isOver = rec.contains("over")
            val won = if (isOver) totalGoals > targetGoals else totalGoals < targetGoals
            val expl = "Final: $homeScore-$awayScore ($totalGoals total goals vs $targetGoals line) → ${if (won) "Prediction HIT" else "Prediction MISSED"}"
            return EvaluationResult(if (won) "WON" else "LOST", expl, homeScore, awayScore, isFinished)
        }

        // 3. Double Chance
        if (cat.contains("double") || rec.contains("1x") || rec.contains("x2") || rec.contains("12") || rec.contains("or draw")) {
            val won = when {
                rec.contains("1x") || (rec.contains(home) && rec.contains("draw")) -> homeWon || draw
                rec.contains("x2") || (rec.contains(away) && rec.contains("draw")) -> awayWon || draw
                rec.contains("12") || (rec.contains(home) && rec.contains(away)) -> homeWon || awayWon
                else -> homeWon || draw
            }
            val scoreSummary = if (homeWon) "${item.homeTeam} Won ($homeScore-$awayScore)" else if (awayWon) "${item.awayTeam} Won ($homeScore-$awayScore)" else "Draw ($homeScore-$awayScore)"
            val expl = "Final: $scoreSummary → ${if (won) "Prediction HIT" else "Prediction MISSED"}"
            return EvaluationResult(if (won) "WON" else "LOST", expl, homeScore, awayScore, isFinished)
        }

        // 4. Draw No Bet (DNB)
        if (cat.contains("draw no bet") || rec.contains("draw no bet") || rec.contains("dnb")) {
            val pickedHome = rec.contains(home) || rec.contains("home")
            if (draw) {
                return EvaluationResult("VOID", "Final: Draw ($homeScore-$awayScore) → Stake Refunded / Push (VOID)", homeScore, awayScore, isFinished)
            }
            val won = if (pickedHome) homeWon else awayWon
            val winner = if (homeWon) item.homeTeam else item.awayTeam
            val expl = "Final: $winner won ($homeScore-$awayScore) → ${if (won) "Prediction HIT" else "Prediction MISSED"}"
            return EvaluationResult(if (won) "WON" else "LOST", expl, homeScore, awayScore, isFinished)
        }

        // 5. Handicap
        if (cat.contains("handicap") || rec.contains("handicap") || rec.contains("ah ")) {
            val won = if (rec.contains("-0.5")) {
                if (rec.contains(home)) homeWon else awayWon
            } else if (rec.contains("+1.5")) {
                if (rec.contains(home)) (homeScore + 1.5) > awayScore else (awayScore + 1.5) > homeScore
            } else {
                if (rec.contains(home)) homeWon else awayWon
            }
            val expl = "Final: $homeScore-$awayScore with Handicap applied → ${if (won) "Prediction HIT" else "Prediction MISSED"}"
            return EvaluationResult(if (won) "WON" else "LOST", expl, homeScore, awayScore, isFinished)
        }

        // 6. Combo Bets
        if (cat.contains("combo") || rec.contains("&") || rec.contains("and")) {
            val cond1 = if (rec.contains(home)) homeWon || (rec.contains("1x") && (homeWon || draw)) else if (rec.contains(away)) awayWon else homeWon
            val cond2 = if (rec.contains("over 1.5")) totalGoals > 1 else if (rec.contains("both teams")) (homeScore > 0 && awayScore > 0) else true
            val won = cond1 && cond2
            val expl = "Final: $homeScore-$awayScore ($totalGoals goals) → ${if (won) "Combo HIT" else "Combo MISSED"}"
            return EvaluationResult(if (won) "WON" else "LOST", expl, homeScore, awayScore, isFinished)
        }

        // 7. 1X2 Standard Match Winner
        val pickedHome = rec.contains(home) || rec.contains("home to win") || rec.contains("1 (")
        val pickedAway = rec.contains(away) || rec.contains("away to win") || rec.contains("2 (")
        val pickedDraw = rec.contains("draw") || rec.contains("x (")

        val won = when {
            pickedDraw -> draw
            pickedAway -> awayWon
            else -> pickedHome && homeWon
        }

        val outcomeName = if (homeWon) "${item.homeTeam} won" else if (awayWon) "${item.awayTeam} won" else "Draw"
        val expl = "Final: $homeScore-$awayScore ($outcomeName) → ${if (won) "Prediction HIT" else "Prediction MISSED"}"
        return EvaluationResult(if (won) "WON" else "LOST", expl, homeScore, awayScore, isFinished)
    }

    /**
     * Evaluates all items in a slip and computes overall slip status.
     */
    fun evaluateSlip(
        slip: SavedPredictionSlip,
        scoreMap: Map<Int, Triple<Int, Int, String>> = emptyMap() // matchId -> (homeScore, awayScore, status)
    ): SavedPredictionSlip {
        val updatedItems = slip.items.map { item ->
            val matchData = scoreMap[item.matchId]
            if (matchData != null) {
                val eval = evaluateItem(item, matchData.first, matchData.second, matchData.third)
                item.copy(
                    homeScore = eval.homeScore,
                    awayScore = eval.awayScore,
                    matchStatus = matchData.third,
                    outcomeStatus = eval.status,
                    outcomeExplanation = eval.explanation
                )
            } else if (item.homeScore != null && item.awayScore != null) {
                val eval = evaluateItem(item, item.homeScore, item.awayScore, item.matchStatus)
                item.copy(
                    outcomeStatus = eval.status,
                    outcomeExplanation = eval.explanation
                )
            } else {
                item
            }
        }

        val wonCount = updatedItems.count { it.outcomeStatus == "WON" }
        val lostCount = updatedItems.count { it.outcomeStatus == "LOST" }
        val voidCount = updatedItems.count { it.outcomeStatus == "VOID" }
        val pendingCount = updatedItems.count { it.outcomeStatus == "PENDING" }

        val overallStatus = when {
            lostCount > 0 -> "LOST"
            pendingCount > 0 && wonCount == 0 -> "PENDING"
            pendingCount > 0 -> "IN_PROGRESS"
            wonCount > 0 && wonCount + voidCount == updatedItems.size -> "WON"
            wonCount > 0 -> "PARTIAL"
            else -> "PENDING"
        }

        return slip.copy(
            items = updatedItems,
            overallStatus = overallStatus,
            wonItemsCount = wonCount,
            lostItemsCount = lostCount,
            voidItemsCount = voidCount,
            pendingItemsCount = pendingCount,
            lastCheckedTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Generates a realistic simulated match result for testing / fast offline verification.
     */
    fun generateSimulatedScore(item: PredictedBetItem, forceWin: Boolean = false): Triple<Int, Int, String> {
        val rec = item.recommendedBet.lowercase(Locale.ROOT)
        val home = item.homeTeam.lowercase(Locale.ROOT)
        val away = item.awayTeam.lowercase(Locale.ROOT)

        if (forceWin) {
            return when {
                rec.contains("over 2.5") -> Triple(2, 1, "FT")
                rec.contains("over 1.5") -> Triple(2, 0, "FT")
                rec.contains("under 2.5") -> Triple(1, 0, "FT")
                rec.contains("under 3.5") -> Triple(1, 1, "FT")
                rec.contains("btts") && (rec.contains("yes") || !rec.contains("no")) -> Triple(2, 1, "FT")
                rec.contains("btts - no") -> Triple(2, 0, "FT")
                rec.contains(home) || rec.contains("1x") -> Triple(2, 1, "FT")
                rec.contains(away) || rec.contains("x2") -> Triple(0, 2, "FT")
                rec.contains("draw") -> Triple(1, 1, "FT")
                else -> Triple(2, 1, "FT")
            }
        }

        // Realistic seed-based simulation using team names
        val hash = (item.homeTeam.hashCode() xor item.awayTeam.hashCode()).let { if (it < 0) -it else it }
        val homeGoals = (hash % 4)
        val awayGoals = ((hash / 4) % 3)

        return Triple(homeGoals, awayGoals, "FT")
    }
}
