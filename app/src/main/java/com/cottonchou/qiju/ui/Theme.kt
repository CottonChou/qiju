package com.cottonchou.qiju.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF0B1124)
val DarkSurface = Color(0xFF121A32)
val DarkCard = Color(0xFF1A2340)
val DarkCardHi = Color(0xFF222D52)
val Accent = Color(0xFF5B8DEF)
val Warm = Color(0xFFF5B85C)
val TextPrimary = Color(0xFFE8ECF6)
val TextSecondary = Color(0xFF8A93AD)
val TextDim = Color(0xFF5A6280)

private val ColorScheme = darkColorScheme(
    primary = Accent,
    secondary = Warm,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun QijuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}
