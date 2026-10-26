package com.bloo.bluelink.wear

import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.doorLockLabel
import com.bloo.bluelink.data.isDriving
import com.bloo.bluelink.data.serviceCountdown
import com.bloo.bluelink.data.serviceDue
import java.util.Locale

/**
 * What the details page says about a car, as label/value pairs: a "status" group (what it is doing now) and a "care"
 * group (odometer, service, plate). Pure and each row shown only when the car reports it, so it can be tested without a
 * watch, and so the page grows with what the car actually tells us rather than with a fixed template.
 */
internal object WearCarFacts {
    fun status(v: VehicleSnapshot): List<Pair<String, String>> = buildList {
        if (v.hasBattery) v.percent?.let { add("Battery" to "$it%") }
        v.rangeMi?.let { add("Range" to "$it mi") }
        doorLockLabel(v.locked)?.let { add("Doors" to it) }
        v.climateOn?.let { add("Climate" to (if (it) "On" else "Off")) }
        if (v.hasBattery) {
            v.charging?.let { add("Charging" to (if (it) "Yes" else "No")) }
            v.chargeLimitPct?.let { add("Charge limit" to "$it%") }
        } else {
            v.engineOn?.let { add("Engine" to (if (it) "Running" else "Off")) }
        }
        // Only when the car has said how fast it is going: a missing speed is "unknown", not "parked".
        if (v.speedMph != null) add("Moving" to (if (v.isDriving) "Driving" else "Parked"))
    }

    fun care(v: VehicleSnapshot): List<Pair<String, String>> = buildList {
        v.odometer?.takeIf { it.isNotBlank() }?.let { add("Odometer" to it) }
        serviceLine(v)?.let { add("Service" to it) }
        v.licensePlate?.takeIf { it.isNotBlank() }?.let { add("Plate" to it) }
    }

    /** "Due in 2,500 mi" or "Overdue by 300 mi", from the last service, its interval and the odometer (the shared arithmetic). */
    fun serviceLine(v: VehicleSnapshot): String? {
        val interval = v.serviceIntervalMiles?.takeIf { it > 0 } ?: return null
        val odo = v.odometer?.filter { it.isDigit() }?.toIntOrNull()
        val left = serviceDue(odo, v.lastServiceMiles, interval) ?: return null
        return serviceCountdown(left) { miles(it) }
    }

    private fun miles(n: Int) = String.format(Locale.US, "%,d mi", n)
}
