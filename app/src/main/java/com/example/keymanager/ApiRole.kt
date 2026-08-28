package com.example.keymanager

enum class ApiRole(
    val code: String,
    val displayName: String,
    val subtitle: String,
    val iconEmoji: String,
    val headerName: String,
    val portalUrl: String = "",
    val portalTitle: String = "",
    val isFreeTierAvailable: Boolean = true
) {
    THE_ODDS_API(
        code = "THE_ODDS_API",
        displayName = "The Odds API",
        subtitle = "Live bookmaker odds, 1X2, Over/Under & accumulator spreads",
        iconEmoji = "📊",
        headerName = "apiKey",
        portalUrl = "https://the-odds-api.com/",
        portalTitle = "the-odds-api.com (500 free requests/mo)",
        isFreeTierAvailable = true
    ),
    FOOTBALL_DATA_ORG(
        code = "FOOTBALL_DATA_ORG",
        displayName = "Football-Data.org",
        subtitle = "12 Major Leagues: Premier League, La Liga, Serie A, UCL, Bundesliga",
        iconEmoji = "🏆",
        headerName = "X-Auth-Token",
        portalUrl = "https://www.football-data.org/client/register",
        portalTitle = "football-data.org (Free Tier)",
        isFreeTierAvailable = true
    ),
    API_FOOTBALL(
        code = "API_FOOTBALL",
        displayName = "API-Football (API-Sports)",
        subtitle = "Live fixtures, odds, team statistics, and H2H data",
        iconEmoji = "⚽",
        headerName = "x-apisports-key",
        portalUrl = "https://dashboard.api-football.com/register",
        portalTitle = "api-sports.io (100 req/day free)",
        isFreeTierAvailable = true
    ),
    SPORTMONKS(
        code = "SPORTMONKS",
        displayName = "Sportmonks Football API",
        subtitle = "Deep analytics, xG metrics, team momentum & H2H stats",
        iconEmoji = "📈",
        headerName = "api_token",
        portalUrl = "https://www.sportmonks.com/football-api/",
        portalTitle = "sportmonks.com (Free Trial)",
        isFreeTierAvailable = true
    ),
    THE_SPORTS_DB(
        code = "THE_SPORTS_DB",
        displayName = "TheSportsDB",
        subtitle = "Open soccer database for fixtures, badges & team logos",
        iconEmoji = "🌐",
        headerName = "X-API-KEY",
        portalUrl = "https://www.thesportsdb.com/api.php",
        portalTitle = "thesportsdb.com (Free API Key)",
        isFreeTierAvailable = true
    ),
    OPENAI_COMPATIBLE(
        code = "OPENAI_COMPATIBLE",
        displayName = "OpenAI Compatible AI",
        subtitle = "Custom API endpoint (OpenRouter, OpenAI, DeepSeek, Groq, Ollama)",
        iconEmoji = "🤖",
        headerName = "Authorization",
        portalUrl = "https://openrouter.ai/keys",
        portalTitle = "openrouter.ai / openai.com",
        isFreeTierAvailable = true
    ),
    GEMINI(
        code = "GEMINI",
        displayName = "Gemini AI",
        subtitle = "Deep tactical analysis, reasoning & match predictions",
        iconEmoji = "✨",
        headerName = "x-goog-api-key",
        portalUrl = "https://aistudio.google.com/app/apikey",
        portalTitle = "Google AI Studio (Free tier)",
        isFreeTierAvailable = true
    ),
    FIRECRAWL(
        code = "FIRECRAWL",
        displayName = "Firecrawl Search",
        subtitle = "Real-time web scraping for latest news & injuries",
        iconEmoji = "🔥",
        headerName = "Authorization",
        portalUrl = "https://www.firecrawl.dev/app/api-keys",
        portalTitle = "firecrawl.dev (Free credits)",
        isFreeTierAvailable = true
    );

    companion object {
        fun fromCode(code: String): ApiRole {
            val upper = code.trim().uppercase()
            return when {
                upper.contains("FOOTBALL_DATA") || upper.contains("FOOTBALL-DATA") || upper.contains("FOOTBALLDATA") -> FOOTBALL_DATA_ORG
                upper.contains("THE_ODDS") || upper.contains("ODDS_API") || upper.contains("THEODDS") || upper == "ODDS" -> THE_ODDS_API
                upper.contains("SPORTMONK") || upper.contains("SPORT_MONK") -> SPORTMONKS
                upper.contains("SPORTSDB") || upper.contains("SPORTS_DB") || upper.contains("THESPORTSDB") -> THE_SPORTS_DB
                upper == "API_FOOTBALL" || upper.contains("FOOTBALL") || upper.contains("SPORTS") -> API_FOOTBALL
                upper == "FIRECRAWL" || upper.contains("CRAWL") || upper.contains("SCRAPER") -> FIRECRAWL
                upper == "GEMINI" || upper.contains("GOOGLE") || upper.contains("VERTEX") -> GEMINI
                upper == "OPENAI_COMPATIBLE" || upper.contains("OPENAI") || upper.contains("OPENROUTER") ||
                        upper.contains("DEEPSEEK") || upper.contains("GROQ") || upper.contains("CLAUDE") ||
                        upper.contains("ANTHROPIC") || upper.contains("OLLAMA") || upper.contains("LLM") ||
                        upper.contains("AGNES") || upper.contains("AI") -> OPENAI_COMPATIBLE
                else -> entries.find { it.code.equals(upper, ignoreCase = true) } ?: API_FOOTBALL
            }
        }
    }
}


