package com.bloo.bluelink.autolock

import android.content.Context
import androidx.core.content.edit

/**
 * A synchronous answer to "is this Bluetooth address one of our cars, and which one?".
 *
 * The manifest [AutoLockBluetoothReceiver] fires for EVERY Bluetooth device the phone sees -- and it
 * runs in a process that may have been cold-started for that one event. It has two needs the DataStore
 * cannot serve:
 *
 *  - **Synchronous.** The trigger starts the progress service, and Android's exemption for starting a
 *    foreground service from the background is tied to the app still PROCESSING the Bluetooth
 *    broadcast. Waiting on a DataStore read means the broadcast is over by the time
 *    `startForegroundService()` is called, which is the difference between the watcher notification
 *    appearing and not appearing at all.
 *  - **Cheap.** A pair of earbuds connecting is not a reason to load and parse the settings file.
 *
 * So the address -> VIN map is mirrored into plain SharedPreferences, written whenever the config that
 * owns it changes ([write], from the Settings writes and from the watcher's own recovery on every
 * foreground). The DataStore config stays authoritative: this is only ever used to decide whether the
 * expensive work is worth doing, and a stale mirror can only ever mean "skip", never "act on the wrong
 * car", because the VIN it returns is what the evaluation then re-reads the real config for.
 */
object AutoLockAddresses {
    private const val PREFS = "bloo_autolock_addresses"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Replace the mirror. Addresses are the keys, VINs the values; an empty map clears it. Called with
     * only the ENABLED cars that have a device chosen -- an entry here is exactly "a car AutoLock can
     * fire for".
     */
    fun write(context: Context, entries: Map<String, String>) {
        val upper = entries.entries.associate { (address, vin) -> address.uppercase() to vin }
        prefs(context).edit {
            clear()
            upper.forEach { (address, vin) -> putString(address, vin) }
        }
    }

    /** The VIN of the car whose AutoLock is on and whose device is [address], or null. */
    fun vinFor(context: Context, address: String): String? =
        runCatching { prefs(context).getString(address.uppercase(), null) }.getOrNull()

    /**
     * Any VIN the mirror knows. For the watcher's restart-with-no-intent case, where all that is
     * needed is a car to hang the foreground notification on (see AutoLockService).
     */
    fun anyVin(context: Context): String? =
        runCatching { prefs(context).all.values.firstOrNull() as? String }.getOrNull()
}
