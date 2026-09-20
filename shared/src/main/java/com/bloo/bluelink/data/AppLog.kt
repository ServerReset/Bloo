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

    /**
     * The log itself: an ArrayDeque, so appending is O(1) and dropping the oldest is O(1).
     *
     * It used to be a `MutableStateFlow<List<String>>` that every [log] call replaced with
     * `(_lines.value + line).takeLast(MAX_LINES)` -- a full 500-element copy per line, on
     * every line, forever (startup alone logs a hundred or so). The log is a ring buffer;
     * copying the ring to append to it was pure allocation churn, and it landed on the
     * cold-start path this app is trying to keep cheap.
     *
     * Observers instead watch [version] (a counter that ticks on every change) and call
     * [snapshot] when it ticks, which is the only time a copy is actually needed.
     */
    private val buffer = ArrayDeque<String>()

    // Bumped on every mutation; private backing flow, public read-only view.
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

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
            buffer.addLast("${timestamp.format(Date())}  $message")
            while (buffer.size > MAX_LINES) buffer.removeFirst()
            _version.value += 1
        }
    }

    /** Resets the log to empty, e.g. when the user taps "clear" in Settings. */
    fun clear() {
        synchronized(this) {
            buffer.clear()
            _version.value += 1
        }
    }

    /** A point-in-time copy, for readers (Settings' logs card, the crash report). */
    fun snapshot(): List<String> = synchronized(this) { buffer.toList() }

    /** Line count without copying the log. */
    fun size(): Int = synchronized(this) { buffer.size }
}
