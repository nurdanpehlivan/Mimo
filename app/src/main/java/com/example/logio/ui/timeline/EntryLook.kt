package com.example.logio.ui.timeline

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import com.example.logio.R
import com.example.logio.data.local.EntryType

data class EntryLook(
    val icon: ImageVector,
    val tint: Color,
    val wash: Color
)

fun entryLook(type: EntryType): EntryLook = when (type) {
    EntryType.TEXT -> EntryLook(Icons.Rounded.EditNote, Color(0xFF3B82F6), Color(0xFFE8F1FF))
    EntryType.MOOD -> EntryLook(Icons.Rounded.SentimentSatisfied, Color(0xFFD97706), Color(0xFFFEF3C7))
    EntryType.MEAL -> EntryLook(Icons.Rounded.Restaurant, Color(0xFFDC2626), Color(0xFFFEE2E2))
    EntryType.EXPENSE -> EntryLook(Icons.Rounded.ShoppingBag, Color(0xFFEA580C), Color(0xFFFFEDD5))
    EntryType.WATER -> EntryLook(Icons.Rounded.WaterDrop, Color(0xFF0284C7), Color(0xFFE0F2FE))
    EntryType.LOCATION -> EntryLook(Icons.Rounded.LocationOn, Color(0xFFE11D48), Color(0xFFFFE4E6))
    EntryType.VOICE -> EntryLook(Icons.Rounded.Mic, Color(0xFFF97316), Color(0xFFFFEDD5))
    EntryType.PHOTO -> EntryLook(Icons.Rounded.Image, Color(0xFF7C3AED), Color(0xFFEDE9FE))
    EntryType.TODO -> EntryLook(Icons.Rounded.Checklist, Color(0xFF2E7D32), Color(0xFFE8F5E9))
    EntryType.DRAW -> EntryLook(Icons.Rounded.Brush, Color(0xFFF57C00), Color(0xFFFFF8E1))
    EntryType.FILM -> EntryLook(Icons.Rounded.Movie, Color(0xFF006064), Color(0xFFE0F7FA))
    EntryType.BOOK -> EntryLook(Icons.Rounded.Book, Color(0xFFB45309), Color(0xFFFFF3E0))
    EntryType.MUSIC -> EntryLook(Icons.Rounded.MusicNote, Color(0xFF6A1B9A), Color(0xFFF3E5F5))
    EntryType.SLEEP -> EntryLook(Icons.Rounded.Bedtime, Color(0xFF4F46E5), Color(0xFFE0E7FF))
    EntryType.COFFEE -> EntryLook(Icons.Rounded.Coffee, Color(0xFF8D5B4C), Color(0xFFF5EBE6))
    EntryType.ALCOHOL -> EntryLook(Icons.Rounded.LocalBar, Color(0xFFF97316), Color(0xFFFFEDD5))
    EntryType.VIDEO -> EntryLook(Icons.Rounded.Videocam, Color(0xFFDB2777), Color(0xFFFCE7F3))
    EntryType.STEPS -> EntryLook(Icons.AutoMirrored.Rounded.DirectionsWalk, Color(0xFF16A34A), Color(0xFFDCFCE7))
    EntryType.LINK -> EntryLook(Icons.Rounded.Link, Color(0xFF0369A1), Color(0xFFE0F2FE))
    EntryType.JOURNEY_VIDEO -> EntryLook(Icons.Rounded.VideoLibrary, Color(0xFF009688), Color(0xFFD1FAE5))
}

/** Kayıt türünün kullanıcıya görünen adı. */
@StringRes
fun entryTypeLabel(type: EntryType): Int = when (type) {
    EntryType.TEXT -> R.string.type_text
    EntryType.MOOD -> R.string.action_mood
    EntryType.MEAL -> R.string.type_meal
    EntryType.EXPENSE -> R.string.action_expense
    EntryType.WATER -> R.string.action_water
    EntryType.LOCATION -> R.string.action_location
    EntryType.VOICE -> R.string.type_voice
    EntryType.PHOTO -> R.string.action_photo
    EntryType.TODO -> R.string.action_todo
    EntryType.DRAW -> R.string.action_draw
    EntryType.FILM -> R.string.type_film
    EntryType.BOOK -> R.string.action_book
    EntryType.MUSIC -> R.string.action_music
    EntryType.SLEEP -> R.string.action_sleep
    EntryType.COFFEE -> R.string.action_coffee
    EntryType.ALCOHOL -> R.string.action_alcohol
    EntryType.VIDEO -> R.string.action_video
    EntryType.STEPS -> R.string.action_steps
    EntryType.LINK -> R.string.action_link
    EntryType.JOURNEY_VIDEO -> R.string.type_journey_video
}
