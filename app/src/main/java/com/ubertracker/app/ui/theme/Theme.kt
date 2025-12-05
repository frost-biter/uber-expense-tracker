package com.ubertracker.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- CYBERPUNK COLOR PALETTE (From your HTML) ---
val CyberBg = Color(0xFF0d0221)       // Dark Purple/Black Background
val CyberPink = Color(0xFFff00c1)     // Neon Pink (Unclaimed/Actions)
val CyberGreen = Color(0xFF00ff97)    // Neon Green (Claimed/Success)
val CyberBlue = Color(0xFF00c2ff)     // Neon Blue (Accents)
val CyberGray = Color(0xFFa6a6a6)     // Subtext

private val CyberpunkScheme = darkColorScheme(
    primary = CyberPink,
    onPrimary = CyberBg,
    secondary = CyberGreen,
    onSecondary = CyberBg,
    tertiary = CyberBlue,
    background = CyberBg,
    surface = CyberBg, // Cards match background but use borders
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun UberTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // DISABLED dynamic color to force Cyberpunk look
    content: @Composable () -> Unit
) {
    val colorScheme = CyberpunkScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = CyberBg.toArgb()
            window.navigationBarColor = CyberBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}