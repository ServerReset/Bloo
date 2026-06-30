package com.bloo.bluelink.tiles

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.R
import com.bloo.bluelink.Shortcuts
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.TileCommandRunner
import com.bloo.bluelink.data.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A configurable Quick Settings tile. Each tile is assigned a car + action in
 * Settings, with an optional custom name. The tile reflects the car's live state
 * (locked/unlocked, climate on/off, charging) with matching icons, and tapping it
 * toggles that state — either silently in the background or via a quick
 * open-send-close surface, per the user's preference.
 */
abstract class BlooTileService : TileService() {

    abstract val index: Int
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render() }
    }

    private suspend fun render() {
        val tile = qsTile ?: return
        val ctx = applicationContext
        val cfg = SettingsStore(ctx).tileConfig(index)
        if (cfg == null) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = SettingsStore(ctx).tileLabel(index) ?: "Bloo tile ${index + 1}"
            tile.icon = Icon.createWithResource(ctx, R.drawable.ic_shortcut_car)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Unassigned"
            tile.updateTile()
            return
        }
        val (vin, cmd) = cfg
        val snap = runCatching {
            SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
        }.getOrNull()
        val custom = SettingsStore(ctx).tileLabel(index)

        tile.state = if (isActiveState(cmd, snap)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(ctx, iconFor(cmd, snap))
        tile.label = custom ?: defaultLabel(cmd, snap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = snap?.name ?: "Car"
        tile.updateTile()

        // Optional: kick a throttled live refresh so the shown state is current.
        maybeLiveRefresh(ctx, vin)
    }

    /** If live refresh is on and this car hasn't refreshed recently, queue one. */
    private suspend fun maybeLiveRefresh(ctx: Context, vin: String) {
        val settings = SettingsStore(ctx)
        if (!settings.tileLiveRefresh()) return
        val now = System.currentTimeMillis()
        if (now - settings.tileRefreshedAt(vin) < LIVE_REFRESH_THROTTLE_MS) return
        settings.setTileRefreshedAt(vin, now)
        TileCommandWorker.enqueueRefresh(ctx, vin)
    }

    /**
     * Whether the tile reads as "on" (filled/white background) given the car's
     * state. For lock/unlock the *unlocked* car is the noteworthy state, so the
     * tile lights up white when unlocked and is plain ("none") when locked.
     */
    private fun isActiveState(cmd: String, snap: VehicleSnapshot?): Boolean = when (cmd) {
        "doors", "lock" -> snap?.locked == false
        "unlock" -> snap?.locked == true
        "climate" -> snap?.climateOn == true
        "charge" -> snap?.charging == true
        else -> false
    }

    private fun iconFor(cmd: String, snap: VehicleSnapshot?): Int = when (cmd) {
        // Only claim "open padlock" when we actually know it's unlocked; a closed
        // padlock otherwise (locked, or state not yet synced).
        "doors" -> if (snap?.locked == false) R.drawable.ic_shortcut_unlock else R.drawable.ic_shortcut_lock
        "lock" -> R.drawable.ic_shortcut_lock
        "unlock" -> R.drawable.ic_shortcut_unlock
        "climate" -> R.drawable.ic_shortcut_climate
        "charge" -> R.drawable.ic_widget_bolt
        else -> R.drawable.ic_shortcut_car
    }

    private fun defaultLabel(cmd: String, snap: VehicleSnapshot?): String = when (cmd) {
        // Known state → state label; unknown → neutral "Lock / unlock".
        "doors" -> when (snap?.locked) {
            true -> "Locked"
            false -> "Unlocked"
            else -> "Lock / unlock"
        }
        "lock" -> "Lock"
        "unlock" -> "Unlock"
        "climate" -> if (snap?.climateOn == true) "Climate on" else "Climate"
        "charge" -> if (snap?.charging == true) "Charging" else "Charge"
        "open" -> "Open"
        else -> cmd.replaceFirstChar { it.uppercase() }
    }

    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        scope.launch {
            val cfg = SettingsStore(ctx).tileConfig(index)
            if (cfg == null) { openApp(null, null); return@launch }
            val (vin, cmd) = cfg
            when {
                cmd == "open" -> openApp(vin, cmd)
                SettingsStore(ctx).tileBackground() -> runBackground(ctx, vin, cmd)
                else -> launchActionActivity(vin, cmd)
            }
        }
    }

    /**
     * Background mode: ack instantly with a toast, then run the command via
     * WorkManager so it survives this service being destroyed (the cause of taps
     * "doing nothing"). The worker refreshes the tiles when it's done.
     */
    private suspend fun runBackground(ctx: Context, vin: String, cmd: String) {
        val snap = runCatching {
            SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
        }.getOrNull()
        Toast.makeText(ctx, TileCommandRunner.ackText(cmd, snap), Toast.LENGTH_SHORT).show()
        val target = SettingsStore(ctx).tileClimateTarget(index)
        TileCommandWorker.enqueue(ctx, vin, cmd, target)
    }

    /** Open Bloo and let it run the command (reuses the shortcut routing). */
    private fun openApp(vin: String?, cmd: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (vin != null && cmd != null) {
                action = Shortcuts.ACTION
                putExtra(Shortcuts.EXTRA_VIN, vin)
                putExtra(Shortcuts.EXTRA_CMD, cmd)
            }
        }
        collapseAndStart(intent)
    }

    /** Open-and-close mode: a transparent activity runs the command then finishes. */
    private fun launchActionActivity(vin: String, cmd: String) {
        val intent = Intent(this, TileActionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(TileActionActivity.EXTRA_VIN, vin)
            putExtra(TileActionActivity.EXTRA_CMD, cmd)
            putExtra(TileActionActivity.EXTRA_INDEX, index)
        }
        collapseAndStart(intent)
    }

    private fun collapseAndStart(intent: Intent) {
        val run = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    this, index, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun(run) else run()
    }

    companion object {
        /** Don't kick a live refresh for the same car more than once per minute. */
        private const val LIVE_REFRESH_THROTTLE_MS = 60_000L

        /** Ask the system to refresh all of Bloo's active tiles. */
        fun requestUpdates(context: Context) {
            val classes = listOf(
                BlooTile1::class.java, BlooTile2::class.java,
                BlooTile3::class.java, BlooTile4::class.java,
                BlooTile5::class.java, BlooTile6::class.java,
                BlooTile7::class.java, BlooTile8::class.java,
                BlooTile9::class.java, BlooTile10::class.java,
                BlooTile11::class.java, BlooTile12::class.java,
            )
            classes.forEach { cls ->
                runCatching { requestListeningState(context, ComponentName(context, cls)) }
            }
        }
    }
}

class BlooTile1 : BlooTileService() { override val index = 0 }
class BlooTile2 : BlooTileService() { override val index = 1 }
class BlooTile3 : BlooTileService() { override val index = 2 }
class BlooTile4 : BlooTileService() { override val index = 3 }
class BlooTile5 : BlooTileService() { override val index = 4 }
class BlooTile6 : BlooTileService() { override val index = 5 }
class BlooTile7 : BlooTileService() { override val index = 6 }
class BlooTile8 : BlooTileService() { override val index = 7 }
class BlooTile9 : BlooTileService() { override val index = 8 }
class BlooTile10 : BlooTileService() { override val index = 9 }
class BlooTile11 : BlooTileService() { override val index = 10 }
class BlooTile12 : BlooTileService() { override val index = 11 }
