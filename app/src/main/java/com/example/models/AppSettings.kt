package com.example.models

import androidx.compose.ui.graphics.Color

enum class ThemeMode(
    val id: String,
    val title: String,
    val description: String,
    val bgColor: Color,
    val cardColor: Color,
    val previewIcon: String
) {
    CYBER_DARK(
        id = "cyber_dark",
        title = "Cyber Slate",
        description = "Deep charcoal with high contrast dark cards",
        bgColor = Color(0xFF0E1015),
        cardColor = Color(0xFF161920),
        previewIcon = "🌑"
    ),
    MIDNIGHT_OLED(
        id = "midnight_oled",
        title = "Midnight OLED",
        description = "True pitch black for maximum battery saving",
        bgColor = Color(0xFF000000),
        cardColor = Color(0xFF0D0E12),
        previewIcon = "🖤"
    ),
    STADIUM_PITCH(
        id = "stadium_pitch",
        title = "Stadium Pitch",
        description = "Deep dark emerald turf night game vibe",
        bgColor = Color(0xFF0A150F),
        cardColor = Color(0xFF102017),
        previewIcon = "⚽"
    ),
    NEON_INDIGO(
        id = "neon_indigo",
        title = "Neon Indigo",
        description = "Modern deep electric navy aesthetic",
        bgColor = Color(0xFF0B101E),
        cardColor = Color(0xFF131B2E),
        previewIcon = "🌌"
    ),
    CRIMSON_ARENA(
        id = "crimson_arena",
        title = "Crimson Arena",
        description = "Intense dark maroon matchday atmosphere",
        bgColor = Color(0xFF160B0E),
        cardColor = Color(0xFF221116),
        previewIcon = "🔥"
    );

    companion object {
        fun fromId(id: String): ThemeMode = entries.find { it.id == id } ?: CYBER_DARK
    }
}

enum class AccentColorMode(
    val id: String,
    val title: String,
    val color: Color
) {
    ORANGE("orange", "Vibrant Amber", Color(0xFFFF6D00)),
    EMERALD("emerald", "Pitch Emerald", Color(0xFF00E676)),
    CYAN("cyan", "Electric Cyan", Color(0xFF00E5FF)),
    YELLOW("yellow", "Hyper Gold", Color(0xFFFFD600)),
    PURPLE("purple", "Neon Violet", Color(0xFFB388FF)),
    PINK("pink", "Hot Coral", Color(0xFFFF4081));

    companion object {
        fun fromId(id: String): AccentColorMode = entries.find { it.id == id } ?: ORANGE
    }
}

enum class OddsFormat(
    val id: String,
    val title: String,
    val example: String
) {
    DECIMAL("decimal", "Decimal", "2.25"),
    FRACTIONAL("fractional", "Fractional", "5/4"),
    AMERICAN("american", "American / Moneyline", "+125");

    companion object {
        fun fromId(id: String): OddsFormat = entries.find { it.id == id } ?: DECIMAL
    }
}

enum class AiReasoningDepth(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconEmoji: String
) {
    FAST_TACTICAL(
        id = "fast",
        title = "Fast Tactical",
        subtitle = "Instant Poisson & Goal Expectancy analysis",
        iconEmoji = "⚡"
    ),
    DEEP_NEURAL(
        id = "deep",
        title = "Deep Neural Matrix",
        subtitle = "Full Form, Head-to-Head & Momentum synthesis",
        iconEmoji = "🧠"
    ),
    QUANTUM_SIMULATION(
        id = "quantum",
        title = "Quantum Monte Carlo",
        subtitle = "10,000 Sim iterations & probability edge evaluation",
        iconEmoji = "🌌"
    );

    companion object {
        fun fromId(id: String): AiReasoningDepth = entries.find { it.id == id } ?: DEEP_NEURAL
    }
}

enum class RiskTolerance(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String
) {
    ULTRA_SAFE(
        id = "safe",
        title = "Ultra Safe",
        subtitle = "Prioritize >80% Win Probabilities & Safe Lines (Double Chance / Under/Over)",
        badge = "🛡️ Safe"
    ),
    BALANCED_VALUE(
        id = "balanced",
        title = "Balanced Value",
        subtitle = "Optimal expected value ratio for Singles and Doubles",
        badge = "⚖️ Value"
    ),
    AGGRESSIVE_ACCUMULATOR(
        id = "aggressive",
        title = "Aggressive Multi-Leg",
        subtitle = "High combined odds value picks for Big Accumulators",
        badge = "🚀 High Odds"
    );

    companion object {
        fun fromId(id: String): RiskTolerance = entries.find { it.id == id } ?: BALANCED_VALUE
    }
}

data class UserAiModel(
    val id: String,
    val name: String,
    val provider: String = "Custom",
    val endpointUrl: String = "https://api.openai.com/v1/",
    val apiKey: String = "",
    val badge: String = "Added",
    val description: String = "Added AI prediction model",
    val addedAt: Long = System.currentTimeMillis()
)

data class AiModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val badge: String,
    val description: String
)

val SupportedAiModels = listOf(
    AiModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", "Google DeepMind", "Recommended", "Lightning fast, high accuracy match predictor"),
    AiModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro", "Google DeepMind", "Deep Reasoning", "Deep tactical reasoning & injury impact analysis"),
    AiModelInfo("deepseek-r1", "DeepSeek R1", "DeepSeek", "Chain of Thought", "Mathematical breakdown & reasoning verification"),
    AiModelInfo("gpt-4o-mini", "GPT-4o Mini", "OpenAI", "Fast", "Compact sports analytics & expected value model"),
    AiModelInfo("claude-3-5-sonnet", "Claude 3.5 Sonnet", "Anthropic", "Context Master", "Holistic form analysis & tactical matchup dynamics"),
    AiModelInfo("custom", "Custom Model", "OpenAI / Custom", "Configurable", "User-specified model identifier and endpoint")
)

data class AppCustomSettings(
    val themeMode: ThemeMode = ThemeMode.CYBER_DARK,
    val accentColorMode: AccentColorMode = AccentColorMode.ORANGE,
    val oddsFormat: OddsFormat = OddsFormat.DECIMAL,
    val autoRefreshSec: Int = 30, // 0 = manual, 15, 30, 60
    val showFinishedMatches: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val dataSaver: Boolean = false,
    val compactCardMode: Boolean = false,
    val aiReasoningDepth: AiReasoningDepth = AiReasoningDepth.DEEP_NEURAL,
    val riskTolerance: RiskTolerance = RiskTolerance.BALANCED_VALUE,
    val minConfidenceThreshold: Int = 65,
    val activeAiModelId: String = "gemini-2.5-flash",
    val customAiModelName: String = "",
    val customAiEndpointUrl: String = "",
    val customTacticalPrompt: String = ""
)
