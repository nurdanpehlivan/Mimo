/*
 * ProfileStats: Profil sekmesindeki özet bilgilerin hesabı.
 *
 * Kayıtlardan seri (streak), aktif gün sayısı, en çok kullanılan türler ve rozetler çıkarılır.
 * Compose'a bağımlı değildir; ProfileViewModel arka planda çağırır, birim testle denenebilir.
 */
package com.example.logio.ui.profile

import androidx.annotation.StringRes
import com.example.logio.R
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val TOP_TYPE_COUNT = 5

internal data class Badge(
    val emoji: String,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val current: Int,
    val target: Int
) {
    val unlocked get() = current >= target
}

internal data class ProfileSummary(
    val totalEntries: Int,
    val activeDays: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val firstDay: LocalDate?,
    val photoCount: Int,
    val totalWaterMl: Int,
    val topTypes: List<Pair<EntryType, Int>>,
    val badges: List<Badge>
)

internal fun summarizeProfile(
    entries: List<JournalEntryEntity>,
    waterGoalMl: Int,
    zone: ZoneId,
    today: LocalDate
): ProfileSummary {
    fun dayOf(entry: JournalEntryEntity) = Instant.ofEpochMilli(entry.createdAt).atZone(zone).toLocalDate()

    val days = entries.mapTo(sortedSetOf()) { dayOf(it) }

    // Güncel seri: bugünden (bugün kayıt yoksa dünden) geriye kesintisiz günler
    var cursor = if (today in days) today else today.minusDays(1)
    var currentStreak = 0
    while (cursor in days) {
        currentStreak++
        cursor = cursor.minusDays(1)
    }

    var longestStreak = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in days) {
        run = if (previous != null && ChronoUnit.DAYS.between(previous, day) == 1L) run + 1 else 1
        longestStreak = maxOf(longestStreak, run)
        previous = day
    }

    val typeCounts = entries.groupingBy { it.entryType }.eachCount()
    val photoCount = entries.filter { it.entryType == EntryType.PHOTO }
        .sumOf { entry -> entry.imagePaths?.split(",")?.count { it.isNotBlank() }?.coerceAtLeast(1) ?: 1 }
    val waterEntries = entries.filter { it.entryType == EntryType.WATER }
    val waterGoalDays = waterEntries
        .groupBy { dayOf(it) }
        .count { (_, dayEntries) -> dayEntries.sumOf { it.waterAmountMl ?: 0 } >= waterGoalMl }

    val badges = listOf(
        Badge("🌱", R.string.badge_first_step, R.string.badge_first_step_desc, entries.size, 1),
        Badge("🔥", R.string.badge_habit, R.string.badge_habit_desc, longestStreak, 7),
        Badge("🏆", R.string.badge_determined, R.string.badge_determined_desc, longestStreak, 30),
        Badge("💯", R.string.badge_hundred, R.string.badge_hundred_desc, entries.size, 100),
        Badge("📸", R.string.badge_photo_hunter, R.string.badge_photo_hunter_desc, photoCount, 20),
        Badge("💧", R.string.badge_water, R.string.badge_water_desc, waterGoalDays, 7),
        Badge("🧭", R.string.badge_explorer, R.string.badge_explorer_desc, typeCounts[EntryType.LOCATION] ?: 0, 10),
        Badge("🎨", R.string.badge_versatile, R.string.badge_versatile_desc, typeCounts.size, 10)
    )

    return ProfileSummary(
        totalEntries = entries.size,
        activeDays = days.size,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        firstDay = days.firstOrNull(),
        photoCount = photoCount,
        totalWaterMl = waterEntries.sumOf { it.waterAmountMl ?: 0 },
        topTypes = typeCounts.entries.sortedByDescending { it.value }.take(TOP_TYPE_COUNT).map { it.key to it.value },
        badges = badges
    )
}
