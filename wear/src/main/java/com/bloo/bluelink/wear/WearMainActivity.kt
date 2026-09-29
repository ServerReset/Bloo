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

/**
 * The watch's only Activity. Hosts [WearGarageScreen]; everything else lives in the
 * repository + shared modules.
 */
class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = WearSnapshotRepository(this)
        // Begin mirroring the phone's pushed snapshot, once the Data Layer bridge exists
        // (today: both sides already read the same on-disk SnapshotStore -- see
        // WearDataLayerSync's own doc).
        WearDataLayerSync.start(this)
        setContent {
            WearTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WearGarageScreen(repo)
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
