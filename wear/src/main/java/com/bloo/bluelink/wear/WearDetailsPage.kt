package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.bloo.bluelink.data.downloadLabel
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ProgressBar
import androidx.compose.foundation.layout.PaddingValues
import com.bloo.bluelink.data.CarAction
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
 * One of a car's sections, a swipe left from the face. Each is a page of its own, so it has the whole round display, and every one
 * opens with a failed command's reason, the one thing the wearer came to read. A scrolling [WearListScreen].
 */
@Composable
internal fun WearCarSection(ui: WearCarUi, update: WearUpdateState, section: CarSection) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    WearListScreen(contentPadding = PaddingValues(top = 34.dp, bottom = 22.dp)) {
        // The car's name in the middle of its page, so a section always says which car it is.
        item { WearCenteredText(ui.car.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        ui.lastResult?.takeIf { it.vin == ui.car.vin && !it.ok }?.let { failure ->
            item {
                WearCenteredText(
                    failure.message ?: "Command failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        when (section) {
            // Smart climate and every preset from the phone are what the wearer swipes to first.
            CarSection.CLIMATE -> item { WearClimatePebble(ui.repo, ui.car, ui.pending, ui.send) }
            CarSection.STATUS -> item { WearStatusPebble(ui.car) }
            // The map is a page of its own (WearCarMapPage), so the list never draws it.
            CarSection.LOCATION -> Unit
            CarSection.CARE -> item {
                WearCarCarePebble(
                    ui.car,
                    refreshing = ui.refreshing,
                    canRefresh = ui.signedIn,
                    locating = ui.isPending(CarAction.LOCATE),
                    onRefresh = { ui.repo.refresh() },
                    onLocate = { ui.send { ui.repo.command(ui.car.vin, CarAction.LOCATE) } },
                )
            }
            CarSection.SETTINGS -> {
                update.error?.let { message ->
                    item { WearPebble(title = "Watch update", icon = AppIcons.SystemUpdate) { WearCaption(message) } }
                }
                if (update.run != null) {
                    item {
                        WearPebble(title = "Update available", icon = AppIcons.SystemUpdate) {
                            if (update.downloading) {
                                WearCenteredText(downloadLabel(update.progress), style = MaterialTheme.typography.bodyMedium)
                                ProgressBar(update.progress, Modifier.padding(horizontal = 12.dp))
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
    }
}

