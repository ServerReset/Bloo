package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How far right the pinned pebble must be dragged before releasing unpins it. */
private val UnpinTravel = 88.dp

/** How long a collapsed pin stays collapsed in the hot seat before it opens as a reveal. */
private const val PinRevealDelayMs = 220L

/**
 * The spring an unpinned pebble flies back into its stack slot on. Mirrors the ReorderColumn's own
 * drop-flight spec, so pinning and unpinning are the same motion in opposite directions.
 */
private val UnpinFlightSpec = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/**
 * The car-info column's "hot seat": the controls pebble pinned under the hero, plus the empty slot a
 * second pebble can be dragged into.
 *
 * The pinned pebbles themselves are NOT drawn here. The pebble column draws OVER the hero column, so a
 * pebble living here would slide behind the stack the moment it was dragged out and then reappear.
 * Instead the second slot reserves its space and reports its position, and [HotSeatOverlay] (a layer
 * above BOTH columns) draws the one pinned pebble into it. That keeps the pinned pebble a single node
 * that is never duplicated and never occluded.
 *
 * There is no remove button: a pinned pebble is unpinned by picking it up (long-press) and dragging it
 * back to the right, toward the stack. While a pebble is dragged over the seat from the stack the seat
 * leans toward the finger, a soft tick fires on entry, and the empty drop zone fills in.
 *
 * A pinned pebble keeps its OWN collapsed/expanded state: one that was collapsed lands here collapsed,
 * opens a beat later as a small reveal, and stays open; unpinned back into the stack it collapses again
 * on the way out, exactly as it was (pinning never touches the stored state).
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
        // that space's position, so the drop target is right and HotSeatOverlay knows where to draw.
        // The reservation itself stays put while the seat is expanded (a pin in flight needs a stable
        // target); only the drop-zone CONTENT retreats, and on the same beat as the flight.
        AnimatedVisibility(
            visible = expanded && (secondaryPebble != null || unpinned.isNotEmpty()),
            enter = EnterTransition.None,
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
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
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
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
 * The one pinned (secondary) pebble, drawn in a layer ABOVE both columns by [HotSeatOverlay]. It is
 * the only node for that pebble, so it is never duplicated and never slides behind the stack while it
 * is dragged out. Long-press and drag it right to unpin: it collapses back to the state it came from,
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
    // The stored state never changes: a collapsed pebble opens a beat after it lands, and collapses
    // again on the way home.
    val initiallyExpanded = remember(v.vin, section) { stateSource.value.isPebbleExpanded(v.vin, section) }
    var revealed by remember(v.vin, section) { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(v.vin, section) {
        if (!initiallyExpanded) {
            delay(PinRevealDelayMs)
            revealed = true
        }
    }
    var dragging by remember(section) { mutableStateOf(false) }
    val dragX = remember(section) { mutableFloatStateOf(0f) }
    val dragY = remember(section) { mutableFloatStateOf(0f) }
    val settleX = remember(section) { Animatable(0f) }
    val settleY = remember(section) { Animatable(0f) }
    val x by remember { derivedStateOf { if (dragging) dragX.floatValue else settleX.value } }
    val y by remember { derivedStateOf { if (dragging) dragY.floatValue else settleY.value } }
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
            if (!initiallyExpanded) revealed = false
            val from = hotDrag?.slotTopLeft
            val to = hotDrag?.stackSlot?.get(section)
            scope.launch {
                settleX.snapTo(dx)
                settleY.snapTo(dy)
                if (from != null && to != null) {
                    launch { settleX.animateTo(to.x - from.x, UnpinFlightSpec) }
                    settleY.animateTo(to.y - from.y, UnpinFlightSpec)
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
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = x
                translationY = y
                scaleX = pickScale
                scaleY = pickScale
                // Scale about the TOP-LEFT so the pick-up lift easing back during the unpin flight
                // cannot shift the corner we aim at the stack slot.
                transformOrigin = TransformOrigin(0f, 0f)
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
        CompositionLocalProvider(LocalForceExpanded provides revealed) {
            SinglePebble(section, v, stateSource, vm, Modifier)
        }
    }
}

/**
 * The pinned (secondary) pebble's layer: a child of the full-screen page's own Box, drawn AFTER the
 * scrolling content, so it sits above BOTH columns. It converts the hot-seat slot's window position
 * into this layer's coordinates and draws [PinnedPebble] into it. Nothing is drawn here when there is
 * no second pin.
 */
@Composable
internal fun HotSeatOverlay(
    v: Vehicle,
    stateSource: State<UiState>,
    vm: AppViewModel,
    hotspots: List<String>,
    hotDrag: HotSeatDrag,
    /** This layer's own origin in window coords, to convert the slot's window position. */
    origin: Offset,
    /** Placement in the page Box (TopStart), so the offset below is measured from the page origin. */
    modifier: Modifier = Modifier,
) {
    val secondary = hotspots.getOrNull(1) ?: return
    if (hotDrag.slotSize.width == 0) return
    Box(
        modifier
            .offset {
                IntOffset(
                    (hotDrag.slotTopLeft.x - origin.x).roundToInt(),
                    (hotDrag.slotTopLeft.y - origin.y).roundToInt(),
                )
            }
            .width(with(LocalDensity.current) { hotDrag.slotSize.width.toDp() }),
    ) {
        PinnedPebble(v, stateSource, vm, secondary, hotDrag)
    }
}

/** The empty second slot: the drop target's resting / hover affordance. */
@Composable
private fun EmptyDropZone(hovered: Boolean) {
    val fill by animateColorAsState(
        if (hovered) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "hotseatFill",
    )
    val tone by animateColorAsState(
        if (hovered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "hotseatTone",
    )
    val outline by animateColorAsState(
        if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        label = "hotseatOutline",
    )
    val lift by animateFloatAsState(if (hovered) 1f else 0f, label = "hotseatLift")
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = PebbleHeaderHeight).graphicsLayer {
            val s = 1f + 0.025f * lift
            scaleX = s
            scaleY = s
        },
        shape = RoundedCornerShape(PebbleCornerCollapsed),
        color = fill,
        contentColor = tone,
        border = BorderStroke(1.dp, outline),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = GapPage, horizontal = GapGroup),
            contentAlignment = Alignment.Center,
        ) {
            MorphButtonLabel(
                Icons.Filled.PushPin,
                if (hovered) "Release to pin" else "Drag a pebble here",
                pending = false,
            )
        }
    }
}
