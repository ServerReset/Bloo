@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
private fun RemoteActionItem(action: RemoteAction) {
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
                    text = action.timestamp,
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
 * Expandable card showing remote actions history for a vehicle.
 * Appears as a collapsible section in the vehicle detail view.
 * Helps users see what commands were sent and their status for debugging.
 *
 * **Usage:**
 * ```kotlin
 * RemoteActionsHistoryCard(
 *     vehicleName = "2024 Ioniq 5",
 *     actions = vm.getActionsFor(vehicleId),
 * )
 * ```
 *
 * @param vehicleName Name of the vehicle for the header
 * @param actions List of RemoteAction objects to display (newest first)
 * @param maxItems Maximum number of actions to show (rest are cut off)
 */
@Composable
fun RemoteActionsHistoryCard(
    vehicleName: String,
    actions: List<RemoteAction>,
    maxItems: Int = 10,
) {
    val isExpanded = remember { mutableStateOf(false) }
    val displayedActions = actions.take(maxItems)

    // Don't show card if no actions
    if (actions.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(0.dp) // Padding added inside header and content
    ) {
        // Header - collapsible toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded.value = !isExpanded.value }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = History,
                    contentDescription = "Actions History",
                    modifier = Modifier.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    Text(
                        text = "Remote Actions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${actions.size} action${if (actions.size != 1) "s" else ""} (showing ${displayedActions.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }

            // Expand/collapse indicator
            Text(
                text = if (isExpanded.value) "▼" else "▶",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Expandable content - list of actions
        AnimatedVisibility(
            visible = isExpanded.value,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                    )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(displayedActions) { action ->
                        RemoteActionItem(action)
                    }

                    // Show truncation message if needed
                    if (actions.size > maxItems) {
                        item {
                            Text(
                                text = "+${actions.size - maxItems} more actions (scroll in full view)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
