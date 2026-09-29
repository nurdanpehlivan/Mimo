package com.example.logio.ui.timeline

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.logio.data.audio.AudioPlayerManager
import com.example.logio.data.audio.AudioRecordResult
import com.example.logio.data.audio.AudioRecorderManager
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalDao
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.remote.GeminiService
import com.example.logio.data.steps.StepCounterManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import javax.inject.Inject
import timber.log.Timber

data class TvMazeShow(val name: String, val summary: String, val imageUrl: String?, val rating: String?)
data class BookInfo(val title: String, val authors: String, val description: String, val imageUrl: String?)
data class MusicTrack(val trackName: String, val artistName: String, val collectionName: String?, val imageUrl: String?)
data class LocationSearchResult(val displayName: String, val shortName: String, val latitude: Double, val longitude: Double)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val journalDao: JournalDao,
    private val geminiService: GeminiService,
    private val audioRecorderManager: AudioRecorderManager,
    private val audioPlayerManager: AudioPlayerManager,
    private val stepCounterManager: StepCounterManager
) : ViewModel() {

    val entries: StateFlow<List<JournalEntryEntity>> = journalDao.getAllEntries()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    val isRecording = audioRecorderManager.isRecording
    val playingFilePath = audioPlayerManager.playingFilePath
    val stepCounterUiState = stepCounterManager.uiState

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _movieSearchResults = MutableStateFlow<List<TvMazeShow>>(emptyList())
    val movieSearchResults: StateFlow<List<TvMazeShow>> = _movieSearchResults.asStateFlow()

    private val _bookSearchResults = MutableStateFlow<List<BookInfo>>(emptyList())
    val bookSearchResults: StateFlow<List<BookInfo>> = _bookSearchResults.asStateFlow()

    private val _musicSearchResults = MutableStateFlow<List<MusicTrack>>(emptyList())
    val musicSearchResults: StateFlow<List<MusicTrack>> = _musicSearchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _locationSearchResults = MutableStateFlow<List<LocationSearchResult>>(emptyList())
    val locationSearchResults: StateFlow<List<LocationSearchResult>> = _locationSearchResults.asStateFlow()

    private var searchJob: Job? = null
    private val httpClient = OkHttpClient()

    fun addNote(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = title.ifBlank { "Not" }, content = content, entryType = EntryType.TEXT)) }
    }

    fun addMood(moodEmoji: String, rating: Int) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "Ruh Hali", content = "Bugün $moodEmoji hissediyorum.", entryType = EntryType.MOOD, moodRating = rating)) }
    }

    fun deleteEntry(entry: JournalEntryEntity) { viewModelScope.launch { journalDao.deleteEntry(entry) } }

    fun addPhotoEntry(context: Context, imageUris: List<Uri>, note: String) {
        viewModelScope.launch {
            val paths = imageUris.map { copyImageToInternalStorage(context, it) }
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = if (note.length > 30) note.take(30) else if (note.isNotBlank()) note else "Fotoğraf Anısı",
                    content = note,
                    entryType = EntryType.PHOTO,
                    imagePaths = paths.joinToString(",")
                )
            )
        }
    }

    fun saveTodoEntry(title: String, tasks: List<String>) {
        val content = tasks.joinToString("\n") { "[ ] $it" }
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = title.ifBlank { "Yapılacaklar" }, content = content, entryType = EntryType.TODO)) }
    }
    fun saveVoiceEntry(audioPath: String, durationMs: Long, note: String = "") {
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = if (note.isNotBlank()) note else "Ses Kaydı",
                    content = note,
                    entryType = EntryType.VOICE,
                    audioPath = audioPath
                )
            )
        }
    }

    fun saveExpenseEntry(amount: Double, category: String, note: String) {
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = "Harcama: \\(amount TL (\\)category)",
                    content = if (note.isNotBlank()) "\\(category -\\)note" else category,
                    entryType = EntryType.EXPENSE
                )
            )
        }
    }
    fun saveLinkEntry(title: String, url: String) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = title.ifBlank { "Bağlantı" }, content = url, entryType = EntryType.LINK)) }
    }

    fun saveMapLocation(lat: Double, lng: Double, address: String, note: String) {
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = address.ifBlank { "Seçilen Konum" },
                    content = note,
                    entryType = EntryType.LOCATION,
                    latitude = lat,
                    longitude = lng
                )
            )
        }
    }

    fun saveSelectedLocation(place: LocationSearchResult, note: String) {
        viewModelScope.launch {
            val locationContent = if (note.isNotBlank()) "$note\n\n${place.displayName}" else place.displayName
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = "📍 ${place.shortName}",
                    content = locationContent,
                    entryType = EntryType.LOCATION,
                    latitude = place.latitude,
                    longitude = place.longitude
                )
            )
        }
    }

    fun toggleTodoItem(entry: JournalEntryEntity, index: Int) {
        val lines = entry.content.split("\n").toMutableList()
        if (index in lines.indices) {
            lines[index] = if (lines[index].startsWith("[ ]")) lines[index].replaceFirst("[ ]", "[x]") else lines[index].replaceFirst("[x]", "[ ]")
            viewModelScope.launch { journalDao.updateEntry(entry.copy(content = lines.joinToString("\n"))) }
        }
    }

    fun saveDrawEntry(path: String) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "Çizim", content = "Çizim Kaydı", entryType = EntryType.DRAW, imagePaths = path)) }
    }

    fun saveSleepEntry(hours: Double, note: String) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "Uyku", content = "$hours saat uyku. $note", entryType = EntryType.SLEEP, sleepHours = hours)) }
    }

    fun saveCoffeeEntry(cups: Int, type: String) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "Kahve", content = "$cups fincan $type", entryType = EntryType.COFFEE, coffeeCups = cups)) }
    }

    fun saveAlcoholEntry(amount: String, type: String) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "Alkol", content = "$amount $type", entryType = EntryType.ALCOHOL, alcoholUnits = amount)) }
    }

    fun saveVideoEntry(videoUri: String, note: String) {
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = if (note.length > 30) note.take(30) else if (note.isNotBlank()) note else "Video Anısı",
                    content = note,
                    entryType = EntryType.VIDEO,
                    mediaUri = videoUri
                )
            )
        }
    }

    fun openVideo(context: Context, entry: JournalEntryEntity) {
        val uriStr = entry.mediaUri ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uriStr), "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Video açılamadı")
        }
    }

    fun openLink(context: Context, entry: JournalEntryEntity) {
        var url = entry.content
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Bağlantı açılamadı")
        }
    }

    fun openMap(context: Context, entry: JournalEntryEntity) {
        val lat = entry.latitude ?: return
        val lng = entry.longitude ?: return
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${entry.title})")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    }

    fun searchLocations(q: String) {
        searchJob?.cancel()
        if (q.isBlank() || q.length < 2) {
            _locationSearchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        _isSearching.value = true
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            try {
                val encodedQuery = URLEncoder.encode(q.trim(), "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=10&addressdetails=1&accept-language=tr"
                val request = Request.Builder().url(url).header("User-Agent", "LogioLifeJournalApp/1.0").build()
                httpClient.newCall(request).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string().orEmpty()
                        val jsonArr = JSONArray(body)
                        val results = mutableListOf<LocationSearchResult>()
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            val fullAddress = obj.optString("display_name", "Bilinmeyen Yer")
                            val shortTitle = fullAddress.split(",").firstOrNull()?.trim() ?: fullAddress
                            val lat = obj.optString("lat", "0.0").toDoubleOrNull() ?: 0.0
                            val lon = obj.optString("lon", "0.0").toDoubleOrNull() ?: 0.0
                            results.add(LocationSearchResult(fullAddress, shortTitle, lat, lon))
                        }
                        _locationSearchResults.value = results
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun analyzeAndSaveMeal(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val path = copyImageToInternalStorage(context, imageUri)
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
                }
                val res = if (bitmap != null) geminiService.analyzeMealImage(bitmap) else null
                journalDao.insertEntry(
                    JournalEntryEntity(
                        title = res?.mealName ?: "Öğün Kaydı",
                        content = if (res != null) "${res.calories} kcal" else "Besin analizi yapılamadı",
                        entryType = EntryType.MEAL,
                        imagePaths = path,
                        calories = res?.calories,
                        protein = res?.protein,
                        carbs = res?.carbs,
                        fat = res?.fat
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Meal analysis error")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun saveWaterEntry(amountMl: Int) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "Su Tüketimi", content = "$amountMl ml su içildi.", entryType = EntryType.WATER, waterAmountMl = amountMl)) }
    }

    fun searchMovies(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) {
            _movieSearchResults.value = emptyList()
            return
        }
        _isSearching.value = true
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            try {
                val res = httpClient.newCall(Request.Builder().url("https://api.tvmaze.com/search/shows?q=$q").build()).execute()
                if (res.isSuccessful) {
                    val arr = JSONArray(res.body?.string().orEmpty())
                    val results = (0 until arr.length()).map { i ->
                        val s = arr.getJSONObject(i).getJSONObject("show")
                        val imageObj = s.optJSONObject("image")
                        TvMazeShow(
                            name = s.getString("name"),
                            summary = s.optString("summary", "").replace(Regex("<[^>]*>"), ""),
                            imageUrl = imageObj?.optString("medium"),
                            rating = s.optJSONObject("rating")?.optString("average", "N/A")
                        )
                    }
                    _movieSearchResults.value = results
                }
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun saveFilmEntry(s: TvMazeShow, note: String) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "🎬 ${s.name}", content = "IMDB: ⭐ ${s.rating}\nNot: $note\n\n${s.summary}", entryType = EntryType.FILM, imagePaths = s.imageUrl)) }
    }

    fun searchBooks(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) {
            _bookSearchResults.value = emptyList()
            return
        }
        _isSearching.value = true
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            try {
                val encodedQuery = URLEncoder.encode(q.trim(), "UTF-8")
                val url = "https://openlibrary.org/search.json?q=$encodedQuery&limit=10&fields=title,author_name,cover_i,isbn,first_sentence,first_publish_year"
                val request = Request.Builder().url(url).header("User-Agent", "LogioApp/1.0").build()
                val res = httpClient.newCall(request).execute()
                if (res.isSuccessful) {
                    val jsonObject = JSONObject(res.body?.string().orEmpty())
                    val docs = jsonObject.optJSONArray("docs")
                    if (docs != null) {
                        val results = (0 until docs.length()).mapNotNull { i ->
                            val doc = docs.optJSONObject(i) ?: return@mapNotNull null
                            val authors = doc.optJSONArray("author_name")?.let { arr -> List(arr.length()) { arr.getString(it) }.joinToString(", ") } ?: "Bilinmeyen Yazar"
                            val coverId = doc.optLong("cover_i", -1L)
                            val coverUrl = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else null
                            BookInfo(doc.optString("title", "Bilinmeyen Kitap"), authors, doc.optJSONArray("first_sentence")?.optString(0) ?: "", coverUrl)
                        }
                        _bookSearchResults.value = results
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun saveBookEntry(b: BookInfo, note: String) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "📚 ${b.title}", content = """Yazar: ${b.authors}
                    Not: $note
${b.description}""".trimIndent(), entryType = EntryType.BOOK, imagePaths = b.imageUrl)) }
    }

    fun searchMusic(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) { _musicSearchResults.value = emptyList(); return }
        _isSearching.value = true
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            try {
                val res = httpClient.newCall(Request.Builder().url("https://itunes.apple.com/search?term=${URLEncoder.encode(q, "UTF-8")}&entity=song&limit=10").build()).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val items = JSONObject(body).getJSONArray("results")
                    val results = List(items.length()) { i ->
                        val item = items.getJSONObject(i)
                        val artwork = item.optString("artworkUrl100").replace("100x100bb", "600x600bb")
                        MusicTrack(item.getString("trackName"), item.getString("artistName"), item.optString("collectionName"), artwork)
                    }
                    _musicSearchResults.value = results
                }
            } catch (e: Exception) { Timber.e(e) } finally { _isSearching.value = false }
        }
    }

    fun saveMusicEntry(m: MusicTrack, note: String) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "🎵 ${m.trackName}", content = "Sanatçı: ${m.artistName}\nNot: $note", entryType = EntryType.MUSIC, imagePaths = m.imageUrl)) }
    }

    fun startVoiceRecording(onError: (String) -> Unit) { audioRecorderManager.startRecording(onSuccess = {}, onError = onError) }
    fun stopVoiceRecording(): AudioRecordResult? { return audioRecorderManager.stopRecording() }
    fun onVoicePlaybackClick(entry: JournalEntryEntity) {
        val p = entry.audioPath ?: return
        if (playingFilePath.value == p) audioPlayerManager.stop() else audioPlayerManager.play(p) { _playbackError.value = it }
    }
    fun clearPlaybackError() { _playbackError.value = null }
    fun startStepTracking() { stepCounterManager.startTracking() }
    fun stopStepTracking() { stepCounterManager.stopTracking() }
    fun clearStepError() { stepCounterManager.clearError() }
    fun stopVoicePlayback() { audioPlayerManager.stop() }

    override fun onCleared() {
        super.onCleared()
        audioPlayerManager.stop()
        stepCounterManager.stopTracking()
        if (isRecording.value) audioRecorderManager.stopRecording()
    }

    private suspend fun copyImageToInternalStorage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg")
        inputStream?.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
        file.absolutePath
    }
}
