@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import kotlin.math.abs
import com.bloo.uicommon.ReorderColumn
import androidx.compose.runtime.derivedStateOf

/**
 * The dual-column "hot spot": slots under the car-info column for pinned pebbles.
 * Slot 1: Primary pebble (defaulting to "controls" with lights/horn, but removable)
 * Slot 2: Secondary user-selectable slot where they can pin additional pebbles
 */
@Composable
internal fun HotspotSlot(
    v: Vehicle,
    hotspots: List<String>,
    /**
     * State SOURCE. It was already immediately re-wrapped into a rememberUpdatedState here,
     * so nothing downstream ever wanted the snapshot -- but taking the snapshot meant the
     * CALLER had to read state.value in its own body, subscribing the whole dual-column view
     * to every UiState emission. Reading it here confines that to this slot.
     */
    stateSource: State<UiState>,
    vm: AppViewModel,
) {
    // Derived, not `val state = stateSource.value`: same reason as PebbleList's own — a body
    // read subscribes this whole dual-column view to every UiState emission. The list compare
    // below then makes an unrelated emission free.
    val allAvailable by remember(v.vin) {
        derivedStateOf {
            val state = stateSource.value
            state.sectionsFor(v).filter {
                it != "summary" && state.isSectionAvailable(v, it)
            }
        }
    }
    val haptics = LocalHaptics.current
    val hotDrag = LocalHotSeatDrag.current
    val hovered = hotDrag?.overSlot == true

    // Primary slot pebble (defaults to "controls" with lights/horn)
    val primaryPebble = hotspots.firstOrNull() ?: "controls"

    // Secondary slot pebble (if any)
    val secondaryPebble = hotspots.getOrNull(1)

    // Available pebbles not yet pinned
    val unpinned = remember(primaryPebble, secondaryPebble, allAvailable) {
        allAvailable.filter { it != primaryPebble && it != secondaryPebble }
    }

    // 12dp between the two slots, the SAME gap every other pair of pebbles in this view sits
    // at (ReorderColumn's own default spacing for the pebble list, and ExpandedCar's
    // `spacedBy(12.dp)` for the column this slot lives in). It was 2.dp, which is the one
    // spacing in the multi-column layout that made the pinned pebbles read as a stuck-together
    // pair of a different kind from the cards around them -- part of the same report that the
    // controls pebble looks unlike every other pebble there. The INNER column below keeps its
    // own 2dp: that gap is between the secondary slot's small pin/Remove label and the pebble
    // that label belongs to, where tight is the point.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapGroup)) {
        // PRIMARY SLOT (Top) - Always "controls" (lights/horn), permanently pinned
        // No removal option - this slot is hardcoded and locked
        CompositionLocalProvider(LocalForceExpanded provides true) {
            SinglePebble(primaryPebble, v, stateSource, vm, Modifier)
        }

        // SECONDARY SLOT (Bottom) - User-selectable
        if (secondaryPebble != null) {
            // Secondary slot is occupied - show the pebble with removal option
            var lifted by remember(secondaryPebble) { mutableStateOf(false) }
            var dragY by remember(secondaryPebble) { mutableFloatStateOf(0f) }
            val lift by animateFloatAsState(if (lifted) 1.03f else 1f, label = "unpinLift")

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    // No fixed 32dp height any more: the standard MorphTextButton beside this
                    // label is a 48dp target (ButtonTargetHeight), and clamping the row to 32dp
                    // clipped it -- the reported "janky button when you go to remove the pinned
                    // one", since its press morph grew past the row it was trapped in. The row
                    // now sizes to the button and the label centres against it.
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (lifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (lifted) "Release to unpin" else sectionLabel(secondaryPebble),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MorphTextButton("Remove", onClick = { vm.setHotspot(v, secondaryPebble) })
                }
                CompositionLocalProvider(LocalForceExpanded provides true) {
                    Box(
                        Modifier
                            .graphicsLayer { scaleX = lift; scaleY = lift }
                            .pointerInput(secondaryPebble) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragY = 0f; lifted = true; haptics?.tick() },
                                    onDrag = { change, amt -> change.consume(); dragY += abs(amt.x) + abs(amt.y) },
                                    onDragEnd = {
                                        lifted = false
                                        if (dragY > 56f) { haptics?.heavy(); vm.setHotspot(v, secondaryPebble) }
                                    },
                                    onDragCancel = { lifted = false },
                                )
                            },
                    ) {
                        SinglePebble(secondaryPebble, v, stateSource, vm, Modifier)
                    }
                }
            }
        } else {
            // Secondary slot is empty. Dragging a pebble here -- from the reorderable list in
            // the other column -- is what pins it (see PebbleList's onDragRelease); this
            // surface is just that drop target's resting/hover affordance. It used to ALSO
            // open a DropdownMenu listing every unpinned pebble on tap; that pop-up is gone at
            // the user's request, so a pebble now reaches this slot only by drag-and-drop.
            if (unpinned.isNotEmpty()) {
                Box(
                    Modifier.onGloballyPositioned {
                        hotDrag?.let { d -> d.slotTopLeft = it.localToWindow(Offset.Zero); d.slotSize = it.size }
                    },
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(PebbleCornerCollapsed),
                        color = if (hovered) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (hovered) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 12.dp),
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
}
