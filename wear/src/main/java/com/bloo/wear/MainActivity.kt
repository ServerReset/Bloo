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
                // Combine phone-synced scale with local watch override.
                val density = LocalDensity.current
                val phoneScale = ui.settings?.uiScale ?: 1f
                val localScale = ui.localSettings.fontScale
                val effectiveScale = phoneScale * localScale
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, density.fontScale * effectiveScale)
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
