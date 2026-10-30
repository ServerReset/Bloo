package com.bloo.uicommon

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    content: @Composable (item: T, dragHandle: Modifier, isDragging: Boolean) -> Unit,
) {
    ReorderColumn(
        items = items,
        keyOf = keyOf,
        onReorder = onReorder,
        spacing = spacing,
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}
