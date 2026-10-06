package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    // Snapshot-backed so a dodger recomposes/redraws when a neighbour moves. Writes come from
    // onGloballyPositioned (layout phase is finished by then, so this is a safe place to write).
    private val bounds = mutableStateMapOf<FloatingId, Rect>()

    /** Who last published each id, so a withdrawal can be checked against it. */
    private val owners = mutableStateMapOf<FloatingId, Any>()

    /**
     * Publish (or with null, withdraw) this element's live bounds under [id]. [owner] identifies
     * the publishing instance, and a withdrawal only takes effect if that instance is the one
     * currently holding the id.
     */
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
}

/** Provided once in `BlooApp` (Screens.kt); the default lets previews work without a host. */
val LocalFloatingRegistry = staticCompositionLocalOf { FloatingRegistry() }

/**
 * Publishes this element's live bounds to the registry under [id], so other floaters can avoid it.
 * [active] false withdraws them (an element that is present but not currently floating -- a title
 * still inline in the page, say -- should not push anything around).
 */
fun Modifier.floatingElement(id: FloatingId, active: Boolean = true): Modifier = composed {
    val registry = LocalFloatingRegistry.current
    // Identifies this instance, since several can share one id (see FloatingRegistry.report).
    val owner = remember { Any() }
    DisposableEffect(registry, id, owner) {
        onDispose { registry.report(id, null, owner) }
    }
    // Withdraw immediately on going inactive rather than waiting for a layout pass that may never
    // come (nothing moved, so onGloballyPositioned would not fire again).
    DisposableEffect(registry, id, active, owner) {
        if (!active) registry.report(id, null, owner)
        onDispose { }
    }
    onGloballyPositioned { if (active) registry.report(id, it.boundsInRoot(), owner) }
}

@Composable
internal fun searchBarClearance(fallback: Dp): Dp {
    val registry = LocalFloatingRegistry.current
    // Snapshot-backed read; recomposes when the search bar's bounds change.
    val searchTop = registry.boundsOf(FloatingIds.Search)?.top ?: return fallback
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current
    return with(density) { (windowHeightPx - searchTop).coerceAtLeast(0f).toDp() } + 16.dp
}
