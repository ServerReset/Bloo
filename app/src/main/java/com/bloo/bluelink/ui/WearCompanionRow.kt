package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Watch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

/** A Wear OS companion shown UNDER the phone it belongs to, not as a peer row. */
@Composable
fun WearCompanionRow(
    name: String,
    detail: String,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier
            .fillMaxWidth()
            .outlinedPanel(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.SubdirectoryArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(GapRow))
        Icon(
            Icons.Filled.Watch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name.ifBlank { "Watch" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                DeviceTag("Companion", MaterialTheme.colorScheme.primary)
            }
            if (detail.isNotBlank()) {
                RollingNumber(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onRemove != null) {
            ConfirmRemoveButton(name, "this watch", onRemove)
        }
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
        Icon(
            Icons.Filled.SubdirectoryArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(GapRow))
        Column(Modifier.weight(1f)) {
            SafeMorphTextButton(label, onClick = onClick, icon = Icons.Filled.Watch)
            BodySmallText(caption)
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
            BodyMediumText(
                "Keep settings in sync across devices with one Drive file.",
                color = scheme.onSurfaceVariant,
            )
            // Join first — it's the correct choice when another device already set sync up, and
            // making it the emphasized (active) card steers people away from accidentally creating
            // a second file.
            DriveSyncChoiceRow(
                icon = Icons.Filled.FileOpen,
                title = "Open from Drive",
                subtitle = "Join the file another device set up to share settings.",
                emphasized = hasExistingSync,
                onClick = onOpenFromDrive,
            )
            if (warnNewFile) {
                // The trap, spelled out, with the safe alternative one tap away.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(StandardShape)
                        .background(scheme.errorContainer.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(GapRow),
                ) {
                    Text(
                        "Starts a new Drive file. Your devices stop sharing settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onErrorContainer,
                    )
                    SafeMorphTextButton(
                        text = "Create a new file anyway",
                        onClick = onSaveToDrive,
                        modifier = Modifier.fillMaxWidth(),
                        emphasis = ButtonEmphasis.Deny,
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
        buttons = {
            MorphTextButton("Cancel", onClick = onDismissRequest, modifier = Modifier.fillMaxWidth())
        },
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
    // The app's standard button component (MorphButton), not a bespoke Surface row -- so this
    // dialog's actions look and feel like every other button in the app instead of a one-off.
    MorphButton(
        onClick = onClick,
        active = emphasized,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
        Spacer(Modifier.width(ButtonIconGap))
        Column(horizontalAlignment = Alignment.Start) {
            TitleSmallText(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
