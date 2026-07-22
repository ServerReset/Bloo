package com.bloo.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.formatEfficiency
import com.bloo.bluelink.data.formatTripDistance
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import kotlinx.coroutines.launch

// Was defined separately here and on the phone (Screens.kt's tripDate()) --
// now shared/FormatUtils.kt's tripDate(), with includeWeekday = false for
// the watch's narrower card (no weekday, matching the previous local format).
private fun tripDate(raw: String?): String = com.bloo.bluelink.data.tripDate(raw, includeWeekday = false)

/**
 * Recent-trips list for one car. [ui].trips is a per-VIN cache
 * (`Map<String, List<Trip>?>`) that this screen doesn't populate itself --
 * [LaunchedEffect] keyed on [vin] fires [WearViewModel.loadTrips] once per
 * distinct VIN shown (re-firing if the user navigates to a different car's
 * trips screen), and the result later lands in [ui] reactively via whatever
 * upstream flow backs it. Three states are rendered from that same cache:
 * a first-load spinner (no cached list yet AND a fetch is in flight, per
 * `"$vin:trips" in ui.pending`), a rich empty state distinguishing "never
 * driven" from "fetch failed" (see the comment below), and the populated list
 * itself, one [SectionCard] per trip.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TripsScreen(vm: WearViewModel, ui: WearUi, vin: String) {
    LaunchedEffect(vin) { vm.loadTrips(vin) }
    val trips = ui.trips[vin]
    val loading = "$vin:trips" in ui.pending

    if (trips == null && loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(visible = true, enter = fadeIn(tween(200))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Loading trips…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    val state = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { e ->
                scope.launch { state.scrollBy(e.verticalScrollPixels) }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        state = state,
        contentPadding = PaddingValues(horizontal = roundSafeHorizontalPadding(), vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ListHeader { Text("Recent trips", textAlign = TextAlign.Center) } }

        if (trips.isNullOrEmpty()) {
            // A genuine fetch failure used to render the exact same "No
            // recent trips reported" text a car with a truly empty history
            // would show -- indistinguishable from "you've just never driven
            // this car", when what actually happened was a network/API
            // error. Gets its own icon/title/retry, matching the richer
            // empty-state pattern HomeScreen uses for "No cars yet".
            val failed = vin in ui.tripsErrors
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (failed) Icons.Filled.WifiOff else Icons.Filled.Route,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (failed) MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        // titleMedium to match HomeScreen's "No cars yet" --
                        // same conceptual role (a big centered empty state).
                        if (failed) "Couldn't load trips" else "No trips yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (failed) "Check your connection and try again." else "Trips will appear once you've driven.",
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
            // One SectionCard per trip, keyed by its position in the list
            // (trips have no stable id of their own here) rather than by date,
            // since two trips could share the same start date. Each field is
            // rendered only `?.let`/`?.takeIf`'d in when the data actually
            // exists/is meaningful -- idle time and regen are hidden entirely
            // when zero rather than shown as a bare "0", and efficiency is
            // only computed (and shown) when both a distance and a non-zero
            // used-kWh figure are present, since dividing by zero or missing
            // data would produce a meaningless number.
            for (index in trips.indices) {
                val t = trips[index]
                item(key = index) {
                    SectionCard(tripDate(t.startdate)) {
                        val metric = ui.localSettings.unitSystem == "metric"
                        t.distance?.let { StatusRow("Distance", formatTripDistance(it, metric)) }
                        t.driveMinutes?.let { StatusRow("Drive", fmtMinutes(it)) }
                        t.idleMinutes?.takeIf { it > 0 }?.let { StatusRow("Idle", fmtMinutes(it)) }
                        t.usedKwh?.let { StatusRow("Used", "$it kWh") }
                        t.regenKwh?.takeIf { it > 0 }?.let { StatusRow("Regen", "$it kWh") }
                        t.distance?.let { d ->
                            t.usedKwh?.let { k ->
                                if (k > 0) {
                                    StatusRow("Efficiency", formatEfficiency(d, k, metric))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
