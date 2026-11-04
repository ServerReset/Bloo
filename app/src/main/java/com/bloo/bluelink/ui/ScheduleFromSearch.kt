package com.bloo.bluelink.ui

import android.content.Context
import com.bloo.bluelink.data.DepartureSchedule
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.formatMinuteOfDay
import com.bloo.bluelink.work.ScheduledCommands

/**
 * Carries out a search command that names a clock time, and returns what to tell the person, or null when the command has no time.
 * "departure" sets the car's first pre-run to that time and switches it on. Any other command is set as an exact alarm for that time.
 */
internal fun scheduleFromSearch(context: Context, vm: AppViewModel, state: UiState, vehicle: Vehicle, command: ParsedVehicleCommand): String? {
    val at = command.atMinute ?: return null
    if (command.cmd == "departure") {
        val slot = (state.departures[vehicle.vin] ?: DepartureSchedule()).slots[0].copy(enabled = true, minuteOfDay = at)
        vm.setDeparture(vehicle, 0, slot)
        return "Pre-run ${slot.label(0)} set for ${formatMinuteOfDay(at)}"
    }
    val exact = ScheduledCommands.schedule(context, vehicle.vin, command.cmd, command.climateTarget, at)
    val scheduled = "${command.label} ${vehicle.name} at ${formatMinuteOfDay(at)}"
    return if (exact) scheduled else "$scheduled (may run a few minutes late: allow exact alarms for Bloo in system settings)"
}
