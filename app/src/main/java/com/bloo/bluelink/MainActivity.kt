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
import androidx.fragment.app.FragmentActivity
import com.bloo.bluelink.ui.AppViewModel
import com.bloo.bluelink.ui.BlooApp
import com.bloo.bluelink.ui.BlooTheme
import com.bloo.bluelink.work.AlertWorker

class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

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
        // Notification permission is requested from the onboarding screen (on a
        // button tap), not silently on first launch.
        handleShortcutIntent(intent)
        setContent {
            val appearance by viewModel.appearance.collectAsState()
            BlooTheme(
                themeMode = appearance.themeMode,
                fontChoice = appearance.fontChoice,
                dynamicColor = appearance.dynamicColor,
                uiScale = appearance.uiScale,
                vibrancy = appearance.vibrancy,
            ) {
                BlooApp(viewModel)
            }
        }
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
