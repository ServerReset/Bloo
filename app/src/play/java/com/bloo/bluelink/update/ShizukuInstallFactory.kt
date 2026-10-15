package com.bloo.bluelink.update

import java.io.File

/**
 * The Play build has no Shizuku at all -- no dependency, no provider, no permission -- and wants none:
 * a Play copy cannot install its own updates, and gets them from Play instead. Reporting "unavailable"
 * is the honest answer, and it is what the UI already acts on.
 */
internal fun shizukuInstall(): ShizukuInstall = object : ShizukuInstall {
    override fun isAvailable() = false
    override fun hasPermission() = false
    override fun requestPermission(requestCode: Int) = Unit
    override fun requestPermissionOnEnable(requestCode: Int) = false
    override fun installApk(apk: File, callerPackageName: String): Result<Unit> =
        Result.failure(IllegalStateException("Updates come from Google Play."))
    override fun registerPermissionResultListener(onResult: (Int, Int) -> Unit) = Unit
    override fun unregisterPermissionResultListener() = Unit
}
