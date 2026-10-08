package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shown before the system installer runs when Shizuku (seamless install) is off.
 *
 * Without Shizuku the OS installer gets in the way every time, and its "Blocked by Play Protect"
 * sheet is folded shut by default, so a normal update can look like it failed. Laying the steps out
 * first, before the sheet appears, turns that into an expected tap. Built on the app's standard
 * [GlassAlertDialog].
 */
@Composable
internal fun UpdateInstallGuideDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "Install the update",
        icon = Icons.Filled.SystemUpdate,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                Text(
                    "Android will hand off to its installer. Two things to expect:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "1. Tap \"Install\" on the system sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "2. If it says \"Blocked by Play Protect\" (Bloo does not come from the Play Store), tap \"More details\", then \"Install anyway\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "3. Confirm with your PIN, password or biometrics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        buttons = {
            ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = GapGroup) {
                SafeMorphTextButton(text = "Cancel", onClick = onDismiss)
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
