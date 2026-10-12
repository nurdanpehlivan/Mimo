/*
 * TimelineDialogs: "+" menüsünden açılan kayıt pencereleri.
 *
 * Not, ruh hali, su, konum (harita + arama), uyku, link, çizim, video, film/kitap/müzik arama vb.
 * QuickActionDialogHost hangi pencerenin açık olduğuna göre doğru pencereyi gösterir.
 * Pencereler kaydı kendileri yapmaz; onAdd... / onSave... parametreleriyle ekrana bildirir.
 * Harcama, kahve ve alkol pencereleri TrackerDialogs.kt'dedir.
 */
package com.example.logio.ui.timeline

import com.example.logio.data.search.BookInfo
import com.example.logio.data.search.LocationSearchResult
import com.example.logio.data.search.MusicTrack
import com.example.logio.data.search.TvMazeShow
import com.example.logio.ui.theme.logioFieldColors
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.logio.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.logio.data.health.SleepSummary
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoNavyMuted
import com.example.logio.ui.theme.MimoPurple
import com.example.logio.ui.share.AiChip
import com.example.logio.ui.share.CategoryBadge
import com.example.logio.ui.share.LinkThumbnail
import com.example.logio.ui.share.SharedLinkCategory
import com.example.logio.ui.share.classifySharedLink
import com.example.logio.ui.share.hostLabel
import com.example.logio.ui.share.parseSharedText
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import android.location.Location
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.logio.ui.timeline.dialogs.JourneyVideoDialog

// Temel Veri Yapıları
data class DrawnPath(val points: List<Offset>, val color: Color)

enum class DialogQuickAction {
    LOG, MOOD, VOICE, EXPENSE, WATER, LOCATION, TODO, DRAW, FILM, BOOK, MUSIC, SLEEP, COFFEE, ALCOHOL, VIDEO, LINK, JOURNEY_VIDEO
}

fun QuickLogAction.toDialogQuickActionOrNull(): DialogQuickAction? = when (this) {
    QuickLogAction.LOG -> DialogQuickAction.LOG
    QuickLogAction.MOOD -> DialogQuickAction.MOOD
    QuickLogAction.VOICE -> DialogQuickAction.VOICE
    QuickLogAction.EXPENSE -> DialogQuickAction.EXPENSE
    QuickLogAction.WATER -> DialogQuickAction.WATER
    QuickLogAction.LOCATION -> DialogQuickAction.LOCATION
    QuickLogAction.TODO -> DialogQuickAction.TODO
    QuickLogAction.DRAW -> DialogQuickAction.DRAW
    QuickLogAction.FILM -> DialogQuickAction.FILM
    QuickLogAction.BOOK -> DialogQuickAction.BOOK
    QuickLogAction.MUSIC -> DialogQuickAction.MUSIC
    QuickLogAction.SLEEP -> DialogQuickAction.SLEEP
    QuickLogAction.COFFEE -> DialogQuickAction.COFFEE
    QuickLogAction.ALCOHOL -> DialogQuickAction.ALCOHOL
    QuickLogAction.VIDEO -> DialogQuickAction.VIDEO
    QuickLogAction.LINK -> DialogQuickAction.LINK
    QuickLogAction.JOURNEY -> DialogQuickAction.JOURNEY_VIDEO
    else -> null
}

@Composable
fun LogioDialog(
    onDismiss: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Color(0xFF64748B)) } },
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White,
        // Beyaz pencerede yazılar her zaman lacivert: temadan gelen renk açık olsa bile okunur kalır
        titleContentColor = MimoNavy,
        textContentColor = MimoNavy,
        title = { Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MimoNavy, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = { Box(modifier = Modifier.padding(top = 8.dp)) { content() } },
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}

@Composable
fun QuickActionDialogHost(
    activeDialogAction: DialogQuickAction?,
    onDismiss: () -> Unit,
    onAddNote: (String, String) -> Unit,
    onAddMood: (String, Int) -> Unit,
    isRecording: Boolean,
    onStartRecording: ((String) -> Unit) -> Unit,
    onStopRecording: () -> com.example.logio.data.audio.AudioRecordResult?,
    onSaveVoice: (String, Long) -> Unit,
    onAddExpense: (String, Double, String) -> Unit,
    onAddWater: (Int) -> Unit,
    onAddLocation: (Double, Double, String, String) -> Unit,
    onAddTodo: (String, List<String>) -> Unit,
    onAddDraw: (String) -> Unit,
    onAddSleep: (Double, String) -> Unit,
    healthSleepState: HealthSleepState = HealthSleepState.NoData,
    onAddHealthSleep: (SleepSummary, String) -> Unit = { _, _ -> },
    onRequestHealthPermission: () -> Unit = {},
    onInstallHealthConnect: () -> Unit = {},
    onAddCoffee: (Int, String) -> Unit,
    onAddAlcohol: (String, String) -> Unit,
    onAddVideo: (Uri, String) -> Unit,
    onAddLink: (String, String) -> Unit,
    onSearchMovies: (String) -> Unit,
    movieSearchResults: List<TvMazeShow>,
    onSearchBooks: (String) -> Unit,
    bookSearchResults: List<BookInfo>,
    onSearchMusic: (String) -> Unit,
    musicSearchResults: List<MusicTrack>,
    onSearchLocations: (String) -> Unit,
    locationSearchResults: List<LocationSearchResult>,
    onSaveLocation: (LocationSearchResult, String) -> Unit,
    isSearching: Boolean,
    onSaveFilm: (TvMazeShow, String) -> Unit,
    onSaveBook: (BookInfo, String) -> Unit,
    onSaveMusic: (MusicTrack, String) -> Unit,
    currentDayWaterTotal: Int,
    onGenerateJourney: (title: String, startDate: Long, endDate: Long, theme: String) -> Unit = { _, _, _, _ -> },
    journeyEntries: List<JournalEntryEntity> = emptyList(),
    onGenerateJourneyFromPhotos: (title: String, photos: List<String>, theme: String) -> Unit = { _, _, _ -> }
) {
    when (activeDialogAction) {
        DialogQuickAction.LOG -> LogInputDialog(onDismiss, onAddNote)
        DialogQuickAction.MOOD -> MoodInputDialog(onDismiss, onAddMood)
        DialogQuickAction.VOICE -> VoiceInputDialog(isRecording, onDismiss, onStartRecording, onStopRecording, onSaveVoice)
        DialogQuickAction.EXPENSE -> ExpenseInputDialog(onDismiss, onAddExpense)
        DialogQuickAction.WATER -> WaterInputDialog(currentDayWaterTotal, onDismiss, onAddWater)
        DialogQuickAction.LOCATION -> LocationAndMapDialog(onDismiss, onSearchLocations, locationSearchResults, isSearching, onSaveLocation, onAddLocation)
        DialogQuickAction.TODO -> TodoInputDialog(onDismiss, onAddTodo)
        DialogQuickAction.DRAW -> DrawInputDialog(onDismiss, onAddDraw)
        DialogQuickAction.SLEEP -> SleepInputDialog(healthSleepState, onDismiss, onAddSleep, onAddHealthSleep, onRequestHealthPermission, onInstallHealthConnect)
        DialogQuickAction.COFFEE -> CoffeeInputDialog(onDismiss, onAddCoffee)
        DialogQuickAction.ALCOHOL -> AlcoholInputDialog(onDismiss, onAddAlcohol)
        DialogQuickAction.VIDEO -> VideoInputDialog(onDismiss, onAddVideo)
        DialogQuickAction.LINK -> LinkInputDialog(onDismiss, onAddLink)
        DialogQuickAction.FILM -> SearchInputDialog(stringResource(R.string.dlg_add_film), stringResource(R.string.dlg_film_name), onDismiss, onSearchMovies, isSearching, movieSearchResults, { s -> SearchItemRow(s.name, s.summary, s.imageUrl, s.rating?.let { "⭐ $it" }) }, onSaveFilm)
        DialogQuickAction.BOOK -> SearchInputDialog(stringResource(R.string.dlg_add_book), stringResource(R.string.dlg_book_or_author), onDismiss, onSearchBooks, isSearching, bookSearchResults, { b -> SearchItemRow(b.title, b.authors, b.imageUrl) }, onSaveBook)
        DialogQuickAction.MUSIC -> SearchInputDialog(stringResource(R.string.dlg_add_music), stringResource(R.string.dlg_song_or_artist), onDismiss, onSearchMusic, isSearching, musicSearchResults, { m -> SearchItemRow(m.trackName, "${m.artistName}", m.imageUrl) }, onSaveMusic)
        DialogQuickAction.JOURNEY_VIDEO -> {
            JourneyVideoDialog(
                entries = journeyEntries,
                onDismiss = onDismiss,
                onGenerate = { title, startDate, endDate, theme ->
                    onGenerateJourney(title, startDate, endDate, theme)
                    onDismiss()
                },
                onGenerateFromPhotos = { title, photos, theme ->
                    onGenerateJourneyFromPhotos(title, photos, theme)
                    onDismiss()
                }
            )
        }
        null -> Unit
    }

}

@Composable
fun LogInputDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    LogioDialog(onDismiss, stringResource(R.string.dlg_new_note), { Button(onClick = { onConfirm(title, note); onDismiss() }, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.save)) } }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(colors = logioFieldColors(), value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.dlg_title)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            OutlinedTextField(colors = logioFieldColors(), value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.dlg_on_your_mind)) }, minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodInputDialog(onDismiss: () -> Unit, onSelectMood: (String, Int) -> Unit) {
    val moods = listOf(
        Triple("🤩", 5, R.string.mood_great), Triple("😊", 4, R.string.mood_good),
        Triple("😐", 3, R.string.mood_okay), Triple("😟", 2, R.string.mood_bad),
        Triple("😢", 1, R.string.mood_awful)
    )
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.dlg_how_feel), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MimoNavy)
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                moods.forEach { (emoji, rating, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSelectMood(emoji, rating); onDismiss() }) {
                        Surface(shape = CircleShape, color = Color(0xFFF1F5F9), modifier = Modifier.size(60.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 32.sp) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun WaterInputDialog(total: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var amount by remember { mutableStateOf("250") }
    LogioDialog(onDismiss, stringResource(R.string.dlg_water), { Button(onClick = { onConfirm(amount.toIntOrNull() ?: 0); onDismiss() }, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.add)) } }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.dlg_water_total, total), fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(colors = logioFieldColors(), value = amount, onValueChange = { amount = it }, label = { Text(stringResource(R.string.dlg_amount_ml)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), trailingIcon = { Icon(Icons.Rounded.WaterDrop, null, tint = Color(0xFF0EA5E9)) })
        }
    }
}

@Composable
fun LocationAndMapDialog(onDismiss: () -> Unit, onSearch: (String) -> Unit, searchResults: List<LocationSearchResult>, isSearching: Boolean, onConfirmLocation: (LocationSearchResult, String) -> Unit, onConfirmMap: (Double, Double, String, String) -> Unit) {
    var tabIndex by remember { mutableIntStateOf(0) }
    LogioDialog(onDismiss, stringResource(R.string.dlg_add_location), confirmButton = {}) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
            SecondaryTabRow(selectedTabIndex = tabIndex, containerColor = Color.Transparent, contentColor = MimoNavy, divider = {}) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, selectedContentColor = MimoPurple, unselectedContentColor = MimoNavyMuted, text = { Text(stringResource(R.string.dlg_search), fontWeight = FontWeight.SemiBold) })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, selectedContentColor = MimoPurple, unselectedContentColor = MimoNavyMuted, text = { Text(stringResource(R.string.dlg_pick_on_map), fontWeight = FontWeight.SemiBold) })
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (tabIndex == 0) {
                MascotHeader(R.drawable.mascot_location, stringResource(R.string.location_mascot_hint))
                Spacer(modifier = Modifier.height(12.dp))
                SearchInputDialogContent(stringResource(R.string.dlg_search_place), onSearch, isSearching, searchResults, { place ->
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocationOn, null, tint = Color(0xFF6366F1)); Spacer(Modifier.width(12.dp))
                        Column { Text(place.shortName, fontWeight = FontWeight.Bold); Text(place.displayName, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                }, { item, note -> onConfirmLocation(item, note); onDismiss() })
            } else {
                GoogleMapPickerContent(onConfirm = { lat, lng, name, note -> onConfirmMap(lat, lng, name, note); onDismiss() })
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun GoogleMapPickerContent(onConfirm: (Double, Double, String, String) -> Unit) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val defaultLocation = LatLng(41.0082, 28.9784) // Konum alınamazsa (izin yok / konum kapalı) İstanbul
    var selectedLocation by remember { mutableStateOf(defaultLocation) }
    // Kullanıcı haritaya dokunup bir yer seçtiyse, sonradan gelen konum bu seçimi ezmez
    var pickedByUser by remember { mutableStateOf(false) }
    var locationName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(defaultLocation, 12f) }

    fun isLocationGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var hasPermission by remember { mutableStateOf(isLocationGranted()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result.values.any { it }
    }
    // Harita açılınca izin yoksa bir kez istenir (yaklaşık konum da yeterli)
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // İzin varsa şu anki konuma git: önce son bilinen konum (anında), ardından güncel konum (birkaç saniye)
    val locationRequest = remember { CancellationTokenSource() }
    DisposableEffect(Unit) { onDispose { locationRequest.cancel() } }
    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val moveTo: (Location?) -> Unit = { location ->
            if (location != null && !pickedByUser) {
                val latLng = LatLng(location.latitude, location.longitude)
                selectedLocation = latLng
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }
        }
        fusedLocationClient.lastLocation.addOnSuccessListener(moveTo)
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, locationRequest.token)
            .addOnSuccessListener(moveTo)
    }

    // İşaret bir kez oluşturulur; haritaya dokunulunca (selectedLocation değişince) yeni konuma taşınır
    val markerState = rememberMarkerState(position = selectedLocation)
    LaunchedEffect(selectedLocation) { markerState.position = selectedLocation }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp))) {
            GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState, properties = MapProperties(isMyLocationEnabled = hasPermission), uiSettings = MapUiSettings(myLocationButtonEnabled = hasPermission), onMapClick = { selectedLocation = it; pickedByUser = true }) { Marker(state = markerState) }
        }
        OutlinedTextField(colors = logioFieldColors(), value = locationName, onValueChange = { locationName = it }, label = { Text(stringResource(R.string.dlg_place_name)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(colors = logioFieldColors(), value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.action_note)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Button(onClick = { onConfirm(selectedLocation.latitude, selectedLocation.longitude, locationName, note) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.dlg_save_location)) }
    }
}

@Composable
fun <T> SearchInputDialog(title: String, label: String, onDismiss: () -> Unit, onSearch: (String) -> Unit, isSearching: Boolean, searchResults: List<T>, itemContent: @Composable (T) -> Unit, onConfirm: (T, String) -> Unit) {
    LogioDialog(onDismiss, title, confirmButton = {}) {
        SearchInputDialogContent(label, onSearch, isSearching, searchResults, itemContent, { item, note -> onConfirm(item, note); onDismiss() })
    }
}

@Composable
fun <T> SearchInputDialogContent(label: String, onSearch: (String) -> Unit, isSearching: Boolean, searchResults: List<T>, itemContent: @Composable (T) -> Unit, onConfirm: (T, String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var userNote by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(query) { if (query.isNotBlank()) onSearch(query) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selectedItem == null) {
            OutlinedTextField(colors = logioFieldColors(), value = query, onValueChange = { query = it }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), trailingIcon = { if(isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Search, null) })
            Column(modifier = Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState())) {
                searchResults.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedItem = item }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) { itemContent(item) }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF6366F1).copy(alpha = 0.1f))) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.weight(1f)) { itemContent(selectedItem!!) }; TextButton(onClick = { selectedItem = null }) { Text(stringResource(R.string.dlg_change)) } }
            }
            OutlinedTextField(colors = logioFieldColors(), value = userNote, onValueChange = { userNote = it }, label = { Text(stringResource(R.string.dlg_your_note)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), minLines = 2)
            Button(onClick = { onConfirm(selectedItem!!, userNote) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
fun SearchItemRow(title: String, subtitle: String, imageUrl: String?, extra: String? = null) {
    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!imageUrl.isNullOrBlank()) { AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.size(45.dp, 65.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
        Column(modifier = Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1); Text(subtitle, fontSize = 12.sp, color = Color(0xFF64748B), maxLines = 2) }
        if (extra != null) Text(extra, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1), fontSize = 12.sp)
    }
}

@Composable
fun TodoInputDialog(onDismiss: () -> Unit, onConfirm: (String, List<String>) -> Unit) {
    var title by remember { mutableStateOf("") }
    var taskInput by remember { mutableStateOf("") }
    val tasks = remember { mutableStateListOf<String>() }
    LogioDialog(onDismiss, stringResource(R.string.action_todo), { Button(onClick = { onConfirm(title, tasks.toList()); onDismiss() }, enabled = tasks.isNotEmpty(), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.save)) } }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(colors = logioFieldColors(), value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.dlg_title)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(colors = logioFieldColors(), value = taskInput, onValueChange = { taskInput = it }, label = { Text(stringResource(R.string.dlg_task)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp))
                IconButton(onClick = { if (taskInput.isNotBlank()) { tasks.add(taskInput.trim()); taskInput = "" } }, modifier = Modifier.background(Color(0xFF6366F1), CircleShape)) { Icon(Icons.Rounded.Add, null, tint = Color.White) }
            }
            Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                tasks.forEachIndexed { i, t -> Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) { Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Text(t, modifier = Modifier.weight(1f)); IconButton(onClick = { tasks.removeAt(i) }) { Icon(Icons.Rounded.Delete, null, tint = Color.Red) } } } }
            }
        }
    }
}

@Composable
fun VoiceInputDialog(isRecording: Boolean, onDismiss: () -> Unit, onStartRecording: ((String) -> Unit) -> Unit, onStopRecording: () -> com.example.logio.data.audio.AudioRecordResult?, onSaveVoice: (String, Long) -> Unit) {
    var recordedPath by remember { mutableStateOf("") }
    var duration by remember { mutableLongStateOf(0L) }
    LogioDialog(onDismiss, stringResource(R.string.dlg_voice), { Button(onClick = { onSaveVoice(recordedPath, duration); onDismiss() }, enabled = recordedPath.isNotEmpty(), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.save)) } }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (isRecording) stringResource(R.string.dlg_listening) else if (recordedPath.isNotEmpty()) stringResource(R.string.dlg_recording_ready) else stringResource(R.string.dlg_press_to_record), textAlign = TextAlign.Center)
            IconButton(onClick = { if (isRecording) onStopRecording()?.let { recordedPath = it.filePath; duration = it.durationMs } else onStartRecording {} }, modifier = Modifier.size(80.dp).background(if (isRecording) Color.Red else Color(0xFF6366F1), CircleShape)) { Icon(if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
        }
    }
}

@Composable
fun PhotoNoteDialog(uris: List<Uri>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    LogioDialog(onDismiss, stringResource(R.string.dlg_photo_memory), { Button(onClick = { onConfirm(note); onDismiss() }, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.save)) } }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) { uris.forEach { uri -> AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(100.dp).clip(RoundedCornerShape(16.dp)).padding(end = 8.dp), contentScale = ContentScale.Crop) } }
            OutlinedTextField(colors = logioFieldColors(), value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.dlg_what_happened)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), minLines = 3)
        }
    }
}

@Composable
fun SleepInputDialog(
    healthSleepState: HealthSleepState,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit,
    onConfirmHealth: (SleepSummary, String) -> Unit,
    onRequestHealthPermission: () -> Unit,
    onInstallHealthConnect: () -> Unit
) {
    var useHealth by remember { mutableStateOf(true) }
    var hours by remember { mutableStateOf("8") }
    var note by remember { mutableStateOf("") }
    val foundSummary = (healthSleepState as? HealthSleepState.Found)?.summary
    val manualHours = hours.replace(',', '.').toDoubleOrNull()
    val manualHoursValid = isPlausibleSleep(manualHours)
    val canSave = if (useHealth) foundSummary != null else manualHoursValid

    LogioDialog(onDismiss, stringResource(R.string.dlg_sleep), {
        Button(
            onClick = {
                if (useHealth) {
                    foundSummary?.let { onConfirmHealth(it, note) }
                } else {
                    manualHours?.let { onConfirm(it, note) }
                }
                onDismiss()
            },
            enabled = canSave,
            shape = RoundedCornerShape(14.dp)
        ) { Text(stringResource(R.string.save)) }
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = useHealth, onClick = { useHealth = true }, label = { Text(stringResource(R.string.dlg_from_health)) }, leadingIcon = { Icon(Icons.Rounded.Favorite, null, modifier = Modifier.size(16.dp)) })
                FilterChip(selected = !useHealth, onClick = { useHealth = false }, label = { Text(stringResource(R.string.dlg_manual)) }, leadingIcon = { Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp)) })
            }

            if (useHealth) {
                when (healthSleepState) {
                    HealthSleepState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.dlg_hc_reading))
                    }
                    HealthSleepState.NotAvailable -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.dlg_hc_missing))
                        OutlinedButton(onClick = onInstallHealthConnect, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.dlg_hc_install)) }
                    }
                    HealthSleepState.NoPermission -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.dlg_hc_permission))
                        OutlinedButton(onClick = onRequestHealthPermission, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.grant_permission)) }
                    }
                    HealthSleepState.NoData -> Text(stringResource(R.string.dlg_hc_no_sleep))
                    is HealthSleepState.Found -> {
                        val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }
                        val s = healthSleepState.summary
                        Text(
                            text = stringResource(R.string.hours_value, String.format(currentLocale(), "%.1f", s.hours)),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            color = MimoNavy
                        )
                        Text("${timeFormat.format(s.startTime)} – ${timeFormat.format(s.endTime)} · Health Connect")
                    }
                }
            } else {
                OutlinedTextField(
                    colors = logioFieldColors(),
                    value = hours,
                    onValueChange = { hours = it },
                    label = { Text(stringResource(R.string.dlg_sleep_hours)) },
                    isError = hours.isNotBlank() && !manualHoursValid,
                    supportingText = { Text(if (hours.isNotBlank() && !manualHoursValid) stringResource(R.string.dlg_sleep_range, MAX_SLEEP_HOURS.toInt()) else stringResource(R.string.dlg_sleep_example)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = { Icon(Icons.Rounded.Bedtime, null) }
                )
            }
            OutlinedTextField(colors = logioFieldColors(), value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.action_note)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        }
    }
}

@Composable
fun VideoInputDialog(onDismiss: () -> Unit, onConfirm: (Uri, String) -> Unit) {
    var note by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { selectedUri = it }
    LogioDialog(onDismiss, stringResource(R.string.dlg_video_memory), { Button(onClick = { selectedUri?.let { onConfirm(it, note) }; onDismiss() }, enabled = selectedUri != null, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.save)) } }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selectedUri == null) {
                Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.Videocam, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.dlg_pick_video)) }
            } else {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))) { Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF6366F1)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.dlg_video_ready), fontWeight = FontWeight.Bold) } }
                OutlinedTextField(colors = logioFieldColors(), value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.action_note)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            }
        }
    }
}

/**
 * Instagram / TikTok / YouTube / web linki yapıştırılan diyalog. Paylaşım penceresiyle aynı
 * ayrıştırıcıyı kullanır: link metinden ayıklanır, başlık ve tür (Tarif / Film / Mekan / İçerik)
 * otomatik önerilir, kayıt zaman çizgisinde link kartı olarak görünür.
 */
@Composable
fun LinkInputDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var titleEdited by remember { mutableStateOf(false) }

    val resources = LocalContext.current.resources
    val parsed = remember(input) { parseSharedText(input, subject = null, res = resources) }
    // Kullanıcı başlığı elle değiştirmediyse önerilen başlığı izle
    LaunchedEffect(parsed?.title) {
        if (!titleEdited) title = parsed?.title.orEmpty()
    }
    val finalTitle = title.ifBlank { parsed?.title.orEmpty() }

    LogioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.dlg_add_link),
        confirmButton = {
            Button(
                onClick = {
                    parsed?.let { onConfirm(finalTitle, it.url) }
                    onDismiss()
                },
                enabled = parsed != null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MimoPurple)
            ) { Text(stringResource(R.string.dlg_add_to_journal)) }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                colors = logioFieldColors(),
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.dlg_paste_link_here)) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, tint = MimoPurple) },
                trailingIcon = {
                    TextButton(onClick = {
                        // Panodaki metni yapıştır (yeni pano API'si askıya alınabilen bir fonksiyon)
                        scope.launch {
                            clipboard.getClipEntry()?.clipData
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.text?.toString()
                                ?.let { input = it }
                        }
                    }) {
                        Text(stringResource(R.string.paste), color = MimoPurple, fontWeight = FontWeight.SemiBold)
                    }
                },
                isError = input.isNotBlank() && parsed == null,
                supportingText = {
                    Text(
                        if (input.isNotBlank() && parsed == null) stringResource(R.string.dlg_invalid_link)
                        else stringResource(R.string.dlg_link_hint)
                    )
                },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (parsed != null) {
                val category = classifySharedLink(finalTitle, parsed.url)
                LinkPreviewCard(
                    url = parsed.url,
                    thumbnailUrl = parsed.thumbnailUrl,
                    category = category,
                    title = title,
                    onTitleChange = {
                        title = it
                        titleEdited = true
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    category.chips.forEach { AiChip(it, compact = true) }
                }
            }
        }
    }
}

@Composable
private fun LinkPreviewCard(
    url: String,
    thumbnailUrl: String?,
    category: SharedLinkCategory,
    title: String,
    onTitleChange: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFEDE8F7))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LinkThumbnail(thumbnailUrl = thumbnailUrl, category = category, size = 60)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(category)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = hostLabel(url),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8A90B4),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    textStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = MimoNavy),
                    maxLines = 2,
                    decorationBox = { inner ->
                        Box {
                            if (title.isEmpty()) Text(stringResource(R.string.dlg_add_title), style = MaterialTheme.typography.titleSmall, color = Color(0xFFB8B5D3))
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun DrawInputDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    val paths = remember { mutableStateListOf<DrawnPath>() }
    var currentPoints = remember { mutableStateListOf<Offset>() }
    var currentColor by remember { mutableStateOf(Color.Black) }
    val colors = listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color(0xFFFFA500), Color(0xFF800080))

    LogioDialog(onDismiss, stringResource(R.string.dlg_draw), { Button(onClick = {
        val bitmap = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply { strokeWidth = 8f; style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND }
        paths.forEach { path -> paint.color = path.color.toArgb(); for (i in 0 until path.points.size - 1) { canvas.drawLine(path.points[i].x, path.points[i].y, path.points[i + 1].x, path.points[i + 1].y, paint) } }
        val file = File(context.filesDir, "draw_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        onConfirm(file.absolutePath); onDismiss()
    }, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.save)) } }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { colors.forEach { color -> Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(color).border(if (currentColor == color) 3.dp else 0.dp, Color(0xFF6366F1), CircleShape).clickable { currentColor = color }) } }
            Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Color.White).border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).pointerInput(Unit) { detectDragGestures(onDragStart = { currentPoints.add(it) }, onDrag = { change, _ -> change.consume(); currentPoints.add(change.position) }, onDragEnd = { paths.add(DrawnPath(currentPoints.toList(), currentColor)); currentPoints.clear() }) }) {
                Canvas(modifier = Modifier.fillMaxSize()) { paths.forEach { path -> for (i in 0 until path.points.size - 1) { drawLine(path.color, path.points[i], path.points[i + 1], strokeWidth = 5f, cap = StrokeCap.Round) } }; for (i in 0 until currentPoints.size - 1) { drawLine(currentColor, currentPoints[i], currentPoints[i + 1], strokeWidth = 5f, cap = StrokeCap.Round) } }
            }
            TextButton(onClick = { paths.clear() }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.journey_clear)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailSheet(
    entry: JournalEntryEntity, 
    onDismiss: () -> Unit, 
    onDelete: () -> Unit,
    onTodoToggle: (Int) -> Unit = {}
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm · dd MMMM yyyy", Locale.getDefault()) }
    val look = entryLook(entry.entryType)
    
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = look.wash, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(look.icon, null, tint = look.tint) }
                }
                Column {
                    Text(displayEntryTitle(entry) ?: stringResource(entryTypeLabel(entry.entryType)), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MimoNavy)
                    Text(timeFormat.format(Date(entry.createdAt)), style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onDelete(); onDismiss() }) { Icon(Icons.Rounded.Delete, null, tint = Color(0xFFEF4444)) }
            }

            val imagePaths = entry.imagePaths?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            if (imagePaths.isNotEmpty()) {
                imagePaths.forEach { path ->
                    AsyncImage(
                        model = path,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            if (entry.entryType == EntryType.TODO) {
                entry.content.split("\n").forEachIndexed { index, task ->
                    if (task.length >= 3) {
                        val isChecked = task.startsWith("[x]")
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTodoToggle(index) }
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(if (isChecked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (isChecked) Color(0xFF10B981) else Color(0xFF94A3B8))
                            Spacer(Modifier.width(12.dp))
                            Text(task.substring(3).trim(), color = if (isChecked) Color(0xFF94A3B8) else MimoNavy)
                        }
                    }
                }
            } else if (entry.content.isNotBlank()) {
                Text(entry.content, fontSize = 16.sp, color = Color(0xFF334155), lineHeight = 24.sp)
            }

            if (entry.calories != null) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.dlg_nutrition), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("${entry.calories} kcal", fontWeight = FontWeight.Bold); Text(stringResource(R.string.nutrient_energy), style = MaterialTheme.typography.labelSmall) }
                            Column { Text("${entry.protein}g", fontWeight = FontWeight.Bold); Text(stringResource(R.string.nutrient_protein), style = MaterialTheme.typography.labelSmall) }
                            Column { Text("${entry.carbs}g", fontWeight = FontWeight.Bold); Text(stringResource(R.string.nutrient_carbs), style = MaterialTheme.typography.labelSmall) }
                            Column { Text("${entry.fat}g", fontWeight = FontWeight.Bold); Text(stringResource(R.string.nutrient_fat), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }
}
