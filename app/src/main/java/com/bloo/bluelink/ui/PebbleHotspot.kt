package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
    val hovered = hotDrag?.overSlot == true

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
    Column(
        Modifier.fillMaxWidth().graphicsLayer { scaleX = seatScale; scaleY = seatScale },
        verticalArrangement = Arrangement.spacedBy(GapGroup),
    ) {
        // PRIMARY SLOT: always "controls" (lights/horn), hardcoded, no removal.
        RevealPebble(primaryPebble, v, stateSource, vm)

        if (secondaryPebble != null) {
            PinnedPebble(v, stateSource, vm, secondaryPebble, hotDrag)
        } else if (unpinned.isNotEmpty()) {
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
    val pickScale by animateFloatAsState(
        if (dragging) 1.04f else 1f,
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
                // Toggle: the same section is already the secondary pin, so this unpins it.
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
            .pointerInput(secondaryPebble) {
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
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(MotionShort)) + slideInHorizontally(tween(MotionShort)) { it },
        ) {
            RevealPebble(secondaryPebble, v, stateSource, vm)
        }
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
            modifier = Modifier.fillMaxWidth().graphicsLayer {
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
