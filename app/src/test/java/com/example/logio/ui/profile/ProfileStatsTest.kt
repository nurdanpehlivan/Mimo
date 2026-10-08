/*
 * ProfileStatsTest: Profil özetinin (ProfileStats.kt) birim testleri.
 *
 * Seri (streak) hesabı ve rozetlerin açılma kuralları burada korunur.
 */
package com.example.logio.ui.profile

import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProfileStatsTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 8)

    private fun entryOn(day: LocalDate, type: EntryType = EntryType.TEXT) = JournalEntryEntity(
        title = "", content = "", entryType = type,
        createdAt = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    )

    private fun summarize(vararg entries: JournalEntryEntity) =
        summarizeProfile(entries.toList(), waterGoalMl = 2000, zone = zone, today = today)

    @Test
    fun `guncel seri bugunden geriye kesintisiz gunleri sayar`() {
        val summary = summarize(entryOn(today), entryOn(today.minusDays(1)), entryOn(today.minusDays(2)), entryOn(today.minusDays(4)))
        assertEquals(3, summary.currentStreak)
    }

    @Test
    fun `bugun kayit yoksa seri dunden baslar, bozulmaz`() {
        val summary = summarize(entryOn(today.minusDays(1)), entryOn(today.minusDays(2)))
        assertEquals(2, summary.currentStreak)
    }

    @Test
    fun `en uzun seri gecmisteki en uzun kesintisiz donemdir`() {
        val summary = summarize(
            entryOn(today.minusDays(10)), entryOn(today.minusDays(9)), entryOn(today.minusDays(8)), entryOn(today.minusDays(7)),
            entryOn(today)
        )
        assertEquals(4, summary.longestStreak)
        assertEquals(1, summary.currentStreak)
        assertEquals(5, summary.activeDays)
    }

    @Test
    fun `ayni gundeki birden cok kayit tek aktif gun sayilir`() {
        val summary = summarize(entryOn(today), entryOn(today), entryOn(today))
        assertEquals(3, summary.totalEntries)
        assertEquals(1, summary.activeDays)
    }

    @Test
    fun `ilk adim rozeti ilk kayitla acilir, aliskanlik rozeti 7 gunluk seri ister`() {
        val summary = summarize(entryOn(today))
        val firstStep = summary.badges.first { it.emoji == "🌱" }
        val habit = summary.badges.first { it.emoji == "🔥" }
        assertTrue(firstStep.unlocked)
        assertFalse(habit.unlocked)
        assertEquals(1, habit.current)
        assertEquals(7, habit.target)
    }
}
