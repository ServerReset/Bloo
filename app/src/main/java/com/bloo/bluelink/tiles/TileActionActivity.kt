package com.bloo.bluelink.tiles

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.TileCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "open & send, then close" path for a Quick Settings tile. A transparent,
 * no-history activity that runs the tile's command with the stored session, shows
 * a short toast, refreshes the tiles, and finishes — so the shade collapses and
 * the user is returned to wherever they were instead of being dropped into the
 * app. (Background mode runs straight from the tile and never comes here.)
 */
class TileActionActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vin = intent.getStringExtra(EXTRA_VIN)
        val cmd = intent.getStringExtra(EXTRA_CMD)
        if (vin == null || cmd == null) { finishNoAnim(); return }
        val appCtx = applicationContext

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val target = SettingsStore(appCtx).tileClimateTarget(intent.getIntExtra(EXTRA_INDEX, 0))
                TileCommandRunner.run(appCtx, vin, cmd, target)
            }
            Toast.makeText(appCtx, result.message, Toast.LENGTH_SHORT).show()
            runCatching { BlooTileService.requestUpdates(appCtx) }
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
