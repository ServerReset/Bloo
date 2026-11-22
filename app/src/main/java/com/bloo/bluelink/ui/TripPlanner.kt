package com.bloo.bluelink.ui

import android.location.Address
import android.location.Geocoder
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.TripPlan
import com.bloo.bluelink.data.TripRoute
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.suspendRunCatching
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphTextButton
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
                suspendRunCatching { findPlaces(context, q) }.getOrElse { emptyList() }
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
            suspendRunCatching {
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

    FullScreenDialog(title = "Plan a trip", onDismiss = onDismiss) {
        SheetNote("From the car's last position. The stops are where it charges on the way.")
        BlooTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Destination") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            modifier = Modifier.fillMaxWidth(),
        )
        MorphTextButton(
            text = if (searching) "Searching…" else "Search",
            onClick = { search() },
            modifier = Modifier.fillMaxWidth().padding(vertical = GapRow),
            enabled = !searching && query.isNotBlank(),
            pending = searching,
            icon = AppIcons.Search,
            emphasis = ButtonEmphasis.Confirm,
        )
        note?.let { SheetNote(it) }
        SheetList {
            places.forEach { p -> SheetRow(p.name, emptyList(), onClick = if (planning) null else ({ planTo(p) })) }
            if (planning) SheetLoading()
            val result = plan
            val dest = chosen
            if (result != null && dest != null) {
                result.routes.forEach { RouteCard(it) }
                MorphTextButton(
                    text = "Navigate in Google Maps",
                    onClick = { openDirections(context, dest) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = AppIcons.ArrowForward,
                    emphasis = ButtonEmphasis.Confirm,
                )
            }
        }
    }
}

/** One route: its distance, time and stops, then each charging stop with how long it charges there. */
@Composable
private fun RouteCard(r: TripRoute) {
    SheetRow(
        title = "%.0f km · %d min · %d charging stop%s".format(r.distanceM / 1000.0, r.minutes, r.stops.size, if (r.stops.size == 1) "" else "s"),
        lines = buildList {
            if (!r.reachable) add("Bluelink says this route can't be reached on the charge.")
            r.arriveSoc?.let { add("Arrive with about $it%") }
            r.stops.forEach { s ->
                val detail = listOfNotNull(
                    s.network,
                    s.arriveSoc?.let { "arrive $it%" },
                    s.targetSoc?.let { "leave $it%" },
                    s.chargeMinutes?.let { "about $it min" },
                    s.powerKw?.let { "$it kW" },
                ).joinToString(" · ")
                add(if (detail.isBlank()) s.name else "${s.name}: $detail")
            }
        },
    )
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
