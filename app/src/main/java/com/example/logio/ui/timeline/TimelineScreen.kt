package com.example.logio.ui.timeline

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.steps.StepCounterUiState
import timber.log.Timber
import java.util.*
import java.text.SimpleDateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val playingFilePath by viewModel.playingFilePath.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val stepCounterUiState by viewModel.stepCounterUiState.collectAsState()
    
    val movieSearchResults by viewModel.movieSearchResults.collectAsState()
    val bookSearchResults by viewModel.bookSearchResults.collectAsState()
    val musicSearchResults by viewModel.musicSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val locationSearchResults by viewModel.locationSearchResults.collectAsState()

    val context = LocalContext.current

    val todayDateLabel = formatTodayLabel()
    val todayWaterTotal = getWaterTotalForDay(entries, System.currentTimeMillis())
    val waterTarget = 2000

    val needsActivityRecognitionPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var hasActivityRecognitionPermission by remember {
        mutableStateOf(
            !needsActivityRecognitionPermission ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showQuickMenu by remember { mutableStateOf(false) }
    var activeDialogAction by remember { mutableStateOf<DialogQuickAction?>(null) }
    
    var selectedPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showPhotoNoteDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                selectedPhotoUris = uris
                showPhotoNoteDialog = true
            }
        }
    )

    val mealPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) viewModel.analyzeAndSaveMeal(context, uri)
        }
    )

    val activityPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasActivityRecognitionPermission = granted }
    )

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playbackError) {
        if (!playbackError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(playbackError!!)
            viewModel.clearPlaybackError()
        }
    }

    LaunchedEffect(hasActivityRecognitionPermission, stepCounterUiState.isSensorAvailable) {
        if (!stepCounterUiState.isSensorAvailable) {
            viewModel.stopStepTracking()
        } else if (!needsActivityRecognitionPermission || hasActivityRecognitionPermission) {
            viewModel.startStepTracking()
        } else {
            viewModel.stopStepTracking()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logio", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickMenu = true },
                containerColor = Color(0xFF22C55E),
                contentColor = Color.White,
                shape = CircleShape
            ) { Icon(Icons.Default.Add, contentDescription = "Ekle") }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                StepCounterCard(
                    uiState = stepCounterUiState,
                    hasActivityRecognitionPermission = hasActivityRecognitionPermission,
                    onEnableTrackingClick = { 
                        if (needsActivityRecognitionPermission) {
                            activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = todayDateLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text(text = "$todayWaterTotal / $waterTarget ml", style = MaterialTheme.typography.labelMedium)
                        }
                        LinearProgressIndicator(
                            progress = { (todayWaterTotal.toFloat() / waterTarget).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = Color(0xFF0284C7),
                            trackColor = Color(0xFFE0F2FE)
                        )
                        if (todayWaterTotal >= waterTarget) {
                            Text(text = "Su hedefi tamamlandı! 💧✨", style = MaterialTheme.typography.bodySmall, color = Color(0xFF16A34A))
                        }
                    }
                }

                if (entries.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(text = "Henüz bir kayıt yok.\nİlk anını eklemek için +'ya bas!", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            TimelineItemCard(
                                entry = entry,
                                isVoicePlaying = entry.audioPath != null && entry.audioPath == playingFilePath,
                                onVoicePlaybackClick = { viewModel.onVoicePlaybackClick(entry) },
                                onDelete = { viewModel.deleteEntry(entry) },
                                onTodoToggle = { index -> viewModel.toggleTodoItem(entry, index) },
                                onVideoClick = { viewModel.openVideo(context, entry) },
                                onLinkClick = { viewModel.openLink(context, entry) },
                                onLocationClick = { viewModel.openMap(context, entry) }
                            )
                        }
                    }
                }
            }

            if (isAnalyzing) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(color = Color(0xFF22C55E))
                                Text("Yemek analiz ediliyor...", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        if (showQuickMenu) {
            QuickAddBottomSheet(
                onDismissRequest = { showQuickMenu = false },
                onActionClick = { action ->
                    showQuickMenu = false
                    val dialogAction = action.toDialogQuickActionOrNull()
                    if (dialogAction != null) {
                        activeDialogAction = dialogAction
                    } else {
                        when (action) {
                            QuickLogAction.PHOTO -> {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            QuickLogAction.MEAL -> {
                                mealPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            else -> Unit
                        }
                    }
                }
            )
        }

        if (showPhotoNoteDialog) {
            PhotoNoteDialog(
                uris = selectedPhotoUris,
                onDismiss = { showPhotoNoteDialog = false },
                onConfirm = { note ->
                    viewModel.addPhotoEntry(context, selectedPhotoUris, note)
                    showPhotoNoteDialog = false
                }
            )
        }

        QuickActionDialogHost(
            activeDialogAction = activeDialogAction,
            onDismiss = { activeDialogAction = null },
            onAddNote = { t, n -> viewModel.addNote(t, n) },
            onAddMood = { e, r -> viewModel.addMood(e, r) },
            isRecording = isRecording,
            onStartRecording = { viewModel.startVoiceRecording(it) },
            onStopRecording = { viewModel.stopVoiceRecording() },
            onSaveVoice = { p, d -> viewModel.saveVoiceEntry(p, durationMs = d) },
            onAddExpense = { t, a, c -> viewModel.saveExpenseEntry(a, t, c) },
            onAddWater = { viewModel.saveWaterEntry(it) },
            onAddLocation = { lat, lng, title, note -> viewModel.saveMapLocation(lat, lng, title, note) },
            onAddTodo = { t, tasks -> viewModel.saveTodoEntry(t, tasks) },
            onAddDraw = { viewModel.saveDrawEntry(it) },
            onAddSleep = { h, n -> viewModel.saveSleepEntry(h, n) },
            onAddCoffee = { c, t -> viewModel.saveCoffeeEntry(c, t) },
            onAddAlcohol = { a, t -> viewModel.saveAlcoholEntry(a, t) },
            onAddVideo = { uri, note -> viewModel.saveVideoEntry(uri.toString(), note) },
            onAddLink = { t, u -> viewModel.saveLinkEntry(t, u) },
            onSearchMovies = { viewModel.searchMovies(it) },
            movieSearchResults = movieSearchResults,
            onSearchBooks = { viewModel.searchBooks(it) },
            bookSearchResults = bookSearchResults,
            onSearchMusic = { viewModel.searchMusic(it) },
            musicSearchResults = musicSearchResults,
            onSearchLocations = { viewModel.searchLocations(it) },
            locationSearchResults = locationSearchResults,
            onSaveLocation = { place, note -> viewModel.saveSelectedLocation(place, note) },
            isSearching = isSearching,
            onSaveFilm = { s, n -> viewModel.saveFilmEntry(s, n) },
            onSaveBook = { b, n -> viewModel.saveBookEntry(b, n) },
            onSaveMusic = { m, n -> viewModel.saveMusicEntry(m, n) },
            currentDayWaterTotal = todayWaterTotal
        )
    }
}

@Composable
fun TimelineItemCard(
    entry: JournalEntryEntity,
    isVoicePlaying: Boolean,
    onVoicePlaybackClick: () -> Unit,
    onDelete: () -> Unit,
    onTodoToggle: (Int) -> Unit,
    onVideoClick: () -> Unit,
    onLinkClick: () -> Unit,
    onLocationClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("tr")) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = dateFormat.format(Date(entry.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(text = timeFormat.format(Date(entry.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }

            val imagePaths = entry.imagePaths?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            if (imagePaths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (imagePaths.size == 1) {
                    AsyncImage(
                        model = imagePaths[0],
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(12.dp)).background(if (entry.entryType == EntryType.DRAW) Color.White else Color.Transparent),
                        contentScale = if (entry.entryType == EntryType.DRAW) ContentScale.Fit else ContentScale.Crop,
                        onError = { Timber.e("Resim yüklenemedi: ${entry.imagePaths}") }
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(imagePaths) { path ->
                            AsyncImage(
                                model = path,
                                contentDescription = null,
                                modifier = Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            if (entry.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            if (entry.entryType == EntryType.TODO) {
                Spacer(modifier = Modifier.height(6.dp))
                entry.content.split("\n").forEachIndexed { index, task ->
                    if (task.length >= 3) {
                        val isChecked = task.startsWith("[x]")
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp).clickable { onTodoToggle(index) }) {
                            Checkbox(checked = isChecked, onCheckedChange = { onTodoToggle(index) }, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = task.substring(3).trim(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else if (entry.content.isNotBlank()) {
                val textToDisplay = when {
                    entry.entryType == EntryType.VOICE && entry.content.isBlank() -> "Ses kaydı"
                    else -> entry.content
                }
                if (textToDisplay.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = textToDisplay, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chipText = when (entry.entryType) {
                    EntryType.EXPENSE -> "₺${entry.expenseAmount}"
                    EntryType.WATER -> "💧 ${entry.waterAmountMl} ml"
                    EntryType.TODO -> "📋 Liste"
                    EntryType.DRAW -> "🎨 Çizim"
                    EntryType.FILM -> "🎬 Film"
                    EntryType.BOOK -> "📚 Kitap"
                    EntryType.MUSIC -> "🎵 Müzik"
                    EntryType.LOCATION -> "📍 Konum"
                    EntryType.SLEEP -> "😴 ${entry.sleepHours} sa"
                    EntryType.COFFEE -> "☕ ${entry.coffeeCups} fincan"
                    EntryType.ALCOHOL -> "🍷 Alkol"
                    EntryType.PHOTO -> "📸 Fotoğraflar"
                    EntryType.VIDEO -> "🎥 Video"
                    EntryType.LINK -> "🔗 Bağlantı"
                    else -> null
                }
                
                val onClickAction: () -> Unit = when(entry.entryType) {
                    EntryType.VIDEO -> onVideoClick
                    EntryType.LINK -> onLinkClick
                    EntryType.LOCATION -> onLocationClick
                    else -> { {} }
                }
                
                val itemIcon = when(entry.entryType) {
                    EntryType.VIDEO -> Icons.Default.PlayCircle
                    EntryType.LINK -> Icons.AutoMirrored.Filled.OpenInNew
                    EntryType.LOCATION -> Icons.Default.LocationOn
                    else -> null
                }

                if (chipText != null) {
                    SuggestionChip(
                        onClick = onClickAction,
                        label = { Text(chipText) },
                        icon = itemIcon?.let { icon ->
                            { Icon(icon, null, Modifier.size(18.dp)) }
                        }
                    )
                }
                
                if (entry.calories != null) {
                    SuggestionChip(onClick = {}, label = { Text("🔥 ${entry.calories} kcal") })
                }
            }

            if (entry.audioPath != null && entry.entryType == EntryType.VOICE) {
                Spacer(modifier = Modifier.height(8.dp))
                SuggestionChip(
                    onClick = onVoicePlaybackClick,
                    label = { Text(if (isVoicePlaying) "Sesi Durdur" else "Sesi Dinle") }
                )
            }
        }
    }
}

@Composable
private fun StepCounterCard(
    uiState: StepCounterUiState,
    hasActivityRecognitionPermission: Boolean,
    onEnableTrackingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Bugünün adımları",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${uiState.todaySteps}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (!uiState.isSensorAvailable) {
                Text(
                    text = "Bu cihazda adım sensörü yok.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (!hasActivityRecognitionPermission) {
                Text(
                    text = "Adım takibi için hareket izni gerekli.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onEnableTrackingClick) {
                    Text("İzni Aç")
                }
            } else if (uiState.isTracking) {
                Text(
                    text = "Telefon adımlarını otomatik sayıyor.",
                    style = MaterialTheme.typography.bodySmall
                )
                uiState.activeSensorName?.let { sensorName ->
                    Text(
                        text = "Aktif sensör: $sensorName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
