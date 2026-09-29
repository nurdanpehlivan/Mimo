package com.example.logio.ui.timeline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class QuickLogAction(
    val title: String,
    val icon: ImageVector,
    val bgColor: Color,
    val iconColor: Color
) {
    LOG("Not", Icons.Rounded.EditNote, Color(0xFFE8F1FF), Color(0xFF3B82F6)),
    MOOD("Ruh Hali", Icons.Rounded.SentimentSatisfied, Color(0xFFFEF3C7), Color(0xFFD97706)),
    PHOTO("Fotoğraf", Icons.Rounded.Image, Color(0xFFEDE9FE), Color(0xFF7C3AED)),
    STEPS("Adımlar", Icons.AutoMirrored.Rounded.DirectionsWalk, Color(0xFFDCFCE7), Color(0xFF16A34A)),
    SLEEP("Uyku", Icons.Rounded.Bedtime, Color(0xFFE0E7FF), Color(0xFF4F46E5)),
    EXPENSE("Harcama", Icons.Rounded.ShoppingBag, Color(0xFFFFEDD5), Color(0xFFEA580C)),
    MEAL("Yemek/Kalori", Icons.Rounded.Restaurant, Color(0xFFFEE2E2), Color(0xFFDC2626)),
    WATER("Su", Icons.Rounded.WaterDrop, Color(0xFFE0F2FE), Color(0xFF0284C7)),
    COFFEE("Kahve", Icons.Rounded.Coffee, Color(0xFFF5EBE6), Color(0xFF8D5B4C)),
    LOCATION("Konum", Icons.Rounded.LocationOn, Color(0xFFFFE4E6), Color(0xFFE11D48)),
    VOICE("Ses", Icons.Rounded.Mic, Color(0xFFFFEDD5), Color(0xFFF97316)),
    FILM("Film", Icons.Rounded.Movie, Color(0xFFE0F7FA), Color(0xFF006064)),
    BOOK("Kitap", Icons.Rounded.Book, Color(0xFFFFF3E0), Color(0xFFB45309)),
    MUSIC("Müzik", Icons.Rounded.MusicNote, Color(0xFFF3E5F5), Color(0xFF6A1B9A)),
    TODO("Yapılacaklar", Icons.Rounded.Checklist, Color(0xFFE8F5E9), Color(0xFF2E7D32)),
    DRAW("Çizim", Icons.Rounded.Brush, Color(0xFFFFF8E1), Color(0xFFF57C00)),
    ALCOHOL("Alkol", Icons.Rounded.LocalBar, Color(0xFFFFEDD5), Color(0xFFF97316)),
    VIDEO("Video", Icons.Rounded.Videocam, Color(0xFFFCE7F3), Color(0xFFDB2777)),
    LINK("Bağlantı", Icons.Rounded.Link, Color(0xFFE0F2FE), Color(0xFF0369A1))
}
