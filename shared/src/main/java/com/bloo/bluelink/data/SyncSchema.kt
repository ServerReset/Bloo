package com.bloo.bluelink.data

/**
 * The shape of what syncs, kept as data rather than scattered through the merge code. Settings are a
 * schema-less key/value bag, so the list that matters is the list of keys that must NOT be in it any
 * more: a setting that was removed from the app would otherwise live on forever in every device's store
 * and in the shared Drive file, ping-ponging between devices.
 *
 * To retire a setting: add its key (or per-car prefix) here and bump [VERSION]. Every install then stops
 * exporting, importing and hashing it, and deletes its local copy on the next launch. To ADD a setting
 * there is nothing to register: any key that is not device-local or deprecated syncs by default.
 */
object SyncSchema {
    /** Bumped whenever keys are retired or a migration is added. */
    const val VERSION = 2

    /** Exact keys of settings that no longer exist. */
    val DEPRECATED_KEYS: Set<String> = setOf(
        "show_search",                      // the search bubble is always on now
        "cover_settings_hint_dismissed",    // the flip-cover screen is gone
    )

    /** Per-car (VIN-suffixed) key families that no longer exist. */
    val DEPRECATED_PREFIXES: List<String> = listOf(
        // AutoLock behaviours that are now always-on or removed.
        "autolock_use_activity_",
        "autolock_dont_lock_if_open_",
        "autolock_use_bt_",
        "autolock_use_geofence_",
        "autolock_geofence_radius_",
    )

    fun isDeprecated(name: String): Boolean =
        name in DEPRECATED_KEYS || DEPRECATED_PREFIXES.any { name.startsWith(it) }
}
