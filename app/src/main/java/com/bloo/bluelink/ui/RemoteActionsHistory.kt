
package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Represents a single remote action taken on a vehicle. Used for displaying action history in
 * RemoteActionsHistoryCard.
 */
data class RemoteAction(
    val id: String,
    val action: String,
    val timestamp: String,
    val status: String,
    val details: String? = null,
)

/** Color for an action status badge, mapped from the status string. */
@Composable
private fun statusColor(status: String) = when (status.lowercase()) {
    "success" -> MaterialTheme.colorScheme.primary
    "failed" -> MaterialTheme.colorScheme.error
    "pending" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

/**
 * One action on one line: a status dot, the name, and the time. Deliberately flat: this list sits
 * inside the lock pebble, so status is the only thing worth colour.
 */
@Composable
private fun RemoteActionItem(action: RemoteAction, use24Hour: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = GapHairline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(statusColor(action.status), CircleShape),
        )
        Spacer(Modifier.width(GapRow))
        Text(
            text = action.action,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // Details share the line, muted, and give up space first.
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
        Spacer(Modifier.width(GapRow))
        Text(
            text = shortTime(action.timestamp, use24Hour),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The action's recorded instant as a wall-clock time ("14:32"), with the date too if not today. */
private fun shortTime(iso: String, use24Hour: Boolean): String = runCatching {
    val at = java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
    val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault())
    // Clock style follows the device's 12/24h setting.
    val clock = if (use24Hour) "HH:mm" else "h:mm a"
    val fmt = if (at.toLocalDate() == today) clock else "d MMM $clock"
    // Not hoisted to file scope: ofPattern() captures Locale.getDefault() at construction, so a
    // file-scope formatter would keep a stale locale after a device language change.
    at.format(java.time.format.DateTimeFormatter.ofPattern(fmt))
}.getOrDefault(iso)

/**
 * Recent remote commands for one car, rendered inline with no card, header or disclosure control
 * (it is revealed inside the lock pebble). A plain Column avoids a nested lazy list's
 * infinite-constraint crash.
 */
@Composable
internal fun RemoteActionsInline(actions: List<RemoteAction>, max: Int = 6) {
    // The panel owns its insets: the pebble's content inset on the sides and real bottom clearance
    // for the corner radius.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = PebbleContentInset, end = PebbleContentInset, bottom = 16.dp),
    ) {
        // A hairline separating the controls from the history.
        SectionDivider(alpha = 0.35f, modifier = Modifier.padding(bottom = 6.dp))
        if (actions.isEmpty()) {
            // Not nothing: the press that reveals this panel has no chrome, so the empty state is
            // its only feedback.
            Text(
                text = "No remote actions yet",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = GapHairline),
            )
            return@Column
        }
        // Resolved once, not per row: is24HourFormat reads a system setting.
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
