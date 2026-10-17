package com.bloo.bluelink.autolock

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import com.bloo.bluelink.ui.hasPermission

data class PairedDevice(val name: String, val address: String)

/** Lists bonded (paired) Bluetooth devices, so Settings can offer a picker for "which
 *  paired device is my car". Ported from i5-AutoLock's `BluetoothDevices`. */
object BluetoothDevices {
    fun hasPermission(context: Context): Boolean =
        @Suppress("InlinedApi") // gated by the minSdk arg on the next line
        context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT, android.os.Build.VERSION_CODES.S)

    fun bondedDevices(context: Context): List<PairedDevice> {
        if (!hasPermission(context)) return emptyList()
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .map { PairedDevice(it.name ?: "Unknown device", it.address) }
                .sortedBy { it.name.lowercase() }
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
