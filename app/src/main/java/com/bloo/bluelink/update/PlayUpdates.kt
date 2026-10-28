package com.bloo.bluelink.update

import android.app.Activity
import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Play's own In-App Updates flow, for a copy that Play installed.
 *
 * Play owns the download and the install. This object only asks Play whether a newer build exists ([refresh]) and, when the user asks, hands
 * Play the flow ([start]). The answer is published as [available], which the update panel reads, so a Play copy shows the same update
 * surface as a GitHub one: an offer, a primary action, a check. The two permissions the sideload path needs -- REQUEST_INSTALL_PACKAGES and
 * Shizuku's -- are absent from the Play artifact by construction.
 */
internal object PlayUpdates {
    /** Only ever used to tell Play's own flow apart from another caller's; never surfaced. */
    private const val REQUEST_CODE = 0x8170

    private var manager: AppUpdateManager? = null
    private var listening = false

    /**
     * One automatic prompt per process. Play's own state already stops a flow being started twice (it reports IN_PROGRESS), but nothing
     * stops a user who DECLINED from being asked again on every resume -- and that is nagging.
     */
    private var prompted = false

    /** Play's own info for the newer build, kept between [refresh] and [start]. Null when Play has nothing newer. */
    private var pending: AppUpdateInfo? = null

    private val _available = MutableStateFlow(false)

    /** True while Play reports a newer build for this copy. Read by the update panel and the update pebble's visibility. */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private fun managerFor(context: Context): AppUpdateManager? =
        manager ?: runCatching { AppUpdateManagerFactory.create(context.applicationContext) }.getOrNull()?.also { manager = it }

    /**
     * Asks Play whether a newer build exists, and publishes the answer to [available]. Needs no Activity, so the Settings card can check at
     * any time. [then] runs only when there is a newer build.
     */
    fun refresh(context: Context, then: (() -> Unit)? = null) {
        val manager = managerFor(context) ?: return
        runCatching {
            manager.appUpdateInfo.addOnSuccessListener { info ->
                val newer = info.takeIf { it.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE }
                pending = newer
                _available.value = newer != null
                if (newer != null) then?.invoke()
            }
        }
    }

    /**
     * Opens Play's own flow for the build [refresh] found. A flexible update downloads while the app carries on, so it is preferred;
     * Play decides which types it allows. Returns false when there is nothing to start.
     */
    fun start(activity: Activity): Boolean {
        val info = pending ?: return false
        val type = when {
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> AppUpdateType.IMMEDIATE
            else -> return false
        }
        val manager = managerFor(activity) ?: return false
        return runCatching {
            manager.startUpdateFlowForResult(info, activity, AppUpdateOptions.newBuilder(type).build(), REQUEST_CODE)
        }.isSuccess
    }

    /** Called from `MainActivity.onStart`, and only for a Play-installed copy. Prompts once per process, then leaves the choice to the user. */
    fun onForeground(activity: Activity) {
        val manager = managerFor(activity) ?: return

        if (!listening) {
            listening = true
            // A flexible update downloads in the background and then waits for the app to say it may restart. If it finished while the app
            // was away, finish it now; from there Play's own UI takes over. An explicit object, not a SAM lambda:
            // InstallStateUpdatedListener inherits its single method from a generic super-interface, which Kotlin will not SAM-convert.
            val onState = object : InstallStateUpdatedListener {
                override fun onStateUpdate(state: InstallState) {
                    if (state.installStatus() == InstallStatus.DOWNLOADED) {
                        runCatching { manager.completeUpdate() }
                    }
                }
            }
            runCatching { manager.registerListener(onState) }
        }

        if (prompted) {
            refresh(activity)
            return
        }
        refresh(activity) {
            if (prompted) return@refresh
            prompted = true
            start(activity)
        }
    }
}
