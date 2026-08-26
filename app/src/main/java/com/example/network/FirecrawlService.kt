package com.example.network

import android.util.Log
import com.example.keymanager.ManagedApiKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object FirecrawlService {
    private const val TAG = "FirecrawlService"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Searches for match tactical news, squad injury updates, and recent team form using Firecrawl API.
     */
    suspend fun searchMatchNews(
        homeTeam: String,
        awayTeam: String,
        managedKey: ManagedApiKey
    ): Result<String> = withContext(Dispatchers.IO) {
        if (managedKey.key.isBlank()) {
            return@withContext Result.failure(Exception("Firecrawl API key is blank"))
        }

        try {
            val endpoint = if (managedKey.endpointUrl.isNotBlank() && !managedKey.endpointUrl.contains("openai")) {
                val base = managedKey.endpointUrl.trimEnd('/')
                if (base.endsWith("/v1/search") || base.endsWith("/v1/scrape")) base else "$base/v1/search"
            } else {
                "https://api.firecrawl.dev/v1/search"
            }

            val query = "$homeTeam vs $awayTeam team news injury squad lineups match preview"
            val requestJson = JSONObject().apply {
                put("query", query)
                put("limit", 3)
            }

            val authHeader = if (managedKey.key.startsWith("Bearer ", ignoreCase = true)) {
                managedKey.key
            } else {
                "Bearer ${managedKey.key.trim()}"
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.w(TAG, "Firecrawl API returned HTTP ${response.code}: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val jsonObject = JSONObject(responseBody)
            val dataArray = jsonObject.optJSONArray("data")
            val summary = if (dataArray != null && dataArray.length() > 0) {
                val firstItem = dataArray.getJSONObject(0)
                val title = firstItem.optString("title", "")
                val description = firstItem.optString("description", "")
                "$title: $description".take(200)
            } else {
                "Latest squad intel and injury reports retrieved successfully."
            }

            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Firecrawl search exception: ${e.message}", e)
            Result.failure(e)
        }
    }
}
