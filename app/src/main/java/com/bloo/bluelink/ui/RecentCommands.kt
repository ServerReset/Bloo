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

/**
 * Sort commands by recency, putting recently used ones first.
 */
internal fun List<CommandMetadata>.sortByRecency(recentIds: List<String>): List<CommandMetadata> {
    val recentMap = recentIds.withIndex().associate { it.value to it.index }
    return sortedBy { cmd ->
        recentMap[cmd.id] ?: recentIds.size // Recently used first, then the rest
    }
}

/**
 * Get suggested commands based on recency and match score.
 */
internal fun suggestCommandsWithRecency(
    query: String,
    vehicle: com.bloo.bluelink.data.Vehicle,
    recentIds: List<String>,
    limit: Int = 5
): List<CommandMetadata> {
    val allSuggested = searchCommands(query, listOf(vehicle), fuzzy = false)

    // Prioritize recent commands that match
    val recent = allSuggested.filter { it.id in recentIds }
    val others = allSuggested.filter { it.id !in recentIds }

    return (recent + others).take(limit)
}
