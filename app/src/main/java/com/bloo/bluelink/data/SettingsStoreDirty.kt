package com.bloo.bluelink.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// --- Drive-sync dirty tracking: the portable keys this device changed since its last sync ---

/**
 * Wraps a settings mutation to record which preference keys it actually changed into the
 * "dirty" set — the keys this device has touched locally since its own last successful Drive
 * sync. [performMainToMainSync] protects these from being overwritten by an incoming remote
 * file, so a local edit that hasn't been uploaded yet is never silently lost (field-level merge
 * instead of one whole-file last-write-wins).
 */
internal suspend fun SettingsStore.editTracked(mutate: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
    context.settingsDataStore.edit { prefs ->
        val before = HashMap(prefs.asMap())
        mutate(prefs)
        val after = prefs.asMap()
        val touched = mutableSetOf<String>()
        after.forEach { (k, v) -> if (before[k] != v) touched += k.name }
        // Value-identical.
        val afterNames = HashSet<String>(after.size)
        after.keys.forEach { afterNames += it.name }
        before.keys.forEach { k -> if (k.name !in afterNames) touched += k.name }
        // Strip BOTH the exact device-local keys AND the per-VIN device-local prefixes (see
        // SyncMerge.DEVICE_LOCAL_PREFIXES for the list -- naming them here just meant this
        // comment fell behind it), matching what the export/hash already exclude via
        // isDeviceLocal — otherwise those transient runtime stamps land in the dirty set and
        // every alert/tile tick fires a redundant full Drive round-trip (the hash is unchanged,
        // so no data corrupts, but it's needless background I/O the prefix design meant to
        // stop).
        touched.removeAll { com.bloo.bluelink.data.SyncMerge.isDeviceLocal(it) }
        if (touched.isNotEmpty()) {
            val dirtyKey = stringPreferencesKey("sync_dirty_keys")
            val existing = prefs.dirtyKeySet()
            prefs[dirtyKey] = (existing + touched).joinToString(",")
        }
    }
}

/**
 * Decode the CSV-encoded "sync_dirty_keys" pref into a Set, dropping blanks (an unset/empty
 * value → empty set).
 */
internal fun Preferences.dirtyKeySet(): Set<String> =
    this[stringPreferencesKey("sync_dirty_keys")]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

internal suspend fun SettingsStore.dirtyKeys(): Set<String> =
    context.settingsDataStore.data.first().dirtyKeySet()

/**
 * Reactive view of the dirty set — emits whenever a tracked setting changes (every
 * [editTracked] that touches a portable key appends to it).
 */
val SettingsStore.dirtyKeysFlow: Flow<Set<String>> get() = context.settingsDataStore.data
    .map { prefs ->
        val keys = prefs.dirtyKeySet()
        // One name -> value map, not a scan per dirty key. Tombstoned keys are absent from
        // prefs and biometric as null, so a delete and a later restore of the same key are
        // correctly distinct.
        val byName = prefs.asMap().entries.associate { it.key.name to it.value }
        // NUL separator below, and written as the ESCAPE rather than the character.
        keys to keys.sorted().joinToString("\u0000") { "$it=${byName[it]}" }.hashCode()
    }
    .distinctUntilChanged()
    .map { it.first }

/**
 * Clear [keys] from the dirty set via set-difference, leaving any key marked dirty after the
 * calling upload's body was snapshotted still pending. Done inside a single edit{} so a
 * concurrent [editTracked] can't race between our read and write; if nothing dirty remains the
 * key is removed entirely.
 */
internal suspend fun SettingsStore.clearDirtyKeys(keys: Set<String>) {
    context.settingsDataStore.edit { prefs ->
        val dirtyKey = stringPreferencesKey("sync_dirty_keys")
        val remaining = prefs.dirtyKeySet() - keys
        if (remaining.isEmpty()) prefs.remove(dirtyKey) else prefs[dirtyKey] = remaining.joinToString(",")
    }
}
