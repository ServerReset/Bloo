package com.bloo.bluelink.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

// --- Notification prefs, alert bookkeeping, and search bubble (extracted from SettingsStore) --

/** The single [SettingsStore.NotificationPrefs] decode, shared by both the one-shot
 *  [notificationPrefs] read and the reactive [notifications] Flow so the two
 *  can't drift apart (they previously inlined the identical block twice). */
internal fun SettingsStore.decodeNotificationPrefs(p: Preferences): SettingsStore.NotificationPrefs =
    SettingsStore.NotificationPrefs(
        service = p[booleanPreferencesKey("notify_service")] ?: true,
        doorOpen = p[booleanPreferencesKey("notify_door")] ?: true,
        doorOpenMinutes = p[stringPreferencesKey("notify_door_min")]?.toIntOrNull() ?: 5,
        running = p[booleanPreferencesKey("notify_running")] ?: true,
        runningMinutes = p[stringPreferencesKey("notify_running_min")]?.toIntOrNull() ?: 10,
        unlocked = p[booleanPreferencesKey("notify_unlocked")] ?: true,
        unlockedMinutes = p[stringPreferencesKey("notify_unlocked_min")]?.toIntOrNull() ?: 10,
        charging = p[booleanPreferencesKey("notify_charging")] ?: true,
        autoLockAlerts = p[booleanPreferencesKey("notify_autolock")] ?: true,
        carStarted = p[booleanPreferencesKey("notify_start")] ?: true,
        chargeComplete = p[booleanPreferencesKey("notify_charge_complete")] ?: true,
        watchLowBattery = p[booleanPreferencesKey("notify_watch_low")] ?: true,
    )

/** One-shot read of [SettingsStore.NotificationPrefs] (vs. the [notifications] Flow below,
 *  which stays subscribed) — used where a caller just needs the current
 *  values once, e.g. deciding whether to schedule a check at all. */
suspend fun SettingsStore.notificationPrefs(): SettingsStore.NotificationPrefs =
    decodeNotificationPrefs(context.settingsDataStore.data.first())

// One setter per SettingsStore.NotificationPrefs field; `.let {}` just discards editTracked's
// Unit return so these can stay one-expression functions (`=` body) rather
// than needing an explicit block body.
suspend fun SettingsStore.setNotifyService(v: Boolean) =
    editTracked { it[booleanPreferencesKey("notify_service")] = v }.let {}

suspend fun SettingsStore.setNotifyDoor(v: Boolean) =
    editTracked { it[booleanPreferencesKey("notify_door")] = v }.let {}

suspend fun SettingsStore.setDoorOpenMinutes(v: Int) =
    editTracked { it[stringPreferencesKey("notify_door_min")] = v.toString() }.let {}

suspend fun SettingsStore.setNotifyRunning(v: Boolean) =
    editTracked { it[booleanPreferencesKey("notify_running")] = v }.let {}

suspend fun SettingsStore.setRunningMinutes(v: Int) =
    editTracked { it[stringPreferencesKey("notify_running_min")] = v.toString() }.let {}

suspend fun SettingsStore.setNotifyUnlocked(v: Boolean) =
    editTracked { it[booleanPreferencesKey("notify_unlocked")] = v }.let {}

suspend fun SettingsStore.setUnlockedMinutes(v: Int) =
    editTracked { it[stringPreferencesKey("notify_unlocked_min")] = v.toString() }.let {}

suspend fun SettingsStore.setNotifyCharging(v: Boolean) =
    editTracked { it[booleanPreferencesKey("notify_charging")] = v }.let {}

suspend fun SettingsStore.setNotifyAutoLock(v: Boolean) =
    editTracked { it[booleanPreferencesKey("notify_autolock")] = v }.let {}

suspend fun SettingsStore.setNotifyCarStarted(v: Boolean) =
    editTracked { it[booleanPreferencesKey("notify_start")] = v }.let {}

suspend fun SettingsStore.setNotifyChargeComplete(v: Boolean) =
    editTracked { it[booleanPreferencesKey("notify_charge_complete")] = v }.let {}

suspend fun SettingsStore.setNotifyWatchLowBattery(v: Boolean) =
    editTracked { it[booleanPreferencesKey("notify_watch_low")] = v }.let {}

// Transient alert bookkeeping (per car), used to fire each alert only once.
// Mechanism: when AlertWorker (see work/AlertWorker.kt) first observes a
// door open (or engine running), it stamps "door_since_$vin"/"engine_since_$vin"
// with the current time via the setters below. On each subsequent check it
// reads that timestamp back and compares elapsed time against the configured
// *Minutes threshold; once the threshold is crossed AND the per-condition
// alertFired(key) flag isn't already set, it fires the notification and
// flips alertFired to true so it won't repeat. The *Since value is cleared
// (set to null, which removes the key) as soon as the condition stops being
// true, so the next occurrence starts timing from zero again.
suspend fun SettingsStore.doorOpenSince(vin: String): Long? =
    context.settingsDataStore.data.first()[stringPreferencesKey("door_since_$vin")]?.toLongOrNull()

suspend fun SettingsStore.setDoorOpenSince(vin: String, value: Long?) {
    editTracked {
        val k = stringPreferencesKey("door_since_$vin")
        if (value == null) it.remove(k) else it[k] = value.toString()
    }
}

/**
 * Where the user last parked the cover screen's floating search bubble, as
 * fractions (0f..1f) of its own drag range -- null until it has been dragged
 * at least once. A plain one-shot suspend read, not a collected Flow: the
 * bubble's own composable seeds itself from this once (see SearchLayer),
 * it does not need to react live to a value only that same composable ever
 * writes.
 */
suspend fun SettingsStore.searchBubblePosition(): Pair<Float, Float>? {
    val prefs = context.settingsDataStore.data.first()
    val x = prefs[SettingsStore.Keys.SEARCH_BUBBLE_X]?.toFloatOrNull() ?: return null
    val y = prefs[SettingsStore.Keys.SEARCH_BUBBLE_Y]?.toFloatOrNull() ?: return null
    return x to y
}

/** Persists the bubble's resting fractions -- called once per drag gesture
 *  (on release), not per frame, from SearchLayer's onDragEnd. */
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
        if (value == null) it.remove(k) else it[k] = value.toString()
    }
}

suspend fun SettingsStore.unlockedSince(vin: String): Long? =
    context.settingsDataStore.data.first()[stringPreferencesKey("unlocked_since_$vin")]?.toLongOrNull()

suspend fun SettingsStore.setUnlockedSince(vin: String, value: Long?) {
    editTracked {
        val k = stringPreferencesKey("unlocked_since_$vin")
        if (value == null) it.remove(k) else it[k] = value.toString()
    }
}

/** Whether a specific alert (identified by an arbitrary caller-defined
 *  [key], typically something like "door_$vin" or "running_$vin") has
 *  already fired for its current occurrence, so callers don't notify twice
 *  for the same continuous door-open/engine-running spell. */
suspend fun SettingsStore.alertFired(key: String): Boolean =
    context.settingsDataStore.data.first()[booleanPreferencesKey("alert_$key")] ?: false

suspend fun SettingsStore.setAlertFired(key: String, value: Boolean) {
    editTracked { it[booleanPreferencesKey("alert_$key")] = value }
}

/**
 * Whether the user swiped away [vin]'s live charging bar during the CURRENT charging
 * session. Google's Live Updates guidance is explicit that a dismissed Live Update
 * must not be reposted, and this notification is otherwise re-posted every five
 * minutes for as long as the charge lasts.
 *
 * Persisted rather than kept in memory because the poller is a WorkManager job: the
 * process is routinely killed between ticks, so an in-memory flag would be forgotten
 * and the bar would come straight back — which is the behaviour this exists to stop.
 *
 * Device-local (see SyncMerge's prefix list): dismissing a notification on a phone
 * says nothing about what a tablet should show. Cleared when charging ends, so the
 * next session starts fresh rather than being permanently suppressed by one swipe.
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

suspend fun SettingsStore.setVibrancy(value: Float) {
    editTracked { it[SettingsStore.Keys.VIBRANCY] = value.toString() }
}

suspend fun SettingsStore.setAuroraBackground(value: Boolean) {
    editTracked { it[SettingsStore.Keys.AURORA] = value.toString() }
}

// The setters below validate the incoming string against the fixed set of
// legal values and silently fall back to the default if it's anything else
// (e.g. a stale string from a future app version we don't recognize),
// rather than storing garbage that the appearance Flow above would then
// have to re-validate on every read.
suspend fun SettingsStore.setAuroraMotion(value: String) {
    editTracked { it[SettingsStore.Keys.AURORA_MOTION] = value.takeIf { it in setOf("off", "static", "motion") } ?: "static" }
}

suspend fun SettingsStore.setUnitSystem(value: String) {
    editTracked { it[SettingsStore.Keys.UNIT_SYSTEM] = value.takeIf { it in setOf("imperial", "metric") } ?: "imperial" }
}

suspend fun SettingsStore.setTempUnit(value: String) {
    editTracked { it[SettingsStore.Keys.TEMP_UNIT] = value.takeIf { it in setOf("auto", "f", "c") } ?: "auto" }
}

suspend fun SettingsStore.setDistanceUnit(value: String) {
    editTracked { it[SettingsStore.Keys.DISTANCE_UNIT] = value.takeIf { it in setOf("auto", "mi", "km") } ?: "auto" }
}

/** True when distances are shown in km, honouring the distance override -- the one-shot read for
 *  non-Compose callers (CarAlerts building a notification string) that the [appearance] flow serves elsewhere. */
suspend fun SettingsStore.metricDistance(): Boolean {
    val p = context.settingsDataStore.data.first()
    return resolveMetricDistance(p[SettingsStore.Keys.UNIT_SYSTEM], p[SettingsStore.Keys.DISTANCE_UNIT])
}

/** Settings view mode: "simple" or "advanced". */
suspend fun SettingsStore.settingsMode(): String = settingsMode(context.settingsDataStore.data.first())

fun SettingsStore.settingsMode(p: Preferences): String = p[SettingsStore.Keys.SETTINGS_MODE] ?: "simple"

/** The chosen distance/temperature unit system ("imperial" default, or "metric"), for
 *  non-Compose callers that need it as a one-shot read rather than the [appearance] flow --
 *  e.g. CarAlerts building a notification string off the main thread. Mirrors [settingsMode]. */
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
        if (id.isNullOrBlank()) it.remove(key) else it[key] = id
    }
}
