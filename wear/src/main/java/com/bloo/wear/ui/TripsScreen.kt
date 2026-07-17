package com.bloo.wear.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.formatEfficiency
import com.bloo.bluelink.data.formatTripDistance
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TripsScreen(vm: WearViewModel, ui: WearUi, vin: String) {
    LaunchedEffect(vin) { vm.loadTrips(vin) }
    val trips = ui.trips[vin]
    val loading = "$vin:trips" in ui.pending

    if (trips == null && loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ListHeader { Text("Recent trips", textAlign = TextAlign.Center) } }

        if (trips.isNullOrEmpty()) {
            item {
                Text(
                    if (loading) "Loading…" else "No recent trips reported",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            for (index in trips.indices) {
                val t = trips[index]
                item(key = index) {
                    SectionCard(t.startdate?.take(16)?.replace('T', ' ')) {
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
