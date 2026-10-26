package com.bloo.bluelink.ui

import com.bloo.uicommon.Dot
import com.bloo.uicommon.MorphIconButton
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SyncMerge
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.ButtonIconSize
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapSection

private const val ACTIVE_DEVICE_MS = 10L * 60 * 1000

/** How recently a device checked in: drives the dot beside its name and the wording under it. */
private enum class DeviceHealth { ACTIVE, RECENT, STALE, UNKNOWN }

private fun healthOf(lastSeenMs: Long, isSelf: Boolean, now: Long): DeviceHealth = when {
    isSelf -> DeviceHealth.ACTIVE
    lastSeenMs <= 0 -> DeviceHealth.UNKNOWN
    now - lastSeenMs <= ACTIVE_DEVICE_MS -> DeviceHealth.ACTIVE
    now - lastSeenMs > STALE_DEVICE_MS -> DeviceHealth.STALE
    else -> DeviceHealth.RECENT
}

/**
 * One phone in the drag-to-reorder devices list, in an outlined box of its own: drag handle, a star
 * for the primary, the name with "This device" / "Primary" tags, a health dot with the model and
 * last-seen line, and rename (this device) or remove (any other; the remove dialog is in DriveSyncUi).
 */
@Composable
internal fun SyncDeviceRow(
    device: SyncMerge.SyncDevice,
    isSelf: Boolean,
    isPrimary: Boolean,
    dragging: Boolean,
    modifier: Modifier,
    onRename: () -> Unit,
    /**
     * Never offered for [isSelf]: removal is a courtesy prune of a stale peer, not a way to leave
     * sync.
     */
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = StandardShape
    val health = healthOf(device.lastSeenMs, isSelf, System.currentTimeMillis())
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (dragging) {
                    Modifier.themedDropShadow(shape)
                } else {
                    Modifier
                },
            )
            // The app's ONE inner-glass panel for something sitting inside a card
            // (SettingsPrimitives), not a per-row outline: four outlined boxes in a card read as four
            // boxes nested in a box, which is exactly the drift the onboarding section had.
            .innerGlassPanel(GapSection)
            ,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Drag to reorder",
            tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = modifier.size(20.dp),
        )
        Spacer(Modifier.width(GapGroup))
        // One icon per row: the device itself. The star was a second signal for what the chip says.
        Icon(
            Icons.Filled.Smartphone,
            contentDescription = null,
            tint = if (isPrimary) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(GapGroup))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RevealText(
                    device.name.ifBlank { "Unnamed device" },
                    style = PrimaryTextStyle(),
                    fontWeight = if (isPrimary || isSelf) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSelf) DeviceTag("This device", scheme.primary)
                if (isPrimary) DeviceTag("Primary", scheme.tertiary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dot = when (health) {
                    DeviceHealth.ACTIVE -> scheme.primary
                    DeviceHealth.RECENT -> scheme.outline
                    DeviceHealth.STALE -> scheme.error
                    DeviceHealth.UNKNOWN -> scheme.outlineVariant
                }
                Dot(dot, 7.dp)
                Spacer(Modifier.width(GapRow))
                val seen = when {
                    isSelf || health == DeviceHealth.ACTIVE -> "Active now"
                    else -> com.bloo.bluelink.data.relativeLabel(device.lastSeenMs).ifBlank { "Never synced" }
                }
                // Two short lines, each allowed to wrap: what it is, then when it was last seen and its version.
                val model = device.model.takeIf { it.isNotBlank() && !it.equals(device.name, ignoreCase = true) }
                val status = listOfNotNull(seen, device.appVersion.takeIf { it.isNotBlank() }?.let { "v$it" }).joinToString(" · ")
                Column {
                    model?.let { RevealText(it, style = SecondaryTextStyle(), color = scheme.onSurfaceVariant) }
                    RollingNumber(
                        status,
                        style = SecondaryTextStyle(),
                        fontWeight = FontWeight.Medium,
                        color = if (health == DeviceHealth.STALE) scheme.error else scheme.onSurfaceVariant,
                    )
                }
            }
            if (health == DeviceHealth.STALE) {
                RevealText(
                    "Hasn't synced in a while. It may be on a different Drive file.",
                    style = SecondaryTextStyle(),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (isSelf) {
            MorphIconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename this device", modifier = Modifier.size(ButtonIconSize))
            }
        } else {
            RemoveDeviceButton(device.name, "this device", onRemove)
        }
    }
}

@Composable
internal fun DeviceTag(text: String, color: Color) {
    RevealText(
        text,
        style = SecondaryTextStyle(),
        color = color,
        maxLines = 1,
        modifier = Modifier.padding(start = GapRow),
    )
}

/**
 * The remove button on a synced-device row. It only asks: the confirm dialog in DriveSyncUi says what removing does, so the
 * button itself is a plain action, not a tap-twice trap.
 */
@Composable
internal fun RemoveDeviceButton(name: String, fallbackNoun: String, onRemove: () -> Unit) {
    val who = name.ifBlank { fallbackNoun }
    MorphIconButton(onClick = onRemove) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = "Remove $who from synced devices",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ButtonIconSize),
        )
    }
}
