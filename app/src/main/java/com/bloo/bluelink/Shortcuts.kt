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

    /** The selectable shortcut actions, in priority order. */
    val ACTIONS = listOf("lock", "unlock", "climate", "open")

    fun actionLabel(cmd: String): String = when (cmd) {
        "lock" -> "Lock"
        "unlock" -> "Unlock"
        "climate" -> "Climate"
        "open" -> "Open"
        else -> cmd.replaceFirstChar { it.uppercase() }
    }

    private fun id(cmd: String, vin: String) = "${cmd}_$vin"

    /**
     * Rebuild the dynamic shortcut set for the current cars. [enabled] is the set
     * of "cmd_vin" ids the user wants shown; null means show them all.
     */
    fun refresh(context: Context, vehicles: List<Vehicle>, enabled: Set<String>? = null) {
        runCatching {
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(4)
            val items = ArrayList<ShortcutInfoCompat>()
            vehicles.forEach { v ->
                ACTIONS.forEach { cmd ->
                    if (enabled == null || id(cmd, v.vin) in enabled) {
                        items += build(context, v, cmd)
                    }
                }
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, items.take(max))
        }
    }

    private fun build(context: Context, v: Vehicle, cmd: String): ShortcutInfoCompat = when (cmd) {
        "lock" -> shortcut(context, v, "lock", "Lock", "Lock ${v.name}", R.drawable.ic_shortcut_lock)
        "unlock" -> shortcut(context, v, "unlock", "Unlock", "Unlock ${v.name}", R.drawable.ic_shortcut_unlock)
        "climate" -> shortcut(context, v, "climate", "Climate", "Start climate · ${v.name}", R.drawable.ic_shortcut_climate)
        else -> shortcut(context, v, "open", v.name.take(10), "Open ${v.name}", R.drawable.ic_shortcut_car)
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
