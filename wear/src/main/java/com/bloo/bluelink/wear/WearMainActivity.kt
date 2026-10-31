package com.bloo.bluelink.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * The watch's only Activity. Hosts [WearApp]; everything else lives in the repository +
 * shared modules.
 *
 * It also receives the Tile's action buttons. A Tile click can only be an Action, so [BlooTileService]
 * launches this Activity with the chosen action in its extras and the screen runs it through the same
 * command layer as a tap on the face. That is the point: the watch keeps exactly one
 * place where a command can be authorised, and the tile is not it.
 */
class WearMainActivity : ComponentActivity() {
    /**
     * An action the Tile asked for. The sequence is not decoration: tapping "Charge" twice has to run
     * twice, and two identical requests would otherwise be the same state, so the screen would not
     * react to the second one.
     */
    data class TileRequest(val seq: Int, val key: String, val slot: Int = -1)

    private var seq = 0
    private var tileRequest by mutableStateOf<TileRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The watch's own power state (Battery Saver, thermal): see PowerPolicy.
        com.bloo.bluelink.data.DeviceStatus.ensureInitialized(this)
        // The wearer's own reduce-effects switch joins Battery Saver in the policy before anything draws.
        com.bloo.bluelink.data.DeviceStatus.setManualLean(WearLook.reduceEffects(this))
        WearPresets.ensureLoaded(this)
        val repo = WearSnapshotRepository(this)
        // The watch's own device lock holds it until the first unlock after a restart, so its work starts here.
        startWork()
        // Only on a fresh start: a recreated activity (a font or locale change) is handed the SAME intent again, and
        // would run the tile's action a second time.
        if (savedInstanceState == null) readTileRequest(intent)
        setContent {
            WearTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // The plain background behind every screen.
                    WearBackdrop(Modifier.fillMaxSize()) {
                        WearApp(
                            repo = repo,
                            tileRequest = tileRequest,
                            onTileRequestHandled = {
                                tileRequest = null
                                // Done with it: it must not come back with the intent.
                                intent?.removeExtra(EXTRA_ACTION)
                                intent?.removeExtra(EXTRA_SLOT)
                            },
                        )
                    }
                }
            }
        }
    }

    /** The app is single-task, so a second Tile tap arrives here rather than starting a second copy. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readTileRequest(intent)
    }

    private fun readTileRequest(intent: Intent?) {
        val key = intent?.getStringExtra(EXTRA_ACTION) ?: return
        tileRequest = TileRequest(++seq, key, intent.getIntExtra(EXTRA_SLOT, -1))
    }

    private fun startWork() {
        // Begin listening for the phone's pushes (real-time sync; the watch never hits the network
        // for CAR data -- see WearDataLayerSync).
        WearDataLayerSync.start(this)
        // Notifications need a one-time grant on Android 13+.
        if (!WearNotifier.canPost(this)) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        // The watch checks for its OWN updates over its own internet, independent of the phone.
        // Lean: not unasked; the watch has its own power budget.
        if (com.bloo.bluelink.data.DeviceStatus.policy(this).pollIntervalMultiplier == 1) lifecycleScope.launch { WearUpdateChecker.check() }
        // A watch the phone has signed in fetches its own car status.
        lifecycleScope.launch { WearCredentialSync.refresh(this@WearMainActivity) }
        // The watch's own AutoLock (beta): re-arm its one-shot trigger sensor for this run.
        WearAutoLock.arm(this)
    }

    companion object {
        /** The extra a Tile button sets; read by [readTileRequest]. */
        const val EXTRA_ACTION = "bloo_action"

        /** Which quarter of the face a tile button was (0 to 3), so two quarters holding the same control stay distinct. */
        const val EXTRA_SLOT = "bloo_slot"
    }
}
