package com.bloo.bluelink.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloo.bluelink.data.ChargeOffer
import com.bloo.bluelink.data.ChargeStatus
import com.bloo.bluelink.data.ChargingStation
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.privatePrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Public chargers within 25 km of the car's last position. */
private const val NEARBY_RADIUS_KM = 25

/** How often a charge in progress is read, while the list is open. */
private const val STATUS_EVERY_MS = 30_000L

/** A paid charge started from the app, kept so it can still be stopped after the list closes. */
internal data class ActiveCharge(val paymentId: String, val poiId: String, val stationName: String)

/** Where the active charge of each car is kept on this phone (per VIN). */
private class ActiveChargeStore(context: Context) {
    private val prefs = context.privatePrefs("active_charge")

    fun load(vin: String): ActiveCharge? {
        val pay = prefs.getString("pay:$vin", null) ?: return null
        return ActiveCharge(pay, prefs.getString("poi:$vin", "").orEmpty(), prefs.getString("name:$vin", "").orEmpty())
    }

    fun save(vin: String, c: ActiveCharge) {
        prefs.edit()
            .putString("pay:$vin", c.paymentId)
            .putString("poi:$vin", c.poiId)
            .putString("name:$vin", c.stationName)
            .apply()
    }

    fun clear(vin: String) {
        prefs.edit().remove("pay:$vin").remove("poi:$vin").remove("name:$vin").apply()
    }
}

@Composable
internal fun ChargersNearbyDialog(vm: AppViewModel, v: Vehicle, at: GeoLocation, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { ActiveChargeStore(context) }
    var stations by remember(v.vin) { mutableStateOf<List<ChargingStation>?>(null) }
    var error by remember(v.vin) { mutableStateOf<String?>(null) }
    var active by remember(v.vin) { mutableStateOf(store.load(v.vin)) }
    var picked by remember(v.vin) { mutableStateOf<ChargingStation?>(null) }
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
                    "Public chargers within $NEARBY_RADIUS_KM km of the car, nearest first. Tap one to start a paid charge.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                active?.let { charge ->
                    ActiveChargePanel(vm, v, charge) {
                        store.clear(v.vin)
                        active = null
                    }
                }
                val list = stations
                when {
                    error != null -> Text(error.orEmpty())
                    list == null -> CircularProgressIndicator()
                    list.isEmpty() -> Text("No public chargers within $NEARBY_RADIUS_KM km.")
                    else -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        list.forEach { s ->
                            // Only a station with a charger to start, and no charge already running, can be picked.
                            val pickable = s.chargerIds.isNotEmpty() && active == null
                            ChargerRow(s, Modifier.fillMaxWidth().then(if (pickable) Modifier.clickable { picked = s } else Modifier))
                        }
                    }
                }
            }
        }
    }
    picked?.let { station ->
        ConfirmChargeDialog(vm, v, station, onDismiss = { picked = null }) { charge ->
            store.save(v.vin, charge)
            active = charge
            picked = null
        }
    }
}

@Composable
private fun ChargerRow(s: ChargingStation, modifier: Modifier) {
    Column(modifier) {
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

/**
 * Shows the price of charging at [station] (its first charger) and starts the charge only when the person taps Start. [onStarted] is
 * called with the charge that was started.
 */
@Composable
private fun ConfirmChargeDialog(
    vm: AppViewModel,
    v: Vehicle,
    station: ChargingStation,
    onDismiss: () -> Unit,
    onStarted: (ActiveCharge) -> Unit,
) {
    var offer by remember(station.id) { mutableStateOf<ChargeOffer?>(null) }
    var error by remember(station.id) { mutableStateOf<String?>(null) }
    var starting by remember(station.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(station.id) {
        runCatching { vm.canadaRepo(vm.brandOf(v)).chargeOffer(v, station.id, station.chargerIds.first()) }
            .onSuccess { offer = it }
            .onFailure { error = it.message ?: "Bluelink wouldn't give the price for this charger." }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Start a paid charge?", style = MaterialTheme.typography.titleMedium)
                Text(station.name, style = MaterialTheme.typography.titleSmall)
                val o = offer
                when {
                    error != null -> Text(error.orEmpty())
                    o == null -> CircularProgressIndicator()
                    else -> {
                        o.productName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        o.priceLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        o.cardLabel?.let { Text("Charged to $it", style = MaterialTheme.typography.bodySmall) }
                        Text(
                            "This spends money on your Bluelink account. Nothing starts until you tap Start.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                val ready = offer
                val failure = error
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, enabled = !starting) { Text("Cancel") }
                    Button(
                        enabled = ready != null && failure == null && !starting,
                        onClick = {
                            val o2 = ready ?: return@Button
                            starting = true
                            scope.launch {
                                runCatching { vm.canadaRepo(vm.brandOf(v)).startCharge(v, o2.paymentId, station.id, o2.cardId) }
                                    .onSuccess { reason ->
                                        if (reason == null) onStarted(ActiveCharge(o2.paymentId, station.id, station.name))
                                        else error = reason
                                    }
                                    .onFailure { error = it.message ?: "The charge didn't start." }
                                starting = false
                            }
                        },
                    ) { Text(if (starting) "Starting…" else "Start") }
                }
            }
        }
    }
}

/** The charge in progress: its numbers, refreshed every [STATUS_EVERY_MS], and a Stop button that ends it. */
@Composable
private fun ActiveChargePanel(vm: AppViewModel, v: Vehicle, charge: ActiveCharge, onStopped: () -> Unit) {
    var status by remember(charge.paymentId) { mutableStateOf<ChargeStatus?>(null) }
    var note by remember(charge.paymentId) { mutableStateOf<String?>(null) }
    var stopping by remember(charge.paymentId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(charge.paymentId) {
        while (true) {
            runCatching { vm.canadaRepo(vm.brandOf(v)).chargeStatus(v, charge.paymentId) }
                .onSuccess { status = it }
                .onFailure { note = it.message }
            delay(STATUS_EVERY_MS)
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Charging at ${charge.stationName}", style = MaterialTheme.typography.titleSmall)
        val s = status
        val currency = s?.currency?.let { " $it" }.orEmpty()
        val line = listOfNotNull(
            s?.kwh?.let { "%.1f kWh".format(it) },
            s?.powerKw?.let { "%.1f kW".format(it) },
            s?.cost?.let { "%.2f$currency".format(it) },
        ).joinToString(" · ")
        Text(line.ifBlank { "Waiting for the first reading…" }, style = MaterialTheme.typography.bodySmall)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Button(
            enabled = !stopping,
            onClick = {
                stopping = true
                scope.launch {
                    runCatching { vm.canadaRepo(vm.brandOf(v)).stopCharge(v, charge.paymentId, charge.poiId) }
                        .onSuccess { reason ->
                            if (reason == null) onStopped() else note = reason
                        }
                        .onFailure { note = it.message ?: "The charge didn't stop. Try again." }
                    stopping = false
                }
            },
        ) { Text(if (stopping) "Stopping…" else "Stop charge") }
    }
}
