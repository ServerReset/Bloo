package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.UpdateApi
import com.bloo.bluelink.data.UpdateGate
import com.bloo.bluelink.data.WorkflowRun
import com.bloo.bluelink.data.installDownloadedApk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * The watch checks GitHub for its OWN updates, over its own internet connection.
 *
 * It has `INTERNET` and it is a standalone-ish device: making it depend on the phone just to
 * learn a new build exists was wrong, and would leave an unwatched/unpaired watch stranded.
 * So the watch runs the same check the phone does ([UpdateApi], shared) against the watch APK
 * asset. The phone's Data Layer push (see `WearDataLayerSync.updateAdvice`) remains a fast
 * PATH -- it can advertise an update the moment the phone sees one -- but it is no longer the
 * only source: this hits the network directly and works with no phone at all.
 *
 * Kept lean: one check on open, one download+install on tap.
 */
object WearUpdateChecker {

    private val _available = MutableStateFlow<WorkflowRun?>(null)
    /** The newer watch build, if any. */
    val available: StateFlow<WorkflowRun?> = _available

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * Check GitHub for a newer watch build. No-op when this is a dev build (run number 0, so
     * there is nothing to compare against). Best-effort: any failure just leaves [available]
     * as-is, so a flaky network never shows a spurious update or an error on open.
     */
    suspend fun check() {
        if (BuildConfig.BUILD_RUN_NUMBER <= 0) return
        val run = runCatching {
            UpdateApi.fetchLatestSuccessfulRun(UpdateApi.DEFAULT_BRANCH)
        }.getOrNull() ?: return
        _available.value = run.takeIf {
            it.watchApkUrl != null && UpdateGate.isNewer(it, BuildConfig.BUILD_RUN_NUMBER)
        }
    }

    /** Download the available watch APK and hand it to the system installer. */
    suspend fun downloadAndInstall(context: Context) {
        val run = _available.value ?: return
        val url = run.watchApkUrl ?: return
        if (_downloading.value) return
        _downloading.value = true
        _progress.value = 0f
        _error.value = null
        try {
            val dest = File(context.cacheDir, "bloo-watch.apk")
            val ok = withContext(Dispatchers.IO) {
                UpdateApi.downloadApk(url, dest) { _progress.value = it }
            }
            if (!ok) {
                _error.value = "Download failed"
                return
            }
            // installDownloadedApk returns false when it could only send the user to the
            // "install unknown apps" toggle (or the installer didn't launch at all) -- report
            // that instead of silently doing nothing, which is how a watch with the toggle off
            // saw "nothing happen" on tap.
            if (!installDownloadedApk(context, dest)) {
                _error.value = "Allow installing apps, then tap again"
            }
        } catch (t: Throwable) {
            _error.value = t.message ?: "Update failed"
        } finally {
            _downloading.value = false
        }
    }
}
