package com.lr.immersiveaudiobook.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD9AA68),
    onPrimary = Color(0xFF321F0E),
    primaryContainer = Color(0xFF4B3420),
    onPrimaryContainer = Color(0xFFFFDDB5),
    secondary = Color(0xFFCDBBA6),
    background = Color(0xFF15120F),
    surface = Color(0xFF1D1915),
    surfaceVariant = Color(0xFF2A241F),
    onBackground = Color(0xFFF0E6DA),
    onSurface = Color(0xFFF0E6DA),
    outline = Color(0xFF8F8174),
    error = Color(0xFFFFB4AB)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF7A4E20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB5),
    onPrimaryContainer = Color(0xFF2A1700),
    secondary = Color(0xFF6F5B46),
    background = Color(0xFFFFF8F2),
    surface = Color(0xFFFFF8F2),
    surfaceVariant = Color(0xFFF0E3D6),
    onBackground = Color(0xFF211A14),
    onSurface = Color(0xFF211A14),
    outline = Color(0xFF817467)
)

@Composable
fun LrAudiobookTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content
    )
}
