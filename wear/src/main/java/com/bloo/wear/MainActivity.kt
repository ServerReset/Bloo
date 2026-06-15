package com.bloo.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.bloo.wear.ui.BlooWearTheme
import com.bloo.wear.ui.WatchApp

class MainActivity : ComponentActivity() {

    private val viewModel: WearViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ui by viewModel.ui.collectAsState()
            BlooWearTheme(ui.settings) {
                // Honour the phone's UI scale by adjusting the font scale.
                val density = LocalDensity.current
                val scale = ui.settings?.uiScale ?: 1f
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, density.fontScale * scale)
                ) {
                    WatchApp(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check phone reachability whenever the watch face returns to Bloo.
        viewModel.refreshConnection()
    }
}
