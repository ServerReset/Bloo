package com.bloo.bluelink.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.bloo.bluelink.data.downloadLabel
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
import kotlin.math.PI
import kotlin.math.sin
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
        when (section) {
            // Smart climate and every preset from the phone are what the wearer swipes to first.
            CarSection.CLIMATE -> item { WearClimatePebble(ui.repo, ui.car, ui.pending, ui.send) }
            CarSection.STATUS -> item { WearStatusPebble(ui.car) }
            CarSection.CARE -> item {
                WearCarCarePebble(ui.car, refreshing = ui.refreshing, canRefresh = ui.signedIn, onRefresh = { ui.repo.refresh() })
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
                                WearWavyProgress(update.progress, Modifier.padding(horizontal = 12.dp))
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

/**
 * The update's progress as the phone's wavy bar: a sine stroke that fills in from the left over a flat track. Drawn here rather than with
 * the Material 3 indicator, because the watch stays on the Compose version pinned in its build.
 */
@Composable
private fun WearWavyProgress(progress: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val stroke = size.height * 0.5f
        val amplitude = size.height * 0.22f
        val wavelength = size.height * 2.4f
        val mid = size.height / 2f
        val end = size.width * progress.coerceIn(0f, 1f)
        drawLine(track, Offset(0f, mid), Offset(size.width, mid), strokeWidth = stroke, cap = StrokeCap.Round)
        if (end > 0f) {
            val wave = Path().apply {
                moveTo(0f, mid)
                var x = 0f
                while (x < end) {
                    x = minOf(x + 2f, end)
                    lineTo(x, mid + amplitude * sin(2f * PI.toFloat() * x / wavelength))
                }
            }
            drawPath(wave, fill, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    }
}
