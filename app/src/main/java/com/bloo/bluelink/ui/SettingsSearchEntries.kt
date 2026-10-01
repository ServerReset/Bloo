@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/** Search results surface: the ranked settings/car-data result list, its stagger
 *  timing constant, and the per-result pop-in helper. Peeled out of
 *  SettingsSearch.kt into its own file. */

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bloo.bluelink.autolock.AutoLockConfig
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.displayChargeLimit
import com.bloo.bluelink.data.parseOdometerMiles
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.lastServiceMiles
import com.bloo.bluelink.data.platform
import com.bloo.bluelink.data.serviceIntervalMiles
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setFontChoice
import com.bloo.bluelink.data.setLastServiceMiles
import com.bloo.bluelink.data.setLicensePlate
import com.bloo.bluelink.data.setLockTiming
import com.bloo.bluelink.data.setPlatform
import com.bloo.bluelink.data.setPowertrain
import com.bloo.bluelink.data.setServiceIntervalMiles
import com.bloo.bluelink.data.setShowSearch
import com.bloo.bluelink.data.setThemeMode
import com.bloo.bluelink.data.setUnitSystem
import com.bloo.bluelink.data.settingsMode
import com.bloo.bluelink.data.unitSystem

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
        add(spec.title, spec.keywords) {
            ToggleRow(spec.label, spec.checked(appearance, notif, state)) { spec.onToggle(vm, it) }
        }
    }
    // Two of Security's own controls, missing from here entirely -- "biometric"
    // and "lock" are exactly the words someone would type for this. Reproduces
    // the real card's logic verbatim (down to the same confirm-to-disable
    // biometric prompt, not a bare toggle) rather than a simplified stand-in,
    // since a security control is the one place a search shortcut skipping a
    // step the real row enforces would be a genuine regression, not just a
    // visual inconsistency.
    if (canBio) {
        add("Require biometrics to open", "biometric lock security app unlock") {
            // LocalContext.current itself has to stay inside this entry's own @Composable
            // content lambda, not hoisted above -- reading a CompositionLocal is a composable
            // call, and the entries list above is built inside a plain (non-composable)
            // remember calculation now.
            val bioContext = LocalContext.current
            SettingsSegmentedRow(
                label = "Require biometrics to open",
                options = listOf(
                    SegmentOption("off", "Off", null),
                    SegmentOption("on", "On", null),
                ),
                selectedKey = if (appearance.biometricLock) "on" else "off",
                onSelect = { key ->
                    if (key == "on") {
                        bioContext.findFragmentActivity()?.let { activity ->
                            showBiometricPrompt(
                                activity = activity,
                                title = "Enable biometric lock",
                                subtitle = "Confirm to require it on launch",
                                onSuccess = { vm.setBiometricLock(true) },
                                onError = { },
                            )
                        }
                    } else {
                        val activity = bioContext.findFragmentActivity()
                        if (activity == null) {
                            vm.reportInfo("Couldn't verify it's you. The lock is still on.")
                        } else {
                            showBiometricPrompt(
                                activity = activity,
                                title = "Disable biometric lock",
                                subtitle = "Confirm to stop requiring it",
                                onSuccess = { vm.setBiometricLock(false) },
                                onError = { },
                            )
                        }
                    }
                },
            )
        }
        if (appearance.biometricLock) {
            add("Lock timing", "lock the app grace period timeout re-lock security") {
                SettingsSegmentedRow(
                    label = "Lock the app",
                    options = LockTiming.entries.map { t -> SegmentOption(t.name, t.label, null) },
                    selectedKey = appearance.lockTiming.name,
                    onSelect = { key -> runCatching { vm.setLockTiming(LockTiming.valueOf(key)) } },
                )
            }
        }
    }
    add("Text & layout scale", "display size zoom bigger") {
        // Deferred-commit, same as the main Appearance card's slider — see there.
        UiScaleSlider(appearance, vm, label = "Scale")
    }
    add("Colour vibrancy", "color saturation vivid material you monochrome best buy tv") {
        // Deferred-commit, same as the main Appearance card's slider — see there.
        VibrancySlider(appearance, vm)
    }
    add("Search on the car screen", "search bubble car screen cover home garage ask command") {
        ToggleRow("Search on the car screen", appearance.showSearch) { vm.setShowSearch(it) }
    }
    add("Units", "unit system metric imperial temperature distance speed miles km") {
        SettingsSegmentedRow(
            label = "Units",
            options = listOf(
                SegmentOption("imperial", "Imperial", null),
                SegmentOption("metric", "Metric", null),
            ),
            selectedKey = appearance.unitSystem,
            onSelect = { vm.setUnitSystem(it) },
        )
    }
    add("Font", "typeface atkinson hyperlegible google sans accessibility low vision") {
        val labels = mapOf(
            FontChoice.SYSTEM to "System default",
            FontChoice.ATKINSON to "Atkinson Hyperlegible",
            FontChoice.GOOGLE_SANS to "Google Sans",
        )
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            FontChoice.entries.forEach { choice ->
                ChoiceRow(labels.getValue(choice), appearance.fontChoice == choice) { vm.setFontChoice(choice) }
            }
        }
    }
    add("Display mode", "theme light dark amoled oled system appearance") {
        SettingsSegmentedRow(
            label = "Appearance",
            options = listOf(
                SegmentOption(ThemeMode.SYSTEM.name, "System", null),
                SegmentOption(ThemeMode.LIGHT.name, "Light", null),
                SegmentOption(ThemeMode.DARK.name, "Dark", null),
            ),
            selectedKey = appearance.themeMode.name,
            onSelect = { vm.setThemeMode(ThemeMode.valueOf(it)) },
        )
    }
    // --- Per-car ---
    state.vehicles.forEach { v ->
        val st = state.statusFor(v)
        val plate = state.licensePlates[v.vin] ?: ""
        add("License plate · ${v.name}", "plate licence registration ${v.name} $plate") {
            BlooTextField(
                value = plate,
                onValueChange = { vm.setLicensePlate(v.vin, it) },
                label = { Text("License plate") },
                singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
            )
        }
        parseOdometerMiles(v.odometer)?.let { odoInt ->
            add("Odometer · ${v.name}", "odometer mileage miles ${v.name}") { StatusRow("Odometer", formatDistance(odoInt, appearance.unitSystem == "metric")) }
        }
        add("VIN · ${v.name}", "vin identification ${v.name} ${v.vin}") {
            SelectionContainer { StatusRow("VIN", v.vin) }
        }
        // VehicleStatus.rangeMiFor -- already imported, and its body was copied here
        // character-for-character (battery-range-else-null ?: dte, then toInt). One source of
        // truth for "what range do we show for this powertrain".
        st?.rangeMiFor(state.hasBattery(v))?.let { r ->
            add("Range · ${v.name}", "range distance dte empty ${v.name}") { StatusRow("Range", formatDistance(r, appearance.unitSystem == "metric")) }
        }
        if (state.hasBattery(v)) {
            st?.evStatus?.batteryStatus?.let { b ->
                add("Battery · ${v.name}", "battery charge soc percent ${v.name}") { StatusRow("Battery", "$b%") }
            }
            // Current-plug target if plugged in, else the configured AC home limit --
            // now the shared EvStatus.displayChargeLimit(), this call site's own fallback
            // generalized so every surface agrees rather than re-deriving it.
            val limit = st?.evStatus?.displayChargeLimit()
            limit?.let { l -> add("Charge limit · ${v.name}", "charge limit target ${v.name}") { StatusRow("Charge limit", "$l%") } }
        } else {
            st?.fuelLevel?.let { f ->
                add("Fuel · ${v.name}", "fuel gas tank percent ${v.name}") { StatusRow("Fuel", "$f%") }
            }
        }
        // rememberRelativeTime itself (not its result) has to stay inside the entry's own
        // @Composable content lambda -- it's a live, ticking value (its own LaunchedEffect
        // re-labels it as it ages), not something this remember block can capture once and
        // freeze. Gate on the raw timestamp instead of the old string result; identical
        // nullability (rememberRelativeTime(null) is exactly what returned null before).
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
        // Same gate CarSettingsCard's own group uses -- nothing to confirm for
        // a vehicle where this picker would have no effect either way.
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
        // The interval, which had no search entry while "Last service" above did. The two
        // are only meaningful TOGETHER -- the service pebble's whole output is
        // `last + interval` -- so search let you set one half of a sum and hid the other,
        // leaving a "next due" figure that could not be corrected from here. Both fields
        // sit side by side in the per-car section; only the index had one of them.
        add("Service interval · ${v.name}", "service interval maintenance mileage due ${v.name}") {
            MilesField(state.serviceIntervalMiles[v.vin], "Interval (mi)", Modifier.fillMaxWidth()) {
                vm.setServiceIntervalMiles(v.vin, it)
            }
        }
        // The dynamic half of the per-car index: every seat heat/cool flag,
        // the steering wheel, and every hideable dashboard section for THIS
        // car, from VehicleToggleSettings -- no per-car, per-toggle code.
        VehicleToggleSettings.forEach { spec ->
            if (!spec.visible(v, state)) return@forEach
            add(spec.title(v), spec.keywords(v)) {
                ToggleRow(spec.label, spec.checked(v, state)) { spec.onToggle(vm, v, it) }
            }
        }
        // AutoLock's own toggles, same one-entry-per-toggle granularity as everything else in
        // this per-car section -- NOT drivable through VehicleToggleSpec/[checked] like the
        // seat/section ones above, though, since AutoLockConfig lives in SettingsStore's own
        // DataStore keys rather than UiState: there's nothing synchronous here to read it
        // from. AutoLockSearchToggle (below) loads it itself, same LaunchedEffect(v.vin)
        // pattern AutoLockSettingsGroup's own Settings card already uses.
        add("AutoLock · ${v.name}", "autolock automatic lock leave walk away bluetooth disconnect ${v.name}") {
            AutoLockSearchToggle(v, vm, "Enabled", { it.enabled }, { c, x -> c.copy(enabled = x) })
        }
        add("AutoLock dry run · ${v.name}", "autolock dry run test simulate safety ${v.name}") {
            AutoLockSearchToggle(v, vm, "Dry run", { it.dryRun }, { c, x -> c.copy(dryRun = x) })
        }
    }
    return entries
}
