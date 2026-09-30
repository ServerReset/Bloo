package com.bloo.bluelink.data

import android.os.Build
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

// --- Sync identity, device registry, and shortcut selection (extracted from SettingsStore) --

/** Enabled shortcut ids ("cmd_vin"); null = never customised (show all). */
suspend fun SettingsStore.enabledShortcuts(): Set<String>? = enabledShortcuts(context.settingsDataStore.data.first())

fun SettingsStore.enabledShortcuts(p: Preferences): Set<String>? {
    val raw = p[stringPreferencesKey("enabled_shortcuts")] ?: return null
    return raw.split(",").filter { it.isNotBlank() }.toSet()
}

suspend fun SettingsStore.setEnabledShortcuts(ids: Set<String>) {
    editTracked { it[stringPreferencesKey("enabled_shortcuts")] = ids.joinToString(",") }
}

/** Drive URI for auto-backup; null when not configured. */
suspend fun SettingsStore.syncUri(): String? = syncUri(context.settingsDataStore.data.first())

fun SettingsStore.syncUri(p: Preferences): String? =
    p[stringPreferencesKey("sync_uri")]?.takeIf { it.isNotBlank() }

/** A short, stable biometric of the ACTUAL Drive file this device is synced
 *  to, derived from the persisted document URI's unique id. Shown in Settings
 *  so two devices can eyeball whether they're on the SAME file: if the two
 *  biometrics differ, they picked different files (Google Drive allows two
 *  files with the same name), which is the #1 reason settings/devices don't
 *  converge. Null when sync isn't set up. */
suspend fun SettingsStore.syncFileFingerprint(): String? = syncFileFingerprint(context.settingsDataStore.data.first())

fun SettingsStore.syncFileFingerprint(p: Preferences): String? {
    if (syncUri(p) == null) return null
    // The CONTENT-based file id (stored inside the Drive file as `_fileId`,
    // cached here device-local). Every device on the same file reads the same
    // value → the same short code. This replaces the old URI hash, which was
    // WRONG: a SAF content:// URI is assigned PER DEVICE by the OS, so the same
    // Drive file had different URIs (and different hashes) on two phones — the
    // exact "I picked the same file but the codes differ" bug. Null until the
    // first successful sync has read/minted the id.
    val id = p[stringPreferencesKey("sync_file_id")] ?: return null
    // Short, human-comparable tag (first 6 hex of a SHA-256 of the full id) so
    // we never surface the raw UUID but two devices still match at a glance.
    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
    return hash.take(3).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

/** The full content-based file id this device currently has cached, or null.
 *  Used by [performMainToMainSync] to decide whether to preserve the remote file's
 *  id or mint a new one. */
internal suspend fun SettingsStore.syncFileId(): String? =
    context.settingsDataStore.data.first()[stringPreferencesKey("sync_file_id")]?.takeIf { it.isNotBlank() }

internal suspend fun SettingsStore.setSyncFileId(id: String) {
    context.settingsDataStore.edit { it[stringPreferencesKey("sync_file_id")] = id }
}

suspend fun SettingsStore.setSyncUri(uri: String?) {
    editTracked {
        val key = stringPreferencesKey("sync_uri")
        if (uri.isNullOrBlank()) it.remove(key) else it[key] = uri
    }
}

/** Timestamp (ms) of the last successful bidirectional sync. */
suspend fun SettingsStore.lastSyncMs(): Long = lastSyncMs(context.settingsDataStore.data.first())

fun SettingsStore.lastSyncMs(p: Preferences): Long =
    p[stringPreferencesKey("sync_last_ms")]?.toLongOrNull() ?: 0L

suspend fun SettingsStore.setLastSyncMs(ms: Long) {
    editTracked { it[stringPreferencesKey("sync_last_ms")] = ms.toString() }
}

/** Persisted so a failure from the background periodic worker (no live
 *  ViewModel to update UiState.syncError) still shows up in Settings the
 *  next time the app is opened, instead of only being visible if a
 *  foreground sync happens to fail while the app is open. */
suspend fun SettingsStore.lastSyncError(): String? = lastSyncError(context.settingsDataStore.data.first())

fun SettingsStore.lastSyncError(p: Preferences): String? = p[stringPreferencesKey("sync_last_error")]

suspend fun SettingsStore.setLastSyncError(error: String?) {
    editTracked { if (error == null) it.remove(stringPreferencesKey("sync_last_error")) else it[stringPreferencesKey("sync_last_error")] = error }
}

/** Wi-Fi only sync (true) or any network (false). */
suspend fun SettingsStore.syncWifiOnly(): Boolean = syncWifiOnly(context.settingsDataStore.data.first())

fun SettingsStore.syncWifiOnly(p: Preferences): Boolean =
    p[stringPreferencesKey("sync_wifi")]?.toBooleanStrictOrNull() ?: true

suspend fun SettingsStore.setSyncWifiOnly(value: Boolean) {
    editTracked { it[stringPreferencesKey("sync_wifi")] = value.toString() }
}

/**
 * When a paired WATCH should ask for the app PIN. Device-local (in DEVICE_LOCAL_KEYS): it
 * describes this phone's own paired watch, so it must not travel in the portable backup.
 * Stored via the RAW DataStore (not editTracked) for the same reason the device-registry
 * keys are -- touching it should not pollute the sync dirty set.
 */
suspend fun SettingsStore.watchLockTiming(): com.bloo.bluelink.data.WatchLockTiming =
    com.bloo.bluelink.data.WatchLockTiming.fromWire(
        context.settingsDataStore.data.first()[stringPreferencesKey("watch_lock_timing")],
    )

suspend fun SettingsStore.setWatchLockTiming(value: com.bloo.bluelink.data.WatchLockTiming) {
    context.settingsDataStore.edit { it[stringPreferencesKey("watch_lock_timing")] = value.wireKey }
}

/** A stable per-install id for this device in the sync registry, created once
 *  (lazily) and persisted. Not derived from any hardware id (privacy + it must
 *  survive a factory-reset-style reinstall as a NEW device, which a random UUID
 *  gives us for free). */
suspend fun SettingsStore.syncDeviceId(): String {
    val existing = context.settingsDataStore.data.first()[stringPreferencesKey("sync_device_id")]
    if (!existing.isNullOrBlank()) return existing
    val fresh = java.util.UUID.randomUUID().toString()
    context.settingsDataStore.edit { it[stringPreferencesKey("sync_device_id")] = fresh }
    return fresh
}

/** Reads the id straight off an already-taken snapshot, without the
 *  mint-if-missing side effect above -- null on a snapshot from before the id
 *  was first minted. Callers on the cold-start path that just want "whatever
 *  is there right now" (e.g. seeding UiState) should prefer this over the
 *  suspend overload; it costs no DataStore round trip against a snapshot they
 *  already hold. */
fun SettingsStore.syncDeviceId(p: Preferences): String? = p[stringPreferencesKey("sync_device_id")]?.takeIf { it.isNotBlank() }

/** Friendly name shown in the "your devices" list. Defaults to the hardware
 *  model until the user renames it. */
suspend fun SettingsStore.syncDeviceName(): String = syncDeviceName(context.settingsDataStore.data.first())

fun SettingsStore.syncDeviceName(p: Preferences): String =
    p[stringPreferencesKey("sync_device_name")]?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "This device"

suspend fun SettingsStore.setSyncDeviceName(name: String) {
    context.settingsDataStore.edit {
        val k = stringPreferencesKey("sync_device_name")
        if (name.isBlank()) it.remove(k) else it[k] = name.trim()
    }
}

/** Hash of the portable content this device last saw or wrote (the change gate). */
suspend fun SettingsStore.syncLastHash(): String? =
    context.settingsDataStore.data.first()[stringPreferencesKey("sync_last_hash")]?.takeIf { it.isNotBlank() }

suspend fun SettingsStore.setSyncLastHash(hash: String?) {
    context.settingsDataStore.edit {
        val k = stringPreferencesKey("sync_last_hash")
        if (hash.isNullOrBlank()) it.remove(k) else it[k] = hash
    }
}

/** Whether this device has ever completed a sync of the CURRENT file. False →
 *  the next pass full-adopts (join-adopt). Reset to false on a file switch. */
suspend fun SettingsStore.syncSyncedEver(): Boolean =
    context.settingsDataStore.data.first()[booleanPreferencesKey("sync_synced_ever")] ?: false

suspend fun SettingsStore.setSyncSyncedEver(value: Boolean) {
    context.settingsDataStore.edit {
        if (value) it[booleanPreferencesKey("sync_synced_ever")] = true
        else it.remove(booleanPreferencesKey("sync_synced_ever"))
    }
}

/** One-shot flag: the next sync pass force-adopts the file (used by
 *  "Pull from primary now"). Cleared by the pass that consumes it. */
suspend fun SettingsStore.syncPullPrimary(): Boolean =
    context.settingsDataStore.data.first()[booleanPreferencesKey("sync_pull_primary")] ?: false

suspend fun SettingsStore.setSyncPullPrimary(value: Boolean) {
    context.settingsDataStore.edit {
        if (value) it[booleanPreferencesKey("sync_pull_primary")] = true
        else it.remove(booleanPreferencesKey("sync_pull_primary"))
    }
}

/** Cached copy of the last-merged `devices` registry, for offline display in
 *  Settings (the file may not be reachable when Settings opens). */
suspend fun SettingsStore.syncedDevices(): List<SyncMerge.SyncDevice> = syncedDevices(context.settingsDataStore.data.first())

fun SettingsStore.syncedDevices(p: Preferences): List<SyncMerge.SyncDevice> {
    val raw = p[stringPreferencesKey("sync_devices_cache")] ?: return emptyList()
    return runCatching { devicesJson.decodeFromString(deviceListSerializer, raw) }.getOrElse { emptyList() }
}

internal suspend fun SettingsStore.setSyncedDevicesCache(devices: List<SyncMerge.SyncDevice>) {
    context.settingsDataStore.edit {
        it[stringPreferencesKey("sync_devices_cache")] = devicesJson.encodeToString(deviceListSerializer, devices)
    }
}

/** The primary device id (source of truth), cached device-local for display and
 *  written into the file on the next upload. Null = no primary chosen. */
suspend fun SettingsStore.syncPrimaryDeviceId(): String? = syncPrimaryDeviceId(context.settingsDataStore.data.first())

fun SettingsStore.syncPrimaryDeviceId(p: Preferences): String? =
    p[stringPreferencesKey("sync_primary_cache")]?.takeIf { it.isNotBlank() }

internal suspend fun SettingsStore.setSyncPrimaryCache(id: String?) {
    context.settingsDataStore.edit {
        val k = stringPreferencesKey("sync_primary_cache")
        if (id.isNullOrBlank()) it.remove(k) else it[k] = id
    }
}

/** A primary designation made on THIS device that hasn't been uploaded yet.
 *
 *  Separate from [syncPrimaryDeviceId] because that pref carries two different
 *  meanings which must not be conflated: "what the file says" (cached for offline
 *  Settings display) and "what I want the file to say". Reading the cache as a write
 *  intent is what stopped the primary from ever changing -- see [performMainToMainSync]. */
internal suspend fun SettingsStore.syncPrimaryPending(): String? =
    context.settingsDataStore.data.first()[stringPreferencesKey("sync_primary_pending")]?.takeIf { it.isNotBlank() }

internal suspend fun SettingsStore.setSyncPrimaryPending(id: String?) {
    context.settingsDataStore.edit {
        val k = stringPreferencesKey("sync_primary_pending")
        if (id.isNullOrBlank()) it.remove(k) else it[k] = id
    }
}

/** Designate the primary device (source of truth). Persists locally; the value
 *  is written into the Drive file on the next [performMainToMainSync] upload.
 *
 *  Records the choice TWICE, deliberately: as a pending write intent (consumed by the
 *  next successful upload) and in the display cache (so Settings reflects the tap
 *  immediately rather than after a round trip). */
suspend fun SettingsStore.setPrimaryDevice(id: String) {
    setSyncPrimaryPending(id)
    setSyncPrimaryCache(id)
}

/** Arm a one-shot force-adopt from the file (the "Pull from primary now" lever). */
suspend fun SettingsStore.requestPullFromPrimary() {
    setSyncPullPrimary(true)
}

/** Device ids queued for removal from the registry, not yet uploaded -- the same
 *  "pending write intent" shape [syncPrimaryPending] uses, so "kick this device"
 *  survives an app restart before the next sync pass gets to enact it. Unlike
 *  [setPrimaryDevice] this is a SET, not a single value: several devices could be
 *  kicked before the next sync runs. */
suspend fun SettingsStore.syncPendingRemovedDeviceIds(): Set<String> {
    val raw = context.settingsDataStore.data.first()[stringPreferencesKey("sync_pending_removed_device_ids")]
    return raw?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
}

internal suspend fun SettingsStore.setSyncPendingRemovedDeviceIds(ids: Set<String>) {
    context.settingsDataStore.edit {
        val k = stringPreferencesKey("sync_pending_removed_device_ids")
        if (ids.isEmpty()) it.remove(k) else it[k] = ids.joinToString(",")
    }
}

/**
 * "Kick" a device out of the synced-devices list. NOT a permanent ban: this is
 * exactly the same pruning [SyncMerge.mergeDevices] already does automatically
 * for a device that hasn't been seen in 90 days, just requested NOW instead of
 * waited-for -- a device that syncs again after being kicked simply reappears,
 * the same way a stale one would if it ever came back online. That is a
 * deliberate safety property, not a limitation: kicking a device you don't
 * recognise can never permanently lock out one that is still genuinely in use.
 *
 * Recorded twice, same shape as [setPrimaryDevice]: as a pending write intent
 * (consumed by the next successful upload, which is what actually keeps it out
 * of the registry the OTHER devices see) and stripped from the display cache
 * immediately, so Settings reflects the tap right away rather than after a
 * round trip to Drive and back.
 */
suspend fun SettingsStore.removeSyncedDevice(id: String) {
    if (id.isBlank() || id == syncDeviceId()) return
    setSyncPendingRemovedDeviceIds(syncPendingRemovedDeviceIds() + id)
    setSyncedDevicesCache(syncedDevices().filterNot { it.id == id })
}

/** This device's own registry entry, freshly stamped. [appVersion] is best-effort. */
internal suspend fun SettingsStore.selfSyncDevice(nowMs: Long): SyncMerge.SyncDevice {
    val appVersion = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    }.getOrDefault("")
    return SyncMerge.SyncDevice(
        id = syncDeviceId(),
        name = syncDeviceName(),
        model = Build.MODEL ?: "",
        appVersion = appVersion,
        lastSeenMs = nowMs,
        // A Wear OS companion registers through the SAME Drive sync as a phone, so it
        // lands in this registry like any peer -- but it must never read as a peer
        // PRIMARY candidate. Settings uses this to show it as a dependent companion
        // under its phone instead. FEATURE_WATCH is how a watch announces itself.
        kind = if (context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_WATCH,
            )
        ) SyncMerge.KIND_WATCH else SyncMerge.KIND_PHONE,
    )
}
