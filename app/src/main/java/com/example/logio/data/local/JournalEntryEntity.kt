package com.example.logio.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val entryType: EntryType = EntryType.TEXT,

    val moodRating: Int? = null,
    val mediaUri: String? = null,
    val expenseAmount: Double? = null,
    val expenseCategory: String? = null,
    val waterAmountMl: Int? = null,
    val isEncrypted: Boolean = false,

    val imagePaths: String? = null, // Virgülle ayrılmış çoklu resim yolları
    val audioPath: String? = null,
    val transcriptionText: String? = null, // Görev 1: STT için eklenen alan

    val calories: Int? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    
    val weightKg: Double? = null,
    val sleepHours: Double? = null,
    val alcoholUnits: String? = null,
    val coffeeCups: Int? = null,

    val latitude: Double? = null,
    val longitude: Double? = null
)
