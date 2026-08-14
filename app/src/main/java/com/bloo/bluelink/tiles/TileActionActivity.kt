package com.bloo.bluelink.tiles

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.TileCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "open & send, then close" path for a Quick Settings tile. A transparent,
 * no-history activity that acks with a toast, enqueues the command on WorkManager
 * (so it survives this activity finishing), and closes — returning the user to
 * wherever they were instead of dropping them into the app. (Background mode runs
 * straight from the tile via the same worker and never comes here.)
 */
class TileActionActivity : FragmentActivity() {

    /**
     * Reads the vin/command/tile-index the tile passed via [android.content.Intent] extras
     * (set by [BlooTileService.launchActionActivity]); missing vin/cmd means this was somehow
     * launched without a valid tile config, so it just closes immediately. Otherwise, on a
     * background IO dispatcher, looks up the cached snapshot (for wording the ack toast, e.g.
     * "Locking" vs "Unlocking") and this tile's configured climate target, shows the toast on
     * the main thread, then hands the actual command off to [TileCommandWorker.enqueue] --
     * which runs independently via WorkManager -- before finishing this activity. Because the
     * command is enqueued (not run inline) before finishing, it keeps running even though this
     * activity is destroyed the moment [finishNoAnim] returns.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vin = intent.getStringExtra(EXTRA_VIN)
        val cmd = intent.getStringExtra(EXTRA_CMD)
        if (vin == null || cmd == null) { finishNoAnim(); return }
        val appCtx = applicationContext
        val index = intent.getIntExtra(EXTRA_INDEX, 0)

        lifecycleScope.launch {
            // One dispatcher hop for both independent reads, not two back to
            // back -- this activity exists specifically to open, enqueue, and
            // close as fast as possible, so an extra Dispatchers.IO handoff
            // on that path is worth skipping even though each read alone is
            // cheap.
            val (snap, target) = withContext(Dispatchers.IO) {
                val snap = SnapshotStore(appCtx).current().vehicles.firstOrNull { it.vin == vin }
                val target = SettingsStore(appCtx).tileClimateTarget(index)
                snap to target
            }
            Toast.makeText(appCtx, TileCommandRunner.ackText(cmd, snap), Toast.LENGTH_SHORT).show()
            TileCommandWorker.enqueue(appCtx, vin, cmd, target)
            finishNoAnim()
        }
    }

    /** Finish without a window-transition animation so this invisible activity never
     *  visibly flashes on top of whatever the user was looking at. */
    private fun finishNoAnim() {
        finishAndRemoveTask()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_VIN = "vin"
        const val EXTRA_CMD = "cmd"
        const val EXTRA_INDEX = "index"
    }
}
