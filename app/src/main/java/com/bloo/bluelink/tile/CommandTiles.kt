package com.bloo.bluelink.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.bloo.bluelink.data.BlueLinkApi
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tiles that send a command to the currently selected car
 * (the same selection the widgets use). Tap the tile from the QS shade.
 */
abstract class CommandTileService(private val label: String) : TileService() {

    protected abstract suspend fun runCommand(repo: BlueLinkRepository, vehicle: com.bloo.bluelink.data.Vehicle)

    override fun onStartListening() {
        qsTile?.apply {
            this.label = label
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val context = applicationContext
        qsTile?.apply { state = Tile.STATE_ACTIVE; updateTile() }
        CoroutineScope(Dispatchers.IO).launch {
            val selected = SnapshotStore(context).current().selected
            if (selected != null) {
                val repo = BlueLinkRepository(BlueLinkApi(), SessionStore(context))
                runCatching { runCommand(repo, selected.toVehicle()) }
            }
            withContext(Dispatchers.Main) {
                qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }
            }
        }
    }
}

class LockTileService : CommandTileService("Lock car") {
    override suspend fun runCommand(repo: BlueLinkRepository, vehicle: com.bloo.bluelink.data.Vehicle) {
        repo.lock(vehicle)
    }
}

class UnlockTileService : CommandTileService("Unlock car") {
    override suspend fun runCommand(repo: BlueLinkRepository, vehicle: com.bloo.bluelink.data.Vehicle) {
        repo.unlock(vehicle)
    }
}
