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

data class AppCustomSettings(
    val themeMode: ThemeMode = ThemeMode.CYBER_DARK,
    val accentColorMode: AccentColorMode = AccentColorMode.ORANGE,
    val oddsFormat: OddsFormat = OddsFormat.DECIMAL,
    val autoRefreshSec: Int = 30, // 0 = manual, 15, 30, 60
    val showFinishedMatches: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val dataSaver: Boolean = false
)
