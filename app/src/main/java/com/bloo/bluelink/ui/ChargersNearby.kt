package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloo.bluelink.data.ChargingStation
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Vehicle

/** Public chargers within 25 km of the car's last position: name, network, power, and how many connectors are free. Read-only. */
private const val NEARBY_RADIUS_KM = 25

@Composable
internal fun ChargersNearbyDialog(vm: AppViewModel, v: Vehicle, at: GeoLocation, onDismiss: () -> Unit) {
    var stations by remember(v.vin) { mutableStateOf<List<ChargingStation>?>(null) }
    var error by remember(v.vin) { mutableStateOf<String?>(null) }
    LaunchedEffect(v.vin) {
        runCatching { vm.canadaRepo(vm.brandOf(v)).chargingStations(v, at.latitude, at.longitude, NEARBY_RADIUS_KM) }
            .onSuccess { stations = it }
            .onFailure { error = it.message ?: "Bluelink wouldn't give the chargers near this car." }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Chargers nearby", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "Public chargers within $NEARBY_RADIUS_KM km of the car, nearest first.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                val list = stations
                when {
                    error != null -> Text(error.orEmpty())
                    list == null -> CircularProgressIndicator()
                    list.isEmpty() -> Text("No public chargers within $NEARBY_RADIUS_KM km.")
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        list.forEach { ChargerRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargerRow(s: ChargingStation) {
    Column(Modifier.fillMaxWidth()) {
        Text(s.name, style = MaterialTheme.typography.titleSmall)
        val free = if (s.available != null && s.total != null) "${s.available} of ${s.total} free" else null
        val line = listOfNotNull(
            s.distanceKm?.let { "%.1f km".format(it) },
            s.network,
            s.maxKw?.let { "up to ${it.toInt()} kW" },
            free,
        ).joinToString(" · ")
        if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodySmall)
        s.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
