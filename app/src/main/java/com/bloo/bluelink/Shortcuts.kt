package com.bloo.bluelink

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bloo.bluelink.data.Vehicle

/** App-icon long-press shortcuts: per-car quick actions and navigation. */
object Shortcuts {

    const val ACTION = "com.bloo.bluelink.action.SHORTCUT"
    const val EXTRA_VIN = "vin"
    const val EXTRA_CMD = "cmd"

    /** Rebuild the dynamic shortcut set for the current cars. */
    fun refresh(context: Context, vehicles: List<Vehicle>) {
        runCatching {
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(4)
            val items = ArrayList<ShortcutInfoCompat>()
            // Per car: lock / unlock / climate / open. The first cars get the full
            // set; we trim to the launcher's capacity.
            vehicles.forEach { v ->
                items += shortcut(context, v, "lock", "Lock", "Lock ${v.name}", R.drawable.ic_shortcut_lock)
                items += shortcut(context, v, "unlock", "Unlock", "Unlock ${v.name}", R.drawable.ic_shortcut_unlock)
                items += shortcut(context, v, "climate", "Climate", "Start climate · ${v.name}", R.drawable.ic_shortcut_climate)
                items += shortcut(context, v, "open", v.name.take(10), "Open ${v.name}", R.drawable.ic_shortcut_car)
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, items.take(max))
        }
    }

    private fun shortcut(
        context: Context,
        v: Vehicle,
        cmd: String,
        shortLabel: String,
        longLabel: String,
        icon: Int,
    ): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION
            putExtra(EXTRA_VIN, v.vin)
            putExtra(EXTRA_CMD, cmd)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return ShortcutInfoCompat.Builder(context, "${cmd}_${v.vin}")
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(intent)
            .build()
    }
}
