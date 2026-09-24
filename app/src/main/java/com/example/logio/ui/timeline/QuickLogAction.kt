package com.example.logio.ui.timeline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.logio.data.features.FeatureRegistry

/**
 * QuickLogAction: Hızlı menü ve Feature Registry'nin UI temsili
 * Eski sistem: QuickLogAction.values() ile 12 aksiyon tanımı
 * Yeni sistem: Feature Registry'den doğrudan alınacak (gelecek)
 */
enum class QuickLogAction(
    val title: String,
    val icon: ImageVector,
    val bgColor: Color,
    val iconColor: Color,
    val featureId: String? = null
) {
    LOG("Log", Icons.Rounded.EditNote, Color(0xFFE8F1FF), Color(0xFF3B82F6), "note"),
    MOOD("Ruh Hali", Icons.Rounded.SentimentSatisfied, Color(0xFFFEF3C7), Color(0xFFD97706), "mood"),
    PHOTO("Fotoğraflar", Icons.Rounded.Image, Color(0xFFEDE9FE), Color(0xFF7C3AED), "photo"),
    STEPS("Adımlar", Icons.Rounded.DirectionsWalk, Color(0xFFDCFCE7), Color(0xFF16A34A), "steps"),
    SLEEP("Uyku", Icons.Rounded.Bedtime, Color(0xFFE0E7FF), Color(0xFF4F46E5), "sleep"),
    EXPENSE("Alışveriş", Icons.Rounded.ShoppingBag, Color(0xFFFFEDD5), Color(0xFFEA580C), "expense"),
    MEAL("Kalori", Icons.Rounded.Restaurant, Color(0xFFFEE2E2), Color(0xFFDC2626), "meal"),
    WATER("Su", Icons.Rounded.WaterDrop, Color(0xFFE0F2FE), Color(0xFF0284C7), "water"),
    COFFEE("Kahve", Icons.Rounded.Coffee, Color(0xFFF5EBE6), Color(0xFF8D5B4C), "coffee"),
    LOCATION("Konum", Icons.Rounded.LocationOn, Color(0xFFFFE4E6), Color(0xFFE11D48), "location"),
    VOICE("Ses", Icons.Rounded.Mic, Color(0xFFFFEDD5), Color(0xFFF97316), "voice"),
    NOTES("Video", Icons.Rounded.Videocam, Color(0xFFFCE7F3), Color(0xFFDB2777), "video"),

    FILM("Film", Icons.Rounded.Movie, Color(0xFFE0F7FA), Color(0xFF006064), "film"),
    BOOK("Kitap", Icons.Rounded.Book, Color(0xFFFFF3E0), Color(0xFFB45309), "book"),
    MUSIC("Müzik", Icons.Rounded.MusicNote, Color(0xFFF3E5F5), Color(0xFF6A1B9A), "music"),
    ALCOHOL("Alkol", Icons.Rounded.LocalBar, Color(0xFFFFEDD5), Color(0xFFF97316), "alcohol"),
    TODO("Yapılacaklar", Icons.Rounded.Checklist, Color(0xFFE8F5E9), Color(0xFF2E7D32), "todo"),
    DRAW("Çizim", Icons.Rounded.Brush, Color(0xFFFFF8E1), Color(0xFFF57C00), "draw"),

}
