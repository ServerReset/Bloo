package com.bloo.bluelink.data

/** Minutes in a day. */
private const val DAY_MIN = 24 * 60

/**
 * A clock time named in a phrase, as a minute of the day, or null when the phrase names none.
 *
 * It takes "9", "9pm", "9 p.m.", "9:30pm", "21:00", "at 9", "for 9:15", "noon" and "midnight". A bare number counts as a time only after
 * "at", "for", "by", "until" or "till", so a temperature such as "72 degrees" is never read as one. A bare hour with no am or pm is
 * read the way a person means it: 1 to 6 is the afternoon or evening, 7 to 11 the morning, 12 noon, and 13 to 23 the 24-hour clock.
 */
fun parseClockMinute(text: String): Int? {
    val q = text.lowercase()
        .replace("a.m.", "am").replace("p.m.", "pm").replace("o'clock", " ")
    if (Regex("\\bnoon\\b").containsMatchIn(q)) return 12 * 60
    if (Regex("\\bmidnight\\b").containsMatchIn(q)) return 0
    // The first number that reads as a time; a stray number ("lock 2 cars at 9") is passed over.
    for (m in RxClockPhrase.findAll(q)) {
        val prefix = m.groups[1]?.value
        val hour = m.groups[2]!!.value.toInt()
        val hasColon = m.groups[3] != null
        val minute = m.groups[3]?.value?.toInt() ?: 0
        val meridiem = m.groups[4]?.value
        // A colon or a meridiem is itself a sign of a time; a bare number needs a word like "at" before it.
        if (prefix == null && !hasColon && meridiem == null) continue
        if (minute >= 60) continue
        val h = when (meridiem) {
            "am" -> if (hour in 1..12) hour % 12 else continue
            "pm" -> if (hour in 1..12) hour % 12 + 12 else continue
            else -> when (hour) {
                0, in 13..23 -> hour
                in 1..6 -> hour + 12
                in 7..11 -> hour
                12 -> 12
                else -> continue
            }
        }
        return h * 60 + minute
    }
    return null
}

private val RxClockPhrase = Regex("""\b(at|for|by|until|till)?\s*\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""")

/** Minutes from [nowMinute] until the next [targetMinute], 0 to 1439: a time already past today means tomorrow. */
fun minutesUntil(nowMinute: Int, targetMinute: Int): Int = ((targetMinute - nowMinute) % DAY_MIN + DAY_MIN) % DAY_MIN
