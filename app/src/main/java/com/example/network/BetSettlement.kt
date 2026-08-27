package com.example.network

import com.example.models.BetOutcome

/**
 * Grades a pick against a final score.
 *
 * This exists because the app previously had no way to know whether any of its
 * predictions were correct. Confidence numbers that are never checked against
 * outcomes are unfalsifiable, so the model could claim 80% forever without
 * anything contradicting it. Settlement closes that loop.
 *
 * Deliberately conservative: anything the parser does not clearly recognise
 * returns [BetOutcome.UNKNOWN] rather than being guessed at, because a wrong
 * settlement corrupts the accuracy history it feeds.
 */
object BetSettlement {

    /** Status codes from API-Football that mean the 90 minutes are complete. */
    private val FINISHED_STATUSES = setOf("FT", "AET", "PEN")

    /** Statuses where no result exists, so stakes are returned. */
    private val VOID_STATUSES = setOf("PST", "CANC", "ABD", "AWD", "WO", "SUSP", "INT")

    fun isFinished(statusShort: String?): Boolean =
        statusShort != null && statusShort.uppercase() in FINISHED_STATUSES

    fun isVoid(statusShort: String?): Boolean =
        statusShort != null && statusShort.uppercase() in VOID_STATUSES

    /**
     * Settles [pick] for a fixture that ended [homeScore]-[awayScore].
     *
     * [homeTeam] and [awayTeam] are needed because picks are free text and often
     * name the team ("Arsenal to Win") rather than the side ("Home").
     *
     * [halftimeHome]/[halftimeAway] enable half-time and HT/FT markets. When they
     * are absent those picks return [BetOutcome.UNKNOWN] rather than being graded
     * against the full-time score, which would be wrong roughly half the time.
     */
    fun settle(
        pick: String,
        homeTeam: String,
        awayTeam: String,
        homeScore: Int?,
        awayScore: Int?,
        statusShort: String?,
        halftimeHome: Int? = null,
        halftimeAway: Int? = null
    ): BetOutcome {
        if (isVoid(statusShort)) return BetOutcome.VOID
        if (!isFinished(statusShort)) return BetOutcome.PENDING
        if (homeScore == null || awayScore == null) return BetOutcome.PENDING

        val p = pick.lowercase().trim()
        if (p.isBlank()) return BetOutcome.UNKNOWN

        val total = homeScore + awayScore
        val homeWon = homeScore > awayScore
        val awayWon = awayScore > homeScore
        val drew = homeScore == awayScore
        val bothScored = homeScore > 0 && awayScore > 0

        // Resolve which side the text refers to. Team-name matching comes first
        // because "Draw No Bet - Arsenal" contains neither "home" nor "away".
        val namesHome = mentionsTeam(p, homeTeam)
        val namesAway = mentionsTeam(p, awayTeam)
        val saysHome = namesHome || p.contains("home")
        val saysAway = namesAway || p.contains("away")

        return when {
            // ---- Both teams to score. Checked before generic "no"/"yes" logic.
            p.contains("btts") || p.contains("both teams") -> {
                val wantsNo = p.contains("no")
                grade(if (wantsNo) !bothScored else bothScored)
            }

            // ---- HT/FT and half-time markets. Must precede Over/Under and 1X2,
            // both of which would otherwise match on the same text and silently
            // grade a half-time pick against the full-time score.
            isHalfTimeMarket(p) -> settleHalfTime(
                p, homeScore, awayScore, halftimeHome, halftimeAway, saysHome, saysAway
            )

            // ---- Correct score. Ahead of the handicap branch because "2-1" carries a
            // hyphen that would otherwise read as a signed handicap line.
            p.contains("correct score") -> {
                val (h, a) = extractScore(p) ?: return BetOutcome.UNKNOWN
                grade(homeScore == h && awayScore == a)
            }

            // ---- Asian/European handicap. Before Over/Under: "Arsenal -1.5" has a
            // signed line but no "over"/"under" keyword, while "AH Over" does.
            isHandicapMarket(p) -> settleHandicap(
                p, homeScore, awayScore, saysHome, saysAway
            )

            // ---- Over/Under. Parse the line so 1.5/2.5/3.5 all settle correctly.
            p.contains("over") || p.contains("under") -> {
                // Team totals need per-team goals, which we have, but the pick must
                // say whose. An unqualified "Over 1.5" is the match total.
                val line = extractLine(p) ?: return BetOutcome.UNKNOWN
                val subject = when {
                    saysHome && !saysAway -> homeScore
                    saysAway && !saysHome -> awayScore
                    else -> total
                }
                // Half-lines can't push; whole lines can.
                if (subject.toDouble() == line) return BetOutcome.VOID
                grade(if (p.contains("over")) subject > line else subject < line)
            }

            // ---- Double chance. Two of the three 1X2 outcomes.
            p.contains("double chance") || p.contains("1x") || p.contains("x2") ||
                    (p.contains(" or ") && (p.contains("draw") || saysHome || saysAway)) -> {
                when {
                    p.contains("1x") || (saysHome && p.contains("draw")) -> grade(homeWon || drew)
                    p.contains("x2") || (saysAway && p.contains("draw")) -> grade(awayWon || drew)
                    p.contains("12") || (saysHome && saysAway) -> grade(!drew)
                    else -> BetOutcome.UNKNOWN
                }
            }

            // ---- Draw no bet. A draw returns the stake.
            p.contains("draw no bet") || p.contains("dnb") -> when {
                drew -> BetOutcome.VOID
                saysHome && !saysAway -> grade(homeWon)
                saysAway && !saysHome -> grade(awayWon)
                else -> BetOutcome.UNKNOWN
            }

            // ---- Correct score. Already handled above, before the handicap branch.
            // ---- Clean sheet / to nil.
            p.contains("clean sheet") || p.contains("to nil") -> when {
                saysHome && !saysAway -> grade(awayScore == 0)
                saysAway && !saysHome -> grade(homeScore == 0)
                else -> BetOutcome.UNKNOWN
            }

            // ---- Odd/even total goals. Matched on whole words only: "odd" is a
            // substring of "odds", so a plain pick like "Home Win (best odds)" would
            // otherwise be routed here and come back ungradable.
            ODD_REGEX.containsMatchIn(p) -> grade(total % 2 == 1)
            EVEN_REGEX.containsMatchIn(p) -> grade(total % 2 == 0)

            // ---- Straight 1X2. Kept last so it doesn't swallow the above.
            p.contains("draw") && !saysHome && !saysAway -> grade(drew)
            saysHome && !saysAway -> grade(homeWon)
            saysAway && !saysHome -> grade(awayWon)

            // Corners, cards, player props and combos need data we don't fetch.
            else -> BetOutcome.UNKNOWN
        }
    }

    private fun grade(won: Boolean) = if (won) BetOutcome.WON else BetOutcome.LOST

    // ------------------------------------------------------------- half-time

    private fun isHalfTimeMarket(p: String): Boolean =
        p.contains("ht/ft") || p.contains("half time") || p.contains("half-time") ||
                p.contains("halftime") || p.contains("1st half") || p.contains("first half")

    /**
     * Grades half-time and HT/FT picks.
     *
     * Returns UNKNOWN when no half-time score was supplied. That is the whole point
     * of the [halftimeHome] parameter existing: previously these picks fell through
     * to the Over/Under and 1X2 branches and were graded against the *full-time*
     * score, producing confident wrong settlements rather than honest gaps.
     */
    private fun settleHalfTime(
        p: String,
        homeScore: Int,
        awayScore: Int,
        htHome: Int?,
        htAway: Int?,
        saysHome: Boolean,
        saysAway: Boolean
    ): BetOutcome {
        if (htHome == null || htAway == null) return BetOutcome.UNKNOWN

        val htTotal = htHome + htAway
        val htResult = resultChar(htHome, htAway)
        val ftResult = resultChar(homeScore, awayScore)

        // "HT/FT 1/1", "Home/Home", "1-X" — two outcomes joined by a separator.
        HT_FT_REGEX.find(p)?.let { m ->
            val wantHt = normaliseResultToken(m.groupValues[1]) ?: return BetOutcome.UNKNOWN
            val wantFt = normaliseResultToken(m.groupValues[2]) ?: return BetOutcome.UNKNOWN
            return grade(htResult == wantHt && ftResult == wantFt)
        }

        // First-half goals line.
        if (p.contains("over") || p.contains("under")) {
            val line = extractLine(p) ?: return BetOutcome.UNKNOWN
            val subject = when {
                saysHome && !saysAway -> htHome
                saysAway && !saysHome -> htAway
                else -> htTotal
            }
            if (subject.toDouble() == line) return BetOutcome.VOID
            return grade(if (p.contains("over")) subject > line else subject < line)
        }

        // First-half BTTS.
        if (p.contains("btts") || p.contains("both teams")) {
            val both = htHome > 0 && htAway > 0
            return grade(if (p.contains("no")) !both else both)
        }

        // Plain half-time result.
        return when {
            p.contains("draw") && !saysHome && !saysAway -> grade(htHome == htAway)
            saysHome && !saysAway -> grade(htHome > htAway)
            saysAway && !saysHome -> grade(htAway > htHome)
            else -> BetOutcome.UNKNOWN
        }
    }

    private fun resultChar(homeScore: Int, awayScore: Int): Char = when {
        homeScore > awayScore -> '1'
        awayScore > homeScore -> '2'
        else -> 'x'
    }

    /** Maps "1"/"home"/"x"/"draw"/"2"/"away" onto the 1/x/2 result characters. */
    private fun normaliseResultToken(raw: String): Char? = when (raw.trim()) {
        "1", "home" -> '1'
        "x", "draw" -> 'x'
        "2", "away" -> '2'
        else -> null
    }

    /**
     * The two halves of an HT/FT pick: "1/1", "Home/Draw", "X/2".
     *
     * Only "/" is accepted as the separator, and single-letter abbreviations are not.
     * Allowing "-" and letters like "d" made this match ordinary prose — "Half Time
     * Home - Away" or any hyphenated phrase — and a false match here produces a
     * confident wrong settlement, which is worse than an ungraded leg.
     */
    private val HT_FT_REGEX =
        Regex("""\b(1|2|x|home|away|draw)\s*/\s*(1|2|x|home|away|draw)\b""")

    // -------------------------------------------------------------- handicap

    private fun isHandicapMarket(p: String): Boolean =
        p.contains("handicap") || p.contains(" ah ") || p.startsWith("ah ") ||
                SIGNED_LINE_REGEX.containsMatchIn(p)

    /**
     * Grades an Asian/European handicap by applying the line to the side named in
     * the pick, then comparing adjusted scores.
     *
     * Quarter lines (-0.25, -0.75) are split bets — half the stake on each adjacent
     * half-line — and settling them as a single win or loss would misstate both the
     * hit rate and ROI, so they return UNKNOWN.
     */
    private fun settleHandicap(
        p: String,
        homeScore: Int,
        awayScore: Int,
        saysHome: Boolean,
        saysAway: Boolean
    ): BetOutcome {
        val line = extractSignedLine(p) ?: return BetOutcome.UNKNOWN

        // Quarter-line split bets can't be a single WON/LOST.
        val quarter = Math.abs(line % 1.0)
        if (quarter > 0.2 && quarter < 0.3) return BetOutcome.UNKNOWN
        if (quarter > 0.7 && quarter < 0.8) return BetOutcome.UNKNOWN

        // The line belongs to whichever side the pick names. Ambiguous picks are not
        // gradable: "Handicap -1" without a team could go either way.
        val adjusted = when {
            saysHome && !saysAway -> (homeScore + line) - awayScore
            saysAway && !saysHome -> (awayScore + line) - homeScore
            else -> return BetOutcome.UNKNOWN
        }

        return when {
            adjusted > 0.0 -> BetOutcome.WON
            adjusted < 0.0 -> BetOutcome.LOST
            // Exact push on a whole line: stake returned.
            else -> BetOutcome.VOID
        }
    }

    /**
     * Pulls a signed handicap line: "-1.5", "+2", "(-0.5)". Returns null when the
     * text has no explicit sign, since an unsigned number is a goals line, not a
     * handicap.
     */
    private fun extractSignedLine(p: String): Double? =
        SIGNED_LINE_REGEX.find(p)?.groupValues?.get(1)?.replace(" ", "")?.toDoubleOrNull()

    /**
     * The sign must open a token — preceded by start-of-string, whitespace or an
     * opening bracket. Without that guard the hyphen in a correct-score pick
     * ("Correct Score 2-1") matched as a "-1" handicap line and routed the whole
     * pick to the handicap grader.
     */
    private val SIGNED_LINE_REGEX = Regex("""(?:^|[\s(\[])([+-]\s*\d+(?:\.\d+)?)""")

    private val ODD_REGEX = Regex("""\bodd\b""")
    private val EVEN_REGEX = Regex("""\beven\b""")

    /**
     * Matches a team by name, tolerating the suffixes providers add ("Manchester
     * United FC" vs "Man United"). Requires a token of 4+ characters to overlap so
     * short words like "FC" or "AC" can't produce a false match.
     */
    private fun mentionsTeam(pickLower: String, team: String): Boolean {
        val cleaned = team.lowercase().trim()
        if (cleaned.isEmpty()) return false
        if (pickLower.contains(cleaned)) return true
        val tokens = cleaned.split(' ', '-', '.')
            .filter { it.length >= 4 && it !in IGNORED_TOKENS }
        return tokens.any { pickLower.contains(it) }
    }

    private val IGNORED_TOKENS = setOf("city", "united", "town", "club", "real", "athletic")

    /** Pulls the goals line out of e.g. "Over 2.5 Total Goals". */
    private fun extractLine(pickLower: String): Double? =
        LINE_REGEX.find(pickLower)?.value?.toDoubleOrNull()

    private val LINE_REGEX = Regex("""\d+(?:\.\d+)?""")

    /** Pulls "2-1" or "2:1" out of a correct-score pick. */
    private fun extractScore(pickLower: String): Pair<Int, Int>? {
        val m = SCORE_REGEX.find(pickLower) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val a = m.groupValues[2].toIntOrNull() ?: return null
        return h to a
    }

    private val SCORE_REGEX = Regex("""(\d+)\s*[-:]\s*(\d+)""")
}
