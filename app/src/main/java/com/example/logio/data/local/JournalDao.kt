package com.example.logio.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow


@Dao
interface JournalDao {

    // Yeni kayıt ekleme (aynı ID varsa üzerine yazar)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntryEntity): Long

    // Kayıt güncelleme
    @Update
    suspend fun updateEntry(entry: JournalEntryEntity)

    // Kayıt silme
    @Delete
    suspend fun deleteEntry(entry: JournalEntryEntity)

    // Zaman tüneli için tüm kayıtları tarihe göre tersten (en yeni en üstte) getiren canlı akış
    @Query("SELECT * FROM journal_entries ORDER BY createdAt DESC")
    fun getAllEntries(): Flow<List<JournalEntryEntity>>

    // Tek bir kaydı ID ile bulma
    @Query("SELECT * FROM journal_entries WHERE id = :entryId")
    suspend fun getEntryById(entryId: Long): JournalEntryEntity?

    // Belirli bir türdeki kayıtları filtreleme (Örn: Sadece yemekler veya harcamalar)
    @Query("SELECT * FROM journal_entries WHERE entryType = :type ORDER BY createdAt DESC")
    fun getEntriesByType(type: EntryType): Flow<List<JournalEntryEntity>>
}