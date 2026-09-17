package com.bloo.bluelink.ui

import android.content.Context

/**
 * Tracks recently used commands for quick access in the search interface.
 * Stores a list of command IDs so the search can suggest recently used commands
 * at the top of results.
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

    /**
     * Get recently used commands as a list of command IDs, most recent first.
     */
    fun recentCommands(): List<String> {
        val stored = prefs.getString(RECENT_COMMANDS_KEY, "") ?: ""
        if (stored.isEmpty()) return emptyList()
        return stored.split("|")
            .filter { it.isNotBlank() }
            .distinct()
            .take(RECENT_COMMANDS_LIMIT)
    }

    /**
     * Record that a command was just used.
     */
    fun recordUsage(commandId: String) {
        val recent = recentCommands().toMutableList()
        // Remove if already exists and add to front
        recent.remove(commandId)
        recent.add(0, commandId)

        // Keep only the limit
        val updated = recent.take(RECENT_COMMANDS_LIMIT)
        prefs.edit().putString(RECENT_COMMANDS_KEY, updated.joinToString("|")).apply()
    }

    /**
     * Clear all recent command history.
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
}

// List<CommandMetadata>.sortByRecency() and suggestCommandsWithRecency() were deleted here,
// both with zero call sites anywhere in the repo.
//
// They were the READ half of this file, and it was never wired up: the only thing that has
// ever touched [RecentCommandsTracker] is SearchResults' recordUsage() call after a command
// actually runs, i.e. the WRITE half. Command suggestions there come straight off
// searchCommands' own relevance ranking, with no recency pass over them at all -- so these two
// re-ranked a list nobody handed them, against a history nobody read.
//
// The tracker itself stays: it is live, it is what records the history, and reading that
// history back is a feature someone may still want. That is a handful of lines against
// `recentCommands()` when it happens, not these two speculative rankers -- one of which
// (sortByRecency) is a stable-sort ordering the other (suggestCommandsWithRecency) reimplements
// by partition, which is itself the sign that neither had a real caller to agree with.
