package com.bloo.bluelink.ui

import android.content.Context
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.formatMinuteOfDay
import com.bloo.bluelink.work.ScheduledCommands

/**
 * Carries out a search command that names a clock time, and returns what to tell the person, or null when the command has no time.
 * Only locks and unlocks are timed: each is set as an exact alarm for that time.
 */
internal fun scheduleFromSearch(context: Context, vehicle: Vehicle, command: ParsedVehicleCommand): String? {
    val at = command.atMinute ?: return null
    val exact = ScheduledCommands.schedule(context, vehicle.vin, command.cmd, command.climateTarget, at)
    val scheduled = "${command.label} ${vehicle.name} at ${formatMinuteOfDay(at)}"
    return if (exact) scheduled else "$scheduled (may run a few minutes late: allow exact alarms for Bloo in system settings)"
}
