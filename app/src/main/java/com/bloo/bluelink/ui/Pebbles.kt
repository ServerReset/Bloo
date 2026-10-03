package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
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
    // Was falling through to the generic capitalise, which renders "Ai" -- visible in the
    // hot-spot slot's pin dropdown and its pinned-section caption, right next to a pebble
    // whose own title is "AI summary". The name of a section belongs in this one `when`.
    "ai" -> "AI summary"
    else -> section.replaceFirstChar { it.uppercase() }
}

/** The reorderable pebble stack for a car.
 *
 * [pinHotspot] defaults to true (exclude "controls" + any secondary pin, on
 * the assumption the caller renders them separately via [HotspotSlot] --
 * true for the wide/dual-column [ExpandedCar]). [VehicleDetailContent]'s
 * single-column layout has no separate hero-column slot to pin anything
 * into, so it passes false: "controls" and any secondary pin then flow
 * through this list like any other pebble, landing in their normal
 * [DEFAULT_SECTIONS] position (right after "summary"/"update") instead of
 * vanishing from the list with nothing rendering them in their place. */
@Composable
internal fun PebbleList(
    v: Vehicle,
    state: State<UiState>,
    vm: AppViewModel,
    exclude: Set<String> = emptySet(),
    pinHotspot: Boolean = true,
    /** Forwarded to the "summary" hero pebble only -- see [HeroHeader]'s `expandAction`. */
    onExpand: (() -> Unit)? = null,
) {
    // ONE derived computation, not `val sel = state.value` plus a remember over its fields.
    //
    // Reading `state.value` in this body subscribed the whole pebble stack's host to every
    // UiState emission, so each poll/refresh/location tick recomposed this composable, its
    // content lambda and every item wrapper. As a derived state, the (pure) section
    // computation reruns on emissions but only invalidates this reader when the resulting
    // LIST actually differs -- and lists compare by content, so an unrelated emission is now
    // completely free here.
    // The car's own section order, derived separately: the reorder handler below needs it,
    // and as a derived read it too stays out of every emission's invalidation set.
    val allSections by remember(v.vin) { derivedStateOf { state.value.sectionsFor(v) } }
    val sections by remember(v.vin, exclude, pinHotspot) {
        derivedStateOf {
            val sel = state.value
            val hasBattery = sel.hasBattery(v)
            // Exclude any pebbles pinned to the hotspot (both primary and secondary slots)
            // -- but only when the caller is actually rendering them separately. See
            // [pinHotspot]'s own doc.
            val pinnedPebbles = if (pinHotspot) sel.hotspotFor(v.vin) else emptyList()
            val allExclude = exclude + pinnedPebbles
            allSections.filter {
                it !in allExclude && sel.isSectionAvailable(v, it)
            }
        }
    }
    val hotDrag = LocalHotSeatDrag.current
    // PERF: each car-pager page composes this whole pebble stack. Composing all
    // 8-10 pebbles eagerly (incl. ClimatePebble/ChargePebble's top-level effects,
    // which run BEFORE their Pebble() call regardless of collapsed state) on the
    // fling-settle frame is the biggest remaining car-swipe cost. So: compose the
    // hero + first EAGER_PEBBLES sections immediately (they're the only ones
    // above the fold), stub the rest with a collapsed-height placeholder, then
    // fill them in ONE AT A TIME, one per frame, rather than all at once.
    // Keyed on VIN so a disposed→recomposed page re-defers cheaply; a page kept
    // warm by beyondViewportPageCount=1 fills before the user ever swipes to it.
    // CRITICAL: `items` stays the FULL section list, so ReorderColumn's per-item
    // Box/key/animatePlacement/onSizeChanged/drag/semantics all exist from frame
    // one — only the body inside the content lambda is deferred, so the reorder
    // model is 100% intact and the off-screen stub→real swap is never visible.
    //
    // One-at-a-time, not the single "wait one frame, then fill everything" this used to
    // be: on a wide/foldable screen (GarageScreen's perPage > 1, multiple cars sharing one
    // page), every car's PebbleList ran that same one-frame defer independently -- so
    // frame 2 of a page settle didn't just fill ONE car's remaining 5-7 pebbles, it filled
    // EVERY visible car's remaining pebbles simultaneously, concentrating the exact cost
    // this mechanism exists to spread out into one single, now-bigger frame. Reported as
    // sluggish switching cars specifically on a large-screen foldable. Filling one pebble
    // per frame spreads that same total work across however many non-eager pebbles there
    // are, so no single frame pays for more than one pebble's worth of composition,
    // regardless of how many cars share the page.
    // A snapshot SET of the non-eager sections that have been filled in so far, read through
    // a PER-SECTION derived boolean below.
    //
    // It was a shared `filledCount` int, and the content lambda read it for every item -- so
    // each one-pebble-per-frame tick invalidated the whole list and recomposed EVERY pebble,
    // including the eager ones that had been ready since frame one. Measured on the API 34
    // emulator: ~570KB allocated per tick across just the three eager pebbles, every 300-600ms
    // for the whole fill, which is the churn that filled a 192MB heap. The mechanism's own
    // point is to spread one pebble's composition per frame; a shared counter made every frame
    // pay for all of them. Derived per section, a tick invalidates exactly one item.
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
            // Merge the reordered visible items back into the full section order so
            // excluded ones (the pinned hot-spot pebbles, summary, hidden) keep
            // their slots instead of being dropped.
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
        // Each item watches ONLY its own readiness, so filling one pebble composes one pebble.
        val ready by remember(section) {
            derivedStateOf { section in eager || section in filledSections }
        }
        if (ready) {
            SinglePebble(section, v, state, vm, itemDragHandle, onExpand = onExpand)
        } else {
            // Off-screen placeholder, up to a few frames now rather than always exactly
            // one: reserves ~collapsed pebble height so the list doesn't visibly jump
            // when the real body fills in, and carries the drag handle so ReorderColumn's
            // item is fully formed. Below the fold, so this transient state is never
            // seen or interacted with.
            Box(Modifier.fillMaxWidth().height(PebbleHeaderHeight).then(itemDragHandle))
        }
    }
}

/** How many pebbles (from the top, incl. the hero summary) the per-car stack
 *  composes eagerly; the rest fill in one frame later, off the swipe. 3 comfortably
 *  covers everything above the fold on a phone so the visible region never stubs. */
internal const val EAGER_PEBBLES = 3

/**
 * Renders one pebble by section name (used by the list and the hot spot).
 *
 * Every pebble function below takes the WHOLE [UiState], not just the fields it
 * reads -- `state` is a data class, so its equality (and therefore Compose's
 * recomposition-skip check) fails on ANY field changing anywhere in the app, not
 * just the fields a given pebble actually uses. A weather refresh for a car
 * that isn't even on screen, an AI probe finishing, another car's status
 * arriving -- every one of those forced every visible pebble on every visible
 * car page to recompose, which is a big part of why the whole app reads as
 * laggy for several seconds after cold start or a car switch: that's exactly
 * the window where the most independent state updates land in quick
 * succession (cached-status restore, per-car status fetches, AI/Shizuku/update
 * probes, weather).
 *
 * Each branch below hands its pebble a slice whose IDENTITY is the exact fields
 * that pebble reads (see [KeyedSlice]/[stateSlice]) -- when none of those
 * changed since last time, the pebble sees an equal parameter and Compose skips
 * recomposing it, even though a genuinely newer `state` exists one frame up.
 * The pebble's own body is untouched; only what gets handed to it here is
 * cached. Keys were catalogued by reading every pebble function's body in
 * full (including what its own helper calls like `statusFor`/`isPending`
 * transitively read) rather than guessed -- a missed key would be a real
 * stale-UI bug, so each list below is the pebble's complete, verified
 * dependency set, not a guess at "probably enough."
 */
/** A UiState slice whose identity is the fields the row reads, not the whole state.
 *  Two slices compare equal when [key] is equal, so handing one to a pebble makes
 *  Compose's own parameter comparison skip it whenever none of its fields moved. */
internal class KeyedSlice(val state: UiState, private val key: Any?) {
    override fun equals(other: Any?): Boolean = other is KeyedSlice && key == other.key
    override fun hashCode(): Int = key?.hashCode() ?: 0
}

/** Memoized slice of [state] keyed on exactly what the row reads (see [KeyedSlice]).
 *
 *  [keys] builds the key from the passed [UiState] rather than the call site passing
 *  pre-read values: a key expression written at the call site (`state.value.imageUrls[v.vin]`)
 *  is itself a read of `state` in the CALLER's composition scope, which subscribed
 *  SinglePebble to every emission even though the slice handed to the pebble was narrow --
 *  the per-branch memo was defeating itself. Reading the fields here keeps that subscription
 *  out of the caller. [contextKey] is whatever else the lambda closes over (the car, here):
 *  a change to it rebuilds the derived instead of leaving a stale one behind. */
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
    // Narrow, derived read: this pebble only cares about ITS car's status. Reading
    // `state.value` here (as this line did) subscribed every pebble to EVERY UiState emission,
    // so a status poll for another car, a device-location tick or a weather refresh
    // recomposed the whole pebble stack -- the per-pebble `stateSlice` calls below exist to
    // stop exactly that, and this one line was undoing them for the pebble's own scope.
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
                    s.updatePendingDismiss,
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
