@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
 * One action, on one line: a status dot, the action's name, and when it happened.
 *
 * Deliberately flat -- no badge chrome, no card, no second type size for the timestamp. This
 * list is revealed INSIDE the lock pebble, so every box drawn here is a box inside a box, and
 * the earlier version (a filled status pill, a monospace time on its own line, and details in a
 * third) turned six entries into a wall three times taller than the pebble it hangs off. Status
 * is the one thing worth colour, so it is the dot and nothing else.
 */
@Composable
private fun RemoteActionItem(action: RemoteAction, use24Hour: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(statusColor(action.status), CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = action.action,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // Details ride on the SAME line, muted, and give up their space first -- a failure
        // reason is worth showing but never worth a row of its own here.
        if (action.details != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = action.details,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = shortTime(action.timestamp, use24Hour),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    // The panel owns ALL of its own insets, and they are the PEBBLE's insets, not arbitrary
    // ones. It is revealed inside the lock pebble's rounded Surface, directly beneath a row
    // whose content starts at 16dp, so anything narrower than that reads as misaligned -- and
    // the bottom needs real clearance or the last row runs into the corner radius, which is
    // exactly how it looked: text flush to the left edge and touching the bottom curve.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = PebbleContentInset, end = PebbleContentInset, bottom = 14.dp),
    ) {
        // A hairline between the controls and the history, so the revealed panel reads as a
        // second section of the same pebble rather than loose text under the buttons.
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (actions.isEmpty()) {
            // NOT nothing. This panel is revealed by pressing the pebble's background -- a
            // gesture with no chrome to announce it -- so drawing nothing for a car that has
            // not been commanded yet made a working gesture indistinguishable from a missing
            // one. The empty state is the only feedback that the press did something.
            Text(
                text = "No remote actions yet",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            return@Column
        }
        // Resolved once here, not per row: is24HourFormat reads a system setting, and every row
        // in the list would otherwise ask the same question again.
        val use24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
        actions.take(max).forEach { RemoteActionItem(it, use24Hour) }
        if (actions.size > max) {
            Text(
                text = "+${actions.size - max} more in the last $REMOTE_ACTION_HISTORY_DAYS days",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
