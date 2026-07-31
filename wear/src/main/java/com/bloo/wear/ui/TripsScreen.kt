package com.bloo.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.bloo.bluelink.data.formatEfficiency
import com.bloo.bluelink.data.formatTripDistance
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel

/**
 * The watch trips card is narrower than the phone's, so it drops the weekday the
 * shared [com.bloo.bluelink.data.tripDate] prepends by default -- one thin wrapper
 * so every call site here reads the same short "Jul 31" form rather than repeating
 * the `includeWeekday = false` flag at each use.
 */
private fun tripDate(raw: String?): String =
    com.bloo.bluelink.data.tripDate(raw, includeWeekday = false)

/**
 * Recent-trips list for one car.
 *
 * [WearUi.trips] is a per-VIN cache (`Map<String, List<EvTrip>>`) that this screen
 * never populates itself: a [LaunchedEffect] keyed on [vin] fires
 * [WearViewModel.loadTrips] once per distinct VIN shown (re-firing only when the
 * user opens a *different* car's trips), and the fetched list lands back in [ui]
 * reactively. The VM is idempotent -- it early-returns if the VIN was already
 * fetched -- so re-entering the same car's trips won't re-hit the network.
 *
 * Three states render off that one cache:
 *  - a first-load spinner (no cached list yet AND a fetch in flight, per
 *    `"$vin:trips" in ui.pending`);
 *  - a rich empty state that DISTINGUISHES a genuine fetch failure
 *    (`vin in ui.tripsErrors`, its own WifiOff icon + Retry) from a car that has
 *    simply never been driven -- the two used to show identical text;
 *  - the populated list, one [SectionCard] per trip.
 */
@Composable
fun TripsScreen(vm: WearViewModel, ui: WearUi, vin: String) {
    LaunchedEffect(vin) { vm.loadTrips(vin) }

    val trips = ui.trips[vin]
    val loading = "$vin:trips" in ui.pending

    // First load: nothing cached yet and a fetch is actually running. (Once a
    // list -- even an empty one -- has landed we fall through to the states below
    // instead of spinning again on a refresh.)
    if (trips == null && loading) {
        BusySpinner("Loading trips…")
        return
    }

    // One state shared between the curved scroll indicator and RotaryScalingColumn
    // -- they must scroll the same list. Hoisted here so ScreenScaffold can track it.
    val listState = rememberScalingLazyListState()

    // timeText = {} suppresses the inherited clock, which otherwise overlapped the
    // "Recent trips" header on this content screen.
    ScreenScaffold(scrollState = listState, timeText = {}) {
        RotaryScalingColumn(state = listState) {
            item { ListHeader { Text("Recent trips", textAlign = TextAlign.Center) } }

            if (trips.isNullOrEmpty()) {
                val failed = vin in ui.tripsErrors
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (failed) Icons.Filled.WifiOff else Icons.Filled.Route,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = if (failed) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                        )
                        Text(
                            // titleMedium mirrors HomeScreen's "No cars yet" -- the
                            // same conceptual role, a big centred empty state.
                            text = if (failed) "Couldn't load trips" else "No trips yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (failed) {
                                "Check your connection and try again."
                            } else {
                                "Trips will appear once you've driven."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (failed) {
                            MorphButton(
                                label = "Retry",
                                icon = Icons.Filled.Refresh,
                                active = false,
                                activeColor = MaterialTheme.colorScheme.primary,
                                pending = false,
                                onClick = { vm.loadTrips(vin) },
                            )
                        }
                    }
                }
            } else {
                // One SectionCard per trip. Keyed by list position rather than by
                // date, since two trips can share a start date and dates aren't
                // stable ids here. Each field renders only when it actually carries
                // meaning: idle time and regen are hidden when zero (rather than a
                // bare "0"), and efficiency is computed only when both a distance
                // and a non-zero used-kWh figure exist -- dividing by zero/missing
                // data would produce a meaningless number.
                val metric = ui.localSettings.unitSystem == "metric"
                for (index in trips.indices) {
                    val t = trips[index]
                    item(key = index) {
                        SectionCard(title = tripDate(t.startdate)) {
                            t.distance?.let { StatusRow("Distance", formatTripDistance(it, metric)) }
                            t.driveMinutes?.let { StatusRow("Drive", fmtMinutes(it)) }
                            t.idleMinutes?.takeIf { it > 0 }?.let { StatusRow("Idle", fmtMinutes(it)) }
                            t.usedKwh?.let { StatusRow("Used", "$it kWh") }
                            t.regenKwh?.takeIf { it > 0 }?.let { StatusRow("Regen", "$it kWh") }
                            val d = t.distance
                            val k = t.usedKwh
                            if (d != null && k != null && k > 0) {
                                // Stacked: "Efficiency" + "4.2 mi/kWh" both want >half
                                // a narrow round-face row and clip side-by-side; the
                                // stacked form keeps both whole.
                                StatusRow("Efficiency", formatEfficiency(d, k, metric), stacked = true)
                            }
                        }
                    }
                }
            }
        }
    }
}
