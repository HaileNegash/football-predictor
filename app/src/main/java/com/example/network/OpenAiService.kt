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
import java.util.concurrent.TimeUnit

object OpenAiService {
    private const val TAG = "OpenAiService"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Executes a chat completion request to any OpenAI-compatible API endpoint.
     */
    suspend fun generatePrediction(
        homeTeam: String,
        awayTeam: String,
        league: String,
        managedKey: ManagedApiKey
    ): Result<PredictionResult> = withContext(Dispatchers.IO) {
        try {
            val rawEndpoint = managedKey.endpointUrl.trim().ifBlank { "https://api.openai.com/v1/" }
            val baseUrl = if (rawEndpoint.endsWith("/")) rawEndpoint else "$rawEndpoint/"
            val endpoint = if (baseUrl.endsWith("chat/completions")) baseUrl else "${baseUrl}chat/completions"
            val model = managedKey.modelName.trim().ifBlank { "gpt-4o-mini" }

            val prompt = """
                Analyze the upcoming football match:
                - Home Team: $homeTeam
                - Away Team: $awayTeam
                - League: $league
                
                Provide a sports betting prediction in strictly valid JSON format with keys:
                - "recommendedBet": (e.g. "Home Win", "Both Teams to Score (BTTS)", "Over 2.5 Goals", "Double Chance 1X")
                - "confidence": an integer between 50 and 95
                - "rationale": 1-2 sentence tactical and statistical reasoning
            """.trimIndent()

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are an expert football tactical analyst and sports statistician. Respond strictly with a JSON object.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.3)
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
            
            // Extract JSON from content
            val parsedResult = parsePredictionJson(content)
            Result.success(parsedResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing OpenAI prediction", e)
            Result.failure(e)
        }
    }

    private fun parsePredictionJson(rawText: String): PredictionResult {
        return try {
            val clean = rawText.substringAfter("{").substringBeforeLast("}")
            val json = JSONObject("{$clean}")
            PredictionResult(
                recommendedBet = json.optString("recommendedBet", "Home Win / Over 1.5 Goals"),
                confidence = json.optInt("confidence", 78).coerceIn(50, 99),
                rationale = json.optString("rationale", "Statistical edge based on recent home offensive metrics.")
            )
        } catch (e: Exception) {
            PredictionResult(
                recommendedBet = "Home Win",
                confidence = 75,
                rationale = rawText.take(150)
            )
        }
    }
}
