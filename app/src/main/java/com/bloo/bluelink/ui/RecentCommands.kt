package com.bloo.bluelink.ui

import android.content.Context
import androidx.core.content.edit

/**
 * Tracks recently used commands for quick access in the search interface. Stores a list of command
 * IDs so the search can suggest recently used commands at the top of results.
 */
internal class RecentCommandsTracker(private val context: Context) {

    companion object {
        private const val PREF_NAME = "recent_commands"
        private const val RECENT_COMMANDS_KEY = "recent_ids"
        private const val RECENT_COMMANDS_LIMIT = 10
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /** Get recently used commands as a list of command IDs, most recent first. */
    fun recentCommands(): List<String> {
        val stored = prefs.getString(RECENT_COMMANDS_KEY, "") ?: ""
        if (stored.isEmpty()) return emptyList()
        return stored.split("|")
            .filter { it.isNotBlank() }
            .distinct()
            .take(RECENT_COMMANDS_LIMIT)
    }

    fun recordUsage(commandId: String) {
        val recent = recentCommands().toMutableList()
        // Remove if already exists and add to front
        recent.remove(commandId)
        recent.add(0, commandId)

        // Keep only the limit
        val updated = recent.take(RECENT_COMMANDS_LIMIT)
        prefs.edit { putString(RECENT_COMMANDS_KEY, updated.joinToString("|")) }
    }

    /** Clear all recent command history. */
    fun clear() {
        prefs.edit { clear() }
    }
}
