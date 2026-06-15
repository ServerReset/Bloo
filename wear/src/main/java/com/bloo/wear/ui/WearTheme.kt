package com.bloo.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.bloo.bluelink.data.WearColorRoles
import com.bloo.bluelink.data.WearSettingsPayload

/** Brand accents reused across the watch UI (mirrors the phone's Screens.kt). */
object WearColors {
    val chargeGreen = Color(0xFF2EBD59)
    val heat = Color(0xFFE5484D)
    val cool = Color(0xFF2E78FF)
}

/** Build a Wear M3 [ColorScheme] from the phone's resolved role colours. */
private fun schemeFrom(c: WearColorRoles): ColorScheme = ColorScheme(
    primary = Color(c.primary),
    onPrimary = Color(c.onPrimary),
    primaryContainer = Color(c.primaryContainer),
    onPrimaryContainer = Color(c.onPrimaryContainer),
    secondary = Color(c.secondary),
    onSecondary = Color(c.onSecondary),
    secondaryContainer = Color(c.secondaryContainer),
    onSecondaryContainer = Color(c.onSecondaryContainer),
    tertiary = Color(c.tertiary),
    onTertiary = Color(c.onTertiary),
    tertiaryContainer = Color(c.tertiaryContainer),
    onTertiaryContainer = Color(c.onTertiaryContainer),
    background = Color(c.background),
    onBackground = Color(c.onBackground),
    onSurface = Color(c.onSurface),
    onSurfaceVariant = Color(c.onSurfaceVariant),
    surfaceContainerLow = Color(c.surfaceContainerLow),
    surfaceContainer = Color(c.surfaceContainer),
    surfaceContainerHigh = Color(c.surfaceContainerHigh),
    outline = Color(c.outline),
    outlineVariant = Color(c.outlineVariant),
    error = Color(c.error),
    onError = Color(c.onError),
    errorContainer = Color(c.errorContainer),
    onErrorContainer = Color(c.onErrorContainer),
)

/**
 * Wear OS Material 3 (Expressive) theme. When the phone has synced its resolved
 * colours, we paint with those so the watch matches the phone exactly; otherwise
 * we fall back to the framework's default expressive watch scheme.
 */
@Composable
fun BlooWearTheme(settings: WearSettingsPayload?, content: @Composable () -> Unit) {
    val colors = settings?.colors
    if (colors != null) {
        MaterialTheme(colorScheme = schemeFrom(colors), content = content)
    } else {
        MaterialTheme(content = content)
    }
}
