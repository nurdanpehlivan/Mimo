package com.example.logio.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.logio.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.time.format.TextStyle
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.ui.theme.MimoBackground
import com.example.logio.ui.theme.MimoCloud
import com.example.logio.ui.theme.MimoLavender
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoNavyMuted
import com.example.logio.ui.theme.MimoPurple
import com.example.logio.ui.timeline.EntryDetailSheet
import com.example.logio.ui.timeline.TimelineItemCard
import com.example.logio.ui.timeline.TimelineViewModel
import com.example.logio.ui.timeline.entryLook
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val TrLocale: Locale get() = Locale.getDefault()
private val SelectedDayColor = Color(0xFF8B7CF6)
private val RailColor = MimoLavender.copy(alpha = 0.55f)
private val MutedText = Color(0xFF8A90B4)
private const val MAX_DOTS_PER_DAY = 3

@Composable
fun CalendarScreen(
    viewModel: TimelineViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {}
) {
    val entries by viewModel.entries.collectAsState()
    val playingFilePath by viewModel.playingFilePath.collectAsState()
    val transcribingEntryIds by viewModel.transcribingEntryIds.collectAsState()
    val context = LocalContext.current

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var visibleMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }
    var detailEntry by remember { mutableStateOf<JournalEntryEntity?>(null) }

    // Gün -> o günün kayıtları (sabahtan akşama sıralı)
    val entriesByDay = remember(entries) {
        entries
            .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
            .mapValues { (_, dayEntries) -> dayEntries.sortedBy { it.createdAt } }
    }
    val selectedEntries = entriesByDay[selectedDate].orEmpty()

    MimoBackground {
        Scaffold(containerColor = Color.Transparent, bottomBar = bottomBar) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
            ) {
                item(key = "title") {
                    CalendarTitleBar(onBack = onBack)
                }
    
                item(key = "calendar") {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFEDE8F7)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                            MonthHeader(
                                month = visibleMonth,
                                showTodayShortcut = visibleMonth != YearMonth.from(today) || selectedDate != today,
                                onPrevious = { visibleMonth = visibleMonth.minusMonths(1) },
                                onNext = { visibleMonth = visibleMonth.plusMonths(1) },
                                onToday = {
                                    visibleMonth = YearMonth.from(today)
                                    selectedDate = today
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            WeekdayRow()
                            Spacer(modifier = Modifier.height(4.dp))
                            AnimatedContent(
                                targetState = visibleMonth,
                                transitionSpec = {
                                    val direction = if (targetState > initialState) 1 else -1
                                    (slideInHorizontally { it / 4 * direction } + fadeIn()) togetherWith
                                        (slideOutHorizontally { -it / 4 * direction } + fadeOut())
                                },
                                label = "monthGrid"
                            ) { month ->
                                MonthGrid(
                                    month = month,
                                    today = today,
                                    selectedDate = selectedDate,
                                    entriesByDay = entriesByDay,
                                    onDayClick = { selectedDate = it }
                                )
                            }
                        }
                    }
                }
    
                item(key = "day-header") {
                    SelectedDayHeader(date = selectedDate, entryCount = selectedEntries.size)
                }
    
                if (selectedEntries.isEmpty()) {
                    item(key = "empty-${selectedDate}") {
                        EmptyDayState(isToday = selectedDate == today)
                    }
                } else {
                    itemsIndexed(selectedEntries, key = { _, entry -> entry.id }) { index, entry ->
                        DayFlowRow(
                            dotColor = entryLook(entry.entryType).tint,
                            drawTop = index > 0,
                            drawBottom = index < selectedEntries.lastIndex
                        ) {
                            TimelineItemCard(
                                entry = entry,
                                isLast = index == selectedEntries.lastIndex,
                                showRail = false,
                                isVoicePlaying = entry.audioPath != null && entry.audioPath == playingFilePath,
                                onVoicePlaybackClick = { viewModel.onVoicePlaybackClick(entry) },
                                isTranscribing = entry.id in transcribingEntryIds,
                                onTranscribeClick = { viewModel.transcribeVoiceEntry(entry) },
                                onDelete = { viewModel.deleteEntry(entry) },
                                onTodoToggle = { taskIndex -> viewModel.toggleTodoItem(entry, taskIndex) },
                                onVideoClick = { viewModel.openVideo(context, entry) },
                                onLinkClick = { viewModel.openLink(context, entry) },
                                onLocationClick = { viewModel.openMap(context, entry) },
                                onCardClick = { detailEntry = entry }
                            )
                        }
                    }
                }
            }
        }

        detailEntry?.let { entry ->
            EntryDetailSheet(
                entry = entry,
                onDismiss = { detailEntry = null },
                onDelete = {
                    viewModel.deleteEntry(entry)
                    detailEntry = null
                },
                onTodoToggle = { taskIndex -> viewModel.toggleTodoItem(entry, taskIndex) }
            )
        }
    }
}

@Composable
private fun CalendarTitleBar(onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = MimoNavy)
            }
        } else {
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = stringResource(R.string.tab_calendar),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MimoNavy
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    showTodayShortcut: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    val monthFormat = remember { DateTimeFormatter.ofPattern("MMMM yyyy", TrLocale) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundNavButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.calendar_prev_month), tint = MimoPurple)
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = month.format(monthFormat).replaceFirstChar { it.titlecase(TrLocale) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MimoNavy
            )
            if (showTodayShortcut) {
                Text(
                    text = stringResource(R.string.calendar_back_to_today),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MimoPurple,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onToday)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        RoundNavButton(onClick = onNext) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.calendar_next_month), tint = MimoPurple)
        }
    }
}

@Composable
private fun RoundNavButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MimoCloud)
            .clickable(onClick = onClick)
    ) { content() }
}

@Composable
private fun WeekdayRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, TrLocale) }.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MutedText
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    entriesByDay: Map<LocalDate, List<JournalEntryEntity>>,
    onDayClick: (LocalDate) -> Unit
) {
    // Pazartesi ile başlayan haftalar; ay öncesi boş hücrelerle doldurulur
    val leadingBlanks = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

    Column {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                dayEntries = entriesByDay[date].orEmpty(),
                                onClick = { onDayClick(date) }
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    dayEntries: List<JournalEntryEntity>,
    onClick: () -> Unit
) {
    val circleColor by animateColorAsState(
        targetValue = if (isSelected) SelectedDayColor else Color.Transparent,
        label = "dayCircle"
    )
    // O günkü farklı kayıt türlerinin renkleri (en fazla 3 nokta)
    val dotColors = remember(dayEntries) {
        dayEntries.map { it.entryType }.distinct().take(MAX_DOTS_PER_DAY).map { entryLook(it).tint.copy(alpha = 0.7f) }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(circleColor)
                .then(
                    if (isToday && !isSelected) Modifier.border(1.5.dp, MimoLavender, CircleShape) else Modifier
                )
        ) {
            Text(
                text = "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isSelected -> Color.White
                    isToday -> MimoPurple
                    else -> MimoNavy
                }
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Row(
            modifier = Modifier.height(5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            dotColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun SelectedDayHeader(date: LocalDate, entryCount: Int) {
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(TrLocale) }
    val weekdayFormat = remember { DateTimeFormatter.ofPattern("EEEE", TrLocale) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 12.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = date.format(dateFormat),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MimoNavy
            )
            Text(
                text = date.format(weekdayFormat).replaceFirstChar { it.titlecase(TrLocale) },
                style = MaterialTheme.typography.bodyMedium,
                color = MimoNavyMuted
            )
        }
        if (entryCount > 0) {
            Surface(shape = RoundedCornerShape(50), color = MimoCloud) {
                Text(
                    text = pluralStringResource(R.plurals.memory_count, entryCount, entryCount),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MimoPurple
                )
            }
        }
    }
}

@Composable
private fun EmptyDayState(isToday: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .padding(vertical = 24.dp, horizontal = 24.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.mascot_empty),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isToday) stringResource(R.string.calendar_empty_today) else stringResource(R.string.calendar_empty_day),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MimoNavy,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isToday) stringResource(R.string.calendar_empty_today_hint) else stringResource(R.string.calendar_empty_day_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            textAlign = TextAlign.Center
        )
    }
}

/** Seçili günün akışı için solda ince kılavuz hat ve kayıt türü renginde durak noktası. */
@Composable
private fun DayFlowRow(
    dotColor: Color,
    drawTop: Boolean,
    drawBottom: Boolean,
    content: @Composable () -> Unit
) {
    val railWidth = 24.dp
    val dotCenterY = 24.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = railWidth.toPx() / 2
                val cy = dotCenterY.toPx()
                val stroke = 2.dp.toPx()
                if (drawTop) drawLine(RailColor, Offset(x, 0f), Offset(x, cy), stroke, StrokeCap.Round)
                if (drawBottom) drawLine(RailColor, Offset(x, cy), Offset(x, size.height), stroke, StrokeCap.Round)
                drawCircle(Color.White, radius = 7.5.dp.toPx(), center = Offset(x, cy))
                drawCircle(dotColor, radius = 5.dp.toPx(), center = Offset(x, cy))
            }
    ) {
        Spacer(modifier = Modifier.width(railWidth + 4.dp))
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}
