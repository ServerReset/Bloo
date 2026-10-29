package com.bloo.uicommon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.max

/**
 * One pop-in cascade: a surface (a pebble, a card) opening or closing, and the components on it that pop in one after another.
 *
 * Each component that calls [cascadeItem] claims an index in the order it is composed, so the cascade runs top to bottom as the
 * surface opens and bottom to top as it closes. Progress is read in the draw phase, so the cascade never recomposes the components.
 */
class Cascade internal constructor(
    private val progress: () -> Float,
    private val closing: () -> Boolean,
) {
    private var claimed = 0

    internal fun claim(): Int = claimed++

    internal val count: Int get() = claimed

    internal fun progressNow(): Float = progress()

    internal fun closingNow(): Boolean = closing()
}

/** The cascade the nearest surface opened, or null where nothing is cascading (every component then simply shows). */
val LocalCascade = compositionLocalOf<Cascade?> { null }

/** Builds the cascade for a surface, from its open-and-close progress (0 closed, 1 open) and whether it is closing. */
fun newCascade(progress: () -> Float, closing: () -> Boolean): Cascade = Cascade(progress, closing)

/**
 * Makes this component pop in and out as its surface's cascade reaches it. Outside a cascade it does nothing, so a component can
 * call it unconditionally and be used anywhere.
 */
@Composable
fun Modifier.cascadeItem(): Modifier {
    val cascade = LocalCascade.current ?: return this
    val index = remember(cascade) { cascade.claim() }
    return graphicsLayer { applyCascade(cascade, index) }
}

/** The cascade's pop for one component at [index]: its own short window of the shared progress, with a soft overshoot on the way in. */
private fun GraphicsLayerScope.applyCascade(cascade: Cascade, index: Int) {
    val progress = cascade.progressNow()
    val total = max(1, cascade.count)
    // Where this component sits in the surface, 0 at the top to just under 1 at the bottom.
    val reached = index.toFloat() / total
    val vis: Float
    val scaleT: Float
    if (cascade.closingNow()) {
        // Closing: the bottom components leave first. progress falls 1 to 0, so (1 - progress) is the closing clock.
        val start = ((1f - reached) * CascadeExitCoverage + CascadePopDelay).coerceAtMost(0.9f)
        val raw = (((1f - progress) - start) / CascadePopWindow).coerceIn(0f, 1f)
        val gone = raw * raw * (3f - 2f * raw)
        vis = 1f - gone
        scaleT = 1f - gone
    } else {
        val start = (reached * CascadeRevealCoverage + CascadePopDelay).coerceAtMost(0.9f)
        val raw = ((progress - start) / CascadePopWindow).coerceIn(0f, 1f)
        vis = raw * raw * (3f - 2f * raw)
        scaleT = cascadeOvershoot(raw)
    }
    // Opacity is modulated, not rendered into its own buffer, so glass rows do not get an offscreen copy for the length of the open.
    compositingStrategy = CompositingStrategy.ModulateAlpha
    alpha = vis
    // 0.82 to 1.0 rather than 0.9: a smaller change is lost next to the alpha fade.
    scaleX = 0.82f + 0.18f * scaleT
    scaleY = 0.82f + 0.18f * scaleT
    transformOrigin = TransformOrigin(0f, 0.5f)
}

/** How much of the opening a component's position maps onto. */
internal const val CascadeRevealCoverage = 0.6f

/** How much of the closing a component's position maps onto. */
internal const val CascadeExitCoverage = 0.5f

/** The beat, in progress, between the surface reaching a component and the component popping. */
internal const val CascadePopDelay = 0.08f

/** How much progress a component's pop takes. */
internal const val CascadePopWindow = 0.25f

/** A short overshoot-then-settle curve for the pop, so components land with the same character as the pebble itself. */
fun cascadeOvershoot(t: Float): Float {
    val overshoot = 1.15f
    val c3 = overshoot + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + overshoot * x * x
}
