package com.bloo.bluelink.data

import android.content.Context
import kotlinx.coroutines.sync.withLock

/**
 * Executes a [WearCommand] against the car backend using the stored session,
 * and folds the result into the on-disk [SnapshotStore]. Lives in :shared so the
 * phone (relaying the watch's command) and the watch (running standalone on its
 * own connection) share one implementation — the same stored-session pattern the
 * Quick-Settings tiles use.
 */
object WearCommandRunner {

    /**
     * Executes one [WearCommand] end-to-end: looks up the target vehicle's current
     * [VehicleSnapshot], builds a fresh brand-specific [VehicleRepository] and
     * [ClimateRequest] from the command's fields, dispatches the right repository
     * call for [command].action inside the process-wide [BlueLinkGate] lock, and
     * (on success) writes the resulting optimistic-but-now-confirmed snapshot back to
     * [SnapshotStore] plus an [AppLog] line. Any thrown exception during dispatch is
     * caught by the outer `runCatching`/`getOrElse` and turned into a failed
     * [WearCommandResult] with the exception's message, rather than propagating.
     */
    suspend fun execute(context: Context, command: WearCommand): WearCommandResult {
        val store = SnapshotStore(context)
        // Same lock refresh() and the phone UI's own command path already use --
        // BlueLink 502s on overlapping requests for the same account, and this
        // was the one command-executing path that skipped it, so a resent watch
        // command (e.g. after a slow BLE ack) could fire the same command twice
        // concurrently, or race a phone-UI-driven command, with no protection.
        return BlueLinkGate.statusMutex.withLock {
            // Read the target vehicle's snapshot INSIDE the lock so the toggle
            // direction is decided from state serialized against every other
            // command path. If this read happened before acquiring the lock, two
            // overlapping TOGGLE_* commands would both observe the same pre-toggle
            // state and the second would invert the first instead of re-applying
            // it (e.g. car locked -> A unlocks, B still sees locked -> sends
            // UNLOCK again). See resolveToggle's docstring.
            val snap = store.current().vehicles.firstOrNull { it.vin == command.vin }
                ?: return@withLock WearCommandResult(command.vin, command.action, ok = false, message = "Car not found")
            val v = snap.toVehicle()
            val repo = repositoryFor(
                Brand.fromIndicator(v.brandIndicator),
                SessionStore(context), CredentialStore(context),
            )
            val climate = ClimateRequest(
                tempF = command.tempF,
                defrost = command.defrost,
                durationMinutes = command.durationMinutes,
                steeringWheelHeat = command.steeringWheelHeat,
                seatFrontLeft = SeatLevel.fromApi(command.seatFrontLeft),
                seatFrontRight = SeatLevel.fromApi(command.seatFrontRight),
                seatRearLeft = SeatLevel.fromApi(command.seatRearLeft),
                seatRearRight = SeatLevel.fromApi(command.seatRearRight),
            )
            runCatching {
                // The optimistic climate flag, but only for a brand whose status
                // can later CONFIRM it (see Brand.reportsClimateState). Europe
                // never reports airCtrlOn, and SnapshotStore keeps the old value
                // when a status field is null -- so an optimistic `true` there is
                // written once and never corrected, leaving the climate button lit
                // permanently and the toggle sending STOP after the car's own
                // timer already ended the session. Unknown is the honest answer.
                fun climateFlag(on: Boolean): Boolean? =
                    if (v.brand.reportsClimateState) on else null
                val updated = when (command.action) {
                    WearAction.TOGGLE_LOCK ->
                        if (snap.locked == true) { repo.unlock(v); snap.copy(locked = false) }
                        else { repo.lock(v); snap.copy(locked = true) }
                    WearAction.LOCK -> { repo.lock(v); snap.copy(locked = true) }
                    WearAction.UNLOCK -> { repo.unlock(v); snap.copy(locked = false) }
                    WearAction.TOGGLE_CLIMATE ->
                        if (snap.climateOn == true) { repo.stopClimate(v); snap.copy(climateOn = climateFlag(false)) }
                        else {
                            // The car rejects remote climate commands while it's
                            // moving (same gate the phone UI's own Start button
                            // applies) -- this is the standalone watch path and
                            // the widget/relay path (both funnel through here),
                            // neither of which checked this before.
                            if (snap.isDriving) error("Can't start climate while driving")
                            repo.startClimate(v, climate); snap.copy(climateOn = climateFlag(true))
                        }
                    WearAction.CLIMATE_ON -> {
                        if (snap.isDriving) error("Can't start climate while driving")
                        repo.startClimate(v, climate); snap.copy(climateOn = climateFlag(true))
                    }
                    WearAction.CLIMATE_OFF -> { repo.stopClimate(v); snap.copy(climateOn = climateFlag(false)) }
                    WearAction.TOGGLE_CHARGE ->
                        if (snap.charging == true) { repo.stopCharge(v); snap.copy(charging = false) }
                        else { repo.startCharge(v); snap.copy(charging = true) }
                    WearAction.CHARGE_ON -> { repo.startCharge(v); snap.copy(charging = true) }
                    WearAction.CHARGE_OFF -> { repo.stopCharge(v); snap.copy(charging = false) }
                    WearAction.SET_CHARGE_LIMITS -> { repo.setChargeTargets(v, command.acLimit, command.dcLimit); snap }
                    // Momentary, not stateful -- no snap field to flip, so
                    // these fall through optimistic()/resolveToggle()/
                    // stateFor() below untouched (their `else` branches).
                    WearAction.FLASH_LIGHTS -> { repo.flashLights(v); snap }
                    WearAction.HORN_AND_LIGHTS -> { repo.hornAndLights(v); snap }
                    else -> return@withLock WearCommandResult(command.vin, command.action, ok = false, message = "Unknown action")
                }
                store.updateVehicle(updated)
                AppLog.log("${command.action} → ${v.name}")
                WearCommandResult(command.vin, command.action, ok = true)
            }.getOrElse { e ->
                AppLog.log("Command failed (${command.action}): ${e.message}")
                WearCommandResult(command.vin, command.action, ok = false, message = e.message ?: "Command failed")
            }
        }
    }

    /**
     * Resolve a TOGGLE_* verb into its explicit direction from [snap]. Any caller
     * that persists [optimistic] BEFORE the command executes MUST resolve first:
     * [execute] decides toggle direction by re-reading the store, so a snapshot
     * that was already optimistically flipped would invert the command (tap
     * "unlock" on a locked car -> store flips to unlocked -> execute sees
     * unlocked -> sends LOCK).
     */
    fun resolveToggle(snap: VehicleSnapshot, action: String): String = when (action) {
        WearAction.TOGGLE_LOCK ->
            if (snap.locked == true) WearAction.UNLOCK else WearAction.LOCK
        WearAction.TOGGLE_CLIMATE ->
            if (snap.climateOn == true) WearAction.CLIMATE_OFF else WearAction.CLIMATE_ON
        WearAction.TOGGLE_CHARGE ->
            if (snap.charging == true) WearAction.CHARGE_OFF else WearAction.CHARGE_ON
        else -> action
    }

    // inverse() was removed. It returned the verb whose optimistic() write undid
    // another's, and both revert sites used it as `optimistic(snap, inverse(action))`
    // to roll back a failed command's flip. That is only an undo when the flip
    // actually changed something. optimistic() writes an absolute value, so when the
    // field had been null -- the car has never reported it -- the "undo" invented a
    // definite false out of nothing, and the widget or tile went on to state plainly
    // that a car whose doors it knows nothing about is Unlocked. Nothing about the
    // post-flip snapshot could have told it otherwise; the information was gone
    // before the revert ran.
    //
    // Deleted rather than kept alongside the fix because leaving it would leave two
    // ways to revert, one of which is wrong in a case the other exists to handle.
    // Use [stateFor] before the flip and [withState] after the failure.

    /**
     * The snapshot field [action]'s [optimistic] prediction will overwrite, read
     * BEFORE that prediction is stored so a failed command can put back exactly what
     * was there.
     *
     * Returns null both for "the car has never reported this" and for verbs that
     * change no snapshot field at all, and those two cases want the same thing from
     * [withState] anyway -- put back nothing definite. Accepts TOGGLE_* as well as
     * the resolved verbs so it can be called on either side of [resolveToggle].
     */
    fun stateFor(snap: VehicleSnapshot, action: String): Boolean? = when (action) {
        WearAction.TOGGLE_LOCK, WearAction.LOCK, WearAction.UNLOCK -> snap.locked
        WearAction.TOGGLE_CLIMATE, WearAction.CLIMATE_ON, WearAction.CLIMATE_OFF -> snap.climateOn
        WearAction.TOGGLE_CHARGE, WearAction.CHARGE_ON, WearAction.CHARGE_OFF -> snap.charging
        else -> null
    }

    /** Puts a value read by [stateFor] back into the field [action] touches — the
     *  revert half. Restoring null is meaningful and intended: it returns the field
     *  to "unknown", which every surface already knows how to render as nothing
     *  rather than as a state the car never reported. */
    fun withState(snap: VehicleSnapshot, action: String, value: Boolean?): VehicleSnapshot = when (action) {
        WearAction.TOGGLE_LOCK, WearAction.LOCK, WearAction.UNLOCK -> snap.copy(locked = value)
        WearAction.TOGGLE_CLIMATE, WearAction.CLIMATE_ON, WearAction.CLIMATE_OFF -> snap.copy(climateOn = value)
        WearAction.TOGGLE_CHARGE, WearAction.CHARGE_ON, WearAction.CHARGE_OFF -> snap.copy(charging = value)
        else -> snap
    }

    /**
     * The snapshot a command is expected to produce, for instant optimistic UI.
     *
     * Climate is gated by [Brand.reportsClimateState], the same flag [execute]'s own
     * `climateFlag` helper reads. This function used to write an unconditional
     * true/false for every brand -- correct for the brands whose status refresh can
     * confirm or correct it, but not for Europe, whose `airCtrlOn` is always null.
     * [execute] already routed its OWN optimistic write through the brand-aware
     * check; this is the other caller of the same idea (widget taps, tile taps),
     * reachable straight from [com.bloo.bluelink.widget.WidgetCommandAction] and
     * `TileCommandRunner` -- both call this directly rather than going through
     * `execute()`, so a Hyundai EU widget/tile button was still free to paint a
     * climate state on ("Climate on", teal highlight) that the car can never
     * actually confirm, for however long the real command takes to land.
     */
    fun optimistic(snap: VehicleSnapshot, action: String): VehicleSnapshot {
        val climateKnown = Brand.fromIndicator(snap.brandIndicator).reportsClimateState
        return when (action) {
            WearAction.TOGGLE_LOCK -> snap.copy(locked = !(snap.locked ?: false))
            WearAction.LOCK -> snap.copy(locked = true)
            WearAction.UNLOCK -> snap.copy(locked = false)
            WearAction.TOGGLE_CLIMATE ->
                snap.copy(climateOn = if (climateKnown) !(snap.climateOn ?: false) else null)
            WearAction.CLIMATE_ON -> snap.copy(climateOn = if (climateKnown) true else null)
            WearAction.CLIMATE_OFF -> snap.copy(climateOn = if (climateKnown) false else null)
            WearAction.TOGGLE_CHARGE -> snap.copy(charging = !(snap.charging ?: false))
            WearAction.CHARGE_ON -> snap.copy(charging = true)
            WearAction.CHARGE_OFF -> snap.copy(charging = false)
            else -> snap
        }
    }

    /**
     * Refresh one car (blank [vin] → all), folding fresh status into snapshots.
     * [force] true wakes the car for a live pull (on-demand button); false reads
     * the server's last-known status — light enough for frequent background polls
     * that keep widgets/tiles fresh without draining the car's 12V battery.
     */
    /** Returns whether any car's status was actually obtained. Callers that show a
     *  failure message need this: it used to return Unit, so the only signal a caller
     *  could reach was "did the relay to the phone succeed", which is a different
     *  question and false in the perfectly healthy standalone case. Most callers
     *  legitimately ignore the result. */
    suspend fun refresh(
        context: Context,
        vin: String,
        force: Boolean = true,
        /**
         * Receives every VehicleStatus this call actually fetched, keyed by VIN.
         *
         * Exists for the WATCH. `refresh` folds each status into the snapshot and drops the
         * rest, which is all the phone needs -- it keeps its own StatusCache. The watch has no
         * other source: its `statuses` map's only inbound path was `statusCache.load()`, and
         * nothing in the wear module ever called `statusCache.save()`, so the map was
         * permanently empty and every statuses-only field was dead. The Diagnostics tile never
         * appeared, `alertCount` was always 0 so the Alerts tile never appeared with a door
         * open, and ~25 diagnostics fields were always null -- while THIS function was
         * fetching all of it and throwing it away.
         *
         * A callback rather than a changed return type, deliberately: the four other callers
         * (tile worker, widget receiver, widget actions, WearPhoneService) are unaffected and
         * do not have to know. And NOT a StatusCache write in here, because the phone calls
         * this too and its cache also holds locations/placeNames -- a read-modify-write from
         * here would race the phone's own saves and could blank them.
         */
        onStatuses: (suspend (Map<String, VehicleStatus>) -> Unit)? = null,
    ): Boolean {
        val store = SnapshotStore(context)
        val targets = store.current().vehicles.let { all ->
            if (vin.isBlank()) all else all.filter { it.vin == vin }
        }
        // Declared outside the lock only so the success signal below can read it; it is
        // still populated and written entirely inside it.
        val merged = mutableListOf<VehicleSnapshot>()
        // The full statuses, for [onStatuses]. The snapshot fold above keeps only the handful
        // of fields VehicleSnapshot carries; this keeps the whole thing.
        val fetched = mutableMapOf<String, VehicleStatus>()
        BlueLinkGate.statusMutex.withLock {
            // One repo instance per brand, reused across that brand's vehicles
            // in this loop -- a fresh KiaRepository per vehicle threw away its
            // account-wide vehicle-list cache each time, so "refresh all" on N
            // Kia cars fired N redundant full-account list calls (each already
            // covering all N cars) instead of one.
            val reposByBrand = mutableMapOf<Brand, VehicleRepository>()
            // Collected and written ONCE at the end rather than per car. The payload is
            // a single JSON blob, so each write decodes and re-encodes every vehicle and
            // commits to disk -- "refresh all" on N cars was paying N of those to change
            // N cars, and emitting N times on SnapshotStore.payload, which made every
            // widget, tile and complication observing it repaint N times per refresh.
            targets.forEach { snap ->
                runCatching {
                    val v = snap.toVehicle()
                    val brand = Brand.fromIndicator(v.brandIndicator)
                    val repo = reposByBrand.getOrPut(brand) {
                        repositoryFor(brand, SessionStore(context), CredentialStore(context))
                    }
                    repo.status(v, refresh = force)?.let {
                        // Only when the status carried no GPS. US brands report it inline, so
                        // they pay nothing here; Canada and Europe expose position only through
                        // a separate find-my-car call, and without it their snapshots never move
                        // -- which also pins isDriving at false, so the "can't start climate
                        // while driving" guard below can never fire on those cars.
                        val fix = if (it.vehicleLocation == null) {
                            runCatching { repo.location(v) }.getOrNull()
                        } else {
                            null
                        }
                        merged += snap.merged(it, fix)
                        fetched[v.vin] = it
                    }
                }
            }
            // Whatever succeeded gets written even if some cars failed -- the per-car
            // runCatching above means one brand being down must not discard the others,
            // which is what the old per-car write gave for free.
            //
            // Still INSIDE the lock, deliberately. The write itself needs no mutex, but
            // moving it out would open a window where a command's optimistic write lands
            // between a status fetch and this write, and this would then overwrite that
            // flip with the older fetched status.
            store.updateVehicles(merged)
        }
        // Outside the lock: the callback is the caller's code and must not run holding the
        // app-wide status mutex. Only when something was actually fetched, so a total failure
        // cannot be mistaken for "fetched nothing, so clear everything".
        if (fetched.isNotEmpty()) onStatuses?.invoke(fetched)
        // Empty means every car's fetch failed (or there were no cars). Note this is
        // computed after the lock, from the same list that was written.
        return merged.isNotEmpty()
    }
}
