package com.bloo.bluelink.ui

/** Trips/drive-history pebbles: TripsPebble, TripRow and climateChunksLabel. */

import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.State
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import com.bloo.bluelink.data.EvTrip
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.climateChunks
import com.bloo.bluelink.data.fmtMinutes
import com.bloo.bluelink.data.formatSpeedMph
import com.bloo.bluelink.data.formatTripDistance
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.tripDate
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.SecondaryTextStyle

/**
 * Recent drives from the Hyundai/Genesis US trip-details feed: distance, time, speeds and (EVs) the
 * energy/regen breakdown. Loaded lazily once per session; head units that report no trips show an
 * empty state.
 */
@Composable
internal fun TripsPebble(v: Vehicle, state: UiState, vm: AppViewModel, modifier: Modifier) {
    // Gen5W head units don't serve evTripDetails, so the pebble is hidden for them. Kia US reports
    // no generation and keeps it. Uses the user-confirmed generation (UiState.isGen5WEffective).
    val isGen5W = state.isGen5WEffective(v)
    if (isGen5W) return
    // Same reasoning one step further out: a Gen5W head unit reports nothing, and neither does a
    // backend with no trips endpoint. Kia US, Canada and Europe all inherit the repository's empty
    // default, so without this they show the pebble and it never fills.
    if (!v.brand.supportsTrips) return
    val trips = state.trips[v.vin]
    val loading = state.isPending(v.vin, "trips")
    // Only load trips if they haven't been fetched yet; prevent redundant loads on recomposition or
    // when data is already available/loading
    LaunchedEffect(v.vin) {
        if (trips == null && !loading) vm.loadTrips(v)
    }
    val summary = when {
        trips == null -> if (loading) "Loading…" else null
        trips.isEmpty() -> "No recent trips"
        else -> "${trips.size} recent"
    }
    Pebble(v, "trips", "Trips", Icons.Filled.Route, state, vm, modifier, summary = summary) {
        when {
            trips == null -> SecondaryText(if (loading) "Fetching trip history…" else "No trip data yet.")
            trips.isEmpty() -> SecondaryText("No recent trips reported by this car.")
            else -> {
                val tMetric = LocalAppearance.current.metricDistance
                // In a forced-open/glance context (LocalForceExpanded) only the 3 most recent trips
                // show so the tile fits without scrolling; the full pebble keeps up to 8.
                val glance = LocalForceExpanded.current
                // Each trip is a row of the pebble's own cascade (not one row holding them all), and pops in on its own.
                trips.take(if (glance) 3 else 8).forEachIndexed { i, trip -> TripRow(trip, metric = tMetric, index = i) }
            }
        }
    }
}

@Composable
internal fun TripRow(trip: EvTrip, metric: Boolean = false, index: Int = 0) {
    val primaryColor = MaterialTheme.colorScheme.onSurface
    // Same color-role swap as DiagnosticsPebble's indented rows: onSurfaceVariant is full alpha, so
    // dimness is the role. Boosted in a glance context, which has no other contrast handling.
    val captionColor = if (LocalForceExpanded.current) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    // The trips pop in one after another, and within a trip the panel pops first and then every text in it, one at a time.
    val clock = rememberPopClock(delayMs = index * TripStaggerMs)
    val stats = listOfNotNull(
        trip.driveMinutes?.let { "Time" to fmtMinutes(it) },
        trip.idleMinutes?.takeIf { it > 0 }?.let { "Idle" to fmtMinutes(it) },
        // formatSpeedMph, not formatSpeed: these values are mph and formatSpeed takes km/h.
        trip.avgspeed?.value?.let { "Avg" to formatSpeedMph(it, metric) },
        trip.maxspeed?.value?.let { "Max" to formatSpeedMph(it, metric) },
        trip.usedKwh?.let { "Used" to "$it kWh" },
        trip.regenKwh?.takeIf { it > 0 }?.let { "Regen" to "$it kWh" },
    )
    // Slots: the panel, the date, the distance, then a label and a value for each stat.
    val slots = 3 + 2 * stats.size
    val textPivot = TransformOrigin(0f, 0.5f)
    // Each trip is its own zone: an outlined panel with a header line (when + how far) and a neat
    // grid of stats underneath.
    Column(
        Modifier.fillMaxWidth().popIn(clock, slot = 0, slots = slots, from = 0.9f).outlinedPanel(GapGroup),
        verticalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tripDate(trip.startdate),
                modifier = Modifier.popIn(clock, 1, slots, pivot = textPivot),
                style = PrimaryTextStyle(), fontWeight = FontWeight.Bold, color = primaryColor,
            )
            trip.distance?.let {
                Text(
                    formatTripDistance(it, metric),
                    modifier = Modifier.popIn(clock, 2, slots, pivot = textPivot),
                    style = PrimaryTextStyle(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GapGroup),
            verticalArrangement = Arrangement.spacedBy(GapHairline),
        ) {
            stats.forEachIndexed { i, (label, value) -> TripStat(label, value, captionColor, clock, 3 + 2 * i, slots) }
        }
    }
}

/** How long after the one before it each trip starts to pop in. */
private const val TripStaggerMs = 90

/** One label-over-value stat in a trip's zone; the label and the value each pop in on their own turn ([slot] and the one after it). */
@Composable
private fun TripStat(label: String, value: String, captionColor: Color, clock: State<Float>, slot: Int, slots: Int) {
    val pivot = TransformOrigin(0f, 0.5f)
    Column {
        Text(label, modifier = Modifier.popIn(clock, slot, slots, pivot = pivot), style = SecondaryTextStyle(), color = captionColor, maxLines = 1)
        Text(value, modifier = Modifier.popIn(clock, slot + 1, slots, pivot = pivot), style = SecondaryTextStyle(), fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * "10 + 3 min" for a 13-minute request: the per-command chunks [climateChunks] splits an
 * auto-extended climate run into, shown on the Run time slider.
 */
internal fun climateChunksLabel(totalMinutes: Int): String =
    climateChunks(totalMinutes).joinToString(" + ") + " min"
