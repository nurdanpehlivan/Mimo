/*
 * JournalRepository: Günlük kayıtlarına erişimin tek kapısı.
 *
 * ViewModel'ler veritabanına (JournalDao) doğrudan değil, bu sınıf üzerinden ulaşır:
 *   Ekran → ViewModel → JournalRepository → JournalDao → Room
 * Kayıtlarla ilgili ortak kurallar (ör. yapılacaklar maddesini işaretlemek) burada bir kez yazılır,
 * böylece Günlük, Takvim ve Profil ekranları aynı davranışı paylaşır.
 */
package com.example.logio.data.journal

import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalDao
import com.example.logio.data.local.JournalEntryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(
    private val journalDao: JournalDao
) {
    /** Tüm kayıtlar, en yeni en üstte. Veritabanı değiştikçe yeni listeyi kendiliğinden gönderir. */
    val entries: Flow<List<JournalEntryEntity>> = journalDao.getAllEntries()

    fun entriesOfType(type: EntryType): Flow<List<JournalEntryEntity>> = journalDao.getEntriesByType(type)

    suspend fun getById(id: Long): JournalEntryEntity? = journalDao.getEntryById(id)

    /** Kaydı ekler ve veritabanının verdiği id'yi döndürür. */
    suspend fun insert(entry: JournalEntryEntity): Long = journalDao.insertEntry(entry)

    suspend fun update(entry: JournalEntryEntity) = journalDao.updateEntry(entry)

    suspend fun delete(entry: JournalEntryEntity) = journalDao.deleteEntry(entry)

    /** Yapılacaklar listesindeki [index]. satırın işaretini değiştirir ("[ ]" ↔ "[x]"). */
    suspend fun toggleTodoItem(entry: JournalEntryEntity, index: Int) {
        val updated = toggleTodoLine(entry.content, index) ?: return
        journalDao.updateEntry(entry.copy(content = updated))
    }
}

/**
 * Yapılacaklar metninde [index]. satırın işaretini değiştirir.
 * Satır yoksa null döner. Veritabanına dokunmadığı için birim testle denenebilir.
 */
internal fun toggleTodoLine(content: String, index: Int): String? {
    val lines = content.split("\n").toMutableList()
    if (index !in lines.indices) return null
    val line = lines[index]
    lines[index] = when {
        line.startsWith("[ ]") -> line.replaceFirst("[ ]", "[x]")
        line.startsWith("[x]") -> line.replaceFirst("[x]", "[ ]")
        else -> line
    }
    return lines.joinToString("\n")
}
