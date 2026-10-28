package com.bloo.bluelink.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

// --- The watch's own settings, kept with the phone's so Drive carries them ---
//
// The watch edits its face controls, reduced-effects mode and auto lock; the phone stores them under ordinary synced
// keys (see SettingsCatalog), so they ride the same Drive backup and merge as every other setting, and a new watch (or
// a reinstalled one) gets them back from the phone's next push.

private val SLOTS = stringPreferencesKey(PrefKeys.WATCH_FACE_SLOTS)
private val REDUCE_EFFECTS = booleanPreferencesKey(PrefKeys.WATCH_REDUCE_EFFECTS)
private val AUTOLOCK = booleanPreferencesKey(PrefKeys.WATCH_AUTOLOCK)

/** The watch's stored settings, or null while nothing has ever been stored (the watch then offers its own). */
suspend fun SettingsStore.watchLook(): WatchLookPrefs? {
    val p = context.settingsDataStore.data.first()
    if (p[SLOTS] == null && p[REDUCE_EFFECTS] == null && p[AUTOLOCK] == null) return null
    return WatchLookPrefs(
        slots = p[SLOTS]?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
        reduceEffects = p[REDUCE_EFFECTS] ?: false,
        autoLock = p[AUTOLOCK] ?: false,
    )
}

/** Stores the watch's settings; true when that changed anything (so the caller can skip a pointless push back). */
suspend fun SettingsStore.setWatchLook(look: WatchLookPrefs): Boolean {
    val stored = watchLook()
    if (stored == look || (look.slots.isEmpty() && stored != null && stored.copy(slots = emptyList()) == look)) return false
    editTracked {
        // An empty list means "the watch has not chosen": keep the controls already stored rather than erasing them.
        if (look.slots.isNotEmpty()) it[SLOTS] = look.slots.joinToString(",")
        it[REDUCE_EFFECTS] = look.reduceEffects
        it[AUTOLOCK] = look.autoLock
    }
    return true
}
