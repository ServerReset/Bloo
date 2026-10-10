package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far right the pinned pebble must be dragged before releasing unpins it. */
private val UnpinTravel = 88.dp

/** How long a collapsed pin stays collapsed in the hot seat before it opens as a reveal. */
private const val PinRevealDelayMs = 220L

/**
 * The car-info column's "hot seat": the one or two pebbles pinned under the hero. It is shown only
 * in the full-screen two-column view (the caller gates it on the expand fraction); collapsed, the
 * pebbles are ordinary members of the reorderable stack.
 *
 * There is no remove button: a pinned pebble is unpinned by picking it up (long-press) and dragging
 * it back to the right, toward the stack. While a pebble is dragged over the seat from the stack the
 * seat leans toward the finger, a soft tick fires on entry, and the empty drop zone cross-fades its
 * fill/outline/tone.
 *
 * A pinned pebble keeps its OWN collapsed/expanded state: one that was collapsed lands here
 * collapsed, opens a beat later as a small reveal and stays open; unpinned back into the stack, it is
 * still collapsed, exactly as it was (pinning never touches the stored state).
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
    // No `spacedBy`: a hidden slot (the empty drop zone, or a leaving pin) must leave NOTHING behind,
    // and `spacedBy` still inserts a gap around a zero-height child -- which was the phantom gap that
    // snapped away when the car collapsed back to one column. The drop zone therefore also sits
    // directly under the control pebble, so it grows out from behind it.
    Column(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = seatScale; scaleY = seatScale },
    ) {
        // PRIMARY SLOT: always "controls" (lights/horn), hardcoded, no removal. Force-expanded, no
        // reveal, and NOT a shared element -- sharing it across the reflowing stack jittered it, and
        // it is always present anyway. Drawn above the drop zone (zIndex) so that one slides out from
        // behind it.
        Box(Modifier.zIndex(1f)) {
            CompositionLocalProvider(LocalForceExpanded provides true) {
                SinglePebble(primaryPebble, v, stateSource, vm, Modifier)
            }
        }

        // SECONDARY SLOT: kept rendered through its exit so unpinning has a node to fade away in
        // place (rather than vanishing on the same frame the stack row returns).
        var leavingSecondary by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(secondaryPebble) { if (secondaryPebble != null) leavingSecondary = secondaryPebble }
        AnimatedVisibility(
            visible = secondaryPebble != null,
            // The dropped pebble has ALREADY flown here (see ReorderColumn's onDragEnd), so it must
            // appear INSTANTLY, exactly where it landed -- an entrance animation would fight the
            // flight. The exit (on unpin) fades and shrinks it away.
            enter = EnterTransition.None,
            exit = fadeOut(tween(MotionShort)) + scaleOut(targetScale = 0.97f),
        ) {
            val section = secondaryPebble ?: leavingSecondary
            if (section != null) {
                PinnedPebble(v, stateSource, vm, section, hotDrag, interactive = secondaryPebble != null)
            }
        }

        // EMPTY SECOND SLOT ("drag a pebble here"): the FIRST thing to go when collapsing. It grows
        // out from behind the control pebble (anchored at the top, drawn behind it) and retreats back
        // behind it, on its own, so it never holds up the rest of the collapse. Gated on [expanded]
        // so its exit starts the moment the collapse does -- and on `pendingPin == null` so that a
        // dropped pebble's flight and this zone's retreat are the SAME beat: the zone is already
        // leaving as the pebble arrives, instead of lingering under it once it has landed.
        AnimatedVisibility(
            visible = expanded && secondaryPebble == null && unpinned.isNotEmpty() && hotDrag?.pendingPin == null,
            // Grows out from behind the control pebble (from the top, drawn behind it), then BOUNCES
            // out: the height spring is under-damped, so it overshoots past its resting height and
            // settles back.
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
            ),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
            ),
        ) {
            EmptyDropZone(hotDrag, hovered)
        }
    }
}

/**
 * Renders a pinned pebble while respecting its own state: it is drawn in whatever state it already
 * has, and a collapsed one opens a beat after it lands (a small reveal), then stays open. The stored
 * state is never modified, so unpinning returns it to the stack exactly as it was.
 */
@Composable
private fun RevealPebble(
    section: String,
    v: Vehicle,
    stateSource: State<UiState>,
    vm: AppViewModel,
) {
    val initiallyExpanded = remember(v.vin, section) { stateSource.value.isPebbleExpanded(v.vin, section) }
    var revealed by remember(v.vin, section) { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(v.vin, section) {
        if (!initiallyExpanded) {
            delay(PinRevealDelayMs)
            revealed = true
        }
    }
    CompositionLocalProvider(LocalForceExpanded provides revealed) {
        SinglePebble(section, v, stateSource, vm, Modifier)
    }
}

/** The pinned secondary pebble: pick it up and drag it right to unpin. */
@Composable
private fun PinnedPebble(
    v: Vehicle,
    stateSource: State<UiState>,
    vm: AppViewModel,
    secondaryPebble: String,
    hotDrag: HotSeatDrag?,
    interactive: Boolean = true,
) {
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    val unpinPx = with(LocalDensity.current) { UnpinTravel.toPx() }
    var dragging by remember(secondaryPebble) { mutableStateOf(false) }
    val dragX = remember(secondaryPebble) { mutableFloatStateOf(0f) }
    val dragY = remember(secondaryPebble) { mutableFloatStateOf(0f) }
    val settleX = remember(secondaryPebble) { Animatable(0f) }
    val settleY = remember(secondaryPebble) { Animatable(0f) }
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
        scope.launch {
            settleX.snapTo(dx)
            settleY.snapTo(dy)
            if (dx > unpinPx) {
                haptics?.heavy()
                // Toggle: the same section is already the secondary pin, so this unpins it. It fades
                // away here while the stack row fades back in, i.e. it returns to the stack.
                vm.setHotspot(v, secondaryPebble)
            } else {
                launch { settleX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                settleY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                hotDrag?.let { d -> d.slotTopLeft = it.localToWindow(Offset.Zero); d.slotSize = it.size }
            }
            .graphicsLayer {
                translationX = x
                translationY = y
                scaleX = pickScale
                scaleY = pickScale
            }
            .pointerInput(secondaryPebble, interactive) {
                // Not interactive while it is animating out (unpinned): it must not react to touch.
                if (!interactive) return@pointerInput
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
        // No entrance animation: a dropped pebble has already flown here (see ReorderColumn's
        // onDragEnd), so this must appear exactly where it landed.
        RevealPebble(secondaryPebble, v, stateSource, vm)
    }
}

/** The empty second slot: the drop target's resting / hover affordance. */
@Composable
private fun EmptyDropZone(hotDrag: HotSeatDrag?, hovered: Boolean) {
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
    Box(
        Modifier.fillMaxWidth().onGloballyPositioned {
            hotDrag?.let { d -> d.slotTopLeft = it.localToWindow(Offset.Zero); d.slotSize = it.size }
        },
    ) {
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
}
