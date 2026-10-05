package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.flow.first
import com.bloo.uicommon.ReorderColumn
import com.bloo.uicommon.animatePlacement
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.derivedStateOf

/** A friendly label for a pebble/section id. */
internal fun sectionLabel(section: String): String = when (section) {
    "charge" -> "Charge / fuel"
    "climate" -> "Climate"
    "location" -> "Location"
    "trips" -> "Trips"
    "info" -> "Car info"
    "diagnostics" -> "Diagnostics"
    "controls" -> "Lock / climate"
    // "AI" is named here; the generic capitalise would render "Ai".
    "ai" -> "AI summary"
    else -> section.replaceFirstChar { it.uppercase() }
}

/** The reorderable pebble stack for a car.
 *
 * [pinHotspot] true (wide/dual-column [ExpandedCar]) excludes "controls" and any secondary pin,
 * which the caller renders via [HotspotSlot]. The single-column layout passes false so they
 * flow through this list in their [DEFAULT_SECTIONS] position. */
@Composable
internal fun PebbleList(
    v: Vehicle,
    state: State<UiState>,
    vm: AppViewModel,
    exclude: Set<String> = emptySet(),
    pinHotspot: Boolean = true,
    /** Forwarded to the "summary" hero pebble only; see [HeroHeader]'s `expandAction`. */
    onExpand: (() -> Unit)? = null,
) {
    // One derived computation, not `state.value` read in this body: that would subscribe the whole
    // stack to every UiState emission. As derived state it only invalidates when the resulting
    // LIST differs. The car's own section order is derived separately for the reorder handler.
    val allSections by remember(v.vin) { derivedStateOf { state.value.sectionsFor(v) } }
    val sections by remember(v.vin, exclude, pinHotspot) {
        derivedStateOf {
            val sel = state.value
            val hasBattery = sel.hasBattery(v)
            // Exclude hotspot-pinned pebbles, but only when the caller renders them separately ([pinHotspot]).
            val pinnedPebbles = if (pinHotspot) sel.hotspotFor(v.vin) else emptyList()
            val allExclude = exclude + pinnedPebbles
            allSections.filter {
                it !in allExclude && sel.isSectionAvailable(v, it)
            }
        }
    }
    val hotDrag = LocalHotSeatDrag.current
    // PERF: composing all pebbles eagerly on the fling-settle frame is the biggest car-swipe cost.
    // Compose the hero + first EAGER_PEBBLES sections now (above the fold), stub the rest with a
    // collapsed-height placeholder, and fill them one per frame so several cars sharing a page
    // (foldables) don't all fill in the same frame. Keyed on VIN so a recomposed page re-defers cheaply.
    // `items` stays the FULL list so ReorderColumn's per-item machinery exists from frame one;
    // only the body inside the content lambda is deferred.
    // Snapshot SET of filled non-eager sections, read through a per-section derived boolean:
    // a shared counter would invalidate and recompose every pebble on each tick.
    val filledSections = remember(v.vin) { mutableStateSetOf<String>() }
    LaunchedEffect(v.vin, sections.size) {
        // One per frame, in list order, first-below-the-fold first.
        sections.drop(EAGER_PEBBLES).forEach { section ->
            withFrameNanos { }
            filledSections.add(section)
        }
    }
    val eager = remember(sections) { sections.take(EAGER_PEBBLES).toSet() }
    ReorderColumn(
        items = sections,
        keyOf = { it },
        onReorder = { newVisible ->
            // Merge reordered visible items back into the full order so excluded ones keep their slots.
            val visibleSet = sections.toSet()
            val full = (allSections + com.bloo.bluelink.data.DEFAULT_SECTIONS).distinct()
            val queue = ArrayDeque(newVisible)
            val merged = full.map { s ->
                if (s in visibleSet && queue.isNotEmpty()) queue.removeFirst() else s
            }
            vm.setSectionOrder(v, merged)
        },
        // In the dual-column view, dragging a pebble onto the hot-spot slot pins it.
        onDragMove = hotDrag?.let { d ->
            { key, pointer -> d.section = key as String; d.pointer = pointer }
        },
        onDragRelease = hotDrag?.let { d ->
            { key ->
                val pin = d.overSlot
                d.section = null
                if (pin) { vm.setHotspot(v, key as String); true } else false
            }
        },
        staggerInOnColdStart = true,
        introKey = v.vin,
    ) { section, itemDragHandle, _ ->
        // Each item watches only its own readiness, so filling one pebble composes one pebble.
        val ready by remember(section) {
            derivedStateOf { section in eager || section in filledSections }
        }
        if (ready) {
            SinglePebble(section, v, state, vm, itemDragHandle, onExpand = onExpand)
        } else {
            // Off-screen placeholder: reserves ~collapsed pebble height (no list jump) and carries the drag
            // handle so ReorderColumn's item is fully formed. heightIn(min), not fixed: a header at large
            // accessibility font is taller than ControlHeight and a hard 76dp clipped it.
            Box(Modifier.fillMaxWidth().heightIn(min = PebbleHeaderHeight).then(itemDragHandle))
        }
    }
}

/** How many pebbles from the top (incl. the hero summary) compose eagerly; the rest fill in
 *  later, off the swipe. 3 covers everything above the fold on a phone. */
internal const val EAGER_PEBBLES = 3

/**
 * Renders one pebble by section name (used by the list and the hot spot).
 *
 * Pebbles take the WHOLE [UiState], whose equality fails on ANY field change, so each branch
 * hands its pebble a [KeyedSlice]/[stateSlice] keyed on exactly the fields it reads (including
 * what helpers like `statusFor`/`isPending` read). A missed key is a stale-UI bug.
 */
/** A UiState slice whose identity is the fields the row reads. Slices with equal [key] compare
 *  equal, so Compose skips a pebble when none of its fields moved. */
internal class KeyedSlice(val state: UiState, private val key: Any?) {
    override fun equals(other: Any?): Boolean = other is KeyedSlice && key == other.key
    override fun hashCode(): Int = key?.hashCode() ?: 0
}

/** Memoized slice of [state] keyed on exactly what the row reads (see [KeyedSlice]).
 *
 *  [keys] builds the key from the passed [UiState] so the read happens here, not in the caller's
 *  scope (which would subscribe it to every emission). [contextKey] covers anything else the
 *  lambda closes over (the car). */
@Composable
internal fun stateSlice(state: State<UiState>, contextKey: Any?, keys: (UiState) -> Any?): UiState {
    val slice by remember(state, contextKey) {
        derivedStateOf {
            val s = state.value
            KeyedSlice(s, keys(s))
        }
    }
    return slice.state
}

@Composable
internal fun SinglePebble(section: String, v: Vehicle, state: State<UiState>, vm: AppViewModel, modifier: Modifier, onExpand: (() -> Unit)? = null) {
    // Narrow derived read of this car's status; `state.value` here would subscribe every pebble
    // to every UiState emission.
    val status by remember(v.vin) { derivedStateOf { state.value.statuses[v.vin] } }
    val metric = LocalAppearance.current.metricDistance
    when (section) {
        "summary" -> {
            val heroState = stateSlice(state, v) { s ->
                listOf(
                    s.statuses[v.vin], s.imageUrls[v.vin], s.hasBattery(v), s.hasFuel(v),
                    s.locations[v.vin], s.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
                )
            }
            HeroHeader(
                v, status, heroState.imageUrls[v.vin], heroState.hasBattery(v), heroState.hasFuel(v), vm,
                modifier = modifier,
                drivingLabel = heroState.drivingLabel(v), metric = metric,
                photoExpanded = heroState.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
                expandAction = onExpand?.let {
                    PebbleHeaderAction(label = "Expand to full screen", icon = Icons.Filled.Fullscreen, onClick = it)
                },
            )
        }
        "update" -> {
            val updateState = stateSlice(state, v) { s ->
                listOf(
                    s.updateAvailable, s.updateTileDismissed, s.shizukuAvailable,
                    s.updateInstalling, s.updateDownloading, s.updateApkReady,
                )
            }
            UpdateAvailableTile(updateState, vm, modifier)
        }
        "controls" -> {
            val controlsState = stateSlice(state, v) { s ->
                listOf(s.statuses[v.vin], s.isPending(v.vin, "doors"), s.isPending(v.vin, "hornLights"))
            }
            ControlsPebble(v, controlsState, vm, modifier)
        }
        "climate" -> {
            val seats by remember(v.vin) { derivedStateOf { state.value.seatConfigFor(v) } }
            val climateState = stateSlice(state, v) { s ->
                listOf(
                    s.statuses[v.vin], s.seatConfigFor(v), s.isPending(v.vin, "climate"), s.climatePresets[v.vin],
                    s.climateSync[v.vin], s.locations[v.vin], s.carWeather[v.vin],
                    s.homeWeather, s.settingsMode, s.isPebbleExpanded(v.vin, "climate"),
                    s.defaultClimatePresets[v.vin],
                )
            }
            ClimatePebble(v, status, seats, climateState, vm, modifier)
        }
        "charge" -> {
            val hasBattery by remember(v.vin) { derivedStateOf { state.value.hasBattery(v) } }
            if (hasBattery) {
                val enabled by remember { derivedStateOf { !state.value.loading } }
                val chargeState = stateSlice(state, v) { s ->
                    listOf(
                        s.statuses[v.vin], !s.loading, s.isPending(v.vin, "charge"), s.isPending(v.vin, "chargeLimit"),
                        s.hasBattery(v), s.hasFuel(v), s.locations[v.vin],
                        s.isPebbleExpanded(v.vin, "charge"),
                    )
                }
                ChargePebble(v, status, enabled, chargeState, vm, modifier)
            } else {
                val fuelState = stateSlice(state, v) { s ->
                    listOf(s.statuses[v.vin], s.refreshing, s.isPebbleExpanded(v.vin, "charge"))
                }
                FuelPebble(v, status, fuelState, vm, modifier)
            }
        }
        "location" -> {
            val locationState = stateSlice(state, v) { s ->
                listOf(
                    s.locations[v.vin], s.placeNames[v.vin], s.isPending(v.vin, "locate"),
                    s.carWeather[v.vin], s.deviceLocation, s.isPebbleExpanded(v.vin, "location"),
                )
            }
            LocationPebble(v, locationState, vm, modifier)
        }
        // Trip history rides on the EV trip-details endpoint, so EVs only.
        "trips" -> {
            val tripsState = stateSlice(state, v) { s ->
                listOf(s.trips[v.vin], s.isPending(v.vin, "trips"), s.isPebbleExpanded(v.vin, "trips"))
            }
            TripsPebble(v, tripsState, vm, modifier)
        }
        "info" -> {
            val infoState = stateSlice(state, v) { s ->
                listOf(
                    s.statuses[v.vin], s.locations[v.vin], s.licensePlates[v.vin], s.lastServiceMiles[v.vin],
                    s.serviceIntervalMiles[v.vin], s.refreshing, s.hasBattery(v),
                    s.placeNames[v.vin], s.fetchedAt(v), s.isPebbleExpanded(v.vin, "info"),
                )
            }
            InfoPebble(v, status, infoState, vm, modifier)
        }
        "diagnostics" -> {
            val diagnosticsState = stateSlice(state, v) { s ->
                listOf(s.statuses[v.vin], s.hasBattery(v), s.isPebbleExpanded(v.vin, "diagnostics"))
            }
            DiagnosticsPebble(v, status, diagnosticsState, vm, modifier)
        }
        "ai" -> {
            val aiState = stateSlice(state, v) { s ->
                listOf(v.vin in s.aiBusy, s.aiSummaries[v.vin], s.isPebbleExpanded(v.vin, "ai"))
            }
            AiPebble(v, aiState, vm, modifier)
        }
        else -> Spacer(Modifier.fillMaxWidth())
    }
}
