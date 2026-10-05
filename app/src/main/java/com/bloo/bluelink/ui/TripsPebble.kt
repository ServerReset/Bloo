package com.bloo.bluelink.ui

/**
 * Trips/drive-history pebbles: TripsPebble, TripRow, tripDate and climateChunksLabel.
 */

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
 * Recent drives from the Hyundai/Genesis US trip-details feed: distance, time, speeds and (EVs)
 * the energy/regen breakdown. Loaded lazily once per session; head units that report no trips
 * show an empty state.
 */
@Composable
internal fun TripsPebble(v: Vehicle, state: UiState, vm: AppViewModel, modifier: Modifier) {
    // Gen5W head units don't serve evTripDetails, so the pebble is hidden for them. Kia US reports
    // no generation and keeps it. Uses the user-confirmed generation (UiState.isGen5WEffective).
    val isGen5W = state.isGen5WEffective(v)
    if (isGen5W) return
    // Same for backends with no trips endpoint (Kia US, Canada, Europe inherit an empty default).
    if (!v.brand.supportsTrips) return
    val trips = state.trips[v.vin]
    val loading = state.isPending(v.vin, "trips")
    // Load only if not yet fetched, to avoid redundant loads on recomposition.
    LaunchedEffect(v.vin) {
        if (trips == null && !loading) vm.loadTrips(v)
    }
    val summary = when {
        trips == null -> if (loading) "Loading…" else null
        trips.isEmpty() -> "No recent trips"
        else -> "${trips.size} recent"
    }
    // Not alwaysExpandedInSimpleMode: this renders up to 8 trips and must stay collapsible.
    Pebble(v, "trips", "Trips", Icons.Filled.Route, state, vm, modifier, summary = summary) {
        when {
            trips == null -> Text(if (loading) "Fetching trip history…" else "No trip data yet.")
            trips.isEmpty() -> Text("No recent trips reported by this car.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                val tMetric = LocalAppearance.current.metricDistance
                // In a forced-open/glance context (LocalForceExpanded) only the 3 most recent trips show so the
                // tile fits without scrolling; the full pebble keeps up to 8.
                val glance = LocalForceExpanded.current
                trips.take(if (glance) 3 else 8).forEach { TripRow(it, metric = tMetric) }
            }
        }
    }
}

@Composable
internal fun TripRow(trip: EvTrip, metric: Boolean = false) {
    // No color override here, so Text inherits onSurfaceVariant; the primary date/distance line is
    // pinned to full onSurface so it stands apart from the caption.
    val primaryColor = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                tripDate(trip.startdate),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor,
            )
            trip.distance?.let {
                Text(formatTripDistance(it, metric), style = MaterialTheme.typography.bodyMedium, color = primaryColor)
            }
        }
        val pace = remember(trip, metric) { buildList {
            // fmtMinutes for these fields ("1h 35m" rather than "95 min").
            trip.driveMinutes?.let { add(fmtMinutes(it)) }
            trip.idleMinutes?.takeIf { it > 0 }?.let { add("${fmtMinutes(it)} idle") }
            // formatSpeedMph, not formatSpeed: these values are mph and formatSpeed takes km/h.
            trip.avgspeed?.value?.let { add("avg ${formatSpeedMph(it, metric)}") }
            trip.maxspeed?.value?.let { add("max ${formatSpeedMph(it, metric)}") }
        } }
        // Same color-role swap as DiagnosticsPebble's indented rows: onSurfaceVariant is full alpha, so
        // dimness is the role. Boosted on the cover, which has no other contrast handling.
        val captionColor = if (LocalForceExpanded.current) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        if (pace.isNotEmpty()) {
            Text(pace.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = captionColor)
        }
        val energy = remember(trip) { buildList {
            trip.usedKwh?.let { add("$it kWh used") }
            trip.regenKwh?.takeIf { it > 0 }?.let { add("$it kWh regen") }
        } }
        if (energy.isNotEmpty()) {
            Text(energy.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = captionColor)
        }
    }
}

internal fun tripDate(raw: String?): String = com.bloo.bluelink.data.tripDate(raw)

/** "10 + 3 min" for a 13-minute request: the per-command chunks [climateChunks] splits an
 *  auto-extended climate run into, shown on the Run time slider. */
internal fun climateChunksLabel(totalMinutes: Int): String =
    climateChunks(totalMinutes).joinToString(" + ") + " min"
