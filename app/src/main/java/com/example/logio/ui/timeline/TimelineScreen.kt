package com.example.logio.ui.timeline

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.logio.R
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.steps.StepCounterUiState
import com.example.logio.ui.navigation.BottomTab
import com.example.logio.ui.navigation.FloatingBottomBar
import com.example.logio.ui.theme.MimoBackground
import com.example.logio.ui.theme.MimoCloud
import com.example.logio.ui.theme.MimoLavender
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoNavyMuted
import com.example.logio.ui.theme.MimoPurple
import com.example.logio.ui.theme.logioFieldColors
import com.example.logio.ui.share.AiChip
import com.example.logio.ui.share.CategoryBadge
import com.example.logio.ui.share.LinkThumbnail
import com.example.logio.ui.share.classifySharedLink
import com.example.logio.ui.share.hostLabel
import com.example.logio.ui.share.youtubeThumbnailUrl
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = hiltViewModel(),
    onNavigate: (BottomTab) -> Unit = {}
) {
    val entries by viewModel.entries.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val playingFilePath by viewModel.playingFilePath.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val transcribingEntryIds by viewModel.transcribingEntryIds.collectAsState()
    val analysisError by viewModel.analysisError.collectAsState()
    val healthSleepState by viewModel.healthSleepState.collectAsState()
    val stepCounterUiState by viewModel.stepCounterUiState.collectAsState()

    val movieSearchResults by viewModel.movieSearchResults.collectAsState()
    val bookSearchResults by viewModel.bookSearchResults.collectAsState()
    val musicSearchResults by viewModel.musicSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val locationSearchResults by viewModel.locationSearchResults.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val healthConnectManager = viewModel.healthConnectManager
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->

        Log.d("HEALTH_DEBUG", "========== İZİN CALLBACK ==========")
        Log.d("HEALTH_DEBUG", "Verilen izinler: $grantedPermissions")

        // Uyku penceresi açıksa izin sonrası Health Connect verisini yeniden oku
        viewModel.loadHealthSleep()

        coroutineScope.launch {
            val missingPermissions =
                healthConnectManager.permissions - grantedPermissions

            Log.d(
                "HEALTH_DEBUG",
                "İstenen izinler: ${healthConnectManager.permissions}"
            )
            Log.d(
                "HEALTH_DEBUG",
                "Eksik izinler: $missingPermissions"
            )

            if (missingPermissions.isEmpty()) {
                Log.d("HEALTH_DEBUG", "TÜM İZİNLER VERİLDİ")

                viewModel.syncHealthData()

                snackbarHostState.showSnackbar(
                    context.getString(R.string.health_syncing)
                )
            } else {
                Log.d("HEALTH_DEBUG", "İZİNLER EKSİK")

                snackbarHostState.showSnackbar(
                    context.getString(R.string.health_permissions_missing)
                )
            }
        }
    }

    val todayWaterTotal = getWaterTotalForDay(entries, System.currentTimeMillis())
    val waterTarget = profile.waterGoalMl

    val needsActivityRecognitionPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var hasActivityRecognitionPermission by remember {
        mutableStateOf(
            !needsActivityRecognitionPermission ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showQuickMenu by remember { mutableStateOf(false) }
    var activeDialogAction by remember { mutableStateOf<DialogQuickAction?>(null) }

    LaunchedEffect(activeDialogAction) {
        if (activeDialogAction == DialogQuickAction.SLEEP) viewModel.loadHealthSleep()
    }
    var selectedEntryForDetail by remember { mutableStateOf<JournalEntryEntity?>(null) }

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

    LaunchedEffect(playbackError) {
        if (!playbackError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(playbackError!!)
            viewModel.clearPlaybackError()
        }
    }

    LaunchedEffect(analysisError) {
        if (!analysisError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(analysisError!!)
            viewModel.clearAnalysisError()
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

    val dateKeyFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val streakDays = remember(entries) { calculateStreak(entries, dateKeyFormat) }
    val greeting = stringResource(greetingForNow(), profile.name)
    // Son 36 saatteki en güncel uyku kaydı (Health Connect ya da manuel)
    val latestSleepHours = remember(entries) {
        val since = System.currentTimeMillis() - 36L * 60 * 60 * 1000
        entries
            .filter { it.entryType == EntryType.SLEEP && isPlausibleSleep(it.sleepHours) && it.createdAt >= since }
            .maxByOrNull { it.createdAt }
            ?.sleepHours
    }
    // Health Connect'ten eşitlenen bugünkü adım (gün başına tek kayıt tutulur)
    val healthStepsToday = remember(entries) {
        val now = System.currentTimeMillis()
        entries
            .filter { it.entryType == EntryType.STEPS && isSameCalendarDay(it.createdAt, now) }
            .mapNotNull { parseStepCount(it.content) }
            .maxOrNull()
    }

    // Arama metnine göre süzülmüş, güne göre gruplanmış kayıtlar (en yeni gün en üstte).
    // Adım bir "anı" değil, gün boyu biriken bir ölçüm: üstteki sağlık kartında ve istatistiklerde gösterilir.
    val visibleGroups = remember(entries, searchQuery) {
        val query = searchQuery.trim()
        entries
            .filter { it.entryType != EntryType.STEPS }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true) }
            .groupBy { dateKeyFormat.format(Date(it.createdAt)) }
            .toList()
    }
    // LazyColumn'da günlerin başlık satırlarının indeksleri (takvimden tarihe atlamak için)
    val headerIndexByDay = remember(visibleGroups) {
        var index = TIMELINE_TOP_ITEM_COUNT
        visibleGroups.associate { (dayKey, dayEntries) ->
            (dayKey to index).also { index += 1 + dayEntries.size }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    val selected = datePickerState.selectedDateMillis ?: return@TextButton
                    // DatePicker UTC gece yarısı döndürür; gün anahtarını UTC ile üret
                    val utcKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }
                    val targetKey = utcKeyFormat.format(Date(selected))
                    // Seçilen gün ya da ondan önceki en yakın kayıtlı gün
                    val dayKey = visibleGroups.firstOrNull { it.first <= targetKey }?.first
                        ?: visibleGroups.lastOrNull()?.first
                    val index = dayKey?.let { headerIndexByDay[it] } ?: return@TextButton
                    coroutineScope.launch { listState.animateScrollToItem(index) }
                }) { Text(stringResource(R.string.go)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Health Connect eşitleme akışı: hem liste içindeki buton hem de hızlı ekleme menüsü kullanır
    val onHealthSyncClick: () -> Unit = {
        val status = healthConnectManager.getAvailabilityStatus()

        when (status) {

            HealthConnectClient.SDK_AVAILABLE -> {

                coroutineScope.launch {

                    val grantedPermissions =
                        healthConnectManager.getGrantedPermissions()

                    val missingPermissions =
                        healthConnectManager.permissions - grantedPermissions

                    Log.d(
                        "HEALTH_DEBUG",
                        "========== SAĞLIK SENKRONİZASYONU =========="
                    )

                    Log.d(
                        "HEALTH_DEBUG",
                        "Verilen: $grantedPermissions"
                    )

                    Log.d(
                        "HEALTH_DEBUG",
                        "Eksik: $missingPermissions"
                    )

                    if (missingPermissions.isEmpty()) {

                        Log.d(
                            "HEALTH_DEBUG",
                            "Tüm Health Connect izinleri mevcut."
                        )

                        viewModel.syncHealthData()

                        snackbarHostState.showSnackbar(
                            context.getString(R.string.health_syncing)
                        )

                    } else {

                        Log.d(
                            "HEALTH_DEBUG",
                            "Eksik izinler isteniyor: $missingPermissions"
                        )

                        permissionsLauncher.launch(
                            missingPermissions
                        )
                    }
                }
            }

            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {

                healthConnectManager.openPlayStoreForHealthConnect()
            }

            HealthConnectClient.SDK_UNAVAILABLE -> {

                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.health_unsupported)
                    )
                }
            }
        }
    }

    MimoBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LogioTopBar(
                    streakDays = streakDays,
                    isSearchVisible = isSearchVisible,
                    onCalendarClick = { showDatePicker = true },
                    onSearchClick = {
                        isSearchVisible = !isSearchVisible
                        if (!isSearchVisible) searchQuery = ""
                    }
                )
            },
            bottomBar = {
                FloatingBottomBar(
                    selectedTab = BottomTab.JOURNAL,
                    onTabClick = { tab ->
                        when (tab) {
                            BottomTab.JOURNAL -> coroutineScope.launch { listState.animateScrollToItem(0) }
                            else -> onNavigate(tab)
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showQuickMenu = true },
                    containerColor = MimoPurple,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(12.dp, CircleShape, ambientColor = MimoPurple.copy(alpha = 0.4f))
                ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add), modifier = Modifier.size(30.dp)) }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isSearchVisible) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.timeline_search_hint)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MimoNavyMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            colors = logioFieldColors(unfocusedBorderColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)
                    ) {
                    item(key = "banner") {
                        MascotGreetingBanner(
                            greeting = greeting,
                            onAddClick = { showQuickMenu = true },
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )
                    }

                    item(key = "stats") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HealthActivityCard(
                            stepState = stepCounterUiState,
                            stepGoal = profile.stepGoal,
                            healthSteps = healthStepsToday,
                            hasActivityRecognitionPermission = hasActivityRecognitionPermission,
                            onEnableTrackingClick = {
                                if (needsActivityRecognitionPermission) {
                                    activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                }
                            },
                            sleepHours = latestSleepHours,
                            modifier = Modifier.fillMaxWidth()
                        )
                        WaterMiniCard(
                            todayWaterTotal = todayWaterTotal,
                            waterTarget = waterTarget,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    }

                    // Sağlık Verilerini Senkronize Etme Butonu
                    item(key = "health-sync") {
                    FilledTonalButton(
                        onClick = onHealthSyncClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MimoPurple.copy(alpha = 0.12f),
                            contentColor = MimoPurple
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = stringResource(R.string.timeline_sync_health),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    }

                    if (visibleGroups.isEmpty()) {
                        item(key = "empty") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 40.dp)
                            ) {
                                Image(
                                    painter = painterResource(
                                        id = if (searchQuery.isBlank()) R.drawable.mascot_empty else R.drawable.mascot_search_empty
                                    ),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(120.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isBlank()) stringResource(R.string.timeline_empty) else stringResource(R.string.timeline_no_results),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MimoNavy,
                                    textAlign = TextAlign.Center
                                )
                                if (searchQuery.isBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.timeline_empty_hint),
                                        color = Color(0xFF8A90B4),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        visibleGroups.forEachIndexed { groupIndex, (dayKey, dayEntries) ->
                            item(key = "header-$dayKey") {
                                TimelineDateHeader(
                                    date = Date(dayEntries.first().createdAt),
                                    drawRailAbove = groupIndex > 0
                                )
                            }
                            itemsIndexed(dayEntries, key = { _, entry -> entry.id }) { index, entry ->
                                val isLastOverall = groupIndex == visibleGroups.lastIndex && index == dayEntries.lastIndex
                                TimelineRailRow(
                                    dotCenterY = 24.dp,
                                    dotRadius = 5.dp,
                                    dotColor = entryLook(entry.entryType).tint,
                                    drawTop = true,
                                    drawBottom = !isLastOverall
                                ) {
                                    TimelineItemCard(
                                        entry = entry,
                                        isLast = index == dayEntries.lastIndex,
                                        showRail = false,
                                        isVoicePlaying = entry.audioPath != null && entry.audioPath == playingFilePath,
                                        onVoicePlaybackClick = { viewModel.onVoicePlaybackClick(entry) },
                                        isTranscribing = entry.id in transcribingEntryIds,
                                        onTranscribeClick = { viewModel.transcribeVoiceEntry(entry) },
                                        onDelete = { viewModel.deleteEntry(entry) },
                                        onTodoToggle = { indexTodo -> viewModel.toggleTodoItem(entry, indexTodo) },
                                        onVideoClick = { viewModel.openVideo(context, entry) },
                                        onLinkClick = { viewModel.openLink(context, entry) },
                                        onLocationClick = { viewModel.openMap(context, entry) },
                                        onCardClick = { selectedEntryForDetail = entry }
                                    )
                                }
                            }
                        }
                    }
                    }
                }

                if (isAnalyzing) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)) {
                        Box(contentAlignment = Alignment.Center) {
                            Card(
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(color = MimoPurple)
                                    Text(
                                        stringResource(R.string.preparing),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MimoNavy
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
                    },
                    onHealthSyncClick = {
                        showQuickMenu = false
                        onHealthSyncClick()
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

            if (selectedEntryForDetail != null) {
                EntryDetailSheet(
                    entry = selectedEntryForDetail!!,
                    onDismiss = { selectedEntryForDetail = null },
                    onDelete = { viewModel.deleteEntry(selectedEntryForDetail!!) }
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
                onSaveVoice = { _, _ -> },
                onAddExpense = { note, amount, categoryKey -> viewModel.saveExpenseEntry(amount, categoryKey, note) },
                onAddWater = { viewModel.saveWaterEntry(it) },
                onAddLocation = { lat, lng, title, note -> viewModel.saveMapLocation(lat, lng, title, note) },
                onAddTodo = { t, tasks -> viewModel.saveTodoEntry(t, tasks) },
                onAddDraw = { viewModel.saveDrawEntry(it) },
                onAddSleep = { h, n -> viewModel.saveSleepEntry(h, n) },
                healthSleepState = healthSleepState,
                onAddHealthSleep = { summary, n -> viewModel.saveHealthSleepEntry(summary, n) },
                onRequestHealthPermission = { permissionsLauncher.launch(healthConnectManager.permissions) },
                onInstallHealthConnect = { healthConnectManager.openPlayStoreForHealthConnect() },
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
                currentDayWaterTotal = todayWaterTotal,
                onGenerateJourney = { title, start, end, theme ->
                    viewModel.createJourneyVideo(title, start, end, theme)
                },
                journeyEntries = entries,
                onGenerateJourneyFromPhotos = { title, photos, theme ->
                    viewModel.createJourneyVideoFromPhotos(title, photos, theme)
                }
            )
        }
    }
}

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
private fun HealthActivityCard(
    stepState: StepCounterUiState,
    stepGoal: Int,
    healthSteps: Int?,
    hasActivityRecognitionPermission: Boolean,
    onEnableTrackingClick: () -> Unit,
    sleepHours: Double?,
    modifier: Modifier = Modifier
) {
    // Telefon sensörü ve Health Connect aynı adımları sayar; toplamak yerine büyük olanı göster
    val todaySteps = maxOf(stepState.todaySteps, healthSteps ?: 0)
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
                    text = String.format(Locale.getDefault(), "%,d", todaySteps),
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
                when {
                    healthSteps == null && !stepState.isSensorAvailable ->
                        Text(stringResource(R.string.steps_no_sensor), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    healthSteps == null && !hasActivityRecognitionPermission ->
                        Text(
                            stringResource(R.string.steps_enable_permission),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MimoPurple,
                            modifier = Modifier.clickable(onClick = onEnableTrackingClick)
                        )
                    else ->
                        Text(stringResource(R.string.goal_value, String.format(Locale.getDefault(), "%,d", stepGoal)), style = MaterialTheme.typography.labelSmall, color = CardMutedText)
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
                    text = stringResource(R.string.todo_progress, doneCount, tasks.size, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(entry.createdAt))),
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
private fun WaterMiniCard(
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

// ---------- Zaman çizgisi iskeleti ----------

// LazyColumn'da günlerden önce gelen sabit öğe sayısı: banner, istatistikler, sağlık eşitleme
private const val TIMELINE_TOP_ITEM_COUNT = 3

private val RailWidth = 28.dp
private val RailColor = MimoLavender.copy(alpha = 0.55f)

@StringRes
private fun greetingForNow(): Int {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_day
        in 18..22 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
}

/** Bugünden (bugün kayıt yoksa dünden) geriye doğru kesintisiz kayıt girilen gün sayısı. */
private fun calculateStreak(entries: List<JournalEntryEntity>, dayKeyFormat: SimpleDateFormat): Int {
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
private fun LogioTopBar(
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
                text = "Logio",
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
private fun MascotGreetingBanner(
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
private fun TimelineRailRow(
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
private fun TimelineDateHeader(date: Date, drawRailAbove: Boolean) {
    val trLocale = Locale.getDefault()
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
