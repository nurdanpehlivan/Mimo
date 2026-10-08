package com.example.logio.ui.timeline

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.logio.data.audio.AudioPlayerManager
import com.example.logio.data.audio.AudioRecordResult
import com.example.logio.data.audio.AudioRecorderManager
import com.example.logio.data.health.HealthConnectManager
import com.example.logio.data.health.SleepSummary
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalDao
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.profile.ProfileRepository
import com.example.logio.data.profile.UserProfile
import com.example.logio.data.remote.GeminiQuotaException
import com.example.logio.data.remote.GeminiService
import com.example.logio.data.remote.LinkPreviewFetcher
import com.example.logio.ui.share.youtubeThumbnailUrl
import com.example.logio.data.steps.StepCounterManager
import com.example.logio.util.JourneyVideoMaker
import com.example.logio.util.JourneyTemplate
import com.example.logio.util.AppLocale
import com.example.logio.R
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.example.logio.util.NoJourneyPhotosException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// Eski ses kayıtlarında içerik olarak kaydedilen yer tutucu metin
const val VOICE_PLACEHOLDER = "Sesli Not"

sealed interface HealthSleepState {
    data object Loading : HealthSleepState
    data object NotAvailable : HealthSleepState
    data object NoPermission : HealthSleepState
    data object NoData : HealthSleepState
    data class Found(val summary: SleepSummary) : HealthSleepState
}

data class TvMazeShow(val name: String, val summary: String, val imageUrl: String?, val rating: String?)
data class BookInfo(val title: String, val authors: String, val description: String, val imageUrl: String?)
data class MusicTrack(val trackName: String, val artistName: String, val collectionName: String?, val imageUrl: String?)
data class LocationSearchResult(val displayName: String, val shortName: String, val latitude: Double, val longitude: Double)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journalDao: JournalDao,
    private val geminiService: GeminiService,
    private val audioRecorderManager: AudioRecorderManager,
    private val audioPlayerManager: AudioPlayerManager,
    private val stepCounterManager: StepCounterManager,
    val healthConnectManager: HealthConnectManager,
    private val profileRepository: ProfileRepository,
    private val linkPreviewFetcher: LinkPreviewFetcher
) : ViewModel() {

    val entries: StateFlow<List<JournalEntryEntity>> = journalDao.getAllEntries()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    val profile: StateFlow<UserProfile> = profileRepository.profile

    fun updateProfile(profile: UserProfile) = profileRepository.update(profile)

    init {
        backfillLinkPreviews()
    }

    // Uygulama bağlamı Android 13 altında dil değişikliğini görmez; her seferinde seçili dile sarılır
    private fun str(@StringRes id: Int, vararg args: Any): String = AppLocale.wrap(context).getString(id, *args)

    private fun plural(@PluralsRes id: Int, count: Int): String =
        AppLocale.wrap(context).resources.getQuantityString(id, count, count)

    private fun journeyContent(templateName: String, photoCount: Int): String =
        "${str(JourneyTemplate.fromName(templateName).label)} · ${plural(R.plurals.photo_count, photoCount)}"

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

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()
    private val _locationSearchResults = MutableStateFlow<List<LocationSearchResult>>(emptyList())
    val locationSearchResults: StateFlow<List<LocationSearchResult>> = _locationSearchResults.asStateFlow()

    private val _transcribingEntryIds = MutableStateFlow<Set<Long>>(emptySet())
    val transcribingEntryIds: StateFlow<Set<Long>> = _transcribingEntryIds.asStateFlow()

    private val _healthSleepState = MutableStateFlow<HealthSleepState>(HealthSleepState.Loading)
    val healthSleepState: StateFlow<HealthSleepState> = _healthSleepState.asStateFlow()

    private var searchJob: Job? = null
    private val httpClient = OkHttpClient()

    fun addNote(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = title.ifBlank { str(R.string.action_note) }, content = content, entryType = EntryType.TEXT)) }
    }

    fun addMood(moodEmoji: String, rating: Int) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = str(R.string.action_mood), content = str(R.string.entry_mood_content, moodEmoji), entryType = EntryType.MOOD, moodRating = rating)) }
    }

    fun deleteEntry(entry: JournalEntryEntity) { viewModelScope.launch { journalDao.deleteEntry(entry) } }

    fun addPhotoEntry(context: Context, imageUris: List<Uri>, note: String) {
        viewModelScope.launch {
            val paths = imageUris.map { copyImageToInternalStorage(context, it) }
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = if (note.length > 30) note.take(30) else if (note.isNotBlank()) note else str(R.string.dlg_photo_memory),
                    content = note,
                    entryType = EntryType.PHOTO,
                    imagePaths = paths.joinToString(",")
                )
            )
        }
    }

    fun saveTodoEntry(title: String, tasks: List<String>) {
        val content = tasks.joinToString("\n") { "[ ] $it" }
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = title.ifBlank { str(R.string.action_todo) }, content = content, entryType = EntryType.TODO)) }
    }

    fun saveVoiceEntry(filePath: String, durationMs: Long, transcription: String = "") {
        viewModelScope.launch {
            val entry = JournalEntryEntity(
                title = str(R.string.type_voice),
                entryType = EntryType.VOICE,
                audioPath = filePath,
                content = transcription,
                createdAt = System.currentTimeMillis()
            )
            journalDao.insertEntry(entry)
        }
    }

    // Kategori anahtar olarak saklanır (istatistikte gruplamak için); başlık kategori adı ya da açıklama
    fun saveExpenseEntry(amount: Double, categoryKey: String, note: String) {
        val category = ExpenseCategory.fromKey(categoryKey)
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = note.ifBlank { "${category.emoji} ${str(category.label)}" },
                    content = if (note.isBlank()) "" else "${category.emoji} ${str(category.label)}",
                    entryType = EntryType.EXPENSE,
                    expenseAmount = amount,
                    expenseCategory = category.key
                )
            )
        }
    }

    // Link hemen kaydedilir; önizleme görseli (ve başlık boşsa sayfa başlığı) arka planda eklenir
    fun saveLinkEntry(title: String, url: String) {
        viewModelScope.launch {
            val entry = JournalEntryEntity(
                title = title.ifBlank { str(R.string.action_link) },
                content = url,
                entryType = EntryType.LINK,
                imagePaths = youtubeThumbnailUrl(url)
            )
            val id = journalDao.insertEntry(entry)
            enrichLinkPreview(entry.copy(id = id), replaceTitle = title.isBlank())
        }
    }

    private suspend fun enrichLinkPreview(entry: JournalEntryEntity, replaceTitle: Boolean) {
        val preview = linkPreviewFetcher.fetch(entry.content.trim()) ?: return
        // Kullanıcı bu arada kaydı silmiş ya da değiştirmiş olabilir; güncel halini al
        val current = journalDao.getEntryById(entry.id) ?: return
        val updated = current.copy(
            imagePaths = current.imagePaths ?: preview.imageUrl,
            title = if (replaceTitle && preview.title != null) preview.title else current.title
        )
        if (updated != current) journalDao.updateEntry(updated)
    }

    // Önizleme özelliğinden önce eklenen linkler için görselleri bir kez tamamla
    private fun backfillLinkPreviews() {
        viewModelScope.launch {
            journalDao.getEntriesByType(EntryType.LINK).first()
                .filter { it.imagePaths.isNullOrBlank() }
                .take(20)
                .forEach { enrichLinkPreview(it, replaceTitle = false) }
        }
    }

    fun saveMapLocation(lat: Double, lng: Double, address: String, note: String) {
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = address.ifBlank { str(R.string.entry_selected_location) },
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
            val line = lines[index]
            lines[index] = when {
                line.startsWith("[ ]") -> line.replaceFirst("[ ]", "[x]")
                line.startsWith("[x]") -> line.replaceFirst("[x]", "[ ]")
                else -> line
            }
            viewModelScope.launch { 
                journalDao.updateEntry(entry.copy(content = lines.joinToString("\n"))) 
            }
        }
    }

    fun saveDrawEntry(path: String) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = str(R.string.action_draw), content = str(R.string.entry_drawing_content), entryType = EntryType.DRAW, imagePaths = path)) }
    }

    fun saveSleepEntry(hours: Double, note: String) {
        val content = listOf(str(R.string.entry_sleep_manual, hours.toString()), note.trim()).filter { it.isNotBlank() }.joinToString(" ")
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = str(R.string.action_sleep), content = content, entryType = EntryType.SLEEP, sleepHours = hours)) }
    }

    fun loadHealthSleep() {
        viewModelScope.launch {
            _healthSleepState.value = HealthSleepState.Loading
            _healthSleepState.value = try {
                when {
                    !healthConnectManager.isAvailable() -> HealthSleepState.NotAvailable
                    !healthConnectManager.hasSleepPermission() -> HealthSleepState.NoPermission
                    else -> healthConnectManager.readLastNightSleep()
                        ?.let { HealthSleepState.Found(it) }
                        ?: HealthSleepState.NoData
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Health Connect uyku verisi okunamadı")
                HealthSleepState.NoData
            }
        }
    }

    fun saveHealthSleepEntry(summary: SleepSummary, note: String = "") {
        viewModelScope.launch { insertHealthSleepIfNew(summary, note) }
    }

    // Health Connect uykusu uyanma anına kaydedilir; aynı oturum iki kez eklenmez
    private suspend fun insertHealthSleepIfNew(summary: SleepSummary, note: String): Boolean {
        val wakeMillis = summary.endTime.toEpochMilli()
        val alreadySaved = journalDao.getEntriesByType(EntryType.SLEEP).first()
            .any { it.createdAt == wakeMillis }
        if (alreadySaved) return false

        val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        val hoursText = String.format(Locale.getDefault(), "%.1f", summary.hours)
        val content = listOf(
            str(R.string.entry_sleep_health, hoursText, timeFormat.format(summary.startTime), timeFormat.format(summary.endTime)),
            note.trim()
        ).filter { it.isNotBlank() }.joinToString(" ")

        journalDao.insertEntry(
            JournalEntryEntity(
                title = str(R.string.entry_night_sleep),
                content = content,
                entryType = EntryType.SLEEP,
                sleepHours = summary.hours,
                createdAt = wakeMillis
            )
        )
        return true
    }

    fun saveCoffeeEntry(cups: Int, type: String) {
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = type.ifBlank { str(R.string.action_coffee) },
                    content = plural(R.plurals.cups_count, cups),
                    entryType = EntryType.COFFEE,
                    coffeeCups = cups
                )
            )
        }
    }

    // Kadeh sayısı alcoholUnits alanında sayı olarak saklanır (istatistik için)
    fun saveAlcoholEntry(amount: String, type: String) {
        val glasses = parseAlcoholDrinks(amount)?.toInt() ?: 1
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = type.ifBlank { str(R.string.action_alcohol) },
                    content = plural(R.plurals.glasses_count, glasses),
                    entryType = EntryType.ALCOHOL,
                    alcoholUnits = amount
                )
            )
        }
    }

    fun saveVideoEntry(videoUri: String, note: String) {
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = if (note.length > 30) note.take(30) else if (note.isNotBlank()) note else str(R.string.dlg_video_memory),
                    content = note,
                    entryType = EntryType.VIDEO,
                    mediaUri = videoUri
                )
            )
        }
    }

    fun openVideo(context: Context, entry: JournalEntryEntity) {
        Log.d("LOGIO_VIDEO", "1. openVideo tetiklendi! mediaUri: ${entry.mediaUri}")

        val uriStr = entry.mediaUri

        if (uriStr.isNullOrBlank()) {
            Log.e("LOGIO_VIDEO", "HATA: entry.mediaUri boş veya null!")
            return
        }

        try {
            val uri: Uri

            if (uriStr.startsWith("content://")) {
                Log.d("LOGIO_VIDEO", "2. content:// formatı tespit edildi")
                uri = Uri.parse(uriStr)
            } else {
                val cleanPath = uriStr.removePrefix("file://")
                val file = File(cleanPath)

                Log.d(
                    "LOGIO_VIDEO",
                    "2. Dosya yolu: $cleanPath, " +
                            "Dosya var mı?: ${file.exists()}, " +
                            "Boyut: ${file.length()} byte"
                )

                if (!file.exists()) {
                    Log.e("LOGIO_VIDEO", "HATA: Video dosyası bulunamadı!")
                    return
                }

                uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                Log.d("LOGIO_VIDEO", "3. FileProvider Uri başarıyla üretildi: $uri")
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            Log.d("LOGIO_VIDEO", "4. Intent başlatılıyor...")
            context.startActivity(intent)
            Log.d("LOGIO_VIDEO", "5. Intent başarıyla gönderildi!")

        } catch (e: Exception) {
            Log.e("LOGIO_VIDEO", "HATA: Video oynatıcı başlatılamadı: ${e.message}", e)
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
            _analysisError.value = null

            try {
                val path = copyImageToInternalStorage(context, imageUri)

                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(imageUri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }

                if (bitmap == null) {
                    _analysisError.value = str(R.string.err_photo_read)
                    return@launch
                }

                val res = try {
                    geminiService.analyzeMealImage(bitmap)
                } catch (e: GeminiQuotaException) {
                    Timber.e(e, "Gemini kota hatası")
                    _analysisError.value = str(R.string.err_ai_quota)
                    return@launch
                }

                if (res == null) {
                    _analysisError.value = str(R.string.err_meal_failed)
                    return@launch
                }

                journalDao.insertEntry(
                    JournalEntryEntity(
                        title = res.mealName.ifBlank { str(R.string.type_meal) },
                        content = "${res.calories} kcal",
                        entryType = EntryType.MEAL,
                        imagePaths = path,
                        calories = res.calories,
                        protein = res.protein,
                        carbs = res.carbs,
                        fat = res.fat
                    )
                )

                Timber.d("Yemek Timeline'a kaydedildi: ${res.mealName}")

            } catch (e: Exception) {
                Timber.e(e, "Meal analysis error")
                _analysisError.value = str(R.string.err_meal_error)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun saveWaterEntry(amountMl: Int) {
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = str(R.string.dlg_water), content = str(R.string.entry_water_content, amountMl), entryType = EntryType.WATER, waterAmountMl = amountMl)) }
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
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "🎬 ${s.name}", content = str(R.string.entry_film_content, s.rating ?: "-", note, s.summary), entryType = EntryType.FILM, imagePaths = s.imageUrl)) }
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
                            val authors = doc.optJSONArray("author_name")?.let { arr -> List(arr.length()) { arr.getString(it) }.joinToString(", ") } ?: str(R.string.entry_unknown_author)
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
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "📚 ${b.title}", content = str(R.string.entry_book_content, b.authors, note, b.description), entryType = EntryType.BOOK, imagePaths = b.imageUrl)) }
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
        viewModelScope.launch { journalDao.insertEntry(JournalEntryEntity(title = "🎵 ${m.trackName}", content = str(R.string.entry_music_content, m.artistName, note), entryType = EntryType.MUSIC, imagePaths = m.imageUrl)) }
    }

    fun startVoiceRecording(onError: (String) -> Unit) { audioRecorderManager.startRecording(onSuccess = {}, onError = onError) }

    fun stopVoiceRecording(): AudioRecordResult? {
        val result = audioRecorderManager.stopRecording()

        if (result == null) {
            Log.e("LOGIO_STT", "HATA: audioRecorderManager null dondu!")
            return null
        }

        // Sadece yerel ses dosyasını kaydet; yazıya çevirme kullanıcı isteğiyle yapılır
        saveVoiceEntry(filePath = result.filePath, durationMs = result.durationMs)
        return result
    }

    fun transcribeVoiceEntry(entry: JournalEntryEntity) {
        val path = entry.audioPath ?: return
        if (entry.id in _transcribingEntryIds.value) return

        _transcribingEntryIds.value = _transcribingEntryIds.value + entry.id
        viewModelScope.launch {
            try {
                val transcription = geminiService.transcribeAudio(path)?.trim()
                if (transcription.isNullOrBlank()) {
                    _playbackError.value = str(R.string.err_transcribe_failed)
                    return@launch
                }

                val current = entries.value.firstOrNull { it.id == entry.id } ?: entry
                val existing = current.content.trim()
                val newContent = if (existing.isBlank() || existing == VOICE_PLACEHOLDER) {
                    transcription
                } else {
                    "$existing\n\n$transcription"
                }
                journalDao.updateEntry(current.copy(content = newContent))
            } catch (e: GeminiQuotaException) {
                _playbackError.value = str(R.string.err_transcribe_quota)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LOGIO_STT", "Yazıya çevirme hatası: ${e.message}", e)
                _playbackError.value = str(R.string.err_transcribe_error)
            } finally {
                _transcribingEntryIds.value = _transcribingEntryIds.value - entry.id
            }
        }
    }

    fun onVoicePlaybackClick(entry: JournalEntryEntity) {
        val p = entry.audioPath ?: return
        if (playingFilePath.value == p) audioPlayerManager.stop() else audioPlayerManager.play(p) { _playbackError.value = it }
    }

    fun clearPlaybackError() { _playbackError.value = null }
    fun clearAnalysisError() { _analysisError.value = null }
    fun startStepTracking() { stepCounterManager.startTracking() }
    fun stopStepTracking() { stepCounterManager.stopTracking() }
    fun clearStepError() { stepCounterManager.clearError() }
    fun stopVoicePlayback() { audioPlayerManager.stop() }

    fun createJourneyVideo(title: String, startDate: Long, endDate: Long, theme: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true

            try {
                // Bitiş gününün sonuna (23:59:59.999) kadar kapsa
                val adjustedEndDate = endDate + (24 * 60 * 60 * 1000L - 1L)

                // Aralıktaki fotoğraflı kayıtlar, eskiden yeniye (hikâye sırası)
                val candidatePaths = entries.value
                    .filter { !it.imagePaths.isNullOrBlank() && it.createdAt in startDate..adjustedEndDate }
                    .sortedBy { it.createdAt }
                    .flatMap { it.imagePaths.orEmpty().split(",") }

                // Yalnızca diskte gerçekten var olan, boş olmayan fotoğraflar.
                // Çok fazlaysa aralığa eşit yayılmış en fazla 40 tanesi (~100 sn video)
                val allPhotos = JourneyVideoMaker.existingPhotoFiles(candidatePaths)
                val maxPhotos = 40
                val photos = if (allPhotos.size <= maxPhotos) allPhotos else
                    List(maxPhotos) { i -> allPhotos[i * (allPhotos.size - 1) / (maxPhotos - 1)] }
                Log.d("JOURNEY_DEBUG", "Şablon: $theme, aday: ${candidatePaths.size}, geçerli fotoğraf: ${photos.size}")

                if (photos.isEmpty()) {
                    _playbackError.value = str(R.string.err_journey_no_photos_range)
                    return@launch
                }

                val videoFile = File(context.filesDir, "journey_${System.currentTimeMillis()}.mp4")
                JourneyVideoMaker(context).generateVideo(photos.map { it.absolutePath }, videoFile, theme)

                journalDao.insertEntry(
                    JournalEntryEntity(
                        title = title,
                        content = journeyContent(theme, photos.size),
                        entryType = EntryType.JOURNEY_VIDEO,
                        imagePaths = photos.first().absolutePath,
                        mediaUri = Uri.fromFile(videoFile).toString(),
                        createdAt = System.currentTimeMillis()
                    )
                )
                Log.d("JOURNEY_DEBUG", "Journey Video Timeline'a kaydedildi.")

            } catch (e: NoJourneyPhotosException) {
                _playbackError.value = str(R.string.err_journey_no_photos)
            } catch (e: Exception) {
                Timber.e(e, "Journey videosu oluşturulamadı")
                Log.e("JOURNEY_DEBUG", "Journey Video HATASI: ${e.message}", e)
                _playbackError.value = str(R.string.err_journey_failed)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * Kullanıcının seçtiği fotoğraflardan, seçtiği sırayla video üretir.
     * Galeriden gelen content:// adresleri önce uygulama klasörüne kopyalanır
     * (seçici izni geçicidir ve video kapağı kalıcı bir dosya ister).
     */
    fun createJourneyVideoFromPhotos(title: String, photos: List<String>, theme: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val paths = withContext(Dispatchers.IO) {
                    val pickDir = File(context.filesDir, "journey_picks").apply { mkdirs() }
                    photos.mapIndexedNotNull { index, source ->
                        if (!source.startsWith("content://")) return@mapIndexedNotNull source
                        runCatching {
                            val target = File(pickDir, "pick_${System.currentTimeMillis()}_$index.jpg")
                            context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                                FileOutputStream(target).use { output -> input.copyTo(output) }
                            }
                            target.absolutePath
                        }.onFailure { Timber.w(it, "Galeri fotoğrafı kopyalanamadı: $source") }.getOrNull()
                    }
                }
                val files = JourneyVideoMaker.existingPhotoFiles(paths)
                if (files.isEmpty()) {
                    _playbackError.value = str(R.string.err_journey_photos_unreadable)
                    return@launch
                }

                val videoFile = File(context.filesDir, "journey_${System.currentTimeMillis()}.mp4")
                JourneyVideoMaker(context).generateVideo(files.map { it.absolutePath }, videoFile, theme)

                journalDao.insertEntry(
                    JournalEntryEntity(
                        title = title,
                        content = journeyContent(theme, files.size),
                        entryType = EntryType.JOURNEY_VIDEO,
                        imagePaths = files.first().absolutePath,
                        mediaUri = Uri.fromFile(videoFile).toString(),
                        createdAt = System.currentTimeMillis()
                    )
                )
            } catch (e: NoJourneyPhotosException) {
                _playbackError.value = str(R.string.err_journey_no_photos)
            } catch (e: Exception) {
                Timber.e(e, "Seçili fotoğraflardan video oluşturulamadı")
                _playbackError.value = str(R.string.err_journey_failed)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun syncHealthData() {
        viewModelScope.launch {
            try {
                if (healthConnectManager.hasAllPermissions()) {
                    val now = Instant.now()
                    val zoneId = ZoneId.systemDefault()
                    val startOfDay = ZonedDateTime.now(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()

                    val steps = healthConnectManager.readSteps(startOfDay, now)
                    upsertTodaySteps(steps, zoneId)

                    healthConnectManager.readLastNightSleep(now)?.let { summary ->
                        insertHealthSleepIfNew(summary, note = "")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Sağlık verisi çekilirken hata oluştu")
            }
        }
    }

    // Adım gün boyu biriken bir toplam: gün başına tek kayıt tutulur, her eşitlemede güncellenir.
    // Eski sürümün her eşitlemede eklediği kopyalar da burada temizlenir (her gün için en son kayıt kalır).
    private suspend fun upsertTodaySteps(steps: Long, zoneId: ZoneId) {
        val stepEntriesByDay = journalDao.getEntriesByType(EntryType.STEPS).first()
            .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zoneId).toLocalDate() }
        stepEntriesByDay.values.forEach { dayEntries ->
            dayEntries.sortedByDescending { it.createdAt }.drop(1).forEach { journalDao.deleteEntry(it) }
        }

        if (steps <= 0) return
        val content = str(R.string.entry_steps_content, steps)
        val existing = stepEntriesByDay[LocalDate.now(zoneId)]?.maxByOrNull { it.createdAt }
        if (existing == null) {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = str(R.string.entry_steps_title),
                    content = content,
                    entryType = EntryType.STEPS,
                    createdAt = System.currentTimeMillis()
                )
            )
        } else {
            journalDao.updateEntry(existing.copy(content = content, createdAt = System.currentTimeMillis()))
        }
    }

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