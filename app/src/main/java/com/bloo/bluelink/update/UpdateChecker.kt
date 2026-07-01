package com.bloo.bluelink.update

import android.content.Context
import com.bloo.bluelink.BuildConfig
import com.bloo.bluelink.data.Release
import com.bloo.bluelink.data.ReleaseAsset
import com.bloo.bluelink.data.UpdateApi
import com.bloo.bluelink.data.UpdateStore

/** A newer release than what's currently installed, resolved with the asset
 *  download URLs for both the phone and watch APKs. */
data class UpdateInfo(
    val release: Release,
    val phoneAsset: ReleaseAsset?,
    val wearAsset: ReleaseAsset?,
)

/**
 * Orchestrates the update-check flow: debounces GitHub calls (Bloo isn't on
 * the Play Store, so this is its own update channel — no point hammering the
 * API on every cold start), compares the release's versionCode (encoded in
 * its tag, e.g. "v7") against this build, and skips a release the user
 * already dismissed until a newer one ships.
 */
object UpdateChecker {

    private const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000L // 12h

    /** Update info for THIS app (phone), or null if none/not due/dismissed. */
    suspend fun checkPhone(context: Context, force: Boolean = false): UpdateInfo? {
        val store = UpdateStore(context)
        val now = System.currentTimeMillis()
        if (!force && now - store.lastCheckedAt() < CHECK_INTERVAL_MS) return null
        store.setLastCheckedAt(now)

        val release = UpdateApi.fetchLatestRelease() ?: return null
        val remoteCode = release.versionCode ?: return null
        if (remoteCode <= BuildConfig.VERSION_CODE) return null
        if (!force && store.dismissedVersionCode() == remoteCode) return null

        return UpdateInfo(
            release = release,
            phoneAsset = release.asset("Bloo.apk"),
            wearAsset = release.asset("Bloo-Wear.apk"),
        )
    }

    suspend fun dismiss(context: Context, versionCode: Int) {
        UpdateStore(context).setDismissedVersionCode(versionCode)
    }
}
