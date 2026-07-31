package com.bloo.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.bloo.bluelink.data.UpdateApi
import com.bloo.bluelink.data.WorkflowRun
import com.google.common.util.concurrent.MoreExecutors
import java.io.File

/**
 * The watch's remote + self-update mechanism, entirely phone-independent where
 * it can be.
 *
 * Two responsibilities, both pure "do the thing" mechanics with no UI state of
 * their own (the [WearViewModel] owns `updateRun`/`updateDownloading`, snooze
 * persistence and the check-interval debounce, and calls into here):
 *
 *  1. **Handoff to the paired phone** — open a URL (map, service page) or the
 *     dialer on the phone via [RemoteActivityHelper].
 *  2. **App self-update** — Bloo isn't on the Play Store, so the real update
 *     channel is the GitHub Releases feed read by the frozen `:shared`
 *     [UpdateApi]. This watch checks that feed against its OWN CI build number
 *     ([currentBuildNumber] = `BuildConfig.BUILD_RUN_NUMBER`), downloads
 *     `Bloo-Wear.apk`, and hands it to the system package installer via a
 *     `FileProvider` content URI — no phone required at all. The install prompt
 *     is gated by the `REQUEST_INSTALL_PACKAGES` permission declared in the
 *     manifest; the system installer surfaces its own consent UI.
 */
object WearRemote {

    // --- Handoff to the paired phone ----------------------------------------

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

    // --- App self-update: build identity ------------------------------------

    /**
     * The GitHub Actions run number this watch APK was compiled from (baked in
     * at CI build time). `0` means a local/dev build — nothing to compare
     * against, so update checks no-op.
     */
    val currentBuildNumber: Int
        get() = BuildConfig.BUILD_RUN_NUMBER

    // --- App self-update: check ---------------------------------------------

    /**
     * Fetches the latest published Bloo-Wear build from the GitHub Releases
     * channel (via the frozen `:shared` [UpdateApi]), resolving the branch the
     * same way the phone does: the CI-baked `BUILD_BRANCH`, falling back to
     * [UpdateApi.DEFAULT_BRANCH] when blank (e.g. a locally built APK).
     *
     * Returns `null` for a dev build ([currentBuildNumber] <= 0 — nothing to
     * compare) or on any network/parse failure. This does NOT compare versions
     * or persist any debounce/snooze state — that stays with the caller, which
     * owns the persistent store and the surfaced-update UI flag. Use
     * [isNewerBuild] to decide whether the returned run is worth surfacing.
     */
    suspend fun fetchLatestBuild(): WorkflowRun? {
        if (currentBuildNumber <= 0) return null
        val branch = BuildConfig.BUILD_BRANCH.ifBlank { UpdateApi.DEFAULT_BRANCH }
        return runCatching { UpdateApi.fetchLatestSuccessfulRun(branch) }.getOrNull()
    }

    /**
     * True when [run] is strictly newer than this watch's own build. Guards
     * against a dev build ([currentBuildNumber] <= 0) so a standalone call
     * never reports an update against a build number of `0`.
     */
    fun isNewerBuild(run: WorkflowRun): Boolean =
        currentBuildNumber > 0 && run.runNumber > currentBuildNumber

    // --- App self-update: download + install --------------------------------

    /** Outcome of [installWearApk], so the caller can surface the right message
     *  without this helper owning any UI strings. */
    enum class UpdateInstallResult {
        /** APK fully downloaded and the system installer was launched. */
        DOWNLOADED_AND_LAUNCHED,

        /** The download never completed (connection dropped, bad response, …). */
        DOWNLOAD_FAILED,

        /** Download succeeded but the installer intent couldn't be launched. */
        INSTALLER_FAILED,
    }

    /**
     * Downloads `Bloo-Wear.apk` from [url] and hands it straight to the system
     * package installer, entirely on-device.
     *
     * The APK is streamed to `<cacheDir>/apk/Bloo-Wear.apk` (see
     * [UpdateApi.downloadApk], which writes to a temp file first so a
     * failed/cancelled download can't leave a truncated, uninstallable file
     * behind). On success we build a `content://` URI through the manifest's
     * `${applicationId}.fileprovider` authority and fire an
     * [Intent.ACTION_VIEW] with the `application/vnd.android.package-archive`
     * MIME type — the standard sideload path — granting the installer read
     * access to the URI. Installing an APK requires the `REQUEST_INSTALL_PACKAGES`
     * permission (declared in the manifest); the OS installer shows its own
     * consent UI, so no explicit runtime check is needed here.
     *
     * [onProgress] receives 0f..1f as bytes land (left at 0 if the server
     * doesn't report a Content-Length); it defaults to a no-op for callers that
     * only show a busy spinner.
     *
     * Callers should handle the "no watch asset on this release" case
     * themselves by opening the release page on the phone ([openOnPhone] with
     * [WorkflowRun.htmlUrl]) — that path deliberately never flips the download
     * spinner, so it's kept out of this function.
     */
    suspend fun installWearApk(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit = {},
    ): UpdateInstallResult {
        val dest = File(File(context.cacheDir, "apk"), "Bloo-Wear.apk")
        val downloaded = UpdateApi.downloadApk(url, dest, onProgress)
        if (!downloaded) return UpdateInstallResult.DOWNLOAD_FAILED
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                dest,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK,
                )
            }
            context.startActivity(intent)
            UpdateInstallResult.DOWNLOADED_AND_LAUNCHED
        }.getOrDefault(UpdateInstallResult.INSTALLER_FAILED)
    }
}
