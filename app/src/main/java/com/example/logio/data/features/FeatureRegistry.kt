package com.example.logio.data.features

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Tüm Features'ın tanımlandığı yer
 * Yeni feature eklemek için bu enum'a ekle
 */
enum class FeatureRegistry(
    override val id: String,
    override val title: String,
    override val icon: ImageVector,
    override val backgroundColor: Color,
    override val iconColor: Color,
    override val description: String,
    override val category: FeatureCategory
) : Feature {

    // Activity
    SLEEP("sleep", "Uyku", Icons.Rounded.Bedtime, Color(0xFFE0E7FF), Color(0xFF4F46E5),
          "Uyku saatini ve kalitesini kaydet", FeatureCategory.ACTIVITY),
    STEPS("steps", "Adımlar", Icons.Rounded.DirectionsWalk, Color(0xFFDCFCE7), Color(0xFF16A34A),
          "Günlük adımları takip et", FeatureCategory.ACTIVITY),

    // Health
    MEAL("meal", "Kalori", Icons.Rounded.Restaurant, Color(0xFFFEE2E2), Color(0xFFDC2626),
         "Yemek fotoğrafı analiz et", FeatureCategory.HEALTH),
    WATER("water", "Su", Icons.Rounded.WaterDrop, Color(0xFFE0F2FE), Color(0xFF0284C7),
          "Günlük su tüketimi", FeatureCategory.HEALTH),
    COFFEE("coffee", "Kahve", Icons.Rounded.Coffee, Color(0xFFF5EBE6), Color(0xFF8D5B4C),
           "Kahve içme zamanını kaydet", FeatureCategory.HEALTH),

    // Mood & Emotions
    MOOD("mood", "Ruh Hali", Icons.Rounded.SentimentSatisfied, Color(0xFFFEF3C7), Color(0xFFD97706),
         "Duygusal durumunu kaydet", FeatureCategory.MOOD),
    ALCOHOL("alcohol", "Alkol", Icons.Rounded.LocalBar, Color(0xFFFFEDD5), Color(0xFFF97316),
            "Alkol tüketimini kaydet", FeatureCategory.MOOD),

    // Location
    LOCATION("location", "Konum", Icons.Rounded.LocationOn, Color(0xFFFFE4E6), Color(0xFFE11D48),
             "Bulunduğun yeri kaydet", FeatureCategory.LOCATION),

    // Media
    PHOTO("photo", "Fotoğraf", Icons.Rounded.Image, Color(0xFFEDE9FE), Color(0xFF7C3AED),
          "Anılarını fotoğrafla sakla", FeatureCategory.MEDIA),
    VOICE("voice", "Ses", Icons.Rounded.Mic, Color(0xFFFFEDD5), Color(0xFFF97316),
          "Sesli not kaydı yap", FeatureCategory.MEDIA),
    VIDEO("video", "Video", Icons.Rounded.Videocam, Color(0xFFFCE7F3), Color(0xFFDB2777),
          "Video anılarını sakla", FeatureCategory.MEDIA),

    // Finance
    EXPENSE("expense", "Alışveriş", Icons.Rounded.ShoppingBag, Color(0xFFFFEDD5), Color(0xFFEA580C),
            "Harcamalarını kaydet", FeatureCategory.FINANCE),

    // Notes & Knowledge
    NOTE("note", "Not", Icons.Rounded.EditNote, Color(0xFFE8F1FF), Color(0xFF3B82F6),
         "Hızlı not al", FeatureCategory.NOTE),
    BOOK("book", "Kitap", Icons.Rounded.BookmarkAdd, Color(0xFFE8F1FF), Color(0xFF3B82F6),
         "Okunan kitapları kaydet", FeatureCategory.NOTE),
    FILM("film", "Film", Icons.Rounded.Movie, Color(0xFFE8F1FF), Color(0xFF3B82F6),
         "İzlenen filmleri kaydet", FeatureCategory.NOTE),
}

