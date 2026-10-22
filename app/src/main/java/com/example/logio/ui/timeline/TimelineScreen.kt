/*
 * TimelineScreen: Günlük sekmesinin (zaman tüneli) ekranı.
 *
 * Kayıt listesi (günlere göre gruplu), arama, tarihe atlama, "+" menüsü, fotoğraf/yemek seçiciler,
 * Health Connect izin akışı ve kayıt pencereleri burada bir araya getirilir.
 * Kartlar TimelineCards.kt'de, üst bar / karşılama kartı / zaman çizgisi TimelineChrome.kt'dedir.
 * Veri TimelineViewModel'den, arama sonuçları SearchViewModel'den gelir.
 */
package com.example.logio.ui.timeline

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.logio.R
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.ui.navigation.BottomTab
import com.example.logio.ui.navigation.FloatingBottomBar
import com.example.logio.ui.theme.MimoBackground
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoNavyMuted
import com.example.logio.ui.theme.MimoPurple
import com.example.logio.ui.theme.logioFieldColors
import com.example.logio.util.EntryLauncher
import kotlinx.coroutines.launch
import com.example.logio.ui.premium.PremiumDialog
import com.example.logio.ui.premium.PremiumReason
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
    onNavigate: (BottomTab) -> Unit = {}
) {
    val entries by viewModel.entries.collectAsState()
    val entriesLoaded by viewModel.entriesLoaded.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val playingFilePath by viewModel.playingFilePath.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val transcribingEntryIds by viewModel.transcribingEntryIds.collectAsState()
    val analysisError by viewModel.analysisError.collectAsState()
    val healthSleepState by viewModel.healthSleepState.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    // Premium penceresi: kilitli Journey seçeneği ya da biten öğün analizi hakkı açar
    var premiumReason by remember { mutableStateOf<PremiumReason?>(null) }

    // Film / kitap / müzik / konum arama pencereleri SearchViewModel'den beslenir
    val movieSearchResults by searchViewModel.movieSearchResults.collectAsState()
    val bookSearchResults by searchViewModel.bookSearchResults.collectAsState()
    val musicSearchResults by searchViewModel.musicSearchResults.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()

    val locationSearchResults by searchViewModel.locationSearchResults.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val healthConnectManager = viewModel.healthConnectManager
    val snackbarHostState = remember { SnackbarHostState() }
    // Uyarı yazıları çizim sırasında alınır; dil değişince yenileriyle güncellenir
    val healthSyncingText = stringResource(R.string.health_syncing)
    val healthPermissionsMissingText = stringResource(R.string.health_permissions_missing)
    val healthUnsupportedText = stringResource(R.string.health_unsupported)

    val permissionsLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        // Uyku penceresi açıksa izin sonrası Health Connect verisini yeniden oku
        viewModel.loadHealthSleep()

        coroutineScope.launch {
            val missingPermissions = healthConnectManager.permissions - grantedPermissions
            if (missingPermissions.isEmpty()) {
                viewModel.syncHealthData()
                snackbarHostState.showSnackbar(
                    healthSyncingText
                )
            } else {
                snackbarHostState.showSnackbar(
                    healthPermissionsMissingText
                )
            }
        }
    }

    val todayWaterTotal = getWaterTotalForDay(entries, System.currentTimeMillis())
    val waterTarget = profile.waterGoalMl

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

    // Adımlar yalnızca Health Connect'ten gelir: uygulama her öne geldiğinde (izin verilmişse) sessizce eşitlenir
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.syncHealthData()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val dateKeyFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val streakDays = remember(entries) { calculateStreak(entries, dateKeyFormat) }
    val greeting = if (profile.name.isBlank()) stringResource(greetingForNow(withName = false))
        else stringResource(greetingForNow(withName = true), profile.name)
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
                    val missingPermissions =
                        healthConnectManager.permissions - healthConnectManager.getGrantedPermissions()

                    if (missingPermissions.isEmpty()) {
                        viewModel.syncHealthData()
                        snackbarHostState.showSnackbar(
                            healthSyncingText
                        )
                    } else {
                        // Eksik izinler istenir; sonuç permissionsLauncher'a döner
                        permissionsLauncher.launch(missingPermissions)
                    }
                }
            }

            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {

                healthConnectManager.openPlayStoreForHealthConnect()
            }

            HealthConnectClient.SDK_UNAVAILABLE -> {

                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        healthUnsupportedText
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
                            stepGoal = profile.stepGoal,
                            healthSteps = healthStepsToday,
                            onConnectHealthClick = onHealthSyncClick,
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

                    if (visibleGroups.isEmpty() && entriesLoaded) {
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
                                        onVideoClick = { EntryLauncher.openVideo(context, entry) },
                                        onLinkClick = { EntryLauncher.openLink(context, entry) },
                                        onLocationClick = { EntryLauncher.openMap(context, entry) },
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
                                    if (viewModel.canAnalyzeMeal()) {
                                        mealPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    } else {
                                        premiumReason = PremiumReason.MEAL_LIMIT
                                    }
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
                onSearchMovies = { searchViewModel.searchMovies(it) },
                movieSearchResults = movieSearchResults,
                onSearchBooks = { searchViewModel.searchBooks(it) },
                bookSearchResults = bookSearchResults,
                onSearchMusic = { searchViewModel.searchMusic(it) },
                musicSearchResults = musicSearchResults,
                onSearchLocations = { searchViewModel.searchLocations(it) },
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
                },
                isPremium = isPremium,
                onPremiumRequired = { premiumReason = PremiumReason.JOURNEY }
            )

            premiumReason?.let { reason ->
                PremiumDialog(reason = reason, onDismiss = { premiumReason = null })
            }
        }
    }
}

