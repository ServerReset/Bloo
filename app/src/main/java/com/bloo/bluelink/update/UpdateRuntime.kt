package com.bloo.bluelink.update

import android.content.Context
import android.os.Build
import com.bloo.bluelink.BuildConfig

/**
 * How THIS copy of the app gets its updates -- decided from how it was INSTALLED, not from how it
 * was built.
 *
 * One codebase ships as two artifacts (see the `distribution` flavors): the Play build, and the build
 * users install from the rolling GitHub Release. But the artifact is not the whole story, which is
 * why this exists at all:
 *
 *  - Play will happily UPDATE an app that was sideloaded, as long as the package name and the signing
 *    key match -- and here they do, deliberately, so a user can move between the two builds without
 *    uninstalling. A copy installed from GitHub can therefore BECOME a Play-installed copy while
 *    still running the sideload build's code, which knows how to update itself. Letting that happen
 *    is precisely what Play forbids, so the decision has to be made at RUNTIME, from the installer.
 *
 *  - The reverse is just as wrong: the Play artifact sideloaded onto a device should not sit there
 *    unable to update.
 *
 * So [fromPlayStore] decides WHICH update path runs, and [canSelfInstall] decides what this
 * particular BUILD is able to do about it.
 */
internal object UpdateRuntime {
    /** The Play Store's own package: what the package manager reports as the installer for a Play install. */
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    @Volatile
    private var fromPlay: Boolean? = null

    /**
     * Whether Google Play installed this copy. Read once from the package manager and cached -- it
     * cannot change while the process lives, because installing replaces the process.
     */
    fun fromPlayStore(context: Context): Boolean = fromPlay ?: synchronized(this) {
        fromPlay ?: detect(context).also { fromPlay = it }
    }

    private fun detect(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        installer == PLAY_STORE_PACKAGE
    }.getOrDefault(false)

    /**
     * Whether this BUILD carries its own installer. False for the Play artifact, which by design has
     * no REQUEST_INSTALL_PACKAGES and no installer code to reach it with -- see
     * src/sideload/AndroidManifest.xml.
     */
    val canSelfInstall: Boolean get() = !BuildConfig.PLAY_DISTRIBUTION
}
