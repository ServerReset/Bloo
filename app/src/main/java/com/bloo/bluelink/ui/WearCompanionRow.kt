package com.bloo.bluelink.ui

import com.bloo.uicommon.PrimaryText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton

/**
 * The watch, as one card under the phone it belongs to: its glyph, its name and status (PRIMARY and SECONDARY text), a remove
 * button for a watch Bloo knows but is not connected to, and [action] under them when there is something to do (signing it in).
 * Deliberately not a [SyncDeviceRow]: a watch has no drag handle and no star, because it depends on its phone. [onRemove] is null
 * for a live Data Layer watch -- pairing is managed by Wear OS, not by Bloo.
 */
@Composable
fun WearCompanionRow(
    name: String,
    detail: String,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)?,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .innerGlassPanel(GapGroup),
        verticalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Filled.Watch, tint = MaterialTheme.colorScheme.primary, size = 36.dp, iconSize = 18.dp)
            Spacer(Modifier.width(GapGroup))
            Column(Modifier.weight(1f)) {
                PrimaryText(name.ifBlank { "Watch" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) SecondaryText(detail)
            }
            if (onRemove != null) RemoveDeviceButton(name, "this watch", onRemove)
        }
        action?.invoke()
    }
}

/**
 * An action hanging off a device row in the sync list: the standard button with a one-line caption
 * under it, indented to sit under the device it belongs to. "Set up watch" and "Sign watch in" are
 * both this.
 */
@Composable
fun CompanionActionRow(label: String, caption: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // No leading arrow, matching WearCompanionRow: the indent carries the relationship, and the
        // button already wears the watch glyph.
        Column(Modifier.weight(1f)) {
            MorphTextButton(label, onClick = onClick, icon = Icons.Filled.Watch)
            SecondaryText(caption)
        }
    }
}

/**
 * Two tappable choice cards instead of three same-weight text buttons, so "start fresh" vs. "join
 * an existing sync" reads as an actual decision rather than an arbitrary button order.
 */
@Composable
internal fun DriveSyncSetupDialog(
    onDismissRequest: () -> Unit,
    onSaveToDrive: () -> Unit,
    onOpenFromDrive: () -> Unit,
    // True when this device has synced before / knows about other devices.
    hasExistingSync: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // Local warning step: first tap of "Save to Drive" while already synced flips this on and swaps
    // the row for a warning + explicit "Create anyway"; the recommended action is to join the
    // existing file instead.
    var warnNewFile by remember { mutableStateOf(false) }
    GlassAlertDialog(
        onDismissRequest = onDismissRequest,
        icon = Icons.Filled.Cloud,
        title = "Google Drive sync",
        text = {
            BodyMediumText("Keep settings in sync across devices with one Drive file.", color = scheme.onSurfaceVariant)
            // Join first - it's the correct choice when another device already set sync up, and
            // making it the emphasized (active) card steers people away from accidentally creating
            // a second file.
            DriveSyncChoiceRow(
                icon = Icons.Filled.FileOpen, title = "Open from Drive",
                subtitle = "Use the file another device already set up.", emphasized = hasExistingSync,
                onClick = onOpenFromDrive,
            )
            if (warnNewFile) {
                // The trap, spelled out, with the safe alternative one tap away.
                Column(
                    Modifier.tintedPanel(scheme.errorContainer.copy(alpha = 0.5f)),
                    verticalArrangement = Arrangement.spacedBy(GapRow),
                ) {
                    RevealText(
                        "Starts a new Drive file. Your devices stop sharing settings.",
                        style = SecondaryTextStyle(),
                        color = scheme.onErrorContainer,
                    )
                    MorphTextButton(
                        text = "Create a new file anyway", onClick = onSaveToDrive,
                        modifier = Modifier.fillMaxWidth(), emphasis = ButtonEmphasis.Deny,
                    )
                }
            } else {
                DriveSyncChoiceRow(
                    icon = Icons.Filled.CreateNewFolder,
                    title = "Save to Drive",
                    subtitle = "Start fresh with this device's settings.",
                    onClick = { if (hasExistingSync) warnNewFile = true else onSaveToDrive() },
                )
            }
        },
        buttons = { DialogButton("Cancel", onDismissRequest) },
    )
}

@Composable
internal fun DriveSyncChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    // Highlights this choice as the recommended one (filled/active MorphButton).
    emphasized: Boolean = false,
) {
    // The app's one list button, like every other pick-one list.
    ListButton(title = title, onClick = onClick, subtitle = subtitle, icon = icon, active = emphasized)
}
