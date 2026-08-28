package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import com.example.models.UserProfile
import com.example.models.UserTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("football_predictor_user_prefs", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentUser = MutableStateFlow(loadStoredUser())
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage: StateFlow<String?> = _authMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        checkAndResetDailyQuota()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun checkAndResetDailyQuota() {
        val today = getTodayDateString()
        val user = _currentUser.value
        if (user.lastActiveDate != today) {
            val updated = user.copy(
                dailyPredictionsUsed = 0,
                lastActiveDate = today
            )
            saveUserLocal(updated)
            _currentUser.value = updated
        }
    }

    private fun loadStoredUser(): UserProfile {
        val today = getTodayDateString()
        val userId = prefs.getString("user_id", null)
        val email = prefs.getString("user_email", "guest@footballpredictor.app") ?: "guest@footballpredictor.app"
        val name = prefs.getString("user_name", "Football Fan") ?: "Football Fan"
        val photoUrl = prefs.getString("user_photo", null)
        val tierStr = prefs.getString("user_tier", UserTier.FREE.name) ?: UserTier.FREE.name
        val tier = try { UserTier.valueOf(tierStr) } catch (e: Exception) { UserTier.FREE }
        val dailyLimit = prefs.getInt("user_daily_limit", if (tier == UserTier.PRO_VIP) 999 else 5)
        val used = prefs.getInt("user_used_today", 0)
        val lastDate = prefs.getString("user_last_date", today) ?: today
        val autoSign = prefs.getBoolean("user_auto_sign_in", true)
        val remember = prefs.getBoolean("user_remember_pw", true)

        val actualUsed = if (lastDate == today) used else 0

        return UserProfile(
            userId = userId ?: "guest_${System.currentTimeMillis() % 100000}",
            email = email,
            displayName = name,
            photoUrl = photoUrl,
            tier = tier,
            dailyPredictionsUsed = actualUsed,
            dailyLimit = dailyLimit,
            isAutoSignedIn = autoSign,
            rememberPassword = remember,
            lastActiveDate = today,
            joinedAt = prefs.getLong("user_joined_at", System.currentTimeMillis())
        )
    }

    private fun saveUserLocal(user: UserProfile) {
        prefs.edit()
            .putString("user_id", user.userId)
            .putString("user_email", user.email)
            .putString("user_name", user.displayName)
            .putString("user_photo", user.photoUrl)
            .putString("user_tier", user.tier.name)
            .putInt("user_daily_limit", user.dailyLimit)
            .putInt("user_used_today", user.dailyPredictionsUsed)
            .putString("user_last_date", user.lastActiveDate)
            .putBoolean("user_auto_sign_in", user.isAutoSignedIn)
            .putBoolean("user_remember_pw", user.rememberPassword)
            .putLong("user_joined_at", user.joinedAt)
            .apply()
    }

    fun updateDisplayName(newName: String) {
        val clean = newName.trim().ifBlank { "Football Fan" }
        val updated = _currentUser.value.copy(displayName = clean)
        saveUserLocal(updated)
        _currentUser.value = updated
    }

    fun toggleUserTier(toVip: Boolean? = null) {
        val cur = _currentUser.value
        val newTier = if (toVip != null) {
            if (toVip) UserTier.PRO_VIP else UserTier.FREE
        } else {
            if (cur.tier == UserTier.PRO_VIP) UserTier.FREE else UserTier.PRO_VIP
        }
        val limit = if (newTier == UserTier.PRO_VIP) 999 else 5
        val updated = cur.copy(tier = newTier, dailyLimit = limit)
        saveUserLocal(updated)
        _currentUser.value = updated
    }

    fun resetDailyPredictions() {
        val updated = _currentUser.value.copy(dailyPredictionsUsed = 0)
        saveUserLocal(updated)
        _currentUser.value = updated
    }

    fun signInWithGoogleAuto(email: String, displayName: String, photoUrl: String? = null, onComplete: (Boolean, String) -> Unit) {
        _isLoading.value = true
        scope.launch {
            try {
                val cleanEmail = email.trim().lowercase()
                val userId = "local_${cleanEmail.replace(".", "_").replace("@", "_")}"
                val today = getTodayDateString()

                val user = UserProfile(
                    userId = userId,
                    email = cleanEmail,
                    displayName = displayName.ifBlank { cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() } },
                    photoUrl = photoUrl,
                    tier = UserTier.PRO_VIP,
                    dailyLimit = 999,
                    dailyPredictionsUsed = 0,
                    isAutoSignedIn = true,
                    rememberPassword = true,
                    lastActiveDate = today,
                    joinedAt = System.currentTimeMillis()
                )

                saveUserLocal(user)
                _currentUser.value = user

                _authMessage.value = "Welcome back, ${user.displayName}!"
                onComplete(true, "Signed in as ${user.email} (VIP Unlimited)")
            } catch (e: Exception) {
                onComplete(false, "Sign-In failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signInWithEmailPassword(email: String, password: String, rememberMe: Boolean, onComplete: (Boolean, String) -> Unit) {
        if (email.isBlank() || !email.contains("@")) {
            onComplete(false, "Please enter a valid email address.")
            return
        }
        if (password.length < 6) {
            onComplete(false, "Password must be at least 6 characters.")
            return
        }

        _isLoading.value = true
        scope.launch {
            try {
                val cleanEmail = email.trim().lowercase()
                val userId = "usr_${cleanEmail.replace(".", "_").replace("@", "_")}"
                val today = getTodayDateString()

                if (rememberMe) {
                    prefs.edit()
                        .putString("saved_auth_email", cleanEmail)
                        .putString("saved_auth_password", password)
                        .apply()
                }

                val existing = _currentUser.value
                val user = UserProfile(
                    userId = userId,
                    email = cleanEmail,
                    displayName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                    tier = existing.tier,
                    dailyLimit = existing.dailyLimit,
                    dailyPredictionsUsed = 0,
                    isAutoSignedIn = true,
                    rememberPassword = rememberMe,
                    lastActiveDate = today,
                    joinedAt = existing.joinedAt
                )

                saveUserLocal(user)
                _currentUser.value = user

                onComplete(true, "Successfully signed in as ${user.email}")
            } catch (e: Exception) {
                onComplete(false, "Sign In error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getRememberedCredentials(): Pair<String, String> {
        val email = prefs.getString("saved_auth_email", "") ?: ""
        val password = prefs.getString("saved_auth_password", "") ?: ""
        return Pair(email, password)
    }

    fun signOut(onComplete: () -> Unit = {}) {
        val guest = UserProfile(
            userId = "guest_${System.currentTimeMillis() % 100000}",
            email = "guest@footballpredictor.app",
            displayName = "Guest User",
            photoUrl = null,
            tier = UserTier.FREE,
            dailyPredictionsUsed = 0,
            dailyLimit = 5,
            isAutoSignedIn = false,
            rememberPassword = false,
            lastActiveDate = getTodayDateString()
        )
        saveUserLocal(guest)
        _currentUser.value = guest
        onComplete()
    }

    fun toggleAutoSignIn(enabled: Boolean) {
        val updated = _currentUser.value.copy(isAutoSignedIn = enabled)
        saveUserLocal(updated)
        _currentUser.value = updated
    }

    fun toggleRememberPassword(enabled: Boolean) {
        val updated = _currentUser.value.copy(rememberPassword = enabled)
        saveUserLocal(updated)
        _currentUser.value = updated
        if (!enabled) {
            prefs.edit().remove("saved_auth_password").apply()
        }
    }

    fun consumePredictionQuota(): Boolean {
        val user = _currentUser.value
        val updated = user.copy(dailyPredictionsUsed = user.dailyPredictionsUsed + 1)
        saveUserLocal(updated)
        _currentUser.value = updated
        return true // Unlimited predictions enabled
    }
}
