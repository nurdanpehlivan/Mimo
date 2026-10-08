package com.example.logio.ui.timeline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.example.logio.R

enum class QuickLogAction(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val bgColor: Color,
    val iconColor: Color
) {
    LOG(R.string.action_note, Icons.Rounded.EditNote, Color(0xFFE8F1FF), Color(0xFF3B82F6)),
    MOOD(R.string.action_mood, Icons.Rounded.SentimentSatisfied, Color(0xFFFEF3C7), Color(0xFFD97706)),
    PHOTO(R.string.action_photo, Icons.Rounded.Image, Color(0xFFEDE9FE), Color(0xFF7C3AED)),
    STEPS(R.string.action_steps, Icons.AutoMirrored.Rounded.DirectionsWalk, Color(0xFFDCFCE7), Color(0xFF16A34A)),
    SLEEP(R.string.action_sleep, Icons.Rounded.Bedtime, Color(0xFFE0E7FF), Color(0xFF4F46E5)),
    EXPENSE(R.string.action_expense, Icons.Rounded.ShoppingBag, Color(0xFFFFEDD5), Color(0xFFEA580C)),
    MEAL(R.string.action_meal, Icons.Rounded.Restaurant, Color(0xFFFEE2E2), Color(0xFFDC2626)),
    WATER(R.string.action_water, Icons.Rounded.WaterDrop, Color(0xFFE0F2FE), Color(0xFF0284C7)),
    COFFEE(R.string.action_coffee, Icons.Rounded.Coffee, Color(0xFFF5EBE6), Color(0xFF8D5B4C)),
    LOCATION(R.string.action_location, Icons.Rounded.LocationOn, Color(0xFFFFE4E6), Color(0xFFE11D48)),
    VOICE(R.string.action_voice, Icons.Rounded.Mic, Color(0xFFFFEDD5), Color(0xFFF97316)),
    FILM(R.string.action_film, Icons.Rounded.Movie, Color(0xFFE0F7FA), Color(0xFF006064)),
    BOOK(R.string.action_book, Icons.Rounded.Book, Color(0xFFFFF3E0), Color(0xFFB45309)),
    MUSIC(R.string.action_music, Icons.Rounded.MusicNote, Color(0xFFF3E5F5), Color(0xFF6A1B9A)),
    TODO(R.string.action_todo, Icons.Rounded.Checklist, Color(0xFFE8F5E9), Color(0xFF2E7D32)),
    DRAW(R.string.action_draw, Icons.Rounded.Brush, Color(0xFFFFF8E1), Color(0xFFF57C00)),
    ALCOHOL(R.string.action_alcohol, Icons.Rounded.LocalBar, Color(0xFFFFEDD5), Color(0xFFF97316)),
    VIDEO(R.string.action_video, Icons.Rounded.Videocam, Color(0xFFFCE7F3), Color(0xFFDB2777)),
    LINK(R.string.action_link, Icons.Rounded.Link, Color(0xFFE0F2FE), Color(0xFF0369A1)),

    JOURNEY(R.string.action_journey, Icons.Rounded.PlayCircle, Color(0xFFE0F2FE), Color(0xFF4F46E5))
}
