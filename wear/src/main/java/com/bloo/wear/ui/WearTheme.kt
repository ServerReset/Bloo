package com.bloo.wear.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.WearColorRoles
import com.bloo.bluelink.data.WearSettingsPayload

/** True when the user has disabled animations in Accessibility settings. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Brand accents reused across the watch UI, sourced from the shared BlooColors. */
object WearColors {
    val chargeGreen = Color(BlooColors.chargeGreen)
    val heat        = Color(BlooColors.heat)
    val cool        = Color(BlooColors.cool)
}

/** Build a Wear M3 [ColorScheme] from the phone's resolved role colours. */
fun schemeFrom(c: WearColorRoles): ColorScheme = ColorScheme(
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
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // Rebuild the 25-colour scheme only when the synced roles actually change.
    val scheme = colors?.let { c -> remember(c) { schemeFrom(c) } }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        if (scheme != null) {
            MaterialTheme(colorScheme = scheme, content = content)
        } else {
            MaterialTheme(content = content)
        }
    }
}
