package com.example.logio.data.features

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Tüm Features (Özellikler) için ortak arayüz
 * Bu sayede her feature aynı şekilde ele alınabilir
 */
interface Feature {
    val id: String
    val title: String
    val icon: ImageVector
    val backgroundColor: Color
    val iconColor: Color
    val description: String
    val category: FeatureCategory
}

enum class FeatureCategory {
    ACTIVITY,      // Uyku, Adımlar, vb.
    HEALTH,        // Kalori, Su, Kahve, vb.
    MOOD,          // Ruh hali
    LOCATION,      // Konum
    MEDIA,         // Fotoğraf, Video, Ses
    FINANCE,       // Harcama
    NOTE           // Not, Kitap, vb.
}

/**
 * Feature'ın input dialog'u için veri yapısı
 */
data class FeatureInputData(
    val featureId: String,
    val title: String = "",
    val value: String = "",
    val numericValue: Double? = null,
    val imageUri: String? = null,
    val audioUri: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

