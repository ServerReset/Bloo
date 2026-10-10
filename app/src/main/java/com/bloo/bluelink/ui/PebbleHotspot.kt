package com.bloo.bluelink.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import kotlin.math.abs

/**
 * The car-info column's "hot seat": the one or two pebbles pinned under the hero.
 *
 * Always rendered, so its pinned pebbles never vanish when a wide car collapses back to one column;
 * only the empty "drag a pebble here" affordance is full-screen-only ([expanded]), since a per-car
 * drop target in the grid would read as clutter.
 *
 * The whole seat is spring-driven: it leans 1.5% toward the finger while a dragged pebble hovers it,
 * the drop zone's fill/outline/tone cross-fade, and a pebble pinned or unpinned pops in/out on the
 * shared bounce spring.
 */
@Composable
internal fun HotspotSlot(
    v: Vehicle,
    hotspots: List<String>,
    /** State SOURCE. Reading it here confines that to this slot. */
    stateSource: State<UiState>,
    vm: AppViewModel,
    /** Full-screen view: show the empty drop-zone affordance when the second slot is free. */
    expanded: Boolean = true,
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
    val dragging = hotDrag?.section != null
    val haptics = LocalHaptics.current
    // A soft tick the moment the dragged pebble first crosses into the drop zone.
    LaunchedEffect(hovered) { if (hovered) haptics?.tick() }

    // Primary slot pebble (defaults to "controls" with lights/horn); permanently pinned.
    val primaryPebble = hotspots.firstOrNull() ?: "controls"
    // Secondary slot pebble (if any).
    val secondaryPebble = hotspots.getOrNull(1)
    // Available pebbles not yet pinned.
    val unpinned = remember(primaryPebble, secondaryPebble, allAvailable) {
        allAvailable.filter { it != primaryPebble && it != secondaryPebble }
    }

    // The seat leans toward the finger while a pebble is over it, then springs back.
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
        CompositionLocalProvider(LocalForceExpanded provides true) {
            SinglePebble(primaryPebble, v, stateSource, vm, Modifier)
        }

        val showSecondary = secondaryPebble != null || (unpinned.isNotEmpty() && (expanded || dragging))
        if (showSecondary) {
            HotSeatSecondary(v, stateSource, vm, hotDrag, hovered, secondaryPebble)
        }
    }
}

/** The second hot-seat slot: a pinned pebble (removable) or, in full screen, the empty drop zone. */
@Composable
private fun HotSeatSecondary(
    v: Vehicle,
    stateSource: State<UiState>,
    vm: AppViewModel,
    hotDrag: HotSeatDrag?,
    hovered: Boolean,
    secondaryPebble: String?,
) {
    val haptics = LocalHaptics.current
    // The drop-zone colours, shared by the empty affordance and the hover glow so a pebble dropped
    // over an existing pin reads the same as one dropped into an empty slot.
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
        if (secondaryPebble != null) {
            // Occupied: show the pebble with a long-press-drag-to-unpin affordance.
            var pulled by remember(secondaryPebble) { mutableStateOf(false) }
            var dragDistance by remember(secondaryPebble) { mutableFloatStateOf(0f) }
            val pullScale by animateFloatAsState(
                if (pulled) 1.04f else 1f,
                spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                label = "unpinPull",
            )
            PopVisible(visible = true, sizeAnimated = true) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = GapRow),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (pulled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(GapRow))
                        Text(
                            if (pulled) "Release to unpin" else sectionLabel(secondaryPebble),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pulled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MorphTextButton("Remove", onClick = { vm.setHotspot(v, secondaryPebble) }, emphasis = ButtonEmphasis.Deny)
                    }
                    CompositionLocalProvider(LocalForceExpanded provides true) {
                        Box(
                            Modifier
                                .graphicsLayer { scaleX = pullScale; scaleY = pullScale }
                                .pointerInput(secondaryPebble) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { dragDistance = 0f; pulled = true; haptics?.tick() },
                                        onDrag = { change, amt -> change.consume(); dragDistance += abs(amt.x) + abs(amt.y) },
                                        onDragEnd = {
                                            pulled = false
                                            if (dragDistance > 56f) { haptics?.heavy(); vm.setHotspot(v, secondaryPebble) }
                                        },
                                        onDragCancel = { pulled = false },
                                    )
                                },
                        ) {
                            SinglePebble(secondaryPebble, v, stateSource, vm, Modifier)
                        }
                    }
                }
            }
        } else {
            // Empty (full screen only): the drop target's resting / hover affordance.
            PopVisible(visible = true, sizeAnimated = true) {
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
    }
}
