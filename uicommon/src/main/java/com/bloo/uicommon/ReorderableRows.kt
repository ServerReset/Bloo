package com.bloo.uicommon

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

/**
 * The app's one standard reorderable list of rows. Every list the user can re-rank (climate presets, pebbles, the watch's presets) goes
 * through here, so the drag, the drop, the neighbours settling and the spacing are the same everywhere.
 *
 * Each row gets a [dragHandle] to put on the part the user grabs, and [isDragging] to know when it is in the air. Rows keep their height
 * while they are dragged: do not animate a row's own size inside [content], or the drop reads differently from the rest.
 */
@Composable
fun <T> ReorderableRows(
    items: List<T>,
    keyOf: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = GapRow,
    /** Whether a row may be carried sideways as well as up and down (the full-screen pebble view does). */
    allowHorizontalDrag: Boolean = false,
    onDragMove: ((key: Any, windowPointer: Offset) -> Unit)? = null,
    onDragRelease: ((key: Any) -> Boolean)? = null,
    dragReleaseTarget: ((key: Any) -> Offset?)? = null,
    onDragReleaseCommit: ((key: Any) -> Unit)? = null,
    onDragCancel: ((key: Any) -> Unit)? = null,
    onPickUp: (() -> Unit)? = null,
    onSwap: (() -> Unit)? = null,
    onDragProximity: ((Float) -> Unit)? = null,
    onLanded: (() -> Unit)? = null,
    staggerInOnColdStart: Boolean = false,
    introKey: Any = Unit,
    content: @Composable (item: T, dragHandle: Modifier, isDragging: Boolean) -> Unit,
) {
    ReorderColumn(
        items = items,
        keyOf = keyOf,
        onReorder = onReorder,
        spacing = spacing,
        modifier = modifier.fillMaxWidth(),
        allowHorizontalDrag = allowHorizontalDrag,
        onDragMove = onDragMove,
        onDragRelease = onDragRelease,
        dragReleaseTarget = dragReleaseTarget,
        onDragReleaseCommit = onDragReleaseCommit,
        onDragCancel = onDragCancel,
        onPickUp = onPickUp,
        onSwap = onSwap,
        onDragProximity = onDragProximity,
        onLanded = onLanded,
        staggerInOnColdStart = staggerInOnColdStart,
        introKey = introKey,
        content = content,
    )
}
