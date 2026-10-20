package com.bloo.bluelink.wear

import com.bloo.uicommon.AppIcons
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.WorkflowRun
import kotlinx.coroutines.launch

/** The watch's own update, as the details page shows it: one thing, so the page does not juggle four flows. */
internal class WearUpdateState(
    /** A newer watch build (from the watch's own check, or advertised by the phone), or null. */
    val run: WorkflowRun?,
    /** Why the last check or download failed, so the page can say instead of staying silent. */
    val error: String?,
    val downloading: Boolean,
    val progress: Float,
)

@Composable
internal fun rememberWearUpdateState(): WearUpdateState {
    val advice by WearDataLayerSync.updateAdvice.collectAsStateWithLifecycle(initialValue = null)
    val own by WearUpdateChecker.available.collectAsStateWithLifecycle(initialValue = null)
    val error by WearUpdateChecker.error.collectAsStateWithLifecycle(initialValue = null)
    val downloading by WearUpdateChecker.downloading.collectAsStateWithLifecycle(initialValue = false)
    val progress by WearUpdateChecker.progress.collectAsStateWithLifecycle(initialValue = 0f)
    val run = own ?: advice?.let { WorkflowRun(it.runNumber, htmlUrl = "", watchApkUrl = it.apkUrl) }
    return WearUpdateState(run, error, downloading, progress)
}

/**
 * The car's details page, one swipe up from the face: a failed command's reason, its readings, its climate, its care and
 * freshness, any watch update, then the watch's own settings. A scrolling [WearListScreen].
 */
@Composable
internal fun WearDetailsPage(ui: WearCarUi, update: WearUpdateState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    WearListScreen(contentPadding = PaddingValues(top = 34.dp, bottom = 22.dp)) {
        // A failed command's reason first: it is what the wearer came here to read, not something to scroll down to find.
        ui.lastResult?.takeIf { it.vin == ui.car.vin && !it.ok }?.let { failure ->
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
        // The climate first: smart climate and every preset from the phone are what the wearer swipes up for.
        item { WearClimatePebble(ui.repo, ui.car, ui.pending, ui.send) }
        item { WearStatusPebble(ui.car) }
        item { WearCarCarePebble(ui.car, refreshing = ui.refreshing, canRefresh = ui.signedIn, onRefresh = { ui.repo.refresh() }) }
        update.error?.let { message ->
            item { WearPebble(title = "Watch update", icon = AppIcons.SystemUpdate) { WearCaption(message) } }
        }
        if (update.run != null) {
            item {
                WearPebble(title = "Update available", icon = AppIcons.SystemUpdate) {
                    if (update.downloading) {
                        WearCenteredText("Downloading… ${(update.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        WearActionRow("Update now", AppIcons.Download) {
                            scope.launch { WearUpdateChecker.downloadAndInstall(context) }
                        }
                    }
                }
            }
        }
        // The watch's own settings, together in one pebble (the face's controls are changed from the face's centre).
        item { WearSettingsPebble(ui.signedIn) }
    }
}
