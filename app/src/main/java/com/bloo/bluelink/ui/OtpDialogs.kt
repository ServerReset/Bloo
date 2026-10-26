package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton

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
        icon = AppIcons.Lock,
        title = if (otp.sentTo == null) "Verify it's you" else "Enter your code",
        text = {
            if (otp.sentTo == null) {
                RevealText("Kia needs a one-time code. Where should it go?")
                if (otp.challenge.hasEmail) {
                    MorphTextButton(
                        text = "Email" + (otp.challenge.email?.let { " · $it" } ?: ""),
                        onClick = { vm.kiaSendOtp("EMAIL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                    )
                }
                if (otp.challenge.hasSms) {
                    MorphTextButton(
                        text = "Text message" + (otp.challenge.sms?.let { " · $it" } ?: ""),
                        onClick = { vm.kiaSendOtp("SMS") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                    )
                }
            } else {
                RevealText(
                    if (otp.sentTo == "SMS") "We texted you a one-time code."
                    else "We emailed you a one-time code.",
                )
                OtpCodeField(code) { code = it }
            }
        },
        buttons = {
            // Verify shown only once a code's been sent; Cancel always. Stacked full-width (primary
            // on top) per the shell's convention.
            OtpButtons(loading, code, showVerify = otp.sentTo != null, onVerify = { vm.kiaVerifyOtp(code) }, onCancel = vm::kiaCancelOtp)
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
        icon = AppIcons.Lock,
        title = "Enter your code",
        text = {
            RevealText(
                "We emailed a one-time code" +
                    (otp.challenge.email?.let { " to $it" } ?: "") + " to verify this sign-in.",
            )
            OtpCodeField(code) { code = it }
        },
        buttons = {
            OtpButtons(loading, code, showVerify = true, onVerify = { vm.canadaVerifyOtp(code) }, onCancel = vm::canadaCancelOtp)
        },
    )
}

/**
 * The one-time-code field shared by [KiaOtpDialog] and [CanadaOtpDialog]; callers hoist `code`
 * since Verify reads it.
 */
@Composable
internal fun OtpCodeField(code: String, onCodeChange: (String) -> Unit) {
    LabeledField(code, onCodeChange, "Code", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

/** The one-time-code dialog's buttons, stacked full-width with the primary on top: Verify (once a code was sent) and Cancel. */
@Composable
private fun OtpButtons(loading: Boolean, code: String, showVerify: Boolean, onVerify: () -> Unit, onCancel: () -> Unit) {
    if (showVerify) {
        MorphTextButton(
            text = "Verify", onClick = onVerify, modifier = Modifier.fillMaxWidth(),
            enabled = !loading && code.isNotBlank(), pending = loading, emphasis = ButtonEmphasis.Confirm,
        )
    }
    MorphTextButton("Cancel", onCancel, enabled = !loading, modifier = Modifier.fillMaxWidth())
}
