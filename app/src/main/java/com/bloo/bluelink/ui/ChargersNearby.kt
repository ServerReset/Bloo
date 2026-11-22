package com.bloo.bluelink.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.bloo.bluelink.data.ChargeOffer
import com.bloo.bluelink.data.ChargeStatus
import com.bloo.bluelink.data.ChargingStation
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.privatePrefs
import com.bloo.bluelink.suspendRunCatching
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.TitleSubtitle
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
        suspendRunCatching { vm.canadaRepo(vm.brandOf(v)).chargingStations(v, at.latitude, at.longitude, NEARBY_RADIUS_KM) }
            .onSuccess { stations = it }
            .onFailure { error = it.message ?: "Bluelink wouldn't give the chargers near this car." }
    }
    FullScreenDialog(title = "Chargers nearby", onDismiss = onDismiss) {
        SheetNote("Public chargers within $NEARBY_RADIUS_KM km of the car, nearest first. Tap one to start a paid charge.")
        // The map is Google Maps, centred on the car, searching for EV charging: the same hand-off the trip planner uses.
        MorphTextButton(
            text = "Show on map",
            onClick = { openUrl(context, "https://www.google.com/maps/search/EV+charging+stations/@${at.latitude},${at.longitude},13z") },
            modifier = Modifier.fillMaxWidth(),
            icon = AppIcons.ArrowForward,
        )
        active?.let { charge ->
            ActiveChargePanel(vm, v, charge) {
                store.clear(v.vin)
                active = null
            }
        }
        SheetLoad(stations, error, empty = "No public chargers within $NEARBY_RADIUS_KM km.") { list ->
            SheetList {
                list.forEach { s ->
                    // Only a station with a charger to start, and no charge already running, can be picked.
                    val pickable = s.chargerIds.isNotEmpty() && active == null
                    ChargerRow(s, onClick = if (pickable) ({ picked = s }) else null)
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
private fun ChargerRow(s: ChargingStation, onClick: (() -> Unit)?) {
    val free = if (s.available != null && s.total != null) "${s.available} of ${s.total} free" else null
    SheetRow(
        title = s.name,
        lines = listOf(
            listOfNotNull(s.distanceKm?.let { "%.1f km".format(it) }, s.network, s.maxKw?.let { "up to ${it.toInt()} kW" }, free)
                .joinToString(" · "),
            s.address,
        ),
        onClick = onClick,
    )
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
        suspendRunCatching { vm.canadaRepo(vm.brandOf(v)).chargeOffer(v, station.id, station.chargerIds.first()) }
            .onSuccess { offer = it }
            .onFailure { error = it.message ?: "Bluelink wouldn't give the price for this charger." }
    }
    GlassAlertDialog(
        onDismissRequest = { if (!starting) onDismiss() },
        icon = AppIcons.Bolt,
        title = "Start a paid charge?",
        text = {
            RevealText(station.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val o = offer
            val failure = error
            when {
                failure != null -> SheetNote(failure, isError = true)
                o == null -> SheetLoading()
                else -> {
                    o.productName?.let { BodySmallText(it) }
                    o.priceLines.forEach { RevealText(it) }
                    o.cardLabel?.let { BodySmallText("Charged to $it") }
                    BodySmallText("This spends money on your Bluelink account. Nothing starts until you tap Start.")
                }
            }
        },
        buttons = {
            val ready = offer
            MorphTextButton(
                text = if (starting) "Starting…" else "Start",
                onClick = {
                    val o = ready ?: return@MorphTextButton
                    starting = true
                    scope.launch {
                        suspendRunCatching { vm.canadaRepo(vm.brandOf(v)).startCharge(v, o.paymentId, station.id, o.cardId) }
                            .onSuccess { reason ->
                                if (reason == null) onStarted(ActiveCharge(o.paymentId, station.id, station.name)) else error = reason
                            }
                            .onFailure { error = it.message ?: "The charge didn't start." }
                        starting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = ready != null && error == null && !starting,
                pending = starting,
                emphasis = ButtonEmphasis.Confirm,
            )
            MorphTextButton("Cancel", onDismiss, modifier = Modifier.fillMaxWidth(), enabled = !starting)
        },
    )
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
            suspendRunCatching { vm.canadaRepo(vm.brandOf(v)).chargeStatus(v, charge.paymentId) }
                .onSuccess { status = it }
                .onFailure { note = it.message }
            delay(STATUS_EVERY_MS)
        }
    }
    Column(
        Modifier.fillMaxWidth().tintedPanel(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        verticalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        val s = status
        val currency = s?.currency?.let { " $it" }.orEmpty()
        val line = listOfNotNull(
            s?.kwh?.let { "%.1f kWh".format(it) },
            s?.powerKw?.let { "%.1f kW".format(it) },
            s?.cost?.let { "%.2f$currency".format(it) },
        ).joinToString(" · ")
        TitleSubtitle("Charging at ${charge.stationName}", listOfNotNull(line.ifBlank { "Waiting for the first reading…" }, note).joinToString("\n"))
        MorphTextButton(
            text = if (stopping) "Stopping…" else "Stop charge",
            onClick = {
                stopping = true
                scope.launch {
                    suspendRunCatching { vm.canadaRepo(vm.brandOf(v)).stopCharge(v, charge.paymentId, charge.poiId) }
                        .onSuccess { reason -> if (reason == null) onStopped() else note = reason }
                        .onFailure { note = it.message ?: "The charge didn't stop. Try again." }
                    stopping = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !stopping,
            pending = stopping,
            emphasis = ButtonEmphasis.Deny,
        )
    }
}
