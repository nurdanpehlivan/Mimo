/*
 * AnalysisCharts: İstatistik sekmesinin en altındaki "Haftalık akış" kartı.
 *
 * Son 7 günün ruh hali (çizgi grafik) ve adım (çubuk grafik) değerleri Compose Canvas ile,
 * kütüphanesiz çizilir. Bir güne dokununca o günün değerleri üstte gösterilir.
 */
package com.example.logio.ui.analysis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.logio.R
import com.example.logio.ui.theme.MimoCloud
import com.example.logio.ui.theme.MimoPurple
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ---------- Haftalık akış grafikleri ----------

/**
 * Ruh hali (1–5) ve adım farklı ölçeklerde olduğu için tek grafikte iki eksen yerine
 * aynı günleri paylaşan iki küçük grafik kullanılır. Bir güne dokununca değerleri gösterilir.
 */
@Composable
internal fun WeeklyFlowCard(
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
