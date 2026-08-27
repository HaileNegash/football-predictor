package com.example.models

enum class BatchItemStatus(val label: String, val colorHex: Long) {
    PENDING("PENDING", 0xFF90A4AE),
    PREDICTING("PREDICTING...", 0xFFFF6D00),
    FINISHED("FINISHED", 0xFF00E676),
    FAILED("FAILED", 0xFFFF5252)
}

data class AgentBatchMatchItem(
    val matchId: Int,
    val homeTeam: String,
    val awayTeam: String,
    val homeLogo: String? = null,
    val awayLogo: String? = null,
    val leagueName: String,
    val startTime: String,
    val isSelected: Boolean = true,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val currentAgentAction: String = "Queued in agent pipeline...",
    val prediction: PredictionResult? = null,
    // Identifiers carried through from Match so the agent loop can fetch real
    // context (standings, H2H, injuries, odds) rather than sending two names.
    val homeTeamId: Int? = null,
    val awayTeamId: Int? = null,
    val leagueId: Int? = null,
    val season: Int? = null,
    val countryName: String? = null,
    val round: String? = null,
    /** Kickoff epoch seconds, carried through so saved legs know when to settle. */
    val kickoffEpoch: Long? = null,
    /** Populated when a prediction attempt fails, so the UI can say why. */
    val failureReason: String? = null
)

data class AgentStreamLog(
    val timestamp: String,
    val message: String,
    val type: String = "INFO" // "INFO", "SEARCH", "AI", "SUCCESS", "WARN"
)

/** Outcome of a settled leg. PENDING until the fixture finishes. */
enum class BetOutcome {
    PENDING,   // fixture not finished yet
    WON,
    LOST,
    VOID,      // fixture abandoned/postponed, or the market can't be settled
    UNKNOWN    // finished but this pick isn't one we know how to grade
}

data class PredictedBetItem(
    val matchId: Int,
    val homeTeam: String,
    val awayTeam: String,
    val homeLogo: String? = null,
    val awayLogo: String? = null,
    val leagueName: String,
    val startTime: String,
    val recommendedBet: String,
    val confidence: Int,
    val rationale: String,
    /**
     * Decimal odds as a string. Nullable and *not* defaulted to "1.75": the old
     * default silently fed an invented price into the combined-odds and payout
     * maths, so a slip with no real market data still displayed a specific
     * expected return.
     */
    val simulatedOdds: String? = null,
    /** Nullable rather than defaulting to "1X2", which mislabelled every unknown pick. */
    val betTypeCategory: String? = null,
    /** False when the pick came from the offline heuristic rather than a real model call. */
    val isModelBacked: Boolean = false,
    /** Model probability minus market-implied probability, in percentage points. */
    val edgePercent: Double? = null,
    /** True when [simulatedOdds] is a real bookmaker price rather than derived fair odds. */
    val isMarketPrice: Boolean = false,
    // ---- settlement ----
    val outcome: BetOutcome = BetOutcome.PENDING,
    val finalHomeScore: Int? = null,
    val finalAwayScore: Int? = null,
    val settledAt: Long = 0L,
    /**
     * Kickoff in epoch seconds. Used to skip legs that cannot possibly have a result
     * yet: the settlement budget is only ~40 fixture ids per pass, and spending it on
     * fixtures that haven't started returns NS for all of them.
     */
    val kickoffEpoch: Long? = null
) {
    val oddsValue: Double? get() = simulatedOdds?.toDoubleOrNull()?.takeIf { it > 1.0 }
}

data class SavedPredictionSlip(
    val slipId: String,
    val timestamp: Long,
    val dateString: String,
    val items: List<PredictedBetItem>,
    val totalMatches: Int,
    val averageConfidence: Int,
    val totalCombinedOdds: String,
    val currencyCode: String = "USD",
    val currencySymbol: String = "$",
    val budgetStake: Float = 50f,
    val estimatedPayout: Double = 0.0,
    val potentialProfit: Double = 0.0,
    val targetMin: Float = 10f,
    val targetMax: Float = 250f,
    /**
     * Joint probability of every leg landing, as a percentage. For an accumulator
     * this is the *product* of the leg probabilities, not their mean —
     * [averageConfidence] of 80 across 5 legs is a joint chance near 33%, and
     * showing the mean is what made long slips look far safer than they are.
     */
    val jointProbability: Int = 0,
    /** Slip-level settlement, derived from the legs. */
    val outcome: BetOutcome = BetOutcome.PENDING,
    val settledAt: Long = 0L
) {
    /**
     * Legs with a usable result. UNKNOWN is excluded deliberately: those are legs
     * whose pick text the grader could not interpret, so counting them as settled
     * would put them in the denominator of a W-L record they can never contribute to.
     */
    val settledLegs: Int
        get() = items.count { it.outcome == BetOutcome.WON || it.outcome == BetOutcome.LOST || it.outcome == BetOutcome.VOID }
    val wonLegs: Int get() = items.count { it.outcome == BetOutcome.WON }
    val lostLegs: Int get() = items.count { it.outcome == BetOutcome.LOST }
    val ungradableLegs: Int get() = items.count { it.outcome == BetOutcome.UNKNOWN }
}

/**
 * Realised accuracy, computed from settled legs only. This is the number that
 * tells the user whether the predictions are any good; without it, confidence
 * figures are unfalsifiable.
 */
data class AccuracyStats(
    val settledLegs: Int = 0,
    val wonLegs: Int = 0,
    val lostLegs: Int = 0,
    val voidLegs: Int = 0,
    val pendingLegs: Int = 0,
    /**
     * Legs that finished but whose pick text the grader could not interpret. Counted
     * and surfaced rather than silently dropped: a large number here means the hit
     * rate is computed over a biased subset of picks, which is worth knowing.
     */
    val ungradableLegs: Int = 0,
    /** Return per unit staked across all settled legs, flat-staked. 1.0 = break-even. */
    val roi: Double? = null,
    val buckets: List<CalibrationBucket> = emptyList()
) {
    val hitRate: Double?
        get() = if (wonLegs + lostLegs > 0) wonLegs.toDouble() / (wonLegs + lostLegs) else null
}

/**
 * One confidence band with its realised hit rate. If the model is calibrated,
 * [claimedAverage] and [actualRate] should track each other; a persistent gap is
 * overconfidence and is directly actionable.
 */
data class CalibrationBucket(
    val label: String,
    val claimedAverage: Int,
    val settled: Int,
    val won: Int
) {
    val actualRate: Double? get() = if (settled > 0) won.toDouble() / settled else null
    /** Positive means the model over-claimed by this many percentage points. */
    val overconfidenceGap: Double?
        get() = actualRate?.let { claimedAverage - it * 100.0 }
}
