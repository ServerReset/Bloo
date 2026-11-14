package com.bloo.bluelink.ui

import com.bloo.uicommon.RevealText
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.layout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import com.bloo.uicommon.lowPowerCalm
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance
import com.bloo.uicommon.bounceSpring
import com.bloo.uicommon.ExpressiveButtonGroup
import com.bloo.uicommon.PrimaryTextStyle
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapPage
import com.bloo.uicommon.MorphActionButton
import com.bloo.uicommon.Haptics
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.animation.core.animate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.LocalPowerPolicy
import com.bloo.uicommon.LocalReduceMotion

private val DetailsPanelCorner = 28.dp

/** What the top bar shows: only its handle (no car selected), the selected car's single line, or that line with its details open. */
internal enum class MapBarMode { Handle, Line, Details }

/**
 * The bar's corner, between the pill it is as a handle or a line (radius half its height) and the rounded panel its details open into
 * ([DetailsPanelCorner]). [detailsT] is 0 for the pill and 1 for the panel; the value is animated by the caller.
 *
 * It is a [RoundedCornerShape] rather than a plain [Shape]: the glass only refracts a rounded corner, and any other shape falls back to
 * the flat frosted blur whatever the glass setting says.
 */
private class BlendCornerSize(private val detailsT: State<Float>) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val pill = shapeSize.height / 2f
        val panel = with(density) { DetailsPanelCorner.toPx() }
        return (pill + (panel - pill) * detailsT.value).coerceIn(0f, shapeSize.minDimension / 2f)
    }
}

/** The handle-only bar's width: the handle with a little glass around it. The bar grows from this to the single line when a car is selected. */
internal val MapBarHandleWidth = 64.dp

/** One car as the map's top bar shows it. */
internal data class BarItem(
    val vin: String,
    val name: String,
    val photo: String?,
    val charge: String?,
    val range: String?,
    val details: List<Pair<String, String>>,
)

/** A bar's side in the strip when it is the only other car: it sits on whichever side the strip is moving toward. */
private const val SideBySwipe = Int.MIN_VALUE

/** One bar in the carousel's strip: which car, and its side (-1 the car before, 0 the car shown, 1 the car after). */
private class BarSlot(val item: Int, val side: Int)

/**
 * The bar over an expanded map sheet, in three states (see [MapBarMode]):
 *  - no car selected: only the drag handle, a small floating pill;
 *  - a car selected: one line, the car's photo and name at the start, its charge and range at the end;
 *  - that line tapped: the car's other details open below it.
 *
 * With a car showing, the bar is an infinite carousel of every car, like the garage's own pages. A drag moves the strip of bars 1:1 under
 * the finger, the neighbouring car's real bar coming in beside this one with the bar's own margin between them. On release the strip
 * settles one car over (a flick, or a drag past a third of the bar) or springs back, carrying the flick's speed. One gesture never moves
 * more than one car, and flicks in quick succession each move one. The car changes only once the strip has landed, and in that same frame
 * the strip is put back at rest, so the bar that came in is the bar that stays: nothing jumps and nothing is swapped for a copy. The caller
 * hears of the change through [onIndexChange], and its [index] catches up a frame later to find the bar already there.
 *
 * The whole bar moves on one timeline. The grow from handle to line ([lineProgress], read in layout), the corners and the details all
 * run on the same spring, and every size, offset and fade is computed from that progress in layout or draw, so the bar is never
 * re-composed while it moves, nor while it is swiped. [dragModifier] carries the drag-to-dismiss and pull-to-refresh gesture.
 */
@Composable
internal fun MapTopBar(
    /** Every car, in the garage's order. */
    items: List<BarItem>,
    /** The car the bar shows: the garage's current car. */
    index: Int,
    /** A swipe on the bar landed on another car. */
    onIndexChange: (Int) -> Unit,
    mapHazeState: HazeState,
    modifier: Modifier = Modifier,
    dragModifier: Modifier,
    /** False once the car has scrolled off screen: the details fold back to the line. */
    carOnScreen: Boolean = true,
    /** What the bar shows: its handle, the car's line, or that line with the details open. */
    mode: MapBarMode = MapBarMode.Handle,
    /** Asks for the details to open or fold; the caller holds the state, so a dismiss can fold them first. */
    onDetailsOpenChange: (Boolean) -> Unit = {},
    /** How far the bar has grown from its handle to its line, 0 to 1. */
    lineProgress: () -> Float = { 1f },
) {
    val count = items.size
    if (count == 0) return
    val selected = mode != MapBarMode.Handle
    LaunchedEffect(carOnScreen) { if (!carOnScreen) onDetailsOpenChange(false) }
    LaunchedEffect(selected) { if (!selected) onDetailsOpenChange(false) }

    val density = LocalDensity.current
    // The gap between two bars in the strip: the bar's gutter from the screen's edge on each side, so a neighbour at rest sits just off
    // the screen and comes in keeping the same margin the bar keeps.
    val gapPx = with(density) { GapSection.toPx() * 2f }
    val haptics = LocalHaptics.current
    val plainMotion = LocalReduceMotion.current || LocalPowerPolicy.current.lean
    // A pager's settle: no bounce, and the flick's speed carried on. Under reduce-motion or power saving, a short plain ease.
    val settleSpec: AnimationSpec<Float> =
        if (plainMotion) tween(160) else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val scope = rememberCoroutineScope()

    // The car the strip is centred on. Kept here, so a swipe changes it in the same frame the strip comes back to rest.
    var shown by remember { mutableIntStateOf(index.coerceIn(0, count - 1)) }
    val center = shown.coerceIn(0, count - 1)
    // The strip's sideways offset in px: 0 at rest, negative while it moves toward the next car. Read only in draw and in the gesture.
    var offsetPx by remember { mutableFloatStateOf(0f) }
    // While the bar slides to a car that is not a neighbour (a pin tap two cars away): the car leaving, and the side it leaves on.
    var outgoing by remember { mutableStateOf<String?>(null) }
    var outgoingSide by remember { mutableIntStateOf(0) }
    // One slot of the strip, a bar and the gap after it, in px. Measured in layout; read in draw and in the gesture.
    val slotPx = remember { floatArrayOf(0f) }
    // Who owns the strip, a finger or a settle. A change of car from outside waits until neither does. The token tells a cancelled settle
    // that something else has taken over, so it does not hand the strip back.
    val busy = remember { booleanArrayOf(false) }
    val owner = remember { intArrayOf(0) }
    // A settle that changes the car when it lands. If a new flick starts first, the change is made then and there, so two quick flicks move
    // two cars instead of the second cancelling the first.
    val pendingDir = remember { intArrayOf(0) }
    // The caller's car when the finger went down: a change from outside during the gesture is caught up with once it is let go.
    val externalAtStart = remember { intArrayOf(-1) }
    val motion = remember { arrayOfNulls<Job>(1) }
    var pressed by remember { mutableStateOf(false) }
    val pillScale by animateFloatAsState(
        targetValue = if (pressed) 1.04f else 1f,
        animationSpec = bounceSpring(stiffness = Spring.StiffnessLow),
        label = "mapBarPop",
    )

    val latestIndex = rememberUpdatedState(index)
    val latestItems = rememberUpdatedState(items)
    val latestChange = rememberUpdatedState(onIndexChange)
    val latestSelected = rememberUpdatedState(selected)
    val latestFold = rememberUpdatedState(onDetailsOpenChange)
    val latestSpec = rememberUpdatedState(settleSpec)

    fun claim(): Int {
        owner[0] += 1
        busy[0] = true
        return owner[0]
    }

    // Lands a settle's car change: the strip's middle moves one car over in the same frame as the strip is put where that car now is (at
    // rest, or, mid-flight, wherever it was, so it keeps its place on screen). Then the caller is told.
    fun commitPending(atRest: Boolean) {
        val dir = pendingDir[0]
        if (dir == 0) return
        pendingDir[0] = 0
        val n = latestItems.value.size
        if (n < 2) return
        val next = wrapIndex(shown.coerceIn(0, n - 1) + dir, n)
        Snapshot.withMutableSnapshot {
            shown = next
            offsetPx = if (atRest) 0f else offsetPx + dir * slotPx[0]
        }
        haptics?.pageTurn()
        latestChange.value(next)
    }

    // Each slide's number: a slide that has been overtaken (by a newer slide, or by a finger) leaves the strip alone as it winds down.
    val slideGen = remember { intArrayOf(0) }

    // Slides the bar to [target] from the side it lies on, the way the garage travels to a car: for a car changed from outside the bar.
    suspend fun slideTo(target: Int) {
        val gen = ++slideGen[0]
        val n = latestItems.value.size
        val from = shown.coerceIn(0, n - 1)
        val w = slotPx[0]
        if (n < 2 || w <= 0f || target == from) {
            Snapshot.withMutableSnapshot { shown = target; offsetPx = 0f; outgoing = null }
            return
        }
        val forward = wrapIndex(target - from, n)
        val dir = if (forward <= n / 2) 1 else -1
        val adjacent = forward == 1 || forward == n - 1
        Snapshot.withMutableSnapshot {
            shown = target
            outgoing = if (adjacent) null else latestItems.value[from].vin
            outgoingSide = -dir
            offsetPx = dir * w
        }
        try {
            animate(offsetPx, 0f, animationSpec = latestSpec.value) { v, _ -> offsetPx = v }
        } finally {
            if (slideGen[0] == gen && outgoing != null) Snapshot.withMutableSnapshot { outgoing = null; offsetPx = 0f }
        }
    }

    // Starts a slide on the strip's one motion, replacing whatever slide was running. A finger takes the motion over by cancelling it.
    fun startSlide(target: Int) {
        motion[0]?.cancel()
        motion[0] = scope.launch { slideTo(target) }
    }

    // A change of car from outside the bar (a pin tap, the map opening on a car): the bar slides to it, unless a finger or its settle owns
    // the strip, in which case it is caught up with once that is done.
    LaunchedEffect(index, count) {
        if (busy[0]) return@LaunchedEffect
        val target = index.coerceIn(0, count - 1)
        if (target != shown.coerceIn(0, count - 1)) startSlide(target)
    }

    // The strip's bars: the car shown, the cars either side, and, mid-slide to a car further off, the car leaving. Composed once per change
    // of car, never per frame: where each is drawn is worked out in draw from the offset.
    val slots = remember(items, center, outgoing, outgoingSide) {
        buildList {
            add(BarSlot(center, 0))
            val leaving = outgoing?.let { vin -> items.indexOfFirst { it.vin == vin } }?.takeIf { it >= 0 && it != center }
            if (count == 2) {
                add(BarSlot(wrapIndex(center + 1, 2), SideBySwipe))
            } else if (count >= 3) {
                val before = wrapIndex(center - 1, count)
                val after = wrapIndex(center + 1, count)
                if (leaving == null) {
                    add(BarSlot(before, -1))
                    add(BarSlot(after, 1))
                } else {
                    // The car leaving takes the side it leaves on, in place of that side's neighbour.
                    val kept = if (outgoingSide == -1) BarSlot(after, 1) else BarSlot(before, -1)
                    add(kept)
                    if (leaving != kept.item) add(BarSlot(leaving, outgoingSide))
                }
            }
        }
    }
    val neighbourMode = if (mode == MapBarMode.Details) MapBarMode.Line else mode

    Layout(
        modifier = modifier
            .then(dragModifier)
            // Press-and-hold pop on the bar. requireUnconsumed = false observes down and up without consuming, so the drags and the tap
            // still receive the touch.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .pointerInput(Unit) {
                // Read in the INITIAL pass, before the map and the sheet behind the bar: read in the main pass, the expanded map's own pan
                // took every sideways drag over it first. A tap is untouched: nothing is claimed until the finger has moved past the slop,
                // and a mostly vertical drag is left to the pull. The strip does not move sideways itself, so the finger's position is
                // read in steady coordinates.
                val slopPx = 10.dp.toPx()
                val flingPx = 400.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // Only a car's line swipes: the handle alone has no car to swipe from, and one car has none to swipe to.
                    if (!latestSelected.value || latestItems.value.size < 2) return@awaitEachGesture
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var last = down.position
                    var dx = 0f
                    var dy = 0f
                    var claimed = false
                    var startOffset = 0f
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        dx += change.position.x - last.x
                        dy += change.position.y - last.y
                        last = change.position
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!claimed) {
                            if (abs(dx) > slopPx && abs(dx) > abs(dy)) {
                                claimed = true
                                // Take the strip over from whatever was moving it, landing a car change it was about to make.
                                motion[0]?.cancel()
                                slideGen[0]++
                                claim()
                                commitPending(atRest = false)
                                if (outgoing != null) Snapshot.withMutableSnapshot { outgoing = null; offsetPx = 0f }
                                externalAtStart[0] = latestIndex.value
                                startOffset = offsetPx - dx
                                latestFold.value(false)
                            } else if (abs(dy) > slopPx) {
                                return@awaitEachGesture
                            } else {
                                continue
                            }
                        }
                        change.consume()
                        // One car per gesture: the strip follows the finger up to one slot either way, and no further.
                        val w = slotPx[0]
                        offsetPx = (startOffset + dx).coerceIn(-w, w)
                    }
                    if (!claimed) return@awaitEachGesture
                    val w = slotPx[0].coerceAtLeast(1f)
                    val vx = tracker.calculateVelocity().x
                    val off = offsetPx
                    // A flick decides by its direction, unless it flicks back past rest; a slow drag decides by how far it went.
                    val dir = when {
                        vx <= -flingPx -> if (off <= 0f) 1 else 0
                        vx >= flingPx -> if (off >= 0f) -1 else 0
                        off <= -w * 0.3f -> 1
                        off >= w * 0.3f -> -1
                        else -> 0
                    }
                    pendingDir[0] = dir
                    val token = claim()
                    motion[0] = scope.launch {
                        try {
                            animate(off, -dir * w, initialVelocity = vx, animationSpec = latestSpec.value) { v, _ -> offsetPx = v }
                            commitPending(atRest = true)
                            if (dir == 0) offsetPx = 0f
                        } finally {
                            if (owner[0] == token) {
                                busy[0] = false
                                pendingDir[0] = 0
                                // A change of car from outside while the strip was held: catch up with it now.
                                val n = latestItems.value.size
                                val ext = latestIndex.value.coerceIn(0, n - 1)
                                if (ext != externalAtStart[0] && ext != shown.coerceIn(0, n - 1)) {
                                    motion[0] = null
                                    startSlide(ext)
                                }
                            }
                        }
                    }
                }
            }
            // The swipe, for anyone who cannot swipe: the previous and next car as actions on the bar.
            .semantics {
                if (selected && count > 1) {
                    customActions = listOf(
                        CustomAccessibilityAction("Previous car") { latestChange.value(wrapIndex(center - 1, count)); true },
                        CustomAccessibilityAction("Next car") { latestChange.value(wrapIndex(center + 1, count)); true },
                    )
                }
            },
        content = {
            slots.forEach { slot ->
                val item = items[slot.item]
                key(item.vin) {
                    val isCenter = slot.side == 0
                    Box(
                        Modifier
                            .graphicsLayer {
                                val side = if (slot.side == SideBySwipe) (if (offsetPx < 0f) 1 else -1) else slot.side
                                translationX = side * slotPx[0] + offsetPx
                                if (isCenter) {
                                    scaleX = pillScale
                                    scaleY = pillScale
                                }
                            }
                            // A neighbour at rest is off the screen: it stays composed, so a swipe never waits for it to be built, but it is
                            // not drawn, so its glass costs nothing until it comes into view.
                            .drawWithContent {
                                val w = slotPx[0]
                                val side = if (slot.side == SideBySwipe) (if (offsetPx < 0f) 1 else -1) else slot.side
                                if (isCenter || abs(side * w + offsetPx) < w - 0.5f) drawContent()
                            }
                            .then(if (isCenter) Modifier else Modifier.clearAndSetSemantics {}),
                    ) {
                        MapBarCard(
                            item = item,
                            mode = if (isCenter) mode else neighbourMode,
                            lineProgress = lineProgress,
                            mapHazeState = mapHazeState,
                            onDetailsOpenChange = if (isCenter) onDetailsOpenChange else null,
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        slotPx[0] = w + gapPx
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        // The bar is as tall as the car shown (the first slot); its neighbours are drawn beside it and take no room.
        val h = placeables.firstOrNull()?.height ?: 0
        layout(w, h) { placeables.forEach { it.place(0, 0) } }
    }
}

/**
 * One car's bar: its glass, the header (photo and name, drag handle, charge and range) and the details below. [onDetailsOpenChange] is
 * null for a neighbour in the strip, which shows its line and does not open.
 */
@Composable
private fun MapBarCard(
    item: BarItem,
    mode: MapBarMode,
    lineProgress: () -> Float,
    mapHazeState: HazeState,
    onDetailsOpenChange: ((Boolean) -> Unit)?,
) {
    val selected = mode != MapBarMode.Handle
    val detailsOpen = mode == MapBarMode.Details
    // The same spring as the grow from the handle (see ExpandableMap), so the details, the corners and the grow land together.
    val spring = lowPowerCalm<Float>(Spring.StiffnessMediumLow)
    // Read as a State, never as a value in composition: the bar's shape and its reveal read it at draw and layout time only.
    val detailsTState = animateFloatAsState(targetValue = if (detailsOpen) 1f else 0f, animationSpec = spring, label = "mapBarDetails")
    // A real RoundedCornerShape, so the liquid glass applies (any other shape falls back to frosted blur). Its corner reads the animated
    // value only when the outline is drawn, so the details spring does not recompose the bar on every frame.
    val barShape = remember(detailsTState) { RoundedCornerShape(BlendCornerSize(detailsTState)) }
    val density = LocalDensity.current
    val handleHeightPx = with(density) { 22.dp.toPx() }
    val lineHeightPx = with(density) { 60.dp.toPx() }
    val gutterPx = with(density) { GapPage.toPx() }
    val slidePx = with(density) { 24.dp.toPx() }
    GlassSurface(
        // The corners ease between the pill and the rounded details panel on the same spring.
        shape = barShape,
        // The bar is liquid glass: the refraction and specular sheen of the app's floating glass, drawn over the map behind it.
        liquid = true,
        modifier = Modifier.fillMaxWidth(),
        hazeState = mapHazeState,
        // No contentColor override: the map follows the app theme (dark filter only in dark mode), so a forced white glyph vanishes on a
        // light map. GlassSurface's onSurface default tracks the theme and custom palettes.
    ) {
        Column {
            // The header: the car at the start, the drag handle in the middle, its charge and range at the end. Its height and gutters
            // grow with the line, in layout, so the header never jumps between the handle and the line.
            Row(
                Modifier
                    .then(
                        if (selected && onDetailsOpenChange != null) Modifier.clickable { onDetailsOpenChange(!detailsOpen) } else Modifier,
                    )
                    .layout { measurable, constraints ->
                        val p = lineProgress().coerceIn(0f, 1f)
                        val minHeight = (handleHeightPx + (lineHeightPx - handleHeightPx) * p).roundToInt()
                        val gutter = (gutterPx * p).roundToInt()
                        val inner = measurable.measure(
                            constraints.copy(
                                minWidth = (constraints.minWidth - 2 * gutter).coerceAtLeast(0),
                                maxWidth = (constraints.maxWidth - 2 * gutter).coerceAtLeast(0),
                                minHeight = max(constraints.minHeight, minHeight),
                            ),
                        )
                        val width = inner.width + 2 * gutter
                        layout(width.coerceAtMost(constraints.maxWidth), inner.height) { inner.place(gutter, 0) }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The car's side: it fades and slides in from the start as the bar grows, and is silent while nothing is selected.
                Box(
                    Modifier
                        .weight(1f)
                        .clipToBounds()
                        .graphicsLayer {
                            val p = lineProgress()
                            alpha = p
                            translationX = -(1f - p) * slidePx
                        }
                        .then(if (selected) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GapGroup),
                    ) {
                        if (item.photo != null) {
                            AsyncImage(
                                model = rememberPhotoModel(item.photo),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 48.dp, height = 36.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        } else {
                            Icon(AppIcons.DirectionsCar, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                        RollingNumber(
                            item.name,
                            style = PrimaryTextStyle(),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                // The drag handle, centred: a short bar on the bar's own glass, with no glass of its own inside. Purely visual.
                Box(Modifier.size(width = 36.dp, height = 18.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(width = 24.dp, height = 4.dp)
                            .background(LocalContentColor.current.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
                    )
                }
                // The charge and the range at the end: they fade and slide in from the end, as the car's side does from the start.
                Box(
                    Modifier
                        .weight(1f)
                        .clipToBounds()
                        .graphicsLayer {
                            val p = lineProgress()
                            alpha = p
                            translationX = (1f - p) * slidePx
                        }
                        .then(if (selected) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    // The charge and the range on one line at the end, so the car's figures read as a pair.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (item.charge != null) {
                            RollingNumber(item.charge, style = PrimaryTextStyle(), fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        if (item.charge != null && item.range != null) Spacer(Modifier.width(GapGroup))
                        if (item.range != null) {
                            RollingNumber(
                                item.range,
                                style = PrimaryTextStyle(),
                                fontWeight = FontWeight.Medium,
                                color = mutedContentColor(),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            // The details reveal from the header down, in layout: the panel is measured in full and shown as far as the spring has opened it,
            // so the bar's height follows the same motion as its corners, with no size animation of its own.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val full = measurable.measure(constraints.copy(minHeight = 0))
                        val shown = (full.height * detailsTState.value).roundToInt()
                        layout(full.width, shown) { full.place(0, 0) }
                    },
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = GapPage).padding(bottom = GapGroup),
                    verticalArrangement = Arrangement.spacedBy(GapRow),
                ) {
                    item.details.forEach { (label, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SecondaryText(label, modifier = Modifier.weight(1f))
                            RevealText(value, style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

