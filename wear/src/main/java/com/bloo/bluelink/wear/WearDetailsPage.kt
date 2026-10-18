package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WatchCommandResult
import com.bloo.bluelink.data.WorkflowRun
import kotlinx.coroutines.launch

/**
 * The car's details page, one swipe up from the face: its readings, any watch update, the face's control
 * choices, the watch's alerts and Auto lock, and a command's failure line. A scrolling [WearListScreen].
 */
@Composable
internal fun WearDetailsPage(
    repo: WearSnapshotRepository,
    v: VehicleSnapshot,
    slotKeys: List<String>,
    refreshing: Boolean,
    signedIn: Boolean,
    updateError: String?,
    updateRun: WorkflowRun?,
    downloading: Boolean,
    progress: Float,
    lastResult: WatchCommandResult?,
    onChooseSlot: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    WearListScreen(
        contentPadding = PaddingValues(vertical = 18.dp),
        // Refresh is the page's one primary action, so it is Wear OS's own edge button at the bottom.
        edgeButton = if (signedIn) {
            {
                EdgeButton(onClick = { repo.refresh() }) {
                    Text(if (refreshing) "Refreshing…" else "Refresh")
                }
            }
        } else null,
    ) {
        item { WearStatusPebble(v, refreshing = refreshing, canRefresh = false, onRefresh = {}) }
        // The update error, shown where the update lives. It used to go nowhere at all.
        updateError?.let { message ->
            item {
                WearPebble(title = "Watch update", icon = Icons.Filled.SystemUpdate) {
                    WearCenteredText(message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        updateRun?.let {
            item {
                WearPebble(title = "Update available", icon = Icons.Filled.SystemUpdate) {
                    if (downloading) {
                        WearCenteredText("Downloading… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        WearActionRow(label = "Update now", icon = Icons.Filled.Download) {
                            scope.launch { WearUpdateChecker.downloadAndInstall(context) }
                        }
                    }
                }
            }
        }
        item { WearFaceControlsPebble(slotKeys, onChooseSlot) }
        item { WearAlertsPebble() }
        // Auto lock needs the car's credentials on the watch, which only the pairing step in Settings on the
        // phone hands over; until then the pebble is a pointer to that step, not a switch.
        item { if (signedIn) WearAutoLockPebble() else WearPairPebble() }
        lastResult?.takeIf { it.vin == v.vin && !it.ok }?.let { failure ->
            item {
                WearCenteredText(
                    failure.message ?: "Command failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                )
            }
        }
    }
}
