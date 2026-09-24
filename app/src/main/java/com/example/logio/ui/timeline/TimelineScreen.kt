package com.example.logio.ui.timeline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.steps.StepCounterUiState
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val todayDateLabel = formatTodayLabel()
    val todayWaterTotal = getWaterTotalForDay(entries, System.currentTimeMillis())

    val needsActivityRecognitionPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var hasActivityRecognitionPermission by remember {
        mutableStateOf(
            !needsActivityRecognitionPermission ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showQuickMenu by remember { mutableStateOf(false) }
    var activeDialogAction by remember { mutableStateOf(null) }
    var pendingPickerAction by remember { mutableStateOf(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            val selectedAction = pendingPickerAction
            if (uri != null && selectedAction != null) {
                if (selectedAction == QuickLogAction.MEAL) {
                    viewModel.analyzeAndSaveMeal(context, uri)
                } else {
                    viewModel.addPhotoEntry(context, uri, "Fotoğraf Anısı")
                }
            }
            pendingPickerAction = null
        }
    )

    val activityPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasActivityRecognitionPermission = granted
        }
    )

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(playbackError) {
        if (!playbackError.isNullOrBlank()) {
            snackbarHostState.showSnackbar("Ses açılamadı. Yazılı hali gösteriliyor.")
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

    LaunchedEffect(stepCounterUiState.errorMessage) {
        val stepError = stepCounterUiState.errorMessage
        if (!stepError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(stepError)
            viewModel.clearStepError()
        }
    }

    val askActivityPermission: () -> Unit = {
        if (needsActivityRecognitionPermission) {
            activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    DisposableEffect(lifecycleOwner, needsActivityRecognitionPermission, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasActivityRecognitionPermission =
                    !needsActivityRecognitionPermission ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACTIVITY_RECOGNITION
                            ) == PackageManager.PERMISSION_GRANTED
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopVoicePlayback()
            viewModel.stopStepTracking()
            viewModel.clearPlaybackError()
            viewModel.clearStepError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logio", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickMenu = true },
                containerColor = Color(0xFF22C55E),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ekle")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                StepCounterCard(
                    uiState = stepCounterUiState,
                    hasActivityRecognitionPermission = hasActivityRecognitionPermission,
                    onEnableTrackingClick = askActivityPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = todayDateLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Bugün su: ${todayWaterTotal} ml / 2000 ml",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (todayWaterTotal >= 2000) {
                            Text(
                                text = "Hedef tamamlandı! 🎉",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Kalan: ${2000 - todayWaterTotal} ml",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Henüz bir kayıt yok.\nİlk anını eklemek için +'ya bas!",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            TimelineItemCard(
                                entry = entry,
                                isVoicePlaying = entry.audioPath != null && entry.audioPath == playingFilePath,
                                onVoicePlaybackClick = { viewModel.onVoicePlaybackClick(entry) },
                                onDelete = { viewModel.deleteEntry(entry) }
                            )
                        }
                    }
                }
            }

            if (isAnalyzing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFF22C55E))
                                Text(
                                    "Yemek analiz ediliyor...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showQuickMenu) {
            QuickAddBottomSheet(
                onDismissRequest = { showQuickMenu = false },
                onActionClick = { action: QuickLogAction ->
                    showQuickMenu = false

                    val dialogAction = action.toDialogQuickActionOrNull()
                    if (dialogAction != null) {
                        activeDialogAction = dialogAction
                    } else {
                        when (action) {
                            QuickLogAction.PHOTO, QuickLogAction.MEAL -> {
                                pendingPickerAction = action
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            else -> Unit
                        }
                    }
                }
            )
        }

        QuickActionDialogHost(
            activeDialogAction = activeDialogAction,
            onDismiss = { activeDialogAction = null },
            onAddNote = { title, note -> viewModel.addNote(title, note) },
            onAddMood = { emoji, rating -> viewModel.addMood(emoji, rating) },
            isRecording = isRecording,
            onStartRecording = { onError -> viewModel.startVoiceRecording(onError) },
            onStopRecording = { viewModel.stopVoiceRecording() },
            onSaveVoice = { audioPath, durationMs ->
                viewModel.saveVoiceEntry(audioPath = audioPath, durationMs = durationMs)
            },
            onAddExpense = { title, amount, category ->
                viewModel.saveExpenseEntry(title, amount, category)
            },
            onAddWater = { amountMl -> viewModel.saveWaterEntry(amountMl) },
            onAddLocation = { placeName, note -> viewModel.saveLocationEntry(placeName, note) },
            currentDayWaterTotal = todayWaterTotal
        )
    }
}

@Composable
fun TimelineItemCard(
    entry: JournalEntryEntity,
    isVoicePlaying: Boolean,
    onVoicePlaybackClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("tr")) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dateFormat.format(Date(entry.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = timeFormat.format(Date(entry.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }

            if (!entry.imagePath.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = entry.imagePath,
                    contentDescription = "Günlük Görseli",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                    onError = { Timber.e("Resim yüklenemedi: ${entry.imagePath}") }
                )
            }

            if (entry.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val textToDisplay = when {
                entry.entryType == EntryType.VOICE && entry.content.isBlank() -> "Ses kaydı"
                else -> entry.content
            }

            if (textToDisplay.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = textToDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (entry.entryType == EntryType.EXPENSE && !entry.expenseCategory.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                SuggestionChip(
                    onClick = {},
                    label = { Text(entry.expenseCategory!!) }
                )
            }

            if (entry.entryType == EntryType.WATER && entry.waterAmountMl != null) {
                Spacer(modifier = Modifier.height(8.dp))
                SuggestionChip(
                    onClick = {},
                    label = { Text("💧 ${entry.waterAmountMl} ml") }
                )
            }

            if (entry.entryType == EntryType.LOCATION) {
                Spacer(modifier = Modifier.height(8.dp))
                SuggestionChip(
                    onClick = {},
                    label = { Text("📍 Konum") }
                )
            }

            if (entry.calories != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("🔥 ${entry.calories} kcal") }
                    )
                    entry.protein?.let {
                        SuggestionChip(onClick = {}, label = { Text("🥩 ${it}g P") })
                    }
                    entry.carbs?.let {
                        SuggestionChip(onClick = {}, label = { Text("🍞 ${it}g K") })
                    }
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

            when {
                !uiState.isSensorAvailable -> {
                    Text(
                        text = "Bu cihazda adım sensörü yok.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                !hasActivityRecognitionPermission -> {
                    Text(
                        text = "Adım takibi için hareket izni gerekli.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = onEnableTrackingClick) {
                        Text("İzni Aç")
                    }
                }
                uiState.isTracking -> {
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
                else -> {
                    Text(
                        text = "Adım takibi başlatılıyor...",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}