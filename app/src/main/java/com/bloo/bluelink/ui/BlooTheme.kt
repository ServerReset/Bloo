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

    // The system bars follow the APP's theme, not the phone's: SystemBarStyle.auto (MainActivity) only
    // sees the system setting, so an app forced Dark on a light phone had dark status icons on a dark
    // screen (and the reverse).
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
    // The STATIC scheme is cheap and is what the FIRST frame paints. The dynamic (Material You)
    // scheme extracts the user's wallpaper colours -- a synchronous binder call that can take
    // well over a second on a cold start -- and was being computed in a `remember` on the main
    // thread, i.e. ON the first frame. It is now resolved off the main thread and swapped in
    // when ready; until then the static scheme is on screen, so a cold start no longer waits on
    // the wallpaper for its first pixels.
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

    // Honour the device's own accessibility font-scale setting, but CLAMP and combine it
    // with the app's uiScale, rather than either ignoring it (the old `Density(density,
    // uiScale)`, which dropped the OS setting entirely) or multiplying it through unbounded
    // (which blew past every fixed-size floating overlay -- the identity pill, Settings'
    // header row -- since those track a pixel spot rather than reflowing). So:
    //   effective = clamp(deviceFontScale, 1..MaxFontScale) * uiScale
    // gives "huge fonts actually get bigger" while capping the blow-up at a point the fixed
    // overlays and the space scale were tuned to survive. Spacing follows the SAME effective
    // scale (see spaceScaleFor), so a bigger font gets breathing room, not clipping.
    val density = LocalDensity.current
    val effectiveFontScale = density.fontScale.coerceIn(1f, MaxFontScale) * uiScale
    val scaledDensity = Density(density.density, effectiveFontScale)

    // Keyed on the resolver rather than un-keyed, and read through a process-wide cache: this
    // is a synchronous binder IPC to the settings provider, and it sat on the first-frame path
    // inside composition. remember{} alone still pays it once per BlooTheme instance -- and the
    // theme is applied on every screen -- so the answer is cached for the process instead. It
    // cannot change without a configuration change that recreates everything anyway.
    val reduceMotion = remember(context.contentResolver) { reduceMotionCached(context) }
    // Memoize typography and motion computations to avoid recomputing on every recomposition
    val typography = remember(fontChoice) { expressiveTypography(fontChoice) }
    val motionScheme = remember { MotionScheme.expressive() }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = motionScheme,
        typography = typography,
        shapes = ExpressiveShapes,
    ) {
        // Defensive: this app's root Scaffold passes containerColor = Color.Transparent so the
        // edge-to-edge gradient/aurora Box behind it shows through, and
        // contentColorFor(Color.Transparent) resolves to Color.Unspecified since
        // Transparent isn't one of the theme's known roles -- which means ANY bare
        // Text() reached before something else (a Card, a themed Surface) sets its
        // own content color falls through to whatever's ambient. Explicit here so
        // that's always this theme's onBackground, never an unthemed framework
        // default, regardless of which screen or dialog it is.
        CompositionLocalProvider(
            LocalDensity provides scaledDensity,
            LocalReduceMotion provides reduceMotion,
            // The vertical gap scale follows the display scale (damped -- see spaceScaleFor),
            // so every gap/inset in the app breathes with the font-size setting together.
            LocalSpaceScale provides spaceScaleFor(effectiveFontScale),
            LocalContentColor provides scheme.onBackground,
            content = content,
        )
    }
}


/** True when the user has disabled animations in Accessibility settings. */
val LocalReduceMotion = staticCompositionLocalOf { false }


/**
 * "Is the APP dark right now?" -- the one answer, for any composable below [BlooTheme]
 * that needs to branch on it.
 *
 * This exists because the same four-line `when (themeMode)` block was re-typed at every
 * site that needed it, and the bug it exists to stop has now been reported and fixed
 * FIVE separate times: a composable reading `isSystemInDarkTheme()` directly, which is
 * the PHONE's setting and has nothing to say about the app's own Light/Dark
 * override. Every one of those five rendered a dark-mode treatment inside a light-themed
 * app (or the reverse) whenever the two disagreed -- [pebbleCardEdge]'s pebble shadow,
 * [glassTint]'s near-solid-black floating glass, `CarMap`'s tile/pin palette
 * (WeatherPebble.kt), `carTonalBrush`'s hero fallback gradient (Hero.kt) and
 * `chargeReadoutOf`'s "Parked" line (HeroReadout.kt). Four of the five were the identical
 * copy-paste of a rule that already had a correct implementation eight lines up in
 * [BlooTheme].
 *
 * So: ONE implementation, and the call is short enough that copying the `when` back out
 * is strictly more work than calling this. LIGHT/DARK force their answer regardless of
 * the system; only SYSTEM falls through to the phone -- byte for byte what [BlooTheme]
 * itself does when it picks the colour scheme, which is the
 * property that matters: anything branching on this agrees with the scheme it is drawing
 * against, by construction rather than by two places happening to stay in step.
 *
 * NOT a `LocalIsDark` composition local, deliberately: [LocalAppearance] is already
 * provided app-wide and already carries `themeMode`, so a second local holding a value
 * derived from the first is one more thing that can be forgotten at a provider.
 */
@Composable
internal fun appIsDarkTheme(): Boolean = when (LocalAppearance.current.themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}
