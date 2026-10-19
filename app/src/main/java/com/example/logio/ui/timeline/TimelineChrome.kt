/*
 * TimelineChrome: Günlük ekranının iskelet parçaları.
 *
 * Üst bar (takvim, Logio yazısı, seri 🔥, arama), maskotlu karşılama kartı, soldaki zaman çizgisi
 * (TimelineRailRow) ve gün başlıkları. Ayrıca saate göre selamlama ve seri (streak) hesabı.
 */
package com.example.logio.ui.timeline

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.logio.R
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.ui.theme.MimoCloud
import com.example.logio.ui.theme.MimoLavender
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoNavyMuted
import com.example.logio.ui.theme.MimoPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Zaman çizgisi iskeleti ----------

// LazyColumn'da günlerden önce gelen sabit öğe sayısı: banner, istatistikler, sağlık eşitleme
internal const val TIMELINE_TOP_ITEM_COUNT = 3

private val RailWidth = 28.dp
private val RailColor = MimoLavender.copy(alpha = 0.55f)

@StringRes
internal fun greetingForNow(): Int {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_day
        in 18..22 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
}

/** Bugünden (bugün kayıt yoksa dünden) geriye doğru kesintisiz kayıt girilen gün sayısı. */
internal fun calculateStreak(entries: List<JournalEntryEntity>, dayKeyFormat: SimpleDateFormat): Int {
    if (entries.isEmpty()) return 0
    val days = entries.mapTo(HashSet()) { dayKeyFormat.format(Date(it.createdAt)) }
    val cal = java.util.Calendar.getInstance()
    if (dayKeyFormat.format(cal.time) !in days) cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
    var streak = 0
    while (dayKeyFormat.format(cal.time) in days) {
        streak++
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}

@Composable
internal fun LogioTopBar(
    streakDays: Int,
    isSearchVisible: Boolean,
    onCalendarClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCalendarClick) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = stringResource(R.string.go_to_date), tint = MimoNavy)
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mimo",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = MimoNavy
            )
            Spacer(modifier = Modifier.width(10.dp))
            Surface(shape = RoundedCornerShape(50), color = Color(0xFFFFEDD5)) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFF97316),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$streakDays",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEA580C)
                    )
                }
            }
        }
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = if (isSearchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription = if (isSearchVisible) stringResource(R.string.close_search) else stringResource(R.string.dlg_search),
                tint = MimoNavy
            )
        }
    }
}

@Composable
internal fun MascotGreetingBanner(
    greeting: String,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 156.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFEFE8FF), Color(0xFFFFE9F2), Color(0xFFFFF2E8))
                )
            )
    ) {
        // Maskotun arkasındaki yumuşak daire
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 18.dp)
                .size(150.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.45f))
        )
        Image(
            painter = painterResource(id = R.drawable.mascot_main),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(128.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp, end = 140.dp, top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MimoNavy
            )
            Text(
                text = stringResource(R.string.timeline_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MimoNavyMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                onClick = onAddClick,
                shape = RoundedCornerShape(50),
                color = MimoPurple,
                contentColor = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.add_memory), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Solda ince dikey kılavuz hat ve yuvarlak durak noktası çizer; içeriği hattın sağına yerleştirir. */
@Composable
internal fun TimelineRailRow(
    dotCenterY: Dp,
    dotRadius: Dp,
    dotColor: Color,
    drawTop: Boolean,
    drawBottom: Boolean,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = RailWidth.toPx() / 2
                val cy = dotCenterY.toPx()
                val stroke = 2.dp.toPx()
                if (drawTop) drawLine(RailColor, Offset(x, 0f), Offset(x, cy), stroke, StrokeCap.Round)
                if (drawBottom) drawLine(RailColor, Offset(x, cy), Offset(x, size.height), stroke, StrokeCap.Round)
                drawCircle(Color.White, radius = dotRadius.toPx() + 2.5.dp.toPx(), center = Offset(x, cy))
                drawCircle(dotColor, radius = dotRadius.toPx(), center = Offset(x, cy))
            }
    ) {
        Spacer(modifier = Modifier.width(RailWidth + 4.dp))
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
internal fun TimelineDateHeader(date: Date, drawRailAbove: Boolean) {
    val trLocale = currentLocale()
    val dayMonthFormat = remember { SimpleDateFormat("d MMMM", trLocale) }
    val yearFormat = remember { SimpleDateFormat("yyyy", trLocale) }
    val weekdayFormat = remember { SimpleDateFormat("EEEE", trLocale) }
    val dayKeyFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    val now = java.util.Calendar.getInstance()
    val todayKey = dayKeyFormat.format(now.time)
    val currentYear = yearFormat.format(now.time)
    now.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val yesterdayKey = dayKeyFormat.format(now.time)

    val weekday = weekdayFormat.format(date)
    val label = when (dayKeyFormat.format(date)) {
        todayKey -> stringResource(R.string.today_with_weekday, weekday)
        yesterdayKey -> stringResource(R.string.yesterday_with_weekday, weekday)
        else -> weekday
    }
    val year = yearFormat.format(date)
    val dayMonth = dayMonthFormat.format(date) + if (year != currentYear) " $year" else ""

    TimelineRailRow(
        dotCenterY = 30.dp,
        dotRadius = 7.dp,
        dotColor = MimoPurple,
        drawTop = drawRailAbove,
        drawBottom = true
    ) {
        Row(
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = dayMonth,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MimoNavy
            )
            Surface(shape = RoundedCornerShape(50), color = MimoCloud) {
                Text(
                    text = label.replaceFirstChar { it.titlecase(trLocale) },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MimoPurple
                )
            }
        }
    }
}
