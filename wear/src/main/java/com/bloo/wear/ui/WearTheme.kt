package com.bloo.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme

/** Brand accents reused across the watch UI (mirrors the phone's Screens.kt). */
object WearColors {
    val chargeGreen = Color(0xFF2EBD59)
    val heat = Color(0xFFE5484D)
    val cool = Color(0xFF2E78FF)
}

/**
 * Wear OS Material 3 (Expressive) theme. We lean on the framework's default
 * watch colour scheme + typography + shapes — they're already the Expressive
 * watch system — so the look matches Google's guidance out of the box.
 */
@Composable
fun BlooWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
