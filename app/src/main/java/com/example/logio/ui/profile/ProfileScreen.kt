/*
 * ProfileScreen: Profil sekmesinin arayüzü.
 *
 * Başlık kartı (isim, seri), istatistik ızgarası, rozetler, en çok kullanılan türler,
 * "Geçmişten bir anı" kartı ve ayarlar (dil, uygulama kilidi) burada çizilir.
 * Hesaplar ProfileViewModel'de (ProfileStats.kt) yapılır; bu dosya yalnızca gösterir.
 */
package com.example.logio.ui.profile

import com.example.logio.ui.theme.logioFieldColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.logio.BuildConfig
import com.example.logio.R
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.logio.ui.timeline.entryTypeLabel
import com.example.logio.util.AppLanguage
import com.example.logio.util.AppLock
import androidx.fragment.app.FragmentActivity
import android.widget.Toast
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.logio.util.AppLocale
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.profile.UserProfile
import com.example.logio.ui.theme.MimoBackground
import com.example.logio.ui.theme.MimoCloud
import com.example.logio.ui.theme.MimoLavender
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoNavyMuted
import com.example.logio.ui.theme.MimoPink
import com.example.logio.ui.theme.MimoPurple
import coil.compose.AsyncImage
import com.example.logio.ui.timeline.EntryDetailSheet
import com.example.logio.ui.timeline.displayEntryContent
import com.example.logio.ui.timeline.displayEntryTitle
import com.example.logio.ui.timeline.entryLook
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// ---------- Sabitler ve renkler ----------

private val TrLocale: Locale get() = Locale.getDefault()
private val CardBorder = Color(0xFFEDE8F7)
private val InkPrimary = MimoNavy
private val InkSecondary = MimoNavyMuted
private val InkMuted = Color(0xFF8A90B4)

private fun formatNumber(value: Int): String = String.format(TrLocale, "%,d", value)

// ---------- Ekran ----------

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    bottomBar: @Composable () -> Unit = {}
) {
    val entries by viewModel.entries.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val profileSummary by viewModel.summary.collectAsState()

    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var showEditDialog by remember { mutableStateOf(false) }
    var detailEntry by remember { mutableStateOf<JournalEntryEntity?>(null) }
    val context = LocalContext.current
    // Kilit ayarındaki yazılar çizim sırasında alınır; dil değişince yenileriyle güncellenir
    val lockUnavailableText = stringResource(R.string.lock_unavailable)
    val lockPromptTitle = stringResource(R.string.lock_prompt_title)
    val lockPromptEnable = stringResource(R.string.lock_prompt_enable)
    val lockPromptDisable = stringResource(R.string.lock_prompt_disable)
    val language = remember { AppLocale.current(context) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var lockEnabled by remember { mutableStateOf(AppLock.isEnabled(context)) }

    MimoBackground {
        Scaffold(containerColor = Color.Transparent, bottomBar = bottomBar) { innerPadding ->
            // İlk hesap arka planda sürerken yalnızca alt menü görünür
            val summary = profileSummary ?: return@Scaffold
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.tab_profile),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                ProfileHeaderCard(
                    name = profile.name,
                    firstDay = summary.firstDay,
                    today = today,
                    currentStreak = summary.currentStreak,
                    onEditClick = { showEditDialog = true }
                )
                Spacer(modifier = Modifier.height(16.dp))

                StatsGrid(summary)
                Spacer(modifier = Modifier.height(20.dp))

                SectionTitle(stringResource(R.string.profile_badges), stringResource(R.string.profile_badges_earned, summary.badges.count { it.unlocked }, summary.badges.size))
                BadgeCheerCard(summary.badges)
                Spacer(modifier = Modifier.height(10.dp))
                BadgeGrid(summary.badges)
                Spacer(modifier = Modifier.height(20.dp))

                SectionTitle(stringResource(R.string.profile_top_types), stringResource(R.string.profile_all_time))
                TopTypesCard(summary.topTypes)
                Spacer(modifier = Modifier.height(20.dp))

                SectionTitle(stringResource(R.string.profile_memory), stringResource(R.string.profile_memory_desc))
                MemoryCard(
                    entries = entries,
                    today = today,
                    zone = zone,
                    onOpen = { detailEntry = it }
                )
                Spacer(modifier = Modifier.height(20.dp))

                SectionTitle(stringResource(R.string.profile_settings), stringResource(R.string.profile_settings_desc))
                LanguageRow(current = language, onClick = { showLanguageDialog = true })
                Spacer(modifier = Modifier.height(10.dp))
                AppLockRow(
                    enabled = lockEnabled,
                    onToggle = { wanted ->
                        val activity = context.findActivity() as? FragmentActivity ?: return@AppLockRow
                        if (!AppLock.isAvailable(context)) {
                            Toast.makeText(context, lockUnavailableText, Toast.LENGTH_LONG).show()
                            return@AppLockRow
                        }
                        // Açarken de kapatırken de doğrulama istenir: ayarı sadece telefonun sahibi değiştirebilsin
                        AppLock.authenticate(
                            activity = activity,
                            title = lockPromptTitle,
                            subtitle = if (wanted) lockPromptEnable else lockPromptDisable,
                            onSuccess = {
                                AppLock.setEnabled(context, wanted)
                                lockEnabled = wanted
                            },
                            onError = { message -> message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.profile_footer, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { showEditDialog = false },
            onSave = {
                viewModel.updateProfile(it)
                showEditDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            current = language,
            onDismiss = { showLanguageDialog = false },
            onSelect = { selected ->
                showLanguageDialog = false
                // Dil değişince aktivite yeniden oluşturulur ve tüm ekran yeni dille çizilir
                if (selected != language) context.findActivity()?.let { AppLocale.set(it, selected) }
            }
        )
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

// ---------- 1. Üst kart ----------

@Composable
private fun ProfileHeaderCard(
    name: String,
    firstDay: LocalDate?,
    today: LocalDate,
    currentStreak: Int,
    onEditClick: () -> Unit
) {
    val sinceFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(TrLocale) }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(Color(0xFFEDE7FF), Color(0xFFFFE9F1))))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mascot_profile),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(62.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (firstDay == null) {
                        stringResource(R.string.profile_journey_starts)
                    } else {
                        val days = ChronoUnit.DAYS.between(firstDay, today) + 1
                        pluralStringResource(R.plurals.profile_since, days.toInt(), firstDay.format(sinceFormat), days.toInt())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(50), color = Color.White) {
                    Text(
                        text = if (currentStreak > 0) pluralStringResource(R.plurals.profile_streak, currentStreak, currentStreak) else stringResource(R.string.profile_streak_start),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (currentStreak > 0) Color(0xFFEA580C) else InkSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            IconButton(onClick = onEditClick) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.profile_edit), tint = MimoPurple)
            }
        }
    }
}

// ---------- 2. Sayılar ----------

@Composable
private fun StatsGrid(summary: ProfileSummary) {
    val tiles = listOf(
        Triple("📝", formatNumber(summary.totalEntries), stringResource(R.string.profile_total_memories)),
        Triple("📅", formatNumber(summary.activeDays), stringResource(R.string.profile_active_days)),
        Triple("🏅", pluralStringResource(R.plurals.days_count, summary.longestStreak, summary.longestStreak), stringResource(R.string.profile_longest_streak)),
        Triple("📸", formatNumber(summary.photoCount), stringResource(R.string.action_photo)),
        Triple("💧", String.format(TrLocale, "%.1f L", summary.totalWaterMl / 1000.0), stringResource(R.string.profile_total_water)),
        Triple("🔥", pluralStringResource(R.plurals.days_count, summary.currentStreak, summary.currentStreak), stringResource(R.string.profile_current_streak))
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (emoji, value, label) ->
                    StatTile(emoji, value, label, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatTile(emoji: String, value: String, label: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = InkPrimary,
                maxLines = 1
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = InkSecondary, maxLines = 1)
        }
    }
}

// ---------- 3. Rozetler ----------

@Composable
private fun SectionTitle(title: String, hint: String) {
    Column(modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = InkPrimary)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
    }
}

/** Rozet ızgarasının üstündeki maskotlu kart: kazanılanları kutlar, sıradaki rozeti gösterir. */
@Composable
private fun BadgeCheerCard(badges: List<Badge>) {
    val unlocked = badges.count { it.unlocked }
    val next = badges.filterNot { it.unlocked }.maxByOrNull { it.current.toFloat() / it.target }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFF7E0),
        border = BorderStroke(1.dp, Color(0xFFFFE3A3)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Image(
                painter = painterResource(if (unlocked > 0) R.drawable.mascot_celebrate else R.drawable.mascot_main),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(76.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (unlocked > 0) R.string.stats_cheer_good else R.string.profile_badge_first),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (next == null) stringResource(R.string.profile_badge_all_done)
                    else stringResource(R.string.profile_badge_next, "${next.emoji} ${stringResource(next.title)}", next.current.coerceAtMost(next.target), next.target),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
        }
    }
}

@Composable
private fun BadgeGrid(badges: List<Badge>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        badges.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { badge -> BadgeTile(badge, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BadgeTile(badge: Badge, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (badge.unlocked) Color(0xFFFFF7E0) else Color.White.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, if (badge.unlocked) Color(0xFFFFE3A3) else CardBorder)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)
        ) {
            Text(
                text = badge.emoji,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.alpha(if (badge.unlocked) 1f else 0.35f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(badge.title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (badge.unlocked) InkPrimary else InkMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (badge.unlocked) {
                Text(stringResource(R.string.profile_badge_earned), style = MaterialTheme.typography.labelSmall, color = Color(0xFFB7791F))
            } else {
                LinearProgressIndicator(
                    progress = { (badge.current.toFloat() / badge.target).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape),
                    color = MimoLavender,
                    trackColor = MimoCloud,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${badge.current.coerceAtMost(badge.target)}/${badge.target}",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkMuted
                )
            }
        }
    }
}

// ---------- 4. En çok kullanılan türler ----------

@Composable
private fun TopTypesCard(topTypes: List<Pair<EntryType, Int>>) {
    WhiteCard {
        if (topTypes.isEmpty()) {
            Text(
                text = stringResource(R.string.profile_top_types_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
            return@WhiteCard
        }
        val max = topTypes.first().second.coerceAtLeast(1)
        topTypes.forEachIndexed { index, (type, count) ->
            if (index > 0) Spacer(modifier = Modifier.height(12.dp))
            val look = entryLook(type)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(look.wash)
                ) {
                    Icon(look.icon, contentDescription = null, tint = look.tint, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        Text(
                            text = stringResource(entryTypeLabel(type)),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = InkPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(formatNumber(count), style = MaterialTheme.typography.labelLarge, color = InkSecondary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { count.toFloat() / max },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = look.tint.copy(alpha = 0.8f),
                        trackColor = look.wash,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}

// ---------- 5. Geçmişten bir anı ----------

// Ölçüm niteliğindeki kayıtlar (adım, su, uyku...) "anı" sayılmaz
private val MemoryTypes = setOf(
    EntryType.TEXT, EntryType.MOOD, EntryType.MEAL, EntryType.LOCATION, EntryType.VOICE,
    EntryType.PHOTO, EntryType.DRAW, EntryType.FILM, EntryType.BOOK, EntryType.MUSIC,
    EntryType.VIDEO, EntryType.LINK, EntryType.JOURNEY_VIDEO
)

@Composable
private fun relativeDayLabel(day: LocalDate, today: LocalDate): String {
    val months = ChronoUnit.MONTHS.between(day, today).toInt()
    val days = ChronoUnit.DAYS.between(day, today).toInt()
    return when {
        day.dayOfMonth == today.dayOfMonth && months >= 12 && months % 12 == 0 ->
            pluralStringResource(R.plurals.years_ago_today, months / 12, months / 12)
        day.dayOfMonth == today.dayOfMonth && months >= 1 -> pluralStringResource(R.plurals.months_ago_today, months, months)
        days == 1 -> stringResource(R.string.yesterday)
        days < 7 -> pluralStringResource(R.plurals.days_ago, days, days)
        days < 30 -> pluralStringResource(R.plurals.weeks_ago, days / 7, days / 7)
        months < 12 -> pluralStringResource(R.plurals.months_ago, months, months)
        else -> pluralStringResource(R.plurals.years_ago, months / 12, months / 12)
    }
}

@Composable
private fun MemoryCard(
    entries: List<JournalEntryEntity>,
    today: LocalDate,
    zone: ZoneId,
    onOpen: (JournalEntryEntity) -> Unit
) {
    fun dayOf(entry: JournalEntryEntity) = Instant.ofEpochMilli(entry.createdAt).atZone(zone).toLocalDate()

    // Bugünden önceki anılar; önce "geçen ay/yıl bugün" olanlar, yoksa hepsinden karışık
    val candidates = remember(entries, today) {
        val past = entries.filter { it.entryType in MemoryTypes && dayOf(it).isBefore(today) }
        past.filter { dayOf(it).dayOfMonth == today.dayOfMonth }.shuffled() +
                past.filter { dayOf(it).dayOfMonth != today.dayOfMonth }.shuffled()
    }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val memory = candidates.getOrNull(index)

    WhiteCard(modifier = if (memory != null) Modifier.clickable { onOpen(memory) } else Modifier) {
        if (memory == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.mascot_empty),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.profile_memory_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            }
            return@WhiteCard
        }

        val look = entryLook(memory.entryType)
        val imagePath = memory.imagePaths?.split(",")?.firstOrNull { it.isNotBlank() }
        val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(TrLocale) }
        val day = dayOf(memory)

        Surface(shape = RoundedCornerShape(50), color = MimoCloud) {
            Text(
                text = "✨ ${relativeDayLabel(day, today)} · ${day.format(dateFormat)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MimoPurple,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(look.wash)
            ) {
                if (imagePath != null) {
                    AsyncImage(
                        model = imagePath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(look.icon, contentDescription = null, tint = look.tint, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayEntryTitle(memory) ?: stringResource(entryTypeLabel(memory.entryType)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                displayEntryContent(memory)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (candidates.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = { index = (index + 1) % candidates.size },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = MimoPurple, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.profile_another_memory), color = MimoPurple, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = modifier.padding(16.dp), content = content)
    }
}

// ---------- 6. Ayarlar: dil ----------

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun languageName(language: AppLanguage): String =
    if (language == AppLanguage.SYSTEM) stringResource(R.string.language_system) else language.nativeName

@Composable
private fun LanguageRow(current: AppLanguage, onClick: () -> Unit) {
    WhiteCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MimoCloud)
            ) {
                Icon(Icons.Rounded.Language, contentDescription = null, tint = MimoPurple, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(languageName(current), style = MaterialTheme.typography.labelLarge, color = InkSecondary)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = InkMuted)
        }
    }
}

@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(stringResource(R.string.language), fontWeight = FontWeight.Bold, color = InkPrimary) },
        text = {
            Column {
                AppLanguage.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(option) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = option == current,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = MimoPurple)
                        )
                        Text(languageName(option), style = MaterialTheme.typography.bodyLarge, color = InkPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = InkSecondary) }
        }
    )
}

// ---------- Düzenleme penceresi ----------

@Composable
private fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var waterGoal by remember { mutableFloatStateOf(profile.waterGoalMl.toFloat()) }
    var stepGoal by remember { mutableFloatStateOf(profile.stepGoal.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(stringResource(R.string.profile_edit), fontWeight = FontWeight.Bold, color = InkPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    colors = logioFieldColors(),
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text(stringResource(R.string.profile_your_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))
                GoalSliderLabel(stringResource(R.string.profile_daily_water), String.format(TrLocale, "%.2f L", waterGoal / 1000.0))
                Slider(
                    value = waterGoal,
                    onValueChange = { waterGoal = it },
                    valueRange = 1_000f..4_000f,
                    steps = 11, // 250 ml adımlar
                    colors = SliderDefaults.colors(thumbColor = MimoPurple, activeTrackColor = MimoPurple)
                )
                GoalSliderLabel(stringResource(R.string.profile_daily_steps), formatNumber(stepGoal.toInt()))
                Slider(
                    value = stepGoal,
                    onValueChange = { stepGoal = it },
                    valueRange = 2_000f..20_000f,
                    steps = 17, // 1.000 adımlık adımlar
                    colors = SliderDefaults.colors(thumbColor = MimoPink, activeTrackColor = MimoPink)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        profile.copy(
                            name = name,
                            waterGoalMl = (Math.round(waterGoal / 250f) * 250),
                            stepGoal = (Math.round(stepGoal / 1000f) * 1000)
                        )
                    )
                }
            ) { Text(stringResource(R.string.save), color = MimoPurple, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = InkSecondary) }
        }
    )
}

@Composable
private fun GoalSliderLabel(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = InkSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = InkPrimary)
    }
}

@Composable
private fun AppLockRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    WhiteCard(modifier = Modifier.clickable { onToggle(!enabled) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MimoCloud)
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MimoPurple, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.lock_setting),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = InkPrimary
                )
                Text(
                    text = stringResource(R.string.lock_setting_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = MimoPurple, checkedThumbColor = Color.White)
            )
        }
    }
}
