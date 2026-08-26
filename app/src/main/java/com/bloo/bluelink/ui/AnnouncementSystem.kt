@file:OptIn(ExperimentalMaterial3Api::class)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.IconButton
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
                .background(
                    color = backgroundColor(announcement.severity),
                    shape = RoundedCornerShape(8.dp)
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
                    if (announcement.actionLabel != null) {
                        Text(
                            text = announcement.actionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { announcement.onAction?.invoke() },
                        )
                    }
                }

                if (announcement.dismissible) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
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
                shape = RoundedCornerShape(8.dp)
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

        if (announcement.actionLabel != null) {
            Text(
                text = announcement.actionLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { announcement.onAction?.invoke() },
            )
        }
    }
}

/**
 * Simple time formatter for display.
 * Shows "Today HH:MM", "Yesterday HH:MM", or date if older.
 */
private fun formatTime(timestamp: String): String {
    // This is a simplified version - in production, use proper date formatting
    return timestamp.substringAfter("T").substring(0, 5)  // HH:MM
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
        items(announcements) { announcement ->
            AnnouncementHistoryItem(announcement)
        }
    }
}
