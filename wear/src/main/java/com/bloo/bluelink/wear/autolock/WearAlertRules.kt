package com.bloo.bluelink.wear

import com.bloo.bluelink.data.WatchNotifyPrefs

import com.bloo.bluelink.data.VehicleSnapshot

/** What the watch should do about one car after its state changed. */
internal data class WearAlertPlan(
    /** Show (or keep) the ongoing "charging" card; false means make sure it is gone. */
    val chargingCard: Boolean,
    val chargeComplete: Boolean,
    val lowBattery: Boolean,
)

internal const val WEAR_LOW_PERCENT = 20
internal const val WEAR_FULL_PERCENT = 95

/**
 * The watch's notification rules as one pure decision, so they can be tested without a watch:
 *  - the charging card is on while the car charges and the user wants it;
 *  - "charged" fires once, on the step from charging to not charging at or above [WEAR_FULL_PERCENT];
 *  - "low battery" fires once, on the step from at-or-above [WEAR_LOW_PERCENT] to below it, and only
 *    when the car isn't charging;
 *  - nothing but the card is ever raised for a car seen for the first time ([before] null), so
 *    opening the watch app never replays an old event.
 */
internal fun planWearAlerts(
    before: VehicleSnapshot?,
    now: VehicleSnapshot,
    prefs: WatchNotifyPrefs,
): WearAlertPlan {
    val pct = now.percent
    val card = now.charging == true && prefs.charging
    if (before == null) return WearAlertPlan(card, chargeComplete = false, lowBattery = false)
    // "Charged" means it reached the car's own limit (within a point, as on the phone), or near full when the car reports no limit:
    // a car set to stop at 80% never reached the old fixed 95%, so it was never told its charge was done.
    val fullAt = now.chargeLimitPct?.takeIf { it in 50..100 }?.minus(1) ?: WEAR_FULL_PERCENT
    val complete = prefs.chargeComplete && before.charging == true && now.charging != true &&
        pct != null && pct >= fullAt
    val was = before.percent
    val low = prefs.lowBattery && was != null && pct != null &&
        was >= WEAR_LOW_PERCENT && pct < WEAR_LOW_PERCENT && now.charging != true
    return WearAlertPlan(card, complete, low)
}
