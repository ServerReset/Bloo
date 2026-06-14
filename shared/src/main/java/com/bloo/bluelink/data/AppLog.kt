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
    private val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun log(message: String) {
        val line = "${timestamp.format(Date())}  $message"
        synchronized(this) {
            val next = (_lines.value + line).takeLast(MAX_LINES)
            _lines.value = next
        }
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
