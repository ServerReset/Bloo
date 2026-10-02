package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SyncMerge
import com.bloo.uicommon.dropShadow
import com.bloo.uicommon.rememberConfirmArm

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
 * One phone in the drag-to-reorder devices list, in an outlined box of its own: drag handle, a
 * star for the primary, the name with "This device" / "Primary" tags, a health dot with the model
 * and last-seen line, and rename (this device) or tap-twice remove (any other).
 */
@Composable
internal fun SyncDeviceRow(
    device: SyncMerge.SyncDevice,
    isSelf: Boolean,
    isPrimary: Boolean,
    dragging: Boolean,
    modifier: Modifier,
    onRename: () -> Unit,
    /** Never offered for [isSelf]: removal is a courtesy prune of a stale peer, not a way to leave sync. */
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
                    Modifier.dropShadow(
                        shape,
                        color = Color.Black.copy(alpha = if (appIsDarkTheme()) 0.38f else 0.12f),
                        blurRadius = 14.dp,
                        offsetY = 4.dp,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(if (isPrimary) scheme.primaryContainer.copy(alpha = 0.40f) else Color.Transparent)
            .border(1.dp, if (isPrimary) scheme.primary.copy(alpha = 0.45f) else hairlineColor(), shape)
            .padding(horizontal = 10.dp, vertical = GapRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Drag to reorder",
            tint = scheme.onSurfaceVariant,
            modifier = modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            if (isPrimary) Icons.Filled.Star else Icons.Filled.Smartphone,
            contentDescription = if (isPrimary) "Primary device" else null,
            tint = if (isPrimary) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.name.ifBlank { "Unnamed device" },
                    style = MaterialTheme.typography.bodyLarge,
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
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                val seen = when {
                    isSelf || health == DeviceHealth.ACTIVE -> "Active now"
                    else -> com.bloo.bluelink.data.relativeLabel(device.lastSeenMs).ifBlank { "Never synced" }
                }
                val line = listOf(device.model.takeIf { it.isNotBlank() }, seen, device.appVersion.takeIf { it.isNotBlank() }?.let { "v$it" })
                    .filterNotNull().joinToString(" · ")
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (health == DeviceHealth.STALE) scheme.error else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (health == DeviceHealth.STALE) {
                Text(
                    "Hasn't synced in a while. It may be on a different Drive file.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (isSelf) {
            MorphIconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename this device", modifier = Modifier.size(18.dp))
            }
        } else {
            // Tap again to confirm, the app's usual destructive-action pattern (auto-resets).
            val confirmRemove = rememberConfirmArm()
            MorphIconButton(onClick = { if (confirmRemove.armed) onRemove() else confirmRemove.arm() }) {
                Icon(
                    AppIcons.Close,
                    contentDescription = if (confirmRemove.armed) {
                        "Tap again to remove ${device.name.ifBlank { "this device" }}"
                    } else {
                        "Remove ${device.name.ifBlank { "this device" }} from synced devices"
                    },
                    tint = if (confirmRemove.armed) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun DeviceTag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier.padding(start = 6.dp),
    )
}
