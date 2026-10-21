package com.bloo.bluelink.ui

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.LocalContentColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.launch
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapPage
import com.bloo.uicommon.LocalHaptics
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke

/** How far right the pinned pebble must be dragged before releasing unpins it. */
private val UnpinTravel = 88.dp

/**
 * The spring an unpinned pebble flies back into its stack slot on. Mirrors the ReorderColumn's own
 * drop-flight spec, so pinning and unpinning are the same motion in opposite directions.
 */
private val UnpinFlightProgressSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * The car-info column's "hot seat": the controls pebble pinned under the hero, plus the empty slot a
 * second pebble can be dragged into.
 *
 * The second slot holds the pinned pebble itself, in the column: it scrolls and turns with the page, and a vertical drag on it
 * scrolls the column. While it is dragged out toward the stack the whole column is drawn above the stack (see
 * [HotSeatDrag.pinnedActive]), so it stays in front instead of sliding behind. The pin target is the whole column under the hero
 * and the controls ([HotSeatDrag.zoneBottom]), not just the empty slot.
 *
 * There is no remove button: a pinned pebble is unpinned by picking it up (long-press) and dragging it
 * back to the right, toward the stack. While a pebble is dragged over the seat from the stack the seat
 * leans toward the finger, a soft tick fires on entry, and the empty drop zone fills in.
 *
 * A pinned pebble is always expanded in the hot seat, on every page: pinning opens it and it stays open. Unpinned back
 * into the stack it folds up again on the way out if it was folded there (pinning never touches the stored state).
 */
@Composable
internal fun HotspotSlot(
    v: Vehicle,
    hotspots: List<String>,
    /** State SOURCE. Reading it here confines that to this slot. */
    stateSource: State<UiState>,
    vm: AppViewModel,
    /** False while the full-screen view is collapsing: drives the empty drop zone's own exit. */
    expanded: Boolean,
) {
    // Derived, not `val state = stateSource.value`: a body read subscribes this whole dual-column
    // view to every UiState emission. The list compare below then makes an unrelated emission free.
    val allAvailable by remember(v.vin) {
        derivedStateOf {
            val state = stateSource.value
            state.sectionsFor(v).filter { it != "summary" && state.isSectionAvailable(v, it) }
        }
    }
    val hotDrag = LocalHotSeatDrag.current
    // Derived, so the per-move pointer updates during a drag recompute this cheaply but only
    // RECOMPOSE the seat when the boolean actually flips -- reading `overSlot` directly in the body
    // would re-run the whole seat (control pebble included) on every pointer event.
    val hovered by remember(hotDrag) { derivedStateOf { hotDrag?.overSlot == true } }
    // A soft tick the moment a dragged pebble first crosses into the seat, so the drop is felt as
    // well as seen (the seat leans and the empty zone fills in the same beat).
    val haptics = LocalHaptics.current
    LaunchedEffect(hovered) { if (hovered) haptics?.tick() }

    // Primary slot pebble (defaults to "controls" with lights/horn); permanently pinned.
    val primaryPebble = hotspots.firstOrNull() ?: "controls"
    // Secondary slot pebble (if any).
    val secondaryPebble = hotspots.getOrNull(1)
    // Available pebbles not yet pinned.
    val unpinned = remember(primaryPebble, secondaryPebble, allAvailable) {
        allAvailable.filter { it != primaryPebble && it != secondaryPebble }
    }

    // The seat leans toward the finger while a dragged pebble is over it, then springs back.
    val seatScale by animateFloatAsState(
        targetValue = if (hovered) 1.015f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "hotSeatScale",
    )
    // No `spacedBy`: a hidden slot must leave NOTHING behind, and `spacedBy` still inserts a gap
    // around a zero-height child -- which was the phantom gap that snapped away when the car
    // collapsed back to one column. Each slot's gap therefore lives INSIDE its own content, so it
    // appears and retreats with the slot.
    Column(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = seatScale; scaleY = seatScale },
    ) {
        // PRIMARY SLOT: always "controls" (lights/horn), hardcoded, no removal. Force-expanded.
        // Drawn above the second slot (zIndex) so the drop zone slides out from behind it.
        Box(Modifier.zIndex(1f)) {
            CompositionLocalProvider(LocalForceExpanded provides true) {
                SinglePebble(primaryPebble, v, stateSource, vm, Modifier)
            }
        }

        // SECOND SLOT: a reservation. It holds the space a second pinned pebble occupies and reports
        // that space's position, so the drop target and the flight home are right.
        // The reservation itself stays put while the seat is expanded (a pin in flight needs a stable
        // target); only the drop-zone CONTENT retreats, and on the same beat as the flight.
        AnimatedVisibility(
            visible = expanded && (secondaryPebble != null || unpinned.isNotEmpty()),
            enter = EnterTransition.None,
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = spring(dampingRatio = GentleDamping, stiffness = Spring.StiffnessMedium),
            ),
        ) {
            Column {
                // The same gap the stack uses between pebbles, so the hot seat reads as the same
                // column of pebbles -- not two cards jammed together.
                Spacer(Modifier.height(GapGroup))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = PebbleHeaderHeight)
                        .onGloballyPositioned {
                            hotDrag?.let { d ->
                                d.slotTopLeft = it.localToWindow(Offset.Zero)
                                d.slotSize = it.size
                            }
                        },
                ) {
                    // The pinned pebble itself, IN the column (not a layer above it): it scrolls with the page, turns with the
                    // page when the pager moves, and a vertical drag on it scrolls the column like any other part of it. Only
                    // while it is dragged out does the column draw above the stack (see HotSeatDrag.pinnedActive).
                    if (secondaryPebble != null) PinnedPebble(v, stateSource, vm, secondaryPebble, hotDrag)
                    androidx.compose.animation.AnimatedVisibility(
                        // Retreats the moment a released pebble starts its flight in (pendingPin), so
                        // the zone is already leaving as the pebble arrives rather than lingering under
                        // it once it has landed.
                        visible = secondaryPebble == null && hotDrag?.pendingPin == null,
                        enter = expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
                        ),
                        exit = shrinkVertically(
                            shrinkTowards = Alignment.Top,
                            animationSpec = spring(dampingRatio = GentleDamping, stiffness = Spring.StiffnessMedium),
                        ),
                    ) {
                        EmptyDropZone(hovered)
                    }
                }
            }
        }
    }
}

/**
 * The one pinned (secondary) pebble, in the hot seat's second slot. It is the only node for that pebble, so it is never
 * duplicated, and the column is drawn above the stack while it is dragged out, so it never slides behind it. Long-press and drag it right to unpin: it collapses back to the state it came from,
 * flies to its own slot in the stack, and only then commits the unpin.
 */
@Composable
internal fun PinnedPebble(
    v: Vehicle,
    stateSource: State<UiState>,
    vm: AppViewModel,
    section: String,
    hotDrag: HotSeatDrag?,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val unpinPx = with(LocalDensity.current) { UnpinTravel.toPx() }
    // A pinned pebble is always open: it lives in the hot seat, so it stays expanded on every page the wearer swipes to. The
    // stored state (what it was in the stack) is only read to decide, on the way home, whether it folds back up.
    val storedExpanded = remember(v.vin, section) { stateSource.value.isPebbleExpanded(v.vin, section) }
    var revealed by remember(v.vin, section) { mutableStateOf(true) }
    var dragging by remember(section) { mutableStateOf(false) }
    val dragX = remember(section) { mutableFloatStateOf(0f) }
    val dragY = remember(section) { mutableFloatStateOf(0f) }
    val settleX = remember(section) { Animatable(0f) }
    val settleY = remember(section) { Animatable(0f) }
    // 0..1 flight progress toward the stack slot. The target is read LIVE every frame, so a column
    // that scrolls or reflows mid-flight cannot leave the pebble landing at a stale position.
    val flight = remember(section) { Animatable(0f) }
    var flightFrom by remember(section) { mutableStateOf(Offset.Zero) }
    var flying by remember(section) { mutableStateOf(false) }
    // One resolver for both axes: dragging follows the finger, flying lerps to the live slot, else settles.
    fun axis(drag: Float, settle: Float, from: Float, pick: (Offset) -> Float): Float {
        if (dragging) return drag
        val to = hotDrag?.stackSlot?.get(section)
        val seat = hotDrag?.slotTopLeft
        if (!flying || to == null || seat == null) return settle
        return from + (pick(to) - pick(seat) - from) * flight.value
    }
    val x by remember { derivedStateOf { axis(dragX.floatValue, settleX.value, flightFrom.x) { it.x } } }
    val y by remember { derivedStateOf { axis(dragY.floatValue, settleY.value, flightFrom.y) { it.y } } }
    // Grows a little as you pick it up, and more once you have dragged it far enough that releasing
    // WILL unpin it -- so the drop is not a surprise.
    val pastUnpin = dragging && x > unpinPx
    val pickScale by animateFloatAsState(
        when {
            !dragging -> 1f
            pastUnpin -> 1.08f
            else -> 1.04f
        },
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "pickScale",
    )
    fun release() {
        dragging = false
        val dx = dragX.floatValue
        val dy = dragY.floatValue
        if (dx > unpinPx) {
            haptics?.heavy()
            // Collapse back to the state it came from, fly to its own slot in the stack, and only
            // THEN commit the unpin -- so it travels to the right place instead of fading where it
            // was dropped. The target is the row's slot, registered even while it is collapsed.
            if (!storedExpanded) revealed = false
            val from = hotDrag?.slotTopLeft
            val to = hotDrag?.stackSlot?.get(section)
            scope.launch {
                settleX.snapTo(dx)
                settleY.snapTo(dy)
                if (from != null && to != null) {
                    flightFrom = Offset(dx, dy)
                    flight.snapTo(0f)
                    flying = true
                    hotDrag.returning = section
                    flight.animateTo(1f, UnpinFlightProgressSpec)
                }
                vm.setHotspot(v, section)
            }
        } else {
            scope.launch {
                settleX.snapTo(dx)
                settleY.snapTo(dy)
                launch { settleX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                settleY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    // The column is drawn above the stack for as long as the pebble is dragged or flying, and its own drag state is the cue.
    LaunchedEffect(dragging, flying) { hotDrag?.pinnedActive = dragging || flying }
    DisposableEffect(hotDrag) { onDispose { hotDrag?.pinnedActive = false } }
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = x
                translationY = y
                scaleX = pickScale
                scaleY = pickScale
                // Scale about the CENTRE so the pick-up grows symmetrically instead of the top-left
                // pinning the left/top edge and pushing the right edge out (the same right-pop the
                // stack's reorder had). The unpin flight still lands exactly: pickScale has eased back
                // to 1.0 by the time it arrives, where the pivot no longer displaces anything.
                transformOrigin = TransformOrigin.Center
            }
            .pointerInput(section) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        dragX.floatValue = settleX.value
                        dragY.floatValue = settleY.value
                        haptics?.tick()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX.floatValue += amount.x
                        dragY.floatValue += amount.y
                    },
                    onDragEnd = { release() },
                    onDragCancel = { release() },
                )
            },
    ) {
        CompositionLocalProvider(LocalForceExpanded provides revealed, LocalPebbleFloating provides dragging) {
            SinglePebble(section, v, stateSource, vm, Modifier)
        }
    }
}

/** The empty second slot: a dashed outline that wakes up into a soft primary glow while a pebble hovers it. */
@Composable
private fun EmptyDropZone(hovered: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val glow by animateFloatAsState(if (hovered) 1f else 0f, spring(dampingRatio = SettleDamping, stiffness = Spring.StiffnessMediumLow), label = "hotseatGlow")
    val tone = lerp(scheme.onSurfaceVariant, scheme.primary, glow)
    val outline = lerp(scheme.outline.copy(alpha = 0.5f), scheme.primary, glow)
    val fill = scheme.primary.copy(alpha = 0.14f * glow)
    val shape = RoundedCornerShape(PebbleCornerCollapsed)
    val dash = with(LocalDensity.current) { floatArrayOf(8.dp.toPx(), 6.dp.toPx()) }
    val stroke = with(LocalDensity.current) { (1f + glow).dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PebbleHeaderHeight)
            .graphicsLayer {
                val s = 1f + 0.02f * glow
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(fill)
            .drawBehind {
                val r = size.height.coerceAtMost(PebbleCornerCollapsed.toPx() * 2) / 2f
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(r),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(dash),
                    ),
                )
            }
            .padding(vertical = GapPage, horizontal = GapGroup),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tone) {
            MorphButtonLabel(
                Icons.Filled.PushPin,
                if (hovered) "Release to pin" else "Drag a pebble here",
                pending = false,
            )
        }
    }
}
