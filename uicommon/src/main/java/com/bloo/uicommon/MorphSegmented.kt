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
import androidx.compose.ui.unit.isSpecified
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One option in a [MorphSegmented] control. [icon] is optional. */
data class SegmentOption(val key: String, val label: String, val icon: ImageVector? = null)

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
 * physically leaning toward what you picked. The chosen segment carries the [indicatorColor] fill
 * and a slightly heavier label; the squashed ones shrink their label a touch.
 *
 * Tap a segment to jump there, or drag across the track: the stretch follows your finger, a tick
 * lands each time it crosses into a new segment, and letting go commits the one you are over.
 *
 * Every colour, type style and haptic is passed in, so this module stays neutral to the theme.
 *
 * @param onTick Called when the selection changes (and on each segment a drag crosses).
 * @param indicatorVisible When false no segment reads as selected (unless being dragged). Used to
 *   split ONE choice across two stacked rows, only one of which holds the selection.
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

    // One spring-driven weight per segment: the chosen one stretches, the others squash.
    val ratio = stretchRatio(n)
    val weights = remember(n) { List(n) { Animatable(1f) } }
    LaunchedEffect(visualIndex, n) {
        weights.forEachIndexed { i, w ->
            val target = if (i == visualIndex) ratio else 1f
            scope.launch {
                w.animateTo(target, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    val gap = 4.dp
    Box(
        modifier = modifier.blockPageSwipe().fillMaxWidth().clip(trackShape).background(containerColor)
            .then(if (borderColor != null) Modifier.border(BorderStroke(1.dp, borderColor), trackShape) else Modifier),
    ) {
        var trackWidthPx by remember { mutableStateOf(0f) }
        val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { gap.toPx() }
        /** Which segment sits under x right now, using the live (mid-spring) widths. */
        fun indexAt(x: Float): Int {
            val free = (trackWidthPx - gapPx * (n - 1)).coerceAtLeast(1f)
            val total = weights.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.01f)
            var left = 0f
            for (i in 0 until n) {
                val w = free * weights[i].value / total
                if (x < left + w + gapPx / 2f) return i
                left += w + gapPx
            }
            return n - 1
        }

        SegmentTrack(
            n = n,
            weights = { FloatArray(n) { weights[it].value } },
            gap = gap,
            height = trackHeight,
            onWidth = { trackWidthPx = it },
            // The chosen segment's fill is painted behind the labels at that segment's live size.
            selectedIndex = { visualIndex },
            indicatorColor = indicatorColor,
            segmentShape = segmentShape,
            modifier = Modifier
                .padding(4.dp)
                .pointerInput(n) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        var claimed = false
                        var last = -1
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
                                if (!claimed) {
                                    val dx = abs(change.position.x - down.position.x)
                                    val dy = abs(change.position.y - down.position.y)
                                    when {
                                        dx > slop && dx >= dy -> {
                                            claimed = true
                                            change.consume()
                                            last = indexAt(change.position.x)
                                            dragIndex = last
                                            currentOnTick()
                                        }
                                        dy > slop -> break
                                    }
                                } else if (change.positionChanged()) {
                                    change.consume()
                                    val idx = indexAt(change.position.x)
                                    if (idx != last) {
                                        last = idx
                                        dragIndex = idx
                                        currentOnTick()
                                    }
                                }
                            }
                            if (claimed) {
                                val idx = dragIndex ?: indexAt(down.position.x)
                                pendingIndex = idx
                                val key = currentOptions[idx].key
                                if (key != currentSelectedKey) currentOnSelect(key)
                            }
                        } finally {
                            dragIndex = null
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
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
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
                        style = if (isSelected) {
                            textStyle.copy(color = fg, fontWeight = FontWeight.SemiBold)
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
    onWidth: (Float) -> Unit,
    selectedIndex: () -> Int,
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
                val sel = selectedIndex()
                if (sel !in 0 until n) return@drawBehind
                val g = gap.toPx()
                val w = weights()
                val total = w.sum().coerceAtLeast(0.01f)
                val free = size.width - g * (n - 1)
                var left = 0f
                for (i in 0 until sel) left += free * w[i] / total + g
                val width = free * w[sel] / total
                drawRoundRect(
                    indicatorColor,
                    topLeft = Offset(left, 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                )
            },
    ) { measurables, constraints ->
        val g = gap.roundToPx()
        val full = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
        onWidth(full.toFloat())
        val w = weights()
        val total = w.sum().coerceAtLeast(0.01f)
        val free = (full - g * (n - 1)).coerceAtLeast(0)
        val h = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: height.roundToPx()
        var left = 0
        val placeables = measurables.mapIndexed { i, m ->
            val width = (free * w[i] / total).toInt().coerceAtLeast(0)
            m.measure(Constraints.fixed(width, h))
        }
        layout(full, h) {
            placeables.forEachIndexed { i, p ->
                p.placeRelative(left, 0)
                left += p.width + g
            }
        }
    }
}
