package com.bloo.bluelink.ui

/** Trips/drive-history pebbles: TripsPebble, TripRow, tripDate and climateChunksLabel. */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.role
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.composed
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.EvTrip
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.formatSpeed
import com.bloo.bluelink.data.formatSpeedMph
import com.bloo.bluelink.data.formatTripDistance
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.climateChunks
import kotlinx.coroutines.flow.first
import kotlin.math.max

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
    // NOT alwaysExpandedInSimpleMode: that flag is for pebbles with a single setting that reads
    // better inline without an expand/collapse control (see its own doc). This one renders a list
    // of up to 8 trips, so forcing it always open in simple mode just removed the ability to
    // collapse it.
    Pebble(v, "trips", "Trips", Icons.Filled.Route, state, vm, modifier, summary = summary) {
        when {
            trips == null -> Text(if (loading) "Fetching trip history…" else "No trip data yet.")
            trips.isEmpty() -> Text("No recent trips reported by this car.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                val tMetric = LocalAppearance.current.metricDistance
                // In a forced-open/glance context (LocalForceExpanded) only the 3 most recent trips
                // show so the tile fits without scrolling; the full pebble keeps up to 8.
                val glance = LocalForceExpanded.current
                trips.take(if (glance) 3 else 8).forEach { TripRow(it, metric = tMetric) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TripRow(trip: EvTrip, metric: Boolean = false) {
    val primaryColor = MaterialTheme.colorScheme.onSurface
    // Same color-role swap as DiagnosticsPebble's indented rows: onSurfaceVariant is full alpha, so
    // dimness is the role. Boosted on the cover, which has no other contrast handling.
    val captionColor = if (LocalForceExpanded.current) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Each trip is its own zone: an outlined panel with a header line (when + how far) and a neat
    // grid of stats underneath.
    Column(
        Modifier.fillMaxWidth().outlinedPanel(GapGroup),
        verticalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                tripDate(trip.startdate),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
            )
            trip.distance?.let {
                Text(
                    formatTripDistance(it, metric),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GapGroup),
            verticalArrangement = Arrangement.spacedBy(GapHairline),
        ) {
            trip.driveMinutes?.let { TripStat("Time", fmtMinutes(it), captionColor) }
            trip.idleMinutes?.takeIf { it > 0 }?.let { TripStat("Idle", fmtMinutes(it), captionColor) }
            // formatSpeedMph, not formatSpeed: these values are mph and formatSpeed takes km/h.
            trip.avgspeed?.value?.let { TripStat("Avg", formatSpeedMph(it, metric), captionColor) }
            trip.maxspeed?.value?.let { TripStat("Max", formatSpeedMph(it, metric), captionColor) }
            trip.usedKwh?.let { TripStat("Used", "$it kWh", captionColor) }
            trip.regenKwh?.takeIf { it > 0 }?.let { TripStat("Regen", "$it kWh", captionColor) }
        }
    }
}

/** One label-over-value stat in a trip's zone. */
@Composable
private fun TripStat(label: String, value: String, captionColor: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = captionColor, maxLines = 1)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

internal fun tripDate(raw: String?): String = com.bloo.bluelink.data.tripDate(raw)

/**
 * "10 + 3 min" for a 13-minute request: the per-command chunks [climateChunks] splits an
 * auto-extended climate run into, shown on the Run time slider.
 */
internal fun climateChunksLabel(totalMinutes: Int): String =
    climateChunks(totalMinutes).joinToString(" + ") + " min"
