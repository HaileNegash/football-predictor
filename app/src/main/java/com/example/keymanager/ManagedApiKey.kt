package com.example.keymanager

import com.squareup.moshi.JsonClass

enum class KeyStatus {
    ACTIVE,          // Healthy and ready
    RATE_LIMITED,    // Temporary cooldown (e.g., 429)
    EXHAUSTED,       // Quota depleted for billing cycle
    ERROR            // Invalid or unauthorized (401/403)
}

@JsonClass(generateAdapter = true)
data class ManagedApiKey(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String = ApiRole.API_FOOTBALL.code,
    val key: String = "",
    val label: String = "Key",
    val status: String = KeyStatus.ACTIVE.name,
    val usageCount: Int = 0,
    val errorCount: Int = 0,
    val lastUsedTimestamp: Long = 0L,
    val rateLimitedUntil: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val endpointUrl: String = "https://api.openai.com/v1/",
    val modelName: String = "gpt-4o-mini"
) {
    val apiRole: ApiRole get() = ApiRole.fromCode(role)
    val keyStatus: KeyStatus get() = try { KeyStatus.valueOf(status) } catch(e: Exception) { KeyStatus.ACTIVE }

    val isCoolingDown: Boolean
        get() = rateLimitedUntil > System.currentTimeMillis()

    val maskedKey: String
        get() = when {
            key.length > 10 -> "${key.take(4)}••••••••${key.takeLast(4)}"
            key.length > 5 -> "${key.take(2)}••••${key.takeLast(2)}"
            key.isNotBlank() -> "••••••••"
            else -> "No key"
        }
}
