/*
 * TimelineCards: Zaman tünelindeki kayıt kartları.
 *
 * TimelineItemCard kaydın türüne göre doğru kartı seçer: MealCard (yemek), VoiceNoteCard (ses),
 * TodoCard (yapılacaklar), SharedLinkCard (link) ya da GenericEntryCard (diğerleri: kahve, su, uyku...).
 * Ayrıca Günlük'ün üstündeki HealthActivityCard (adım + uyku) ve WaterMiniCard (su) buradadır.
 * Takvim ve Profil ekranları da kayıtları bu kartlarla gösterir.
 */
package com.example.logio.ui.timeline

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.logio.R
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoPurple
import com.example.logio.ui.share.AiChip
import com.example.logio.ui.share.CategoryBadge
import com.example.logio.ui.share.LinkThumbnail
import com.example.logio.ui.share.classifySharedLink
import com.example.logio.ui.share.hostLabel
import com.example.logio.ui.share.youtubeThumbnailUrl
import com.example.logio.data.audio.VOICE_PLACEHOLDER
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineItemCard(
    entry: JournalEntryEntity,
    isLast: Boolean = true,
    // false: zaman çizgisini ekran iskeleti (TimelineRailRow) çizer
    showRail: Boolean = true,
    isVoicePlaying: Boolean,
    onVoicePlaybackClick: () -> Unit,
    isTranscribing: Boolean = false,
    onTranscribeClick: () -> Unit = {},
    onDelete: () -> Unit,
    onTodoToggle: (Int) -> Unit,
    onVideoClick: () -> Unit,
    onLinkClick: () -> Unit,
    onLocationClick: () -> Unit,
    onCardClick: () -> Unit
) {
    val look = entryLook(entry.entryType)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showRail) {
            Column(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AccentBubble(icon = look.icon, tint = look.tint, wash = look.wash, size = 44.dp)
                if (!isLast) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .width(3.dp)
                            .weight(1f)
                            .clip(CircleShape)
                            .background(look.wash)
                    )
                }
            }
        }

        val cardModifier = Modifier
            .weight(1f)
            .padding(bottom = if (isLast) 8.dp else 12.dp)

        when {
            entry.entryType == EntryType.MEAL ->
                MealCard(entry, onDelete, onCardClick, cardModifier)
            entry.entryType == EntryType.VOICE && entry.audioPath != null ->
                VoiceNoteCard(entry, isVoicePlaying, onVoicePlaybackClick, isTranscribing, onTranscribeClick, onDelete, onCardClick, cardModifier)
            entry.entryType == EntryType.TODO ->
                TodoCard(entry, onTodoToggle, onDelete, onCardClick, cardModifier)
            entry.entryType == EntryType.LINK ->
                SharedLinkCard(entry, onLinkClick, onDelete, cardModifier)
            else ->
                GenericEntryCard(entry, onDelete, onVideoClick, onLinkClick, onLocationClick, onCardClick, cardModifier)
        }
    }
}

// ---------- Kart ortak parçaları ----------

private val CardShape = RoundedCornerShape(18.dp)
private val CardBorderColor = Color(0xFFEDE8F7)
private val CardMutedText = Color(0xFF8A90B4)
private val CardBodyText = Color(0xFF4A4F75)

/** Tüm zaman çizgisi kartlarının ortak kabuğu: 18dp köşe, ince sınır, hafif gölge. */
@Composable
private fun LogioCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), content = content)
    }
}

@Composable
private fun DeleteChip(onDelete: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0xFFF4F1F8))
            .clickable(onClick = onDelete)
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.delete),
            tint = Color(0xFF9AA0C0),
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun TimeLabel(createdAt: Long) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Text(
        text = timeFormat.format(Date(createdAt)),
        style = MaterialTheme.typography.labelMedium,
        color = CardMutedText
    )
}

// ---------- 1. Besin / Yemek kartı ----------

@Composable
private fun MealCard(
    entry: JournalEntryEntity,
    onDelete: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val look = entryLook(entry.entryType)
    val imagePath = entry.imagePaths?.split(",")?.firstOrNull { it.isNotBlank() }

    LogioCard(modifier = modifier, onClick = onCardClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(look.wash),
                contentAlignment = Alignment.Center
            ) {
                if (imagePath != null) {
                    AsyncImage(
                        model = imagePath,
                        contentDescription = entry.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(look.icon, contentDescription = null, tint = look.tint, modifier = Modifier.size(32.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = entry.title.ifBlank { stringResource(R.string.type_meal) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MimoNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    TimeLabel(entry.createdAt)
                    Spacer(modifier = Modifier.width(6.dp))
                    DeleteChip(onDelete)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${entry.calories ?: 0}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFEA580C)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "kcal",
                        style = MaterialTheme.typography.labelLarge,
                        color = CardMutedText,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MacroBadge(stringResource(R.string.macro_protein_short), entry.protein, wash = Color(0xFFDCFCE7), tint = Color(0xFF15803D))
                    MacroBadge(stringResource(R.string.macro_carbs_short), entry.carbs, wash = Color(0xFFFEF9C3), tint = Color(0xFFA16207))
                    MacroBadge(stringResource(R.string.macro_fat_short), entry.fat, wash = Color(0xFFFFEDD5), tint = Color(0xFFC2410C))
                }
            }
        }
    }
}

@Composable
private fun MacroBadge(label: String, grams: Double?, wash: Color, tint: Color) {
    val value = grams?.let { if (it % 1.0 == 0.0) "${it.toInt()}" else String.format(Locale.getDefault(), "%.1f", it) } ?: "–"
    Surface(shape = RoundedCornerShape(50), color = wash) {
        Text(
            text = "$label $value g",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}

// ---------- 2. Ses / Not kartı ----------

@Composable
private fun VoiceNoteCard(
    entry: JournalEntryEntity,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    isTranscribing: Boolean,
    onTranscribeClick: () -> Unit,
    onDelete: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val look = entryLook(entry.entryType)
    val transcript = entry.content.trim().takeUnless { it.isBlank() || it == VOICE_PLACEHOLDER }

    LogioCard(modifier = modifier, onClick = onCardClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(look.icon, contentDescription = null, tint = look.tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.tile_voice),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = look.tint,
                modifier = Modifier.weight(1f)
            )
            DeleteChip(onDelete)
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (transcript != null) {
            Text(
                text = transcript,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = CardBodyText,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.voice_not_transcribed),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = CardMutedText
                )
                if (isTranscribing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = look.tint)
                    Text(stringResource(R.string.voice_transcribing), style = MaterialTheme.typography.labelMedium, color = look.tint)
                } else {
                    PillBadge(text = stringResource(R.string.voice_transcribe), wash = look.wash, tint = look.tint, onClick = onTranscribeClick)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(look.tint)
                    .clickable(onClick = onPlayClick)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            FakeWaveform(
                seed = entry.id,
                isPlaying = isPlaying,
                activeColor = look.tint,
                idleColor = look.wash,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            TimeLabel(entry.createdAt)
        }
    }
}

/** Kayda özel (id'den türetilen) sabit çubuklardan oluşan sahte dalga formu; çalarken hafifçe dalgalanır. */
@Composable
private fun FakeWaveform(
    seed: Long,
    isPlaying: Boolean,
    activeColor: Color,
    idleColor: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    val heights = remember(seed) {
        val random = kotlin.random.Random(seed)
        List(barCount) { 0.25f + random.nextFloat() * 0.75f }
    }
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing)),
        label = "waveformPhase"
    )
    val barColor by animateColorAsState(if (isPlaying) activeColor else activeColor.copy(alpha = 0.45f), label = "waveColor")

    Canvas(modifier = modifier) {
        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
        heights.forEachIndexed { i, base ->
            val factor = if (isPlaying) 0.65f + 0.35f * kotlin.math.sin(phase + i * 0.6f) else 1f
            val barHeight = (size.height * base * factor).coerceAtLeast(barWidth)
            val x = i * (barWidth + gap)
            val top = (size.height - barHeight) / 2
            drawRoundRect(
                color = if (i % 2 == 0) barColor else barColor.copy(alpha = barColor.alpha * 0.8f),
                topLeft = Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
            )
        }
        // Çalmıyorken taban çizgisi: kartın pastel tonunda ince hat
        if (!isPlaying) {
            drawLine(idleColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        }
    }
}

// ---------- 3. Sağlık & Aktivite kartı ----------

@Composable
internal fun HealthActivityCard(
    stepGoal: Int,
    healthSteps: Int?,
    onConnectHealthClick: () -> Unit,
    sleepHours: Double?,
    modifier: Modifier = Modifier
) {
    // Bugünkü adım Health Connect'ten eşitlenen kayıttan gelir (telefon sensörü kullanılmaz)
    val todaySteps = healthSteps ?: 0
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(14.dp)
        ) {
            // Sol yarı: adımlar
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccentBubble(Icons.AutoMirrored.Rounded.DirectionsWalk, tint = Color(0xFF16A34A), wash = Color(0xFFDCFCE7), size = 30.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.tile_steps), style = MaterialTheme.typography.labelLarge, color = CardMutedText)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = String.format(currentLocale(), "%,d", todaySteps),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MimoNavy,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (todaySteps.toFloat() / stepGoal).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = Color(0xFF4ADE80),
                    trackColor = Color(0xFFDCFCE7),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (healthSteps == null) {
                    // Bugün için Health Connect'ten adım gelmedi (izin yok ya da henüz eşitlenmedi)
                    Text(
                        stringResource(R.string.steps_connect_health),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MimoPurple,
                        modifier = Modifier.clickable(onClick = onConnectHealthClick)
                    )
                } else {
                    Text(stringResource(R.string.goal_value, String.format(currentLocale(), "%,d", stepGoal)), style = MaterialTheme.typography.labelSmall, color = CardMutedText)
                }
            }

            // Ortada ince ayraç
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(CardBorderColor)
            )

            // Sağ yarı: uyku
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccentBubble(Icons.Rounded.Bedtime, tint = Color(0xFF4F46E5), wash = Color(0xFFE0E7FF), size = 30.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_sleep), style = MaterialTheme.typography.labelLarge, color = CardMutedText)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = sleepHours?.let { formatSleepDuration(it) } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MimoNavy,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                val (qualityText, qualityWash, qualityTint) = sleepQuality(sleepHours)
                Surface(shape = RoundedCornerShape(50), color = qualityWash) {
                    Text(
                        text = stringResource(qualityText),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = qualityTint
                    )
                }
            }
        }
    }
}

@Composable
private fun formatSleepDuration(hours: Double): String {
    val totalMinutes = Math.round(hours * 60).toInt()
    return stringResource(R.string.duration_hm, totalMinutes / 60, totalMinutes % 60)
}

private fun sleepQuality(hours: Double?): Triple<Int, Color, Color> = when {
    hours == null -> Triple(R.string.sleep_no_record, Color(0xFFF1F0F7), CardMutedText)
    hours < 6 -> Triple(R.string.sleep_too_little, Color(0xFFFFE4E6), Color(0xFFE11D48))
    hours < 7 -> Triple(R.string.sleep_fair, Color(0xFFFEF9C3), Color(0xFFA16207))
    hours <= 9 -> Triple(R.string.sleep_good, Color(0xFFDCFCE7), Color(0xFF15803D))
    else -> Triple(R.string.sleep_long, Color(0xFFE0E7FF), Color(0xFF4F46E5))
}

// ---------- 4. Görev kartı ----------

private const val TODO_VISIBLE_LIMIT = 5

@Composable
private fun TodoCard(
    entry: JournalEntryEntity,
    onTodoToggle: (Int) -> Unit,
    onDelete: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val look = entryLook(entry.entryType)
    // Satır indeksleri ViewModel'deki toggleTodoItem ile aynı olmalı (content.split("\n"))
    val tasks = entry.content.split("\n")
        .mapIndexedNotNull { index, line -> if (line.length >= 3) index to line else null }
    val doneCount = tasks.count { it.second.startsWith("[x]") }

    LogioCard(modifier = modifier, onClick = onCardClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title.ifBlank { stringResource(R.string.action_todo) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MimoNavy
                )
                Text(
                    text = stringResource(R.string.todo_progress, doneCount, tasks.size, SimpleDateFormat("HH:mm", currentLocale()).format(Date(entry.createdAt))),
                    style = MaterialTheme.typography.labelMedium,
                    color = CardMutedText
                )
            }
            DeleteChip(onDelete)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tasks.take(TODO_VISIBLE_LIMIT).forEach { (lineIndex, line) ->
                TodoRow(
                    text = line.substring(3).trim(),
                    isChecked = line.startsWith("[x]"),
                    accent = look.tint,
                    onToggle = { onTodoToggle(lineIndex) }
                )
            }
            if (tasks.size > TODO_VISIBLE_LIMIT) {
                Text(
                    text = pluralStringResource(R.plurals.todo_more, tasks.size - TODO_VISIBLE_LIMIT, tasks.size - TODO_VISIBLE_LIMIT),
                    style = MaterialTheme.typography.labelMedium,
                    color = CardMutedText,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TodoRow(
    text: String,
    isChecked: Boolean,
    accent: Color,
    onToggle: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (isChecked) Color(0xFFEAF8F1) else Color(0xFFF7F4FF),
        animationSpec = tween(300),
        label = "todoBackground"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isChecked) accent else Color(0xFFB8B5D3),
        animationSpec = tween(300),
        label = "todoIconTint"
    )
    val textColor by animateColorAsState(
        targetValue = if (isChecked) CardMutedText else CardBodyText,
        animationSpec = tween(300),
        label = "todoTextColor"
    )
    // Tik atılınca ikon hafifçe büyüyüp yerine oturur
    val iconScale by animateFloatAsState(
        targetValue = if (isChecked) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "todoIconScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(targetState = isChecked, animationSpec = tween(250), label = "todoIcon") { checked ->
            Icon(
                imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (checked) stringResource(R.string.todo_done) else stringResource(R.string.todo_not_done),
                tint = iconTint,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------- 5. Paylaşılan link kartı ----------

@Composable
private fun SharedLinkCard(
    entry: JournalEntryEntity,
    onOpenLink: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val url = entry.content.trim()
    // Tür, paylaşım penceresiyle aynı kuralla başlık + linkten türetilir (şemada ayrı alan yok)
    val category = remember(entry.title, url) { classifySharedLink(entry.title, url) }
    val thumbnail = entry.imagePaths?.split(",")?.firstOrNull { it.startsWith("http") } ?: youtubeThumbnailUrl(url)

    LogioCard(modifier = modifier, onClick = onOpenLink) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkThumbnail(thumbnailUrl = thumbnail, category = category, size = 64)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category)
                    Spacer(modifier = Modifier.weight(1f))
                    TimeLabel(entry.createdAt)
                    Spacer(modifier = Modifier.width(6.dp))
                    DeleteChip(onDelete)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = entry.title.ifBlank { stringResource(R.string.action_link) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MimoNavy,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = CardMutedText, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = hostLabel(url),
                        style = MaterialTheme.typography.labelSmall,
                        color = CardMutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            category.chips.forEach { AiChip(it, compact = true) }
        }
    }
}

// ---------- Diğer kayıt türleri ----------

@Composable
private fun GenericEntryCard(
    entry: JournalEntryEntity,
    onDelete: () -> Unit,
    onVideoClick: () -> Unit,
    onLinkClick: () -> Unit,
    onLocationClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val look = entryLook(entry.entryType)
    val titleText = displayEntryTitle(entry)
    val contentText = displayEntryContent(entry)

    LogioCard(modifier = modifier, onClick = onCardClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeLabel(entry.createdAt)
            DeleteChip(onDelete)
        }

        val imagePaths = entry.imagePaths?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

        if (entry.entryType == EntryType.JOURNEY_VIDEO) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E1B4B))
                    .clickable { onVideoClick() },
                contentAlignment = Alignment.Center
            ) {
                if (imagePaths.isNotEmpty()) {
                    AsyncImage(
                        model = imagePaths.first(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.38f))
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = stringResource(R.string.play),
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = stringResource(R.string.journey_watch),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        } else if (imagePaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            if (imagePaths.size == 1) {
                AsyncImage(
                    model = imagePaths[0],
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (entry.entryType == EntryType.DRAW) Color.White else Color.Transparent),
                    contentScale = if (entry.entryType == EntryType.DRAW) ContentScale.Fit else ContentScale.Crop,
                    onError = { Timber.e("Resim yüklenemedi: ${entry.imagePaths}") }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    imagePaths.take(2).forEach { path ->
                        AsyncImage(
                            model = path,
                            contentDescription = null,
                            modifier = Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    if (imagePaths.size > 2) {
                        Box(
                            modifier = Modifier.size(120.dp).clip(RoundedCornerShape(14.dp)).background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+${imagePaths.size - 2}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (!titleText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MimoNavy
            )
        }

        if (!contentText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = contentText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5C6288),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        val chipText = when (entry.entryType) {
            EntryType.EXPENSE -> formatExpenseAmountLabel(entry.expenseAmount)?.let { "${ExpenseCategory.fromKey(entry.expenseCategory).emoji} $it" } ?: stringResource(R.string.action_expense)
            EntryType.WATER -> entry.waterAmountMl?.let { "💧 $it ml" }
            EntryType.DRAW -> "🎨 " + stringResource(R.string.action_draw)
            EntryType.FILM -> "🎬 " + stringResource(R.string.action_film)
            EntryType.BOOK -> "📚 " + stringResource(R.string.action_book)
            EntryType.MUSIC -> "🎵 " + stringResource(R.string.action_music)
            EntryType.LOCATION -> "📍 " + stringResource(R.string.action_location)
            EntryType.SLEEP -> entry.sleepHours?.let { "😴 ${formatSleepDuration(it)}" } ?: ("😴 " + stringResource(R.string.action_sleep))
            EntryType.COFFEE -> entry.coffeeCups?.let { "☕ " + pluralStringResource(R.plurals.cups_count, it, it) } ?: ("☕ " + stringResource(R.string.action_coffee))
            EntryType.ALCOHOL -> parseAlcoholDrinks(entry.alcoholUnits)?.let { "🍷 " + pluralStringResource(R.plurals.glasses_count, it.toInt(), it.toInt()) } ?: ("🍷 " + stringResource(R.string.action_alcohol))
            EntryType.PHOTO -> "📸 " + stringResource(R.string.journey_photos)
            EntryType.VIDEO -> "🎥 " + stringResource(R.string.action_video)
            EntryType.LINK -> "🔗 " + stringResource(R.string.action_link)
            EntryType.JOURNEY_VIDEO -> "✨ " + stringResource(R.string.journey_title)
            else -> null
        }

        val onClickAction: () -> Unit = when (entry.entryType) {
            EntryType.VIDEO, EntryType.JOURNEY_VIDEO -> onVideoClick
            EntryType.LINK -> onLinkClick
            EntryType.LOCATION -> onLocationClick
            else -> { {} }
        }

        val itemIcon = when (entry.entryType) {
            EntryType.VIDEO, EntryType.JOURNEY_VIDEO -> Icons.Default.PlayCircle
            EntryType.LINK -> Icons.AutoMirrored.Filled.OpenInNew
            EntryType.LOCATION -> Icons.Default.LocationOn
            else -> null
        }

        if (chipText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            PillBadge(
                text = chipText,
                wash = look.wash,
                tint = look.tint,
                icon = itemIcon,
                onClick = onClickAction
            )
        }
    }
}

@Composable
internal fun WaterMiniCard(
    todayWaterTotal: Int,
    waterTarget: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        SoftStatCard(modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp)) {
            AccentBubble(
                icon = Icons.Rounded.WaterDrop,
                tint = Color(0xFF0284C7),
                wash = Color(0xFFE0F2FE),
                size = 36.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.action_water),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF7A7FA8)
            )
            Text(
                text = "$todayWaterTotal ml",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MimoNavy,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (todayWaterTotal.toFloat() / waterTarget).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Color(0xFF7EC8FF),
                trackColor = Color(0xFFE0F2FE),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
            if (todayWaterTotal >= waterTarget) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.water_goal_done), style = MaterialTheme.typography.labelSmall, color = Color(0xFF16A34A))
            }
        }
        Image(
            painter = painterResource(id = R.drawable.mascot_water),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(46.dp)
                .offset(x = 6.dp, y = (-6).dp)
        )
    }
}

@Composable
private fun SoftStatCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun AccentBubble(
    icon: ImageVector,
    tint: Color,
    wash: Color,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(wash)
            .border(1.dp, Color.White, RoundedCornerShape(16.dp))
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun PillBadge(
    text: String,
    wash: Color,
    tint: Color,
    icon: ImageVector? = null,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = wash,
        contentColor = tint
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1
            )
        }
    }
}

