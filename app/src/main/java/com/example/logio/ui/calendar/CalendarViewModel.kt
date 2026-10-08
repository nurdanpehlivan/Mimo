/*
 * CalendarViewModel: Takvim sekmesinin ViewModel'i.
 *
 * Kayıtları günlere göre gruplar (takvimdeki noktalar ve seçili günün listesi için) ve
 * takvimdeki kartlardan gelen işleri (sil, yapılacak işaretle, sesi çal/yazıya çevir) yapar.
 * Ses oynatma ve yazıya çevirme durumu Günlük ekranıyla ortaktır (AudioPlayerManager, VoiceTranscriber).
 */
package com.example.logio.ui.calendar

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.logio.data.audio.AudioPlayerManager
import com.example.logio.data.audio.VoiceTranscriber
import com.example.logio.data.journal.JournalRepository
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.util.AppLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journalRepository: JournalRepository,
    private val audioPlayerManager: AudioPlayerManager,
    private val voiceTranscriber: VoiceTranscriber
) : ViewModel() {

    /** Gün → o günün kayıtları (sabahtan akşama sıralı). Hesap arka planda yapılır. */
    val entriesByDay: StateFlow<Map<LocalDate, List<JournalEntryEntity>>> = journalRepository.entries
        .map { entries ->
            val zone = ZoneId.systemDefault()
            entries
                .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
                .mapValues { (_, dayEntries) -> dayEntries.sortedBy { it.createdAt } }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val playingFilePath: StateFlow<String?> = audioPlayerManager.playingFilePath
    val transcribingEntryIds: StateFlow<Set<Long>> = voiceTranscriber.transcribingEntryIds

    // Ekranın alttan kısa uyarı (Snackbar) olarak göstereceği hata mesajı
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun deleteEntry(entry: JournalEntryEntity) {
        viewModelScope.launch { journalRepository.delete(entry) }
    }

    fun toggleTodoItem(entry: JournalEntryEntity, index: Int) {
        viewModelScope.launch { journalRepository.toggleTodoItem(entry, index) }
    }

    fun onVoicePlaybackClick(entry: JournalEntryEntity) {
        val path = entry.audioPath ?: return
        audioPlayerManager.toggle(path) { _message.value = it }
    }

    fun transcribeVoiceEntry(entry: JournalEntryEntity) {
        viewModelScope.launch {
            voiceTranscriber.transcribe(entry)?.let { _message.value = AppLocale.wrap(context).getString(it) }
        }
    }
}
