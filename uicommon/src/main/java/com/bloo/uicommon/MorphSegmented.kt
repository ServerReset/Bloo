package com.bloo.uicommon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One option in a [MorphSegmented] control. [icon] is optional. */
data class SegmentOption(val key: String, val label: String, val icon: ImageVector? = null)

/**
 * Each segment's share of the track at highlight position [pos] (fractional while travelling): 1,
 * plus up to ([ratio] - 1) where the highlight sits, fading linearly to the neighbour as it moves,
 * scaled by [shown] (0 = nothing selected, all equal).
 */
fun segmentWeights(n: Int, pos: Float, shown: Float, ratio: Float): FloatArray = FloatArray(n) { i ->
    1f + (ratio - 1f) * shown * (1f - abs(pos - i)).coerceAtLeast(0f)
}

/**
 * Pixel widths for the segments: [free] split by [weights], except no segment goes below its
 * [naturals] (the width its label needs), so a squashed neighbour never truncates its text.
 */
fun fitSegmentWidths(free: Float, weights: FloatArray, naturals: FloatArray): FloatArray {
    val n = weights.size
    if (naturals.sum() >= free) {
        val total = naturals.sum().coerceAtLeast(0.01f)
        return FloatArray(n) { free * naturals[it] / total }
    }
    val pinned = BooleanArray(n)
    val out = FloatArray(n)
    repeat(n) {
        val pinnedSum = (0 until n).filter { pinned[it] }.sumOf { naturals[it].toDouble() }.toFloat()
        val share = (free - pinnedSum).coerceAtLeast(0f)
        val total = (0 until n).filter { !pinned[it] }.sumOf { weights[it].toDouble() }.toFloat().coerceAtLeast(0.01f)
        var changed = false
        for (i in 0 until n) {
            out[i] = if (pinned[i]) naturals[i] else share * weights[i] / total
            if (!pinned[i] && out[i] < naturals[i]) { pinned[i] = true; changed = true }
        }
        if (!changed) return out
    }
    return FloatArray(n) { maxOf(out[it], naturals[it]) }
}

/** How much wider the chosen segment is than each of the others, by option count. */
private fun stretchRatio(n: Int): Float = when {
    n <= 2 -> 2.2f
    n == 3 -> 1.9f
    n == 4 -> 1.6f
    else -> 1.4f
}

/**
 * Bloo's full-width segmented selector: a pick-one control where the chosen segment STRETCHES and
 * the rest SQUASH to make room, all riding one bouncy spring, so the choice reads as the control
 * physically leaning toward what you picked.
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
    /** Hairline rim colour, or null for a borderless track. */
    borderColor: Color? = null,
    indicatorVisible: Boolean = true,
) {
    val n = options.size
    val selectedIndex = options.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val currentSelectedKey by rememberUpdatedState(selectedKey)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnTick by rememberUpdatedState(onTick)
    val currentOptions by rememberUpdatedState(options)
    val trackShape = RoundedCornerShape(16.dp)
    val segmentShape = RoundedCornerShape(12.dp)
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // The segment the finger is over mid-drag, or the committed one; -1 when none should light up.
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    // A drag's choice is held until the new selection arrives, so the control doesn't flick back.
    var pendingIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingIndex, selectedIndex) {
        val pending = pendingIndex ?: return@LaunchedEffect
        if (pending == selectedIndex) pendingIndex = null else {
            kotlinx.coroutines.delay(1200)
            pendingIndex = null
        }
    }
    val visualIndex = dragIndex ?: pendingIndex ?: if (indicatorVisible) selectedIndex else -1

    // ONE spring drives everything: the highlight's position along the track (0..n-1, fractional
    // while it travels).
    val ratio = stretchRatio(n)
    val pos = remember(n) { Animatable(selectedIndex.toFloat()) }
    val shown = remember(n) { Animatable(if (visualIndex >= 0) 1f else 0f) }
    LaunchedEffect(visualIndex, n) {
        if (visualIndex >= 0) {
            launch { shown.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
            pos.animateTo(visualIndex.toFloat(), spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
        } else {
            shown.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
        }
    }
    // While a finger is down the highlight is the finger's: [dragFrac] is its fractional position
    // (NaN = no drag) and [lag] is how far it still trails the finger after the initial resistance.
    var dragFrac by remember { mutableFloatStateOf(Float.NaN) }
    val lag = remember { Animatable(0f) }
    // Equal widths while dragging, so the highlight is the same size under the finger all the way
    // along; the stretch springs back in on release.
    val stretch = remember { Animatable(1f) }
    // The elastic budget at the first/last option, in option units: same fraction of the range the
    // slider uses (see [EdgeOverscrollFraction]), so pulling past the end of either control
    // stretches by the same proportion.
    val edgeOver = if (n > 1) (n - 1) * EdgeOverscrollFraction else 0f
    // Physics-driven edge bounce, not a hard clamp: past the first/last option the highlight keeps
    // moving under the finger but works for it (the shared asymptotic rubber band), and letting go
    // hands it to the settle spring below, which carries it back with a little overshoot.
    fun posNow(): Float {
        val raw = if (dragFrac.isNaN()) pos.value else (dragFrac - lag.value)
        return when {
            raw < 0f -> -rubberBand(-raw, edgeOver)
            raw > (n - 1).toFloat() -> (n - 1).toFloat() + rubberBand(raw - (n - 1).toFloat(), edgeOver)
            else -> raw
        }
    }
    fun weightsNow(): FloatArray = segmentWeights(n, posNow(), shown.value * stretch.value, ratio)

    val gap = 4.dp
    Box(
        modifier = modifier.blockPageSwipe().fillMaxWidth().clip(trackShape).background(containerColor)
            .then(if (borderColor != null) Modifier.border(BorderStroke(1.dp, borderColor), trackShape) else Modifier),
    ) {
        val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { gap.toPx() }
        // The segment widths of the last layout, written by the track and read by touch handling.
        val widths = remember(n) { FloatArray(n) }
        fun indexAt(x: Float): Int {
            var left = 0f
            for (i in 0 until n) {
                if (x < left + widths[i] + gapPx / 2f) return i
                left += widths[i] + gapPx
            }
            return n - 1
        }

        SegmentTrack(
            n = n,
            weights = ::weightsNow,
            position = ::posNow,
            shown = { shown.value },
            gap = gap,
            height = trackHeight,
            widths = widths,
            // The chosen segment's fill is painted behind the labels at that segment's live size.
            indicatorColor = indicatorColor,
            segmentShape = segmentShape,
            modifier = Modifier
                .padding(4.dp)
                .pointerInput(n) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        var claimed = false
                        var crossed = false
                        var startFrac = 0f
                        var last = -1
                        val stepPx = widths.sum() / n + gapPx
                        // The first few dp of a drag are held back (it resists), then it lets go
                        // and catches up to the finger.
                        val resistPx = 18.dp.toPx()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!claimed) {
                                        change.consume()
                                        val idx = indexAt(down.position.x)
                                        val key = currentOptions[idx].key
                                        if (key != currentSelectedKey) {
                                            currentOnTick()
                                            currentOnSelect(key)
                                        }
                                    }
                                    break
                                }
                                val dxPx = change.position.x - down.position.x
                                if (!claimed) {
                                    val dy = abs(change.position.y - down.position.y)
                                    when {
                                        abs(dxPx) > slop && abs(dxPx) >= dy -> {
                                            claimed = true
                                            change.consume()
                                            startFrac = pos.value
                                            dragFrac = startFrac
                                            scope.launch { lag.snapTo(0f); stretch.animateTo(0f, spring(stiffness = Spring.StiffnessHigh)) }
                                            currentOnTick()
                                        }
                                        dy > slop -> break
                                    }
                                }
                                if (claimed && change.positionChanged()) {
                                    change.consume()
                                    val target = startFrac + dxPx / stepPx
                                    if (!crossed && abs(dxPx) < resistPx) {
                                        dragFrac = startFrac + dxPx * 0.3f / stepPx
                                    } else {
                                        if (!crossed) {
                                            crossed = true
                                            val heldAt = startFrac + (if (dxPx > 0) 1f else -1f) * resistPx * 0.3f / stepPx
                                            scope.launch { lag.snapTo(target - heldAt); lag.animateTo(0f, spring(0.8f, Spring.StiffnessMedium)) }
                                        }
                                        dragFrac = target
                                    }
                                    val idx = Math.round(posNow()).coerceIn(0, n - 1)
                                    dragIndex = idx
                                    if (idx != last) { last = idx; currentOnTick() }
                                }
                            }
                            if (claimed) {
                                val idx = Math.round(posNow()).coerceIn(0, n - 1)
                                pendingIndex = idx
                                val key = currentOptions[idx].key
                                if (key != currentSelectedKey) currentOnSelect(key)
                            }
                        } finally {
                            val from = posNow()
                            val land = Math.round(from).coerceIn(0, n - 1)
                            dragIndex = null
                            if (claimed) {
                                // Hand the highlight back to its own spring from exactly where the
                                // finger left it, and let it snap to the option it is over.
                                scope.launch {
                                    pos.snapTo(from)
                                    dragFrac = Float.NaN
                                    lag.snapTo(0f)
                                    launch { stretch.animateTo(1f, spring(0.7f, Spring.StiffnessMediumLow)) }
                                    pos.animateTo(land.toFloat(), spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        }
                    }
                },
        ) { i ->
            val opt = options[i]
            val isSelected = i == visualIndex
            val fg by animateColorAsState(
                if (isSelected) selectedTextColor else unselectedTextColor,
                spring(stiffness = Spring.StiffnessMediumLow),
                label = "segFg",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(segmentShape)
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
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    opt.icon?.let { icon ->
                        Image(
                            painter = rememberVectorPainter(icon),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(fg),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    BasicText(
                        opt.label,
                        style = textStyle.copy(color = fg, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The track's layout: [n] children sharing the width in proportion to [weights], which is read in
 * measure (and draw) only, so the spring that stretches and squashes them re-lays-out the track
 * every frame without ever recomposing it.
 */
@Composable
private fun SegmentTrack(
    n: Int,
    weights: () -> FloatArray,
    gap: Dp,
    height: Dp,
    widths: FloatArray,
    position: () -> Float,
    shown: () -> Float,
    indicatorColor: Color,
    segmentShape: RoundedCornerShape,
    modifier: Modifier,
    segment: @Composable (Int) -> Unit,
) {
    Layout(
        content = { for (i in 0 until n) Box(Modifier.layoutId(i)) { segment(i) } },
        modifier = modifier
            .height(height)
            .drawBehind {
                val vis = shown()
                if (vis <= 0.01f) return@drawBehind
                val g = gap.toPx()
                // Left and right edge of every segment, from the widths the last layout settled on.
                val lefts = FloatArray(n)
                val rights = FloatArray(n)
                var x = 0f
                for (i in 0 until n) {
                    lefts[i] = x
                    x += widths[i]
                    rights[i] = x
                    x += g
                }
                // The highlight: edges blended between the two segments it is travelling across.
                val p = position().coerceIn(0f, (n - 1).toFloat())
                val i = p.toInt().coerceAtMost(n - 1)
                val j = (i + 1).coerceAtMost(n - 1)
                val f = p - i
                val l = lefts[i] + (lefts[j] - lefts[i]) * f
                val r = rights[i] + (rights[j] - rights[i]) * f
                // A pane of tinted glass rather than a flat chip: the theme colour at ~three
                // quarters so the track shows through, a bright sheen across the top, and a
                // light-catching rim.
                val topLeft = Offset(l, 0f)
                val sz = Size((r - l).coerceAtLeast(0f), size.height)
                val corner = CornerRadius(12.dp.toPx())
                drawRoundRect(indicatorColor.copy(alpha = indicatorColor.alpha * 0.78f * vis), topLeft, sz, corner)
                drawRoundRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.30f * vis),
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.10f * vis),
                        startY = 0f,
                        endY = size.height,
                    ),
                    topLeft, sz, corner,
                )
                drawRoundRect(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.55f * vis),
                        1f to Color.White.copy(alpha = 0.12f * vis),
                        startY = 0f,
                        endY = size.height,
                    ),
                    topLeft, sz, corner,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                )
            },
    ) { measurables, constraints ->
        val g = gap.roundToPx()
        val full = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        val free = (full - g * (n - 1)).coerceAtLeast(0)
        val h = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: height.roundToPx()
        val naturals = FloatArray(n) { measurables[it].maxIntrinsicWidth(h).toFloat() }
        val fitted = fitSegmentWidths(free.toFloat(), weights(), naturals)
        for (i in 0 until n) widths[i] = fitted[i]
        var left = 0
        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(fitted[i].toInt().coerceAtLeast(0), h)) }
        layout(full, h) {
            placeables.forEachIndexed { i, p ->
                p.placeRelative(left, 0)
                left += p.width + g
            }
        }
    }
}
