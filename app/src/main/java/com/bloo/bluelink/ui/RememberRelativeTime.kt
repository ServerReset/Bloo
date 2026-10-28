package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.bloo.uicommon.content

/** Shared state for dragging a pebble onto (or off) the dual-column hot spot. */
internal class HotSeatDrag {
    var section by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var slotTopLeft by mutableStateOf(Offset.Zero)
    var slotSize by mutableStateOf(IntSize.Zero)
    /**
     * The window y of the bottom of the page. The pin target is not just the empty slot but the whole left column from the
     * slot DOWN to here (under the hero and the controls), so a pebble can be dropped anywhere there rather than aimed at a
     * strip the right-hand column makes small.
     */
    var zoneBottom by mutableFloatStateOf(0f)
    /**
     * True while the pinned pebble is being dragged, or flying home: the car's first column is then drawn above the stack, so
     * the pebble stays in front of it on its way out instead of sliding behind.
     */
    var pinnedActive by mutableStateOf(false)
    /** The key released over the slot, awaiting its fly-in before the pin commits. */
    var pendingPin by mutableStateOf<String?>(null)
    /**
     * Window top-left of each section's slot in the reorderable STACK, keyed by section. Registered
     * by every row (even a pinned one, collapsed to zero height), so an unpinned hot-seat pebble has
     * a live target to fly back to -- the mirror of [slotTopLeft] for the pin direction.
     */
    val stackSlot = mutableStateMapOf<String, Offset>()
    /**
     * The pinned section flying home. Its stack row opens (invisibly) for the whole flight, so the
     * list has already made room and the landing is a plain swap: no reflow, no pop.
     */
    var returning by mutableStateOf<String?>(null)
    val overSlot: Boolean
        get() = section != null && slotSize.width > 0 &&
            pointer.x in slotTopLeft.x..(slotTopLeft.x + slotSize.width) &&
            pointer.y in slotTopLeft.y..maxOf(slotTopLeft.y + slotSize.height, zoneBottom)
}

internal val LocalHotSeatDrag = staticCompositionLocalOf<HotSeatDrag?> { null }

/**
 * Trivial full-size [Box] wrapper; exists as a distinct composable purely so the hot-seat drag
 * machinery has a single, stable, named host to reason about/hang [LocalHotSeatDrag] state around
 * rather than an anonymous Box.
 */
@Composable
internal fun BackdropHost(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}
