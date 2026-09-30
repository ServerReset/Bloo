package com.bloo.bluelink.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * The watch's only Activity. Hosts [WearGarageScreen]; everything else lives in the
 * repository + shared modules.
 */
class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = WearSnapshotRepository(this)
        val pinStore = WatchPinStore(this)
        // Begin listening for the phone's pushes (real-time sync; the watch never hits the
        // network for CAR data -- see WearDataLayerSync).
        WearDataLayerSync.start(this)
        // Notifications need a one-time grant on Android 13+.
        if (!WearNotifier.canPost(this)) {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // The watch checks for its OWN updates over its own internet, independent of the phone.
        lifecycleScope.launch { WearUpdateChecker.check() }
        // A watch the phone has signed in fetches its own car status.
        lifecycleScope.launch { WearCredentialSync.refresh(this@WearMainActivity) }
        setContent {
            WearTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WearGarageScreen(repo, pinStore)
                }
            }
        }
    }
}

/**
 * The watch theme: a dark, high-contrast scheme for a small AMOLED watch face. The
 * phone's dynamic-colour machinery does not exist on a watch the same way, so this is a
 * fixed palette -- deliberately tiny, just enough for the one screen.
 */
@Composable
private fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7B83EB),
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
