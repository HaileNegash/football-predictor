package com.example.keymanager

enum class ApiRole(
    val code: String,
    val displayName: String,
    val subtitle: String,
    val iconEmoji: String,
    val headerName: String
) {
    API_FOOTBALL(
        code = "API_FOOTBALL",
        displayName = "API-Football (API-Sports)",
        subtitle = "Live fixtures, odds, team statistics, and H2H data",
        iconEmoji = "⚽",
        headerName = "x-apisports-key"
    ),
    OPENAI_COMPATIBLE(
        code = "OPENAI_COMPATIBLE",
        displayName = "OpenAI Compatible AI",
        subtitle = "Custom API endpoint (OpenAI, DeepSeek, OpenRouter, Groq, Ollama)",
        iconEmoji = "🤖",
        headerName = "Authorization"
    ),
    GEMINI(
        code = "GEMINI",
        displayName = "Gemini AI",
        subtitle = "Deep tactical analysis, reasoning & match predictions",
        iconEmoji = "✨",
        headerName = "x-goog-api-key"
    ),
    FIRECRAWL(
        code = "FIRECRAWL",
        displayName = "Firecrawl Search",
        subtitle = "Real-time web scraping for latest news & injuries",
        iconEmoji = "🔥",
        headerName = "Authorization"
    );

    companion object {
        fun fromCode(code: String): ApiRole {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: API_FOOTBALL
        }
    }
}

