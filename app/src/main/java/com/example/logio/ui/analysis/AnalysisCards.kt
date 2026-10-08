package com.example.logio.ui.analysis

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.logio.R
import com.example.logio.ui.theme.MimoPurple

private const val SLEEP_BAR_MAX_HOURS = 10.0

// Ruh hali dağılımı kutupsaldır (kötü ↔ iyi): iki uçlu, ortası nötr gri
private val MoodScaleColors = listOf(
    Color(0xFFE5486A), // 1 Çok kötü
    Color(0xFFF4A3B4), // 2 Kötü
    Color(0xFFC9C7D3), // 3 Normal (nötr)
    Color(0xFF8FD6B8), // 4 İyi
    Color(0xFF2E9E78)  // 5 Harika
)
private val MoodEmojis = listOf("😢", "😟", "😐", "😊", "🤩")

private fun formatSleep(hours: Double, pattern: String): String {
    val totalMinutes = Math.round(hours * 60).toInt()
    return String.format(pattern, totalMinutes / 60, totalMinutes % 60)
}

private fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) formatNumber(value.toInt()) else String.format(TrLocale, "%.1f", value)

// ---------- 1. Üst kısım ----------

@Composable
internal fun AnalysisHeader(
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
internal fun PeriodSelector(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
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
internal fun SleepMetricCard(stats: PeriodStats, modifier: Modifier) {
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
internal fun MoodMetricCard(stats: PeriodStats, modifier: Modifier) {
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
internal fun StepsMetricCard(stats: PeriodStats, stepGoal: Int, modifier: Modifier) {
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
internal fun NutritionMetricCard(stats: PeriodStats, waterGoalMl: Int, modifier: Modifier) {
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

@Composable
internal fun CoffeeMetricCard(stats: PeriodStats, modifier: Modifier) {
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
internal fun AlcoholMetricCard(stats: PeriodStats, period: StatsPeriod, modifier: Modifier) {
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
internal fun ExpenseCard(stats: PeriodStats, period: StatsPeriod) {
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
