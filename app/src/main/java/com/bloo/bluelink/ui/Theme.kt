package com.bloo.bluelink.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF0B5FFF)
private val BlueDark = Color(0xFF4C8DFF)

private val LightColors = lightColorScheme(primary = Blue, secondary = Blue)
private val DarkColors = darkColorScheme(primary = BlueDark, secondary = BlueDark)

@Composable
fun BlooTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
