package com.bloo.bluelink.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * The watch's only Activity. Hosts [WearGarageScreen]; everything else lives in the repository +
 * shared modules.
 *
 * It also receives the Tile's action buttons. A Tile click can only be an Action, so [BlooTileService]
 * launches this Activity with the chosen action in its extras and the screen runs it through the same
 * PIN gate and command layer as a tap on the face. That is the point: the watch keeps exactly one
 * place where a command can be authorised, and the tile is not it.
 */
class WearMainActivity : ComponentActivity() {
    /**
     * An action the Tile asked for. The sequence is not decoration: tapping "Charge" twice has to run
     * twice, and two identical requests would otherwise be the same state, so the screen would not
     * react to the second one.
     */
    data class TileRequest(val seq: Int, val key: String)

    private var seq = 0
    private var tileRequest by mutableStateOf<TileRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The watch's own power state (Battery Saver, thermal): see PowerPolicy.
        com.bloo.bluelink.data.DeviceStatus.ensureInitialized(this)
        val repo = WearSnapshotRepository(this)
        val pinStore = WatchPinStore(this)
        // Sealed after a restart until the PIN is entered (see WearBootGate): nothing starts before that.
        if (!WearBootGate.isLocked(this)) startWork()
        readTileRequest(intent)
        setContent {
            WearTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WearGarageScreen(
                        repo = repo,
                        pinStore = pinStore,
                        onBootUnlocked = { startWork() },
                        tileRequest = tileRequest,
                        onTileRequestHandled = { tileRequest = null },
                    )
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
        tileRequest = TileRequest(++seq, key)
    }

    private fun startWork() {
        // Begin listening for the phone's pushes (real-time sync; the watch never hits the network
        // for CAR data -- see WearDataLayerSync).
        WearDataLayerSync.start(this)
        // Notifications need a one-time grant on Android 13+.
        if (!WearNotifier.canPost(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }
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
        const val ACTION_LOCK = "lock"
        const val ACTION_CLIMATE = "climate"
        const val ACTION_CHARGE = "charge"
        const val ACTION_FLASH = "flash"
    }
}

/** The watch theme: a dark, high-contrast scheme for a small AMOLED watch face. */
@Composable
private fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(com.bloo.bluelink.data.BlooColors.brandAccent),
            onPrimary = Color.White,
            secondaryContainer = Color(0xFF2A2A33),
            onSecondaryContainer = Color.White,
            surfaceContainer = Color(0xFF1C1C22),
            background = Color.Black,
            onBackground = Color.White,
        ),
        content = content,
    )
}