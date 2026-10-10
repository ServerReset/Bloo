package com.bloo.bluelink.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bloo.bluelink.ui.FontChoice
import com.bloo.bluelink.ui.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

// --- Per-car identity, layout, AutoLock, climate, palettes, chargers and weather (extracted from SettingsStore) --

suspend fun SettingsStore.licensePlate(vin: String): String = licensePlate(vin, context.settingsDataStore.data.first())

fun SettingsStore.licensePlate(vin: String, p: Preferences): String =

    p[stringPreferencesKey("plate_$vin")] ?: ""

suspend fun SettingsStore.setLicensePlate(vin: String, value: String) {
    editTracked {
        val key = stringPreferencesKey("plate_$vin")
        if (value.isBlank()) it.remove(key) else it[key] = value.trim()
    }
}

suspend fun SettingsStore.lastServiceMiles(vin: String): Int? = lastServiceMiles(vin, context.settingsDataStore.data.first())

fun SettingsStore.lastServiceMiles(vin: String, p: Preferences): Int? =

    p[stringPreferencesKey("svc_last_$vin")]?.toIntOrNull()

suspend fun SettingsStore.setLastServiceMiles(vin: String, value: Int?) {
    editTracked {
        val key = stringPreferencesKey("svc_last_$vin")
        if (value == null) it.remove(key) else it[key] = value.toString()
    }
}

suspend fun SettingsStore.serviceIntervalMiles(vin: String): Int? = serviceIntervalMiles(vin, context.settingsDataStore.data.first())

fun SettingsStore.serviceIntervalMiles(vin: String, p: Preferences): Int? =

    p[stringPreferencesKey("svc_interval_$vin")]?.toIntOrNull()

suspend fun SettingsStore.setServiceIntervalMiles(vin: String, value: Int?) {
    editTracked {
        val key = stringPreferencesKey("svc_interval_$vin")
        if (value == null) it.remove(key) else it[key] = value.toString()
    }
}

suspend fun SettingsStore.lastVehicleVin(): String? = lastVehicleVin(context.settingsDataStore.data.first())

fun SettingsStore.lastVehicleVin(p: Preferences): String? = p[SettingsStore.Keys.LAST_VIN]

suspend fun SettingsStore.setLastVehicleVin(vin: String) {
    editTracked { it[SettingsStore.Keys.LAST_VIN] = vin }
}

/** User-defined display order of vehicles (by VIN). */
suspend fun SettingsStore.vehicleOrder(): List<String> = vehicleOrder(context.settingsDataStore.data.first())

fun SettingsStore.vehicleOrder(p: Preferences): List<String> =

    p[SettingsStore.Keys.ORDER]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

suspend fun SettingsStore.setVehicleOrder(order: List<String>) {
    editTracked { it[SettingsStore.Keys.ORDER] = order.joinToString("\n") }
}

/**
 * Force the settings DataStore's first load now.
 *
 * Its very first read is the expensive one -- opening the file, parsing the preferences
 * protobuf -- and on the cold-start path the auto-login's own `appearance.first()` was
 * paying it, measured at 452ms on the API 34 emulator, directly between the credential
 * load and the garage load. DataStore caches the parsed data in memory after that first
 * read, so loading it on the startup warm-up thread makes the real read ~free.
 */
suspend fun SettingsStore.warmUp() {
    runCatching { context.settingsDataStore.data.first() }
}

/**
 * ONE Preferences snapshot, for a caller about to read many keys at once.
 *
 * Every getter here inlines its own `data.first()`, which after the first read is served
 * from memory but is still a collect-and-cancel round trip on the DataStore actor, and
 * they are sequential suspends. `loadGarageInner` made twelve of them PER CAR -- 36 on a
 * three-car account, on the cold-start critical path, every one returning the identical
 * object -- and `refreshLocalCarConfig` did it again on every settings import.
 *
 * Pair this with the `Preferences`-taking overloads: read once, pass it down. Those
 * overloads exist so the KEY and the DEFAULT stay written exactly once, in the getter --
 * a caller that reached for the raw key itself would be the drift this store exists to
 * prevent.
 */
suspend fun SettingsStore.snapshot(): Preferences {
    com.bloo.bluelink.data.StartupTrace.markIfStarting("SettingsStore.snapshot(): DataStore data.first() begin")
    val prefs = context.settingsDataStore.data.first()
    com.bloo.bluelink.data.StartupTrace.markIfStarting("SettingsStore.snapshot(): DataStore data.first() done")
    return prefs
}

/** Optional user-set photo URL per vehicle (empty = use the default gradient). */
suspend fun SettingsStore.imageUrl(vin: String): String? = imageUrl(vin, context.settingsDataStore.data.first())

fun SettingsStore.imageUrl(vin: String, p: Preferences): String? =

    p[stringPreferencesKey("img_$vin")]?.takeIf { it.isNotBlank() }

suspend fun SettingsStore.setImageUrl(vin: String, url: String) {
    editTracked {
        val key = stringPreferencesKey("img_$vin")
        if (url.isBlank()) it.remove(key) else it[key] = url.trim()
    }
}

/**
 * Reads the per-seat heat/cool capability flags for [vin], each stored under
 * its own short-suffixed key (e.g. "seat_dh_$vin" for driver-heat).
 *
 * Migration: older versions tracked one flag per axle (front/rear heat/cool). Each per-seat key is read
 * first; if absent, the old grouped flag is the fallback, then a hardcoded default, so an old
 * front-heat=true becomes driver and passenger heat on first read with no explicit migration.
 */
suspend fun SettingsStore.seatConfig(vin: String): SeatConfig =

    seatConfig(vin, context.settingsDataStore.data.first())

/**
 * Reads THIRTEEN keys plus an older grouped-flag format, which is exactly why it takes a
 * Preferences: thirteen reads for one car became thirteen DataStore round trips, and
 * loadGarage does this per car.
 */
fun SettingsStore.seatConfig(vin: String, p: Preferences): SeatConfig {
    fun b(key: String): Boolean? = p[booleanPreferencesKey(key)]
    // Migration: older builds stored grouped front/rear flags.
    val oldFrontHeat = b("seat_fh_$vin")
    val oldFrontCool = b("seat_fc_$vin")
    val oldRearHeat = b("seat_rh_$vin")
    val oldRearCool = b("seat_rc_$vin")
    return SeatConfig(
        driverHeat = b("seat_dh_$vin") ?: oldFrontHeat ?: true,
        driverCool = b("seat_dc_$vin") ?: oldFrontCool ?: false,
        passHeat = b("seat_ph_$vin") ?: oldFrontHeat ?: true,
        passCool = b("seat_pc_$vin") ?: oldFrontCool ?: false,
        rearLeftHeat = b("seat_rlh_$vin") ?: oldRearHeat ?: false,
        rearLeftCool = b("seat_rlc_$vin") ?: oldRearCool ?: false,
        rearRightHeat = b("seat_rrh_$vin") ?: oldRearHeat ?: false,
        rearRightCool = b("seat_rrc_$vin") ?: oldRearCool ?: false,
        steeringWheel = b("seat_sw_$vin") ?: false,
    )
}

/** [field] is one of dh/dc/ph/pc/rlh/rlc/rrh/rrc. */
suspend fun SettingsStore.setSeatFlag(vin: String, field: String, value: Boolean) {
    editTracked { it[booleanPreferencesKey("seat_${field}_$vin")] = value }
}

suspend fun SettingsStore.onboardingSeen(): Boolean = onboardingSeen(context.settingsDataStore.data.first())

fun SettingsStore.onboardingSeen(p: Preferences): Boolean = p[booleanPreferencesKey("onboarding_seen")] ?: false

suspend fun SettingsStore.setOnboardingSeen() {
    editTracked { it[booleanPreferencesKey("onboarding_seen")] = true }
}

/** True once a car has been through the feature-setup wizard. */
suspend fun SettingsStore.isCarConfigured(vin: String): Boolean =

    isCarConfigured(vin, context.settingsDataStore.data.first())

fun SettingsStore.isCarConfigured(vin: String, p: Preferences): Boolean =

    p[booleanPreferencesKey("car_configured_$vin")] ?: false

suspend fun SettingsStore.setCarConfigured(vin: String) {
    editTracked { it[booleanPreferencesKey("car_configured_$vin")] = true }
}

/**
 * The order in which detail-pebble sections should render for [vin],
 * reconciled against [DEFAULT_SECTIONS] so app updates that add a brand-new
 * section (or a user's stored list that's stale/corrupt) still produce a
 * complete, valid ordering rather than silently dropping the new section
 * forever.
 *
 * Mechanism: the saved comma-separated order is read and filtered down to
 * only names still present in DEFAULT_SECTIONS (drops anything renamed or
 * removed since). If nothing valid is left, the whole default order is used
 * as-is. Otherwise, any DEFAULT_SECTIONS entries missing from the saved list
 * (i.e. new since the user last customized their order) are inserted:
 * "summary"/"controls" are always pinned back to the very front (in
 * DEFAULT_SECTIONS order) since they're the primary at-a-glance sections;
 * every other missing section is inserted immediately after the nearest
 * section that precedes it in DEFAULT_SECTIONS order and IS present in the
 * user's list, so a newly-added section lands in a sensible relative spot
 * instead of always being tacked onto the end.
 */
suspend fun SettingsStore.sectionOrder(vin: String): List<String> =

    sectionOrder(vin, context.settingsDataStore.data.first())

fun SettingsStore.sectionOrder(vin: String, p: Preferences): List<String> {
    val saved = p[stringPreferencesKey("sections_$vin")]
        ?.split(",")?.filter { it.isNotBlank() }
    val valid = saved?.filter { it in DEFAULT_SECTIONS } ?: emptyList()
    if (valid.isEmpty()) return DEFAULT_SECTIONS
    val result = valid.toMutableList()
    val missing = DEFAULT_SECTIONS.filter { it !in result }
    val (lead, trail) = missing.partition { it == "summary" || it == "controls" }
    // Prepend any missing pinned-lead sections in order.
    lead.reversed().forEach { s -> result.add(0, s) }
    // Insert each remaining new section after its nearest preceding sibling in
    // DEFAULT_SECTIONS order so it lands in a sensible position (e.g. "ai" goes
    // right after "charge" rather than being appended at the end).
    for (section in trail) {
        val defIdx = DEFAULT_SECTIONS.indexOf(section)
        val predecessor = DEFAULT_SECTIONS.subList(0, defIdx).lastOrNull { it in result }
        val pos = if (predecessor != null) result.indexOf(predecessor) + 1 else result.size
        result.add(pos, section)
    }
    return result
}

suspend fun SettingsStore.setSectionOrder(vin: String, order: List<String>) {
    editTracked { it[stringPreferencesKey("sections_$vin")] = order.joinToString(",") }
}

/** Shared helper: reads [key] as a comma-separated string and splits it back
 *  into a Set, dropping empty segments (so a stored empty string decodes to
 *  an empty set rather than a set containing one blank element). Used for
 *  every "set of section names" preference (collapsed/hidden sections here)
 *  since Preferences DataStore has no native Set<String> support for
 *  primitives written as plain strings elsewhere in this file. */
private fun SettingsStore.csv(p: androidx.datastore.preferences.core.Preferences, key: String): Set<String> =

    p[stringPreferencesKey(key)]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

suspend fun SettingsStore.collapsedSections(vin: String): Set<String> = collapsedSections(vin, context.settingsDataStore.data.first())

fun SettingsStore.collapsedSections(vin: String, p: Preferences): Set<String> =

    csv(p, "collapsed_$vin")

/** Toggles [section] in or out of [vin]'s collapsed set: reads the current
 *  CSV-encoded set, adds or removes the section, then re-encodes and writes
 *  it back — a read-modify-write pair inside one editTracked() transaction
 *  so a concurrent write to the same key can't be lost between the read and
 *  the write (DataStore's edit{} block runs with the current prefs snapshot
 *  passed in, not a stale one captured earlier). */
suspend fun SettingsStore.setSectionCollapsed(vin: String, section: String, collapsed: Boolean) {
    editTracked {
        val set = csv(it, "collapsed_$vin").toMutableSet()
        if (collapsed) set.add(section) else set.remove(section)
        it[stringPreferencesKey("collapsed_$vin")] = set.joinToString(",")
    }
}

suspend fun SettingsStore.aiEnabled(): Boolean =

    context.settingsDataStore.data.first()[booleanPreferencesKey("ai_enabled")] ?: false

suspend fun SettingsStore.setAiEnabled(value: Boolean) {
    editTracked { it[booleanPreferencesKey("ai_enabled")] = value }
}

/** The single pebble pinned to the hotspot's secondary slot for [vin], or null if none
 *  selected. The primary slot ("controls") is hardcoded and never persisted here. */
suspend fun SettingsStore.hotspots(vin: String): String? = hotspots(vin, context.settingsDataStore.data.first())

fun SettingsStore.hotspots(vin: String, p: Preferences): String? {
    return p[stringPreferencesKey("hotspots_$vin")]?.takeIf { it.isNotBlank() }
}

suspend fun SettingsStore.setHotspots(vin: String, section: String?) {
    editTracked {
        val key = stringPreferencesKey("hotspots_$vin")
        if (section.isNullOrBlank()) it.remove(key) else it[key] = section
    }
}

/** Null means "not confirmed by the user yet" — the US Hyundai/Genesis API
 *  only distinguishes EV vs. gas, so the app asks the user to disambiguate
 *  hybrid/PHEV during car setup and stores their answer here; callers fall
 *  back to whatever the API-derived guess was when this is null. */
suspend fun SettingsStore.powertrain(vin: String): Powertrain? = powertrain(vin, context.settingsDataStore.data.first())

fun SettingsStore.powertrain(vin: String, p: Preferences): Powertrain? =

    p[stringPreferencesKey("ptrain_$vin")]
        ?.let { runCatching { Powertrain.valueOf(it) }.getOrNull() }

suspend fun SettingsStore.setPowertrain(vin: String, value: Powertrain) {
    editTracked { it[stringPreferencesKey("ptrain_$vin")] = value.name }
}

suspend fun SettingsStore.platform(vin: String): VehiclePlatform? = platform(vin, context.settingsDataStore.data.first())

fun SettingsStore.platform(vin: String, p: Preferences): VehiclePlatform? =

    p[stringPreferencesKey("platform_$vin")]
        ?.let { runCatching { VehiclePlatform.valueOf(it) }.getOrNull() }

suspend fun SettingsStore.setPlatform(vin: String, value: VehiclePlatform) {
    editTracked { it[stringPreferencesKey("platform_$vin")] = value.name }
}

// Remaining global appearance setters (theme/font/dynamic-color/palette):
// each stores its enum's name() (or, for dynamicColor, a "true"/"false"
// string) under its fixed SettingsStore.Keys.* entry; decoding happens once, centrally,
// in the `appearance` Flow above.
suspend fun SettingsStore.setThemeMode(mode: ThemeMode) {
    editTracked { it[SettingsStore.Keys.THEME] = mode.name }
}

suspend fun SettingsStore.setFontChoice(choice: FontChoice) {
    editTracked { it[SettingsStore.Keys.FONT] = choice.name }
}

suspend fun SettingsStore.setDynamicColor(enabled: Boolean) {
    editTracked { it[SettingsStore.Keys.DYNAMIC] = enabled.toString() }
}
