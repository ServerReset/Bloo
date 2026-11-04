package com.bloo.bluelink.data

import java.util.Locale
import kotlin.math.roundToInt

/** How long before a departure the car starts warming. */
const val DEPARTURE_LEAD_MIN = 30

/** How often the background check runs. Android may run a periodic check late, so a departure is due for longer than that. */
const val DEPARTURE_CHECK_MIN = 15

/**
 * How long, from [DEPARTURE_LEAD_MIN] before a departure, a check still starts it. Wider than the check period so a late check (Doze, a
 * busy phone) still starts the car; the once-a-day record stops a second start.
 */
const val DEPARTURE_WINDOW_MIN = 60

/** A departure: a time the car should be ready, every day, switched on or off. Smart climate sets what it should be. */
data class DepartureSlot(val enabled: Boolean = false, val minuteOfDay: Int = 8 * 60) {
    fun encode(): String = "${if (enabled) 1 else 0},$minuteOfDay"

    companion object {
        fun decode(text: String): DepartureSlot? {
            val p = text.split(",")
            if (p.size != 2) return null
            val on = p[0].toIntOrNull() ?: return null
            val minute = p[1].toIntOrNull() ?: return null
            return DepartureSlot(enabled = on == 1, minuteOfDay = minute.coerceIn(0, 1439))
        }
    }
}

/** A car's two departures. Stored as one string, so the synced settings carry it. */
data class DepartureSchedule(val slots: List<DepartureSlot> = listOf(DepartureSlot(), DepartureSlot(minuteOfDay = 17 * 60))) {
    fun encode(): String = slots.joinToString(";") { it.encode() }

    /**
     * The index of the departure that should start now, or null. A departure is due from [DEPARTURE_LEAD_MIN] before its time until
     * [DEPARTURE_WINDOW_MIN] after that start, and only once per day ([lastRun] holds each slot's last start day).
     */
    fun dueSlot(nowMinute: Int, epochDay: Long, lastRun: List<Long>): Int? {
        slots.forEachIndexed { i, s ->
            if (!s.enabled) return@forEachIndexed
            val start = s.minuteOfDay - DEPARTURE_LEAD_MIN
            val into = ((nowMinute - start) % 1440 + 1440) % 1440
            if (into < DEPARTURE_WINDOW_MIN && lastRun.getOrElse(i) { -1L } != epochDay) return i
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
