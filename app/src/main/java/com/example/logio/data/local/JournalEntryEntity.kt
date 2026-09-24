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

    // Opsiyonel / Modüler Alanlar
    val moodRating: Int? = null,           // 1-5 arası ruh hali puanı
    val mediaUri: String? = null,          // Fotoğraf veya ses dosyasının cihazdaki yerel yolu
    val calorieEstimate: Int? = null,      // Gemini analiziyle gelen kalori miktarı
    val expenseAmount: Double? = null,      // Harcama tutarı
    val expenseCategory: String? = null,   // Harcama kategorisi (Yemek, Ulaşım, Sağlık vb.)
    val waterAmountMl: Int? = null,        // Su içme miktarı (ml)
    val isEncrypted: Boolean = false,       // Biyometrik ile ekstra gizlenecek mi?

    val imagePath: String? = null,
    val audioPath: String? = null,

    val calories: Int? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null
)