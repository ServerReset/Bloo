package com.bloo.uicommon

import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * The gesture the app's sideways controls share: the slider and the segmented control. Once the finger is down, a press that lifts without
 * moving sideways is a tap, and [onTap] runs with the press it began on. A press that moves sideways past [slop], and further sideways than
 * down, claims the gesture: [onDragStart] runs once with that move, [onDrag] runs for each move after it, and [onDragEnd] runs when the
 * finger lifts. A press that moves down first is left to scrolling, and nothing runs.
 */
internal suspend fun AwaitPointerEventScope.awaitTapOrHorizontalDrag(
    down: PointerInputChange,
    slop: Float,
    onTap: (PointerInputChange) -> Unit,
    onDragStart: (PointerInputChange) -> Unit,
    onDrag: (PointerInputChange) -> Unit,
    onDragEnd: () -> Unit,
) {
    var claimed = false
    var lift: PointerInputChange? = null
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) {
            lift = change
            break
        }
        if (!claimed) {
            val dx = abs(change.position.x - down.position.x)
            val dy = abs(change.position.y - down.position.y)
            when {
                dx > slop && dx >= dy -> {
                    claimed = true
                    change.consume()
                    onDragStart(change)
                }
                dy > slop -> return
            }
        } else if (change.positionChanged()) {
            change.consume()
            onDrag(change)
        }
    }
    when {
        claimed -> onDragEnd()
        lift != null -> {
            lift.consume()
            onTap(down)
        }
    }
}
