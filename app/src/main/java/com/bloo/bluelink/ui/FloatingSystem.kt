package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
    /** The Settings cog in the top-right corner. */
    val SettingsIcon = FloatingId("settingsIcon")
    /** The flip-columns button, beside the cog while a car is expanded. */
    val FlipIcon = FloatingId("flipIcon")
    /** The back arrow in the top-left corner while a car is expanded. */
    val BackIcon = FloatingId("backIcon")
    /**
     * The search bubble/bar. The one floater a PERSON positions: on a cover screen it can be
     * dragged and parked anywhere along an edge, and where the device reports a camera island it
     * docks into that band instead. Registering it publishes wherever it ended up, so the rest of
     * the chrome can avoid it -- which is the whole point of it being in here, and is why nothing
     * about how it is placed belongs in this system. See SearchLayer.
     */
    val Search = FloatingId("search")
    /** The pull-to-refresh spinner, which takes the page dots' place while a refresh runs. */
    val RefreshIndicator = FloatingId("refreshIndicator")
    /** The cover screen's right-edge tile scrubber. Its own id, not the page dots': it is a
     *  different control in a different place, and sharing an id would have two elements
     *  overwriting each other's bounds. */
    val TileRail = FloatingId("tileRail")
}

@Stable
class FloatingRegistry {
    // Snapshot-backed so a dodger recomposes/redraws when a neighbour moves. Writes come from
    // onGloballyPositioned (layout phase is finished by then, so this is a safe place to write).
    private val bounds = mutableStateMapOf<FloatingId, Rect>()

    /**
     * Shared chrome state, applied by [Modifier.floatingOverlay] rather than by each element.
     *
     * Two behaviours belong to floating chrome as a class, not to any one piece of it: it slides
     * down while the user pulls to refresh, and the transient parts of it fade out while a
     * refresh is actually running. Both used to be hand-applied at each site, which is exactly
     * why they disagreed -- the page dots took the fade but not the shift (though the comment
     * driving it named the dots first), and the corner buttons took the shift but not the fade.
     * Holding them here means a new floating element gets the same behaviour by declaring one
     * modifier, and cannot quietly get half of it.
     *
     * Targets, not animated values: the modifier owns the springs, so a screen publishing these
     * does not recompose on every frame of them.
     */
    var chromeShiftTarget by mutableStateOf(0.dp)
    var chromeHidden by mutableStateOf(false)

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

/**
 * Everything a floating element needs, in one modifier: it publishes its bounds so others can
 * avoid it, rides the pull-to-refresh shift, and fades while a refresh runs.
 *
 * [fade] is off for chrome that is *about* the refresh (the loading indicator, which must stay
 * visible precisely when everything else goes) and for persistent navigation that should not
 * blink. [shift] is off for anything anchored to the screen rather than to the page beneath it --
 * the Settings cog is a nav target, not page chrome, so it stays put while the page slides.
 */
fun Modifier.floatingOverlay(
    id: FloatingId,
    active: Boolean = true,
    fade: Boolean = true,
    shift: Boolean = true,
): Modifier = composed {
    val registry = LocalFloatingRegistry.current
    val shiftDp by animateDpAsState(
        targetValue = if (shift) registry.chromeShiftTarget else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (registry.chromeHidden) Spring.StiffnessLow else Spring.StiffnessMedium,
        ),
        label = "floatingShift",
    )
    val alpha by animateFloatAsState(
        targetValue = if (fade && registry.chromeHidden) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "floatingFade",
    )
    this
        // Layout phase and draw phase respectively -- neither re-runs composition per frame,
        // which is the whole reason these are read inside lambdas.
        .offset { IntOffset(0, shiftDp.roundToPx()) }
        .graphicsLayer { this.alpha = alpha }
        .floatingElement(id, active)
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
 *
 * MUST be paired with [floatingElement] using the same [self] id, because the collision test
 * asks the registry where `self` is. Alone, this modifier finds no bounds for itself, reports
 * no collision, and simply never dodges -- a silent no-op with nothing to notice, which is why
 * it is stated here rather than left to be discovered.
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
