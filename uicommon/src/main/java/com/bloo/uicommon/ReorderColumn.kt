package com.bloo.uicommon

/** Reorderable column + shared reorder state, pure Foundation. */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Which [ReorderColumn.introKey]s have already played their cold-start intro (see
 * `staggerInOnColdStart`), so it plays once per key per process -- keyed per-vehicle (not a single
 * global flag) so a prefetched/off-screen neighbour in the expanded car pager can't "use up" the
 * intro before the page the user actually sees composes.
 */
val coldStartIntroPlayed = mutableSetOf<Any>()

/** True while ANY [ReorderColumn] item is being dragged (the "floating pebble" state). */
val LocalReorderActive = staticCompositionLocalOf { false }

/** The spring a released pebble flies into its drop target on (see [ReorderColumn]'s onDragEnd). */
private val DragFlightSpec = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

@Composable
fun <T> ReorderColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    /**
     * Whether the dragged item may follow the finger sideways as well as up/down. False in the
     * single-column/grid views, where a pebble only reorders vertically (a sideways drift there read
     * as the whole column lurching); true in the full-screen view, where a pebble is carried across
     * to the hot seat.
     */
    allowHorizontalDrag: Boolean = false,
    onDragMove: ((key: Any, windowPointer: Offset) -> Unit)? = null,
    onDragRelease: ((key: Any) -> Boolean)? = null,
    /** Window-coords target to fly the released item to when [onDragRelease] handled it. */
    dragReleaseTarget: ((key: Any) -> Offset?)? = null,
    /** Called once the released item has flown to its target (or at once when there is none). */
    onDragReleaseCommit: ((key: Any) -> Unit)? = null,
    /** Drag cancelled (not a drop): the caller should clear any in-flight drop-hover state. */
    onDragCancel: ((key: Any) -> Unit)? = null,
    staggerInOnColdStart: Boolean = false,
    // Identity for the "already played" check above -- distinct per logical column (e.g. each car's
    // VIN), so one column consuming the intro can't rob another (possibly still
    // off-screen/prefetched) column of its own.
    introKey: Any = Unit,
    content: @Composable (item: T, dragHandle: Modifier, isDragging: Boolean) -> Unit,
) {
    // The four callback parameters, behind rememberUpdatedState so the per-item drag Modifier below
    // can be remembered without capturing a stale one. See `handle`.
    val keyOfNow by rememberUpdatedState(keyOf)
    val onReorderNow by rememberUpdatedState(onReorder)
    val onDragMoveNow by rememberUpdatedState(onDragMove)
    val onDragReleaseNow by rememberUpdatedState(onDragRelease)
    val dragReleaseTargetNow by rememberUpdatedState(dragReleaseTarget)
    val onDragReleaseCommitNow by rememberUpdatedState(onDragReleaseCommit)
    val onDragCancelNow by rememberUpdatedState(onDragCancel)
    val allowHorizontalNow by rememberUpdatedState(allowHorizontalDrag)
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    val reorderActive = draggingKey != null
    CompositionLocalProvider(LocalReorderActive provides reorderActive) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    // Consumed the instant this key is first read, so navigating back to the garage (or a second
    // car's column composing) later never replays it.
    val playIntro = remember(introKey) {
        staggerInOnColdStart && coldStartIntroPlayed.add(introKey)
    }
    // Set while a released item is FLYING to a drop target: its pick-up lift eases back to 1.0 during
    // the flight, so the element that lands matches the one the caller then draws in place (no pop).
    var flyingKey by remember { mutableStateOf<Any?>(null) }

    // Sync with upstream changes only while not actively dragging.
    LaunchedEffect(items) { if (draggingKey == null) order = items }
    // An Animatable and a LaunchedEffect that could only ever do nothing, described by a comment
    // ("shows the 'weight' of the move") for an effect no user has seen.

    Layout(
        modifier = modifier,
        content = {
        order.forEachIndexed { index, item ->
            val k = keyOf(item)
            // Identity key so Compose moves the existing node when the order changes (instead of
            // reusing nodes by slot, which looks janky).
            key(k) {
                val dragging = draggingKey == k
                val lifted = dragging && flyingKey != k
                val flying = dragging && flyingKey == k
                val lift by animateFloatAsState(
                    targetValue = if (lifted) 1.08f else 1f,
                    animationSpec = when {
                        // Picking up / putting back down: a small springy pop.
                        lifted -> spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
                        // FLYING to the drop target: ease the lift straight down to 1.0 with NO
                        // overshoot, so the pebble the caller then draws in place lines up exactly.
                        flying -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
                        else -> spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow)
                    },
                    label = "lift"
                )
                // Quick top-to-bottom lockstep reveal, once, on a fresh launch.
                val intro = remember { Animatable(if (playIntro) 0f else 1f) }
                LaunchedEffect(Unit) {
                    if (playIntro) {
                        delay(index * 45L)
                        intro.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                    }
                }
                Box(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        // Non-dragged items glide to their new slot; the dragged one is positioned
                        // manually via graphicsLayer below.
                        .then(if (dragging) Modifier else Modifier.animatePlacement())
                        .graphicsLayer {
                            // The dragged pebble follows the finger on BOTH axes, so it can be
                            // carried sideways onto the hot-seat column (the vertical part still
                            // drives the reorder). Non-dragged items keep their intro offset only.
                            translationX = if (dragging) offsetX else 0f
                            translationY = if (dragging) offsetY else (1f - intro.value) * 28.dp.toPx()
                            scaleX = lift
                            scaleY = lift
                            // Scale about the TOP-LEFT, not the centre: the release flight aims the
                            // item's top-left at the drop target, and a centre pivot would move that
                            // corner as the lift eases back during the flight (landing a few dp off).
                            transformOrigin = TransformOrigin(0f, 0f)
                            alpha = intro.value
                        }
                        .onSizeChanged { heights[k] = it.height },
                ) {
                    val handleCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
                    // REMEMBERED, so this is ONE instance for the item's lifetime. Every pebble
                    // takes this as a `dragHandle: Modifier`. Built inline, the chain below is
                    // rebuilt on every recomposition, and a child can only skip if its arguments
                    // compare equal -- so a fresh chain means a changed argument.
                    val handle = remember(k) {
                        Modifier
                        .onGloballyPositioned { handleCoords.value = it }
                        // Additive semantics-only "Move up"/"Move down" actions alongside the
                        // existing gesture (same pattern already used for MorphSegmented's drag
                        // track), reusing the same reorder + commit logic the drag path uses.
                        .semantics {
                            val cur = order.indexOfFirst { keyOfNow(it) == k }
                            customActions = listOfNotNull(
                                if (cur > 0) CustomAccessibilityAction("Move up") {
                                    order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                    onReorderNow(order)
                                    true
                                } else null,
                                if (cur in 0 until order.lastIndex) CustomAccessibilityAction("Move down") {
                                    order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                    onReorderNow(order)
                                    true
                                } else null,
                            )
                        }
                        .pointerInput(k) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingKey = k; offsetY = 0f; offsetX = 0f },
                        onDragEnd = {
                            val handled = onDragReleaseNow?.invoke(k) ?: false
                            val target = if (handled) dragReleaseTargetNow?.invoke(k) else null
                            val itemWin = handleCoords.value?.takeIf { it.isAttached }?.localToWindow(Offset.Zero)
                            if (handled && target != null && itemWin != null) {
                                // Fly the SAME element to the drop target, then commit: the caller's
                                // posted pin then appears exactly where this one landed, so the drop
                                // reads as one element moving rather than a pop.
                                // itemWin is the item's CURRENT drawn top-left, which already includes
                                // the drag translation, so the flight must END at start + (target -
                                // itemWin) -- NOT at (target - itemWin), which would be short by the
                                // whole drag offset and strand the pebble near the finger. The item
                                // scales from its TOP-LEFT (see its graphicsLayer), so the lift easing
                                // back during the flight cannot shift that corner either.
                                val endX = offsetX + (target.x - itemWin.x)
                                val endY = offsetY + (target.y - itemWin.y)
                                flyingKey = k
                                scope.launch {
                                    val ax = Animatable(offsetX)
                                    val ay = Animatable(offsetY)
                                    // Await BOTH axes before committing: they run together, but a
                                    // shorter axis would otherwise settle early and let the pin land
                                    // a frame before the flight has actually finished.
                                    coroutineScope {
                                        launch { ax.animateTo(endX, DragFlightSpec) { offsetX = value } }
                                        launch { ay.animateTo(endY, DragFlightSpec) { offsetY = value } }
                                    }
                                    onDragReleaseCommitNow?.invoke(k)
                                    draggingKey = null
                                    flyingKey = null
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            } else {
                                draggingKey = null; offsetY = 0f; offsetX = 0f
                                if (handled) onDragReleaseCommitNow?.invoke(k) else onReorderNow(order)
                            }
                        },
                        onDragCancel = {
                            onDragCancelNow?.invoke(k)
                            draggingKey = null; offsetY = 0f; offsetX = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetY += dragAmount.y
                            if (allowHorizontalNow) offsetX += dragAmount.x
                            handleCoords.value?.takeIf { it.isAttached }?.let {
                                onDragMoveNow?.invoke(k, it.localToWindow(change.position))
                            }
                            val cur = order.indexOfFirst { keyOfNow(it) == k }
                            if (cur >= 0) {
                                // Skip over any zero-height (collapsed) entries: they add no height, so
                                // the dragged item moves past them for free and never gets blocked.
                                if (offsetY > 0 && cur < order.lastIndex) {
                                    var n = cur + 1
                                    var acc = 0
                                    while (n <= order.lastIndex) {
                                        val h = heights[keyOfNow(order[n])] ?: 0
                                        acc += h
                                        if (h > 0) break
                                        n++
                                    }
                                    if (n <= order.lastIndex && acc > 0 && offsetY > acc / 2f) {
                                        order = order.toMutableList().also { it.add(n, it.removeAt(cur)) }
                                        offsetY -= acc
                                    }
                                } else if (offsetY < 0 && cur > 0) {
                                    var n = cur - 1
                                    var acc = 0
                                    while (n >= 0) {
                                        val h = heights[keyOfNow(order[n])] ?: 0
                                        acc += h
                                        if (h > 0) break
                                        n--
                                    }
                                    if (n >= 0 && acc > 0 && -offsetY > acc / 2f) {
                                        order = order.toMutableList().also { it.add(n, it.removeAt(cur)) }
                                        offsetY += acc
                                    }
                                }
                            }
                        },
                    )
                    }
                    }
                    content(item, handle, dragging)
                }
            }
        }
        },
    ) { measurables, constraints ->
        // Stack the items, adding the gap only BETWEEN items that actually have height -- so an item
        // collapsed to zero (a pinned pebble hidden in the stack, or one animating out) contributes
        // no gap and effectively vanishes.
        val gap = spacing.roundToPx()
        val childConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(childConstraints) }
        val pos = IntArray(placeables.size)
        var total = 0
        placeables.forEachIndexed { i, p ->
            if (total > 0 && p.height > 0) total += gap
            pos[i] = total
            total += p.height
        }
        // The dragged item is placed last so it draws on top of its neighbours.
        val dragIdx = order.indexOfFirst { keyOfNow(it) == draggingKey }
        layout(constraints.maxWidth, total) {
            placeables.forEachIndexed { i, p -> if (i != dragIdx) p.place(0, pos[i]) }
            if (dragIdx in placeables.indices) placeables[dragIdx].place(0, pos[dragIdx])
        }
    }
}
}

/**
 * Animates an item gliding to its new placement when siblings reorder around it, instead of
 * snapping. Used for the non-dragged pebbles so they slide out of the way smoothly. The dragged
 * item is offset manually and must not use this.
 */
fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(IntOffset.Zero) }
    var anim by remember { mutableStateOf<Animatable<IntOffset, *>?>(null) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val a = anim ?: Animatable(target, IntOffset.VectorConverter).also { anim = it }
            if (a.targetValue != target) {
                scope.launch { a.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
            }
            a.value - target
        }
}
