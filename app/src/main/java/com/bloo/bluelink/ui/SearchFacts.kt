package com.bloo.bluelink.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.degLabel
import com.bloo.bluelink.data.displayChargeLimit
import com.bloo.bluelink.data.doorLockLabel
import com.bloo.bluelink.data.engineLabel
import com.bloo.bluelink.data.fmtMinutes
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.isPluggedIn
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.uicommon.AppIcons

/**
 * One thing the app KNOWS about a car, ready to show as an answer: "Battery 80%, charging, 1h 20m to full". Search shows these
 * directly (the live readout, not a control, and not only what the AI says about it), so typing "battery" answers with the
 * battery of every car.
 */
internal class CarFact(
    val car: String,
    val label: String,
    val value: String,
    val detail: String?,
    val icon: ImageVector,
    /** The words that find it, beyond its label and the car's name. */
    val keywords: String,
) {
    /** The same "label · car" title the ranker, the group split and the tests use for every per-car result. */
    val title: String get() = "$label · $car"

    /** What the ranker matches a query against. */
    val haystack: String get() = "$title $value ${detail.orEmpty()} $keywords".lowercase()
}

/** Every fact the loaded cars can answer, from the status the garage already holds. Cars with no reading yet answer nothing. */
internal fun carFacts(state: UiState, appearance: SettingsStore.Appearance): List<CarFact> = buildList {
    state.vehicles.forEach { v ->
        val st = state.statusFor(v) ?: return@forEach
        val name = v.name
        val hasBattery = state.hasBattery(v)
        val ev = st.evStatus
        fun fact(label: String, value: String, detail: String?, icon: ImageVector, keywords: String) =
            add(CarFact(name, label, value, detail?.takeIf { it.isNotBlank() }, icon, keywords))

        st.percentFor(hasBattery)?.let { pct ->
            val detail = if (hasBattery) {
                listOfNotNull(
                    if (ev?.batteryCharge == true) "Charging" else if (ev?.isPluggedIn == true) "Plugged in" else null,
                    ev?.minutesToFull?.let { "${fmtMinutes(it)} to full" },
                    ev?.displayChargeLimit()?.let { "limit $it%" },
                ).joinToString(" · ")
            } else {
                null
            }
            if (hasBattery) {
                fact("Battery", "$pct%", detail, AppIcons.BatteryFull, "battery charge charging soc percent level ev plug plugged limit")
            } else {
                fact("Fuel", "$pct%", null, AppIcons.Bolt, "fuel gas tank percent level petrol")
            }
        }
        if (hasBattery) {
            st.fuelLevel?.let { fact("Fuel", "$it%", null, AppIcons.Bolt, "fuel gas tank percent level petrol") }
        }
        st.rangeMiFor(hasBattery)?.let { fact("Range", formatDistance(it, appearance.metricDistance), null, Icons.Filled.Speed, "range distance dte empty left") }
        st.battery?.level?.let { fact("12V battery", "$it%", st.battery?.health, AppIcons.BatteryAlert, "battery 12v 12 volt auxiliary accessory starter health") }
        doorLockLabel(st.doorLock)?.let { fact("Doors", it, null, AppIcons.Lock, "door doors lock locked unlock unlocked secure") }
        val open = buildList {
            st.doorOpen?.openLabels()?.forEach { add("$it door") }
            st.windowOpen?.openLabels()?.forEach { add("$it window") }
            if (st.trunkOpen == true) add("trunk")
            if (st.hoodOpen == true) add("hood")
        }
        if (st.doorOpen != null || st.windowOpen != null) {
            fact("Open", if (open.isEmpty()) "All closed" else open.joinToString(", "), null, AppIcons.DirectionsCar, "open door doors window windows trunk hood closed ajar")
        }
        st.airCtrlOn?.let { on ->
            val setpoint = st.airTemp?.value?.let { degLabel(it, appearance.useFahrenheit, st.airTemp?.unit) }
            fact("Climate", if (on) "On" else "Off", setpoint?.let { "set to $it" }, AppIcons.Climate, "climate ac heat heater cool air conditioning temperature defrost")
        }
        engineLabel(st.engine)?.let { fact("Engine", it, null, AppIcons.DirectionsCar, "engine running parked ignition on off") }
        parseOdometerMiles(v.odometer)?.let { fact("Odometer", formatDistance(it, appearance.metricDistance), null, Icons.Filled.Speed, "odometer mileage miles km distance driven") }
        (state.placeNames[v.vin] ?: state.locations[v.vin]?.coordString(4))?.let { fact("Location", it, null, Icons.Filled.Place, "location where parked place gps address map") }
    }
}

/** The facts [tokens] ask for, best first: the same ranking every other result uses, so a fact and a setting compete fairly. */
internal fun rankFacts(tokens: List<String>, facts: List<CarFact>): List<CarFact> {
    if (tokens.isEmpty()) return emptyList()
    fun scored(fuzzy: Boolean) = facts.mapNotNull { f ->
        searchScore(tokens, SearchEntry(f.title, f.haystack) {}, fuzzy)?.let { f to it }
    }
    return scored(fuzzy = false).ifEmpty { scored(fuzzy = true) }.sortedByDescending { it.second }.map { it.first }
}
