package com.bloo.bluelink.autolock

import android.content.Context
import com.bloo.bluelink.data.AppLog

/**
 * What happens when a car's Bluetooth link goes away (and when it comes back), shared by the
 * manifest [AutoLockBluetoothReceiver] and the debug-only [AutoLockDebugReceiver].
 *
 * Rebased onto the i5-AutoLock reference (github.com/Vel-San/i5-AutoLock), which simply runs
 * its evaluation in-process: the controller owns its own coroutine scope, so the Bluetooth
 * broadcast that just fired the receiver is all it needs to start. This app had grown a whole
 * second "alarm + persisted pending record" fallback for the case where the OS refuses a
 * background foreground-service start -- and that fallback (not the evaluation itself) is what
 * broke. The reference never needed it, so it is gone: the trigger just starts the controller.
 *
 * The foreground service is still started BEST-EFFORT, purely so the progress notification can
 * show. A blocked background start (the norm on Android 12+) is expected and harmless now that
 * nothing depends on it -- the evaluation is already running in this process.
 */
internal object AutoLockTrigger {

    /**
     * A car's Bluetooth just disconnected: start an evaluation.
     *
     * [settings] is passed in rather than read here so the debug trigger can supply its own
     * dry-run/short-grace config while still exercising exactly this code.
     */
    fun onCarDisconnected(
        context: Context,
        vin: String,
        settings: AutoLockConfig,
        log: Boolean = true,
    ) {
        val ctx = context.applicationContext
        // Best-effort notification wrapper -- see this object's own doc for why nothing depends
        // on it succeeding.
        AutoLockService.start(ctx, vin)
        // The evaluation itself, in-process and independent of the service.
        AutoLockController.onTriggerFired(ctx, vin)
        if (log) {
            AppLog.log("AutoLock: car Bluetooth disconnected for $vin — evaluating.")
        }
    }

    /** The car reconnected (the user got back in): abort whatever is pending for it. */
    fun onCarConnected(context: Context, vin: String) {
        AutoLockController.cancel(context.applicationContext, vin)
    }
}
