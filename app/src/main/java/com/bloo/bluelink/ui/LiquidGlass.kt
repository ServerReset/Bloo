package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Real "liquid glass" for the opt-in Settings toggle, via the Haze library
 * (`dev.chrisbanes.haze`) — hardware backdrop-blur sampled from the content
 * behind each floating element, with a theme tint on top.
 *
 * ## Why Haze (and not the old Kyant Backdrop)
 * Kyant Backdrop hard-crashed the app at runtime (a Compose-Multiplatform lib
 * built against a newer Kotlin than this project, plus a fragile RuntimeShader
 * lens). Haze is AndroidX-native and 1.7.2 is built against this project's exact
 * Kotlin (2.2.20), and it does its blur with the platform's own `RenderEffect`
 * (Android 12 / API 31+), degrading to a translucent scrim below that instead
 * of a shader crash.
 *
 * ## Crash safety (the whole point)
 * Every Haze call is confined to THIS file and gated so it can only run where
 * it's safe:
 *  - The toggle must be ON *and* the device must be API 31+ ([glassSupported]).
 *  - A floating element only blurs if a live [HazeState] reached it
 *    ([LocalHazeState]); a Dialog/Popup in a separate window won't have one and
 *    safely uses the frosted fallback.
 *  - Anything not on the real path routes to [liquidGlassFallback]
 *    (LiquidGlassFallback.kt — a translucent tint + specular edge, ZERO Haze
 *    dependency), so a glass problem can never brick the app.
 * The public API here (LiquidGlassRoot / Modifier.liquidGlass / the two locals /
 * liquidGlassSupported) is unchanged from before, so no call site needed edits.
 *
 * If Haze ever misbehaves: delete the `dev.chrisbanes.haze` line in
 * app/build.gradle.kts and replace this file's Haze paths with the
 * fallback-only version — the toggle keeps working on frosted.
 */

/** True where Haze's RenderEffect blur is actually supported (Android 12+).
 *  Below this, and whenever the toggle is off, the frosted fallback is used. */
val glassSupported: Boolean get() = Build.VERSION.SDK_INT >= 31

/** Back-compat alias for the old name some call sites may still read. */
val liquidGlassSupported: Boolean get() = glassSupported

/** The live Haze source that floating glass samples, or null when glass is off /
 *  unsupported / unreachable (a separate Dialog/Popup window). [Modifier.liquidGlass]
 *  falls back to frosted whenever this is null. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Kept purely for source-compatibility with earlier call sites that referenced
 *  the old backdrop local; it is no longer read by anything. */
val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Any?> { null }

/**
 * Wraps the app content so descendant [Modifier.liquidGlass] calls can sample a
 * live blurred backdrop. When [enabled] and [glassSupported], marks [content] as
 * the Haze source and publishes the state; otherwise renders [content] unchanged
 * with a null state, so every glass call site takes its frosted fallback path.
 * Placed once, high in [BlooApp], around the whole screen stack.
 */
@Composable
fun LiquidGlassRoot(
    enabled: Boolean,
    baseColor: Color,
    content: @Composable () -> Unit,
) {
    if (!enabled || !glassSupported) {
        CompositionLocalProvider(LocalHazeState provides null) { content() }
        return
    }
    val hazeState = remember { HazeState() }
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize().hazeSource(state = hazeState)) { content() }
    }
}

/**
 * Apply the liquid-glass material to a floating element (button, search bar,
 * dialog surface, card/pebble) of the given [shape].
 *
 * When a live [LocalHazeState] is present (toggle on + API 31+ + reachable),
 * draws a real hardware blur of the backdrop with a translucent theme [tint] on
 * top (via Haze's [hazeEffect]). Otherwise delegates to [liquidGlassFallback]
 * — the dependency-free enhanced-frosted look that works on every device.
 *
 * [tintAlpha] controls the tint strength; [blurRadiusDp] the blur amount. The
 * old [refractionDp] param is accepted-and-ignored for call-site compatibility.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color,
    tintAlpha: Float = 0.5f,
    blurRadiusDp: Float = 20f,
    @Suppress("UNUSED_PARAMETER") refractionDp: Float = 14f,
): Modifier {
    val hazeState = LocalHazeState.current
    if (hazeState == null || !glassSupported) {
        return this.liquidGlassFallback(shape, tint, tintAlpha)
    }
    val tintColor = tint.copy(alpha = tintAlpha)
    return this
        .clip(shape)
        .hazeEffect(state = hazeState) {
            blurRadius = blurRadiusDp.dp
            backgroundColor = tint
            tints = listOf(HazeTint(tintColor))
        }
}
