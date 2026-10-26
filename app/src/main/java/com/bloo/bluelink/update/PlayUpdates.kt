package com.bloo.bluelink.update

import android.app.Activity
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Google Play's own In-App Updates flow, for a copy that Play installed.
 *
 * Play owns the download and the install, so there is nothing here to show and nothing to install:
 * the app's own update tile and install guide stand down (see AppViewModel.checkForUpdate) and Play's
 * UI does the work. The two permissions the sideload path needs -- REQUEST_INSTALL_PACKAGES and
 * Shizuku's -- are absent from the Play artifact by construction.
 */
internal object PlayUpdates {
    /** Only ever used to tell Play's own flow apart from another caller's; never surfaced. */
    private const val REQUEST_CODE = 0x8170

    private var manager: AppUpdateManager? = null
    private var listening = false

    /**
     * One prompt per process. Play's own state already stops a flow being started twice (it reports
     * IN_PROGRESS), but nothing stops a user who DECLINED from being asked again on every resume --
     * and that is nagging.
     */
    private var prompted = false

    /** Called from `MainActivity.onStart`, and only for a Play-installed copy. */
    fun onForeground(activity: Activity) {
        val manager = manager ?: runCatching {
            AppUpdateManagerFactory.create(activity.applicationContext)
        }.getOrNull()?.also { manager = it } ?: return

        if (!listening) {
            listening = true
            // A flexible update downloads in the background and then waits for the app to say it may
            // restart. If it finished while the app was away, finish it now; from there Play's own UI
            // takes over. An explicit object, not a SAM lambda: InstallStateUpdatedListener inherits
            // its single method from a generic super-interface, which Kotlin will not SAM-convert.
            val onState = object : InstallStateUpdatedListener {
                override fun onStateUpdate(state: InstallState) {
                    if (state.installStatus() == InstallStatus.DOWNLOADED) {
                        runCatching { manager.completeUpdate() }
                    }
                }
            }
            runCatching { manager.registerListener(onState) }
        }

        if (prompted) return
        runCatching {
            manager.appUpdateInfo.addOnSuccessListener { info ->
                if (prompted) return@addOnSuccessListener
                if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener
                // Play decides which flow this update may use. Prefer the quiet one: a flexible update
                // downloads while the app carries on, rather than stopping the user in their tracks the
                // way an immediate one does.
                val type = when {
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
                    else -> return@addOnSuccessListener
                }
                prompted = true
                runCatching {
                    manager.startUpdateFlowForResult(info, activity, AppUpdateOptions.newBuilder(type).build(), REQUEST_CODE)
                }
            }
        }
    }
}
