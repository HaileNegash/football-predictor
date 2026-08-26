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
    val prediction: PredictionResult? = null
)

data class AgentStreamLog(
    val timestamp: String,
    val message: String,
    val type: String = "INFO" // "INFO", "SEARCH", "AI", "SUCCESS", "WARN"
)

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
    val simulatedOdds: String = "1.75",
    val betTypeCategory: String = "1X2"
)

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
    val targetMax: Float = 250f
)
