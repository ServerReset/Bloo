package com.bloo.bluelink.ui

import android.location.Address
import android.location.Geocoder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.TripPlan
import com.bloo.bluelink.data.TripRoute
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** The charge the car should have on reaching a charger, and on reaching the destination, both in percent. */
private const val CHARGER_ARRIVAL_PERCENT = 20
private const val DESTINATION_ARRIVAL_PERCENT = 10

/** A place the search found, by its name and position. */
private data class Place(val name: String, val lat: Double, val lon: Double)

/**
 * Plans a trip from the car's last position to a place the person names: the search finds the place on the phone, the planner finds the
 * charging stops on Bluelink, and Google Maps does the navigation when the person asks for it. Read-only; nothing is started.
 */
@Composable
internal fun TripPlannerDialog(vm: AppViewModel, v: Vehicle, at: GeoLocation, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember(v.vin) { mutableStateOf("") }
    var places by remember(v.vin) { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember(v.vin) { mutableStateOf(false) }
    var chosen by remember(v.vin) { mutableStateOf<Place?>(null) }
    var plan by remember(v.vin) { mutableStateOf<TripPlan?>(null) }
    var planning by remember(v.vin) { mutableStateOf(false) }
    var note by remember(v.vin) { mutableStateOf<String?>(null) }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { findPlaces(context, q) }.getOrElse { emptyList() }
            }
            places = found
            if (found.isEmpty()) note = "No place found for \"$q\"."
            searching = false
        }
    }

    fun planTo(p: Place) {
        chosen = p
        plan = null
        note = null
        planning = true
        scope.launch {
            runCatching {
                vm.canadaRepo(vm.brandOf(v)).tripPlan(v, at.latitude, at.longitude, p.lat, p.lon, CHARGER_ARRIVAL_PERCENT, DESTINATION_ARRIVAL_PERCENT)
            }
                .onSuccess { result ->
                    plan = result
                    if (result.status != 0 || result.routes.isEmpty()) note = "Bluelink found no route to ${p.name}."
                }
                .onFailure { note = it.message ?: "Bluelink wouldn't plan the trip." }
            planning = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Plan a trip", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    "From the car's last position. The stops are where it charges on the way.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Destination") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { search() }, enabled = !searching && query.isNotBlank(), modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(if (searching) "Searching…" else "Search")
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    places.forEach { p ->
                        OutlinedButton(onClick = { planTo(p) }, enabled = !planning, modifier = Modifier.fillMaxWidth()) {
                            Text(p.name, maxLines = 2)
                        }
                    }
                    if (planning) CircularProgressIndicator(Modifier.padding(8.dp))
                    val result = plan
                    val dest = chosen
                    if (result != null && dest != null) {
                        result.routes.forEach { RouteCard(it) }
                        Button(
                            onClick = { openDirections(context, dest) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Navigate in Google Maps") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteCard(r: TripRoute) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "%.0f km · %d min · %d charging stop%s".format(r.distanceM / 1000.0, r.minutes, r.stops.size, if (r.stops.size == 1) "" else "s"),
            style = MaterialTheme.typography.titleSmall,
        )
        if (!r.reachable) Text("Bluelink says this route can't be reached on the charge.", style = MaterialTheme.typography.bodySmall)
        r.arriveSoc?.let { Text("Arrive with about $it%", style = MaterialTheme.typography.bodySmall) }
        r.stops.forEach { s ->
            val line = listOfNotNull(
                s.network,
                s.arriveSoc?.let { "arrive $it%" },
                s.targetSoc?.let { "leave $it%" },
                s.chargeMinutes?.let { "about $it min" },
                s.powerKw?.let { "$it kW" },
            ).joinToString(" · ")
            Text(s.name, style = MaterialTheme.typography.bodyMedium)
            if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Places matching [query], from the phone's own geocoder. */
@Suppress("DEPRECATION")
private fun findPlaces(context: android.content.Context, query: String): List<Place> {
    val geocoder = Geocoder(context, Locale.getDefault())
    val found: List<Address> = geocoder.getFromLocationName(query, 6).orEmpty()
    return found.map { a -> Place(listOfNotNull(a.featureName, a.locality, a.adminArea).joinToString(", ").ifBlank { query }, a.latitude, a.longitude) }
}

private fun openDirections(context: android.content.Context, p: Place) {
    openUrl(context, "https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=${p.lat},${p.lon}")
}
