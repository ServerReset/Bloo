package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Registry of where every floating element (corner buttons, search bubble, refresh indicator)
 * currently is.
 */
@Immutable
@JvmInline
value class FloatingId(val name: String)

/** The app's own floaters; anything may define its own id. */
object FloatingIds {
    /**
     * The search bubble/bar, the one floater a person positions (dragged along an edge or docked in
     * a camera island). See SearchLayer.
     */
    val Search = FloatingId("search")
}

@Stable
class FloatingRegistry {
    // Snapshot-backed so a dodger recomposes when a neighbour moves; written from
    // onGloballyPositioned (post-layout).
    private val bounds = mutableStateMapOf<FloatingId, Rect>()

    /**
     * Pull-to-refresh progress, 0..1, as a lambda so the drag is read in the layout phase
     * ([Modifier.floatingOverlay]'s offset lambda) and never recomposes the screen per frame.
     */
    var chromePull: () -> Float = { 0f }

    /**
     * A refresh is in flight and the shift holds open; narrower than [chromeHidden], which is also
     * true during the pull.
     */
    var chromeHolding by mutableStateOf(false)

    /** Chrome should fade: true through the pull AND the refresh, unlike [chromeHolding]. */
    var chromeHidden by mutableStateOf(false)

    /**
     * Back to rest. A publishing screen must call this when it leaves so its state doesn't outlive
     * it.
     */
    fun resetChrome() {
        chromePull = { 0f }
        chromeHolding = false
        chromeHidden = false
    }

    /** Who last published each id, so a withdrawal can be checked against it. */
    private val owners = mutableStateMapOf<FloatingId, Any>()

    /** Publish (or with null, withdraw) this element's live bounds under [id]. */
    fun report(id: FloatingId, rect: Rect?, owner: Any) {
        if (rect == null) {
            if (owners[id] === owner) {
                bounds.remove(id)
                owners.remove(id)
            }
        } else {
            bounds[id] = rect
            owners[id] = owner
        }
    }

    fun boundsOf(id: FloatingId): Rect? = bounds[id]

    /**
     * Where the search element is docked (null when off screen); CENTER means "above it",
     * LEFT/RIGHT "beside it".
     */
    internal var searchDock by mutableStateOf<SearchDock?>(null)

    /**
     * Does anything else registered overlap [rect]? [marginPx] pads the other element so near
     * misses count.
     */
    fun collidesWithOthers(
        self: FloatingId,
        rect: Rect?,
        marginPx: Float,
        /**
         * Ids worth yielding to; null means every other floater. Name them so a new floater can't
         * hide an existing one (e.g. a user-parked search bubble).
         */
        avoid: Set<FloatingId>? = null,
    ): Boolean {
        if (rect == null) return false
        bounds.forEach { (id, other) ->
            if (id == self) return@forEach
            if (avoid != null && id !in avoid) return@forEach
            if (overlaps(rect, other, marginPx)) return true
        }
        return false
    }

    internal companion object {
        /**
         * Delegates to uicommon's [com.bloo.uicommon.floatersOverlap], where its JVM tests live.
         */
        fun overlaps(a: Rect, b: Rect, marginPx: Float): Boolean =
            com.bloo.uicommon.floatersOverlap(a, b, marginPx)
    }
}

/**
 * Everything a floating element needs in one modifier: publishes its bounds, rides the
 * pull-to-refresh shift, and fades during a refresh. [fade] is off for chrome about the refresh
 * itself and for persistent navigation; [shift] is off for anything anchored to the screen rather
 * than the page (the Settings cog).
 */
fun Modifier.floatingOverlay(
    id: FloatingId,
    active: Boolean = true,
    fade: Boolean = true,
    shift: Boolean = true,
): Modifier = composed {
    val registry = LocalFloatingRegistry.current
    // The hold is animated; the drag is not, so a pull tracks the finger. Both are read in the
    // offset lambda.
    val holdState = animateFloatAsState(
        targetValue = if (registry.chromeHolding) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "floatingShiftHold",
    )
    val alphaState = animateFloatAsState(
        targetValue = if (fade && registry.chromeHidden) 0f else 1f,
        animationSpec = tween(MotionShort),
        label = "floatingFade",
    )
    this
        // Layout and draw phase respectively, so neither recomposes per frame.
        .offset {
            if (!shift) return@offset IntOffset.Zero
            // Whichever is further along: the finger, or the settled refresh hold.
            val f = maxOf(registry.chromePull().coerceIn(0f, 1f), holdState.value)
            IntOffset(0, (RefreshPullShift.toPx() * f).roundToInt())
        }
        .graphicsLayer { this.alpha = alphaState.value }
        .floatingElement(id, active)
}

/** Provided once in `BlooApp` (Screens.kt); the default lets previews work without a host. */
val LocalFloatingRegistry = staticCompositionLocalOf { FloatingRegistry() }

/**
 * Publishes this element's live bounds under [id] so other floaters can avoid it. [active] false
 * withdraws them (present but not currently floating).
 */
fun Modifier.floatingElement(id: FloatingId, active: Boolean = true): Modifier = composed {
    val registry = LocalFloatingRegistry.current
    // Identifies this instance, since several can share one id (see FloatingRegistry.report).
    val owner = remember { Any() }
    DisposableEffect(registry, id, owner) {
        onDispose { registry.report(id, null, owner) }
    }
    // Withdraw immediately; onGloballyPositioned won't fire again if nothing moved.
    DisposableEffect(registry, id, active, owner) {
        if (!active) registry.report(id, null, owner)
        onDispose { }
    }
    onGloballyPositioned { if (active) registry.report(id, it.boundsInRoot(), owner) }
}

/**
 * Fades this element out while another registered floater overlaps it, and back in when clear.
 * Draw-phase only; the animation is keyed on the collision boolean, not the bounds, which would
 * restart it every frame.
 */
fun Modifier.dodgeFloating(
    self: FloatingId,
    /** Which floaters to yield to; null means all. See [FloatingRegistry.collidesWithOthers]. */
    avoid: Set<FloatingId>? = null,
    margin: Dp = 8.dp,
    dampingRatio: Float = 0.6f,
    stiffness: Float = Spring.StiffnessMedium,
): Modifier = composed {
    val registry = LocalFloatingRegistry.current
    val marginPx = with(LocalDensity.current) { margin.toPx() }
    val alpha = remember { Animatable(1f) }
    val colliding by remember(registry, self, marginPx, avoid) {
        derivedStateOf { registry.collidesWithOthers(self, registry.boundsOf(self), marginPx, avoid) }
    }
    LaunchedEffect(colliding) {
        alpha.animateTo(
            targetValue = if (colliding) 0f else 1f,
            animationSpec = spring(dampingRatio = dampingRatio, stiffness = stiffness),
        )
    }
    graphicsLayer { this.alpha = alpha.value }
}

@Composable
internal fun searchBarClearance(fallback: Dp, extraMargin: Dp = 16.dp): Dp {
    val registry = LocalFloatingRegistry.current
    // Snapshot-backed read; recomposes when the search bar's bounds change.
    val searchTop = registry.boundsOf(FloatingIds.Search)?.top ?: return fallback
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current
    return with(density) { (windowHeightPx - searchTop).coerceAtLeast(0f).toDp() } + extraMargin
}
