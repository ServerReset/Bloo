package com.bloo.bluelink.data

import java.util.Locale
import kotlin.math.roundToInt

/** Weekdays as bits: bit 0 is Monday, bit 6 is Sunday. */
const val DAYS_WEEKDAYS = 0b0011111
const val DAYS_EVERY = 0b1111111

/** How long before a departure the car starts warming. */
const val DEPARTURE_LEAD_MIN = 30

/** How often the background check runs. A departure is started in the first check window it falls into. */
const val DEPARTURE_CHECK_MIN = 15

/** One departure: the time the car should be ready, on which days, and what it should be. */
data class DepartureSlot(
    val enabled: Boolean = false,
    val minuteOfDay: Int = 8 * 60,
    val days: Int = DAYS_WEEKDAYS,
    /** True: the car takes smart climate's target for the weather. False: [targetF] as set. */
    val smart: Boolean = true,
    val targetF: Int = 72,
    val seats: Boolean = false,
    val wheel: Boolean = false,
    val defrost: Boolean = false,
) {
    fun encode(): String = listOf(enabled, minuteOfDay, days, smart, targetF, seats, wheel, defrost)
        .joinToString(",") { if (it is Boolean) (if (it) "1" else "0") else it.toString() }

    companion object {
        fun decode(text: String): DepartureSlot? {
            val p = text.split(",")
            if (p.size != 8) return null
            val ints = p.map { it.toIntOrNull() ?: return null }
            return DepartureSlot(
                enabled = ints[0] == 1, minuteOfDay = ints[1].coerceIn(0, 1439), days = ints[2] and DAYS_EVERY, smart = ints[3] == 1,
                targetF = ints[4].coerceIn(50, 90), seats = ints[5] == 1, wheel = ints[6] == 1, defrost = ints[7] == 1,
            )
        }
    }
}

/** A car's two departures. Stored as one string, so the synced settings carry it. */
data class DepartureSchedule(val slots: List<DepartureSlot> = listOf(DepartureSlot(), DepartureSlot(minuteOfDay = 17 * 60, days = DAYS_WEEKDAYS))) {
    fun encode(): String = slots.joinToString(";") { it.encode() }

    /**
     * The index of the departure that should start now, or null. A departure is due in the check window that begins
     * [DEPARTURE_LEAD_MIN] before its time, on a day it is set for, and only once per day ([lastRun] holds each slot's last start day).
     */
    fun dueSlot(nowMinute: Int, weekday: Int, epochDay: Long, lastRun: List<Long>): Int? {
        slots.forEachIndexed { i, s ->
            if (!s.enabled || (s.days and (1 shl weekday)) == 0) return@forEachIndexed
            val start = s.minuteOfDay - DEPARTURE_LEAD_MIN
            val into = ((nowMinute - start) % 1440 + 1440) % 1440
            if (into < DEPARTURE_CHECK_MIN && lastRun.getOrElse(i) { -1L } != epochDay) return i
        }
        return null
    }

    companion object {
        fun decode(text: String): DepartureSchedule {
            val parts = text.split(";")
            if (parts.size != 2) return DepartureSchedule()
            val a = DepartureSlot.decode(parts[0]) ?: return DepartureSchedule()
            val b = DepartureSlot.decode(parts[1]) ?: return DepartureSchedule()
            return DepartureSchedule(listOf(a, b))
        }
    }
}

/** One climate start the person made: the day, the weekday (0 Monday), the minute of the day, and the outside °F. */
data class StartRecord(val epochDay: Long, val weekday: Int, val minute: Int, val ambientF: Int) {
    fun encode(): String = "$epochDay:$weekday:$minute:$ambientF"

    companion object {
        fun decode(text: String): StartRecord? {
            val p = text.split(":")
            if (p.size != 4) return null
            return StartRecord(p[0].toLongOrNull() ?: return null, p[1].toIntOrNull() ?: return null, p[2].toIntOrNull() ?: return null, p[3].toIntOrNull() ?: return null)
        }

        /** The most recent records are kept, so the history follows how the person lives now. */
        const val KEPT = 60

        fun encodeAll(records: List<StartRecord>): String = records.takeLast(KEPT).joinToString(";") { it.encode() }

        fun decodeAll(text: String): List<StartRecord> = text.split(";").mapNotNull { decode(it) }
    }
}

/** How many starts on a weekday are needed before a time is suggested. */
private const val SUGGEST_MIN_STARTS = 5

/** The spread of starts, in minutes (middle half), beyond which the times are too scattered to predict. */
private const val SUGGEST_MAX_SPREAD_MIN = 120

/**
 * The departure time to suggest for [weekday], from when the person usually starts the car on that weekday: the typical start, plus the
 * lead time to leave, rounded to a quarter hour. Null when there are too few starts, or they are too scattered to predict.
 */
fun suggestDeparture(records: List<StartRecord>, weekday: Int): Int? {
    val minutes = records.filter { it.weekday == weekday }.map { it.minute }.sorted()
    if (minutes.size < SUGGEST_MIN_STARTS) return null
    val q1 = minutes[minutes.size / 4]
    val q3 = minutes[minutes.size * 3 / 4]
    if (q3 - q1 > SUGGEST_MAX_SPREAD_MIN) return null
    val typical = minutes[minutes.size / 2] + 15
    return ((typical / 15.0).roundToInt() * 15).coerceIn(0, 1439)
}

/** A minute of the day as a clock time, "07:30". */
fun formatMinuteOfDay(minute: Int): String = String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60)
