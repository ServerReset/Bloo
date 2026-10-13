package com.bloo.bluelink.data

/**
 * The glance text for a car, written ONCE. Every out-of-process surface needs the same words -- the
 * phone's homescreen widget, the watch's garage screen and the watch's tile -- and each had its own
 * copy of "Locked · Charging · 270 mi" and "76%", three chances to drift apart. Pure, so it is
 * unit-tested without a device.
 */

/** The one value a glance should show: charge/fuel %, else the lock state, else a dash. */
fun VehicleSnapshot.bigValue(): String =
    percent?.let { "$it%" } ?: locked?.let { if (it) "Locked" else "Unlocked" } ?: "—"

/**
 * The supporting line: lock state, charging and range, whichever the car reports, or null when it
 * reports none of them -- so a caller can leave the line out rather than show an empty one.
 */
fun VehicleSnapshot.stateLine(): String? = listOfNotNull(
    locked?.let { if (it) "Locked" else "Unlocked" }.takeIf { percent != null },
    if (charging == true) "Charging" else null,
    rangeMi?.let { "$it mi" },
).joinToString(" · ").ifBlank { null }

/** [stateLine], never blank: "Parked" when the car reports nothing (for a surface with a fixed slot). */
fun VehicleSnapshot.stateLineOrParked(): String = stateLine() ?: "Parked"

/** Minutes since this car's last fresh reading, or null when it has never had one. */
fun VehicleSnapshot.ageMinutes(now: Long = System.currentTimeMillis()): Long? =
    if (fetchedAt <= 0L) null else ((now - fetchedAt) / 60_000L).coerceAtLeast(0L)

/** "Just now" / "4 min ago" / "2 h ago" for the last reading, or null before there is one. */
fun VehicleSnapshot.updatedText(now: Long = System.currentTimeMillis()): String? =
    ageMinutes(now)?.let { m ->
        when {
            m < 1 -> "Just now"
            m < 60 -> "$m min ago"
            else -> "${m / 60} h ago"
        }
    }

/** Whether the last reading is old enough to doubt: over half an hour. */
fun VehicleSnapshot.isStale(now: Long = System.currentTimeMillis()): Boolean =
    fetchedAt > 0L && now - fetchedAt > 30 * 60_000L
