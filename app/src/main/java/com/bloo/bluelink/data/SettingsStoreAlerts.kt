package com.bloo.bluelink.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

// --- Notification prefs, alert bookkeeping, and search bubble (extracted from SettingsStore) --

internal fun SettingsStore.decodeNotificationPrefs(p: Preferences): SettingsStore.NotificationPrefs =
    SettingsStore.NotificationPrefs(
        service = p[booleanPreferencesKey(AlertKeys.SERVICE)] ?: true,
        doorOpen = p[booleanPreferencesKey(AlertKeys.DOOR)] ?: true,
        doorOpenMinutes = p.intOr(stringPreferencesKey(AlertKeys.DOOR_MINUTES), 5),
        running = p[booleanPreferencesKey(AlertKeys.RUNNING)] ?: true,
        runningMinutes = p.intOr(stringPreferencesKey(AlertKeys.RUNNING_MINUTES), 10),
        unlocked = p[booleanPreferencesKey(AlertKeys.UNLOCKED)] ?: true,
        unlockedMinutes = p.intOr(stringPreferencesKey(AlertKeys.UNLOCKED_MINUTES), 10),
        charging = p[booleanPreferencesKey(AlertKeys.CHARGING)] ?: true,
        autoLockAlerts = p[booleanPreferencesKey(AlertKeys.AUTOLOCK)] ?: true,
        carStarted = p[booleanPreferencesKey(AlertKeys.CAR_STARTED)] ?: true,
        chargeComplete = p[booleanPreferencesKey(AlertKeys.CHARGE_COMPLETE)] ?: true,
        watchLowBattery = p[booleanPreferencesKey(AlertKeys.WATCH_LOW_BATTERY)] ?: true,
    )

/**
 * One-shot read of [SettingsStore.NotificationPrefs] (vs. the [notifications] Flow below, which
 * stays subscribed) - used where a caller just needs the current values once, e.g. deciding whether
 * to schedule a check at all.
 */
suspend fun SettingsStore.notificationPrefs(): SettingsStore.NotificationPrefs =
    decodeNotificationPrefs(context.settingsDataStore.data.first())

// One setter per SettingsStore.NotificationPrefs field: the switches are stored as booleans, the minute thresholds as text.
private suspend fun SettingsStore.putSwitch(key: String, v: Boolean) { editTracked { it[booleanPreferencesKey(key)] = v } }

private suspend fun SettingsStore.putMinutes(key: String, v: Int) { editTracked { it[stringPreferencesKey(key)] = v.toString() } }

suspend fun SettingsStore.setNotifyService(v: Boolean) = putSwitch(AlertKeys.SERVICE, v)
suspend fun SettingsStore.setNotifyDoor(v: Boolean) = putSwitch(AlertKeys.DOOR, v)
suspend fun SettingsStore.setDoorOpenMinutes(v: Int) = putMinutes(AlertKeys.DOOR_MINUTES, v)
suspend fun SettingsStore.setNotifyRunning(v: Boolean) = putSwitch(AlertKeys.RUNNING, v)
suspend fun SettingsStore.setRunningMinutes(v: Int) = putMinutes(AlertKeys.RUNNING_MINUTES, v)
suspend fun SettingsStore.setNotifyUnlocked(v: Boolean) = putSwitch(AlertKeys.UNLOCKED, v)
suspend fun SettingsStore.setUnlockedMinutes(v: Int) = putMinutes(AlertKeys.UNLOCKED_MINUTES, v)
suspend fun SettingsStore.setNotifyCharging(v: Boolean) = putSwitch(AlertKeys.CHARGING, v)
suspend fun SettingsStore.setNotifyAutoLock(v: Boolean) = putSwitch(AlertKeys.AUTOLOCK, v)
suspend fun SettingsStore.setNotifyCarStarted(v: Boolean) = putSwitch(AlertKeys.CAR_STARTED, v)
suspend fun SettingsStore.setNotifyChargeComplete(v: Boolean) = putSwitch(AlertKeys.CHARGE_COMPLETE, v)
suspend fun SettingsStore.setNotifyWatchLowBattery(v: Boolean) = putSwitch(AlertKeys.WATCH_LOW_BATTERY, v)

// Mechanism: when AlertWorker (see work/AlertWorker.kt) first observes a door open (or engine
// running), it stamps "door_since_$vin"/"engine_since_$vin" with the current time via the setters
// below.
suspend fun SettingsStore.doorOpenSince(vin: String): Long? =
    context.settingsDataStore.data.first()[stringPreferencesKey("door_since_$vin")]?.toLongOrNull()

suspend fun SettingsStore.setDoorOpenSince(vin: String, value: Long?) {
    editTracked {
        val k = stringPreferencesKey("door_since_$vin")
        it.putOrRemove(k, value)
    }
}

/**
 * Where the user last parked the floating search bubble, as fractions (0f..1f) of its own drag
 * range -- null until it has been dragged at least once.
 */
suspend fun SettingsStore.searchBubblePosition(): Pair<Float, Float>? {
    val prefs = context.settingsDataStore.data.first()
    val x = prefs[SettingsStore.Keys.SEARCH_BUBBLE_X]?.toFloatOrNull() ?: return null
    val y = prefs[SettingsStore.Keys.SEARCH_BUBBLE_Y]?.toFloatOrNull() ?: return null
    return x to y
}

/**
 * Persists the bubble's resting fractions -- called once per drag gesture (on release), not per
 * frame, from SearchLayer's onDragEnd.
 */
suspend fun SettingsStore.setSearchBubblePosition(xFrac: Float, yFrac: Float) {
    editTracked {
        it[SettingsStore.Keys.SEARCH_BUBBLE_X] = xFrac.toString()
        it[SettingsStore.Keys.SEARCH_BUBBLE_Y] = yFrac.toString()
    }
}

suspend fun SettingsStore.engineOnSince(vin: String): Long? =
    context.settingsDataStore.data.first()[stringPreferencesKey("engine_since_$vin")]?.toLongOrNull()

suspend fun SettingsStore.setEngineOnSince(vin: String, value: Long?) {
    editTracked {
        val k = stringPreferencesKey("engine_since_$vin")
        it.putOrRemove(k, value)
    }
}

suspend fun SettingsStore.unlockedSince(vin: String): Long? =
    context.settingsDataStore.data.first()[stringPreferencesKey("unlocked_since_$vin")]?.toLongOrNull()

suspend fun SettingsStore.setUnlockedSince(vin: String, value: Long?) {
    editTracked {
        val k = stringPreferencesKey("unlocked_since_$vin")
        it.putOrRemove(k, value)
    }
}

/**
 * Whether a specific alert (identified by an arbitrary caller-defined [key], typically something
 * like "door_$vin" or "running_$vin") has already fired for its current occurrence, so callers
 * don't notify twice for the same continuous door-open/engine-running spell.
 */
suspend fun SettingsStore.alertFired(key: String): Boolean =
    context.settingsDataStore.data.first()[booleanPreferencesKey("alert_$key")] ?: false

suspend fun SettingsStore.setAlertFired(key: String, value: Boolean) {
    editTracked { it[booleanPreferencesKey("alert_$key")] = value }
}

/**
 * Whether the user swiped away [vin]'s live charging bar during the CURRENT charging session.
 * Google's Live Updates guidance is explicit that a dismissed Live Update must not be reposted, and
 * this notification is otherwise re-posted every five minutes for as long as the charge lasts.
 */
suspend fun SettingsStore.liveChargeDismissed(vin: String): Boolean =
    context.settingsDataStore.data.first()[booleanPreferencesKey("live_dismissed_$vin")] ?: false

suspend fun SettingsStore.setLiveChargeDismissed(vin: String, value: Boolean) {
    editTracked { it[booleanPreferencesKey("live_dismissed_$vin")] = value }
}

/** Track the previous engine state per VIN to detect "started" transitions. */
suspend fun SettingsStore.engineStartNotificationSent(vin: String): Boolean =
    context.settingsDataStore.data.first()[booleanPreferencesKey("notif_start_sent_$vin")] ?: false

suspend fun SettingsStore.setEngineStartNotificationSent(vin: String, value: Boolean) {
    editTracked { it[booleanPreferencesKey("notif_start_sent_$vin")] = value }
}

/** Track the previous charging state per VIN to detect "complete" transitions. */
suspend fun SettingsStore.chargeCompleteNotificationSent(vin: String): Boolean =
    context.settingsDataStore.data.first()[booleanPreferencesKey("notif_complete_sent_$vin")] ?: false

suspend fun SettingsStore.setChargeCompleteNotificationSent(vin: String, value: Boolean) {
    editTracked { it[booleanPreferencesKey("notif_complete_sent_$vin")] = value }
}

suspend fun SettingsStore.setUiScale(value: Float) {
    editTracked { it[SettingsStore.Keys.UI_SCALE] = value.toString() }
}

suspend fun SettingsStore.setGlassClarity(value: Float) {
    editTracked { it[SettingsStore.Keys.GLASS_CLARITY] = value.coerceIn(0f, 1f).toString() }
}

suspend fun SettingsStore.setUltraGlass(value: Boolean) {
    putFlag(SettingsStore.Keys.ULTRA_GLASS, value)
}

suspend fun SettingsStore.setVibrancy(value: Float) {
    editTracked { it[SettingsStore.Keys.VIBRANCY] = value.toString() }
}

suspend fun SettingsStore.setAuroraBackground(value: Boolean) {
    putFlag(SettingsStore.Keys.AURORA, value)
}

// The setters below validate the incoming string against the fixed set of legal values and silently
// fall back to the default if it's anything else (e.g. a stale string from a future app version we
// don't recognize), rather than storing garbage that the appearance Flow above would then have to
// re-validate on every read.
suspend fun SettingsStore.setUnitSystem(value: String) {
    editTracked { it[SettingsStore.Keys.UNIT_SYSTEM] = value.takeIf { it in setOf("imperial", "metric") } ?: "imperial" }
}

suspend fun SettingsStore.setTempUnit(value: String) {
    editTracked { it[SettingsStore.Keys.TEMP_UNIT] = value.takeIf { it in setOf("auto", "f", "c") } ?: "auto" }
}

suspend fun SettingsStore.setDistanceUnit(value: String) {
    editTracked { it[SettingsStore.Keys.DISTANCE_UNIT] = value.takeIf { it in setOf("auto", "mi", "km") } ?: "auto" }
}

/**
 * True when distances are shown in km, honouring the distance override -- the one-shot read for
 * non-Compose callers (CarAlerts building a notification string) that the [appearance] flow serves
 * elsewhere.
 */
suspend fun SettingsStore.metricDistance(): Boolean {
    val p = context.settingsDataStore.data.first()
    // Overrides only count in Advanced mode (see the Appearance decode).
    val override = p[SettingsStore.Keys.DISTANCE_UNIT].takeIf { p[SettingsStore.Keys.SETTINGS_MODE] == "advanced" }
    return resolveMetricDistance(p[SettingsStore.Keys.UNIT_SYSTEM], override)
}

/** Settings view mode: "simple" or "advanced". */
suspend fun SettingsStore.settingsMode(): String = settingsMode(context.settingsDataStore.data.first())

fun SettingsStore.settingsMode(p: Preferences): String = p[SettingsStore.Keys.SETTINGS_MODE] ?: "simple"

/**
 * The chosen distance/temperature unit system ("imperial" default, or "metric"), for non-Compose
 * callers that need it as a one-shot read rather than the [appearance] flow -- e.g.
 */
suspend fun SettingsStore.unitSystem(): String =
    context.settingsDataStore.data.first()[SettingsStore.Keys.UNIT_SYSTEM] ?: "imperial"

suspend fun SettingsStore.setSettingsMode(value: String) {
    editTracked { it[SettingsStore.Keys.SETTINGS_MODE] = value }
}

/** Per-VIN default climate preset ID for the one-tap Start button. */
suspend fun SettingsStore.defaultClimatePreset(vin: String): String? = defaultClimatePreset(vin, context.settingsDataStore.data.first())

fun SettingsStore.defaultClimatePreset(vin: String, p: Preferences): String? =
    p[stringPreferencesKey(SettingsStore.Keys.DEFAULT_CLIMATE_PRESET_PREFIX + vin)]?.takeIf { it.isNotBlank() }

suspend fun SettingsStore.setDefaultClimatePreset(vin: String, id: String?) {
    editTracked {
        val key = stringPreferencesKey(SettingsStore.Keys.DEFAULT_CLIMATE_PRESET_PREFIX + vin)
        it.putNonBlank(key, id)
    }
}
