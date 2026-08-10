package com.bloo.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.common.util.concurrent.MoreExecutors

/**
 * Handoff to the paired phone: open a URL (map, service page) or the dialer on the phone via
 * [RemoteActivityHelper], with the failure surfaced as a watch notification.
 *
 * This object used to ALSO own the watch's self-update mechanics (currentBuildNumber,
 * fetchLatestBuild, isNewerBuild, installWearApk, UpdateInstallResult). All of that was dead:
 * WearViewModel does the self-update inline instead -- `currentBuildNumber` reads
 * BuildConfig.BUILD_RUN_NUMBER directly, and `downloadAndInstallUpdate` calls
 * `UpdateApi.downloadApk` and launches the installer itself. The WearRemote copies had no
 * callers, so they were removed along with their now-orphaned imports (FileProvider, UpdateApi,
 * WorkflowRun, File). If the self-update path is ever consolidated, do it in ONE place -- the
 * duplication was the bug, not the location.
 */
object WearRemote {

    /** Opens [url] on the paired phone (e.g. the car's location in Maps). */
    fun openOnPhone(context: Context, url: String) {
        startRemoteActivity(
            context = context,
            uri = Uri.parse(url),
            failId = ("open$url").hashCode(),
            failTitle = "Couldn't open on phone",
        )
    }

    /** Opens the phone dialer pre-filled with [number] (digits only). */
    fun dialOnPhone(context: Context, number: String) {
        startRemoteActivity(
            context = context,
            uri = Uri.parse("tel:$number"),
            failId = ("dial$number").hashCode(),
            failTitle = "Couldn't open dialer on phone",
        )
    }

    /**
     * Fires a browsable [Intent.ACTION_VIEW] for [uri] on the phone.
     *
     * `startRemoteActivity` resolves asynchronously; the future was previously
     * ignored, which made a tap silently do nothing when the phone was
     * unreachable. We listen for the failure and surface it as a watch
     * notification, the same way standalone command failures are reported.
     */
    private fun startRemoteActivity(
        context: Context,
        uri: Uri,
        failId: Int,
        failTitle: String,
    ) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(uri)
            val future = RemoteActivityHelper(context).startRemoteActivity(intent)
            future.addListener(
                {
                    runCatching { future.get() }.onFailure {
                        WearNotifications.post(
                            context,
                            failId,
                            failTitle,
                            "Bring your phone nearby and try again.",
                        )
                    }
                },
                MoreExecutors.directExecutor(),
            )
        }
    }
}
