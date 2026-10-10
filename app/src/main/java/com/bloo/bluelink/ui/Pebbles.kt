package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.onClick
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.ReorderColumn
import kotlinx.coroutines.flow.first

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

/**
 * The reorderable pebble stack for a car. [pinHotspot] defaults to true (exclude "controls" + any
 * secondary pin, on the assumption the caller renders them separately via [HotspotSlot] -- true for
 * the wide/dual-column [ExpandedCar]). [VehicleDetailContent]'s single-column layout has no
 * separate hero-column slot to pin anything into, so it passes false: "controls" and any secondary
 * pin then flow through this list like any other pebble, landing in their normal [DEFAULT_SECTIONS]
 * position (right after "summary"/"update") instead of vanishing from the list with nothing
 * rendering them in their place.
 */
@Composable
internal fun PebbleList(
    v: Vehicle,
    state: State<UiState>,
    vm: AppViewModel,
    exclude: Set<String> = emptySet(),
    pinHotspot: Boolean = true,
    /** Forwarded to the "summary" hero pebble only; see [HeroHeader]'s `expandAction`. */
    onExpand: (() -> Unit)? = null,
    /**
     * When non-null, a pinned pebble is a shared element: it stays in this list collapsed to zero
     * height and the hot seat renders the matching shared node, so it slides between the two.
     */
    sharedScope: SharedTransitionScope? = null,
) {
    // One derived computation, not `state.value` read in this body: that would subscribe the whole
    // stack to every UiState emission. As derived state it only invalidates when the resulting LIST
    // differs.
    val allSections by remember(v.vin) { derivedStateOf { state.value.sectionsFor(v) } }
    val sections by remember(v.vin, exclude) {
        derivedStateOf {
            val sel = state.value
            allSections.filter { it !in exclude && sel.isSectionAvailable(v, it) }
        }
    }
    // Pinned pebbles stay in the list (so they can be shared elements) but are collapsed to zero
    // height here; the hot seat renders the matching node.
    val pinned by remember(v.vin, pinHotspot) {
        derivedStateOf { if (pinHotspot) state.value.hotspotFor(v.vin).toSet() else emptySet() }
    }
    val hotDrag = LocalHotSeatDrag.current
    val haptics = LocalHaptics.current
    // PERF: each car-pager page composes this whole pebble stack. Composing all 8-10 pebbles
    // eagerly (incl. ClimatePebble/ChargePebble's top-level effects, which run BEFORE their
    // Pebble() call regardless of collapsed state) on the fling-settle frame is the biggest
    // remaining car-swipe cost.
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
        // Only the full-screen view lets a pebble be carried sideways (to the hot seat); in the
        // grid/single column it reorders vertically only.
        allowHorizontalDrag = pinHotspot,
        onReorder = { newVisible ->
            // Merge the reordered visible items back into the full section order so excluded ones
            // (the pinned hot-spot pebbles, summary, hidden) keep their slots instead of being
            // dropped.
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
                // Handled means "pin": record which key, so the fly-in target and the commit know.
                d.pendingPin = if (pin) key as String else null
                pin
            }
        },
        dragReleaseTarget = hotDrag?.let { d -> { _ -> if (d.pendingPin != null) d.slotTopLeft else null } },
        onDragReleaseCommit = hotDrag?.let { d ->
            { _ ->
                d.pendingPin?.let { pinned ->
                    haptics?.heavy()
                    vm.setHotspot(v, pinned)
                }
                d.pendingPin = null
            }
        },
        onDragCancel = hotDrag?.let { d ->
            { _ -> d.section = null; d.pendingPin = null }
        },
        staggerInOnColdStart = true,
        introKey = v.vin,
    ) { section, itemDragHandle, _ ->
        // A pinned pebble stays in the list but collapsed to zero (see the list's own layout, which
        // adds no gap around a zero-height row); the hot seat renders the matching shared node.
        AnimatedVisibility(
            visible = section !in pinned,
            enter = fadeIn() + scaleIn(initialScale = 0.97f),
            exit = fadeOut() + scaleOut(targetScale = 0.97f),
        ) {
            val ready by remember(section) {
                derivedStateOf { section in eager || section in filledSections }
            }
            val shared = if (sharedScope != null) {
                with(sharedScope) {
                    Modifier.sharedElement(
                        rememberSharedContentState(key = "pin:${v.vin}:$section"),
                        animatedVisibilityScope = this@AnimatedVisibility,
                    )
                }
            } else {
                Modifier
            }
            if (ready) {
                SinglePebble(section, v, state, vm, itemDragHandle.then(shared), onExpand = onExpand)
            } else {
                // Below the fold, so this transient state is never seen or interacted with.
                // heightIn(min), not a fixed height: a pebble header carries a title plus an optional
                // summary line, and at a large accessibility font that stacked text is taller than
                // ControlHeight -- a hard height clipped it.
                Box(Modifier.fillMaxWidth().heightIn(min = PebbleHeaderHeight).then(itemDragHandle).then(shared))
            }
        }
    }
}

/**
 * How many pebbles from the top (incl. the hero summary) compose eagerly; the rest fill in later,
 * off the swipe. 3 covers everything above the fold on a phone.
 */
internal const val EAGER_PEBBLES = 3

/**
 * A UiState slice whose identity is the fields the row reads. Slices with equal [key] compare
 * equal, so Compose skips a pebble when none of its fields moved.
 */
internal class KeyedSlice(val state: UiState, private val key: Any?) {
    override fun equals(other: Any?): Boolean = other is KeyedSlice && key == other.key
    override fun hashCode(): Int = key?.hashCode() ?: 0
}

/**
 * Memoized slice of [state] keyed on exactly what the row reads (see [KeyedSlice]). [keys] builds
 * the key from the passed [UiState] so the read happens here, not in the caller's scope (which
 * would subscribe it to every emission). [contextKey] covers anything else the lambda closes over
 * (the car).
 */
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

/** Renders one pebble by section name (used by the list and the hot spot). */
@Composable
internal fun SinglePebble(section: String, v: Vehicle, state: State<UiState>, vm: AppViewModel, modifier: Modifier, onExpand: (() -> Unit)? = null) {
    // Narrow derived read of this car's status; `state.value` here would subscribe every pebble to
    // every UiState emission.
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
                    // Icon-only, like the collapse action on the dual-column view: the label here is
                    // a whole sentence ("Expand to full screen"), so a visible label both reads
                    // wrong and made the button overlap the title. contentDescription carries it to
                    // TalkBack.
                    PebbleHeaderAction(
                        label = "",
                        icon = Icons.Filled.Fullscreen,
                        contentDescription = "Expand to full screen",
                        onClick = it,
                    )
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
