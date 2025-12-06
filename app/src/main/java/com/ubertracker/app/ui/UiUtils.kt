package com.ubertracker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Define your Neon Colors here so they are available everywhere
val NeonPink = Color(0xFFFF00FF)
val NeonGreen = Color(0xFF00FF99)
val NeonBg = Color(0xFF121212) // Very dark grey/black
val GlassBlack = Color(0xFF000000).copy(alpha = 0.7f) // 70% opaque black

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

@Composable
fun NeonCard(
    glowColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .padding(8.dp) // Make room for the glow so it doesn't get clipped
            .neonGlow(color = glowColor, blurRadius = 12.dp) // The Outer Glow
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = GlassBlack // Semi-transparent black
            ),
            border = BorderStroke(2.dp, glowColor), // The Sharp Neon Border
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}