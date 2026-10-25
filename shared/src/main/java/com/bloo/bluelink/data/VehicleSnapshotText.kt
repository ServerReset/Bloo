package com.bloo.bluelink.data

/**
 * The glance text for a car, written ONCE. Every out-of-process surface needs the same words -- the
 * phone's homescreen widget, the watch's garage screen and the watch's tile -- and each had its own
 * copy of "Locked · Charging · 270 mi" and "76%", three chances to drift apart.
 *
 * The age line is [relativeLabel], the app's one "N min ago" (the phone's own chips use it too), so
 * there is not a second implementation hiding here. Pure, so it is unit-tested without a device.
 */

/** The one value a glance should show: charge/fuel %, else the lock state, else a dash. */
fun VehicleSnapshot.bigValue(): String =
    percent?.let { "$it%" } ?: locked?.let { if (it) "Locked" else "Unlocked" } ?: "-"

/**
 * The supporting line: lock state, charging and range, whichever the car reports, or null when it
 * reports none of them -- so a caller can leave the line out rather than show an empty one.
 */
fun VehicleSnapshot.stateLine(): String? = listOfNotNull(
    locked?.let { if (it) "Locked" else "Unlocked" }.takeIf { percent != null },
    if (charging == true) "Charging" else null,
    chargeLeftLabel()?.let { "$it left" },
    rangeMi?.let { "$it mi" },
).joinToString(" · ").ifBlank { null }

/** "1h 55m" while the car charges and says how long is left; null otherwise (a car that is not charging has no time to show). */
fun VehicleSnapshot.chargeLeftLabel(): String? =
    chargeMinutes?.takeIf { charging == true && it > 0 }?.let { fmtMinutes(it) }

/** [stateLine], never blank: "Parked" when the car reports nothing (for a surface with a fixed slot). */
fun VehicleSnapshot.stateLineOrParked(): String = stateLine() ?: "Parked"

/** How old this car's last fresh reading is before a glance should doubt it: half an hour. */
private const val STALE_GLANCE_MS = 30L * 60_000L

/** [relativeLabel] for this car's last reading, or null when it has never had one. */
fun VehicleSnapshot.updatedLabel(): String? = relativeLabel(fetchedAt).takeIf { it.isNotBlank() }

/** Whether the last reading is old enough to doubt: over half an hour. */
fun VehicleSnapshot.isStale(now: Long = System.currentTimeMillis()): Boolean =
    fetchedAt > 0L && now - fetchedAt > STALE_GLANCE_MS
