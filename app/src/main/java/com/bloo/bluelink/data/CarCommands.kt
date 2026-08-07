package com.bloo.bluelink.data

import android.content.Context
import com.bloo.bluelink.work.ClimateExtendWorker

/**
 * Run a car command and apply the side effects that belong to the PHONE rather than to
 * the command itself.
 *
 * Right now that is exactly one rule: stopping climate must cancel any pending
 * auto-extend chain. [ClimateExtendWorker] re-issues CLIMATE_ON every ~10 minutes to
 * stretch a 30-minute request past the car's per-command ceiling, so a chain left
 * running after the user stopped climate would restart the car up to ten minutes later.
 *
 * The rule lives here because [WearCommandRunner.execute] is in `:shared` and cannot
 * reference `:app`'s worker, and because the four call sites that CAN reach it --
 * WidgetActions, AlertActionReceiver (the notification's "Turn off" button),
 * WearPhoneService (relayed watch commands) and TileCommandRunner -- would otherwise each
 * need their own copy of it. Four copies of a rule is how this codebase's recurring class
 * of bug gets made; ClimateExtendWorker.cancel already had exactly two callers, both in
 * AppViewModel, under a KDoc claiming it was "called whenever climate is stopped
 * manually".
 *
 * ⚠ Known residual gap, deliberately not papered over: the watch's STANDALONE path calls
 * WearCommandRunner directly from `:wear`, which also cannot see `:app`. That only happens
 * when the phone is unreachable — and the chain runs ON the phone, so an unreachable phone
 * will still fire it. Closing that needs a stop marker in the shared snapshot payload,
 * which is a cross-process schema change and wants a real device to validate.
 */
suspend fun runCarCommand(context: Context, command: WearCommand): WearCommandResult {
    // Resolve the direction the same way execute() will, from the same store, so a
    // TOGGLE_CLIMATE that lands on "off" cancels too rather than only explicit
    // CLIMATE_OFF. Pure function over the snapshot; no extra network.
    val resolved = runCatching {
        SnapshotStore(context).current().vehicles.firstOrNull { it.vin == command.vin }
            ?.let { WearCommandRunner.resolveToggle(it, command.action) }
    }.getOrNull() ?: command.action

    val result = WearCommandRunner.execute(context, command)

    // Only on success: a failed stop means climate may still be running, and cancelling
    // the chain then would end the extension the user actually asked for.
    if (result.ok && resolved == WearAction.CLIMATE_OFF) {
        runCatching { ClimateExtendWorker.cancel(context, command.vin) }
    }
    return result
}
