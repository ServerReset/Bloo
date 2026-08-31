@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.uicommon

/**
 * Reorderable column + shared reorder state, pure Foundation.
 *
 * Consumers (phone, watch, widget surfaces) supply colours/sizes/content
 * via parameters; this module carries no Material dependency, so the same
 * component works under compose.material3 and wear.compose.material3.
 */
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.round
import androidx.compose.foundation.layout.offset

/** Which [ReorderColumn.introKey]s have already played their cold-start
 *  intro (see `staggerInOnColdStart`), so it plays once per key per process
 *  -- keyed per-vehicle (not a single global flag) so a prefetched/off-screen
 *  neighbour in the expanded car pager can't "use up" the intro before the
 *  page the user actually sees composes. */
val coldStartIntroPlayed = mutableSetOf<Any>()

/** True while ANY [ReorderColumn] item is being dragged (the "floating
 *  pebble" state). The page switchers (dots) read this and hide themselves
 *  for the drag -- a floating card under the finger plus an animated dots
 *  rail is the exact clutter the dots-tracking code warns about. */
val LocalReorderActive = staticCompositionLocalOf { false }

@Composable
fun <T> ReorderColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    // Optional cross-target drag hooks: [onDragMove] reports the live finger
    // position (window coords) of the dragged item; [onDragRelease] is called on
    // drop and, if it returns true, the drop was handled elsewhere (e.g. pinned
    // to the hot spot) so the normal reorder is skipped.
    onDragMove: ((key: Any, windowPointer: Offset) -> Unit)? = null,
    onDragRelease: ((key: Any) -> Boolean)? = null,
    // When true, each item fades/slides in top-to-bottom in quick lockstep the
    // first time this column appears after a fresh process start (see
    // [coldStartIntroPlayed]) -- e.g. the garage's pebble list, so opening the
    // app feels alive instead of the whole screen just popping in at once.
    staggerInOnColdStart: Boolean = false,
    // Identity for the "already played" check above -- distinct per logical
    // column (e.g. each car's VIN), so one column consuming the intro can't
    // rob another (possibly still off-screen/prefetched) column of its own.
    introKey: Any = Unit,
    content: @Composable (item: T, dragHandle: Modifier, isDragging: Boolean) -> Unit,
) {
    // The four callback parameters, behind rememberUpdatedState so the per-item drag
    // Modifier below can be remembered without capturing a stale one. See `handle`.
    val keyOfNow by rememberUpdatedState(keyOf)
    val onReorderNow by rememberUpdatedState(onReorder)
    val onDragMoveNow by rememberUpdatedState(onDragMove)
    val onDragReleaseNow by rememberUpdatedState(onDragRelease)
    var order by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    val reorderActive = draggingKey != null
    CompositionLocalProvider(LocalReorderActive provides reorderActive) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    // Consumed the instant this key is first read, so navigating back to the
    // garage (or a second car's column composing) later never replays it.
    val playIntro = remember(introKey) {
        staggerInOnColdStart && coldStartIntroPlayed.add(introKey)
    }

    // Sync with upstream changes only while not actively dragging.
    LaunchedEffect(items) { if (draggingKey == null) order = items }
    // The "drop ripple" animation that used to live here is gone. It was dead twice
    // over: `dropRipple` was declared and never assigned, so the effect's `!= 0L`
    // guard could not become true; and even if it had, nothing ever read
    // maxRippleScale, so no ripple would have been drawn. An Animatable and a
    // LaunchedEffect that could only ever do nothing, described by a comment
    // ("shows the 'weight' of the move") for an effect no user has seen.

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        order.forEachIndexed { index, item ->
            val k = keyOf(item)
            // Identity key so Compose moves the existing node when the order
            // changes (instead of reusing nodes by slot, which looks janky).
            key(k) {
                val dragging = draggingKey == k
                val lift by animateFloatAsState(
                    targetValue = if (dragging) 1.08f else 1f,
                    animationSpec = if (dragging) spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
                                   else spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow),
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
                        // Non-dragged items glide to their new slot; the dragged
                        // one is positioned manually via graphicsLayer below.
                        .then(if (dragging) Modifier else Modifier.animatePlacement())
                        .graphicsLayer {
                            translationY = if (dragging) offsetY else (1f - intro.value) * 28.dp.toPx()
                            scaleX = lift
                            scaleY = lift
                            alpha = intro.value
                        }
                        .onSizeChanged { heights[k] = it.height },
                ) {
                    val handleCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
                    // REMEMBERED, so this is ONE instance for the item's lifetime.
                    //
                    // Every pebble takes this as a `dragHandle: Modifier`. Built inline, the
                    // chain below is rebuilt on every recomposition, and a child can only skip
                    // if its arguments compare equal -- so a fresh chain means a changed
                    // argument. `Modifier` is a @Stable type, so it is compared with equals(),
                    // and each element's equals() compares its lambda by reference.
                    //
                    // ⚠ HONEST CAVEAT, because I first wrote this comment claiming more than
                    // it can. The reasoning I used -- "one unstable parameter makes the whole
                    // composable non-skippable" -- is PRE-strong-skipping framing and is
                    // outdated on this toolchain. Strong skipping has been the default since
                    // Kotlin 2.0.20 and this project is on 2.2.20: an unstable parameter no
                    // longer blocks skipping, it is just compared by reference instead of
                    // equals(). Worse for my claim, Kotlin 2.0.20+ also auto-remembers lambdas
                    // declared inside a composable, keyed on their captures -- so the three
                    // lambdas below may well have been memoized already, making this remember
                    // belt-and-braces rather than the unlock the commit said it was.
                    //
                    // Kept anyway: one remembered instance is strictly stronger than relying
                    // on per-lambda auto-remember plus every element's equals(), and it costs
                    // nothing. But do NOT treat this as the reason pebbles now skip. The
                    // measured lever is passing narrower parameters than the whole UiState.
                    //
                    // Safe to remember despite the captures: `order`, `offsetY`,
                    // `draggingKey` and `heights` are all delegated/remembered snapshot
                    // state, so the captured object is stable and the lambdas read and write
                    // the LIVE value when they run. The four caller-supplied callbacks are
                    // the ones that genuinely change identity per recomposition, and they go
                    // through rememberUpdatedState above rather than being captured directly.
                    val handle = remember(k) {
                        Modifier
                        .onGloballyPositioned { handleCoords.value = it }
                        // The drag gesture below has no TalkBack equivalent at
                        // all -- reordering pebbles/presets/cars was completely
                        // unreachable for screen-reader users. Additive
                        // semantics-only "Move up"/"Move down" actions alongside
                        // the existing gesture (same pattern already used for
                        // MorphSegmented's drag track), reusing the same reorder
                        // + commit logic the drag path uses.
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
                        onDragStart = { draggingKey = k; offsetY = 0f },
                        onDragEnd = {
                            val handled = onDragReleaseNow?.invoke(k) ?: false
                            draggingKey = null; offsetY = 0f
                            if (!handled) onReorderNow(order)
                        },
                        onDragCancel = { onDragReleaseNow?.invoke(k); draggingKey = null; offsetY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetY += dragAmount.y
                            handleCoords.value?.takeIf { it.isAttached }?.let {
                                onDragMoveNow?.invoke(k, it.localToWindow(change.position))
                            }
                            val cur = order.indexOfFirst { keyOfNow(it) == k }
                            if (cur >= 0) {
                                if (offsetY > 0 && cur < order.lastIndex) {
                                    val nextH = heights[keyOfNow(order[cur + 1])] ?: 0
                                    if (nextH > 0 && offsetY > nextH / 2f) {
                                        order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                        offsetY -= nextH
                                    }
                                } else if (offsetY < 0 && cur > 0) {
                                    val prevH = heights[keyOfNow(order[cur - 1])] ?: 0
                                    if (prevH > 0 && -offsetY > prevH / 2f) {
                                        order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                        offsetY += prevH
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
    }
}
}

/**
 * Animates an item gliding to its new placement when siblings reorder around it,
 * instead of snapping. Used for the non-dragged pebbles so they slide out of the
 * way smoothly. The dragged item is offset manually and must not use this.
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
