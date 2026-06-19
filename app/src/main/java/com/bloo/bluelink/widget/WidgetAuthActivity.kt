package com.bloo.bluelink.widget

import android.content.Intent
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.Shortcuts
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearCommandRunner
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch

/**
 * A transparent activity that gates a widget button behind a biometric / device-
 * credential prompt (widgets can't show the prompt themselves), then runs the
 * action with the stored session and refreshes the widget. Finishes immediately
 * so the user stays on their home screen.
 */
class WidgetAuthActivity : FragmentActivity() {

    private var widgetId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        val vin = intent.getStringExtra(EXTRA_VIN)
        val action = WidgetAction.fromKey(intent.getStringExtra(EXTRA_ACTION))
        if (vin == null || action == null) { finish(); return }

        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuth = BiometricManager.from(this)
            .canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

        lifecycleScope.launch {
            val requireAuth = SettingsStore(applicationContext).widgetRequireAuth(widgetId)
            if (action.requiresAuth && canAuth && requireAuth) {
                promptThenRun(action, vin, authenticators)
            } else {
                run(action, vin)
            }
        }
    }

    private fun promptThenRun(action: WidgetAction, vin: String, authenticators: Int) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    run(action, vin)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finishNoAnim()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm with Bloo")
            .setSubtitle(action.label)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    private fun run(action: WidgetAction, vin: String) {
        if (action.kind == WidgetAction.Kind.OPEN) {
            openApp(vin)
            finishNoAnim()
            return
        }
        lifecycleScope.launch {
            val ctx = applicationContext
            // Optimistic snapshot update so the widget immediately shows the expected
            // new state (behind the loading icon) once the home screen comes forward.
            if (action.wearAction != null) {
                runCatching {
                    val store = SnapshotStore(ctx)
                    val snap = store.current().vehicles.firstOrNull { it.vin == vin }
                    if (snap != null) store.updateVehicle(WearCommandRunner.optimistic(snap, action.wearAction))
                }
            }
            // Mark pending so the widget overlays the refresh spinner.
            SettingsStore(ctx).setWidgetPendingAction(widgetId, action.key)
            runCatching { BlooWidget().updateAll(ctx) }
            // Queue the actual work; finish right away so the home screen is unblocked.
            WidgetCommandWorker.enqueue(ctx, widgetId, vin, action)
            finishNoAnim()
        }
    }

    /** Finish without any window animation (prevents the opaque task-switch flash). */
    private fun finishNoAnim() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun openApp(vin: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Shortcuts.ACTION
            putExtra(Shortcuts.EXTRA_VIN, vin)
            putExtra(Shortcuts.EXTRA_CMD, "open")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(intent) }
    }

    companion object {
        const val ACTION_RUN = "com.bloo.bluelink.widget.RUN"
        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_VIN = "vin"
        const val EXTRA_ACTION = "action"
    }
}
