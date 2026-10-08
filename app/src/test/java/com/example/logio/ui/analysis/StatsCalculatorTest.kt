/*
 * StatsCalculatorTest: İstatistik hesaplarının (StatsCalculator.kt) birim testleri.
 *
 * Telefon gerekmez; bilgisayarda saniyeler içinde çalışır:  ./gradlew :app:testDebugUnitTest
 * Her test bir kuralı korur (ör. "16 saatten uzun uyku sayılmaz"). Biri bozulursa test kırmızı yanar.
 */
package com.example.logio.ui.analysis

import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.ui.timeline.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsCalculatorTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 8)

    private fun at(day: LocalDate, hour: Int = 12): Long = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun entry(type: EntryType, day: LocalDate, hour: Int = 12) = JournalEntryEntity(
        title = "", content = "", entryType = type, createdAt = at(day, hour)
    )

    private fun summarize(entries: List<JournalEntryEntity>) =
        summarizeDays(entries, zone, today)

    // ---------- Günlük özet ----------

    @Test
    fun `16 saatten uzun uyku sayilmaz`() {
        val summaries = summarize(
            listOf(
                entry(EntryType.SLEEP, today).copy(sleepHours = 85.0),
                entry(EntryType.SLEEP, today).copy(sleepHours = 7.0),
                entry(EntryType.SLEEP, today.minusDays(1)).copy(sleepHours = 85.0)
            )
        )
        assertEquals(7.0, summaries.getValue(today).sleepHours!!, 0.001)
        assertNull("Yalnızca hatalı kayıt varsa uyku 'veri yok' sayılır", summaries.getValue(today.minusDays(1)).sleepHours)
    }

    @Test
    fun `ayni gece iki kez kaydedilen uykudan en uzunu alinir`() {
        val summaries = summarize(
            listOf(
                entry(EntryType.SLEEP, today).copy(sleepHours = 6.5),
                entry(EntryType.SLEEP, today).copy(sleepHours = 7.5)
            )
        )
        assertEquals(7.5, summaries.getValue(today).sleepHours!!, 0.001)
    }

    @Test
    fun `kahve fincanlari toplanir, sayisi bos kayit 1 fincan sayilir`() {
        val summaries = summarize(
            listOf(
                entry(EntryType.COFFEE, today).copy(coffeeCups = 2),
                entry(EntryType.COFFEE, today).copy(coffeeCups = null)
            )
        )
        assertEquals(3, summaries.getValue(today).coffeeCups)
    }

    @Test
    fun `veri girilmeyen gun sifir degil null olur`() {
        val day = summarize(emptyList()).getValue(today)
        assertNull(day.sleepHours)
        assertNull(day.coffeeCups)
        assertNull(day.waterMl)
        assertNull(day.alcoholDrinks)
    }

    @Test
    fun `ayni gunun birden cok adim kaydindan en buyugu alinir, toplanmaz`() {
        val summaries = summarize(
            listOf(
                entry(EntryType.STEPS, today, hour = 9).copy(content = "Bugün 4.000 adım attınız."),
                entry(EntryType.STEPS, today, hour = 18).copy(content = "Bugün 6.500 adım attınız."),
                entry(EntryType.STEPS, today.minusDays(1)).copy(content = "Today you walked 3000 steps.")
            )
        )
        assertEquals(6500, summaries.getValue(today).steps)
        assertEquals("Metin hangi dilde olursa olsun ilk sayı okunur", 3000, summaries.getValue(today.minusDays(1)).steps)
    }

    @Test
    fun `alkol miktari eski serbest metinden de okunur`() {
        val summaries = summarize(
            listOf(
                entry(EntryType.ALCOHOL, today).copy(alcoholUnits = "2"),
                entry(EntryType.ALCOHOL, today).copy(alcoholUnits = "1,5 bira")
            )
        )
        assertEquals(3.5, summaries.getValue(today).alcoholDrinks!!, 0.001)
    }

    // ---------- Dönem özeti ----------

    private fun weekStats(entries: List<JournalEntryEntity>) =
        computePeriodStats(entries, summarize(entries), StatsPeriod.WEEK, today, zone)

    @Test
    fun `haftalik uyku ortalamasi yalnizca veri olan gunlerden hesaplanir`() {
        val stats = weekStats(
            listOf(
                entry(EntryType.SLEEP, today).copy(sleepHours = 8.0),
                entry(EntryType.SLEEP, today.minusDays(2)).copy(sleepHours = 6.0)
            )
        )
        assertEquals(7.0, stats.sleepAverage!!, 0.001)
        assertEquals("Mini grafik 7 dilimdir", 7, stats.sleepBuckets.size)
    }

    @Test
    fun `donem disindaki kayitlar sayilmaz`() {
        val stats = weekStats(
            listOf(
                entry(EntryType.TEXT, today),
                entry(EntryType.TEXT, today.minusDays(6)),
                entry(EntryType.TEXT, today.minusDays(7))
            )
        )
        assertEquals(2, stats.entryCount)
    }

    @Test
    fun `alkolsuz gunler icin alkol iceren gun sayisi dogru`() {
        val stats = weekStats(
            listOf(
                entry(EntryType.ALCOHOL, today).copy(alcoholUnits = "2"),
                entry(EntryType.ALCOHOL, today.minusDays(3)).copy(alcoholUnits = "1")
            )
        )
        assertEquals(3.0, stats.alcoholTotal, 0.001)
        assertEquals(2, stats.alcoholDays)
    }

    @Test
    fun `harcamalar kategoriye gore toplanir ve buyukten kucuge siralanir`() {
        val stats = weekStats(
            listOf(
                entry(EntryType.EXPENSE, today).copy(expenseAmount = 100.0, expenseCategory = "food"),
                entry(EntryType.EXPENSE, today).copy(expenseAmount = 150.0, expenseCategory = "food"),
                entry(EntryType.EXPENSE, today).copy(expenseAmount = 300.0, expenseCategory = "transport"),
                entry(EntryType.EXPENSE, today).copy(expenseAmount = 40.0, expenseCategory = "Genel"),
                entry(EntryType.EXPENSE, today).copy(expenseAmount = 0.0, expenseCategory = "food")
            )
        )
        assertEquals(590.0, stats.expenseTotal, 0.001)
        assertEquals(
            listOf(ExpenseCategory.TRANSPORT to 300.0, ExpenseCategory.FOOD to 250.0, ExpenseCategory.OTHER to 40.0),
            stats.expenseByCategory
        )
    }
}
