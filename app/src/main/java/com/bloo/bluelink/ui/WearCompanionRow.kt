package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphButton
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.SafeMorphTextButton

/**
 * A Wear OS companion shown UNDER the phone it belongs to, not as a peer row. Deliberately not a
 * [SyncDeviceRow]: a watch has no drag handle and no star, because it depends on its phone.
 * [onRemove] is null for a live Data Layer watch -- pairing is managed by Wear OS, not by Bloo.
 */
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
            // The same inner-glass panel the phone rows use, one step down in size, rather than the
            // outlinedPanel(10.dp) it had: a watch is part of its phone's row, not a different kind of
            // box.
            .innerGlassPanel(GapGroup),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No SubdirectoryArrowRight: the indent IS the "belongs to the phone above" signal, and the
        // Watch icon says what it is. Two leading icons for one row was one too many.
        Icon(
            Icons.Filled.Watch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(GapGroup))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RevealText(
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
        // No leading arrow, matching WearCompanionRow: the indent carries the relationship, and the
        // button already wears the watch glyph.
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
                subtitle = "Use the file another device already set up.",
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
                        .padding(GapSection),
                    verticalArrangement = Arrangement.spacedBy(GapRow),
                ) {
                    RevealText(
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
            MorphTextButton("Cancel", onClick = onDismissRequest, modifier = Modifier.fillMaxWidth(), emphasis = ButtonEmphasis.Deny)
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
    // The app's one list button, like every other pick-one list.
    ListButton(title = title, onClick = onClick, subtitle = subtitle, icon = icon, active = emphasized)
}
