package com.bloo.bluelink.wear

import com.bloo.uicommon.LocalPowerPolicy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import com.bloo.uicommon.canBlurBackdrops
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp

/*
 * The watch's two glass ingredients, shared by every screen so they all read as one language with the phone:
 * the hairline rim of a glass surface, and the soft wash of colour (the phone's aurora) behind a full screen.
 */

/** A hairline rim, bright toward the top-left and again faintly at the bottom-right, like the phone's cards. */
fun Modifier.wearGlassRim(shape: Shape): Modifier = border(
    1.dp,
    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.2f))),
    shape,
)

/** The Haze state of the watch's aurora, which glass panes blur; null where there is no backdrop (a preview, a test). */
internal val LocalWearHaze = androidx.compose.runtime.staticCompositionLocalOf<HazeState?> { null }

/**
 * The watch's aurora, as the phone has one: drifting soft blobs of the accent palette on black, with a faint vignette,
 * marked as the Haze source every glass pane in front of it blurs. It drifts slowly (in the draw phase, a handful of
 * times a second -- every step re-blurs the glass above it, so it is deliberately not 60fps) and stands still whenever the
 * device runs lean: Battery Saver, a hot watch, or the wearer's own reduce-effects switch.
 */
@androidx.compose.runtime.Composable
internal fun WearBackdrop(modifier: Modifier = Modifier, content: @androidx.compose.runtime.Composable () -> Unit) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val haze = androidx.compose.runtime.remember { HazeState() }
    val policy = LocalPowerPolicy.current
    var phase by androidx.compose.runtime.mutableFloatStateOf(0f)
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val start = androidx.compose.runtime.remember { System.nanoTime() }
    androidx.compose.runtime.LaunchedEffect(policy.ambientMotion) {
        if (!policy.ambientMotion) return@LaunchedEffect
        // Only while the watch is in front of the wearer: a stopped or dimmed activity keeps its composition, and a loop
        // that outlived the screen would wake the CPU a few times a second for nothing.
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                phase = ((System.nanoTime() - start) / 1_000_000_000f) / DriftSeconds * (2f * Math.PI.toFloat())
                kotlinx.coroutines.delay(DriftStepMs)
            }
        }
    }
    val primary = scheme.primary
    val secondary = scheme.secondary
    val tertiary = scheme.tertiary
    androidx.compose.foundation.layout.Box(modifier) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .drawBehind {
                    drawRect(Color.Black)
                    val t = phase
                    fun blob(c: Color, fx: Float, fy: Float, r: Float) {
                        val center = androidx.compose.ui.geometry.Offset(size.width * fx, size.height * fy)
                        val radius = size.minDimension * r
                        drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center = center, radius = radius), radius, center)
                    }
                    // Dimmer than the phone's: this is an OLED a wrist holds close, and what is lit costs battery.
                    blob(primary.copy(alpha = 0.34f), 0.30f + 0.06f * kotlin.math.sin(t), 0.26f + 0.05f * kotlin.math.cos(t), 0.80f)
                    blob(tertiary.copy(alpha = 0.26f), 0.80f + 0.05f * kotlin.math.cos(t * 1.3f), 0.34f + 0.06f * kotlin.math.sin(t * 0.9f), 0.62f)
                    blob(secondary.copy(alpha = 0.24f), 0.50f + 0.06f * kotlin.math.sin(t * 0.7f), 0.84f + 0.04f * kotlin.math.cos(t * 1.1f), 0.72f)
                    // The vignette, so the glass and the clock read at the bezel.
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
                            radius = size.maxDimension * 0.62f,
                        ),
                    )
                },
        )
        androidx.compose.runtime.CompositionLocalProvider(LocalWearHaze provides haze) { content() }
    }
}

/** One drift cycle, in seconds: slow enough to read as ambient, not as animation. */
private const val DriftSeconds = 70f

/** Time between drift steps: about 4 a second (each step re-blurs the glass above, so it is kept low). */
private const val DriftStepMs = 250L

/**
 * Frosted glass for a watch surface: the backdrop blurred behind [shape] when the device can and may (API 31+, not lean),
 * else nothing -- the caller's own translucent fill and [wearGlassRim] then carry the look. Put it BEFORE the fill.
 */
@androidx.compose.runtime.Composable
fun Modifier.wearGlassBlur(shape: Shape): Modifier {
    val haze = LocalWearHaze.current
    if (haze == null || !canBlurBackdrops()) return this
    return this.clip(shape).hazeBlur(
        input = HazeInput.Sources(haze),
        style = HazeBlurStyle { blurRadius(WearBlurRadius) },
        performanceMode = HazePerformanceMode.Performance,
    )
}

private val WearBlurRadius = 14.dp

/** The wash behind a full screen: [tint] fading to nothing from the middle. */
fun Modifier.wearAuroraWash(tint: Color): Modifier = background(
    Brush.radialGradient(listOf(tint.copy(alpha = 0.22f), Color.Transparent)),
)

/**
 * Scales THIS element so it sits inside the round display: its widest edge is compared with the chord of the
 * circle at the far end of its height, and it shrinks (never below [minScale]) to fit that chord -- text,
 * a row, a heading, each on its own, wherever it currently is on the screen. A row at the middle is full
 * size and one nearing the top or bottom narrows with the curve, instead of being clipped by the bezel or the
 * whole card being scaled as one.
 */
@androidx.compose.runtime.Composable
fun Modifier.fitRound(minScale: Float = 0.55f): Modifier {
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    var centreY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(Float.NaN) }
    var width by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var height by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned { centreY = it.positionInWindow().y + it.size.height / 2f; width = it.size.width.toFloat(); height = it.size.height.toFloat() }
        .graphicsLayer {
            if (centreY.isNaN() || width <= 0f || window.width <= 0) return@graphicsLayer
            val s = roundFitScale(centreY, width, height, window.width.toFloat(), window.height.toFloat(), minScale)
            scaleX = s
            scaleY = s
        }
}

/**
 * The scale at which an element [width] x [height] centred at [centreY] fits the round display [displayWidth] x
 * [displayHeight]: its far edge's distance from the middle gives the circle's chord there, and the element shrinks
 * to that chord, never below [minScale] and never above 1. Pure, so it can be tested.
 */
internal fun roundFitScale(centreY: Float, width: Float, height: Float, displayWidth: Float, displayHeight: Float, minScale: Float): Float {
    if (width <= 0f || displayWidth <= 0f) return 1f
    val radius = minOf(displayWidth, displayHeight) / 2f
    val reach = kotlin.math.abs(centreY - displayHeight / 2f) + height / 2f
    val chord = if (reach >= radius) 0f else 2f * kotlin.math.sqrt(radius * radius - reach * reach)
    return (chord / width).coerceIn(minScale, 1f)
}
