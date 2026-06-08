package com.bloo.bluelink.widget

import android.os.Bundle
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.bloo.bluelink.data.BlueLinkApi
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent activity launched by widget command buttons. It authenticates the
 * user (fingerprint, falling back to device PIN), sends the command, and shows a
 * toast with the result.
 */
class CommandActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra(EXTRA_ACTION)
        if (action == null) {
            finish()
            return
        }
        authenticateThen { performAndFinish(action) }
    }

    private fun authenticateThen(onOk: () -> Unit) {
        val mgr = BiometricManager.from(this)
        val canBiometric = mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        val canCredential = mgr.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
        if (!canBiometric && !canCredential) {
            onOk()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    finish()
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm command")
            .setSubtitle("Authenticate to control your car")
        if (canBiometric) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("Cancel")
        } else {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        }
        prompt.authenticate(builder.build())
    }

    private fun performAndFinish(action: String) {
        val ctx = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val selected = SnapshotStore(ctx).current().selected
            val message = if (selected == null) {
                "No car selected — open Bloo first"
            } else {
                val repo = BlueLinkRepository(BlueLinkApi(), SessionStore(ctx))
                runCatching { perform(repo, selected.toVehicle(), action) }
                    .fold({ confirmText(action) }, { it.message ?: "Command failed" })
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
                BlooGlanceWidget().updateAll(ctx)
                finish()
            }
        }
    }

    private suspend fun perform(repo: BlueLinkRepository, v: Vehicle, action: String) {
        when (action) {
            "lock" -> repo.lock(v)
            "unlock" -> repo.unlock(v)
            "climate_on" -> repo.startClimate(v, ClimateRequest(tempF = 72, defrost = false, durationMinutes = 10))
            "climate_off" -> repo.stopClimate(v)
            "charge_on" -> repo.startCharge(v)
            "charge_off" -> repo.stopCharge(v)
        }
    }

    private fun confirmText(action: String) = when (action) {
        "lock" -> "Doors locked"
        "unlock" -> "Doors unlocked"
        "climate_on" -> "Climate started"
        "climate_off" -> "Climate stopped"
        "charge_on" -> "Charging started"
        "charge_off" -> "Charging stopped"
        else -> "Command sent"
    }

    companion object {
        const val EXTRA_ACTION = "action"
    }
}
