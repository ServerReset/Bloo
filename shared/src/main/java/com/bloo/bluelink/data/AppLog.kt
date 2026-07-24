package com.bloo.bluelink.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight in-memory log shown in Settings so the user can copy/paste
 * activity (network calls, commands, errors). Capped to a ring buffer.
 */
object AppLog {

    private const val MAX_LINES = 500
    // Single shared formatter reused across all log() calls; SimpleDateFormat is not
    // thread-safe, so all access to it happens inside the `synchronized(this)` block
    // below to avoid concurrent formatting from multiple threads corrupting state.
    private val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US)

    // Backing mutable flow is private; the public `lines` exposes a read-only view so
    // observers (Settings screen) can collect it but can't push new values directly.
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    /**
     * Appends a timestamped line to the in-memory log.
     *
     * Builds a brand-new immutable list each call (`_lines.value + line`) rather than
     * mutating in place, since StateFlow requires distinct value instances to notify
     * collectors. `takeLast(MAX_LINES)` trims the oldest entries once the buffer grows
     * past the cap, keeping this a bounded ring buffer instead of growing unbounded.
     * The `synchronized` block guards the format-then-publish sequence so concurrent
     * callers from different threads don't interleave and drop each other's lines.
     */
    fun log(message: String) {
        synchronized(this) {
            val line = "${timestamp.format(Date())}  $message"
            val next = (_lines.value + line).takeLast(MAX_LINES)
            _lines.value = next
        }
    }

    /** Resets the log to empty, e.g. when the user taps "clear" in Settings. */
    fun clear() {
        _lines.value = emptyList()
    }
}
