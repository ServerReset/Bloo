
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
        // Details ride on the SAME line, muted, and give up their space first -- a failure reason
        // is worth showing but never worth a row of its own here.
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
    // NOT hoisted to four file-scope DateTimeFormatters the way SettingsIndex.kt's Rx* patterns
    // are, even though ofPattern() does parse its pattern on every construction. ofPattern() with
    // no explicit Locale captures Locale.getDefault() AT CONSTRUCTION, so a file-scope formatter
    // would freeze this row's month name and AM/PM marker to whatever locale the process started in
    // -- a device language change recreates activities but not the process, so the stale text would
    // survive until the app was killed.
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
            // NOT nothing. This panel is revealed by pressing the pebble's background -- a gesture
            // with no chrome to announce it -- so drawing nothing for a car that has not been
            // commanded yet made a working gesture indistinguishable from a missing one. The empty
            // state is the only feedback that the press did something.
            Text(
                text = "No remote actions yet",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = GapHairline),
            )
            return@Column
        }
        // Resolved once here, not per row: is24HourFormat reads a system setting, and every row in
        // the list would otherwise ask the same question again.
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
