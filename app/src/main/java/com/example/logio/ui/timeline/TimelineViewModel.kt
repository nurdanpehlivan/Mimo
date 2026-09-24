package com.example.logio.ui.timeline

import android.content.Context
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val journalDao: JournalDao,
    private val geminiService: GeminiService,
    private val audioRecorderManager: AudioRecorderManager,
    private val audioPlayerManager: AudioPlayerManager,
    private val stepCounterManager: StepCounterManager
) : ViewModel() {

    val entries: StateFlow<List<JournalEntryEntity>> = journalDao.getAllEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    val isRecording = audioRecorderManager.isRecording
    val playingFilePath = audioPlayerManager.playingFilePath
    val stepCounterUiState = stepCounterManager.uiState

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    fun addNote(title: String, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = title.ifBlank { "Günlük Notu" },
                    content = content,
                    entryType = EntryType.TEXT
                )
            )
        }
    }

    fun addMood(moodEmoji: String, rating: Int) {
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = "Günün Hali: $moodEmoji",
                    content = "Bugün kendimi $moodEmoji gibi hissediyorum.",
                    entryType = EntryType.MOOD,
                    moodRating = rating
                )
            )
        }
    }

    fun deleteEntry(entry: JournalEntryEntity) {
        viewModelScope.launch {
            journalDao.deleteEntry(entry)
        }
    }

    fun addPhotoEntry(context: Context, imageUri: Uri, note: String) {
        viewModelScope.launch {
            val savedImagePath = copyImageToInternalStorage(context, imageUri)

            journalDao.insertEntry(
                JournalEntryEntity(
                    title = note.ifBlank { "Fotoğraf Anısı" },
                    content = note,
                    entryType = EntryType.PHOTO,
                    imagePath = savedImagePath
                )
            )
        }
    }

    fun analyzeAndSaveMeal(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _isAnalyzing.value = true

            // 1. Resmi cihazın kalıcı dahili hafızasına kaydet
            val savedImagePath = copyImageToInternalStorage(context, imageUri)

            // 2. URI üzerinden Bitmap nesnesi oluştur
            val bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }

            // 3. Gemini API'ye gönder ve sonucu bekle
            val result = if (bitmap != null) {
                geminiService.analyzeMealImage(bitmap)
            } else {
                null
            }

            // 4. Veritabanına MEAL tipiyle besin değerlerini yaz
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = result?.mealName ?: "Öğün Kaydı",
                    content = if (result != null) "${result.calories} kcal" else "Besin analizi yapılamadı.",
                    entryType = EntryType.MEAL,
                    imagePath = savedImagePath,
                    calories = result?.calories,
                    protein = result?.protein,
                    carbs = result?.carbs,
                    fat = result?.fat
                )
            )

            _isAnalyzing.value = false
        }
    }

    fun saveVoiceEntry(audioPath: String, transcript: String = "", durationMs: Long = 0L) {
        if (audioPath.isBlank()) return
        val normalizedTranscript = transcript.trim()
        val fallbackText = "Ses kaydı"

        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = if (normalizedTranscript.isBlank()) "Sesli Günlük" else normalizedTranscript,
                    content = buildString {
                        append(if (normalizedTranscript.isBlank()) fallbackText else normalizedTranscript)
                        if (durationMs > 0L) {
                            append("\n")
                            append("Süre: ${durationMs / 1000} sn")
                        }
                    },
                    entryType = EntryType.VOICE,
                    audioPath = audioPath
                )
            )
        }
    }

    fun saveExpenseEntry(title: String, amount: Double, category: String = "Diğer") {
        if (title.isBlank() || amount <= 0) return
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = title,
                    content = "Harcama: ₺${String.format("%.2f", amount)}",
                    entryType = EntryType.EXPENSE,
                    expenseCategory = category
                )
            )
        }
    }

    fun saveWaterEntry(amountMl: Int) {
        if (amountMl <= 0) return
        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = "Su içme",
                    content = "$amountMl ml su içtim",
                    entryType = EntryType.WATER,
                    waterAmountMl = amountMl
                )
            )
        }
    }

    fun saveLocationEntry(placeName: String, note: String) {
        val normalizedPlace = placeName.trim()
        val normalizedNote = note.trim()
        if (normalizedPlace.isBlank() && normalizedNote.isBlank()) return

        viewModelScope.launch {
            journalDao.insertEntry(
                JournalEntryEntity(
                    title = normalizedPlace.ifBlank { "Konum" },
                    content = normalizedNote.ifBlank { "Konum kaydı" },
                    entryType = EntryType.LOCATION
                )
            )
        }
    }

    fun startVoiceRecording(onError: (String) -> Unit) {
        audioRecorderManager.startRecording(
            onSuccess = {},
            onError = onError
        )
    }

    fun stopVoiceRecording(): AudioRecordResult? {
        return audioRecorderManager.stopRecording()
    }

    fun onVoicePlaybackClick(entry: JournalEntryEntity) {
        val path = entry.audioPath
        if (path.isNullOrBlank()) {
            _playbackError.value = "Bu kayıtta ses dosyası bulunamadı."
            return
        }

        if (playingFilePath.value == path) {
            audioPlayerManager.stop()
            return
        }

        audioPlayerManager.play(
            filePath = path,
            onError = { message -> _playbackError.value = message }
        )
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    fun startStepTracking() {
        stepCounterManager.startTracking()
    }

    fun stopStepTracking() {
        stepCounterManager.stopTracking()
    }

    fun clearStepError() {
        stepCounterManager.clearError()
    }

    fun stopVoicePlayback() {
        audioPlayerManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerManager.stop()
        stepCounterManager.stopTracking()
        if (isRecording.value) {
            audioRecorderManager.stopRecording()
        }
    }

    private suspend fun copyImageToInternalStorage(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
        val fileName = "img_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        val outputStream = FileOutputStream(file)

        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    }
}