package com.inventornext.vivacoach.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF1E2A5A)
private val Amber = Color(0xFFFFC857)

private val Light = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF0E1A47),
    secondary = Color(0xFF8A5A00),
    secondaryContainer = Color(0xFFFFE6B3),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFF2E6B4F),
    tertiaryContainer = Color(0xFFCDEBD9),
    background = Color(0xFFFAFAFD),
    surface = Color(0xFFFAFAFD),
    surfaceVariant = Color(0xFFE6E7F0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    onPrimary = Color(0xFF0E1A47),
    primaryContainer = Color(0xFF2E3B73),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Amber,
    secondaryContainer = Color(0xFF5C4100),
    onSecondaryContainer = Color(0xFFFFE6B3),
    tertiary = Color(0xFF9ED5B8),
    tertiaryContainer = Color(0xFF16503A),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF2A2D38),
)

@Composable
fun VivaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Colour for a 0-10 or 0-100 score. */
@Composable
fun scoreColor(fraction: Float): Color = when {
    fraction >= 0.75f -> Color(0xFF2E9D63)
    fraction >= 0.5f -> Color(0xFFD99A00)
    else -> Color(0xFFD1453B)
}
