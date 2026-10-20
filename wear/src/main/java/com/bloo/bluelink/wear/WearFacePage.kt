package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.bigValue
import com.bloo.bluelink.data.isStale

/**
 * The car's face page: the charge ring and the four chosen controls ([WearCarFace]) with the phone's pull-down refresh over
 * it (pull from the top, a disc drops in and arms at a tick, release refreshes). Pressing the centre opens the face
 * editor. A control that opens the car is not sent from here: it goes to [onConfirm] so the page can ask first.
 */
@Composable
internal fun WearFacePage(
    ui: WearCarUi,
    slotKeys: List<String>,
    faceStatus: FaceStatus?,
    onConfirm: (String) -> Unit,
    /** A quarter that must be told which climate preset to use (none chosen, or the chosen one is gone): ask, for this quarter. */
    onChooseClimate: (slot: Int) -> Unit,
    onEditFace: () -> Unit,
) {
    val v = ui.car
    ScreenScaffold(timeText = { TimeText() }) {
        val pullState = remember { WearPullState() }
        Box(Modifier.fillMaxSize().wearPullToRefresh(pullState) { ui.repo.refresh() }) {
            val actions = slotKeys.mapIndexed { slot, key ->
                val control = WearFaceControls.byKey(key)?.takeIf { it.available(v) } ?: return@map null
                WearQuickAction(
                    control.label(v), control.icon(v),
                    active = control.active(v),
                    pending = ui.isPending(control.action),
                ) {
                    when {
                        control.needsChoice(v) -> onChooseClimate(slot)
                        control.confirm(v) -> onConfirm(control.action)
                        else -> ui.run(control)
                    }
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
                status = if (ui.refreshing && faceStatus == null) FaceStatus("Updating…") else faceStatus,
                refreshing = ui.refreshing,
                onHubClick = onEditFace,
            )
            WearPullIndicator(pullState, ui.refreshing)
        }
    }
}
