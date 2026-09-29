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
 * The watch's only Activity. A plain ComponentActivity (not a Wear
 * [androidx.wear.activity.AmbientModeSupport] host yet) so the first pass stays
 * minimal: it hosts [WearGarageScreen] and nothing else.
 */
class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = WearSnapshotRepository(this)
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
 * The watch theme -- a dark, high-contrast scheme for a small AMOLED watch face,
 * rather than the phone's dynamic-colour machinery (which does not exist on a watch).
 * Deliberately tiny: two surface tones and a primary, enough for the one screen.
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
