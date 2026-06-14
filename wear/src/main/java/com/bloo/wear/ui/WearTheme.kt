package com.bloo.wear.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The phone app's dark "Expressive" palette, reused so the watch matches the
// flip-phone cover screen. A watch is effectively always-dark, so there is no
// light scheme.
private val BlooDark = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFCFBCFF),
    onSecondary = Color(0xFF381E72),
    tertiary = Color(0xFF4CD9E0),
    onTertiary = Color(0xFF00363A),
    tertiaryContainer = Color(0xFF004F54),
    onTertiaryContainer = Color(0xFF9EF0F6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
)

/** Brand accent colours used across tiles (mirrors Screens.kt constants). */
object WearColors {
    val chargeGreen = Color(0xFF2EBD59)
    val seatHeat = Color(0xFFE5484D)
}

@Composable
fun BlooWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BlooDark, content = content)
}
