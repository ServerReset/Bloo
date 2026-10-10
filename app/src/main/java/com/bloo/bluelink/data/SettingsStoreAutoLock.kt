package com.bloo.bluelink.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bloo.bluelink.autolock.AutoLockConfig
import kotlinx.coroutines.flow.first

// --- AutoLock: per-car config, the "cars with AutoLock" registry, and sign-out cleanup ---
//
// Ported from the i5-AutoLock reference app (github.com/Vel-San/i5-AutoLock): locks a car
// automatically when the phone disconnects from its paired Bluetooth device (the head unit),
// confirmed by Activity Recognition (driving -> walking), after a cancellable grace period, and
// only when the car's own status says it's safe to (unlocked, engine off, doors/windows closed). See
// app/.../autolock/ for the state machine, policy and detection plumbing.

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
