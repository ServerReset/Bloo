@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Represents a single remote action taken on a vehicle.
 * Used for displaying action history in RemoteActionsHistoryCard.
 *
 * @param id Unique identifier for this action
 * @param action Type of action ("Lock", "Unlock", "Climate", "Charge", etc.)
 * @param timestamp ISO 8601 formatted timestamp (UTC)
 * @param status Success/failure status ("Success", "Failed", "Pending")
 * @param details Optional additional details (temperature setting, charge level, etc.)
 */
data class RemoteAction(
    val id: String,
    val action: String,
    val timestamp: String,
    val status: String,
    val details: String? = null,
)

/**
 * Color for action status badge.
 * Maps status string to Material theme color for visual feedback.
 */
@Composable
private fun statusColor(status: String) = when (status.lowercase()) {
    "success" -> MaterialTheme.colorScheme.primary
    "failed" -> MaterialTheme.colorScheme.error
    "pending" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

/**
 * Compact badge showing action status.
 * Used in action history items for quick status recognition.
 */
@Composable
private fun StatusBadge(status: String) {
    Box(
        modifier = Modifier
            .background(
                color = statusColor(status).copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor(status),
            fontSize = 10.sp,
        )
    }
}

/**
 * Single row representing one remote action in history.
 * Shows action name, timestamp, status, and optional details.
 */
@Composable
private fun RemoteActionItem(action: RemoteAction, use24Hour: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Action name and timestamp
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.action,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = shortTime(action.timestamp, use24Hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Status badge
            StatusBadge(action.status)
        }

        // Optional details (e.g., "Set to 72°F", "16 kWh")
        if (action.details != null) {
            Text(
                text = action.details,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 10.sp,
            )
        }
    }
}

/**
 * The action's own recorded instant, as a plain wall-clock time ("14:32") -- or the date too if
 * it did not happen today. [RemoteAction.timestamp] is a raw `Instant.toString()`, which is
 * exactly what this used to render: "2026-08-31T21:33:15.123456Z" in an 11sp monospace label
 * under every row.
 */
private fun shortTime(iso: String, use24Hour: Boolean): String = runCatching {
    val at = java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
    val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    // Clock style follows the DEVICE's 12/24h setting rather than being hardcoded. This is the
    // app's only user-facing wall-clock string (the other patterns in the codebase are wire
    // formats and the debug log), so there was no house convention to inherit -- and a fixed
    // "21:33" is the wrong default for a US-market Hyundai/Genesis/Kia app whose units already
    // default to imperial.
    val clock = if (use24Hour) "HH:mm" else "h:mm a"
    val fmt = if (at.toLocalDate() == today) clock else "d MMM $clock"
    at.format(java.time.format.DateTimeFormatter.ofPattern(fmt))
}.getOrDefault(iso)

/**
 * Recent remote commands for one car, rendered inline -- no card of its own, no header, and no
 * disclosure control.
 *
 * This is deliberately not a card. It is revealed by pressing the lock pebble's own background
 * (see ControlsPebble), so it is already inside a pebble: a second card with its own chrome and
 * its own "▶" toggle -- which is what the previous RemoteActionsHistoryCard was -- would be a
 * card inside a card, disclosed twice. It also drops that card's nested LazyColumn, which was a
 * standing hazard: a lazy list inside another scrollable needs a height cap to avoid an infinite
 * -constraint crash, and a plain Column over a handful of already-capped rows cannot hit it at all.
 */
@Composable
internal fun RemoteActionsInline(actions: List<RemoteAction>, max: Int = 6) {
    if (actions.isEmpty()) return
    // Resolved once here, not per row: is24HourFormat reads a system setting, and every row in
    // the list would otherwise ask the same question again.
    val use24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        actions.take(max).forEach { RemoteActionItem(it, use24Hour) }
        if (actions.size > max) {
            Text(
                text = "+${actions.size - max} older",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 10.sp,
            )
        }
    }
}
