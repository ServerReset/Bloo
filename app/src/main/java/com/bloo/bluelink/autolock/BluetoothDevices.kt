package com.bloo.bluelink.autolock

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class PairedDevice(val name: String, val address: String)

/** Lists bonded (paired) Bluetooth devices, so Settings can offer a picker for "which
 *  paired device is my car". Ported from i5-AutoLock's `BluetoothDevices`. */
object BluetoothDevices {
    fun hasPermission(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

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
