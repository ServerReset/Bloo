package com.bloo.bluelink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bloo.bluelink.data.StartupTrace
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.ui.AppViewModel
import com.bloo.bluelink.ui.BlooApp
import com.bloo.bluelink.ui.BlooTheme
import com.bloo.bluelink.ui.ensureAutoLockWatcher
import com.bloo.bluelink.ui.handleShortcut
import com.bloo.bluelink.ui.maybeRelock
import com.bloo.bluelink.ui.onShizukuPermissionResult
import com.bloo.bluelink.ui.refreshShizukuAvailable
import com.bloo.bluelink.work.AlertWorker
import com.bloo.bluelink.work.MainToMainSyncWorker
import com.bloo.bluelink.work.UpdateCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku

/**
 * The app's single Activity: hosts the Compose UI tree ([BlooApp]) and owns the process-wide setup
 * that only needs to happen once per launch -- scheduling the background workers and routing
 * shortcut intents into the ViewModel.
 */
class MainActivity : FragmentActivity() {

    companion object {
        /**
         * The most recently created MainActivity, for system-level integrations that have no view
         * of their own -- currently only [StartupFrameMonitor] calling the platform's own
         * `reportFullyDrawn()` once the first frame lands, which turns the app's internal timing
         * into the same "fully drawn" number `adb shell am start -W` prints.
         */
        @Volatile
        var current: MainActivity? = null
            private set
    }

    private val viewModel: AppViewModel by viewModels()

    // App-lock bookkeeping: when we last left the foreground, and whether this is the very first
    // foreground (cold start, where the ViewModel already decides the lock).
    private var backgroundedAt = 0L
    private var firstStart = true

    // Wall-clock time the screen last turned off, for LockTiming.SCREEN_OFF. The re-lock predicate
    // only counts a screen-off that happened AFTER the app was backgrounded (screenOffAt >
    // backgroundedAt), so a screen timeout while the user is actively using the app never re-locks
    // it.
    @Volatile
    private var screenOffAt = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) screenOffAt = System.currentTimeMillis()
        }
    }

    // Shizuku runtime-permission result → forward to the ViewModel so the update flow can proceed
    // once the user grants it. Registered only while Shizuku is present.
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        viewModel.onShizukuPermissionResult(requestCode, grantResult)
    }

    /**
     * Runs once when the Activity's process/window is created (not on every foreground -- see
     * [onStart]/[onStop] for that).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("MainActivity.onCreate: begin")
        super.onCreate(savedInstanceState)
        current = this
        StartupTrace.mark("MainActivity.super.onCreate done")
        // ACTION_SCREEN_OFF is a protected broadcast (a manifest receiver never sees it) but a
        // runtime-registered one does. Exported so the SYSTEM can deliver it.
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_EXPORTED,
        )
        // Fully transparent system bars so the app's gradient shows through and content can draw
        // edge-to-edge behind the status & navigation bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // Seed com.bloo.bluelink.ui.inMultiWindowMode from the Activity's actual starting state --
        // onMultiWindowModeChanged (below) only fires on a TRANSITION, so a cold start directly
        // INTO split-screen/freeform would otherwise never set this at all and StatusBarScrim would
        // blur empty space outside this window from the very first frame.
        com.bloo.bluelink.ui.inMultiWindowMode = isInMultiWindowMode
        // Each schedule() call does a synchronous Room round-trip inside WorkManager's
        // enqueueUniquePeriodicWork (regardless of the ExistingPeriodicWorkPolicy), so running
        // these on the main thread ahead of setContent() delays the first Compose frame on every
        // cold start.
        lifecycleScope.launch(Dispatchers.Default) {
            AlertWorker.schedule(applicationContext)
            MainToMainSyncWorker.schedule(applicationContext)
            UpdateCheckWorker.schedule(applicationContext)
        }
        // Shizuku (optional silent-install path): lift the runtime non-SDK block once so the
        // reflected PackageInstaller/IntentSender constructors are callable, and listen for the
        // permission-grant result.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lifecycleScope.launch(Dispatchers.Default) {
                StartupTrace.trace("HiddenApiBypass.addHiddenApiExemptions") {
                    runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
                }
            }
        }
        // Register unconditionally (binder-independent, cheap): if Shizuku is started AFTER launch
        // and the user later grants permission, the result still routes to
        // onShizukuPermissionResult. Removed in onDestroy under runCatching.
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }
        // Notification permission is requested from the onboarding screen (on a button tap), not
        // silently on first launch.
        if (savedInstanceState == null) {
            handleShortcutIntent(intent)
            setIntent(Intent())
        }
        StartupTrace.mark("MainActivity: pre-setContent work done")
        // Touched HERE, before setContent, and deliberately not for its value. Six stores, five
        // DataStore collectors and the auto-login kick-off all landed on the first-frame critical
        // path.
        @Suppress("UNUSED_EXPRESSION")
        viewModel
        StartupTrace.mark("MainActivity: AppViewModel constructed (the `viewModel` deref)")
        StartupTrace.mark("MainActivity: setContent begin")
        setContent {
            val appearance by viewModel.appearance.collectAsState()
            // Memoize palette lookup to avoid repeated linear search through custom palettes
            val activeCustom = remember(
                appearance.dynamicColor, appearance.customPalettes, appearance.activeCustomPaletteId
            ) {
                if (!appearance.dynamicColor)
                    appearance.customPalettes.find { it.id == appearance.activeCustomPaletteId }
                else null
            }
            BlooTheme(
                themeMode = appearance.themeMode,
                fontChoice = appearance.fontChoice,
                dynamicColor = appearance.dynamicColor,
                colorPalette = appearance.colorPalette,
                customPalette = activeCustom,
                uiScale = appearance.uiScale,
                vibrancy = appearance.vibrancy,
            ) {
                BlooApp(viewModel)
            }
        }
    }

    /**
     * Keeps [com.bloo.bluelink.ui.inMultiWindowMode] live across a drag into/out of split-screen or
     * freeform while this Activity is already running -- see that flag's own doc for why
     * [StatusBarScrim][com.bloo.bluelink.ui.StatusBarScrim] needs to know.
     */
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        com.bloo.bluelink.ui.inMultiWindowMode = isInMultiWindowMode
    }

    /**
     * Records the wall-clock time the Activity left the foreground, so the next [onStart] can
     * measure how long the app was backgrounded for the app-lock check.
     */
    override fun onStop() {
        super.onStop()
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        StartupTrace.mark("MainActivity.onStart")
        super.onStart()
        // Reattach AutoLock's dynamic Bluetooth watcher for existing installs whose config was
        // already enabled before the watcher existed. This is idempotent: starting the service
        // again only refreshes its foreground notification and receiver, it does not start a car
        // evaluation.
        viewModel.ensureAutoLockWatcher()
        // Cold start is handled by the ViewModel; only re-evaluate on warm resumes.
        if (!firstStart) {
            viewModel.maybeRelock(backgroundedAt, screenOffAt > backgroundedAt)
            // The user may have started Shizuku while away (its own app / ADB); re-probe so the
            // "Updates" toggle appears without a cold restart. Off-main-thread.
            viewModel.refreshShizukuAvailable()
        }
        firstStart = false
    }

    override fun onResume() {
        StartupTrace.mark("MainActivity.onResume: begin")
        super.onResume()
        StartupTrace.mark("MainActivity.onResume: end")
    }

    /** Remove the Shizuku listener so it doesn't leak past this Activity instance. */
    override fun onDestroy() {
        if (current === this) current = null
        runCatching { unregisterReceiver(screenOffReceiver) }
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onDestroy()
    }

    /**
     * Called instead of a fresh [onCreate] when this Activity is already running and receives a new
     * launch Intent (e.g. tapping another shortcut/notification while the app is open) -- must
     * replace the stored intent via [setIntent] so a later config change/recreation doesn't
     * re-process the stale original intent.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    /** Route an app-icon shortcut (lock/unlock/climate/open a car) to the VM. */
    private fun handleShortcutIntent(intent: Intent?) {
        if (intent?.action != Shortcuts.ACTION) return
        val vin = intent.getStringExtra(Shortcuts.EXTRA_VIN) ?: return
        val cmd = intent.getStringExtra(Shortcuts.EXTRA_CMD) ?: return
        reportShortcutUsage(vin, cmd)
        viewModel.handleShortcut(vin, cmd)
    }

    /**
     * The id must be the EXACT one [Shortcuts.refresh] registered: "cmd_vin" for a car shortcut,
     * "bluelink_<brand>" for an "Open the <brand> app" shortcut. Best-effort by design: this is an
     * analytics nicety, never a reason for a shortcut tap to fail.
     */
    private fun reportShortcutUsage(vin: String, cmd: String) {
        val id = if (cmd == "bluelink") {
            val brand = runCatching {
                viewModel.state.value.vehicles.firstOrNull { it.vin == vin }?.brand?.name
            }.getOrNull() ?: return
            "bluelink_$brand"
        } else {
            "${cmd}_$vin"
        }
        runCatching { ShortcutManagerCompat.reportShortcutUsed(this, id) }
    }
}
