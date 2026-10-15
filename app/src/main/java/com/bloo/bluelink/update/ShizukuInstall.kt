package com.bloo.bluelink.update

import java.io.File

/**
 * Seamless update installs through Shizuku, on a purely local ADB connection.
 *
 * An interface, with the implementation in the `sideload` source set, for the same reason the watch
 * installer has one: this is the only thing in the app that needs the Shizuku stack, and that stack
 * brings a manifest permission, a provider and a dependency with it. GitHub installs keep all of it.
 * The Play build has none of it -- and does not want it, because a Play copy cannot self-install at all
 * (`UpdateRuntime.canSelfInstall` is false there) and gets its updates from Play.
 *
 * Nothing in the UI needs to know which build it is: the Play implementation simply reports that
 * Shizuku is unavailable, and every Shizuku-facing row is already gated on that.
 */
internal interface ShizukuInstall {
    fun isAvailable(): Boolean
    fun hasPermission(): Boolean
    fun requestPermission(requestCode: Int)
    /** Whether a request was actually sent; false when Shizuku is not there to ask. */
    fun requestPermissionOnEnable(requestCode: Int): Boolean
    fun installApk(apk: File, callerPackageName: String): Result<Unit>
    /** Wraps Shizuku's own parcelable listener so the Activity never imports the library. */
    fun registerPermissionResultListener(onResult: (requestCode: Int, grantResult: Int) -> Unit)
    fun unregisterPermissionResultListener()
}
