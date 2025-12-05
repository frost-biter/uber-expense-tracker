package com.ubertracker.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow

// Helper for the "Text Glow" effect seen in the HTML
fun neonGlow(color: Color) = Shadow(
    color = color,
    offset = Offset(0f, 0f),
    blurRadius = 12f
)

val Typography = Typography(
    // Large Header (App Title)
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 2.sp, // Wide tracking like the HTML
        shadow = neonGlow(CyberPink) // Pink Glow
    ),
    // Section Headers (Pending/History)
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 1.sp
    ),
    // Normal Text
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    // Small labels
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)