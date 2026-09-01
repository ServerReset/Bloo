package com.bloo.bluelink

import android.content.Intent
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bloo.bluelink.ui.AppViewModel
import com.bloo.bluelink.ui.BlooApp
import com.bloo.bluelink.ui.BlooTheme
import com.bloo.bluelink.work.AlertWorker
import com.bloo.bluelink.work.DriveSyncWorker
import com.bloo.bluelink.work.UpdateCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku

/**
 * The app's single Activity: hosts the Compose UI tree ([BlooApp]) and owns the
 * process-wide setup that only needs to happen once per launch -- scheduling the
 * background workers that keep widgets/watch/tiles fresh, wiring up the screen-off
 * receiver used for app-lock timing, and routing shortcut intents into the ViewModel.
 * All actual screen/business logic lives in [AppViewModel] and the Compose tree; this
 * class is deliberately thin plumbing around the Android Activity lifecycle.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    // App-lock bookkeeping: when we last left the foreground, and whether this is the
    // very first foreground (cold start, where the ViewModel already decides the lock).
    //
    // There used to be a third piece here -- a screenOffWhileAway flag fed by a
    // BroadcastReceiver registered for ACTION_SCREEN_OFF, passed to maybeRelock, and
    // read by nothing. See maybeRelock's own comment for why it was deleted rather
    // than wired up. Worth removing rather than leaving inert: it was a real
    // registered system receiver, woken on every screen-off for the whole life of
    // this Activity, plus the register/unregister lifecycle around it.
    private var backgroundedAt = 0L
    private var firstStart = true

    // Shizuku runtime-permission result → forward to the ViewModel so the update flow
    // can proceed once the user grants it. Registered only while Shizuku is present.
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        viewModel.onShizukuPermissionResult(requestCode, grantResult)
    }

    /**
     * Runs once when the Activity's process/window is created (not on every foreground --
     * see [onStart]/[onStop] for that). Order of operations: configure edge-to-edge system
     * bars, schedule all of the app's background WorkManager jobs (each `schedule()` call is
     * itself idempotent/unique-work-keyed, so calling this on every cold start doesn't create
     * duplicate schedules), route
     * in any shortcut intent that launched this instance, then finally hand off to Compose via
     * [setContent] -- which reads the current [AppViewModel.appearance] as Compose state so the
     * whole UI recomposes live if theme/appearance settings change while it's open.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fully transparent system bars so the app's gradient shows through and
        // content can draw edge-to-edge behind the status & navigation bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // Each schedule() call does a synchronous Room round-trip inside WorkManager's
        // enqueueUniquePeriodicWork (regardless of the ExistingPeriodicWorkPolicy), so
        // running these on the main thread ahead of setContent() delays the first
        // Compose frame on every cold start. Dispatched off-thread instead; the
        // schedules are idempotent/unique-work-keyed so ordering relative to the UI
        // doesn't matter.
        lifecycleScope.launch(Dispatchers.Default) {
            AlertWorker.schedule(applicationContext)
            // Drive settings sync used to only run while the app was foregrounded and
            // a refresh settled — a no-op periodic worker when sync isn't configured.
            DriveSyncWorker.schedule(applicationContext)
            // Bloo isn't on the Play Store, so it checks its own GitHub Actions builds
            // for updates; this is that check running even when the app is closed,
            // presenting a newer build via notification instead of the in-app tile.
            UpdateCheckWorker.schedule(applicationContext)
        }
        // Shizuku (optional silent-install path): lift the runtime non-SDK block once
        // so the reflected PackageInstaller/IntentSender constructors are callable, and
        // listen for the permission-grant result. Both are guarded — no-ops (and no
        // Shizuku classes touched beyond a cheap ping) when Shizuku isn't installed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { HiddenApiBypass.addHiddenApiExemptions("") }
        }
        // Register unconditionally (binder-independent, cheap): if Shizuku is started
        // AFTER launch and the user later grants permission, the result still routes to
        // onShizukuPermissionResult. Removed in onDestroy under runCatching.
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener) }
        // Notification permission is requested from the onboarding screen (on a
        // button tap), not silently on first launch.
        // Only route the shortcut on a genuine first creation, not on a
        // config-change/process-death recreation: getIntent() still returns the
        // original ACTION_SHORTCUT intent across recreation, so an unguarded call
        // here would re-fire the car command (duplicating/inverting a lock/unlock).
        // After handling, neutralize the stored intent so a later recreate can't
        // replay it. (onNewIntent handles the already-running case and setIntents
        // itself.)
        if (savedInstanceState == null) {
            handleShortcutIntent(intent)
            setIntent(Intent())
        }
        // Touched HERE, before setContent, and deliberately not for its value. `by viewModels()`
        // is lazy, and the first dereference used to be inside the composition lambda below --
        // so AppViewModel's constructor and its whole init block ran synchronously in the middle
        // of composing the first frame. Six stores, five DataStore collectors and the auto-login
        // kick-off all landed on the first-frame critical path. Constructing it here does not
        // make that work cheaper, but it stops it happening between "begin composition" and
        // "first pixel", which is the part the user sees as a stutter.
        @Suppress("UNUSED_EXPRESSION")
        viewModel
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

    /** Records the wall-clock time the Activity left the foreground, so the next
     *  [onStart] can measure how long the app was backgrounded for the app-lock check. */
    override fun onStop() {
        super.onStop()
        backgroundedAt = System.currentTimeMillis()
    }

    /**
     * Fires every time the Activity returns to the foreground (including the very first
     * launch). [firstStart] distinguishes that initial launch -- where [AppViewModel]'s own
     * init logic already decides whether to show the lock screen -- from every subsequent
     * warm resume, where [viewModel.maybeRelock] re-evaluates using how long the app was
     * backgrounded ([backgroundedAt]).
     */
    override fun onStart() {
        super.onStart()
        // Cold start is handled by the ViewModel; only re-evaluate on warm resumes.
        if (!firstStart) {
            viewModel.maybeRelock(backgroundedAt)
            // The user may have started Shizuku while away (its own app / ADB); re-probe
            // so the "Updates" toggle appears without a cold restart. Off-main-thread.
            viewModel.refreshShizukuAvailable()
        }
        firstStart = false
    }

    /** Remove the Shizuku listener so it doesn't leak past this Activity instance. */
    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) }
        super.onDestroy()
    }

    /** Called instead of a fresh [onCreate] when this Activity is already running and
     *  receives a new launch Intent (e.g. tapping another shortcut/notification while the
     *  app is open) -- must replace the stored intent via [setIntent] so a later config
     *  change/recreation doesn't re-process the stale original intent. */
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
        viewModel.handleShortcut(vin, cmd)
    }
}
