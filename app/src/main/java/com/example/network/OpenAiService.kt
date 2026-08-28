package com.example.network

import android.util.Log
import com.example.keymanager.ManagedApiKey
import com.example.models.PredictionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit

object OpenAiService {
    private const val TAG = "OpenAiService"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Executes a chat completion request to any OpenAI-compatible API endpoint with support for
     * user-specified bet types, tactical intel, odds estimation, and confidence scoring.
     */
    suspend fun generatePrediction(
        homeTeam: String,
        awayTeam: String,
        league: String,
        managedKey: ManagedApiKey,
        allowedBetTypes: List<String> = emptyList(),
        tacticalIntel: String? = null
    ): Result<PredictionResult> = withContext(Dispatchers.IO) {
        try {
            val rawEndpoint = managedKey.endpointUrl.trim().ifBlank { "https://api.openai.com/v1/" }
            val baseUrl = if (rawEndpoint.endsWith("/")) rawEndpoint else "$rawEndpoint/"
            val endpoint = if (baseUrl.endsWith("chat/completions")) baseUrl else "${baseUrl}chat/completions"
            val model = managedKey.modelName.trim().ifBlank { "qwen-3.8-max-free" }

            val betTypesSection = if (allowedBetTypes.isNotEmpty()) {
                """
                ALLOWED BET TYPE MARKETS (You MUST pick the best value pick strictly adhering to one of these types):
                ${allowedBetTypes.joinToString("\n") { "- $it" }}
                
                Examples of market picks:
                - Over/Under: "Over 2.5 Goals", "Under 2.5 Goals", "Over 1.5 Goals"
                - BTTS: "Both Teams to Score (BTTS) - Yes", "Both Teams to Score - No"
                - Double Chance: "Double Chance (1X)", "Double Chance (X2)", "Double Chance (12)"
                - Draw No Bet: "Draw No Bet (DNB) - Home", "Draw No Bet (DNB) - Away"
                - Asian / European Handicap: "Asian Handicap -0.5 Home", "Handicap (+1) Away"
                - 1X2: "Home Win", "Away Win", "Draw"
                - Combo: "Home Win & Over 1.5 Goals", "1X & Under 3.5 Goals"
                """.trimIndent()
            } else {
                "ALLOWED MARKETS: 1X2, Over/Under 2.5 Goals, Both Teams to Score (BTTS), Double Chance, Draw No Bet"
            }

            val intelSection = if (!tacticalIntel.isNullOrBlank()) {
                "\nREAL-TIME SQUAD & TACTICAL NEWS:\n$tacticalIntel\n"
            } else ""

            val prompt = """
                Analyze the upcoming football match:
                - Home Team: $homeTeam
                - Away Team: $awayTeam
                - League: $league
                $intelSection
                $betTypesSection

                Respond STRICTLY in valid JSON format with the following keys:
                - "recommendedBet": The exact bet pick (e.g. "Over 2.5 Goals", "Both Teams to Score (BTTS)", "Double Chance (1X)", "Home Win", "Draw No Bet - Away")
                - "betType": The market type (e.g. "Over/Under", "BTTS", "Double Chance", "DNB", "Handicap", "1X2")
                - "confidence": Integer between 55 and 95 representing probability confidence percentage
                - "odds": Decimal odds float between 1.30 and 4.50 (e.g. 1.85, 2.10, 1.45)
                - "rationale": 1-2 concise tactical sentences explaining the selection based on form, xG, squad news, or matchup metrics.
            """.trimIndent()

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are an elite football tactical analyst, data scientist, and sports statistician. You provide high-value, calculated betting predictions matching the requested market types with strict JSON output.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.35)
            }

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")

            if (managedKey.key.isNotBlank()) {
                val authHeader = if (managedKey.key.startsWith("Bearer ", ignoreCase = true)) {
                    managedKey.key
                } else {
                    "Bearer ${managedKey.key.trim()}"
                }
                requestBuilder.addHeader("Authorization", authHeader)
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "OpenAI Compatible request failed: HTTP ${response.code} - $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val jsonObject = JSONObject(responseBody)
            val choices = jsonObject.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(Exception("No choices returned from model"))
            }

            val content = choices.getJSONObject(0).getJSONObject("message").getString("content")
            val parsedResult = parsePredictionJson(content, allowedBetTypes)
            Result.success(parsedResult)
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "OpenAI prediction timed out for $homeTeam vs $awayTeam - switching to smart tactical model")
            Result.failure(Exception("Model response timeout: using fallback tactical engine"))
        } catch (e: Exception) {
            Log.e(TAG, "Error executing OpenAI prediction: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAvailableModels(
        endpointUrl: String,
        apiKey: String
    ): Result<List<com.example.models.UserAiModel>> = withContext(Dispatchers.IO) {
        try {
            val rawEndpoint = endpointUrl.trim().ifBlank { "https://api.openai.com/v1/" }
            val cleanUrl = if (rawEndpoint.endsWith("/")) rawEndpoint else "$rawEndpoint/"

            val isGemini = cleanUrl.contains("generativelanguage.googleapis.com", ignoreCase = true)
            val requestUrl = if (isGemini) {
                if (cleanUrl.contains("models")) cleanUrl.trimEnd('/') + "?key=$apiKey"
                else "${cleanUrl}v1beta/models?key=$apiKey"
            } else {
                if (cleanUrl.endsWith("models/")) cleanUrl.trimEnd('/') else "${cleanUrl}models"
            }

            val requestBuilder = Request.Builder().url(requestUrl).get()
            if (!isGemini && apiKey.isNotBlank()) {
                val authHeader = if (apiKey.startsWith("Bearer ", ignoreCase = true)) apiKey else "Bearer ${apiKey.trim()}"
                requestBuilder.addHeader("Authorization", authHeader)
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: $body"))
            }

            val json = JSONObject(body)
            val modelsList = mutableListOf<com.example.models.UserAiModel>()

            val providerName = when {
                cleanUrl.contains("openrouter.ai") -> "OpenRouter"
                cleanUrl.contains("api.openai.com") -> "OpenAI"
                cleanUrl.contains("groq.com") -> "Groq"
                cleanUrl.contains("deepseek.com") -> "DeepSeek"
                cleanUrl.contains("mistral.ai") -> "Mistral"
                cleanUrl.contains("together.xyz") || cleanUrl.contains("together.ai") -> "Together"
                cleanUrl.contains("localhost") || cleanUrl.contains("10.0.2.2") || cleanUrl.contains("11434") -> "Ollama"
                isGemini -> "Google Gemini"
                else -> "Custom Provider"
            }

            if (json.has("data")) {
                val dataArr = json.getJSONArray("data")
                for (i in 0 until dataArr.length()) {
                    val obj = dataArr.getJSONObject(i)
                    val id = obj.optString("id", "").ifBlank { obj.optString("name", "") }
                    if (id.isNotBlank()) {
                        val name = formatModelDisplayName(id)
                        val badge = when {
                            id.contains("r1", true) || id.contains("reason", true) -> "Reasoning"
                            id.contains("flash", true) || id.contains("mini", true) || id.contains("turbo", true) -> "Fast"
                            id.contains("pro", true) || id.contains("max", true) || id.contains("plus", true) -> "Pro"
                            else -> "General"
                        }
                        val desc = obj.optString("description", "").ifBlank { "Model ID: $id" }
                        modelsList.add(
                            com.example.models.UserAiModel(
                                id = id,
                                name = name,
                                provider = providerName,
                                endpointUrl = rawEndpoint,
                                apiKey = apiKey,
                                badge = badge,
                                description = desc
                            )
                        )
                    }
                }
            } else if (json.has("models")) {
                val modelsArr = json.getJSONArray("models")
                for (i in 0 until modelsArr.length()) {
                    val obj = modelsArr.getJSONObject(i)
                    val rawName = obj.optString("name", "")
                    val id = rawName.removePrefix("models/")
                    if (id.isNotBlank()) {
                        val displayName = obj.optString("displayName", formatModelDisplayName(id))
                        val desc = obj.optString("description", "Google Gemini Model: $id")
                        modelsList.add(
                            com.example.models.UserAiModel(
                                id = id,
                                name = displayName,
                                provider = "Google Gemini",
                                endpointUrl = rawEndpoint,
                                apiKey = apiKey,
                                badge = if (id.contains("flash")) "Fast" else "Pro",
                                description = desc
                            )
                        )
                    }
                }
            }

            modelsList.sortBy { it.name.lowercase() }
            Result.success(modelsList)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching models: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun formatModelDisplayName(id: String): String {
        val clean = id.substringAfterLast("/")
        return clean.split("-", "_", ".").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun parsePredictionJson(rawText: String, allowedBetTypes: List<String>): PredictionResult {
        return try {
            val clean = rawText.substringAfter("{").substringBeforeLast("}")
            val json = JSONObject("{$clean}")

            val pick = json.optString("recommendedBet", "Both Teams to Score (BTTS)")
            val conf = json.optInt("confidence", 78).coerceIn(50, 99)
            val rationale = json.optString("rationale", "High xG creation and favorable tactical matchup.")
            val rawOdds = json.optDouble("odds", 0.0)
            val oddsStr = if (rawOdds >= 1.10) {
                String.format(Locale.US, "%.2f", rawOdds)
            } else {
                null
            }
            val betType = json.optString("betType", "").ifBlank { null }

            PredictionResult(
                recommendedBet = pick,
                confidence = conf,
                rationale = rationale,
                odds = oddsStr,
                betType = betType
            )
        } catch (e: Exception) {
            val fallbackPick = if (allowedBetTypes.contains("Both Teams to Score (BTTS)")) {
                "Both Teams to Score (BTTS)"
            } else if (allowedBetTypes.contains("Over/Under Goals")) {
                "Over 2.5 Goals"
            } else if (allowedBetTypes.contains("Double Chance (1X, 12, X2)")) {
                "Double Chance (1X)"
            } else {
                "Home Win"
            }
            PredictionResult(
                recommendedBet = fallbackPick,
                confidence = 75,
                rationale = rawText.take(150),
                odds = "1.85",
                betType = "1X2"
            )
        }
    }
}
