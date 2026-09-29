package com.example.logio.ui.timeline

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

enum class DialogQuickAction {
    LOG, MOOD, VOICE, EXPENSE, WATER, LOCATION, TODO, DRAW, FILM, BOOK, MUSIC, SLEEP, COFFEE, ALCOHOL, VIDEO, LINK
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
    else -> null
}

data class DrawnPath(val points: List<Offset>, val color: Color)

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
    currentDayWaterTotal: Int
) {
    when (activeDialogAction) {
        DialogQuickAction.LOG -> LogInputDialog(onDismiss, onAddNote)
        DialogQuickAction.MOOD -> MoodInputDialog(onDismiss, onAddMood)
        DialogQuickAction.VOICE -> VoiceInputDialog(isRecording, onDismiss, onStartRecording, onStopRecording, onSaveVoice)
        DialogQuickAction.EXPENSE -> ExpenseInputDialog(onDismiss, onAddExpense)
        DialogQuickAction.WATER -> WaterInputDialog(currentDayWaterTotal, onDismiss, onAddWater)
        DialogQuickAction.LOCATION -> LocationAndMapDialog(
            onDismiss = onDismiss,
            onSearch = onSearchLocations,
            searchResults = locationSearchResults,
            isSearching = isSearching,
            onConfirmLocation = onSaveLocation,
            onConfirmMap = onAddLocation
        )
        DialogQuickAction.TODO -> TodoInputDialog(onDismiss, onAddTodo)
        DialogQuickAction.DRAW -> DrawInputDialog(onDismiss, onAddDraw)
        DialogQuickAction.SLEEP -> SleepInputDialog(onDismiss, onAddSleep)
        DialogQuickAction.COFFEE -> CoffeeInputDialog(onDismiss, onAddCoffee)
        DialogQuickAction.ALCOHOL -> AlcoholInputDialog(onDismiss, onAddAlcohol)
        DialogQuickAction.VIDEO -> VideoInputDialog(onDismiss, onAddVideo)
        DialogQuickAction.LINK -> LinkInputDialog(onDismiss, onAddLink)
        DialogQuickAction.FILM -> SearchInputDialog(
            title = "Film/Dizi Ekle",
            label = "Film Adı",
            onDismiss = onDismiss,
            onSearch = onSearchMovies,
            isSearching = isSearching,
            searchResults = movieSearchResults,
            itemContent = { show ->
                SearchItemRow(show.name, show.summary, show.imageUrl, show.rating?.let { "⭐ $it" })
            },
            onConfirm = onSaveFilm
        )
        DialogQuickAction.BOOK -> SearchInputDialog(
            title = "Kitap Ekle",
            label = "Kitap veya Yazar",
            onDismiss = onDismiss,
            onSearch = onSearchBooks,
            isSearching = isSearching,
            searchResults = bookSearchResults,
            itemContent = { book ->
                SearchItemRow(book.title, book.authors, book.imageUrl)
            },
            onConfirm = onSaveBook
        )
        DialogQuickAction.MUSIC -> SearchInputDialog(
            title = "Müzik Ekle",
            label = "Şarkı veya Sanatçı",
            onDismiss = onDismiss,
            onSearch = onSearchMusic,
            isSearching = isSearching,
            searchResults = musicSearchResults,
            itemContent = { track ->
                SearchItemRow(track.trackName, "${track.artistName} - ${track.collectionName}", track.imageUrl)
            },
            onConfirm = onSaveMusic
        )
        null -> Unit
    }
}

@Composable
fun LocationAndMapDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    searchResults: List<LocationSearchResult>,
    isSearching: Boolean,
    onConfirmLocation: (LocationSearchResult, String) -> Unit,
    onConfirmMap: (Double, Double, String, String) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konum Ekle") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) {
                        Text("Ara", modifier = Modifier.padding(16.dp))
                    }
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) {
                        Text("Haritadan Seç", modifier = Modifier.padding(16.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (tabIndex == 0) {
                    SearchInputDialogContent(
                        label = "Mekan veya Şehir Ara",
                        onSearch = onSearch,
                        isSearching = isSearching,
                        searchResults = searchResults,
                        itemContent = { place ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(place.shortName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(place.displayName, maxLines = 1, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
                                }
                            }
                        },
                        onConfirm = { item, note ->
                            onConfirmLocation(item, note)
                            onDismiss()
                        }
                    )
                } else {
                    GoogleMapPickerContent(onConfirm = { lat, lng, name, note ->
                        onConfirmMap(lat, lng, name, note)
                        onDismiss()
                    })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun GoogleMapPickerContent(onConfirm: (Double, Double, String, String) -> Unit) {
    val context = LocalContext.current
    val initialLocation = LatLng(41.0082, 28.9784) // İstanbul default
    
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result.values.any { it }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    var selectedLocation by remember { mutableStateOf(initialLocation) }
    var locationName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLocation, 11f)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                // Güvenlik: İzin yoksa bu özellikleri asla true yapma
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
                onMapClick = { latLng ->
                    selectedLocation = latLng
                }
            ) {
                Marker(
                    state = MarkerState(position = selectedLocation),
                    title = "Seçilen Konum"
                )
            }
        }
        
        if (!hasLocationPermission) {
            Text("Haritada kendi konumunu görmek için izin vermelisin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        OutlinedTextField(
            value = locationName,
            onValueChange = { locationName = it },
            label = { Text("Mekan İsmi (Opsiyonel)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Notun") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onConfirm(selectedLocation.latitude, selectedLocation.longitude, locationName, note) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Kaydet")
        }
    }
}

@Composable
fun LinkInputDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bağlantı Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Başlık") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL (http...)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(title, url); onDismiss() }, enabled = url.isNotBlank()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun <T> SearchInputDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<T>,
    itemContent: @Composable (T) -> Unit,
    onConfirm: (T, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SearchInputDialogContent(label, onSearch, isSearching, searchResults, itemContent, onConfirm)
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun <T> SearchInputDialogContent(
    label: String,
    onSearch: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<T>,
    itemContent: @Composable (T) -> Unit,
    onConfirm: (T, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var userNote by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<T?>(null) }

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            onSearch(query)
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedItem == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (isSearching) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(searchResults) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedItem = item },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            itemContent(item)
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Seçildi", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { selectedItem = null }) { Text("Değiştir") }
            }
            OutlinedTextField(
                value = userNote,
                onValueChange = { userNote = it },
                label = { Text("Notun...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { selectedItem?.let { onConfirm(it, userNote) } },
                modifier = Modifier.align(Alignment.End),
                enabled = selectedItem != null
            ) {
                Text("Kaydet")
            }
        }
    }
}

@Composable
fun SearchItemRow(title: String, subtitle: String, imageUrl: String?, extra: String? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.size(50.dp, 75.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 14.sp)
            Text(subtitle, maxLines = 2, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp)
        }
        if (extra != null) Text(extra, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
    }
}

@Composable
fun LogInputDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Not") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Başlık") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Aklındakiler...") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(title, note); onDismiss() }, enabled = note.isNotBlank() || title.isNotBlank()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodInputDialog(onDismiss: () -> Unit, onSelectMood: (String, Int) -> Unit) {
    val moods = listOf(Triple("😍", 5, "Muhteşem"), Triple("😊", 4, "İyi"), Triple("😐", 3, "Normal"), Triple("😟", 2, "Fena değil"), Triple("😢", 1, "İyi değil"))
    var selectedMood by remember { mutableStateOf<Triple<String, Int, String>?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                moods.forEach { mood ->
                    val isSelected = selectedMood == mood
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { selectedMood = mood },
                            shape = CircleShape,
                            modifier = Modifier.size(54.dp),
                            border = if (isSelected) ButtonDefaults.outlinedButtonBorder(true).copy(width = 2.dp, brush = SolidColor(MaterialTheme.colorScheme.primary)) else ButtonDefaults.outlinedButtonBorder(true)
                        ) { Text(mood.first, fontSize = 24.sp) }
                        Text(mood.third, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Button(onClick = { selectedMood?.let { onSelectMood(it.first, it.second) }; onDismiss() }, enabled = selectedMood != null, modifier = Modifier.align(Alignment.End)) { Text("Kaydet") }
        }
    }
}

@Composable
fun VoiceInputDialog(isRecording: Boolean, onDismiss: () -> Unit, onStartRecording: ((String) -> Unit) -> Unit, onStopRecording: () -> com.example.logio.data.audio.AudioRecordResult?, onSaveVoice: (String, Long) -> Unit) {
    var recordedPath by remember { mutableStateOf("") }
    var duration by remember { mutableLongStateOf(0L) }
    var errorText by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sesli Kayıt") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRecording) Text("Kaydediliyor...", color = Color.Red)
                else if (recordedPath.isNotEmpty()) Text("Kayıt Tamamlandı")
                if (errorText != null) Text(errorText!!, color = Color.Red, fontSize = 12.sp)
                Button(onClick = {
                    if (isRecording) {
                        onStopRecording()?.let { recordedPath = it.filePath; duration = it.durationMs }
                    } else {
                        onStartRecording { errorText = it }
                    }
                }) {
                    Text(if (isRecording) "Durdur" else if (recordedPath.isEmpty()) "Başlat" else "Yeniden")
                }
            }
        },
        confirmButton = { Button(onClick = { onSaveVoice(recordedPath, duration); onDismiss() }, enabled = recordedPath.isNotEmpty()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun ExpenseInputDialog(onDismiss: () -> Unit, onConfirm: (String, Double, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Harcama Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Açıklama") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Miktar") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onConfirm(title, amount.toDoubleOrNull() ?: 0.0, "Genel"); onDismiss() }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun WaterInputDialog(total: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Su Tüketimi") },
        text = { Column { Text("Bugün: $total ml"); OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Miktar (ml)") }) } },
        confirmButton = { Button(onClick = { onConfirm(amount.toIntOrNull() ?: 0); onDismiss() }) { Text("Ekle") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun TodoInputDialog(onDismiss: () -> Unit, onConfirm: (String, List<String>) -> Unit) {
    var title by remember { mutableStateOf("") }
    var taskInput by remember { mutableStateOf("") }
    val tasks = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yapılacaklar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Başlık") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = taskInput, onValueChange = { taskInput = it }, label = { Text("Görev") }, modifier = Modifier.weight(1f))
                    Button(onClick = { if (taskInput.isNotBlank()) { tasks.add(taskInput.trim()); taskInput = "" } }) { Text("Ekle") }
                }
                tasks.forEachIndexed { i, t -> Row(verticalAlignment = Alignment.CenterVertically) { Text("• $t", modifier = Modifier.weight(1f)); IconButton(onClick = { tasks.removeAt(i) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) } } }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(title, tasks.toList()); onDismiss() }, enabled = tasks.isNotEmpty()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun DrawInputDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    val paths = remember { mutableStateListOf<DrawnPath>() }
    var currentPoints = remember { mutableStateListOf<Offset>() }
    var currentColor by remember { mutableStateOf(Color.Black) }
    val colors = listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color(0xFFFFA500), Color(0xFF800080))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Çizim Yap") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { color ->
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(color).border(if (currentColor == color) 2.dp else 0.dp, Color.Gray, CircleShape).clickable { currentColor = color })
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Color.White).clip(RoundedCornerShape(8.dp)).pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { currentPoints.add(it) },
                        onDrag = { change, _ -> change.consume(); currentPoints.add(change.position) },
                        onDragEnd = { paths.add(DrawnPath(paths.toList().lastOrNull()?.points ?: emptyList(), currentColor)); currentPoints = mutableStateListOf() }
                    )
                }) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        paths.forEach { path ->
                            for (i in 0 until path.points.size - 1) {
                                drawLine(path.color, path.points[i], path.points[i + 1], strokeWidth = 5f, cap = StrokeCap.Round)
                            }
                        }
                        for (i in 0 until currentPoints.size - 1) {
                            drawLine(currentColor, currentPoints[i], currentPoints[i + 1], strokeWidth = 5f, cap = StrokeCap.Round)
                        }
                    }
                }
                TextButton(onClick = { paths.clear() }, modifier = Modifier.align(Alignment.End)) { Text("Temizle") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val bitmap = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                val paint = android.graphics.Paint().apply { strokeWidth = 8f; style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND }
                paths.forEach { path ->
                    paint.color = path.color.toArgb()
                    for (i in 0 until path.points.size - 1) {
                        canvas.drawLine(path.points[i].x, path.points[i].y, path.points[i + 1].x, path.points[i + 1].y, paint)
                    }
                }
                val file = File(context.filesDir, "draw_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                onConfirm(file.absolutePath)
                onDismiss()
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun SleepInputDialog(onDismiss: () -> Unit, onConfirm: (Double, String) -> Unit) {
    var hours by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uyku Takibi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = hours, onValueChange = { hours = it }, label = { Text("Süre (Saat)") })
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Not") })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(hours.toDoubleOrNull() ?: 0.0, note); onDismiss() }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun CoffeeInputDialog(onDismiss: () -> Unit, onConfirm: (Int, String) -> Unit) {
    var cups by remember { mutableStateOf("1") }
    var type by remember { mutableStateOf("Filtre Kahve") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kahve Ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = cups, onValueChange = { cups = it }, label = { Text("Fincan Sayısı") })
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Kahve Türü") })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(cups.toIntOrNull() ?: 1, type); onDismiss() }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun AlcoholInputDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alkol Tüketimi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Miktar (cl/kadeh)") })
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Tür") })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(amount, type); onDismiss() }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun VideoInputDialog(onDismiss: () -> Unit, onConfirm: (Uri, String) -> Unit) {
    var note by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        selectedUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video Anısı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedUri == null) {
                    Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
                        Text("Video Seç")
                    }
                } else {
                    Text("Video Seçildi ✅")
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Not") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(onClick = { selectedUri?.let { onConfirm(it, note) }; onDismiss() }, enabled = selectedUri != null) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

@Composable
fun PhotoNoteDialog(uris: List<Uri>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fotoğraf Anısı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(uris) { uri ->
                        AsyncImage(model = uri, contentDescription = null, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Bir şeyler yaz...") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(note) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}
