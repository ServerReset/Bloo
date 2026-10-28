package com.bloo.bluelink.data

/**
 * The stored names of the notification settings. They are also the keys sync writes to other devices, so renaming one is a breaking
 * change for every paired device. Every reader and writer refers to them here, not by a copied string.
 */
object AlertKeys {
    const val SERVICE = "notify_service"
    const val DOOR = "notify_door"
    const val DOOR_MINUTES = "notify_door_min"
    const val RUNNING = "notify_running"
    const val RUNNING_MINUTES = "notify_running_min"
    const val UNLOCKED = "notify_unlocked"
    const val UNLOCKED_MINUTES = "notify_unlocked_min"
    const val CHARGING = "notify_charging"
    const val AUTOLOCK = "notify_autolock"
    const val CAR_STARTED = "notify_start"
    const val CHARGE_COMPLETE = "notify_charge_complete"
    const val WATCH_LOW_BATTERY = "notify_watch_low"
}
