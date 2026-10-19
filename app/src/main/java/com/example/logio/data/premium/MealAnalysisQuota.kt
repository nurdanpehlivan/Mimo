package com.example.logio.data.premium

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ücretsiz kullanımda günlük yapay zekâ öğün analizi hakkı. Yalnızca başarılı analizler sayılır;
 * sayaç her gün sıfırlanır. Premium'da sınır yoktur (sınırı TimelineViewModel uygular).
 */
@Singleton
class MealAnalysisQuota @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("mimo_premium", Context.MODE_PRIVATE)

    fun remainingToday(today: LocalDate = LocalDate.now()): Int =
        (FREE_DAILY_LIMIT - usedOn(today)).coerceAtLeast(0)

    fun recordUse(today: LocalDate = LocalDate.now()) {
        prefs.edit()
            .putString(KEY_DAY, today.toString())
            .putInt(KEY_COUNT, usedOn(today) + 1)
            .apply()
    }

    private fun usedOn(day: LocalDate): Int =
        if (prefs.getString(KEY_DAY, null) == day.toString()) prefs.getInt(KEY_COUNT, 0) else 0

    companion object {
        const val FREE_DAILY_LIMIT = 3
        private const val KEY_DAY = "meal_quota_day"
        private const val KEY_COUNT = "meal_quota_count"
    }
}
