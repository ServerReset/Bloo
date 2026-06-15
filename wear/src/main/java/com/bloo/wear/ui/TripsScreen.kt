package com.bloo.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel

@Composable
fun TripsScreen(vm: WearViewModel, ui: WearUi, vin: String) {
    LaunchedEffect(vin) { vm.loadTrips(vin) }
    val trips = ui.trips[vin]
    val loading = "$vin:trips" in ui.pending

    if (trips == null && loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val state = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ListHeader { Text("Recent trips", textAlign = TextAlign.Center) } }

        if (trips.isNullOrEmpty()) {
            item {
                Text(
                    "No recent trips reported",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            for (index in trips.indices) {
                val t = trips[index]
                item(key = index) {
                    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        t.startdate?.take(16)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        t.distance?.let { StatusRow("Distance", "%.1f mi".format(it)) }
                        t.driveMinutes?.let { StatusRow("Drive", fmtMinutes(it)) }
                        t.idleMinutes?.takeIf { it > 0 }?.let { StatusRow("Idle", fmtMinutes(it)) }
                        t.usedKwh?.let { StatusRow("Used", "$it kWh") }
                        t.regenKwh?.takeIf { it > 0 }?.let { StatusRow("Regen", "$it kWh") }
                    }
                }
            }
        }
    }
}
