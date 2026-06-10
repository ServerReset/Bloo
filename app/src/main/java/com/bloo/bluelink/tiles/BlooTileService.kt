package com.bloo.bluelink.tiles

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.Shortcuts
import com.bloo.bluelink.data.BlueLinkApi
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A configurable Quick Settings tile. Each tile is assigned a car + action in
 * Settings. Tapping it either runs the command in the background (using the
 * stored session) or opens the app and runs it, per the user's preference.
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
        val cfg = SettingsStore(applicationContext).tileConfig(index)
        if (cfg == null) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Bloo tile ${index + 1}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = "Unassigned"
        } else {
            val (vin, cmd) = cfg
            val name = runCatching {
                SnapshotStore(applicationContext).current().vehicles.firstOrNull { it.vin == vin }?.name
            }.getOrNull() ?: "Car"
            tile.state = Tile.STATE_ACTIVE
            tile.label = cmd.replaceFirstChar { it.uppercase() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = name
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val ctx = applicationContext
        scope.launch {
            val cfg = SettingsStore(ctx).tileConfig(index)
            if (cfg == null) {
                openApp(null, null)
                return@launch
            }
            val (vin, cmd) = cfg
            // "open" always opens the app; otherwise honour the background setting.
            if (cmd != "open" && SettingsStore(ctx).tileBackground()) {
                runInBackground(ctx, vin, cmd)
            } else {
                openApp(vin, cmd)
            }
        }
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

    /** Fire the command directly from the tile using the stored session. */
    private fun runInBackground(ctx: Context, vin: String, cmd: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin } ?: return@runCatching
                val v = snap.toVehicle()
                val brand = Brand.fromIndicator(v.brandIndicator)
                val repo = BlueLinkRepository(BlueLinkApi(brand), SessionStore(ctx), brand)
                when (cmd) {
                    // Toggles based on the last-known snapshot state.
                    "doors" -> if (snap.locked == true) repo.unlock(v) else repo.lock(v)
                    "climate" -> if (snap.climateOn == true) repo.stopClimate(v) else {
                        repo.startClimate(v, ClimateRequest(tempF = 72, defrost = false, durationMinutes = 10))
                    }
                    "lock" -> repo.lock(v)
                    "unlock" -> repo.unlock(v)
                    "charge" -> repo.startCharge(v)
                }
            }
        }
    }

    companion object {
        /** Ask the system to refresh all of Bloo's active tiles. */
        fun requestUpdates(context: Context) {
            val classes = listOf(
                BlooTile1::class.java, BlooTile2::class.java,
                BlooTile3::class.java, BlooTile4::class.java,
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
