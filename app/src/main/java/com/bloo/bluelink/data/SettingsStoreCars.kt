package com.bloo.bluelink.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bloo.bluelink.autolock.AutoLockConfig
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


/** Optional user-set photo URL per vehicle (empty = use the default gradient). */
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


suspend fun SettingsStore.snapshot(): Preferences {
    com.bloo.bluelink.data.StartupTrace.markIfStarting("SettingsStore.snapshot(): DataStore data.first() begin")
    val prefs = context.settingsDataStore.data.first()
    com.bloo.bluelink.data.StartupTrace.markIfStarting("SettingsStore.snapshot(): DataStore data.first() done")
    return prefs
}


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


suspend fun SettingsStore.autoLockConfig(vin: String): AutoLockConfig =

    autoLockConfig(vin, context.settingsDataStore.data.first())


/**
 * [Preferences]-taking overload, same reason as [seatConfig]/[isCarConfigured]: the
 * Bluetooth receiver checks EVERY registered VIN on every single connect/disconnect
 * event, and each of those used to be its own full `.data.first()` DataStore round trip
 * -- N reads for N configured cars, on every Bluetooth event this phone ever sees, not
 * just the car's own. One snapshot, taken once by the caller (see
 * [autoLockConfiguredVins]'s own overload), serves all of them.
 */
fun SettingsStore.autoLockConfig(vin: String, p: Preferences): AutoLockConfig {
    fun b(key: String, default: Boolean) = p[booleanPreferencesKey(key)] ?: default
    fun s(key: String) = p[stringPreferencesKey(key)]
    fun i(key: String, default: Int) = s(key)?.toIntOrNull() ?: default
    return AutoLockConfig(
        enabled = b("autolock_enabled_$vin", false),
        deviceAddress = s("autolock_device_addr_$vin"),
        deviceName = s("autolock_device_name_$vin"),
        graceSeconds = i("autolock_grace_$vin", 30),
        dryRun = b("autolock_dry_run_$vin", true),
    )
}


/** Every DataStore key one car's [AutoLockConfig] occupies -- named once so
 *  [setAutoLockConfig] (which writes them) and [clearAllAutoLockConfigs] (which removes
 *  them on sign-out) can't drift out of sync with each other the way two hand-written key
 *  lists eventually would.
 *
 *  Includes several keys ("autolock_use_activity_$vin", "autolock_dont_lock_if_open_$vin",
 *  "autolock_use_bt_$vin", "autolock_use_geofence_$vin", "autolock_geofence_radius_$vin")
 *  that [autoLockConfig] no longer reads and [setAutoLockConfig] no longer writes -- those
 *  behaviors are now either hardcoded always-on or removed entirely (geofence). Left in
 *  this list purely so a sign-out still clears any value an older build wrote for them,
 *  rather than leaving orphaned keys behind. */
private fun SettingsStore.autoLockKeys(vin: String) = listOf(
    booleanPreferencesKey("autolock_enabled_$vin"),
    stringPreferencesKey("autolock_device_addr_$vin"),
    stringPreferencesKey("autolock_device_name_$vin"),
    booleanPreferencesKey("autolock_use_bt_$vin"),
    stringPreferencesKey("autolock_grace_$vin"),
    booleanPreferencesKey("autolock_use_activity_$vin"),
    booleanPreferencesKey("autolock_use_geofence_$vin"),
    stringPreferencesKey("autolock_geofence_radius_$vin"),
    booleanPreferencesKey("autolock_dont_lock_if_open_$vin"),
    booleanPreferencesKey("autolock_dry_run_$vin"),
)


suspend fun SettingsStore.setAutoLockConfig(vin: String, config: AutoLockConfig) {
    editTracked {
        it[booleanPreferencesKey("autolock_enabled_$vin")] = config.enabled
        val addrKey = stringPreferencesKey("autolock_device_addr_$vin")
        if (config.deviceAddress == null) it.remove(addrKey) else it[addrKey] = config.deviceAddress
        val nameKey = stringPreferencesKey("autolock_device_name_$vin")
        if (config.deviceName == null) it.remove(nameKey) else it[nameKey] = config.deviceName
        it[stringPreferencesKey("autolock_grace_$vin")] = config.graceSeconds.toString()
        it[booleanPreferencesKey("autolock_dry_run_$vin")] = config.dryRun
    }
    // Maintain the registry of "cars with AutoLock configured" so the Bluetooth
    // receiver -- which starts from a raw device MAC or a VIN, not a UI selection -- can
    // enumerate every car to check instead of needing one BroadcastReceiver registration
    // per car.
    editTracked {
        val key = stringPreferencesKey("autolock_vins")
        val current = (it[key] ?: "").split(',').filter { s -> s.isNotBlank() }.toMutableSet()
        if (config.enabled) current += vin else current -= vin
        it[key] = current.joinToString(",")
    }
}


/** Every VIN that has ever had AutoLock enabled -- see [setAutoLockConfig]'s registry
 *  note. Used by [com.bloo.bluelink.autolock.AutoLockBluetoothReceiver] to find which
 *  car(s), if any, a disconnected Bluetooth device belongs to. */
suspend fun SettingsStore.autoLockConfiguredVins(): List<String> =

    autoLockConfiguredVins(context.settingsDataStore.data.first())


fun SettingsStore.autoLockConfiguredVins(p: Preferences): List<String> =

    (p[stringPreferencesKey("autolock_vins")] ?: "").split(',').filter { it.isNotBlank() }


/** Forgets AutoLock entirely for every currently-registered car -- called on a full
 *  sign-out (see [com.bloo.bluelink.ui.AppViewModel.logout]) alongside the other
 *  account-derived stores it already wipes there (statusCache, snapshotStore). Without
 *  this, a signed-out car's VIN stayed in the registry forever: every Bluetooth
 *  connect/disconnect this phone ever saw kept checking it, futilely, against a vehicle
 *  [com.bloo.bluelink.autolock.AutoLockController] can never find again. */
suspend fun SettingsStore.clearAllAutoLockConfigs() {
    val vins = autoLockConfiguredVins()
    if (vins.isEmpty()) return
    editTracked {
        it.remove(stringPreferencesKey("autolock_vins"))
        // MutablePreferences.remove is generic on the key's own value type
        // (fun <T> remove(key: Preferences.Key<T>): T) and autoLockKeys' mixed
        // Boolean/String keys collapse to Preferences.Key<*> in the list -- Kotlin can't
        // infer T from a star projection, so the type parameter is pinned to Any here
        // instead (an unchecked but safe cast: remove() only ever reads the key's
        // identity, never the value type, to find and drop the entry).
        @Suppress("UNCHECKED_CAST")
        vins.forEach { vin -> autoLockKeys(vin).forEach { key -> it.remove(key as Preferences.Key<Any>) } }
    }
}


/** One DataStore read for every registered car's full [AutoLockConfig] -- what the
 *  Bluetooth receiver actually wants (a device MAC or a VIN comes in, every configured
 *  car needs checking against it), instead of the registry list plus one [autoLockConfig]
 *  round trip per car. */
suspend fun SettingsStore.allAutoLockConfigs(): Map<String, AutoLockConfig> {
    val p = context.settingsDataStore.data.first()
    return autoLockConfiguredVins(p).associateWith { autoLockConfig(it, p) }
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
