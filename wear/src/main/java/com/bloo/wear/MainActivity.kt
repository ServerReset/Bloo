package com.bloo.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.bloo.wear.ui.BlooWearTheme
import com.bloo.wear.ui.WatchApp

class MainActivity : ComponentActivity() {

    private val viewModel: WearViewModel by viewModels()

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    // PIN-lock relock timing, mirroring the phone's MainActivity: record when
    // the app left the foreground, then on return ask the ViewModel whether
    // enough time passed to re-lock. `firstStart` skips the very first
    // onStart after cold-create, since cold-start locking is already decided
    // by the ViewModel's own init (see WearViewModel's localStore.flow collect).
    private var backgroundedAt = 0L
    private var firstStart = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ask once for notification permission so the watch can surface command
        // results / alerts (Wear OS 4+ gates POST_NOTIFICATIONS at runtime).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        setContent {
            val ui by viewModel.ui.collectAsState()
            BlooWearTheme(ui.settings) {
                // Combine phone-synced scale with local watch override.
                val density = LocalDensity.current
                val phoneScale = ui.settings?.uiScale ?: 1f
                val localScale = ui.localSettings.fontScale
                // Cap the PRODUCT of the two app-convenience sliders at 1.4. Each is
                // independently clamped (~1.3 and ~1.4), but multiplying them let two
                // maxed sliders reach ~1.82× — and that then multiplies the OS
                // accessibility fontScale below, so text could hit ~2.3× and truncate
                // everywhere ("cut off weirdly"). 1.4 = roughly one maxed slider's
                // worth; the app's own scale never compounds past that. The OS
                // accessibility fontScale is still applied in full (density.fontScale
                // below), so true accessibility scaling is untouched — only the app's
                // two convenience knobs are prevented from stacking.
                val effectiveScale = (phoneScale * localScale).coerceIn(0.5f, 1.4f)
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
        // Also re-check for a newer build (debounced) so an update pushed while
        // the app was backgrounded surfaces on return, not only on cold relaunch.
        viewModel.onAppResumed()
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (firstStart) {
            firstStart = false
        } else {
            viewModel.maybeRelock(backgroundedAt)
        }
    }
}
