package com.bloo.uicommon

/** Reorderable column + shared reorder state, pure Foundation. */

import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch

/**
 * Which [ReorderColumn.introKey]s have already played their cold-start intro (see
 * `staggerInOnColdStart`), so it plays once per key per process -- keyed per-vehicle (not a single
 * global flag) so a prefetched/off-screen neighbour in the expanded car pager can't "use up" the
 * intro before the page the user actually sees composes.
 */
val coldStartIntroPlayed = mutableSetOf<Any>()

/** The spring a released pebble flies into its drop target on (see [ReorderColumn]'s onDragEnd). */
/** A put-down that is not a pin: a short, slightly springy settle into the slot it belongs in. */
private val DragSettleSpec = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

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
    /** Fired the moment the long-press lifts an item (for a pick-up haptic). */
    onPickUp: (() -> Unit)? = null,
    /** Fired each time the dragged item swaps places with a neighbour. */
    onSwap: (() -> Unit)? = null,
    /**
     * Hot/cold drag guidance: a throttled 0..1 proximity to the nearest drop slot (1 = the item is
     * centred on a slot, so a release there is "optimal"; 0 = straddling the midpoint between two).
     * The caller turns it into a ramping haptic, so the hand is guided toward a clean drop.
     */
    onDragProximity: ((Float) -> Unit)? = null,
    /** Fired when a put-down finishes settling into its slot. */
    onLanded: (() -> Unit)? = null,
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
    val onPickUpNow by rememberUpdatedState(onPickUp)
    val onSwapNow by rememberUpdatedState(onSwap)
    val onDragProximityNow by rememberUpdatedState(onDragProximity)
    val onLandedNow by rememberUpdatedState(onLanded)
    val allowHorizontalNow by rememberUpdatedState(allowHorizontalDrag)
    val scope = rememberCoroutineScope()
    // The put-down settle in flight, so a new pick-up can take over from it cleanly.
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var order by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    // Throttle for the drag's hot/cold proximity haptic (see onDragProximity).
    var dragHapticAt by remember { mutableLongStateOf(0L) }
    // Consumed the instant this key is first read, so navigating back to the garage (or a second
    // car's column composing) later never replays it.
    val playIntro = remember(introKey) {
        staggerInOnColdStart && coldStartIntroPlayed.add(introKey)
    }
    // Set while a released item is FLYING to a drop target: its pick-up lift eases back to 1.0 during
    // the flight, so the element that lands matches the one the caller then draws in place (no pop).
    var flyingKey by remember { mutableStateOf<Any?>(null) }
    // Set while a released item is SETTLING back into its own slot after an ordinary reorder drop (no
    // pin). Kept separate from [flyingKey] so the put-down can land with a small bounce instead of the
    // pin flight's flat, no-overshoot ease.
    var settlingKey by remember { mutableStateOf<Any?>(null) }

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
                val flying = dragging && flyingKey == k
                val settling = dragging && settlingKey == k
                val lifted = dragging && !flying && !settling
                val lift by animateFloatAsState(
                    targetValue = if (lifted) 1.08f else 1f,
                    animationSpec = when {
                        // Picking up / putting back down: a small springy pop.
                        lifted -> spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
                        // FLYING to the drop target: ease the lift straight down to 1.0 with NO
                        // overshoot, so the pebble the caller then draws in place lines up exactly.
                        flying -> spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
                        // SETTLING into its own slot after an ordinary reorder drop: let the lift ease
                        // back with a touch of overshoot so the put-down reads as a landing bounce
                        // rather than a flat snap.
                        settling -> spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
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
                        .then(if (dragging) Modifier else Modifier.animatePlacement { order })
                        .graphicsLayer {
                            // The dragged pebble follows the finger on BOTH axes, so it can be
                            // carried sideways onto the hot-seat column (the vertical part still
                            // drives the reorder). Non-dragged items keep their intro offset only.
                            translationX = if (dragging) offsetX else 0f
                            translationY = if (dragging) offsetY else (1f - intro.value) * 28.dp.toPx()
                            scaleX = lift
                            scaleY = lift
                            // Scale about the CENTRE so the pick-up lift grows symmetrically. A
                            // top-left pivot grew the card's right and bottom edges while the left and
                            // top stayed pinned, which read as the pebble "popping out to the right"
                            // -- far more noticeable than the vertical growth, because a pebble is
                            // wide and short. This does not cost the release flight its aiming: by the
                            // time the flight lands, the lift has eased back to 1.0, where the pivot no
                            // longer displaces anything (the transient during the flight is a fraction
                            // of the lift amount, and reads as the pebble settling into the slot).
                            transformOrigin = TransformOrigin.Center
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
                    // A swap moves the item past a neighbour AND the gap between them, so the drag offset
                    // is corrected by both (by the neighbour alone it jumped one gap per swap).
                    val gapPx = spacing.toPx()
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            // Picking up an item that is still settling takes over from where it is.
                            val carryX = if (settleJob?.isActive == true && draggingKey == k) offsetX else 0f
                            val carryY = if (settleJob?.isActive == true && draggingKey == k) offsetY else 0f
                            settleJob?.cancel()
                            flyingKey = null
                            settlingKey = null
                            draggingKey = k; offsetY = carryY; offsetX = carryX
                            onPickUpNow?.invoke()
                        },
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
                                // whole drag offset and strand the pebble near the finger. The item now
                                // scales about its centre, but by the time the flight lands the lift has
                                // eased back to 1.0, where the pivot is a no-op and the corner is exact.
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
                                    // The commit reaches the list a frame or two later; resetting now would
                                    // snap the item back to its old slot until the pin collapses it.
                                    withFrameNanos { }
                                    withFrameNanos { }
                                    draggingKey = null
                                    flyingKey = null
                                    settlingKey = null
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            } else {
                                // Not a pin: commit the order now, then let the item SETTLE into the slot
                                // it belongs in -- from wherever it was let go, sideways included, so a
                                // drop in the wrong place flies back instead of snapping. settlingKey
                                // drives the landing bounce (see the lift spec above).
                                if (handled) onDragReleaseCommitNow?.invoke(k) else onReorderNow(order)
                                settlingKey = k
                                settleJob = scope.launch {
                                    val ax = Animatable(offsetX)
                                    val ay = Animatable(offsetY)
                                    coroutineScope {
                                        launch { ax.animateTo(0f, DragSettleSpec) { offsetX = value } }
                                        launch { ay.animateTo(0f, DragSettleSpec) { offsetY = value } }
                                    }
                                    if (draggingKey == k) {
                                        draggingKey = null; flyingKey = null; settlingKey = null
                                        offsetX = 0f; offsetY = 0f
                                    }
                                    onLandedNow?.invoke()
                                }
                            }
                        },
                        onDragCancel = {
                            onDragCancelNow?.invoke(k)
                            settlingKey = k
                            settleJob = scope.launch {
                                val ax = Animatable(offsetX)
                                val ay = Animatable(offsetY)
                                coroutineScope {
                                    launch { ax.animateTo(0f, DragSettleSpec) { offsetX = value } }
                                    launch { ay.animateTo(0f, DragSettleSpec) { offsetY = value } }
                                }
                                if (draggingKey == k) {
                                    draggingKey = null; flyingKey = null; settlingKey = null
                                    offsetX = 0f; offsetY = 0f
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetY += dragAmount.y
                            if (allowHorizontalNow) offsetX += dragAmount.x
                            handleCoords.value?.takeIf { it.isAttached }?.let {
                                onDragMoveNow?.invoke(k, it.localToWindow(change.position))
                            }
                            // Hot/cold: pulse the guide haptic harder the closer the item is centred
                            // on its slot (a clean drop), lighter while it straddles the gap between
                            // two. Throttled, so it reads as a steady buzz that swells, not a rattle.
                            val slotH = heights[k] ?: 0
                            if (slotH > 0) {
                                val prox = 1f - (kotlin.math.abs(offsetY) / (slotH * 0.5f)).coerceIn(0f, 1f)
                                val nowMs = android.os.SystemClock.elapsedRealtime()
                                if (nowMs - dragHapticAt > 90L) {
                                    dragHapticAt = nowMs
                                    onDragProximityNow?.invoke(prox)
                                }
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
                                        onSwapNow?.invoke()
                                        offsetY -= acc + gapPx
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
                                        onSwapNow?.invoke()
                                        offsetY += acc + gapPx
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
            // The gap grows in with the row (full once it is a few gaps tall), so a row expanding from
            // zero eases its spacing in instead of popping a whole gap on its first frame.
            if (total > 0 && p.height > 0) total += (gap * minOf(1f, p.height / (gap * 4f))).toInt()
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

/**
 * Animates an item gliding to its new placement when siblings reorder around it, instead of
 * snapping. Used for the non-dragged pebbles so they slide out of the way smoothly. The dragged
 * item is offset manually and must not use this.
 */
fun Modifier.animatePlacement(
    /**
     * The list's order. Only a CHANGE of order glides an item to its new place; any other move (a pebble
     * above opening or closing, a placeholder filling in) follows the layout directly. Chasing those on a
     * spring launched an animation per row per frame and made rows trail and rubber-band behind a growing
     * card.
     */
    order: () -> Any? = { null },
): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(IntOffset.Zero) }
    var anim by remember { mutableStateOf<Animatable<IntOffset, *>?>(null) }
    // Plain holder, not state: read and written in placement, it must not invalidate anything.
    val seen = remember { arrayOfNulls<Any?>(1).also { it[0] = order() } }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val a = anim ?: Animatable(target, IntOffset.VectorConverter).also { anim = it }
            if (a.targetValue != target) {
                val now = order()
                if (now != seen[0] || a.isRunning) {
                    // A reorder (or a glide still under way): slide there.
                    seen[0] = now
                    scope.launch { a.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
                } else {
                    // Follow the layout this very frame; the animatable catches up in the background.
                    scope.launch { a.snapTo(target) }
                    return@offset IntOffset.Zero
                }
            }
            a.value - target
        }
}
