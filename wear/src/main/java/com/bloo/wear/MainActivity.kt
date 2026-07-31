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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import com.bloo.wear.ui.BlooWearTheme
import com.bloo.wear.ui.WatchApp

/**
 * The single launcher Activity for the Bloo watch app.
 *
 * FROZEN CONTRACT: this class is referenced as `.MainActivity` (LAUNCHER) in the
 * manifest, so its fully-qualified name — package `com.bloo.wear`, class
 * `MainActivity` — must not change. Placed tiles/complications and the OS point
 * at it by name.
 *
 * Responsibilities, kept deliberately thin so the whole app lives in Compose +
 * the [WearViewModel]:
 *  - own the process-scoped [WearViewModel] (via `by viewModels()`),
 *  - request POST_NOTIFICATIONS once on Wear OS 4+,
 *  - host the Compose tree under [BlooWearTheme] with a font-scale that combines
 *    the phone-synced UI scale, the local watch override, and the OS
 *    accessibility scale (with the app's two knobs capped — see below),
 *  - drive two lifecycle hooks: connection/update recheck on resume, and
 *    PIN re-lock timing across background/foreground transitions.
 */
class MainActivity : ComponentActivity() {

    /** Process-scoped VM; survives config changes and screen rebuilds. */
    private val viewModel: WearViewModel by viewModels()

    /**
     * One-shot POST_NOTIFICATIONS request. Best-effort: the watch still works
     * without it, we just can't surface command-result / alert notifications,
     * so the callback intentionally does nothing.
     */
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    // --- PIN re-lock timing (mirrors the phone's MainActivity) ---------------
    //
    // We record the wall-clock time the app leaves the foreground, then on
    // return ask the ViewModel whether enough time elapsed to re-lock (per the
    // user's chosen timing: immediate / 1min / 5min / 10min / off).
    //
    // `firstStart` skips the very first onStart after cold-create: cold-start
    // locking is already decided by the ViewModel's own init (its localStore
    // flow collect sets `pinLocked` before the Ready screen can render). Without
    // this guard the first foregrounding would run maybeRelock against a zero
    // `backgroundedAt` — harmless today, but the guard keeps the two lock paths
    // strictly separated.
    private var backgroundedAt = 0L
    private var firstStart = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask once for notification permission so the watch can surface command
        // results / alerts. Wear OS 4+ (Android 13 / TIRAMISU) gates
        // POST_NOTIFICATIONS at runtime; older versions grant it at install.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }

        setContent {
            // Settings arrive from the phone (or standalone defaults) via the VM;
            // BlooWearTheme derives the color scheme + typography from them.
            val ui by viewModel.ui.collectAsState()

            BlooWearTheme(settings = ui.settings) {
                val density = LocalDensity.current

                // Two independent app-convenience scales:
                //  - phoneScale: WearSettingsPayload.uiScale, mirrored from the phone.
                //  - localScale: the watch-local text-size override.
                val phoneScale = ui.settings?.uiScale ?: 1f
                val localScale = ui.localSettings.fontScale

                // Cap the PRODUCT of the two app-convenience sliders at 1.4. Each
                // is independently clamped (~1.3 and ~1.4), but multiplying them
                // let two maxed sliders reach ~1.82x — and that then multiplies
                // the OS accessibility fontScale below, so text could hit ~2.3x
                // and truncate everywhere ("cut off weirdly"). 1.4 = roughly one
                // maxed slider's worth; the app's own scale never compounds past
                // that. The OS accessibility fontScale is still applied in full
                // (density.fontScale, below), so true accessibility scaling is
                // untouched — only the app's two convenience knobs are prevented
                // from stacking.
                val effectiveScale = (phoneScale * localScale).coerceIn(0.5f, 1.4f)

                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = density.density,
                        fontScale = density.fontScale * effectiveScale,
                    )
                ) {
                    WatchApp(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check phone reachability whenever the watch face returns to Bloo,
        // so a link that dropped while backgrounded reflects immediately.
        viewModel.refreshConnection()
        // Debounced re-check for a newer build, so an update pushed while the app
        // was backgrounded surfaces on return — not only on a cold relaunch.
        viewModel.onAppResumed()
    }

    override fun onStop() {
        super.onStop()
        // Stamp the moment we leave the foreground; consumed by maybeRelock().
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (firstStart) {
            // Cold-start lock state is owned by the VM's init; don't double-decide.
            firstStart = false
        } else {
            // Returning to the foreground: let the VM apply the PIN timing rule.
            viewModel.maybeRelock(backgroundedAt)
        }
    }
}
