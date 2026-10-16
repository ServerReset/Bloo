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

    /**
     * Bloo's own Play Store page.
     *
     * The PUBLIC listing, not the `/apps/testing/` opt-in page. It resolves for anyone who can see the
     * app -- which is testers, who are the only people running this build -- and it is the page that
     * keeps working unchanged when the app goes to production. The opt-in page is a step in joining,
     * not a destination to send someone who has already joined.
     */
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.bloo.bluelink"

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

    /**
     * THE decision every update surface asks: does this copy get its updates from the GitHub release?
     *
     * Both halves are needed, which is why this exists rather than each caller picking one:
     *
     *  - Play installed it ([fromPlayStore]) -> Play owns the update, whatever the artifact can do. Play
     *    will happily update a copy that was sideloaded first, so the artifact alone is not the answer.
     *  - The artifact has an installer ([canSelfInstall]) -> a Play copy sideloaded onto a device must
     *    not sit there unable to update either.
     *
     * Every place that offers, hides or refuses a CI build asks THIS. The alternative -- a runtime
     * check here, a compile-time flavour check there -- is how the crash screen and the watch both ended
     * up offering GitHub downloads to Play copies after the phone app had been fixed.
     */
    fun usesGitHub(context: Context): Boolean = canSelfInstall && !fromPlayStore(context)

}
