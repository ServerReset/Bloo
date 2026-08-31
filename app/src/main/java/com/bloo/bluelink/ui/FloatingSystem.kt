package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One place that knows where every floating thing on screen currently is.
 *
 * "Floating" here means anything drawn over the page rather than in its scroll flow: the car
 * name once it docks into its corner pill, the page dots, the corner icon buttons. They overlap
 * each other, so they have to negotiate -- and until this existed, the ONE negotiation the app
 * actually did (dots hiding behind the flying name) was hand-wired as an `onNameBoundsChanged`
 * callback threaded down through TitleFlightOverlay -> FloatingNamePill -> FullDetail (twice)
 * -> GarageScreen, just to land in a `mutableStateOf` that got handed back down to the dots.
 * Adding a second pair of floaters that needed to avoid each other would have meant a second
 * parallel set of callbacks all the way down.
 *
 * Instead every floater reports its own bounds under an id ([Modifier.floatingElement]) and
 * anything that wants to get out of the way asks the registry ([Modifier.dodgeFloating]) --
 * no call site in between has to know the two exist, so a new floater is one modifier, not a
 * new parameter on five composables.
 *
 * Bounds are root-space and post-transform (`boundsInRoot`), which is what makes them
 * comparable across elements that live in completely different parts of the tree.
 */
@Immutable
@JvmInline
value class FloatingId(val name: String)

/** The app's own floaters. Anything may define its own id; these are just the ones that
 *  currently negotiate with each other. */
object FloatingIds {
    /** The car name / "Settings" title, whether inline on the hero card or docked in its pill. */
    val Title = FloatingId("title")
    /** The centered page-dot indicator. */
    val PagerDots = FloatingId("pagerDots")
}

@Stable
class FloatingRegistry {
    // Snapshot-backed so a dodger recomposes/redraws when a neighbour moves. Writes come from
    // onGloballyPositioned (layout phase is finished by then, so this is a safe place to write).
    private val bounds = mutableStateMapOf<FloatingId, Rect>()

    /** Publish (or with null, withdraw) this element's live bounds. */
    fun report(id: FloatingId, rect: Rect?) {
        if (rect == null) bounds.remove(id) else bounds[id] = rect
    }

    fun boundsOf(id: FloatingId): Rect? = bounds[id]

    /**
     * Does anything else registered overlap [rect]? [marginPx] pads the OTHER element, so two
     * things that merely come close still count as colliding -- a name ellipsizing right up
     * against the dots reads as a collision long before the rectangles actually intersect.
     */
    fun collidesWithOthers(self: FloatingId, rect: Rect?, marginPx: Float): Boolean {
        if (rect == null) return false
        bounds.forEach { (id, other) ->
            if (id != self && overlaps(rect, other, marginPx)) return true
        }
        return false
    }

    internal companion object {
        /** Delegates to uicommon's [com.bloo.uicommon.floatersOverlap] -- the same pure check
         *  the page dots have always used, kept over there because that is where the JVM tests
         *  pinning its boundary behaviour live. */
        fun overlaps(a: Rect, b: Rect, marginPx: Float): Boolean =
            com.bloo.uicommon.floatersOverlap(a, b, marginPx)
    }
}

/** Provided once for the whole app in `BlooApp` (Screens.kt). The default instance exists so a
 *  preview or an isolated composable still works without a host. */
val LocalFloatingRegistry = staticCompositionLocalOf { FloatingRegistry() }

/**
 * Publishes this element's live bounds to the registry under [id], so other floaters can avoid
 * it. [active] false withdraws them (an element that is present but not currently floating --
 * a title still inline in the page, say -- should not push anything around).
 */
fun Modifier.floatingElement(id: FloatingId, active: Boolean = true): Modifier = composed {
    val registry = LocalFloatingRegistry.current
    DisposableEffect(registry, id) {
        onDispose { registry.report(id, null) }
    }
    // Withdraw immediately on going inactive rather than waiting for a layout pass that may
    // never come (nothing moved, so onGloballyPositioned would not fire again).
    DisposableEffect(registry, id, active) {
        if (!active) registry.report(id, null)
        onDispose { }
    }
    onGloballyPositioned { if (active) registry.report(id, it.boundsInRoot()) }
}

/**
 * Fades this element out while any OTHER registered floater overlaps it, and back in when the
 * way is clear -- the generic form of "the page dots get out of the car name's way".
 *
 * Draw-phase only: the alpha is read inside a `graphicsLayer {}` lambda and the collision test
 * is a `derivedStateOf`, so a neighbour moving through this element does not recompose it. The
 * effect that drives the animation is keyed on the collision BOOLEAN, never on the bounds
 * themselves -- keying on bounds restarts (and so cancels) the animation on every frame the
 * neighbour moves, which is precisely how the dots ended up frozen half-visible under the name.
 */
fun Modifier.dodgeFloating(
    self: FloatingId,
    margin: Dp = 8.dp,
    dampingRatio: Float = 0.6f,
    stiffness: Float = Spring.StiffnessMedium,
): Modifier = composed {
    val registry = LocalFloatingRegistry.current
    val marginPx = with(LocalDensity.current) { margin.toPx() }
    val alpha = remember { Animatable(1f) }
    val colliding by remember(registry, self, marginPx) {
        derivedStateOf { registry.collidesWithOthers(self, registry.boundsOf(self), marginPx) }
    }
    LaunchedEffect(colliding) {
        alpha.animateTo(
            targetValue = if (colliding) 0f else 1f,
            animationSpec = spring(dampingRatio = dampingRatio, stiffness = stiffness),
        )
    }
    graphicsLayer { this.alpha = alpha.value }
}
