package com.bloo.bluelink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.bloo.bluelink.ui.AppViewModel
import com.bloo.bluelink.ui.BlooApp
import com.bloo.bluelink.ui.BlooTheme
import com.bloo.bluelink.widget.WidgetRefreshWorker
import com.bloo.bluelink.work.AlertWorker

class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

    // App-lock bookkeeping: when we last left the foreground, whether the screen
    // turned off meanwhile, and whether this is the very first foreground (cold
    // start, where the ViewModel already decides the lock).
    private var backgroundedAt = 0L
    private var screenOffWhileAway = false
    private var firstStart = true
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) screenOffWhileAway = true
        }
    }

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
        AlertWorker.schedule(applicationContext)
        // Keep widgets, the watch, and QS tiles fresh in the background even when
        // the app is closed — the phone-hub heartbeat for all spokes.
        WidgetRefreshWorker.schedule(applicationContext)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Notification permission is requested from the onboarding screen (on a
        // button tap), not silently on first launch.
        handleShortcutIntent(intent)
        setContent {
            val appearance by viewModel.appearance.collectAsState()
            val activeCustom = if (!appearance.dynamicColor)
                appearance.customPalettes.find { it.id == appearance.activeCustomPaletteId }
            else null
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

    override fun onStop() {
        super.onStop()
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Cold start is handled by the ViewModel; only re-evaluate on warm resumes.
        if (!firstStart) {
            viewModel.maybeRelock(backgroundedAt, screenOffWhileAway)
        }
        firstStart = false
        screenOffWhileAway = false
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

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
