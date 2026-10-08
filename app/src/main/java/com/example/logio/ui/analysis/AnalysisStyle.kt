package com.example.logio.ui.analysis

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.logio.ui.theme.MimoNavy
import com.example.logio.ui.theme.MimoNavyMuted
import java.util.Locale

// İstatistik ekranındaki kartların ve grafiklerin ortak renkleri ve yardımcıları

internal val TrLocale: Locale get() = Locale.getDefault()

internal val CardBorder = Color(0xFFEDE8F7)
internal val InkPrimary = MimoNavy
internal val InkSecondary = MimoNavyMuted
internal val InkMuted = Color(0xFF8A90B4)
internal val GridColor = Color(0xFFEDEAF5)
internal val EmptyStub = Color(0xFFE6E3F0)

// Grafik serileri (doğrulandı: açık zeminde CVD ve kontrast kontrollerinden geçiyor)
internal val MoodSeriesColor = Color(0xFF7C6CFF)
internal val StepsSeriesColor = Color(0xFF22A35A)

internal fun formatNumber(value: Int): String = String.format(TrLocale, "%,d", value)

/** Yalnızca üst köşeleri yuvarlatılmış, tabana oturan çubuk. */
internal fun DrawScope.drawTopRoundedBar(
    color: Color,
    x: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float
) {
    val r = radius.coerceAtMost(width / 2).coerceAtMost(height)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(x, top, x + width, top + height),
                topLeft = CornerRadius(r),
                topRight = CornerRadius(r),
                bottomRight = CornerRadius.Zero,
                bottomLeft = CornerRadius.Zero
            )
        )
    }
    drawPath(path, color)
}
