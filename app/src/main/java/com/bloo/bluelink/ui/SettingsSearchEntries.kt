package com.bloo.bluelink.ui

/**
 * Search results surface: the ranked settings/car-data result list, its stagger timing constant,
 * and the per-result pop-in helper. Peeled out of SettingsSearch.kt into its own file.
 */

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bloo.bluelink.data.SettingsCatalog
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.displayChargeLimit
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.lastServiceMiles
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.serviceIntervalMiles
import com.bloo.bluelink.data.setLastServiceMiles
import com.bloo.bluelink.data.setLicensePlate
import com.bloo.bluelink.data.setPlatform
import com.bloo.bluelink.data.setPowertrain
import com.bloo.bluelink.data.setServiceIntervalMiles
import com.bloo.bluelink.data.settingsMode
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.SafeMorphTextButton
import com.bloo.uicommon.FieldShape
import com.bloo.uicommon.content

/**
 * Every searchable setting as a [SearchEntry]: its title, the words that find it, and the control
 * itself to draw in the result. Built from the live state so each control shows its real value, and
 * peeled out of [SettingsSearchResults] so that composable only deals with matching and showing.
 */
internal fun buildSettingsSearchEntries(
    state: UiState,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
    vm: AppViewModel,
    canBio: Boolean,
    /** Every stored preference, so settings with no control of their own can still be found and read. */
    prefs: Map<String, Any> = emptyMap(),
): List<SearchEntry> {
    val entries = ArrayList<SearchEntry>()
    fun add(title: String, keywords: String, content: @Composable () -> Unit) {
        // Filter by mode: don't add advanced-only settings when in simple mode
        if (!isSettingAvailableInMode(title, state.settingsMode)) return
        entries.add(SearchEntry(title, "$title $keywords".lowercase(), content))
    }

    // --- App-wide settings ---
    // The dynamic half of the index: every plain toggle in ToggleSettings
    // renders itself here with no per-toggle code -- see that list's own doc
    // comment for what does and doesn't fit this shape.
    ToggleSettings.forEach { spec ->
        if (!spec.visible(state)) return@forEach
        // Additional mode filter for ToggleSettings
        if (!isSettingAvailableInMode(spec.title, state.settingsMode)) return@forEach
        add(spec.title, "${spec.keywords} ${spec.phrases}") {
            ToggleRow(spec.rowLabel, spec.checked(appearance, notif, state)) { spec.onToggle(vm, it) }
        }
    }
    // Two of Security's own controls, missing from here entirely -- "biometric" and "lock" are
    // exactly the words someone would type for this.
    if (canBio) {
        add("App lock", "biometric biometrics fingerprint face lock security app unlock require timing grace re-lock screen off immediate") {
            AppLockRow(state, appearance, vm, LocalContext.current)
        }
    }
    // The welcome-cards replay action: SettingsCardLook's Visuals card draws this button by hand, so
    // unlike the registry-driven settings it needs its own search entry. "welcome cards" is exactly
    // what someone would type for it.
    add("Show welcome cards", "welcome cards onboarding intro tour replay walkthrough tips tutorial bring back show visuals") {
        SafeMorphTextButton("Show welcome cards", onClick = { vm.showWelcomeCards() }, icon = Icons.Filled.Style)
    }
    // Every range and every picker, straight from the registry: no per-setting code here.
    (RangeSettings + ChoiceSettings).forEach { spec ->
        if (!spec.visible(state)) return@forEach
        add(spec.title, "${spec.keywords} ${spec.phrases}") { spec.content(appearance, notif, state, vm) }
    }
    // --- Per-car ---
    state.vehicles.forEach { v ->
        val st = state.statusFor(v)
        val plate = state.licensePlates[v.vin] ?: ""
        add("Licence plate · ${v.name}", "plate licence registration ${v.name} $plate") {
            BlooTextField(
                value = plate,
                onValueChange = { vm.setLicensePlate(v.vin, it) },
                label = { RevealText("Licence plate") },
                singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
            )
        }
        parseOdometerMiles(v.odometer)?.let { odoInt ->
            add("Odometer · ${v.name}", "odometer mileage miles ${v.name}") { StatusRow("Odometer", formatDistance(odoInt, appearance.metricDistance)) }
        }
        // The last six digits as their own word: that is how a VIN is usually quoted.
        add("VIN · ${v.name}", "vin identification ${v.name} ${v.vin} ${v.vin.takeLast(6)}") {
            SelectionContainer { StatusRow("VIN", v.vin) }
        }
        // One source of truth for "what range do we show for this powertrain".
        st?.rangeMiFor(state.hasBattery(v))?.let { r ->
            add("Range · ${v.name}", "range distance dte empty ${v.name}") { StatusRow("Range", formatDistance(r, appearance.metricDistance)) }
        }
        if (state.hasBattery(v)) {
            st?.evStatus?.batteryStatus?.let { b ->
                add("Battery · ${v.name}", "battery charge soc percent ${v.name}") { StatusRow("Battery", "$b%") }
            }
            val limit = st?.evStatus?.displayChargeLimit()
            limit?.let { l -> add("Charge limit · ${v.name}", "charge limit target ${v.name}") { StatusRow("Charge limit", "$l%") } }
        } else {
            st?.fuelLevel?.let { f ->
                add("Fuel · ${v.name}", "fuel gas tank percent ${v.name}") { StatusRow("Fuel", "$f%") }
            }
        }
        // rememberRelativeTime itself (not its result) has to stay inside the entry's own
        // @Composable content lambda -- it's a live, ticking value (its own LaunchedEffect
        // re-labels it as it ages), not something this remember block can capture once and freeze.
        if (state.fetchedAt(v) != null) {
            add("Last refreshed · ${v.name}", "updated refreshed time ${v.name}") {
                rememberRelativeTime(state.fetchedAt(v))?.let { rel -> StatusRow("Last refreshed", rel) }
            }
        }
        (state.placeNames[v.vin] ?: state.locations[v.vin]?.coordString(4))?.let { loc ->
            add("Location · ${v.name}", "location where place gps ${v.name}") { StatusRow("Location", loc) }
        }
        add("Powertrain · ${v.name}", "powertrain ev gas hybrid phev ${v.name}") {
            PowertrainPicker(current = state.powertrainOf(v)) { pt -> vm.setPowertrain(v, pt) }
        }
        // Same gate CarSettingsCard's own group uses -- nothing to confirm for a vehicle where this
        // picker would have no effect either way.
        if (v.platformOverridable) {
            add("Head-unit generation · ${v.name}", "gen5w ccnc platform generation trips ${v.name}") {
                PlatformPicker(current = state.platformOf(v)) { pt -> vm.setPlatform(v, pt) }
            }
        }
        add("Last service · ${v.name}", "service maintenance mileage ${v.name}") {
            MilesField(state.lastServiceMiles[v.vin], "Last service (mi)", Modifier.fillMaxWidth()) {
                vm.setLastServiceMiles(v.vin, it)
            }
        }
        // The interval, which had no search entry while "Last service" above did. Both fields sit
        // side by side in the per-car section; only the index had one of them.
        add("Service interval · ${v.name}", "service interval maintenance mileage due ${v.name}") {
            MilesField(state.serviceIntervalMiles[v.vin], "Interval (mi)", Modifier.fillMaxWidth()) {
                vm.setServiceIntervalMiles(v.vin, it)
            }
        }
        // The dynamic half of the per-car index: every seat heat/cool flag, the steering wheel, and
        // every hideable dashboard section for THIS car, from VehicleToggleSettings -- no per-car,
        // per-toggle code.
        VehicleToggleSettings.forEach { spec ->
            if (!spec.visible(v, state)) return@forEach
            add(spec.title(v), spec.keywords(v)) {
                ToggleRow(spec.label, spec.checked(v, state)) { spec.onToggle(vm, v, it) }
            }
        }
        // AutoLock's own toggles, same one-entry-per-toggle granularity as everything else in this
        // per-car section -- NOT drivable through VehicleToggleSpec/[checked] like the seat/section
        // ones above, though, since AutoLockConfig lives in SettingsStore's own DataStore keys
        // rather than UiState: there's nothing synchronous here to read it from.
        add("AutoLock · ${v.name}", "autolock automatic lock leave walk away bluetooth disconnect ${v.name}") {
            AutoLockSearchToggle(v, vm, "Enabled", { it.enabled }, { c, x -> c.copy(enabled = x) })
        }
        add("AutoLock grace period · ${v.name}", "autolock grace period seconds wait delay countdown before locking ${v.name} how long before it locks") {
            AutoLockGraceSearchRow(v, vm)
        }
        com.bloo.bluelink.Shortcuts.ACTIONS.forEach { cmd ->
            val action = com.bloo.bluelink.Shortcuts.actionLabel(cmd)
            add("Shortcut: $action · ${v.name}", "app shortcut launcher icon long press quick action $action ${v.name}") {
                ToggleRow(action, state.isShortcutEnabled(v.vin, cmd)) { vm.setShortcutEnabled(v.vin, cmd, it) }
            }
        }
        add("AutoLock dry run · ${v.name}", "autolock dry run test simulate safety ${v.name}") {
            AutoLockSearchToggle(v, vm, "Dry run", { it.dryRun }, { c, x -> c.copy(dryRun = x) })
        }
    }
    // The rest of the store: every searchable setting the catalog knows that has no control above,
    // read straight from the same preferences Drive sync carries, so what syncs is what can be found.
    addStructuralEntries(entries, state, appearance, vm)
    addCatalogReadOnlyEntries(entries, state, prefs)
    return entries
}

/**
 * Read-only results for the catalog settings that have no control of their own in search (colour
 * palette, car order, weather place, pinned pebble, photo set or not...). Per-car families get one
 * entry per car. Anything that already has a control is skipped, so nothing appears twice.
 */
private fun addCatalogReadOnlyEntries(entries: MutableList<SearchEntry>, state: UiState, prefs: Map<String, Any>) {
    SettingsCatalog.searchable.filter { !it.control }.forEach { family ->
        fun add(title: String, key: String, car: String?) {
            if (!isSettingAvailableInMode(family.title, state.settingsMode)) return
            val value = SettingsCatalog.describe(family, prefs[key])
            entries.add(
                SearchEntry(title, "$title ${family.keywords} ${car.orEmpty()}".lowercase()) { StatusRow(family.title, value) },
            )
        }
        when (family.scope) {
            SettingsCatalog.Scope.APP -> add(family.title, family.key, null)
            SettingsCatalog.Scope.CAR -> state.vehicles.forEach { v -> add("${family.title} · ${v.name}", family.key + v.vin, v.name) }
        }
    }
}
