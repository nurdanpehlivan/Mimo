package com.example.logio.ui.theme

import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Uygulamadaki tüm yazı alanları için ortak renkler.
 * Varsayılan Material renkleri yazıyı/etiketi/imleci mor (primary) yapıyordu; burada okunaklı lacivert kullanılır.
 * Kartlar ve pencereler hep açık renkli olduğu için koyu temada da aynı renkler kullanılır.
 */
@Composable
fun logioFieldColors(
    containerColor: Color = Color.White,
    unfocusedBorderColor: Color = Color(0xFFE3DEF0)
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MimoNavy,
    unfocusedTextColor = MimoNavy,
    disabledTextColor = MimoNavyMuted,
    cursorColor = MimoNavy,
    selectionColors = TextSelectionColors(handleColor = MimoNavy, backgroundColor = MimoNavy.copy(alpha = 0.18f)),
    focusedBorderColor = MimoNavy.copy(alpha = 0.45f),
    unfocusedBorderColor = unfocusedBorderColor,
    focusedLabelColor = MimoNavy,
    unfocusedLabelColor = MimoNavyMuted,
    focusedPlaceholderColor = MimoNavyMuted.copy(alpha = 0.7f),
    unfocusedPlaceholderColor = MimoNavyMuted.copy(alpha = 0.7f),
    focusedLeadingIconColor = MimoNavyMuted,
    unfocusedLeadingIconColor = MimoNavyMuted,
    focusedTrailingIconColor = MimoNavyMuted,
    unfocusedTrailingIconColor = MimoNavyMuted,
    focusedContainerColor = containerColor,
    unfocusedContainerColor = containerColor
)
