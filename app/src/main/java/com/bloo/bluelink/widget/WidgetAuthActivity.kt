package com.bloo.bluelink.widget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.Shortcuts
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.WearCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A transparent activity that gates a widget button behind a biometric / device-
 * credential prompt (widgets can't show the prompt themselves), then runs the
 * action with the stored session and refreshes the widget. Finishes immediately
 * so the user stays on their home screen.
 */
class WidgetAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vin = intent.getStringExtra(EXTRA_VIN)
        val action = WidgetAction.fromKey(intent.getStringExtra(EXTRA_ACTION))
        if (vin == null || action == null) { finish(); return }

        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuth = BiometricManager.from(this)
            .canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

        if (action.requiresAuth && canAuth) {
            promptThenRun(action, vin, authenticators)
        } else {
            run(action, vin)
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
                    finish()
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
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { execute(action, vin) }
            finish()
        }
    }

    private suspend fun execute(action: WidgetAction, vin: String) {
        val ctx = applicationContext
        when (action.kind) {
            WidgetAction.Kind.COMMAND ->
                action.wearAction?.let { WearCommandRunner.execute(ctx, WearCommand(vin, it)) }

            WidgetAction.Kind.REFRESH -> WearCommandRunner.refresh(ctx, vin)

            WidgetAction.Kind.LOCATION -> {
                WearCommandRunner.refresh(ctx, vin)
                val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
                val lat = snap?.lat
                val lon = snap?.lon
                if (lat != null && lon != null) openMaps(lat, lon) else openApp(vin)
            }

            WidgetAction.Kind.OPEN -> openApp(vin)
        }
        runCatching { BlooWidget().updateAll(ctx) }
    }

    private fun openMaps(lat: Double, lon: Double) {
        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Car)")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }.onFailure {
            val web = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(web) }
        }
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
