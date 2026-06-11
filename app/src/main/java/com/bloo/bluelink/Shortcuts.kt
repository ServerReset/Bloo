package com.bloo.bluelink

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.brand

/** App-icon long-press shortcuts: per-car quick actions and navigation. */
object Shortcuts {

    const val ACTION = "com.bloo.bluelink.action.SHORTCUT"
    const val EXTRA_VIN = "vin"
    const val EXTRA_CMD = "cmd"

    /** Selectable per-car shortcut actions (toggles + open), in priority order. */
    val ACTIONS = listOf("doors", "climate", "open")

    fun actionLabel(cmd: String): String = when (cmd) {
        "doors" -> "Lock / unlock"
        "climate" -> "Climate"
        "open" -> "Open"
        else -> cmd.replaceFirstChar { it.uppercase() }
    }

    /** Short verb + car name used as a shortcut's visible label. */
    private fun label(cmd: String, name: String): Pair<String, String> = when (cmd) {
        "doors" -> "Doors" to "Lock or unlock $name"
        "climate" -> "Climate" to "Climate · $name"
        else -> name.take(10) to "Open $name"
    }

    private fun id(cmd: String, vin: String) = "${cmd}_$vin"

    /**
     * Rebuild the dynamic shortcut set for the current cars. [enabled] is the set
     * of "cmd_vin" ids the user wants shown; null means show them all. An
     * "Open <brand> app" shortcut is always offered per signed-in brand.
     */
    fun refresh(context: Context, vehicles: List<Vehicle>, enabled: Set<String>? = null) {
        runCatching {
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(4)
            val items = ArrayList<ShortcutInfoCompat>()
            vehicles.forEach { v ->
                ACTIONS.forEach { cmd ->
                    if (enabled == null || id(cmd, v.vin) in enabled) items += carShortcut(context, v, cmd)
                }
            }
            // One "open the OEM app" shortcut per distinct brand present.
            vehicles.distinctBy { it.brand }.forEach { v ->
                items += oemShortcut(context, v)
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, items.take(max))
        }
    }

    private fun carShortcut(context: Context, v: Vehicle, cmd: String): ShortcutInfoCompat {
        val (short, long) = label(cmd, v.name)
        val icon = when (cmd) {
            "doors" -> R.drawable.ic_shortcut_lock
            "climate" -> R.drawable.ic_shortcut_climate
            else -> R.drawable.ic_shortcut_car
        }
        return shortcut(context, "${cmd}_${v.vin}", v.vin, cmd, short, long, icon)
    }

    private fun oemShortcut(context: Context, v: Vehicle): ShortcutInfoCompat {
        val name = when (v.brand) {
            Brand.GENESIS -> "Genesis"
            Brand.KIA -> "Kia Access"
            else -> "Bluelink"
        }
        return shortcut(context, "bluelink_${v.brand.name}", v.vin, "bluelink", name, "Open the $name app", R.drawable.ic_shortcut_car)
    }

    private fun shortcut(
        context: Context,
        id: String,
        vin: String,
        cmd: String,
        shortLabel: String,
        longLabel: String,
        icon: Int,
    ): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION
            putExtra(EXTRA_VIN, vin)
            putExtra(EXTRA_CMD, cmd)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(intent)
            .build()
    }
}
