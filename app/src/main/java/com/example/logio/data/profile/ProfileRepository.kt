package com.example.logio.data.profile

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class UserProfile(
    val name: String = DEFAULT_NAME,
    val waterGoalMl: Int = DEFAULT_WATER_GOAL_ML,
    val stepGoal: Int = DEFAULT_STEP_GOAL,
    val isAppLockEnabled: Boolean = false
) {
    companion object {
        const val DEFAULT_NAME = "Nurdan"
        const val DEFAULT_WATER_GOAL_ML = 2_000
        const val DEFAULT_STEP_GOAL = 10_000
    }
}

/** Kullanıcı adı ve günlük hedefler; SharedPreferences'ta saklanır, tüm ekranlar aynı akışı dinler. */
@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("logio_profile", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(
        UserProfile(
            name = prefs.getString(KEY_NAME, null) ?: UserProfile.DEFAULT_NAME,
            waterGoalMl = prefs.getInt(KEY_WATER_GOAL, UserProfile.DEFAULT_WATER_GOAL_ML),
            stepGoal = prefs.getInt(KEY_STEP_GOAL, UserProfile.DEFAULT_STEP_GOAL),
            isAppLockEnabled = prefs.getBoolean(KEY_APP_LOCK, false)
        )
    )
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    fun update(profile: UserProfile) {
        val cleaned = profile.copy(
            name = profile.name.trim().ifBlank { UserProfile.DEFAULT_NAME },
            waterGoalMl = profile.waterGoalMl.coerceIn(500, 6_000),
            stepGoal = profile.stepGoal.coerceIn(1_000, 50_000)
        )
        prefs.edit()
            .putString(KEY_NAME, cleaned.name)
            .putInt(KEY_WATER_GOAL, cleaned.waterGoalMl)
            .putInt(KEY_STEP_GOAL, cleaned.stepGoal)
            .putBoolean(KEY_APP_LOCK, cleaned.isAppLockEnabled)
            .apply()
        _profile.value = cleaned
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_WATER_GOAL = "water_goal_ml"
        const val KEY_STEP_GOAL = "step_goal"
        const val KEY_APP_LOCK = "app_lock_enabled"
    }
}
