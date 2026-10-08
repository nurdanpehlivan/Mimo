package com.example.logio.ui.timeline.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.logio.R
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.ui.theme.MimoBackground
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoPurple
import com.example.logio.util.JourneyTemplate
import com.example.logio.util.JourneyVideoMaker
import java.time.LocalDate
import java.time.ZoneId

private val MutedText = Color(0xFF7A7FA8)
private const val MAX_MANUAL_PHOTOS = 40

/** Şablonun tarih aralığını yerel gün başlangıçları (ms) olarak döndürür. */
private fun JourneyTemplate.dateRange(today: LocalDate = LocalDate.now()): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val end = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val start = daysBack?.let { today.minusDays(it - 1L).atStartOfDay(zone).toInstant().toEpochMilli() } ?: 0L
    return start to end
}

private enum class JourneyMode(@StringRes val label: Int) {
    AUTO(R.string.journey_mode_auto),
    MANUAL(R.string.journey_mode_manual)
}

/** Seçilebilir fotoğraf: Logio'daki bir dosya ya da galeriden seçilen bir içerik adresi. */
private data class PhotoSource(val key: String, val model: Any, val createdAt: Long)

/**
 * Yolculuk videosu tam ekran sayfası. İki yol:
 * - Otomatik: şablon seç, tarih aralığı ve müzik şablondan gelir.
 * - Kendim seçeyim: Logio'daki fotoğraflardan ve/veya galeriden istediklerini, istediğin sırayla seç.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyVideoDialog(
    entries: List<JournalEntryEntity>,
    onDismiss: () -> Unit,
    onGenerate: (title: String, startDate: Long, endDate: Long, theme: String) -> Unit,
    onGenerateFromPhotos: (title: String, photos: List<String>, theme: String) -> Unit
) {
    var mode by remember { mutableStateOf(JourneyMode.AUTO) }
    var selectedTemplate by remember { mutableStateOf(JourneyTemplate.WEEKLY_SUMMARY) }
    val templateTitle = stringResource(selectedTemplate.label)
    val manualTitle = stringResource(R.string.journey_manual_title)

    // Logio'daki fotoğraflar (en yeni üstte), diskte gerçekten var olanlar
    val journalPhotos = remember(entries) {
        entries
            .filter { !it.imagePaths.isNullOrBlank() }
            .sortedByDescending { it.createdAt }
            .flatMap { entry ->
                JourneyVideoMaker.existingPhotoFiles(entry.imagePaths.orEmpty().split(","))
                    .map { PhotoSource(it.absolutePath, it, entry.createdAt) }
            }
            .distinctBy { it.key }
    }
    var galleryPhotos by remember { mutableStateOf<List<PhotoSource>>(emptyList()) }
    // Seçim sırası = videodaki sıra
    var selectedKeys by remember { mutableStateOf<List<String>>(emptyList()) }

    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_MANUAL_PHOTOS)
    ) { uris: List<Uri> ->
        val picked = uris.map { PhotoSource(it.toString(), it, System.currentTimeMillis()) }
            .filter { p -> galleryPhotos.none { it.key == p.key } }
        galleryPhotos = picked + galleryPhotos
        selectedKeys = (selectedKeys + picked.map { it.key }).distinct().take(MAX_MANUAL_PHOTOS)
    }

    val photoCountByTemplate = remember(journalPhotos) {
        JourneyTemplate.entries.associateWith { template ->
            val (start, end) = template.dateRange()
            val endOfDay = end + 24 * 60 * 60 * 1000L - 1
            journalPhotos.count { it.createdAt in start..endOfDay }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        MimoBackground {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(stringResource(R.string.journey_title), fontWeight = FontWeight.Bold, color = MimoNavy)
                                Text(stringResource(R.string.journey_subtitle), style = MaterialTheme.typography.bodySmall, color = MutedText)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back), tint = MimoNavy)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                },
                bottomBar = {
                    val canGenerate = when (mode) {
                        JourneyMode.AUTO -> (photoCountByTemplate[selectedTemplate] ?: 0) > 0
                        JourneyMode.MANUAL -> selectedKeys.isNotEmpty()
                    }
                    Surface(color = Color.White, shadowElevation = 8.dp) {
                        Button(
                            onClick = {
                                when (mode) {
                                    JourneyMode.AUTO -> {
                                        val (start, end) = selectedTemplate.dateRange()
                                        onGenerate(templateTitle, start, end, selectedTemplate.name)
                                    }
                                    JourneyMode.MANUAL ->
                                        onGenerateFromPhotos(manualTitle, selectedKeys, selectedTemplate.name)
                                }
                            },
                            enabled = canGenerate,
                            colors = ButtonDefaults.buttonColors(containerColor = MimoPurple),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(16.dp)
                                .height(52.dp)
                        ) {
                            Text(
                                text = when {
                                    mode == JourneyMode.MANUAL && selectedKeys.isNotEmpty() -> stringResource(R.string.journey_create_count, selectedKeys.size)
                                    mode == JourneyMode.MANUAL -> stringResource(R.string.journey_pick_one)
                                    canGenerate -> stringResource(R.string.journey_create)
                                    else -> stringResource(R.string.journey_no_photos_range)
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            ) { innerPadding ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    fullWidth("mode") {
                        ModeSelector(selected = mode, onSelect = { mode = it })
                    }

                    when (mode) {
                        JourneyMode.AUTO -> {
                            JourneyTemplate.entries.forEach { template ->
                                fullWidth("template-${template.name}") {
                                    TemplateCard(
                                        template = template,
                                        photoCount = photoCountByTemplate[template] ?: 0,
                                        selected = template == selectedTemplate,
                                        onSelect = { selectedTemplate = template }
                                    )
                                }
                            }
                        }

                        JourneyMode.MANUAL -> {
                            fullWidth("music") {
                                MusicSelector(selected = selectedTemplate, onSelect = { selectedTemplate = it })
                            }
                            fullWidth("photos-header") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.journey_photos), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MimoNavy)
                                        Text(
                                            text = if (selectedKeys.isEmpty()) stringResource(R.string.journey_order_hint) else stringResource(R.string.journey_selected, selectedKeys.size, MAX_MANUAL_PHOTOS),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MutedText
                                        )
                                    }
                                    if (selectedKeys.isNotEmpty()) {
                                        TextButton(onClick = { selectedKeys = emptyList() }) { Text(stringResource(R.string.journey_clear), color = MutedText) }
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.journey_gallery))
                                    }
                                }
                            }

                            val allPhotos = galleryPhotos + journalPhotos
                            if (allPhotos.isEmpty()) {
                                fullWidth("empty") { EmptyPhotosHint() }
                            } else {
                                items(allPhotos, key = { it.key }) { photo ->
                                    val order = selectedKeys.indexOf(photo.key)
                                    PhotoTile(
                                        photo = photo,
                                        order = order.takeIf { it >= 0 }?.plus(1),
                                        onClick = {
                                            selectedKeys = if (order >= 0) {
                                                selectedKeys - photo.key
                                            } else if (selectedKeys.size < MAX_MANUAL_PHOTOS) {
                                                selectedKeys + photo.key
                                            } else {
                                                selectedKeys
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullWidth(
    key: String,
    content: @Composable () -> Unit
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun ModeSelector(selected: JourneyMode, onSelect: (JourneyMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.8f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        JourneyMode.entries.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                if (isSelected) MimoPurple.copy(alpha = 0.14f) else Color.Transparent,
                label = "modeBackground"
            )
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .clickable { onSelect(option) }
                    .padding(vertical = 10.dp)
            ) {
                Icon(
                    imageVector = if (option == JourneyMode.AUTO) Icons.Rounded.AutoAwesome else Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = if (isSelected) MimoPurple else MutedText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(option.label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MimoPurple else MutedText
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: JourneyTemplate,
    photoCount: Int,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val background by animateColorAsState(if (selected) Color(0xFFF4EEFF) else Color.White, label = "templateBg")
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = background,
        border = BorderStroke(1.5.dp, if (selected) Color(0xFFCFC2FF) else Color(0xFFEDE8F7)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8F7FB))
            ) {
                Text(template.emoji, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(template.label), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MimoNavy)
                Text(stringResource(template.description), style = MaterialTheme.typography.bodySmall, color = MutedText)
                Text(
                    text = if (photoCount > 0) "📸 " + pluralStringResource(R.plurals.photo_count, photoCount, photoCount) else stringResource(R.string.journey_no_photos_range),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (photoCount > 0) MimoPurple else MutedText
                )
            }
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = MimoPurple)
            )
        }
    }
}

// Manuel modda tarih aralığı yok; şablon yalnızca müziği belirler
@StringRes
private fun JourneyTemplate.musicLabel(): Int = when (this) {
    JourneyTemplate.WEEKLY_SUMMARY -> R.string.journey_music_cheerful
    JourneyTemplate.TRAVEL_MOMENTS -> R.string.journey_music_cinematic
    JourneyTemplate.BEST_MOMENTS -> R.string.journey_music_nostalgic
}

@Composable
private fun MusicSelector(selected: JourneyTemplate, onSelect: (JourneyTemplate) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(stringResource(R.string.action_music), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MimoNavy)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JourneyTemplate.entries.forEach { template ->
                FilterChip(
                    selected = template == selected,
                    onClick = { onSelect(template) },
                    label = { Text(stringResource(template.musicLabel())) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White,
                        selectedContainerColor = MimoPurple.copy(alpha = 0.14f),
                        selectedLabelColor = MimoPurple
                    )
                )
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: PhotoSource, order: Int?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .then(if (order != null) Modifier.border(3.dp, MimoPurple, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = photo.model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFEDE8F7))
        )
        if (order != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MimoPurple.copy(alpha = 0.18f))
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (order != null) MimoPurple else Color.Black.copy(alpha = 0.25f))
                .border(1.5.dp, Color.White, CircleShape)
        ) {
            if (order != null) {
                Text("$order", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyPhotosHint() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.mascot_journey),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(110.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.journey_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            textAlign = TextAlign.Center
        )
    }
}
