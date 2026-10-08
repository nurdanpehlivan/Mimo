/*
 * VoiceTranscriber: Sesli notları Gemini ile yazıya çeviren sınıf.
 *
 * Tek örnek (Singleton) olduğu için "şu an hangi kayıt çevriliyor" bilgisi
 * Günlük ve Takvim ekranlarında aynı görünür. Çeviri sonucu kaydın metnine eklenir.
 * Kullanıcıya gösterilecek hata metnini ViewModel seçer; bu sınıf yalnızca hatanın türünü döndürür.
 */
package com.example.logio.data.audio

import com.example.logio.R
import com.example.logio.data.journal.JournalRepository
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.data.remote.GeminiQuotaException
import com.example.logio.data.remote.GeminiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// Eski ses kayıtlarında içerik olarak kaydedilen yer tutucu metin
const val VOICE_PLACEHOLDER = "Sesli Not"

@Singleton
class VoiceTranscriber @Inject constructor(
    private val geminiService: GeminiService,
    private val journalRepository: JournalRepository
) {
    private val _transcribingEntryIds = MutableStateFlow<Set<Long>>(emptySet())
    /** Şu an yazıya çevrilmekte olan kayıtların id'leri (kartta yükleniyor göstermek için). */
    val transcribingEntryIds: StateFlow<Set<Long>> = _transcribingEntryIds.asStateFlow()

    /**
     * Kaydı yazıya çevirip metnine ekler.
     * Başarılıysa (ya da zaten çevriliyorsa) null, değilse kullanıcıya gösterilecek hata metninin
     * kaynağı (R.string...) döner.
     */
    suspend fun transcribe(entry: JournalEntryEntity): Int? {
        val path = entry.audioPath ?: return null
        if (entry.id in _transcribingEntryIds.value) return null

        _transcribingEntryIds.update { it + entry.id }
        return try {
            val transcription = geminiService.transcribeAudio(path)?.trim()
            if (transcription.isNullOrBlank()) return R.string.err_transcribe_failed

            // Çeviri sürerken kayıt değişmiş olabilir; güncel halini al
            val current = journalRepository.getById(entry.id) ?: entry
            val existing = current.content.trim()
            val newContent = if (existing.isBlank() || existing == VOICE_PLACEHOLDER) {
                transcription
            } else {
                "$existing\n\n$transcription"
            }
            journalRepository.update(current.copy(content = newContent))
            null
        } catch (e: GeminiQuotaException) {
            R.string.err_transcribe_quota
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Yazıya çevirme hatası")
            R.string.err_transcribe_error
        } finally {
            _transcribingEntryIds.update { it - entry.id }
        }
    }
}
