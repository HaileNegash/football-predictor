package com.example.network

import android.content.Context
import android.util.Log
import com.example.models.PredictionResult
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists generated predictions keyed by fixture id.
 *
 * Without this, predictions lived only in `_countries` and `_batchMatchItems`, both
 * in-memory StateFlows. Killing the app discarded every one of them, so the next
 * launch re-ran the whole batch: a fresh API-Football context fetch, a fresh
 * Firecrawl scrape and a fresh model completion per fixture, all to reproduce
 * answers that had already been paid for. On a free API tier and a metered LLM key
 * that is the single most expensive bug in the pipeline.
 *
 * Deliberately keyed on fixture id rather than on team names: names vary by provider
 * ("Man Utd" / "Manchester United FC") and would silently miss.
 */
class PredictionStore(context: Context) {

    private val prefs = context.getSharedPreferences("prediction_store", Context.MODE_PRIVATE)
    private val tag = "PredictionStore"

    companion object {
        private const val KEY = "cached_predictions_json"

        /**
         * How long a stored prediction stays usable.
         *
         * A prediction is about one fixture at one kickoff, so it does not go stale
         * in the usual sense — but team news moves, and a prediction made three days
         * before kickoff was made without the lineup. 36h keeps the common case
         * (predict in the evening, view the next day) free while forcing a refresh
         * on anything genuinely old.
         */
        const val TTL_MS = 36 * 60 * 60 * 1000L

        /**
         * Cap on stored entries. Prunes oldest-first. Without a cap this grows
         * unboundedly in SharedPreferences, which is read synchronously on the main
         * thread at startup.
         */
        const val MAX_ENTRIES = 400
    }

    /** One stored prediction plus when it was generated. */
    data class Entry(
        val fixtureId: Int,
        val prediction: PredictionResult,
        val storedAt: Long
    )

    /**
     * Loads all non-expired predictions, keyed by fixture id.
     *
     * Never throws: a corrupt or partially-written store returns empty rather than
     * blocking startup, since a lost cache costs requests but a crash costs the app.
     */
    fun load(): Map<Int, PredictionResult> {
        val now = System.currentTimeMillis()
        return readEntries()
            .filter { now - it.storedAt < TTL_MS }
            .associate { it.fixtureId to it.prediction }
    }

    /**
     * Merges [predictions] into the store, replacing any existing entry for the same
     * fixture. Expired and over-cap entries are pruned in the same pass so the write
     * is the only place that needs to think about size.
     */
    fun save(predictions: Map<Int, PredictionResult>) {
        if (predictions.isEmpty()) return
        val now = System.currentTimeMillis()

        val merged = LinkedHashMap<Int, Entry>()
        readEntries()
            .filter { now - it.storedAt < TTL_MS }
            .forEach { merged[it.fixtureId] = it }
        predictions.forEach { (id, pred) ->
            merged[id] = Entry(id, pred, now)
        }

        // Newest kept when over cap.
        val kept = merged.values
            .sortedByDescending { it.storedAt }
            .take(MAX_ENTRIES)

        try {
            val array = JSONArray()
            kept.forEach { array.put(it.toJson()) }
            prefs.edit().putString(KEY, array.toString()).apply()
        } catch (e: Exception) {
            Log.w(tag, "failed to persist predictions: ${e.message}")
        }
    }

    /** Drops everything. Used by the "clear cache" action. */
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun readEntries(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { entryFromJson(it) }
            }
        } catch (e: Exception) {
            Log.w(tag, "prediction store unreadable, discarding: ${e.message}")
            emptyList()
        }
    }

    private fun Entry.toJson(): JSONObject = JSONObject().apply {
        put("fixtureId", fixtureId)
        put("storedAt", storedAt)
        put("recommendedBet", prediction.recommendedBet)
        put("confidence", prediction.confidence)
        put("rationale", prediction.rationale)
        prediction.odds?.let { put("odds", it) }
        prediction.betType?.let { put("betType", it) }
        put("isModelBacked", prediction.isModelBacked)
        prediction.marketOdds?.let { put("marketOdds", it) }
        prediction.edgePercent?.let { put("edgePercent", it) }
        if (prediction.dataSources.isNotEmpty()) {
            put("dataSources", JSONArray().apply { prediction.dataSources.forEach { put(it) } })
        }
    }

    private fun entryFromJson(obj: JSONObject): Entry? {
        val fixtureId = obj.optInt("fixtureId", 0)
        val pick = obj.optString("recommendedBet")
        // A prediction with no fixture or no pick is not a prediction. Dropping it is
        // correct: it would otherwise suppress a real re-fetch for that fixture.
        if (fixtureId == 0 || pick.isBlank()) return null

        val sourcesArray = obj.optJSONArray("dataSources")
        val sources = if (sourcesArray == null) emptyList() else
            (0 until sourcesArray.length()).mapNotNull { sourcesArray.optString(it).takeIf { s -> s.isNotBlank() } }

        return Entry(
            fixtureId = fixtureId,
            storedAt = obj.optLong("storedAt", 0L),
            prediction = PredictionResult(
                recommendedBet = pick,
                confidence = obj.optInt("confidence", 0),
                rationale = obj.optString("rationale"),
                odds = obj.optString("odds").takeIf { it.isNotBlank() },
                betType = obj.optString("betType").takeIf { it.isNotBlank() },
                isModelBacked = obj.optBoolean("isModelBacked", false),
                marketOdds = obj.optString("marketOdds").takeIf { it.isNotBlank() },
                // `has` guard, not optDouble's 0.0 default: an absent edge is unknown,
                // and storing it as 0.0 would claim the model measured no edge.
                edgePercent = if (obj.has("edgePercent")) obj.optDouble("edgePercent") else null,
                dataSources = sources
            )
        )
    }
}
