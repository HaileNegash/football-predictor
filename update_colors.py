import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

old_colors = """val AppDarkBg = Color(0xFF030914)
val HeaderBg = Color(0xFF001535)
val DividerColor = Color(0xFF0C1B33)
val TextMain = Color(0xFFF0F2F5)
val TextSub = Color(0xFF8B9CB6)
val AccentPrimary = Color(0xFF1E63D6)
val PredictBg = Color(0xFF0E223D)"""

new_colors = """// Pro UI Dark Theme - Premium Betting/Sports Aesthetic
val AppDarkBg = Color(0xFF121214)     // Deep OLED-friendly Charcoal
val HeaderBg = Color(0xFF18181B)      // Slightly elevated Zinc for Header
val DividerColor = Color(0xFF27272A)  // Crisp subtle borders
val TextMain = Color(0xFFFAFAFA)      // Pure sharp white for readability
val TextSub = Color(0xFFA1A1AA)       // Muted Zinc for secondary info
val AccentPrimary = Color(0xFF10B981) // Emerald Green (Pro Sports/Betting Accent)
val PredictBg = Color(0xFF132E25)     // Deep emerald tint for prediction cards"""

content = content.replace(old_colors, new_colors)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
