package com.example.logio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = MimoPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7FF),
    onPrimaryContainer = MimoNavy,
    secondary = Color(0xFF5BB8A4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF6F0),
    onSecondaryContainer = MimoNavy,
    tertiary = MimoPink,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4EE),
    onTertiaryContainer = MimoNavy,
    background = MimoCream,
    onBackground = MimoNavy,
    surface = MimoCard,
    onSurface = MimoNavy,
    surfaceVariant = MimoChip,
    onSurfaceVariant = MimoNavyMuted,
    surfaceContainer = Color(0xFFF8F4FF),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFFFFBFE),
    outline = Color(0xFFD9D4EA),
    outlineVariant = Color(0xFFECE7F7),
    error = Color(0xFFE45B6B)
)

private val DarkColorScheme = darkColorScheme(
    primary = MimoLavender,
    onPrimary = MimoNavy,
    primaryContainer = Color(0xFF3A3470),
    onPrimaryContainer = Color(0xFFEDE7FF),
    secondary = MimoMint,
    onSecondary = MimoNavy,
    tertiary = MimoPink,
    background = Color(0xFF16142B),
    onBackground = Color(0xFFF4F0FF),
    surface = Color(0xFF221F3A),
    onSurface = Color(0xFFF4F0FF),
    surfaceVariant = Color(0xFF2E2A4A),
    onSurfaceVariant = Color(0xFFC8C3DC),
    outline = Color(0xFF5A5578)
)

val LogioShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun LogioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = if (darkTheme) Color(0xFF16142B).toArgb() else MimoCream.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = LogioShapes,
        content = content
    )
}
