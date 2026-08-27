package com.example.network

import com.example.models.AccuracyStats
import com.example.models.BetOutcome
import com.example.models.CalibrationBucket
import com.example.models.PredictedBetItem
import com.example.models.SavedPredictionSlip

/**
 * Slip-level maths: joint probability, combined odds, expected value, and realised
 * accuracy from settled legs.
 *
 * Two things here correct real errors in the previous implementation:
 *  - An accumulator's chance of landing is the *product* of its legs, not the mean
 *    of their confidences. Five legs at 80% is ~33%, not 80%. Showing the mean made
 *    long slips look dramatically safer than they were.
 *  - Legs with no real price were being given an invented 1.75, which then
 *    multiplied into a specific-looking payout figure. Here a missing price makes
 *    the combined odds explicitly unknown instead.
 */
object SlipMath {

    /**
     * Joint probability that every leg lands, 0..1.
     *
     * Assumes independence, which is not strictly true (legs in the same
     * competition correlate), so treat it as an upper bound. It is still far closer
     * to reality than averaging.
     */
    fun jointProbability(items: List<PredictedBetItem>): Double? {
        if (items.isEmpty()) return null
        var p = 1.0
        items.forEach { item ->
            val legP = item.confidence.coerceIn(1, 99) / 100.0
            p *= legP
        }
        return p
    }

    /**
     * Product of the leg odds, or null when any leg lacks a real price — the whole
     * accumulator price is unknowable in that case, and a partial product would
     * understate it.
     */
    fun combinedOdds(items: List<PredictedBetItem>): Double? {
        if (items.isEmpty()) return null
        var acc = 1.0
        items.forEach { item ->
            val o = item.oddsValue ?: return null
            acc *= o
        }
        return acc
    }

    /**
     * Expected value per unit staked. 1.0 is break-even; above 1.0 means the
     * model's probability implies the price is generous.
     *
     * ev = p * odds, since a losing stake returns nothing.
     */
    fun expectedValue(items: List<PredictedBetItem>): Double? {
        val p = jointProbability(items) ?: return null
        val odds = combinedOdds(items) ?: return null
        return p * odds
    }

    /** Per-leg EV, used to rank and trim a slip down to its best legs. */
    fun legExpectedValue(item: PredictedBetItem): Double? {
        val odds = item.oddsValue ?: return null
        return (item.confidence.coerceIn(1, 99) / 100.0) * odds
    }

    /**
     * Orders legs by value rather than by raw confidence.
     *
     * Ranking by confidence alone systematically selects short-priced favourites,
     * which is exactly the set of bets where the bookmaker margin bites hardest.
     * Legs with a measured edge against the market come first, then legs with a
     * positive EV, then everything unpriced ordered by confidence.
     */
    fun rankByValue(items: List<PredictedBetItem>): List<PredictedBetItem> =
        items.sortedWith(
            compareByDescending<PredictedBetItem> { it.edgePercent ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { legExpectedValue(it) ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.isModelBacked }
                .thenByDescending { it.confidence }
        )

    /**
     * Picks the [maxLegs] best legs. Shorter slips with real edges beat long slips
     * assembled to inflate the headline odds: each added leg multiplies the failure
     * probability, so a 12-leg accumulator is mostly a lottery ticket regardless of
     * how good the individual picks are.
     */
    fun selectBestLegs(items: List<PredictedBetItem>, maxLegs: Int): List<PredictedBetItem> =
        rankByValue(items).take(maxLegs.coerceAtLeast(1))

    // ---------------------------------------------------------------- accuracy

    /**
     * Realised performance across every settled leg in [slips].
     *
     * ROI is flat-staked: one unit per leg, returning `odds` on a win, the stake
     * back on a void, and nothing on a loss. Legs with no recorded price are
     * excluded from ROI (they still count toward hit rate) because there is no
     * honest return to compute for them.
     */
    fun accuracyFrom(slips: List<SavedPredictionSlip>): AccuracyStats {
        val legs = slips.flatMap { it.items }
        if (legs.isEmpty()) return AccuracyStats()

        var won = 0
        var lost = 0
        var void = 0
        var pending = 0
        var ungradable = 0
        var staked = 0.0
        var returned = 0.0

        legs.forEach { leg ->
            when (leg.outcome) {
                BetOutcome.WON -> {
                    won++
                    leg.oddsValue?.let { odds -> staked += 1.0; returned += odds }
                }
                BetOutcome.LOST -> {
                    lost++
                    leg.oddsValue?.let { staked += 1.0 }
                }
                BetOutcome.VOID -> {
                    void++
                    leg.oddsValue?.let { staked += 1.0; returned += 1.0 }
                }
                BetOutcome.PENDING -> pending++
                // Graded but unsettleable. Counted so the UI can say how much of the
                // record is missing, but excluded from hit rate and ROI — there is no
                // honest win/loss to assign.
                BetOutcome.UNKNOWN -> ungradable++
            }
        }

        return AccuracyStats(
            settledLegs = won + lost + void,
            wonLegs = won,
            lostLegs = lost,
            voidLegs = void,
            pendingLegs = pending,
            ungradableLegs = ungradable,
            roi = if (staked > 0.0) returned / staked else null,
            buckets = calibrationBuckets(legs)
        )
    }

    /**
     * Buckets settled legs by claimed confidence so over-confidence becomes
     * visible. If the 80-89 bucket wins 55% of the time, the model is not 80%
     * confident about anything and the prompt's calibration section needs work.
     */
    private fun calibrationBuckets(legs: List<PredictedBetItem>): List<CalibrationBucket> {
        val graded = legs.filter { it.outcome == BetOutcome.WON || it.outcome == BetOutcome.LOST }
        if (graded.isEmpty()) return emptyList()

        val bands = listOf(
            "50-59" to 50..59,
            "60-69" to 60..69,
            "70-79" to 70..79,
            "80-89" to 80..89,
            "90-100" to 90..100
        )

        return bands.mapNotNull { (label, range) ->
            val inBand = graded.filter { it.confidence in range }
            if (inBand.isEmpty()) return@mapNotNull null
            CalibrationBucket(
                label = label,
                claimedAverage = inBand.map { it.confidence }.average().toInt(),
                settled = inBand.size,
                won = inBand.count { it.outcome == BetOutcome.WON }
            )
        }
    }

    /**
     * Slip outcome derived from its legs: any lost leg loses the accumulator, all
     * remaining legs won wins it, otherwise still pending.
     *
     * VOID and UNKNOWN legs are both excluded from the decision. VOID is the normal
     * bookmaker treatment (stake returned, leg drops out). UNKNOWN is a leg whose
     * pick text the grader could not interpret — leaving it in the decidable set
     * would pin the slip at PENDING permanently, because an ungradable leg can never
     * become WON no matter how many results arrive.
     */
    fun slipOutcome(items: List<PredictedBetItem>): BetOutcome {
        if (items.isEmpty()) return BetOutcome.PENDING
        if (items.any { it.outcome == BetOutcome.LOST }) return BetOutcome.LOST
        val decidable = items.filter {
            it.outcome != BetOutcome.VOID && it.outcome != BetOutcome.UNKNOWN
        }
        if (decidable.isEmpty()) return BetOutcome.VOID
        return if (decidable.all { it.outcome == BetOutcome.WON }) BetOutcome.WON else BetOutcome.PENDING
    }
}
