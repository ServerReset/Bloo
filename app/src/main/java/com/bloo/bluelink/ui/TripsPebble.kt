@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Trips/drive-history pebbles: TripsPebble, TripRow, tripDate and
 * climateChunksLabel -- extracted from Pebbles.kt so the UI file stays smaller.
 */

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.role
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.unit.dp
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
 * Recent drives from the Hyundai/Genesis US trip-details feed, with distance,
 * time, speeds and (for EVs) the energy/regen breakdown. Loaded lazily the
 * first time the pebble is composed, once per session. Shown for every car;
 * cars whose head unit doesn't report trips simply show an empty state.
 */
@Composable
internal fun TripsPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    // The evTripDetails feed isn't served by Gen5W (generation 2) head units -
    // they report nothing, EV or not - so the pebble is hidden for them rather
    // than sitting permanently empty. Kia US doesn't report a generation, so it's
    // excluded from the check and keeps the pebble. Reads the user's own
    // confirmed generation (Settings/onboarding) over the raw API guess when
    // one's been set -- see UiState.isGen5WEffective.
    val isGen5W = state.isGen5WEffective(v)
    if (isGen5W) return
    // Same reasoning one step further out: a Gen5W head unit reports nothing,
    // and neither does a backend with no trips endpoint. Kia US, Canada and
    // Europe all inherit the repository's empty default, so without this they
    // show the pebble and it never fills.
    if (!v.brand.supportsTrips) return
    val trips = state.trips[v.vin]
    val loading = state.isPending(v.vin, "trips")
    // Only load trips if they haven't been fetched yet; prevent redundant loads
    // on recomposition or when data is already available/loading
    LaunchedEffect(v.vin) {
        if (trips == null && !loading) vm.loadTrips(v)
    }
    val summary = when {
        trips == null -> if (loading) "Loading…" else null
        trips.isEmpty() -> "No recent trips"
        else -> "${trips.size} recent"
    }
    // NOT alwaysExpandedInSimpleMode: that flag is for pebbles with a single setting
    // that reads better inline without an expand/collapse control (see its own doc).
    // This one renders a list of up to 8 trips, so forcing it always open in simple
    // mode just removed the ability to collapse it.
    Pebble(v, "trips", "Trips", Icons.Filled.Route, state, vm, dragHandle, summary = summary) {
        when {
            trips == null -> Text(if (loading) "Fetching trip history…" else "No trip data yet.")
            trips.isEmpty() -> Text("No recent trips reported by this car.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val tMetric = LocalAppearance.current.unitSystem == "metric"
                // COVER SCREEN: a small "Recent trips" header + only the 3 most recent,
                // so the tile fits the small square without scrolling and you land at
                // the top. Phone keeps up to 8 with no header. Gated on forceExpanded.
                val coverGlance = LocalForceExpanded.current
                // No cover hero: the summary already says "3 recent" and is now the tile's
                // headline. The same count in two different words helped nobody.
                trips.take(if (coverGlance) 3 else 8).forEach { TripRow(it, metric = tMetric) }
            }
        }
    }
}

@Composable
internal fun TripRow(trip: EvTrip, metric: Boolean = false) {
    // TripsPebble renders this list straight under the cover's CoverHero with no
    // color override of its own, so every Text below inherits whatever the
    // pebble's own container hands out -- surfaceVariant's onSurfaceVariant by
    // default. Pinning the primary date/distance line to full onSurface (it was
    // entirely unstyled before, not just muted) is the same "the important half
    // shouldn't be barely distinguishable from the caption below it" fix
    // StatusRow's own value already has.
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
            // Same fmtMinutes the watch's Trips screen uses for these two exact
            // fields -- without it, one trip read "95 min" here and "1h 35m" there.
            trip.driveMinutes?.let { add(fmtMinutes(it)) }
            trip.idleMinutes?.takeIf { it > 0 }?.let { add("${fmtMinutes(it)} idle") }
            // formatSpeedMph, not formatSpeed: these are mph (EvTrip's KDoc, corroborated
            // by its sibling `distance` being treated as miles on both surfaces), and
            // formatSpeed's input is km/h. 62 mph used to render as "38 mph" in imperial
            // and "62 km/h" in metric. `.value` is already Double, so no toDouble().
            trip.avgspeed?.value?.let { add("avg ${formatSpeedMph(it, metric)}") }
            trip.maxspeed?.value?.let { add("max ${formatSpeedMph(it, metric)}") }
        } }
        // Same color-role swap as DiagnosticsPebble's indented rows: onSurfaceVariant
        // is already full-alpha as a raw color, so its dimness is the ROLE, not
        // something an alpha bump alone would fix. Boosted on the cover, where this
        // whole list has no other contrast handling of its own.
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

/** "10 + 3 min" for a 13-minute request -- the per-command chunks
 *  [climateChunks] splits an auto-extended climate run into, shown on the
 *  Climate pebble's Run time slider so it's clear a request past the car's
 *  single-command cap becomes more than one command rather than one longer
 *  one. */
internal fun climateChunksLabel(totalMinutes: Int): String =
    climateChunks(totalMinutes).joinToString(" + ") + " min"
