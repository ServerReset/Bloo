package com.bloo.bluelink.ui

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun BlooTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontChoice: FontChoice = FontChoice.SYSTEM,
    dynamicColor: Boolean = true,
    colorPalette: ColorPalette = ColorPalette.BLUE,
    customPalette: CustomPaletteData? = null,
    uiScale: Float = 1f,
    vibrancy: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    // System bars follow the app's theme, not the phone's (SystemBarStyle.auto only sees the system
    // setting).
    val barsView = androidx.compose.ui.platform.LocalView.current
    if (!barsView.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            var ctx = barsView.context
            while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
            val window = (ctx as? android.app.Activity)?.window ?: return@SideEffect
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, barsView)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    val context = LocalContext.current
    // The cheap static scheme paints the first frame; the dynamic (Material You) scheme needs a
    // slow synchronous binder call, so it is resolved off the main thread and swapped in when
    // ready.
    val staticScheme = remember(dark, colorPalette, customPalette, vibrancy) {
        blooColorScheme(
            context = context,
            dark = dark,
            dynamicColor = false,
            colorPalette = colorPalette,
            customPalette = customPalette,
            vibrancy = vibrancy,
        )
    }
    val scheme by produceState(
        initialValue = staticScheme,
        dark, dynamicColor, colorPalette, customPalette, vibrancy,
    ) {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            value = withContext(Dispatchers.Default) {
                blooColorScheme(
                    context = context,
                    dark = dark,
                    dynamicColor = true,
                    colorPalette = colorPalette,
                    customPalette = customPalette,
                    vibrancy = vibrancy,
                )
            }
        }
    }

    // Spacing follows the same scale (see spaceScaleFor).
    val density = LocalDensity.current
    val effectiveFontScale = density.fontScale.coerceIn(1f, MaxFontScale) * uiScale
    val scaledDensity = Density(density.density, effectiveFontScale)

    // Read through a process-wide cache: a synchronous binder IPC on the first-frame path, and it
    // cannot change without a configuration change.
    val reduceMotion = remember(context.contentResolver) { reduceMotionCached(context) }
    // Memoize typography and motion to avoid recomputing on every recomposition.
    val typography = remember(fontChoice) { expressiveTypography(fontChoice) }
    val motionScheme = remember { MotionScheme.expressive() }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = motionScheme,
        typography = typography,
        shapes = ExpressiveShapes,
    ) {
        // The root Scaffold passes Color.Transparent, so contentColorFor yields Unspecified; set
        // onBackground explicitly so bare Text() never falls back to an unthemed default.
        CompositionLocalProvider(
            LocalDensity provides scaledDensity,
            LocalReduceMotion provides reduceMotion,
            // The gap scale follows the damped display scale (see spaceScaleFor).
            LocalSpaceScale provides spaceScaleFor(effectiveFontScale),
            LocalContentColor provides scheme.onBackground,
            content = content,
        )
    }
}

/** True when the user has disabled animations in Accessibility settings. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Honours the app's Light/Dark override; only SYSTEM falls through to the phone. Never read
 * `isSystemInDarkTheme()` directly. Matches how [BlooTheme] picks its colour scheme.
 */
@Composable
internal fun appIsDarkTheme(): Boolean = when (LocalAppearance.current.themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}
