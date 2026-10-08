package com.example.logio.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun MimoBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF8F1FF),
                        Color(0xFFFFF6F0),
                        Color(0xFFEAF8F4)
                    )
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = w * 0.42f,
                center = Offset(w * 0.12f, h * 0.08f)
            )
            drawCircle(
                color = Color(0xFFE7D9FF).copy(alpha = 0.45f),
                radius = w * 0.38f,
                center = Offset(w * 0.95f, h * 0.02f)
            )
            drawCircle(
                color = Color(0xFFD8F4EE).copy(alpha = 0.55f),
                radius = w * 0.36f,
                center = Offset(w * 0.9f, h * 0.92f)
            )
            drawCircle(
                color = Color(0xFFFFD9E8).copy(alpha = 0.28f),
                radius = w * 0.28f,
                center = Offset(w * 0.08f, h * 0.72f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = w * 0.22f,
                center = Offset(w * 0.55f, h * 0.38f)
            )
        }
        content()
    }
}
