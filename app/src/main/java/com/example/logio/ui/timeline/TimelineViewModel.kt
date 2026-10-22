/*
 * TimelineViewModel: Günlük (zaman tüneli) ekranının ViewModel'i.
 *
 * Görevleri:
 *  - Kayıtları JournalRepository'den okuyup ekrana vermek (entries)
 *  - "+" menüsündeki pencerelerden gelen kayıtları oluşturmak (not, kahve, su, uyku, harcama, film...)
 *  - Yemek fotoğrafını Gemini ile analiz etmek (premium yoksa günde MealAnalysisQuota.FREE_DAILY_LIMIT kez);
 *    sesli not kaydetmek, çalmak, yazıya çevirmek
 *  - Health Connect'ten adım ve uyku eşitlemek (adımın tek kaynağı Health Connect'tir), Journey videosu üretmek
 * Aramalar SearchViewModel'de, istatistik AnalysisViewModel'de, takvim CalendarViewModel'de,
 * profil ProfileViewModel'dedir.
 */
package com.example.logio.ui.timeline

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.logio.data.audio.AudioPlayerManager
import com.example.logio.data.audio.AudioRecordResult
import com.example.logio.data.audio.AudioRecorderManager
import com.example.logio.data.audio.VoiceTranscriber
import com.example.logio.data.health.HealthConnectManager
import com.example.logio.data.health.SleepSummary
import com.example.logio.data.journal.JournalRepository
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.profile.ProfileRepository
import com.example.logio.data.profile.UserProfile
import com.example.logio.data.premium.MealAnalysisQuota
import com.example.logio.data.premium.PremiumManager
import com.example.logio.data.remote.GeminiQuotaException
import com.example.logio.data.remote.GeminiService
import com.example.logio.data.remote.LinkPreviewFetcher
import com.example.logio.data.search.BookInfo
import com.example.logio.data.search.LocationSearchResult
import com.example.logio.data.search.MusicTrack
import com.example.logio.data.search.TvMazeShow
import com.example.logio.ui.share.youtubeThumbnailUrl
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

sealed interface HealthSleepState {
    data object Loading : HealthSleepState
    data object NotAvailable : HealthSleepState
    data object NoPermission : HealthSleepState
    data object NoData : HealthSleepState
    data class Found(val summary: SleepSummary) : HealthSleepState
}

// Health Connect eşitlemesinde okunan gün sayısı (bugün dahil)
private const val STEP_SYNC_DAYS = 7

@HiltViewModel
class TimelineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journalRepository: JournalRepository,
    private val geminiService: GeminiService,
    private val voiceTranscriber: VoiceTranscriber,
    private val audioRecorderManager: AudioRecorderManager,
    private val audioPlayerManager: AudioPlayerManager,
    val healthConnectManager: HealthConnectManager,
    profileRepository: ProfileRepository,
    private val linkPreviewFetcher: LinkPreviewFetcher,
    premiumManager: PremiumManager,
    private val mealAnalysisQuota: MealAnalysisQuota
) : ViewModel() {

    val entries: StateFlow<List<JournalEntryEntity>> = journalRepository.entries
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    // Şifreli veritabanı açılırken ilk liste birkaç saniye gecikebilir; o arada "Henüz kayıt yok" gösterilmesin
    val entriesLoaded: StateFlow<Boolean> = journalRepository.entries
        .map { true }
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = false)

    val profile: StateFlow<UserProfile> = profileRepository.profile

    val isPremium: StateFlow<Boolean> = premiumManager.isPremium

    /** Öğün analizi yapılabilir mi: premium'da her zaman, ücretsizde günlük hak bitene kadar. */
    fun canAnalyzeMeal(): Boolean = isPremium.value || mealAnalysisQuota.remainingToday() > 0

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

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    val transcribingEntryIds: StateFlow<Set<Long>> = voiceTranscriber.transcribingEntryIds

    private val _healthSleepState = MutableStateFlow<HealthSleepState>(HealthSleepState.Loading)
    val healthSleepState: StateFlow<HealthSleepState> = _healthSleepState.asStateFlow()

    fun addNote(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch { journalRepository.insert(JournalEntryEntity(title = title.ifBlank { str(R.string.action_note) }, content = content, entryType = EntryType.TEXT)) }
    }

    fun addMood(moodEmoji: String, rating: Int) {
        viewModelScope.launch { journalRepository.insert(JournalEntryEntity(title = str(R.string.action_mood), content = str(R.string.entry_mood_content, moodEmoji), entryType = EntryType.MOOD, moodRating = rating)) }
    }

    fun deleteEntry(entry: JournalEntryEntity) { viewModelScope.launch { journalRepository.delete(entry) } }

    fun addPhotoEntry(context: Context, imageUris: List<Uri>, note: String) {
        viewModelScope.launch {
            val paths = imageUris.map { copyImageToInternalStorage(context, it) }
            journalRepository.insert(
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
        viewModelScope.launch { journalRepository.insert(JournalEntryEntity(title = title.ifBlank { str(R.string.action_todo) }, content = content, entryType = EntryType.TODO)) }
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
            journalRepository.insert(entry)
        }
    }

    // Kategori anahtar olarak saklanır (istatistikte gruplamak için); başlık kategori adı ya da açıklama
    fun saveExpenseEntry(amount: Double, categoryKey: String, note: String) {
        val category = ExpenseCategory.fromKey(categoryKey)
        viewModelScope.launch {
            journalRepository.insert(
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
            val id = journalRepository.insert(entry)
            enrichLinkPreview(entry.copy(id = id), replaceTitle = title.isBlank())
        }
    }

    private suspend fun enrichLinkPreview(entry: JournalEntryEntity, replaceTitle: Boolean) {
        val preview = linkPreviewFetcher.fetch(entry.content.trim()) ?: return
        // Kullanıcı bu arada kaydı silmiş ya da değiştirmiş olabilir; güncel halini al
        val current = journalRepository.getById(entry.id) ?: return
        val updated = current.copy(
            imagePaths = current.imagePaths ?: preview.imageUrl,
            title = if (replaceTitle && preview.title != null) preview.title else current.title
        )
        if (updated != current) journalRepository.update(updated)
    }

    // Önizleme özelliğinden önce eklenen linkler için görselleri bir kez tamamla
    private fun backfillLinkPreviews() {
        viewModelScope.launch {
            journalRepository.entriesOfType(EntryType.LINK).first()
                .filter { it.imagePaths.isNullOrBlank() }
                .take(20)
                .forEach { enrichLinkPreview(it, replaceTitle = false) }
        }
    }

    fun saveMapLocation(lat: Double, lng: Double, address: String, note: String) {
        viewModelScope.launch {
            journalRepository.insert(
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
            journalRepository.insert(
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
        viewModelScope.launch { journalRepository.toggleTodoItem(entry, index) }
    }

    fun saveDrawEntry(path: String) {
        viewModelScope.launch { journalRepository.insert(JournalEntryEntity(title = str(R.string.action_draw), content = str(R.string.entry_drawing_content), entryType = EntryType.DRAW, imagePaths = path)) }
    }

    fun saveSleepEntry(hours: Double, note: String) {
        val content = listOf(str(R.string.entry_sleep_manual, hours.toString()), note.trim()).filter { it.isNotBlank() }.joinToString(" ")
        viewModelScope.launch { journalRepository.insert(JournalEntryEntity(title = str(R.string.action_sleep), content = content, entryType = EntryType.SLEEP, sleepHours = hours)) }
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
        val alreadySaved = journalRepository.entriesOfType(EntryType.SLEEP).first()
            .any { it.createdAt == wakeMillis }
        if (alreadySaved) return false

        val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        val hoursText = String.format(Locale.getDefault(), "%.1f", summary.hours)
        val content = listOf(
            str(R.string.entry_sleep_health, hoursText, timeFormat.format(summary.startTime), timeFormat.format(summary.endTime)),
            note.trim()
        ).filter { it.isNotBlank() }.joinToString(" ")

        journalRepository.insert(
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
            journalRepository.insert(
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
            journalRepository.insert(
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
            journalRepository.insert(
                JournalEntryEntity(
                    title = if (note.length > 30) note.take(30) else if (note.isNotBlank()) note else str(R.string.dlg_video_memory),
                    content = note,
                    entryType = EntryType.VIDEO,
                    mediaUri = videoUri
                )
            )
        }
    }

    fun analyzeAndSaveMeal(context: Context, imageUri: Uri) {
        // Ekran hakkı fotoğraf seçtirmeden önce kontrol eder; bu yalnızca ikinci bir güvence
        if (!canAnalyzeMeal()) return
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

                journalRepository.insert(
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

                if (!isPremium.value) mealAnalysisQuota.recordUse()
                Timber.d("Yemek analizi kaydedildi")

            } catch (e: Exception) {
                Timber.e(e, "Meal analysis error")
                _analysisError.value = str(R.string.err_meal_error)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun saveWaterEntry(amountMl: Int) {
        viewModelScope.launch { journalRepository.insert(JournalEntryEntity(title = str(R.string.dlg_water), content = str(R.string.entry_water_content, amountMl), entryType = EntryType.WATER, waterAmountMl = amountMl)) }
    }

    fun saveFilmEntry(s: TvMazeShow, note: String) {
        viewModelScope.launch { journalRepository.insert(JournalEntryEntity(title = "🎬 ${s.name}", content = str(R.string.entry_film_content, s.rating ?: "-", note, s.summary), entryType = EntryType.FILM, imagePaths = s.imageUrl)) }
    }

    fun saveBookEntry(b: BookInfo, note: String) {
        viewModelScope.launch { journalRepository.insert(JournalEntryEntity(title = "📚 ${b.title}", content = str(R.string.entry_book_content, b.authors, note, b.description), entryType = EntryType.BOOK, imagePaths = b.imageUrl)) }
    }

    fun saveMusicEntry(m: MusicTrack, note: String) {
        viewModelScope.launch { journalRepository.insert(JournalEntryEntity(title = "🎵 ${m.trackName}", content = str(R.string.entry_music_content, m.artistName, note), entryType = EntryType.MUSIC, imagePaths = m.imageUrl)) }
    }

    fun startVoiceRecording(onError: (String) -> Unit) { audioRecorderManager.startRecording(onSuccess = {}, onError = onError) }

    fun stopVoiceRecording(): AudioRecordResult? {
        val result = audioRecorderManager.stopRecording()

        if (result == null) {
            Timber.e("Ses kaydı durdurulamadı: kayıt sonucu boş")
            return null
        }

        // Sadece yerel ses dosyasını kaydet; yazıya çevirme kullanıcı isteğiyle yapılır
        saveVoiceEntry(filePath = result.filePath, durationMs = result.durationMs)
        return result
    }

    fun transcribeVoiceEntry(entry: JournalEntryEntity) {
        viewModelScope.launch {
            voiceTranscriber.transcribe(entry)?.let { _playbackError.value = str(it) }
        }
    }

    fun onVoicePlaybackClick(entry: JournalEntryEntity) {
        val path = entry.audioPath ?: return
        audioPlayerManager.toggle(path) { _playbackError.value = it }
    }

    fun clearPlaybackError() { _playbackError.value = null }
    fun clearAnalysisError() { _analysisError.value = null }

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
                Timber.d("Şablon: $theme, aday: ${candidatePaths.size}, geçerli fotoğraf: ${photos.size}")

                if (photos.isEmpty()) {
                    _playbackError.value = str(R.string.err_journey_no_photos_range)
                    return@launch
                }

                val videoFile = File(context.filesDir, "journey_${System.currentTimeMillis()}.mp4")
                JourneyVideoMaker(context).generateVideo(photos.map { it.absolutePath }, videoFile, theme)

                journalRepository.insert(
                    JournalEntryEntity(
                        title = title,
                        content = journeyContent(theme, photos.size),
                        entryType = EntryType.JOURNEY_VIDEO,
                        imagePaths = photos.first().absolutePath,
                        mediaUri = Uri.fromFile(videoFile).toString(),
                        createdAt = System.currentTimeMillis()
                    )
                )
                Timber.d("Journey Video Timeline'a kaydedildi.")

            } catch (e: NoJourneyPhotosException) {
                _playbackError.value = str(R.string.err_journey_no_photos)
            } catch (e: Exception) {
                Timber.e(e, "Journey videosu oluşturulamadı")
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

                journalRepository.insert(
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
            // Adım ve uyku birbirinden bağımsız okunur: her biri yalnızca kendi iznine bakar ve izin yoksa
            // boş döner. Kullanıcı sadece adıma izin verdiyse adımlar yine eşitlenir.
            try {
                val zoneId = ZoneId.systemDefault()
                val today = ZonedDateTime.now(zoneId).toLocalDate()

                // Son 7 gün tek istekte, her biri tam gün olarak okunur: eski sürümün yazdığı eksik değerler düzelir,
                // uygulamanın açılmadığı günler tamamlanır (neden tam gün: HealthConnectManager.readDailySteps)
                val dailySteps = healthConnectManager.readDailySteps(today.minusDays(STEP_SYNC_DAYS - 1L), today)
                if (dailySteps.isNotEmpty()) upsertDailySteps(dailySteps, today, zoneId)

                healthConnectManager.readLastNightSleep(Instant.now())?.let { summary ->
                    insertHealthSleepIfNew(summary, note = "")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Sağlık verisi çekilirken hata oluştu")
            }
        }
    }

    // Adım gün boyu biriken bir toplam: gün başına tek kayıt tutulur, her eşitlemede güncellenir.
    // Eski sürümün her eşitlemede eklediği kopyalar da burada temizlenir (her gün için en son kayıt kalır).
    private suspend fun upsertDailySteps(dailySteps: Map<LocalDate, Long>, today: LocalDate, zoneId: ZoneId) {
        val stepEntriesByDay = journalRepository.entriesOfType(EntryType.STEPS).first()
            .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zoneId).toLocalDate() }
        stepEntriesByDay.values.forEach { dayEntries ->
            dayEntries.sortedByDescending { it.createdAt }.drop(1).forEach { journalRepository.delete(it) }
        }

        dailySteps.forEach { (day, steps) ->
            if (steps <= 0) return@forEach
            val content = str(R.string.entry_steps_content, steps)
            val existing = stepEntriesByDay[day]?.maxByOrNull { it.createdAt }
            // Bugünün kaydı "şu an"a, geçmiş günlerinki o günün sonuna yazılır (kayıt doğru güne düşsün)
            val timestamp = if (day == today) System.currentTimeMillis()
                else day.atTime(23, 59).atZone(zoneId).toInstant().toEpochMilli()
            if (existing == null) {
                journalRepository.insert(
                    JournalEntryEntity(
                        title = str(R.string.entry_steps_title),
                        content = content,
                        entryType = EntryType.STEPS,
                        createdAt = timestamp
                    )
                )
            } else if (existing.content != content) {
                // Uygulama her açıldığında eşitlendiği için adım değişmediyse veritabanına boşuna yazılmaz
                val createdAt = if (day == today) timestamp else existing.createdAt
                journalRepository.update(existing.copy(content = content, createdAt = createdAt))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerManager.stop()
        if (isRecording.value) audioRecorderManager.stopRecording()
    }

    private suspend fun copyImageToInternalStorage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "img_${System.currentTimeMillis()}.jpg")
        inputStream?.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
        file.absolutePath
    }
}
