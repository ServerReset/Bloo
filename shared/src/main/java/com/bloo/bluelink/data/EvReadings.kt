package com.bloo.bluelink.data

import kotlinx.serialization.json.JsonElement

/**
 * The two EV readings the Canada and Kia US status parsers both build the same way. The brands differ in where each value sits in their
 * JSON and in units, so each parser finds its own value and hands it here. Shared so the record shape is written once.
 */

/** An electric range reading in the car's own distance unit: the value and the unit it is in, as one [DrvDistance]. */
internal fun electricRange(range: JsonElement?, toMiles: (Double) -> Double = { it }): List<DrvDistance> {
    if (range == null) return emptyList()
    val value = range.path("value").dbl()?.let(toMiles) ?: return emptyList()
    return listOf(DrvDistance(RangeByFuel(Dte(value, range.path("unit").int()))))
}

/** The current-plug and AC/DC charge-time estimates, in minutes, or null when the car reports none of them. */
internal fun remainingChargeTime(atc: Double?, etc1: Double?, etc3: Double?): RemainTime2? =
    RemainTime2(
        atc = atc?.let { TimeValue(it, 1) },
        etc1 = etc1?.let { TimeValue(it, 1) },
        etc3 = etc3?.let { TimeValue(it, 1) },
    ).takeIf { it.atc != null || it.etc1 != null || it.etc3 != null }
