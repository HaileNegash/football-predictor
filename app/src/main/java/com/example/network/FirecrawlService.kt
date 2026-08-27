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
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

object FirecrawlService {
    private const val TAG = "FirecrawlService"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Searches for match tactical news, squad injury updates, and recent team form using Firecrawl API.
     * Includes automatic timeout protection and clean graceful fallback.
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
            val endpoint = resolveEndpoint(managedKey.endpointUrl)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Refusing to send Firecrawl key to non-HTTPS endpoint")
                )

            // Concise query to optimize response time and avoid search crawler timeouts
            val query = "$homeTeam vs $awayTeam lineup injuries form"
            val requestJson = JSONObject().apply {
                put("query", query)
                put("limit", 3)
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Authorization", managedKey.authHeaderValue)
                .addHeader("Content-Type", "application/json")
                .build()

            // use{} so the connection is released even if body reading throws.
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    Log.w(TAG, "Firecrawl returned HTTP ${response.code}")
                    return@withContext Result.failure(
                        ApiHttpException(response.code, responseBody)
                    )
                }

                val jsonObject = JSONObject(responseBody)
                val dataArray = jsonObject.optJSONArray("data")
                if (dataArray == null || dataArray.length() == 0) {
                    // Previously this returned a *fabricated* success string
                    // ("Latest squad intel and injury reports retrieved
                    // successfully."), which was then injected into the model
                    // prompt as if it were real scraped news. Report the miss.
                    return@withContext Result.failure(
                        IOException("Firecrawl returned no results for $homeTeam vs $awayTeam")
                    )
                }

                // Combine the top hits rather than only the first — a single
                // result is often a fixture listing with no actual team news.
                val summary = buildString {
                    for (i in 0 until minOf(dataArray.length(), 3)) {
                        val item = dataArray.optJSONObject(i) ?: continue
                        val title = item.optString("title", "").trim()
                        val description = item.optString("description", "").trim()
                        if (title.isBlank() && description.isBlank()) continue
                        if (isNotEmpty()) append(" | ")
                        append(title)
                        if (description.isNotBlank()) append(": $description")
                    }
                }.take(500)

                if (summary.isBlank()) {
                    return@withContext Result.failure(
                        IOException("Firecrawl results contained no usable text")
                    )
                }

                Result.success(summary)
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Firecrawl search timed out")
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "Firecrawl search exception: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Validates and normalises the endpoint. Refuses plaintext HTTP so a
     * misconfigured Firestore document cannot leak the key over the wire.
     */
    private fun resolveEndpoint(rawEndpoint: String): String? {
        val configured = rawEndpoint.trim()
        // Only honour a configured endpoint if it looks like a Firecrawl host;
        // the old `!contains("openai")` check let any mis-roled URL through.
        val usable = configured.isNotBlank() &&
                configured.startsWith("https://", ignoreCase = true) &&
                configured.contains("firecrawl", ignoreCase = true)

        if (!usable) {
            // Fall back to the canonical host rather than guessing.
            return if (configured.isBlank() || !configured.startsWith("http", ignoreCase = true)) {
                "https://api.firecrawl.dev/v1/search"
            } else if (!configured.startsWith("https://", ignoreCase = true)) {
                null // http:// explicitly configured — refuse
            } else {
                "https://api.firecrawl.dev/v1/search"
            }
        }

        val base = configured.trimEnd('/')
        return if (base.endsWith("/v1/search") || base.endsWith("/v1/scrape") ||
            base.endsWith("/v2/search") || base.endsWith("/v2/scrape")
        ) base else "$base/v1/search"
    }
}
