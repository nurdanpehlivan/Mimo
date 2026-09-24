package com.example.logio.data.features

import com.example.logio.data.local.EntryType

/**
 * Feature'lar için veritabanı ve işlem katmanı
 * Tüm özellikler için ortak bir yapı sağlar
 */
data class FeatureEntry(
    val id: String,
    val featureId: String,
    val title: String,
    val value: String,
    val numericValue: Double? = null,
    val category: FeatureCategory = FeatureCategory.NOTE,
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val entryType: EntryType = EntryType.TEXT
)

/**
 * Feature yazma işlemleri için repository interface
 */
interface FeatureRepository {
    suspend fun saveFeature(entry: FeatureEntry)
    suspend fun getFeatureEntries(featureId: String): List<FeatureEntry>
    suspend fun deleteFeature(id: String)
}

