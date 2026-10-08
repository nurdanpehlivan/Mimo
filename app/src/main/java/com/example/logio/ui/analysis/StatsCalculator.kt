/*
 * StatsCalculator: İstatistik hesapları.
 *
 * summarizeDays: kayıtları gün gün özetler (o günün uykusu, kahvesi, harcaması...).
 * computePeriodStats: seçili dönemin (hafta/ay/yıl) ortalamalarını ve toplamlarını çıkarır.
 * Compose'a ve Android'e bağımlı değildir; birim testleri StatsCalculatorTest.kt içindedir.
 */
package com.example.logio.ui.analysis

import androidx.annotation.StringRes
import com.example.logio.R
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.ui.timeline.ExpenseCategory
import com.example.logio.ui.timeline.isPlausibleSleep
import com.example.logio.ui.timeline.parseAlcoholDrinks
import com.example.logio.ui.timeline.parseStepCount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

private const val MINI_BAR_COUNT = 7

internal enum class StatsPeriod(@StringRes val label: Int, val days: Int, @StringRes val description: Int) {
    WEEK(R.string.period_week, 7, R.string.period_week_desc),
    MONTH(R.string.period_month, 30, R.string.period_month_desc),
    YEAR(R.string.period_year, 365, R.string.period_year_desc)
}

/** Bir günün özet değerleri; veri yoksa null. */
internal data class DaySummary(
    val sleepHours: Double?,
    val moodAverage: Double?,
    val moodRatings: List<Int>,
    val steps: Int?,
    val calories: Int?,
    val waterMl: Int?,
    val coffeeCups: Int?,
    val alcoholDrinks: Double?,
    val expenses: List<JournalEntryEntity>
)

internal data class PeriodStats(
    val entryCount: Int,
    val sleepAverage: Double?,
    val sleepBuckets: List<Double?>,
    val moodAverage: Double?,
    val moodCounts: List<Int>,
    val stepsAverage: Int?,
    val caloriesAverage: Int?,
    val waterAverage: Int?,
    val coffeeAverage: Double?,
    val coffeeBuckets: List<Double?>,
    val alcoholTotal: Double,
    val alcoholDays: Int,
    val alcoholBuckets: List<Double?>,
    val expenseTotal: Double,
    val expenseByCategory: List<Pair<ExpenseCategory, Double>>
)

internal fun summarizeDays(
    entries: List<JournalEntryEntity>,
    zone: ZoneId,
    today: LocalDate
): Map<LocalDate, DaySummary> {
    val byDay = entries.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
    val days = byDay.keys + today
    return days.associateWith { day ->
        val dayEntries = byDay[day].orEmpty()
        val moods = dayEntries.filter { it.entryType == EntryType.MOOD }.mapNotNull { it.moodRating }.filter { it in 1..5 }
        // Adım Health Connect'ten gelir; aynı gün birden çok eşitleme olabilir, en yüksek değeri al (çift saymamak için)
        val steps = dayEntries.filter { it.entryType == EntryType.STEPS }.mapNotNull { parseStepCount(it.content) }.maxOrNull()
        DaySummary(
            // Manuel + Health Connect aynı geceyi iki kez kaydedebilir; en uzun olanı al
            sleepHours = dayEntries.filter { it.entryType == EntryType.SLEEP }.mapNotNull { it.sleepHours }.filter { isPlausibleSleep(it) }.maxOrNull(),
            moodAverage = moods.takeIf { it.isNotEmpty() }?.average(),
            moodRatings = moods,
            steps = steps,
            calories = dayEntries.filter { it.entryType == EntryType.MEAL }.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }?.sum(),
            waterMl = dayEntries.filter { it.entryType == EntryType.WATER }.mapNotNull { it.waterAmountMl }.takeIf { it.isNotEmpty() }?.sum(),
            coffeeCups = dayEntries.filter { it.entryType == EntryType.COFFEE }.map { it.coffeeCups ?: 1 }.takeIf { it.isNotEmpty() }?.sum(),
            alcoholDrinks = dayEntries.filter { it.entryType == EntryType.ALCOHOL }.map { parseAlcoholDrinks(it.alcoholUnits) ?: 1.0 }.takeIf { it.isNotEmpty() }?.sum(),
            expenses = dayEntries.filter { it.entryType == EntryType.EXPENSE && (it.expenseAmount ?: 0.0) > 0.0 }
        )
    }
}

internal fun computePeriodStats(
    entries: List<JournalEntryEntity>,
    summaries: Map<LocalDate, DaySummary>,
    period: StatsPeriod,
    today: LocalDate,
    zone: ZoneId
): PeriodStats {
    val start = today.minusDays(period.days - 1L)
    val periodDays = (0 until period.days).map { start.plusDays(it.toLong()) }
    val inPeriod = periodDays.mapNotNull { summaries[it] }

    // Mini çubuklar: dönem 7 eşit parçaya bölünür, her parçanın uyku ortalaması
    val bucketSize = ceil(period.days / MINI_BAR_COUNT.toDouble()).toInt()
    val sleepBuckets = periodDays.chunked(bucketSize).takeLast(MINI_BAR_COUNT).map { bucket ->
        bucket.mapNotNull { summaries[it]?.sleepHours }.takeIf { it.isNotEmpty() }?.average()
    }

    val allMoods = inPeriod.flatMap { it.moodRatings }
    val periodExpenses = inPeriod.flatMap { it.expenses }
    // Kahve ve alkol için de aynı 7 dilim: dilimdeki günlerin toplamı
    fun buckets(value: (DaySummary) -> Double?): List<Double?> =
        periodDays.chunked(bucketSize).takeLast(MINI_BAR_COUNT).map { bucket ->
            bucket.mapNotNull { summaries[it]?.let(value) }.takeIf { it.isNotEmpty() }?.sum()
        }
    return PeriodStats(
        entryCount = entries.count {
            val day = Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate()
            !day.isBefore(start) && !day.isAfter(today)
        },
        sleepAverage = inPeriod.mapNotNull { it.sleepHours }.takeIf { it.isNotEmpty() }?.average(),
        sleepBuckets = sleepBuckets,
        moodAverage = allMoods.takeIf { it.isNotEmpty() }?.average(),
        moodCounts = (1..5).map { rating -> allMoods.count { it == rating } },
        stepsAverage = inPeriod.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
        caloriesAverage = inPeriod.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
        waterAverage = inPeriod.mapNotNull { it.waterMl }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
        coffeeAverage = inPeriod.mapNotNull { it.coffeeCups }.takeIf { it.isNotEmpty() }?.average(),
        coffeeBuckets = buckets { it.coffeeCups?.toDouble() },
        alcoholTotal = inPeriod.sumOf { it.alcoholDrinks ?: 0.0 },
        alcoholDays = inPeriod.count { it.alcoholDrinks != null },
        alcoholBuckets = buckets { it.alcoholDrinks },
        expenseTotal = periodExpenses.sumOf { it.expenseAmount ?: 0.0 },
        expenseByCategory = periodExpenses
            .groupBy { ExpenseCategory.fromKey(it.expenseCategory) }
            .map { (category, items) -> category to items.sumOf { it.expenseAmount ?: 0.0 } }
            .sortedByDescending { it.second }
    )
}
