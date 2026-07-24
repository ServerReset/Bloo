package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * The one and only place the app touches the Kyant Backdrop library.
 *
 * ## Why this file is isolated
 * Every `com.kyant.backdrop.*` import in the whole app lives here. That is
 * deliberate: the library provides *real* hardware-refraction glass (the
 * iOS-26 "liquid glass" look the user opted into in Settings), but it is a
 * young dependency and its render effects require newer Android than Bloo's
 * `minSdk 26`. By funnelling all Backdrop usage through this file behind
 * [Modifier.liquidGlass], the rest of the UI stays library-agnostic and the
 * whole feature degrades to the existing frosted chrome ([frostedRim] /
 * [ambientRing] / a translucent tint) whenever glass is off, unsupported, or
 * unavailable — see [Modifier.liquidGlass].
 *
 * ## Platform gate
 * Backdrop's blur is a `RenderEffect` (Android 12 / API 31+) and its `lens`
 * refraction uses `RuntimeShader` (Android 13 / API 33+). Below those levels
 * we never call Backdrop at all — [liquidGlassSupported] gates every path.
 *
 * ## If the build fails on this file
 * The Backdrop dependency version is pinned in `app/build.gradle.kts` and is
 * the one thing that can't be verified without a compile. If CI rejects it,
 * the fix is contained: remove the `io.github.kyant0:backdrop` line and this
 * file, and [Modifier.liquidGlass] falls back to `liquidGlassFallback` (which
 * lives in LiquidGlassFallback.kt and has NO Backdrop dependency), so the
 * Settings toggle keeps working with the enhanced-frosted look.
 */

/** True when the device can render Backdrop effects at all (API 31+ for blur).
 *  Below this the liquid-glass toggle silently uses the frosted fallback. */
val liquidGlassSupported: Boolean get() = Build.VERSION.SDK_INT >= 31

/** The root backdrop layer that floating glass samples from. `null` means
 *  "no live glass here" — either the user's toggle is off, the device is
 *  pre-API-31, or we're inside a separate window (Dialog/Popup) that can't
 *  reach the root layer — and [Modifier.liquidGlass] uses the frosted fallback. */
val LocalLiquidGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Wraps the app content so descendant [Modifier.liquidGlass] calls can sample a
 * live backdrop. When [enabled] and [liquidGlassSupported], captures [content]
 * as a Backdrop layer (with an opaque [baseColor] fill so transparent pixels
 * outside the drawn content don't sample garbage) and publishes it via
 * [LocalLiquidGlassBackdrop]. Otherwise renders [content] unchanged with a null
 * backdrop, so every glass call site takes its frosted fallback path.
 *
 * Placed once, high in [BlooApp], around the whole screen stack.
 */
@Composable
fun LiquidGlassRoot(
    enabled: Boolean,
    baseColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    if (!enabled || !liquidGlassSupported) {
        // Toggle off or unsupported device: no backdrop, content unchanged.
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides null) { content() }
        return
    }
    val backdrop = rememberLayerBackdrop {
        // Opaque base first so the sampled layer has no transparent regions,
        // then the real content on top — this is the layer floating glass refracts.
        drawRect(baseColor)
        drawContent()
    }
    CompositionLocalProvider(LocalLiquidGlassBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content() }
    }
}

/**
 * Apply the liquid-glass material to a floating element (button, search bar,
 * dialog surface, card/pebble) of the given [shape].
 *
 * - When a live [LocalLiquidGlassBackdrop] is present (toggle on + API 31+),
 *   draws real refraction: a subtle [vibrancy] boost, a soft [blur] of the
 *   backdrop, a [lens] refraction bending the edges, and a faint theme tint
 *   drawn *after* the effect (via `onDrawSurface`) so content keeps contrast.
 * - Otherwise delegates to [liquidGlassFallback] — the enhanced-frosted look
 *   that needs no Backdrop dependency and works on every device.
 *
 * [tint] is the theme-derived surface color the glass is nudged toward (pass a
 * role like `surfaceContainer`); [tintAlpha] how strongly (kept low so the
 * refraction stays visible). [blurRadiusDp]/[refractionDp] tune the material.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    tint: androidx.compose.ui.graphics.Color,
    tintAlpha: Float = 0.5f,
    blurRadiusDp: Float = 6f,
    refractionDp: Float = 14f,
): Modifier {
    val backdrop = LocalLiquidGlassBackdrop.current
        ?: return this.liquidGlassFallback(shape, tint, tintAlpha)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val blurPx = with(density) { blurRadiusDp.dp.toPx() }
    val refractionPx = with(density) { refractionDp.dp.toPx() }
    val surfaceTint = tint.copy(alpha = tintAlpha)
    return this
        .clip(shape)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blurPx)
                lens(refractionPx, refractionPx * 2f)
            },
            // Theme tint drawn AFTER the effect so text over the glass stays legible.
            onDrawSurface = { drawRect(surfaceTint) },
        )
}
