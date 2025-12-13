package com.ubertracker.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale

// Define your Neon Colors here so they are available everywhere
val NeonPink = Color(0xFFFF00FF)
val NeonGreen = Color(0xFF00FF99)
val NeonBg = Color(0xFF121212) // Very dark grey/black
val GlassBlack = Color(0xFF000000).copy(alpha = 0.7f) // 70% opaque black
val ElectricBlue = Color(0xFF7DF9FF)

// --- NO CLASS WRAPPER HERE ---

fun Modifier.neonGlow(
    color: Color,
    borderRadius: Dp = 8.dp,
    blurRadius: Dp = 10.dp
) = this.then(
    Modifier.drawBehind {
        this.drawIntoCanvas {
            val paint = Paint()
            val frameworkPaint = paint.asFrameworkPaint()
            frameworkPaint.color = color.toArgb()

            // Create the blur effect (The Glow)
            frameworkPaint.setShadowLayer(
                blurRadius.toPx(),
                0f,
                0f,
                color.toArgb()
            )

            // Draw the shape
            it.drawRoundRect(
                0f,
                0f,
                this.size.width,
                this.size.height,
                borderRadius.toPx(),
                borderRadius.toPx(),
                paint
            )
        }
    }
)

fun String.toUiDate(): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val date = inputFormat.parse(this)
        outputFormat.format(date ?: return this)
    } catch (e: Exception) {
        this // Return original if parsing fails
    }
}