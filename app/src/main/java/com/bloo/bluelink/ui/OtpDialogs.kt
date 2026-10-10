package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

// --- One-time-code dialogs for the region-specific sign-in challenges ---

/**
 * Kia sign-in verification: pick where the one-time code goes (email/text), then enter it. Shown
 * over the login form while a Kia OTP challenge is open.
 */
@Composable
internal fun KiaOtpDialog(otp: KiaOtpUi, loading: Boolean, vm: AppViewModel) {
    var code by remember(otp.sentTo) { mutableStateOf("") }
    // Shared GlassAlertDialog shell with stacked full-width buttons.
    GlassAlertDialog(
        onDismissRequest = { if (!loading) vm.kiaCancelOtp() },
        icon = Icons.Filled.Lock,
        title = if (otp.sentTo == null) "Verify it's you" else "Enter your code",
        text = {
            if (otp.sentTo == null) {
                Text("Kia needs a one-time code. Where should it go?")
                if (otp.challenge.hasEmail) {
                    SafeMorphTextButton(
                        text = "Email" + (otp.challenge.email?.let { " · $it" } ?: ""),
                        onClick = { vm.kiaSendOtp("EMAIL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                    )
                }
                if (otp.challenge.hasSms) {
                    SafeMorphTextButton(
                        text = "Text message" + (otp.challenge.sms?.let { " · $it" } ?: ""),
                        onClick = { vm.kiaSendOtp("SMS") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                    )
                }
            } else {
                Text(
                    if (otp.sentTo == "SMS") "We texted you a one-time code."
                    else "We emailed you a one-time code.",
                )
                OtpCodeField(code) { code = it }
            }
        },
        buttons = {
            // Verify shown only once a code's been sent; Cancel always. Stacked full-width (primary
            // on top) per the shell's convention.
            if (otp.sentTo != null) {
                SafeMorphTextButton(
                    text = "Verify",
                    onClick = { vm.kiaVerifyOtp(code) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading && code.isNotBlank(),
                    pending = loading,
                    emphasis = ButtonEmphasis.Confirm,
                )
            }
            SafeMorphTextButton(
                "Cancel",
                vm::kiaCancelOtp,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                emphasis = ButtonEmphasis.Deny,
            )
        },
    )
}

/**
 * Canada sign-in verification: unlike [KiaOtpDialog] there is no destination to pick, since the
 * code is already sent (see AppViewModel.loginCanada), so it goes straight to code entry.
 */
@Composable
internal fun CanadaOtpDialog(otp: CanadaOtpUi, loading: Boolean, vm: AppViewModel) {
    var code by remember(otp.challenge) { mutableStateOf("") }
    GlassAlertDialog(
        onDismissRequest = { if (!loading) vm.canadaCancelOtp() },
        icon = Icons.Filled.Lock,
        title = "Enter your code",
        text = {
            Text(
                "We emailed a one-time code" +
                    (otp.challenge.email?.let { " to $it" } ?: "") + " to verify this sign-in.",
            )
            OtpCodeField(code) { code = it }
        },
        buttons = {
            SafeMorphTextButton(
                text = "Verify",
                onClick = { vm.canadaVerifyOtp(code) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading && code.isNotBlank(),
                pending = loading,
                emphasis = ButtonEmphasis.Confirm,
            )
            SafeMorphTextButton(
                "Cancel",
                vm::canadaCancelOtp,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                emphasis = ButtonEmphasis.Deny,
            )
        },
    )
}

/**
 * The one-time-code field shared by [KiaOtpDialog] and [CanadaOtpDialog]; callers hoist `code`
 * since Verify reads it.
 */
@Composable
internal fun OtpCodeField(code: String, onCodeChange: (String) -> Unit) {
    BlooTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text("Code") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
