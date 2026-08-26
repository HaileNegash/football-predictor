package com.example.models

enum class UserTier(val title: String, val badgeColorHex: Long) {
    FREE("Free Tier", 0xFF90A4AE),
    PRO_VIP("PRO VIP", 0xFFFFD600)
}

data class UserProfile(
    val userId: String = "guest_${System.currentTimeMillis()}",
    val email: String = "guest@footballpredictor.app",
    val displayName: String = "Football Fan",
    val photoUrl: String? = null,
    val tier: UserTier = UserTier.FREE,
    val dailyPredictionsUsed: Int = 0,
    val dailyLimit: Int = 5,
    val isAutoSignedIn: Boolean = false,
    val rememberPassword: Boolean = true,
    val isBanned: Boolean = false,
    val lastActiveDate: String = "",
    val joinedAt: Long = System.currentTimeMillis()
) {
    val remainingPredictions: Int
        get() = if (tier == UserTier.PRO_VIP) 999 else (dailyLimit - dailyPredictionsUsed).coerceAtLeast(0)

    val isLimitReached: Boolean
        get() = tier != UserTier.PRO_VIP && remainingPredictions <= 0
}
