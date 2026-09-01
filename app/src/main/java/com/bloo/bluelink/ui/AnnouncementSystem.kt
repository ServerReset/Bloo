@file:OptIn(ExperimentalMaterial3Api::class)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Severity level for announcements.
 * Determines colors, icons, and user prominence.
 */
enum class AnnouncementSeverity {
    /**
     * Informational: app updates, new features, general news.
     * Blue/info colored, subtle prominence.
     */
    INFO,

    /**
     * Warning: optional updates, deprecated features, non-critical issues.
     * Yellow/warning colored, moderate prominence.
     */
    WARNING,

    /**
     * Critical: security updates, critical failures, important actions needed.
     * Red/error colored, high prominence.
     */
    CRITICAL,
}

/**
 * Represents a single announcement/notification.
 * Can be displayed as a toast or in announcement history.
 *
 * @param id Unique identifier for this announcement
 * @param title Short title/headline for the announcement
 * @param message Detailed message body
 * @param severity Importance level (Info/Warning/Critical)
 * @param timestamp When the announcement was created (ISO 8601)
 * @param actionLabel Optional text for call-to-action button ("Update", "Learn more", etc.)
 * @param onAction Optional callback when user taps the action button
 * @param dismissible Whether user can dismiss this announcement
 */
data class Announcement(
    val id: String,
    val title: String,
    val message: String,
    val severity: AnnouncementSeverity = AnnouncementSeverity.INFO,
    val timestamp: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val dismissible: Boolean = true,
)

/**
 * Get icon for announcement based on severity.
 */
@Composable
private fun getAnnouncementIcon(severity: AnnouncementSeverity): ImageVector {
    return when (severity) {
        AnnouncementSeverity.INFO -> Icons.Filled.Info
        AnnouncementSeverity.WARNING -> Icons.Filled.Warning
        AnnouncementSeverity.CRITICAL -> Icons.Filled.ErrorOutline
    }
}

/**
 * Get background color for announcement badge/card.
 */
@Composable
private fun backgroundColor(severity: AnnouncementSeverity): androidx.compose.ui.graphics.Color {
    return when (severity) {
        AnnouncementSeverity.INFO -> MaterialTheme.colorScheme.primaryContainer
        AnnouncementSeverity.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        AnnouncementSeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
    }
}

/**
 * Get foreground color for announcement text.
 */
@Composable
private fun foregroundColor(severity: AnnouncementSeverity): androidx.compose.ui.graphics.Color {
    return when (severity) {
        AnnouncementSeverity.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        AnnouncementSeverity.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        AnnouncementSeverity.CRITICAL -> MaterialTheme.colorScheme.onErrorContainer
    }
}

/**
 * Toast-style notification that slides up from bottom and auto-dismisses.
 * Used for transient announcements and user feedback.
 *
 * **Usage:**
 * ```kotlin
 * AnnouncementToast(
 *     announcement = Announcement(
 *         id = "update-1",
 *         title = "New Features",
 *         message = "Climate presets are now available",
 *         severity = AnnouncementSeverity.INFO,
 *         timestamp = "2026-08-26T12:00:00Z"
 *     ),
 *     onDismiss = { showToast = false }
 * )
 * ```
 *
 * @param announcement The announcement to display
 * @param visible Whether the toast is visible
 * @param onDismiss Callback when user dismisses the toast
 */
@Composable
fun AnnouncementToast(
    announcement: Announcement,
    visible: Boolean = true,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                // PebbleCornerExpanded, not a one-off 8dp: this banner floats over the same
                // screens as the pebbles and was the only surface in the app still drawing a
                // near-square corner.
                .background(
                    color = backgroundColor(announcement.severity),
                    shape = RoundedCornerShape(PebbleCornerExpanded),
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = getAnnouncementIcon(announcement.severity),
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp),
                    tint = foregroundColor(announcement.severity),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = foregroundColor(announcement.severity),
                    )
                    Text(
                        text = announcement.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = foregroundColor(announcement.severity),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // A real button, not a clickable Text. This was a bare label with no
                    // container, no press feedback and a ~16dp tall touch target, sitting on
                    // the one surface most likely to be tapped in a hurry.
                    if (announcement.actionLabel != null) {
                        MorphTextButton(
                            text = announcement.actionLabel,
                            onClick = { announcement.onAction?.invoke() },
                            modifier = Modifier.padding(top = 8.dp),
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (announcement.dismissible) {
                    // MorphIconButton: the app's standard icon target (press spring, haptic,
                    // 48dp frame) rather than a stock IconButton, and the glyph sized from the
                    // shared token instead of Material's own default.
                    MorphIconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(ButtonIconOnlySize),
                            tint = foregroundColor(announcement.severity),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single announcement item shown in history list.
 * Shows full details with timestamp and optional action.
 */
@Composable
private fun AnnouncementHistoryItem(announcement: Announcement) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor(announcement.severity).copy(alpha = 0.5f),
                shape = RoundedCornerShape(PebbleCornerExpanded),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = getAnnouncementIcon(announcement.severity),
                    contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp),
                    tint = foregroundColor(announcement.severity),
                )

                Column {
                    Text(
                        text = announcement.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = foregroundColor(announcement.severity),
                    )
                    Text(
                        text = announcement.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = foregroundColor(announcement.severity),
                    )
                }
            }

            Text(
                text = formatTime(announcement.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp,
            )
        }

        // The history list's copy of the same action -- a real button here too, for the same
        // reason as the banner's above.
        if (announcement.actionLabel != null) {
            MorphTextButton(
                text = announcement.actionLabel,
                onClick = { announcement.onAction?.invoke() },
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Simple time formatter for display.
 * Shows "HH:MM" from ISO 8601 timestamp, or fallback if malformed.
 * Defensive against missing "T" or truncated timestamps.
 */
private fun formatTime(timestamp: String): String {
    return try {
        val afterT = timestamp.substringAfter("T")
        if (afterT.isEmpty()) {
            timestamp.take(10)  // Fallback to YYYY-MM-DD if no time part
        } else {
            afterT.take(5)  // HH:MM (substring safe)
        }
    } catch (e: Exception) {
        timestamp.take(10)  // Final fallback to date portion
    }
}

/**
 * History view showing past announcements.
 * Useful for users to review missed alerts, updates, or important info.
 *
 * **Usage:**
 * ```kotlin
 * AnnouncementHistory(
 *     announcements = vm.getAnnouncements(),
 * )
 * ```
 *
 * @param announcements List of announcements to display (newest first)
 * @param modifier Optional modifier for the container
 */
@Composable
fun AnnouncementHistory(
    announcements: List<Announcement>,
    modifier: Modifier = Modifier,
) {
    if (announcements.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No announcements yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        items(announcements, key = { it.id }) { announcement ->
            AnnouncementHistoryItem(announcement)
        }
    }
}
