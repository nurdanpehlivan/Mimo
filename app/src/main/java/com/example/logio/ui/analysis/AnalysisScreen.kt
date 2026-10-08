package com.example.logio.ui.analysis

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.logio.R
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.ui.theme.MimoBackground
import com.example.logio.ui.theme.MimoCloud
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoNavyMuted
import com.example.logio.ui.theme.MimoPurple
import com.example.logio.ui.timeline.TimelineViewModel
import com.example.logio.ui.timeline.isPlausibleSleep
import com.example.logio.ui.timeline.ExpenseCategory
import com.example.logio.ui.timeline.parseAlcoholDrinks
import com.example.logio.ui.timeline.parseStepCount
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

// ---------- Sabitler ve renkler ----------

private val TrLocale: Locale get() = Locale.getDefault()
private const val SLEEP_BAR_MAX_HOURS = 10.0
private const val MINI_BAR_COUNT = 7

private val CardBorder = Color(0xFFEDE8F7)
private val InkPrimary = MimoNavy
private val InkSecondary = MimoNavyMuted
private val InkMuted = Color(0xFF8A90B4)
private val GridColor = Color(0xFFEDEAF5)
private val EmptyStub = Color(0xFFE6E3F0)

// Grafik serileri (doğrulandı: açık zeminde CVD ve kontrast kontrollerinden geçiyor)
private val MoodSeriesColor = Color(0xFF7C6CFF)
private val StepsSeriesColor = Color(0xFF22A35A)

// Ruh hali dağılımı kutupsaldır (kötü ↔ iyi): iki uçlu, ortası nötr gri
private val MoodScaleColors = listOf(
    Color(0xFFE5486A), // 1 Çok kötü
    Color(0xFFF4A3B4), // 2 Kötü
    Color(0xFFC9C7D3), // 3 Normal (nötr)
    Color(0xFF8FD6B8), // 4 İyi
    Color(0xFF2E9E78)  // 5 Harika
)
private val MoodEmojis = listOf("😢", "😟", "😐", "😊", "🤩")

private enum class StatsPeriod(@StringRes val label: Int, val days: Int, @StringRes val description: Int) {
    WEEK(R.string.period_week, 7, R.string.period_week_desc),
    MONTH(R.string.period_month, 30, R.string.period_month_desc),
    YEAR(R.string.period_year, 365, R.string.period_year_desc)
}

// ---------- İstatistik hesapları (yalnızca okunan kayıtlardan) ----------

/** Bir günün özet değerleri; veri yoksa null. */
private data class DaySummary(
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

private data class PeriodStats(
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


private fun summarizeDays(
    entries: List<JournalEntryEntity>,
    zone: ZoneId,
    today: LocalDate,
    sensorStepsToday: Int
): Map<LocalDate, DaySummary> {
    val byDay = entries.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
    val days = byDay.keys + today
    return days.associateWith { day ->
        val dayEntries = byDay[day].orEmpty()
        val moods = dayEntries.filter { it.entryType == EntryType.MOOD }.mapNotNull { it.moodRating }.filter { it in 1..5 }
        // Aynı gün birden çok eşitleme olabilir; en yüksek değeri al (çift saymamak için)
        val healthSteps = dayEntries.filter { it.entryType == EntryType.STEPS }.mapNotNull { parseStepCount(it.content) }.maxOrNull()
        val steps = listOfNotNull(healthSteps, sensorStepsToday.takeIf { day == today && it > 0 }).maxOrNull()
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

private fun computePeriodStats(
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

private fun formatSleep(hours: Double, pattern: String): String {
    val totalMinutes = Math.round(hours * 60).toInt()
    return String.format(pattern, totalMinutes / 60, totalMinutes % 60)
}

private fun formatNumber(value: Int): String = String.format(TrLocale, "%,d", value)

// ---------- Ekran ----------

@Composable
fun AnalysisScreen(
    viewModel: TimelineViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {}
) {
    val entries by viewModel.entries.collectAsState()
    val stepState by viewModel.stepCounterUiState.collectAsState()
    val profile by viewModel.profile.collectAsState()

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var period by remember { mutableStateOf(StatsPeriod.WEEK) }

    val summaries = remember(entries, stepState.todaySteps) {
        summarizeDays(entries, zone, today, stepState.todaySteps)
    }
    val stats = remember(summaries, period) {
        computePeriodStats(entries, summaries, period, today, zone)
    }
    val lastSevenDays = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }

    MimoBackground {
        Scaffold(containerColor = Color.Transparent, bottomBar = bottomBar) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                AnalysisHeader(
                    entryCount = stats.entryCount,
                    periodDescription = stringResource(period.description),
                    onBack = onBack
                )
                Spacer(modifier = Modifier.height(16.dp))
    
                PeriodSelector(selected = period, onSelect = { period = it })
                Spacer(modifier = Modifier.height(16.dp))
    
                // 2x2 metrik kartları
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SleepMetricCard(stats, Modifier.weight(1f).fillMaxHeight())
                    MoodMetricCard(stats, Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StepsMetricCard(stats, profile.stepGoal, Modifier.weight(1f).fillMaxHeight())
                    NutritionMetricCard(stats, profile.waterGoalMl, Modifier.weight(1f).fillMaxHeight())
                }
    
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CoffeeMetricCard(stats, Modifier.weight(1f).fillMaxHeight())
                    AlcoholMetricCard(stats, period, Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(modifier = Modifier.height(12.dp))
                ExpenseCard(stats, period)

                Spacer(modifier = Modifier.height(20.dp))
                WeeklyFlowCard(days = lastSevenDays, summaries = summaries, today = today, stepGoal = profile.stepGoal)
            }
        }
    }
}

// ---------- 1. Üst kısım ----------

@Composable
private fun AnalysisHeader(
    entryCount: Int,
    periodDescription: String,
    onBack: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = InkPrimary)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = InkPrimary
            )
            Text(
                text = periodDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        }
        // Mini maskot tebrik kartı
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFFFF1F6),
            border = BorderStroke(1.dp, Color(0xFFFFDDE8))
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = if (entryCount > 0) R.drawable.mascot_celebrate else R.drawable.mascot_stats),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(58.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = if (entryCount > 0) stringResource(R.string.stats_cheer_good) else stringResource(R.string.stats_cheer_start),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary
                    )
                    Text(
                        text = if (entryCount > 0) pluralStringResource(R.plurals.stats_saved_memories, entryCount, entryCount) else stringResource(R.string.stats_add_first),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkSecondary
                    )
                }
            }
        }
    }
}

// ---------- 2. Periyot seçici ----------

@Composable
private fun PeriodSelector(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.75f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StatsPeriod.entries.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                if (isSelected) MimoPurple.copy(alpha = 0.14f) else Color.Transparent,
                label = "periodBackground"
            )
            Text(
                text = stringResource(option.label),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .clickable { onSelect(option) }
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MimoPurple else InkSecondary
            )
        }
    }
}

// ---------- 3. Metrik kartları ----------

@Composable
private fun MetricCard(
    title: String,
    icon: ImageVector,
    wash: Color,
    tint: Color,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    @DrawableRes mascot: Int? = null,
    visual: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = wash.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = InkSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                if (mascot != null) {
                    Image(
                        painter = painterResource(mascot),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp).offset(x = 4.dp, y = (-4).dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    maxLines = 1
                )
                if (unit != null) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(unit, style = MaterialTheme.typography.labelMedium, color = InkMuted, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Spacer(modifier = Modifier.weight(1f))
            visual()
        }
    }
}

@Composable
private fun SleepMetricCard(stats: PeriodStats, modifier: Modifier) {
    val tint = Color(0xFF4F46E5)
    val durationPattern = stringResource(R.string.duration_hm)
    val noData = stringResource(R.string.no_data)
    val sleepA11y = stringResource(R.string.a11y_sleep_distribution)
    MetricCard(
        title = stringResource(R.string.action_sleep),
        icon = Icons.Rounded.Bedtime,
        wash = Color(0xFFE0E7FF),
        tint = tint,
        value = stats.sleepAverage?.let { formatSleep(it, durationPattern) } ?: "—",
        unit = if (stats.sleepAverage != null) stringResource(R.string.unit_avg) else null,
        modifier = modifier
    ) {
        val description = stats.sleepBuckets.joinToString { it?.let { h -> formatSleep(h, durationPattern) } ?: noData }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics { contentDescription = sleepA11y.format(description) }
        ) {
            val gap = 4.dp.toPx()
            val count = stats.sleepBuckets.size.coerceAtLeast(1)
            val barWidth = (size.width - gap * (count - 1)) / count
            stats.sleepBuckets.forEachIndexed { i, hours ->
                val x = i * (barWidth + gap)
                if (hours == null) {
                    // Veri olmayan dilim: tabanda kısa gri çubuk
                    drawRoundRect(EmptyStub, Offset(x, size.height - 3.dp.toPx()), androidx.compose.ui.geometry.Size(barWidth, 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
                } else {
                    val h = (size.height * (hours / SLEEP_BAR_MAX_HOURS).toFloat()).coerceIn(4.dp.toPx(), size.height)
                    drawTopRoundedBar(tint.copy(alpha = 0.75f), x, size.height - h, barWidth, h, 4.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun MoodMetricCard(stats: PeriodStats, modifier: Modifier) {
    MetricCard(
        title = stringResource(R.string.action_mood),
        icon = Icons.Rounded.WbSunny,
        wash = Color(0xFFFEF3C7),
        tint = Color(0xFFD97706),
        value = stats.moodAverage?.let { String.format(TrLocale, "%.1f", it) } ?: "—",
        unit = if (stats.moodAverage != null) "/5" else null,
        modifier = modifier
    ) {
        val total = stats.moodCounts.sum()
        val moodA11y = stringResource(R.string.a11y_mood_distribution)
        // Durum dağılımı: kötüden iyiye yığılmış ince çubuk, segmentler arası 2dp boşluk
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .semantics {
                    contentDescription = moodA11y.format(
                        MoodEmojis.zip(stats.moodCounts).joinToString { (emoji, count) -> "$emoji $count" })
                }
        ) {
            if (total == 0) {
                drawRoundRect(EmptyStub, cornerRadius = CornerRadius(size.height / 2))
                return@Canvas
            }
            val gap = 2.dp.toPx()
            val nonZero = stats.moodCounts.count { it > 0 }
            val available = size.width - gap * (nonZero - 1)
            var x = 0f
            stats.moodCounts.forEachIndexed { i, count ->
                if (count == 0) return@forEachIndexed
                val w = available * count / total
                drawRoundRect(MoodScaleColors[i], Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height), CornerRadius(size.height / 2))
                x += w + gap
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MoodEmojis.zip(stats.moodCounts).forEach { (emoji, count) ->
                Text(
                    text = if (count > 0) "$emoji$count" else emoji,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (count > 0) InkSecondary else InkMuted.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun StepsMetricCard(stats: PeriodStats, stepGoal: Int, modifier: Modifier) {
    val progress = ((stats.stepsAverage ?: 0).toFloat() / stepGoal).coerceIn(0f, 1f)
    MetricCard(
        title = stringResource(R.string.action_steps),
        icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
        wash = Color(0xFFDCFCE7),
        tint = Color(0xFF16A34A),
        value = stats.stepsAverage?.let(::formatNumber) ?: "—",
        unit = if (stats.stepsAverage != null) stringResource(R.string.unit_per_day) else null,
        modifier = modifier
    ) {
        GoalBar(progress = progress, color = StepsSeriesColor, track = Color.White)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.stats_goal_progress, formatNumber(stepGoal), (progress * 100).toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = InkSecondary
        )
    }
}

@Composable
private fun NutritionMetricCard(stats: PeriodStats, waterGoalMl: Int, modifier: Modifier) {
    val waterProgress = ((stats.waterAverage ?: 0).toFloat() / waterGoalMl).coerceIn(0f, 1f)
    MetricCard(
        title = stringResource(R.string.stats_nutrition_water),
        icon = Icons.Rounded.Restaurant,
        wash = Color(0xFFFFEDD5),
        tint = Color(0xFFEA580C),
        value = stats.caloriesAverage?.let(::formatNumber) ?: "—",
        unit = if (stats.caloriesAverage != null) stringResource(R.string.unit_kcal_per_day) else null,
        mascot = R.drawable.mascot_eating,
        modifier = modifier
    ) {
        GoalBar(progress = waterProgress, color = Color(0xFF38A3E0), track = Color.White)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stats.waterAverage?.let { stringResource(R.string.stats_water_avg, String.format(TrLocale, "%.1f", it / 1000.0), (waterProgress * 100).toInt()) }
                ?: stringResource(R.string.stats_no_water),
            style = MaterialTheme.typography.labelSmall,
            color = InkSecondary
        )
    }
}

@Composable
private fun GoalBar(progress: Float, color: Color, track: Color) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
        color = color,
        trackColor = track,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

/** Dönemin 7 dilimi için küçük çubuklar; veri olmayan dilim tabanda gri çizgi. */
@Composable
private fun MiniBars(values: List<Double?>, color: Color, a11y: String) {
    val max = (values.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .semantics { contentDescription = a11y }
    ) {
        val gap = 4.dp.toPx()
        val count = values.size.coerceAtLeast(1)
        val barWidth = (size.width - gap * (count - 1)) / count
        values.forEachIndexed { i, value ->
            val x = i * (barWidth + gap)
            if (value == null || value <= 0.0) {
                drawRoundRect(EmptyStub, Offset(x, size.height - 3.dp.toPx()), androidx.compose.ui.geometry.Size(barWidth, 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
            } else {
                val h = (size.height * (value / max).toFloat()).coerceIn(4.dp.toPx(), size.height)
                drawTopRoundedBar(color, x, size.height - h, barWidth, h, 4.dp.toPx())
            }
        }
    }
}

private fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) formatNumber(value.toInt()) else String.format(TrLocale, "%.1f", value)

@Composable
private fun CoffeeMetricCard(stats: PeriodStats, modifier: Modifier) {
    val tint = Color(0xFF8D5B4C)
    MetricCard(
        title = stringResource(R.string.action_coffee),
        icon = Icons.Rounded.Coffee,
        wash = Color(0xFFF5EBE6),
        tint = tint,
        value = stats.coffeeAverage?.let(::formatDecimal) ?: "—",
        unit = if (stats.coffeeAverage != null) stringResource(R.string.unit_cups_per_day) else null,
        mascot = R.drawable.mascot_coffee,
        modifier = modifier
    ) {
        MiniBars(
            values = stats.coffeeBuckets,
            color = tint.copy(alpha = 0.75f),
            a11y = stringResource(R.string.a11y_coffee_distribution, stats.coffeeBuckets.joinToString { it?.let(::formatDecimal) ?: "-" })
        )
    }
}

@Composable
private fun AlcoholMetricCard(stats: PeriodStats, period: StatsPeriod, modifier: Modifier) {
    val tint = Color(0xFFC2410C)
    MetricCard(
        title = stringResource(R.string.action_alcohol),
        icon = Icons.Rounded.LocalBar,
        wash = Color(0xFFFFEDD5),
        tint = tint,
        value = if (stats.alcoholDays > 0) formatDecimal(stats.alcoholTotal) else "0",
        unit = pluralStringResource(R.plurals.glasses_unit, stats.alcoholTotal.toInt().coerceAtLeast(0)),
        mascot = R.drawable.mascot_alcohol,
        modifier = modifier
    ) {
        MiniBars(
            values = stats.alcoholBuckets,
            color = tint.copy(alpha = 0.7f),
            a11y = stringResource(R.string.a11y_alcohol_distribution, stats.alcoholBuckets.joinToString { it?.let(::formatDecimal) ?: "-" })
        )
        Spacer(modifier = Modifier.height(6.dp))
        // Alkolsüz gün sayısı olumlu bir hedef gibi gösterilir
        val freeDays = (period.days - stats.alcoholDays).coerceAtLeast(0)
        Text(
            text = stringResource(R.string.stats_alcohol_free_days, freeDays, period.days),
            style = MaterialTheme.typography.labelSmall,
            color = InkSecondary
        )
    }
}

@Composable
private fun ExpenseCard(stats: PeriodStats, period: StatsPeriod) {
    val money = remember { java.text.NumberFormat.getNumberInstance(TrLocale).apply { maximumFractionDigits = 0 } }
    fun lira(value: Double) = "₺${money.format(value)}"

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFEDD5).copy(alpha = 0.55f),
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                        ) {
                            Icon(Icons.Rounded.Payments, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_expense), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = InkSecondary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(lira(stats.expenseTotal), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = InkPrimary)
                    Text(
                        text = stringResource(R.string.stats_expense_daily, lira(stats.expenseTotal / period.days)),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkMuted
                    )
                }
                Image(
                    painter = painterResource(R.drawable.mascot_money),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (stats.expenseByCategory.isEmpty()) {
                Text(stringResource(R.string.stats_no_expense), style = MaterialTheme.typography.labelSmall, color = InkSecondary)
            } else {
                val total = stats.expenseTotal.coerceAtLeast(0.01)
                // Kategori dağılımı: tek yığılmış çubuk, segmentler arası 2dp boşluk
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .semantics {
                            contentDescription = stats.expenseByCategory.joinToString { (c, a) -> "${c.name} ${lira(a)}" }
                        }
                ) {
                    val gap = 2.dp.toPx()
                    val available = size.width - gap * (stats.expenseByCategory.size - 1)
                    var x = 0f
                    stats.expenseByCategory.forEach { (category, amount) ->
                        val w = (available * (amount / total)).toFloat()
                        drawRoundRect(category.color, Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height), CornerRadius(size.height / 2))
                        x += w + gap
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                stats.expenseByCategory.take(4).forEach { (category, amount) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(category.color))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${category.emoji} ${stringResource(category.label)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${lira(amount)} · ${stringResource(R.string.percent_value, (amount / total * 100).toInt())}",
                            style = MaterialTheme.typography.labelMedium,
                            color = InkSecondary
                        )
                    }
                }
            }
        }
    }
}

// ---------- 4. Haftalık akış grafikleri ----------

/**
 * Ruh hali (1–5) ve adım farklı ölçeklerde olduğu için tek grafikte iki eksen yerine
 * aynı günleri paylaşan iki küçük grafik kullanılır. Bir güne dokununca değerleri gösterilir.
 */
@Composable
private fun WeeklyFlowCard(
    days: List<LocalDate>,
    summaries: Map<LocalDate, DaySummary>,
    today: LocalDate,
    stepGoal: Int
) {
    var selectedIndex by remember(days) { mutableIntStateOf(days.lastIndex) }
    val dayLabelFormat = remember { DateTimeFormatter.ofPattern("EEE", TrLocale) }
    val dayDetailFormat = remember { DateTimeFormatter.ofPattern("d MMMM EEEE", TrLocale) }
    val noData = stringResource(R.string.no_data)

    val moodValues = days.map { summaries[it]?.moodAverage }
    val stepValues = days.map { summaries[it]?.steps }
    val selectedDay = days[selectedIndex]

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.stats_weekly_flow),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary
                    )
                    Text(
                        text = stringResource(R.string.stats_weekly_flow_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary
                    )
                }
                Image(
                    painter = painterResource(R.drawable.mascot_stats),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(72.dp)
                )
            }

            // Seçili günün değerleri (dokunma ile değişir)
            Spacer(modifier = Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = MimoCloud) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedDay == today) stringResource(R.string.today) else selectedDay.format(dayDetailFormat).replaceFirstChar { it.titlecase(TrLocale) },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = InkPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    LegendValue(MoodSeriesColor, moodValues[selectedIndex]?.let { String.format(TrLocale, "%.1f/5", it) } ?: "—")
                    Spacer(modifier = Modifier.width(12.dp))
                    LegendValue(StepsSeriesColor, stepValues[selectedIndex]?.let { pluralStringResource(R.plurals.steps_count, it, formatNumber(it)) } ?: "—")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            ChartLabel(stringResource(R.string.action_mood), "1–5")
            MoodLineChart(
                values = moodValues,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
                description = days.zip(moodValues).joinToString { (d, v) -> "${d.format(dayLabelFormat)} ${v?.let { String.format(TrLocale, "%.1f", it) } ?: noData}" }
            )

            Spacer(modifier = Modifier.height(14.dp))
            ChartLabel(stringResource(R.string.action_steps), stringResource(R.string.daily))
            StepsBarChart(
                values = stepValues,
                stepGoal = stepGoal,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
                description = days.zip(stepValues).joinToString { (d, v) -> "${d.format(dayLabelFormat)} ${v?.let(::formatNumber) ?: noData}" }
            )

            // Ortak gün ekseni
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                days.forEachIndexed { i, day ->
                    Text(
                        text = day.format(dayLabelFormat).replaceFirstChar { it.titlecase(TrLocale) },
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (i == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == selectedIndex) InkPrimary else InkMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendValue(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = InkPrimary)
    }
}

@Composable
private fun ChartLabel(title: String, hint: String) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = InkSecondary)
        Spacer(modifier = Modifier.width(4.dp))
        Text(hint, style = MaterialTheme.typography.labelSmall, color = InkMuted)
    }
}

private fun Modifier.daySelectable(count: Int, onSelect: (Int) -> Unit): Modifier =
    pointerInput(count) {
        detectTapGestures { offset ->
            val index = (offset.x / (size.width / count.toFloat())).toInt().coerceIn(0, count - 1)
            onSelect(index)
        }
    }

@Composable
private fun MoodLineChart(
    values: List<Double?>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    description: String,
    height: Dp = 96.dp
) {
    val chartA11y = stringResource(R.string.a11y_mood_chart)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .daySelectable(values.size, onSelect)
            .semantics { contentDescription = chartA11y.format(description) }
    ) {
        val slot = size.width / values.size
        val padTop = 8.dp.toPx()
        val padBottom = 8.dp.toPx()
        val plotHeight = size.height - padTop - padBottom
        fun yFor(v: Double) = padTop + plotHeight * (1f - ((v - 1.0) / 4.0).toFloat())

        // Silik yardımcı çizgiler: 1, 3, 5
        listOf(1.0, 3.0, 5.0).forEach { level ->
            drawLine(GridColor, Offset(0f, yFor(level)), Offset(size.width, yFor(level)), strokeWidth = 1.dp.toPx())
        }
        // Seçili gün kılavuzu
        val selX = slot * selectedIndex + slot / 2
        drawLine(MimoPurple.copy(alpha = 0.18f), Offset(selX, 0f), Offset(selX, size.height), strokeWidth = slot * 0.6f, cap = StrokeCap.Round)

        // Çizgi: veri olmayan günlerde kopar
        val path = Path()
        var penDown = false
        values.forEachIndexed { i, v ->
            if (v == null) {
                penDown = false
            } else {
                val x = slot * i + slot / 2
                if (penDown) path.lineTo(x, yFor(v)) else path.moveTo(x, yFor(v))
                penDown = true
            }
        }
        drawPath(path, MoodSeriesColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        // İşaretler: 8dp, 2dp beyaz halka
        values.forEachIndexed { i, v ->
            if (v != null) {
                val center = Offset(slot * i + slot / 2, yFor(v))
                val r = if (i == selectedIndex) 5.dp.toPx() else 4.dp.toPx()
                drawCircle(Color.White, radius = r + 2.dp.toPx(), center = center)
                drawCircle(MoodSeriesColor, radius = r, center = center)
            }
        }
    }
}

@Composable
private fun StepsBarChart(
    values: List<Int?>,
    stepGoal: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    description: String,
    height: Dp = 96.dp
) {
    val chartA11y = stringResource(R.string.a11y_steps_chart)
    val maxValue = (values.filterNotNull().maxOrNull() ?: 0).coerceAtLeast(stepGoal)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .daySelectable(values.size, onSelect)
            .semantics { contentDescription = chartA11y.format(description) }
    ) {
        val slot = size.width / values.size
        val barWidth = (slot * 0.46f).coerceAtMost(22.dp.toPx())
        val baseline = size.height

        // Hedef çizgisi (silik, kesik yerine ince)
        val goalY = baseline - size.height * (stepGoal / maxValue.toFloat())
        drawLine(GridColor, Offset(0f, goalY), Offset(size.width, goalY), strokeWidth = 1.dp.toPx())
        drawLine(GridColor, Offset(0f, baseline - 0.5.dp.toPx()), Offset(size.width, baseline - 0.5.dp.toPx()), strokeWidth = 1.dp.toPx())

        values.forEachIndexed { i, v ->
            val x = slot * i + (slot - barWidth) / 2
            if (v == null || v == 0) {
                drawRoundRect(EmptyStub, Offset(x, baseline - 3.dp.toPx()), androidx.compose.ui.geometry.Size(barWidth, 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
            } else {
                val h = (size.height * (v / maxValue.toFloat())).coerceAtLeast(4.dp.toPx())
                val color = if (i == selectedIndex) StepsSeriesColor else StepsSeriesColor.copy(alpha = 0.55f)
                drawTopRoundedBar(color, x, baseline - h, barWidth, h, 4.dp.toPx())
            }
        }
    }
}

/** Yalnızca üst köşeleri yuvarlatılmış, tabana oturan çubuk. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTopRoundedBar(
    color: Color,
    x: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float
) {
    val r = radius.coerceAtMost(width / 2).coerceAtMost(height)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(x, top, x + width, top + height),
                topLeft = CornerRadius(r),
                topRight = CornerRadius(r),
                bottomRight = CornerRadius.Zero,
                bottomLeft = CornerRadius.Zero
            )
        )
    }
    drawPath(path, color)
}
