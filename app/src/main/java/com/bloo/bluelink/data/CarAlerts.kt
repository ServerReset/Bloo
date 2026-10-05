package com.bloo.bluelink.data

/**
 * Evaluates per-car alert conditions, persisting bookkeeping so each fires once.
 * Returns the alerts that should be shown now (toast + notification).
 */
object CarAlerts {
    data class Alert(
        val id: Int,
        val title: String,
        val text: String,
        val actions: List<Notifications.Action> = emptyList(),
        /** Channel this alert belongs on; defaults to state alerts, transitions pass [Notifications.CHANNEL_EVENTS]. */
        val channelId: String = Notifications.CHANNEL_ALERTS,
        /** Keep it off the watch: set when the watch raises its own version of this alert. */
        val localOnly: Boolean = false,
    )

    /**
     * Runs all enabled alert checks for one car against its latest polled [status] and returns the
     * alerts that should fire this call.
     *
     * Each check is fire-once via a per-check, per-VIN flag in [settings] ([SettingsStore.alertFired]);
     * the flag resets when the condition clears, even when [canDeliver] is false.
     */
    suspend fun evaluate(
        settings: SettingsStore,
        v: Vehicle,
        status: VehicleStatus?,
        // Null default (suspend calls cannot be default values); callers that already loaded prefs pass them in.
        prefs: SettingsStore.NotificationPrefs? = null,
        /**
         * Whether the CALLER can actually get an alert in front of the user. The fire-once flag means
         * "the user has been told", so false makes the fire sites no-ops that touch no flags (re-evaluated
         * next tick); RESET branches still run. Only notification-only callers should pass false
         * (Notifications.post silently skips without POST_NOTIFICATIONS).
         */
        canDeliver: Boolean = true,
    ): List<Alert> {
        val prefs = prefs ?: settings.notificationPrefs()
        val out = mutableListOf<Alert>()
        // The shared powertrain rule: an EV has no "engine" for the wording below.
        val powertrain = resolvePowertrain(v, settings.powertrain(v.vin))

        if (prefs.service) {
            // Odometer strings may have thousands separators and fractions; parseOdometerMiles floors to Int.
            val odo = parseOdometerMiles(v.odometer)
            val last = settings.lastServiceMiles(v.vin)
            val interval = settings.serviceIntervalMiles(v.vin)
            // "Due" mileage only exists once both the last-serviced mileage and the chosen interval
            // are known; either missing means we can't judge due-ness at all (rather than treating
            // it as "not due"). nextServiceMiles is the shared formula -- this was a third inline
            // `last + interval`, the exact re-inlining that helper's KDoc warns against, and the
            // one the phone pebble already routes through.
            val due = if (last != null && interval != null) nextServiceMiles(last, interval) else null
            // Raw signed miles remaining; `remaining <= 0` fires the moment odo reaches the interval.
            val remaining = serviceDue(odo, last, interval)
            val key = "service_${v.vin}"
            if (remaining != null && remaining <= 0) {
                if (canDeliver && !settings.alertFired(key)) {
                    // formatDistance so metric users see km.
                    val metric = settings.metricDistance()
                    // odo and due are both non-null in this branch (remaining != null requires all
                    // three inputs), but the compiler can't carry that through serviceDue's
                    // signature -- odo is Int? from parseOdometerMiles. `?.let ?: ""` keeps it
                    // total without a not-null assertion; the empty fallback is unreachable here.
                    val odoStr = odo?.let { formatDistance(it, metric) } ?: ""
                    val dueStr = due?.let { formatDistance(it, metric) } ?: ""
                    out += Alert(
                        serviceId(v),
                        "${v.name} is due for service",
                        "Odometer $odoStr is past the $dueStr service interval.",
                    )
                    settings.setAlertFired(key, true)
                }
            } else {
                // Not due or unknowable: reset the flag. Guarded since editTracked writes are costly.
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }

        // A null status is a failed fetch, not "closed"; skip rather than reset the timers.
        if (prefs.doorOpen && status != null) {
            val open = status.doorOpen?.anyOpen == true || status.trunkOpen == true || status.hoodOpen == true
            val key = "door_${v.vin}"
            val now = System.currentTimeMillis()
            if (open) {
                // Timestamp is set the first time we observe "open" and left alone on every
                // subsequent open observation, so it always reflects when the open state *began*,
                // not when we last checked.
                val since = settings.doorOpenSince(v.vin)
                if (since == null) {
                    settings.setDoorOpenSince(v.vin, now)
                } else if (
                    canDeliver &&
                    // Not while driving (matches the unlocked and running checks): an unlatched trunk mid-drive is expected.
                    !status.isDriving &&
                    now - since > prefs.doorOpenMinutes * 60_000L && !settings.alertFired(key)
                ) {
                    // Open long enough and not yet alerted: offer a one-tap Lock action.
                    out += Alert(
                        doorId(v),
                        "${v.name} door is open",
                        "A door/trunk/hood has been open for over ${prefs.doorOpenMinutes} min.",
                        actions = listOf(Notifications.Action("Lock", v.vin, CarAction.LOCK)),
                    )
                    settings.setAlertFired(key, true)
                }
            } else {
                // Closed: reset the open-since clock and fired flag.
                if (settings.doorOpenSince(v.vin) != null) settings.setDoorOpenSince(v.vin, null)
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }

        // Distinct from door-open: a closed car can still be unlocked (doorLock == false).
        if (prefs.unlocked && status != null) {
            val unlocked = status.doorLock == false
            val key = "unlocked_${v.vin}"
            val now = System.currentTimeMillis()
            if (unlocked) {
                // Start the clock on first observation, as in the door-open check.
                val since = settings.unlockedSince(v.vin)
                if (since == null) {
                    settings.setUnlockedSince(v.vin, now)
                } else if (
                    canDeliver &&
                    // Not while driving: some cars report doorLock == false mid-drive. `since` is not reset, so a
                    // still-unlocked car alerts immediately after the drive using the original start time.
                    !status.isDriving &&
                    now - since > prefs.unlockedMinutes * 60_000L &&
                    !settings.alertFired(key)
                ) {
                    out += Alert(
                        unlockedId(v),
                        "${v.name} is unlocked",
                        "It's been left unlocked for over ${prefs.unlockedMinutes} min.",
                        actions = listOf(Notifications.Action("Lock", v.vin, CarAction.LOCK)),
                    )
                    settings.setAlertFired(key, true)
                }
            } else {
                // Locked again: reset the clock and fired flag.
                if (settings.unlockedSince(v.vin) != null) settings.setUnlockedSince(v.vin, null)
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }

        if (prefs.running && status != null) {
            // Remote start / climate (and on some cars the engine) report as "on".
            val on = status.engine == true || status.airCtrlOn == true
            val key = "running_${v.vin}"
            val now = System.currentTimeMillis()
            if (on) {
                // Start the clock on first observation, as in the door-open check.
                val since = settings.engineOnSince(v.vin)
                if (since == null) {
                    settings.setEngineOnSince(v.vin, now)
                } else if (
                    canDeliver &&
                    // Not while driving: running is expected on a drive. `since` is not reset (see the unlocked check).
                    !status.isDriving &&
                    now - since > prefs.runningMinutes * 60_000L &&
                    !settings.alertFired(key)
                ) {
                    out += Alert(
                        runningId(v),
                        "${v.name} is running",
                        // A pure EV has no engine at all to reference -- "engine/climate" read as a
                        // hedge that didn't apply to it. Every other powertrain (gas, hybrid, PHEV)
                        // keeps the hedge: airCtrlOn can be true from either the engine or, on some
                        // of them, a battery-only remote climate run, so "engine/climate" is the
                        // accurate answer there.
                        if (powertrain == Powertrain.EV) {
                            "The climate has been running for over ${prefs.runningMinutes} min."
                        } else {
                            "The engine/climate has been running for over ${prefs.runningMinutes} min."
                        },
                        actions = listOf(Notifications.Action("Turn off", v.vin, CarAction.CLIMATE_OFF)),
                    )
                    settings.setAlertFired(key, true)
                }
            } else {
                // Off: reset the clock and fired flag.
                if (settings.engineOnSince(v.vin) != null) settings.setEngineOnSince(v.vin, null)
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }

        // Car started: detect the engine off-to-on transition.
        if (prefs.carStarted && status != null) {
            val engineOn = status.engine == true
            if (engineOn && !settings.engineStartNotificationSent(v.vin)) {
                // Unlike the unlocked/running-too-long alerts above, this one does NOT fire late
                // once driving stops either: it is a one-time "just turned on" notice, and one that
                // shows up only after the drive already happened would be equally useless -- so the
                // sent flag is set here even though nothing was posted, closing out this ON-episode
                // for good.
                if (status.isDriving) {
                    settings.setEngineStartNotificationSent(v.vin, true)
                } else if (canDeliver) {
                    out += Alert(
                        carStartedId(v),
                        "${v.name} has been started",
                        // An EV has no engine to call "running".
                        if (powertrain == Powertrain.EV) "Your car is now on." else "Your car's engine is now running.",
                        // An event, not a state alert: own channel so it can be silenced independently.
                        channelId = Notifications.CHANNEL_EVENTS,
                    )
                    settings.setEngineStartNotificationSent(v.vin, true)
                }
            } else if (!engineOn) {
                // Engine off: reset the flag.
                if (settings.engineStartNotificationSent(v.vin)) settings.setEngineStartNotificationSent(v.vin, false)
            }
        }

        // Charge complete: detect the charging-to-complete transition.
        if (prefs.chargeComplete && status != null) {
            val isCharging = status.evStatus?.batteryCharge == true
            if (!isCharging && settings.chargeCompleteNotificationSent(v.vin)) {
                // Only send complete if the charging notification was sent earlier.
                if (canDeliver) {
                    out += Alert(
                        chargeCompleteId(v),
                        "${v.name} charging is complete",
                        "Your car has finished charging.",
                        channelId = Notifications.CHANNEL_EVENTS,
                        // The watch raises its own "charged" alert, so don't also bridge this one.
                        localOnly = com.bloo.bluelink.wear.WatchPresence.appInstalled(settings.context),
                    )
                }
                // Reset for the next charge cycle.
                settings.setChargeCompleteNotificationSent(v.vin, false)
            } else if (isCharging) {
                // Charging: mark the session started.
                if (!settings.chargeCompleteNotificationSent(v.vin)) {
                    settings.setChargeCompleteNotificationSent(v.vin, true)
                }
            }
        }
        return out
    }

    // Stable per-VIN notification ids, one distinct id per alert *kind* so the three alert types
    // for the same car never overwrite each other's notification (each hashes a kind-prefixed
    // string unique to that VIN).
    private fun serviceId(v: Vehicle) = ("svc" + v.vin).hashCode()
    private fun doorId(v: Vehicle) = ("door" + v.vin).hashCode()
    private fun runningId(v: Vehicle) = ("run" + v.vin).hashCode()
    private fun unlockedId(v: Vehicle) = ("unlocked" + v.vin).hashCode()
    private fun carStartedId(v: Vehicle) = ("started" + v.vin).hashCode()
    private fun chargeCompleteId(v: Vehicle) = ("complete" + v.vin).hashCode()
}
