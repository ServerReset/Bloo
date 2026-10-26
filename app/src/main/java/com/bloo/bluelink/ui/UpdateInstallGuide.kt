package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.MorphActionButton

/**
 * Shown before the system installer runs when Shizuku (seamless install) is off.
 *
 * Without Shizuku the OS installer gets in the way every time, and its "Blocked by Play Protect"
 * sheet is folded shut by default, so a normal update can look like it failed. Laying the steps out
 * first, before the sheet appears, turns that into an expected tap. Built on the app's standard
 * [GlassAlertDialog].
 */
@Composable
internal fun UpdateInstallGuideDialog(onDismiss: () -> Unit, onContinue: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "Install the update",
        icon = Icons.Filled.SystemUpdate,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                RevealText("Android will hand off to its installer. Two things to expect:", style = SecondaryTextStyle())
                RevealMutedText("1. Tap \"Install\" on the system sheet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                RevealMutedText("2. If it says \"Blocked by Play Protect\" (this copy was installed from a file, not from Google Play), tap \"More details\", then \"Install anyway\".", color = MaterialTheme.colorScheme.onSurfaceVariant)
                RevealMutedText("3. Confirm with your PIN, password or biometrics.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        buttons = {
            ActionRow(spacing = GapGroup) {
                MorphTextButton(text = "Cancel", onClick = onDismiss)
                MorphActionButton(
                    label = "Continue",
                    icon = AppIcons.Check,
                    onClick = onContinue,
                    emphasis = ButtonEmphasis.Confirm,
                )
            }
        },
    )
}
