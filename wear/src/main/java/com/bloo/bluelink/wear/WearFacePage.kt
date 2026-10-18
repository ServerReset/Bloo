package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.isStale

/**
 * The car's face page: the charge ring and the four chosen controls ([WearCarFace]), with the phone's pull-down
 * refresh over it. Pulling down from the top drops a disc in, arms at a point (a tick), and refreshes on release.
 *
 * A control that opens the car is not sent from here: it goes to [onConfirm] so the caller can ask first.
 */
@Composable
internal fun WearFacePage(
    repo: WearSnapshotRepository,
    v: VehicleSnapshot,
    slotKeys: List<String>,
    pendingCommands: Set<String>,
    faceStatus: FaceStatus?,
    refreshing: Boolean,
    onConfirm: (String) -> Unit,
    requestCommand: ((() -> Unit)) -> Unit,
) {
    ScreenScaffold(timeText = { TimeText() }) {
        val pullState = remember { WearPullState() }
        Box(Modifier.fillMaxSize().wearPullToRefresh(pullState) { repo.refresh() }) {
            val actions: List<WearQuickAction?> = slotKeys.map { key ->
                val control = WearFaceControls.byKey(key)?.takeIf { it.available(v) } ?: return@map null
                WearQuickAction(
                    control.label(v), control.icon(v),
                    active = control.active(v),
                    pending = WearCommands.key(v.vin, control.action) in pendingCommands,
                ) {
                    if (control.confirm(v)) onConfirm(control.action)
                    else requestCommand { repo.command(v.vin, control.action) }
                }
            }
            WearCarFace(
                modifier = Modifier.fillMaxSize(),
                value = v.bigValue(),
                caption = v.name,
                progress = v.percent?.let { it / 100f },
                tint = faceTint(v.percent, v.charging == true, MaterialTheme.colorScheme.onBackground),
                actions = actions,
                charging = v.charging == true,
                stale = v.isStale(),
                status = if (refreshing && faceStatus == null) FaceStatus("Updating…") else faceStatus,
                refreshing = refreshing,
            )
            WearPullIndicator(pullState, refreshing)
        }
    }
}
