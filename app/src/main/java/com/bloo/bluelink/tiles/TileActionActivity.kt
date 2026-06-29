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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vin = intent.getStringExtra(EXTRA_VIN)
        val cmd = intent.getStringExtra(EXTRA_CMD)
        if (vin == null || cmd == null) { finishNoAnim(); return }
        val appCtx = applicationContext
        val index = intent.getIntExtra(EXTRA_INDEX, 0)

        lifecycleScope.launch {
            val snap = withContext(Dispatchers.IO) {
                SnapshotStore(appCtx).current().vehicles.firstOrNull { it.vin == vin }
            }
            val target = withContext(Dispatchers.IO) { SettingsStore(appCtx).tileClimateTarget(index) }
            Toast.makeText(appCtx, TileCommandRunner.ackText(cmd, snap), Toast.LENGTH_SHORT).show()
            TileCommandWorker.enqueue(appCtx, vin, cmd, target)
            finishNoAnim()
        }
    }

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
