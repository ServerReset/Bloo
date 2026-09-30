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
        /** Which channel this alert belongs on. Defaults to state alerts; the
         *  "car started" and "charge complete" transitions pass
         *  [Notifications.CHANNEL_EVENTS]. */
        val channelId: String = Notifications.CHANNEL_ALERTS,
    )

    /**
     * Runs all enabled alert checks for one car against its latest polled
     * [status] and returns only the alerts that should fire *this call*.
     *
     * Each check follows the same "fire once" pattern using a per-check,
     * per-VIN boolean flag persisted in [settings] ([SettingsStore.alertFired] /
     * [SettingsStore.setAlertFired]): the condition is evaluated fresh every
     * call (this function is expected to run on a timer, e.g. from
     * [com.bloo.bluelink.work.AlertWorker]), but the flag suppresses posting the
     * same alert again on every subsequent tick while the condition remains true.
     * The flag is cleared back to false as soon as the underlying condition goes
     * away, so the alert is free to fire again next time the condition recurs --
     * and that reset happens regardless of [canDeliver], deliberately, so a flag
     * left set before delivery lapsed cannot outlive the condition it describes.
     * `out` accumulates whichever alerts actually fired on this pass and is
     * returned to the caller for posting/toasting.
     */
    suspend fun evaluate(
        settings: SettingsStore,
        v: Vehicle,
        status: VehicleStatus?,
        // Kotlin doesn't allow a suspend call as a default parameter value, so
        // the "reuse an already-loaded prefs" default has to be null + a
        // fallback load inside the body instead of in the signature. AlertWorker
        // already loads this once per tick and passes it in — it was being
        // re-read from DataStore once per VEHICLE, per tick, for an identical
        // value; other callers (AppViewModel) are unchanged, they just don't
        // pass one and this loads it itself, same as before.
        prefs: SettingsStore.NotificationPrefs? = null,
        /**
         * Whether the CALLER can actually get an alert in front of the user.
         *
         * The fire-once flag means "the user has been told about this", so recording it
         * when nobody was told is a lie that this function then believes forever. That was
         * live: [com.bloo.bluelink.work.AlertWorker]'s only delivery channel is a system
         * notification, and [Notifications.post] silently returns without posting when
         * POST_NOTIFICATIONS isn't granted -- so with notifications off, a door left open
         * marked itself alerted and stayed that way for the whole open episode. Worse for
         * the service-due alert, whose condition never clears on its own: marked once,
         * suppressed until the user records a service.
         *
         * False makes the fire sites below no-ops that touch NO flags -- so the condition
         * is simply re-evaluated next tick, and it fires for real the moment delivery
         * becomes possible. The RESET branches deliberately still run either way: a flag
         * left true because permission lapsed mid-episode would suppress the next genuine
         * alert once permission came back.
         *
         * Defaults true because most callers can always deliver --
         * [com.bloo.bluelink.ui.AppViewModel.checkAlerts] shows an in-app snackbar as well
         * as posting, and that needs no permission at all. Only a notification-only caller
         * should pass anything else.
         */
        canDeliver: Boolean = true,
    ): List<Alert> {
        val prefs = prefs ?: settings.notificationPrefs()
        val out = mutableListOf<Alert>()
        // The one shared powertrain rule (SettingsStore.kt) -- an EV has no
        // "engine" to reference, so the running/car-started wording below reads
        // from this instead of assuming every car burns fuel.
        val powertrain = resolvePowertrain(v, settings.powertrain(v.vin))

        if (prefs.service) {
            // Odometer strings can arrive with thousands separators (e.g. "12,345")
            // and fractional miles; parseOdometerMiles strips/floors them to an Int.
            val odo = parseOdometerMiles(v.odometer)
            val last = settings.lastServiceMiles(v.vin)
            val interval = settings.serviceIntervalMiles(v.vin)
            // "Due" mileage only exists once both the last-serviced mileage and
            // the chosen interval are known; either missing means we can't judge
            // due-ness at all (rather than treating it as "not due"). nextServiceMiles
            // is the shared formula -- this was a third inline `last + interval`, the
            // exact re-inlining that helper's KDoc warns against, and the one the phone
            // pebble already routes through.
            val due = if (last != null && interval != null) nextServiceMiles(last, interval) else null
            // serviceDue returns raw signed miles remaining ((last+interval) - odo),
            // or null if any input is unknown. `remaining <= 0` is exactly the
            // original `odo >= due` edge (fires the moment odo reaches the interval).
            val remaining = serviceDue(odo, last, interval)
            val key = "service_${v.vin}"
            if (remaining != null && remaining <= 0) {
                if (canDeliver && !settings.alertFired(key)) {
                    // formatDistance, not a bare "mi". This notification stated miles to a
                    // metric user while the phone service pebble (also formatDistance)
                    // showed the same figures in km -- so the one
                    // surface that interrupts you was the one in the wrong unit.
                    val metric = settings.unitSystem() == "metric"
                    // odo and due are both non-null in this branch (remaining != null requires
                    // all three inputs), but the compiler can't carry that through serviceDue's
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
                // Not due (or not knowable) -- reset the flag so a future crossing
                // of the threshold is free to alert again. Guarded: an unconditional
                // write still costs editTracked a full copy+diff of every preference
                // and the store's write mutex, even when the value is already false.
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }

        // A null status means this poll's fetch failed, not that doors/engine
        // are actually closed/off -- treating it as "closed" reset the open/running
        // timers on every transient failure, so a genuinely open door across a run
        // of flaky polls could indefinitely delay the alert it was meant to fire.
        // Skip evaluation entirely rather than guess.
        if (prefs.doorOpen && status != null) {
            val open = status.doorOpen?.anyOpen == true || status.trunkOpen == true || status.hoodOpen == true
            val key = "door_${v.vin}"
            val now = System.currentTimeMillis()
            if (open) {
                // Timestamp is set the first time we observe "open" and left alone
                // on every subsequent open observation, so it always reflects when
                // the open state *began*, not when we last checked.
                val since = settings.doorOpenSince(v.vin)
                if (since == null) {
                    settings.setDoorOpenSince(v.vin, now)
                } else if (
                    canDeliver &&
                    // Not while actually driving -- same exclusion the unlocked and
                    // running-too-long checks below already apply, and for the same
                    // reason: a window cracked or a trunk not yet latched while the
                    // car is genuinely moving is expected the entire drive, not a
                    // car left open and unattended. This check was the one of the
                    // three missing it, an inconsistency rather than a deliberate
                    // difference.
                    !status.isDriving &&
                    now - since > prefs.doorOpenMinutes * 60_000L && !settings.alertFired(key)
                ) {
                    // Been open long enough and haven't already alerted for this
                    // open episode -- fire, offering a one-tap Lock action.
                    out += Alert(
                        doorId(v),
                        "${v.name} door is open",
                        "A door/trunk/hood has been open for over ${prefs.doorOpenMinutes} min.",
                        actions = listOf(Notifications.Action("Lock", v.vin, CarAction.LOCK)),
                    )
                    settings.setAlertFired(key, true)
                }
            } else {
                // Door closed (and status was actually fetched, per the note above)
                // -- reset both the open-since clock and the fired flag so the next
                // open episode starts its own fresh timer/alert.
                if (settings.doorOpenSince(v.vin) != null) settings.setDoorOpenSince(v.vin, null)
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }

        // Distinct from the door-open check above: a car can be fully closed up
        // and still sitting unlocked (doorLock == false is the car's own lock
        // state, independent of whether any door/trunk/hood happens to be
        // open right now) -- same null-status skip reasoning as doorOpen.
        if (prefs.unlocked && status != null) {
            val unlocked = status.doorLock == false
            val key = "unlocked_${v.vin}"
            val now = System.currentTimeMillis()
            if (unlocked) {
                // Same "start the clock on first observation, don't reset it
                // while still true" pattern as the door-open/running checks.
                val since = settings.unlockedSince(v.vin)
                if (since == null) {
                    settings.setUnlockedSince(v.vin, now)
                } else if (
                    canDeliver &&
                    // Not while actually driving: some cars report doorLock == false
                    // for a stretch of active driving (child-lock/central-locking
                    // states that don't map cleanly to the simple locked/unlocked
                    // status field), and "your car has been left unlocked" is a
                    // false alarm when you're the one driving it right now -- reported
                    // as a real false-positive alert mid-drive. Deliberately NOT
                    // resetting `since` here (see below): once actual driving stops,
                    // a genuinely-still-unlocked car alerts immediately using the
                    // ORIGINAL start time, rather than waiting a fresh unlockedMinutes
                    // from the moment the drive ended.
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
                // Locked again (and status was actually fetched) -- reset both
                // the unlocked-since clock and the fired flag so the next
                // unlocked spell starts its own fresh timer/alert.
                if (settings.unlockedSince(v.vin) != null) settings.setUnlockedSince(v.vin, null)
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }

        if (prefs.running && status != null) {
            // Remote start / climate (and on supported cars, the engine) report as "on".
            val on = status.engine == true || status.airCtrlOn == true
            val key = "running_${v.vin}"
            val now = System.currentTimeMillis()
            if (on) {
                // Same "start the clock on first observation, don't reset it while
                // still true" pattern as the door-open check above.
                val since = settings.engineOnSince(v.vin)
                if (since == null) {
                    settings.setEngineOnSince(v.vin, now)
                } else if (
                    canDeliver &&
                    // Not while actually driving: the engine/climate being "on" for
                    // more than runningMinutes is completely expected the entire time
                    // you're driving -- that alert exists to catch a car idling
                    // unattended (remote-started and forgotten, or left running in a
                    // driveway), not to interrupt an ordinary long drive. Reported as
                    // a real false-positive mid-drive. `since` is deliberately NOT
                    // reset here (see the matching note on the unlocked check above):
                    // once you stop, a car that's STILL running alerts right away off
                    // the original on-since timestamp rather than restarting the clock.
                    !status.isDriving &&
                    now - since > prefs.runningMinutes * 60_000L &&
                    !settings.alertFired(key)
                ) {
                    out += Alert(
                        runningId(v),
                        "${v.name} is running",
                        // A pure EV has no engine at all to reference -- "engine/climate"
                        // read as a hedge that didn't apply to it. Every other powertrain
                        // (gas, hybrid, PHEV) keeps the hedge: airCtrlOn can be true from
                        // either the engine or, on some of them, a battery-only remote
                        // climate run, so "engine/climate" is the accurate answer there.
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
                // Engine/climate off -- reset the clock and the fired flag so the
                // next running episode gets its own fresh timer/alert.
                if (settings.engineOnSince(v.vin) != null) settings.setEngineOnSince(v.vin, null)
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }

        // Car started notification: detect transition from engine off to on
        if (prefs.carStarted && status != null) {
            val engineOn = status.engine == true
            if (engineOn && !settings.engineStartNotificationSent(v.vin)) {
                // Not while actually driving: on a coarse poll cadence, the FIRST
                // time this notices the engine turned on can already be well into
                // a drive, and "your car has been started" telling you something
                // you're actively doing and already know is noise, not news --
                // reported directly. Unlike the unlocked/running-too-long alerts
                // above, this one does NOT fire late once driving stops either: it
                // is a one-time "just turned on" notice, and one that shows up
                // only after the drive already happened would be equally useless
                // -- so the sent flag is set here even though nothing was posted,
                // closing out this ON-episode for good. That's distinct from the
                // canDeliver branch below it: a genuinely undelivered notification
                // (no permission) still leaves the flag UNSET so it retries once
                // delivery becomes possible, same as every other alert in this
                // function -- "couldn't tell you" and "chose not to tell you"
                // are different reasons and only one of them should ever retry.
                if (status.isDriving) {
                    settings.setEngineStartNotificationSent(v.vin, true)
                } else if (canDeliver) {
                    out += Alert(
                        carStartedId(v),
                        "${v.name} has been started",
                        // Same reasoning as the running-too-long alert above: an EV
                        // has no engine, so don't tell an EV owner theirs is "running".
                        if (powertrain == Powertrain.EV) "Your car is now on." else "Your car's engine is now running.",
                        // An event, not a nagging state alert: its own channel so it can be
                        // silenced (or made louder) independently of "door left open".
                        channelId = Notifications.CHANNEL_EVENTS,
                    )
                    settings.setEngineStartNotificationSent(v.vin, true)
                }
            } else if (!engineOn) {
                // Engine is off -- reset the notification flag so it fires again next time
                if (settings.engineStartNotificationSent(v.vin)) settings.setEngineStartNotificationSent(v.vin, false)
            }
        }

        // Charge complete notification: detect transition from charging to complete
        if (prefs.chargeComplete && status != null) {
            val isCharging = status.evStatus?.batteryCharge == true
            if (!isCharging && settings.chargeCompleteNotificationSent(v.vin)) {
                // Was charging before but no longer is -- already sent the start notification,
                // so only send complete if we had previously sent the charging notification
                if (canDeliver) {
                    out += Alert(
                        chargeCompleteId(v),
                        "${v.name} charging is complete",
                        "Your car has finished charging.",
                        channelId = Notifications.CHANNEL_EVENTS,
                    )
                }
                // Reset the flag so we're ready for the next charge cycle
                settings.setChargeCompleteNotificationSent(v.vin, false)
            } else if (isCharging) {
                // Currently charging -- mark that we've started a charge session
                if (!settings.chargeCompleteNotificationSent(v.vin)) {
                    settings.setChargeCompleteNotificationSent(v.vin, true)
                }
            }
        }
        return out
    }

    // Stable per-VIN notification ids, one distinct id per alert *kind* so the
    // three alert types for the same car never overwrite each other's
    // notification (each hashes a kind-prefixed string unique to that VIN).
    private fun serviceId(v: Vehicle) = ("svc" + v.vin).hashCode()
    private fun doorId(v: Vehicle) = ("door" + v.vin).hashCode()
    private fun runningId(v: Vehicle) = ("run" + v.vin).hashCode()
    private fun unlockedId(v: Vehicle) = ("unlocked" + v.vin).hashCode()
    private fun carStartedId(v: Vehicle) = ("started" + v.vin).hashCode()
    private fun chargeCompleteId(v: Vehicle) = ("complete" + v.vin).hashCode()
}
