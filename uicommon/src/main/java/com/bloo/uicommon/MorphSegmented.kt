package com.bloo.uicommon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** One option in a [MorphSegmented] control. [icon] is optional (watch omits it). */
data class SegmentOption(val key: String, val label: String, val icon: ImageVector? = null)

/**
 * Bloo's full-width segmented selector, shared between phone (:app) and watch
 * (:wear) so the drag/tap gesture and sliding-highlight rendering live in exactly
 * one place. A tonal track whose active segment fills with [indicatorColor] and
 * morphs to a rounded square, the rest staying pill-calm; drag it and the
 * highlight bounces to wherever you let go, or tap a segment to jump there.
 *
 * Callers supply all colour/typography/haptic context as parameters so this
 * module stays neutral to compose.material3 vs wear.compose.material3.
 *
 * @param onTick Called each time the selection actually changes (for a haptic).
 * @param options The segments to render, in display order; each becomes one
 *   equal-width pill in the track.
 * @param selectedKey The currently committed selection (matched against
 *   [SegmentOption.key]); drives where the highlight rests when not being dragged.
 * @param onSelect Called with the newly chosen key on tap or on drag-release.
 * @param trackHeight Defaults taller (48.dp) when any option carries an icon so
 *   icon + label both fit comfortably; otherwise a slightly shorter text-only track.
 */
@Composable
fun MorphSegmented(
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    containerColor: Color,
    indicatorColor: Color,
    selectedTextColor: Color,
    unselectedTextColor: Color,
    textStyle: TextStyle,
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = if (options.any { it.icon != null }) 48.dp else 44.dp,
    /** Hairline rim colour, or null for the old borderless look. Every other
     *  interactive surface (MorphButton, cards) got a rim once real glass
     *  blur stopped giving flat surfaces a second depth cue; this control was
     *  the one left out. Null-default keeps any caller that hasn't been
     *  updated visually unchanged. */
    borderColor: Color? = null,
    /** When false, the highlight indicator is hidden and no label reads as
     *  selected (unless the user is actively dragging within this control).
     *  Default true = unchanged behavior. Used to split ONE logical choice across
     *  TWO stacked MorphSegmented rows: each row is passed the same selectedKey,
     *  but only the row that actually contains it shows the highlight — the other
     *  passes indicatorVisible=false so it doesn't falsely light its first segment
     *  (which the index-0 fallback below would otherwise do). */
    indicatorVisible: Boolean = true,
) {
    // Falls back to the first option (index 0) if selectedKey doesn't match any
    // option's key -- e.g. a caller passes a stale/unsupported key -- rather than
    // producing a -1 index that would crash every offset/width calculation below.
    val selectedIndex = options.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    // Inset between the track's outer edge and the row of segments.
    val trackPad = 4.dp
    // Horizontal breathing room between adjacent segment pills.
    val gap = 4.dp
    // pointerInput below is keyed on (n, stepPx) only, so its gesture-handling
    // coroutine is launched once and keeps running (awaitEachGesture loops
    // internally) across many recompositions without restarting. A plain closure
    // over selectedKey/onSelect/onTick would freeze at whatever those were on that
    // first launch — every read inside the gesture handler would silently use the
    // ORIGINAL selection forever, not the current one ("stops working after a
    // couple of taps, can't reselect the original option"). rememberUpdatedState
    // keeps these reads live without relaunching (interrupting) an in-progress
    // gesture.
    val currentSelectedKey by rememberUpdatedState(selectedKey)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnTick by rememberUpdatedState(onTick)
    // Slightly less rounded than before (was 20.dp) -- a small tune-down per feedback.
    val trackShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier.fillMaxWidth().clip(trackShape).background(containerColor)
            .then(if (borderColor != null) Modifier.border(BorderStroke(1.dp, borderColor), trackShape) else Modifier),
    ) {
        // BoxWithConstraints exposes maxWidth (the available width after trackPad is
        // applied) so segment width can be computed by dividing that width evenly
        // among n segments minus the gaps between them -- ordinary Row/Arrangement
        // weighting can't give this composable the raw pixel width it needs for the
        // pointerInput math below, since gesture coordinates arrive in px, not dp.
        BoxWithConstraints(Modifier.padding(trackPad).height(trackHeight)) {
            val n = options.size
            // Exact per-segment width so the highlight is flush with each pill's own
            // edges — no residual gap between the highlight and the track border.
            val segWidth = (maxWidth - gap * (n - 1)) / n
            val density = LocalDensity.current
            // Everything from here on (gesture tracking, indicator translation) works
            // in raw pixels rather than Dp, so segWidth/gap are converted once via the
            // current density. stepPx is the horizontal distance from one segment's
            // left edge to the next (segment width + the gap after it) -- the "pitch"
            // used by indexFor() to quantize a pixel offset back to a segment index.
            val stepPx = with(density) { (segWidth + gap).toPx() }
            // The furthest pixel offset the indicator's left edge can reach: the left
            // edge of the last segment. Used to clamp drag/tap positions in offsetFor.
            val maxXPx = with(density) { (segWidth * (n - 1) + gap * (n - 1)).toPx() }

            // While the finger is down, the highlight tracks it 1:1 (no spring lag);
            // on release it snaps to a real selection and springs — "drag it and it
            // bounces to wherever you let go" — instead of only responding to a tap.
            var dragXPx by remember { mutableStateOf<Float?>(null) }
            val segWidthPx = with(density) { segWidth.toPx() }
            val gapPx = with(density) { gap.toPx() }
            // On drag release, dragXPx clears immediately, but the new
            // selectedIndex only arrives after currentOnSelect's state change
            // round-trips back down through the caller -- for that gap (often
            // a full frame or more) restingXPx still reflected the OLD index,
            // so the indicator visibly snapped back toward where it used to
            // be before jerking again to the correct spot ("jumpy when you
            // let go"). pendingIndex holds the just-picked index as the
            // resting target the instant the drag ends, so there's nothing
            // to snap back to; it clears itself once the real prop catches up.
            var pendingIndex by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(pendingIndex, selectedIndex) {
                val pending = pendingIndex ?: return@LaunchedEffect
                if (pending == selectedIndex) {
                    // The caller adopted it. restingXPx resolves to the same
                    // segment either way now, so dropping it here is invisible.
                    pendingIndex = null
                    return@LaunchedEffect
                }
                // Not adopted YET is the normal case for a frame or two, and is
                // the whole reason pendingIndex exists. But a controlled caller
                // that REJECTS the change -- validation, a disabled option, an
                // unsupported combination -- never moves selectedIndex at all,
                // and the previous `LaunchedEffect(selectedIndex)` could not fire
                // then, because its key never changed. pendingIndex stayed set,
                // restingXPx kept resolving through it, and the indicator sat
                // parked on a segment the caller had refused -- disagreeing with
                // the bold label, which reads selectedIndex (see visualIndex
                // below) -- until some later accepted interaction cleared it.
                //
                // Releasing after a grace period covers both cases, and the two
                // are NOT symmetric: an accepted change clears in the branch
                // above the moment selectedIndex arrives, cancelling this delay,
                // so the wait only ever elapses on a rejection.
                //
                // That asymmetry is why this is deliberately generous rather than
                // snappy. Every real caller here routes through a DataStore write
                // and a Flow re-emission (setUnitSystem, setThemeMode,
                // setSettingsMode...), so a slow device can take a few hundred ms
                // to come back -- and cutting the hold short of that would spring
                // the indicator backwards and then forwards again, which is
                // exactly the "jumpy when you let go" this whole mechanism exists
                // to prevent. Undershooting reintroduces a live bug on every
                // caller; overshooting only delays the correction on a caller that
                // rejects, of which there are currently none. So: err long.
                delay(1200)
                pendingIndex = null
            }
            val restingXPx = (segWidthPx + gapPx) * (pendingIndex ?: selectedIndex)
            // A raw pixel Animatable driving graphicsLayer's translationX, not
            // animateDpAsState + Modifier.offset(x = ...dp) -- offset() moves
            // the layout position, so every single pixel of finger movement
            // during a drag forced a full relayout pass of this control. At
            // touch-sampling rates that read as visibly janky ("junky when
            // you're dragging your finger around on it") rather than a smooth
            // 1:1 follow. graphicsLayer's translation is a pure draw-phase
            // transform -- same visual position, no relayout.
            val indicatorXPx = remember { Animatable(restingXPx) }
            val targetXPx = dragXPx ?: restingXPx
            LaunchedEffect(targetXPx, dragXPx != null) {
                if (dragXPx != null) {
                    indicatorXPx.snapTo(targetXPx)
                } else {
                    // LowBouncy keeps a quick StiffnessMedium settle with just a
                    // light touch of overshoot (MediumBouncy wobbled too much on
                    // every change).
                    indicatorXPx.animateTo(
                        targetXPx,
                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                    )
                }
            }
            // Raw touch/drag X is where the finger physically is; the indicator's
            // rendered position is its LEFT edge. Centre the indicator on the touch
            // point (subtract half a segment) so it doesn't jump half a segment off
            // ("tracks your finger but not really").
            fun offsetFor(touchXPx: Float): Float = (touchXPx - segWidthPx / 2f).coerceIn(0f, maxXPx)

            // stepPx is the pitch (segment + gap); indexFor expects an X already in
            // "indicator left-edge" terms (run through offsetFor), so a segment's own
            // centre lands on an exact multiple of stepPx — the rounding boundary is
            // each segment's centre, not its trailing edge (which rounded a back-third
            // tap up into the next segment).
            fun indexFor(offsetXPx: Float): Int =
                (offsetXPx / stepPx).roundToInt().coerceIn(0, n - 1)

            // Which segment reads as "selected" (bold, tinted text) while dragging:
            // the live drag position, not the committed prop (which only changes on
            // release) — otherwise the moving highlight and the bold label disagree.
            // -1 when this row has no selection to show (indicatorVisible=false and
            // not dragging), so no label falsely bolds via the index-0 fallback.
            val visualIndex = dragXPx?.let { indexFor(it) } ?: if (indicatorVisible) selectedIndex else -1

            // Motion blur on the indicator: subtle while still, intensifies during drag
            // and fades as the spring settles — gives the sliding highlight a fluid,
            // refractive feel without a real directional blur.
            val isMoving = dragXPx != null
            // snap() jumps motionBlurX straight to 6f the instant dragging starts (no
            // ramp-up lag behind the finger); the spring only governs the fade back to
            // 0f once the finger lifts, so the blur relaxes smoothly instead of
            // vanishing abruptly the same frame the drag ends.
            val motionBlurX by animateFloatAsState(
                targetValue = if (isMoving) 6f else 0f,
                animationSpec = if (isMoving) snap() else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                label = "segMotionBlur",
            )
            // The sliding highlight pill. Declared before the Row of segment labels
            // below, so it draws underneath them in z-order -- the labels' text stays
            // legible on top of the highlight as it passes beneath. Its horizontal
            // position comes purely from graphicsLayer's translationX (a draw-phase
            // transform, see the comment on indicatorXPx above), never from layout,
            // so moving it never triggers a relayout of the sibling Row.
            // Hidden when this row doesn't hold the selection (indicatorVisible=false)
            // and the user isn't mid-drag on it — so a two-row split highlights only
            // the row that actually contains the selected key.
            val indicatorAlpha = if (indicatorVisible || dragXPx != null) 1f else 0f
            Box(
                Modifier
                    .width(segWidth)
                    .fillMaxHeight()
                    // motionBlurX folded into this SAME deferred lambda, not a
                    // separate body-level `.then(if (motionBlurX > 0.5f) ...)` --
                    // that read motionBlurX (an animateFloatAsState value that
                    // changes every frame of a drag and its settle-back) directly
                    // in the composable body, recomposing this whole Box on every
                    // one of those frames instead of just redrawing it, unlike
                    // translationX/alpha right above which were already correctly
                    // deferred.
                    .graphicsLayer {
                        translationX = indicatorXPx.value
                        alpha = indicatorAlpha
                        renderEffect = if (motionBlurX > 0.5f) {
                            BlurEffect(motionBlurX, motionBlurX, TileMode.Clamp)
                        } else {
                            null
                        }
                    }
                    .background(indicatorColor, RoundedCornerShape(14.dp)),
            )
            // The row of segment labels/icons, layered on top of the indicator Box
            // above and hosting the single pointerInput gesture detector that drives
            // both dragging the highlight and tapping a segment directly.
            Row(
                Modifier
                    .fillMaxSize()
                    // Touch-slop race (same as AnimatedSlider): don't consume anything
                    // until the gesture is confirmed horizontal (dx > slop && dx >= dy).
                    // A release before that is a tap (selects at that position);
                    // confirmed vertical movement (dy > slop) cedes the gesture to an
                    // ancestor scroll, so a settings list still scrolls past this control.
                    .pointerInput(n, stepPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val slop = viewConfiguration.touchSlop
                            // `claimed` is the gesture's little state machine: false means
                            // "undecided, still watching for enough movement to tell a tap
                            // from a drag"; once dx crosses the slop threshold horizontally
                            // it flips true and every subsequent move updates dragXPx
                            // directly. If the pointer is released while still undecided
                            // (claimed == false) that's treated as a tap at the release
                            // point rather than a drag.
                            var claimed = false
                            // A width change mid-drag (rotation, entering/leaving a
                            // compact layout) re-keys this pointerInput on n/stepPx
                            // and cancels this coroutine outright -- without the
                            // finally below, dragXPx was only ever cleared at the
                            // natural end of the loop, so a cancelled drag left the
                            // indicator frozen at its last dragged position forever.
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (!claimed) {
                                            change.consume()
                                            val idx = indexFor(offsetFor(down.position.x))
                                            if (options[idx].key != currentSelectedKey) {
                                                currentOnTick()
                                                currentOnSelect(options[idx].key)
                                            }
                                        }
                                        break
                                    }
                                    if (!claimed) {
                                        val dx = abs(change.position.x - down.position.x)
                                        val dy = abs(change.position.y - down.position.y)
                                        when {
                                            // Movement is more horizontal than vertical and past
                                            // the slop threshold: commit to a drag, consume the
                                            // event so no ancestor sees it, and start tracking the
                                            // finger's raw x position for the indicator.
                                            dx > slop && dx >= dy -> {
                                                claimed = true
                                                change.consume()
                                                dragXPx = offsetFor(change.position.x)
                                            }
                                            // Movement is vertical past the slop threshold before
                                            // horizontal movement was confirmed: this isn't a drag
                                            // on this control at all, so break out without
                                            // consuming -- letting an ancestor (e.g. a scrollable
                                            // list) handle it instead.
                                            dy > slop -> break
                                        }
                                    } else if (change.positionChanged()) {
                                        // Already dragging: keep following the finger 1:1 every
                                        // frame (see indicatorXPx's snapTo above).
                                        change.consume()
                                        dragXPx = offsetFor(change.position.x)
                                    }
                                }
                                // Gesture ended after being claimed as a drag: commit whatever
                                // segment the release point maps to as the new selection, and
                                // stash it in pendingIndex so the resting position doesn't
                                // visibly snap back before the caller's prop round-trips (see
                                // the comment on pendingIndex above).
                                if (claimed) {
                                    val x = dragXPx ?: offsetFor(down.position.x)
                                    val idx = indexFor(x)
                                    pendingIndex = idx
                                    if (options[idx].key != currentSelectedKey) {
                                        currentOnTick()
                                        currentOnSelect(options[idx].key)
                                    }
                                }
                            } finally {
                                dragXPx = null
                            }
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                // One Box per option, laid out left-to-right at a fixed segWidth so
                // each one lines up exactly with the sliding indicator underneath it.
                options.forEachIndexed { i, opt ->
                    val isSelected = i == visualIndex
                    // Foreground colour springs between selected/unselected rather than
                    // snapping, so text/icon tint fades in step with the indicator's
                    // own motion instead of flipping abruptly the instant visualIndex
                    // changes.
                    val fg by animateColorAsState(
                        if (isSelected) selectedTextColor else unselectedTextColor,
                        spring(stiffness = Spring.StiffnessMediumLow),
                        label = "segFg",
                    )
                    Box(
                        modifier = Modifier
                            .width(segWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            // The whole control's touch/drag handling lives in
                            // the parent Row's single pointerInput above (a
                            // real clickable/selectable here would register its
                            // own gesture detector and fight that custom drag
                            // logic), so this is a semantics-only, additive
                            // accessibility action -- TalkBack invokes it via
                            // the AccessibilityNodeInfo click action, which
                            // doesn't go through the normal touch dispatch
                            // pipeline the drag gesture depends on. Previously
                            // this control had NO accessibility semantics at
                            // all: unlabelled, unfocusable, state unannounced.
                            .semantics {
                                contentDescription = opt.label
                                role = Role.Tab
                                selected = isSelected
                                onClick(label = "Select ${opt.label}") {
                                    if (opt.key != currentSelectedKey) {
                                        currentOnTick()
                                        currentOnSelect(opt.key)
                                    }
                                    true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            // Only rendered when the option supplies an icon (the watch
                            // build typically omits icons entirely, see SegmentOption's
                            // doc). Icon grows slightly and gets more breathing room from
                            // the label when selected, echoing the same "active choice
                            // reads bigger/bolder" treatment as the label's font weight
                            // below.
                            opt.icon?.let { icon ->
                                Image(
                                    painter = rememberVectorPainter(icon),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(fg),
                                    modifier = Modifier.size(if (isSelected) 16.dp else 14.dp),
                                )
                                Spacer(Modifier.width(if (isSelected) 6.dp else 4.dp))
                            }
                            BasicText(
                                opt.label,
                                // BasicText doesn't consult LocalContentColor like Text()
                                // does, so the animated selected/unselected color has to be
                                // baked into the style here — it was computed as `fg` above
                                // but never actually reached the label, silently rendering
                                // at TextStyle's default (unspecified → black) regardless of
                                // selection or theme. Also lighter than the caller's style:
                                // Medium when selected reads as the active choice; Normal
                                // for the rest keeps the whole control visually quiet.
                                // Scale the unselected label down slightly, but only when
                                // the caller's fontSize is actually specified — multiplying an
                                // unspecified TextUnit throws IllegalArgumentException, so an
                                // unspecified-size style copies through unchanged instead.
                                style = if (isSelected) {
                                    textStyle.copy(color = fg, fontWeight = FontWeight.Medium)
                                } else {
                                    val unselected = textStyle.copy(color = fg, fontWeight = FontWeight.Normal)
                                    if (textStyle.fontSize.isSpecified) unselected.copy(fontSize = textStyle.fontSize * 0.88f) else unselected
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
