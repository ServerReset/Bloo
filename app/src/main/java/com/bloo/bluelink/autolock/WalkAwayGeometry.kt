package com.bloo.bluelink.autolock

import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.distanceMilesTo

/**
 * Whether the phone is still beside the car, from a location fix: the one thing that should stop a lock after a walk was felt
 * (unloading the boot, children still getting out). Pure, so it is tested without a phone.
 *
 * It only ever HOLDS a lock back on good evidence. A fix that is old, vague, mocked or missing says nothing, and AutoLock then goes
 * ahead exactly as it would have without it: a missing fix must never be what leaves a car unlocked.
 */
internal object WalkAwayGeometry {
    /** Within this many metres of the car (beyond the fix's own uncertainty) counts as beside it. */
    const val BESIDE_M = 20.0

    /** A fix older than this says nothing about where the person is now. */
    const val MAX_FIX_AGE_MS = 2L * 60_000L

    /** A fix vaguer than this says nothing about a 20 m question. */
    const val MAX_ACCURACY_M = 50.0

    data class Fix(val lat: Double, val lon: Double, val accuracyM: Double, val ageMs: Long, val mock: Boolean)

    /** True when [fix] shows the phone within [BESIDE_M] of the car at [carLat]/[carLon], counting the fix's own uncertainty against it. */
    fun stillBesideCar(fix: Fix?, carLat: Double?, carLon: Double?): Boolean {
        if (fix == null || carLat == null || carLon == null) return false
        if (fix.mock || fix.ageMs > MAX_FIX_AGE_MS || fix.ageMs < 0 || fix.accuracyM > MAX_ACCURACY_M || fix.accuracyM < 0) return false
        val metres = GeoLocation(fix.lat, fix.lon).distanceMilesTo(GeoLocation(carLat, carLon)) * METRES_PER_MILE
        return metres <= BESIDE_M
    }

    private const val METRES_PER_MILE = 1609.344
}
