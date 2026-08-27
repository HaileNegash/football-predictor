package com.example.network

import android.util.Log
import com.example.keymanager.ManagedApiKey
import com.example.models.MatchContext
import com.example.models.PredictionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Typed failure so callers can distinguish a rate limit from a bad key from a
 * server fault. The previous `Exception("HTTP $code: $body")` forced every call
 * site to pass `isAuthError = false`, which meant a 429 never triggered a
 * cooldown and a 401 never marked the key dead — key rotation was effectively
 * inert.
 */
class ApiHttpException(
    val code: Int,
    val bodySnippet: String
) : IOException("HTTP $code: ${bodySnippet.take(200)}") {
    val isRateLimit: Boolean get() = code == 429
    val isAuthError: Boolean get() = code == 401 || code == 403
    val isRetryable: Boolean get() = code == 429 || code in 500..599
}

object OpenAiService {
    private const val TAG = "OpenAiService"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private const val MAX_ATTEMPTS = 3

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Requests a prediction grounded in [ctx].
     *
     * Returns [Result.failure] when the model cannot be reached or its output
     * cannot be parsed. It deliberately does **not** synthesise a plausible-looking
     * prediction on failure: the old fallback returned `confidence = 75, odds = "1.85"`
     * with the raw error text as the rationale, which the UI then displayed as real
     * analysis. A visible failure is strictly better than an invented pick.
     */
    suspend fun generatePrediction(
        ctx: MatchContext,
        managedKey: ManagedApiKey,
        allowedBetTypes: List<String> = emptyList()
    ): Result<PredictionResult> = withContext(Dispatchers.IO) {
        val endpoint = resolveEndpoint(managedKey.endpointUrl)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Refusing to send key to non-HTTPS endpoint: ${managedKey.endpointUrl}")
            )
        val model = managedKey.modelName.trim().ifBlank { DEFAULT_MODEL }

        val requestJson = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", PredictionPromptBuilder.SYSTEM_PROMPT.trim())
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", PredictionPromptBuilder.buildUserPrompt(ctx, allowedBetTypes))
                })
            })
            // Low temperature: this is an estimation task, not a creative one.
            // Sampling variance here shows up directly as calibration noise.
            put("temperature", 0.15)
            // Required by Anthropic and several gateways; also prevents a
            // truncated response from being silently mis-parsed.
            put("max_tokens", 900)
            // Honoured by OpenAI-compatible servers that support it; harmless
            // elsewhere since unknown fields are ignored.
            put("response_format", JSONObject().apply { put("type", "json_object") })
        }

        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val content = executeChat(endpoint, requestJson, managedKey)
                val parsed = parsePrediction(content, ctx)
                    ?: return@withContext Result.failure(
                        IOException("Model returned unparseable output: ${content.take(200)}")
                    )
                return@withContext Result.success(parsed)
            } catch (e: ApiHttpException) {
                lastError = e
                if (!e.isRetryable || attempt == MAX_ATTEMPTS - 1) {
                    return@withContext Result.failure(e)
                }
                // Exponential backoff: 1s, 2s. Rate limits and 5xx are usually transient.
                delay(1000L * (1L shl attempt))
            } catch (e: SocketTimeoutException) {
                lastError = e
                if (attempt == MAX_ATTEMPTS - 1) {
                    return@withContext Result.failure(e)
                }
                delay(1000L * (1L shl attempt))
            } catch (e: Exception) {
                Log.w(TAG, "Prediction attempt failed for ${ctx.homeTeam} vs ${ctx.awayTeam}: ${e.message}")
                return@withContext Result.failure(e)
            }
        }
        Result.failure(lastError ?: IOException("Prediction failed after $MAX_ATTEMPTS attempts"))
    }

    private fun executeChat(
        endpoint: String,
        requestJson: JSONObject,
        managedKey: ManagedApiKey
    ): String {
        val builder = Request.Builder()
            .url(endpoint)
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json")

        if (managedKey.key.isNotBlank()) {
            builder.addHeader("Authorization", managedKey.authHeaderValue)
        }

        // use{} guarantees the connection is released even if body reading throws.
        httpClient.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Log the code but not the body verbatim — several providers echo
                // the submitted key back inside error payloads.
                Log.w(TAG, "Chat completion failed: HTTP ${response.code}")
                throw ApiHttpException(response.code, body)
            }

            val root = JSONObject(body)
            root.optJSONObject("error")?.let { err ->
                throw IOException("Provider error: ${err.optString("message", "unknown")}")
            }
            val choices = root.optJSONArray("choices")
                ?: throw IOException("Response contained no 'choices' array")
            if (choices.length() == 0) throw IOException("Response contained zero choices")

            val first = choices.optJSONObject(0)
                ?: throw IOException("Malformed choice entry")
            val message = first.optJSONObject("message")
                ?: throw IOException("Choice contained no 'message'")
            val content = message.optString("content", "")
            if (content.isBlank()) {
                val finish = first.optString("finish_reason", "")
                throw IOException("Model returned empty content (finish_reason=$finish)")
            }
            return content
        }
    }

    /**
     * Parses the model's JSON and derives the derived quantities we need.
     * Returns null on unparseable input so the caller can surface a real failure.
     */
    private fun parsePrediction(rawText: String, ctx: MatchContext): PredictionResult? {
        val jsonText = JsonExtractor.extractObject(rawText) ?: return null
        val json = try {
            JSONObject(jsonText)
        } catch (e: Exception) {
            return null
        }

        val pick = json.optString("recommendedBet", "").trim()
        if (pick.isBlank()) return null

        // Accept "probability" (new schema) or "confidence" (older prompt) so a
        // model that echoes the old key still parses.
        val rawProb = when {
            json.has("probability") -> json.optInt("probability", -1)
            json.has("confidence") -> json.optInt("confidence", -1)
            else -> -1
        }
        if (rawProb < 0) return null

        // Enforce the evidence ceiling on our side too. The model is asked to
        // respect it, but a model that ignores the instruction must not be able
        // to present an unsupported 95% to the user.
        val probability = rawProb.coerceIn(1, ctx.confidenceCeiling)

        val rationale = json.optString("rationale", "").trim()
            .ifBlank { "No rationale supplied by model." }
        val keyFactor = json.optString("keyFactor", "").trim().takeIf { it.isNotBlank() }
        val gaps = json.optString("dataGaps", "").trim()
            .takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
        val disagreement = json.optString("marketDisagreement", "").trim()
            .takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }

        val betType = json.optString("betType", "").trim().takeIf { it.isNotBlank() }

        // Fair odds implied by the model's own probability. If the model supplied
        // its own fairOdds we prefer the derived value — it must be consistent
        // with the probability to be usable for expected value.
        val fairOdds = if (probability > 0) 100.0 / probability else null

        // Real bookmaker price for this pick, when the market was fetched.
        val marketOdds = ctx.odds?.oddsFor(pick)
        val marketImplied = ctx.odds?.impliedFor(pick)

        // Edge in percentage points: model probability minus market probability.
        // Positive means the model thinks the market is too long on this pick.
        val edge = if (marketImplied != null) {
            probability - (marketImplied * 100.0)
        } else null

        val fullRationale = buildString {
            append(rationale)
            keyFactor?.let { append(" Key factor: $it.") }
            disagreement?.let { append(" Market read: $it.") }
            gaps?.let { append(" Limitation: $it.") }
        }.trim()

        return PredictionResult(
            recommendedBet = pick,
            confidence = probability,
            rationale = fullRationale,
            // Display odds: prefer the actual market price over the model's
            // theoretical fair price, since that is what the user would be offered.
            odds = (marketOdds ?: fairOdds)?.let { String.format(Locale.US, "%.2f", it) },
            betType = betType,
            isModelBacked = true,
            marketOdds = marketOdds?.let { String.format(Locale.US, "%.2f", it) },
            edgePercent = edge,
            dataSources = ctx.sources
        )
    }

    /**
     * Normalises the endpoint and refuses plaintext HTTP. [ManagedApiKey.endpointUrl]
     * comes from a Firestore document, so an attacker-controlled or misconfigured
     * value would otherwise get the API key sent in the clear.
     */
    private fun resolveEndpoint(rawEndpoint: String): String? {
        val trimmed = rawEndpoint.trim().ifBlank { "https://api.openai.com/v1/" }
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        val base = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        return if (base.trimEnd('/').endsWith("chat/completions")) {
            base.trimEnd('/')
        } else {
            "${base}chat/completions"
        }
    }

    /**
     * Fallback default. Note this is a placeholder rather than a real model id —
     * the value that actually ships should be whatever the configured provider
     * serves. See KeyRotationManager.DEFAULT_BRAIN_MODEL.
     */
    private const val DEFAULT_MODEL = "gpt-4o-mini"
}
