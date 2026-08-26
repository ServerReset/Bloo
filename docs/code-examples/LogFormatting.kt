/**
 * LogFormatting.kt - Log export utility functions
 *
 * Provides methods to format app logs in multiple output formats:
 * - Plain text (human-readable)
 * - CSV (spreadsheet-compatible)
 * - JSON (machine-readable)
 *
 * This is a reference implementation showing how log export could be
 * integrated into AppViewModel and the Logs UI in SettingsScreen.
 */

package com.bloo.bluelink.data

import java.text.SimpleDateFormat
import java.util.*

/**
 * Represents a single log entry with metadata.
 *
 * @param index Sequential position in log list (0-based)
 * @param timestamp ISO 8601 formatted timestamp (UTC)
 * @param category Log category (SYNC, GARAGE, COMMANDS, etc.)
 * @param message The log message text
 * @param level Log level (info, warning, error, debug)
 */
data class LogEntry(
    val index: Int,
    val timestamp: String,
    val category: String,
    val message: String,
    val level: String = "info"
)

/**
 * Utility functions for formatting application logs.
 *
 * Usage:
 *   val formatter = LogFormatter()
 *   val csvText = formatter.formatAsCSV(logs, timestamps, categories)
 *   clipboard.setText(AnnotatedString(csvText))
 */
object LogFormatter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val simpleTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    // ==================== Plain Text Format ====================

    /**
     * Format logs as plain text with optional timestamps.
     *
     * Example output:
     *   [07:30:15] Synced with Drive
     *   [07:30:20] Loaded vehicle: 2024 Hyundai Ioniq 5
     *
     * @param logs List of log messages
     * @param timestamps Optional list of timestamps (milliseconds since epoch)
     * @return Newline-separated log text
     */
    fun formatAsPlainText(logs: List<String>, timestamps: List<Long> = emptyList()): String {
        return if (timestamps.isNotEmpty() && timestamps.size == logs.size) {
            logs.zip(timestamps).joinToString("\n") { (log, time) ->
                val formatted = simpleTimeFormat.format(Date(time))
                "[$formatted] $log"
            }
        } else {
            logs.joinToString("\n")
        }
    }

    // ==================== CSV Format ====================

    /**
     * Format logs as CSV (Comma-Separated Values).
     * Compatible with Excel, Google Sheets, etc.
     *
     * Example output:
     *   Timestamp,Category,Message
     *   2026-08-26 07:30:15,SYNC,Synced with Drive
     *   2026-08-26 07:30:20,GARAGE,Loaded vehicle: 2024 Hyundai Ioniq 5
     *
     * @param logs List of log messages
     * @param timestamps Optional list of timestamps (milliseconds since epoch)
     * @param categories Optional list of log categories
     * @return CSV-formatted text
     */
    fun formatAsCSV(
        logs: List<String>,
        timestamps: List<Long> = emptyList(),
        categories: List<String> = emptyList()
    ): String {
        val header = "Timestamp,Category,Message"
        val rows = logs.mapIndexed { index, log ->
            val timestamp = if (timestamps.isNotEmpty() && index < timestamps.size) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamps[index]))
            } else "N/A"

            val category = if (categories.isNotEmpty() && index < categories.size) {
                categories[index]
            } else "APP"

            // CSV escape: wrap in quotes and double any internal quotes
            val escapedMessage = log.replace("\"", "\"\"")
            """$timestamp,"$category","$escapedMessage""""
        }

        return (listOf(header) + rows).joinToString("\n")
    }

    // ==================== JSON Format ====================

    /**
     * Format logs as JSON (JavaScript Object Notation).
     * Includes app metadata and structured entries.
     *
     * Example output:
     *   {
     *     "export": {
     *       "timestamp": "2026-08-26T07:30:15Z",
     *       "app_version": "1.2.3",
     *       "log_count": 2,
     *       "logs": [
     *         {"index": 0, "timestamp": "2026-08-26T07:30:15Z", ...},
     *         {"index": 1, "timestamp": "2026-08-26T07:30:20Z", ...}
     *       ]
     *     }
     *   }
     *
     * @param logs List of log messages
     * @param timestamps Optional list of timestamps
     * @param categories Optional list of log categories
     * @param appVersion App version string (defaults to "unknown")
     * @param deviceModel Device model name
     * @param androidVersion Android API level
     * @return JSON-formatted text
     */
    fun formatAsJSON(
        logs: List<String>,
        timestamps: List<Long> = emptyList(),
        categories: List<String> = emptyList(),
        appVersion: String = "unknown",
        deviceModel: String? = null,
        androidVersion: Int? = null
    ): String {
        val entries = logs.mapIndexed { index, log ->
            val timestamp = if (timestamps.isNotEmpty() && index < timestamps.size) {
                dateFormat.format(Date(timestamps[index]))
            } else "unknown"

            val category = if (categories.isNotEmpty() && index < categories.size) {
                categories[index]
            } else "APP"

            LogEntry(
                index = index,
                timestamp = timestamp,
                category = category,
                message = log
            )
        }

        // Build export structure
        val export = buildMap {
            put("timestamp", dateFormat.format(Date()))
            put("app_version", appVersion)
            if (deviceModel != null) put("device_model", deviceModel)
            if (androidVersion != null) put("android_version", androidVersion)
            put("log_count", logs.size)
            put("logs", entries.map { entry ->
                mapOf(
                    "index" to entry.index,
                    "timestamp" to entry.timestamp,
                    "category" to entry.category,
                    "message" to entry.message,
                    "level" to entry.level
                )
            })
        }

        return buildJsonString(mapOf("export" to export))
    }

    // ==================== Utility Functions ====================

    /**
     * Simple JSON builder (replacement for kotlinx.serialization in cases where
     * it's not available, or for demonstration purposes).
     *
     * Production code should use kotlinx.serialization or similar.
     */
    private fun buildJsonString(obj: Any?, indent: Int = 0): String {
        val indentStr = "  ".repeat(indent)
        val nextIndentStr = "  ".repeat(indent + 1)

        return when (obj) {
            is Map<*, *> -> {
                val entries = obj.entries.joinToString(",\n$nextIndentStr") { (key, value) ->
                    "\"$key\": ${buildJsonString(value, indent + 1)}"
                }
                if (entries.isEmpty()) "{}" else "{\n$nextIndentStr$entries\n$indentStr}"
            }
            is List<*> -> {
                val entries = obj.joinToString(",\n$nextIndentStr") { value ->
                    buildJsonString(value, indent + 1)
                }
                if (entries.isEmpty()) "[]" else "[\n$nextIndentStr$entries\n$indentStr]"
            }
            is String -> "\"${obj.replace("\"", "\\\"")}\""
            is Number -> obj.toString()
            is Boolean -> obj.toString()
            null -> "null"
            else -> "\"${obj.toString().replace("\"", "\\\"")}\""
        }
    }

    /**
     * Detect appropriate log category from message content.
     * Can be used to auto-categorize logs that don't have explicit categories.
     *
     * @param message Log message text
     * @return Detected category or "APP" as default
     */
    fun detectCategory(message: String): String {
        return when {
            message.contains("Sync", ignoreCase = true) -> "SYNC"
            message.contains("Drive", ignoreCase = true) -> "SYNC"
            message.contains("vehicle", ignoreCase = true) -> "GARAGE"
            message.contains("garage", ignoreCase = true) -> "GARAGE"
            message.contains("climate", ignoreCase = true) -> "CLIMATE"
            message.contains("charge", ignoreCase = true) -> "CHARGE"
            message.contains("lock", ignoreCase = true) -> "COMMANDS"
            message.contains("unlock", ignoreCase = true) -> "COMMANDS"
            message.contains("command", ignoreCase = true) -> "COMMANDS"
            message.contains("error", ignoreCase = true) -> "ERROR"
            message.contains("failed", ignoreCase = true) -> "ERROR"
            message.contains("timeout", ignoreCase = true) -> "ERROR"
            message.contains("login", ignoreCase = true) -> "AUTH"
            message.contains("logout", ignoreCase = true) -> "AUTH"
            message.contains("token", ignoreCase = true) -> "AUTH"
            else -> "APP"
        }
    }

    /**
     * Sanitize log message for safe export.
     * Removes or escapes potentially problematic characters.
     *
     * @param message Log message to sanitize
     * @return Sanitized message
     */
    fun sanitizeMessage(message: String): String {
        return message
            .replace(" ", "")  // Remove null bytes
            .replace("\r\n", "\n")  // Normalize line endings
            .replace("\r", "\n")
            .trimEnd()
    }
}

// ==================== Integration Examples ====================

/**
 * Example: How to integrate log formatting into AppViewModel
 *
 * class AppViewModel(...) : ViewModel() {
 *     private val _logs = MutableStateFlow<List<String>>(emptyList())
 *     private val _logTimestamps = MutableStateFlow<List<Long>>(emptyList())
 *     private val _logCategories = MutableStateFlow<List<String>>(emptyList())
 *
 *     val logs = _logs.asStateFlow()
 *
 *     fun exportLogsAsCSV(): String {
 *         return LogFormatter.formatAsCSV(
 *             _logs.value,
 *             _logTimestamps.value,
 *             _logCategories.value
 *         )
 *     }
 *
 *     fun exportLogsAsJSON(): String {
 *         return LogFormatter.formatAsJSON(
 *             _logs.value,
 *             _logTimestamps.value,
 *             _logCategories.value,
 *             appVersion = BuildConfig.VERSION_NAME
 *         )
 *     }
 * }
 */

/**
 * Example: How to integrate into SettingsScreen Logs UI
 *
 * PopVisible(logsExpanded) {
 *     Row {
 *         MorphTextButton("Copy", onClick = {
 *             val text = LogFormatter.formatAsPlainText(logs)
 *             clipboard.setText(AnnotatedString(text))
 *         })
 *         MorphTextButton("CSV", onClick = {
 *             val text = LogFormatter.formatAsCSV(logs)
 *             clipboard.setText(AnnotatedString(text))
 *         })
 *         MorphTextButton("JSON", onClick = {
 *             val text = LogFormatter.formatAsJSON(logs)
 *             clipboard.setText(AnnotatedString(text))
 *         })
 *         MorphTextButton("Clear", onClick = { vm.clearLogs() })
 *     }
 * }
 */
