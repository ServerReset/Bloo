@file:OptIn(ExperimentalMaterial3Api::class)

package com.bloo.bluelink.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.vector.ImageVector
import com.bloo.bluelink.data.Vehicle

/**
 * Command index for the search system: intent-based commands that execute
 * actions on vehicles or app state, distinct from settings searches.
 *
 * Commands are organized by category and support multiple natural language
 * variations for each action. The search system can:
 * - Recognize commands by partial matching and synonyms
 * - Execute commands through TileCommandRunner
 * - Track recently used commands for quick access
 * - Provide suggestions based on vehicle capabilities
 *
 * This complements SettingsIndex (which searches settings by name/keywords)
 * by making the search bar function as a command palette for vehicle actions.
 */

internal enum class CommandCategory {
    LOCK, CHARGING, CLIMATE, LIGHTS, HORN, ENGINE, TRUNK, INFO
}

/**
 * Metadata for one executable command: the action it performs, its aliases,
 * category, and whether it applies to a specific vehicle or the whole app.
 */
internal data class CommandMetadata(
    val id: String,
    val title: String,
    val description: String,
    val category: CommandCategory,
    val icon: ImageVector,
    val keywords: String,
    val aliases: List<String> = emptyList(),
    val requiresArg: Boolean = false,
    val argLabel: String = "",
    /** Vehicle-specific or app-wide */
    val perVehicle: Boolean = true,
    /** Optional: whether this command applies to a given vehicle */
    val isAvailable: (Vehicle) -> Boolean = { true },
)

/** All available commands in the system, organized for searching and presentation. */
internal val CommandCatalog = listOf(
    // LOCK COMMANDS
    CommandMetadata(
        id = "lock",
        title = "Lock",
        description = "Lock the car doors",
        category = CommandCategory.LOCK,
        icon = Icons.Filled.Lock,
        keywords = "lock secure close",
        aliases = listOf("lock up", "lock the car", "secure the car"),
    ),
    CommandMetadata(
        id = "unlock",
        title = "Unlock",
        description = "Unlock the car doors",
        category = CommandCategory.LOCK,
        icon = Icons.Filled.Lock,
        keywords = "unlock open let in",
        aliases = listOf("unlock the car", "open the car", "let me in"),
    ),

    // CHARGING COMMANDS
    CommandMetadata(
        id = "charge_on",
        title = "Start charging",
        description = "Begin charging the battery",
        category = CommandCategory.CHARGING,
        icon = Icons.Filled.Bolt,
        keywords = "start charging begin charge on",
        aliases = listOf("start charge", "charge the car", "plug in"),
        isAvailable = { it.canCharge() },
    ),
    CommandMetadata(
        id = "charge_off",
        title = "Stop charging",
        description = "Stop the battery charging",
        category = CommandCategory.CHARGING,
        icon = Icons.Filled.Bolt,
        keywords = "stop charging end unplug halt",
        aliases = listOf("stop charge", "unplug the car", "cancel charging"),
        isAvailable = { it.canCharge() },
    ),
    CommandMetadata(
        id = "charge_limit",
        title = "Set charge limit",
        description = "Set the target charge percentage",
        category = CommandCategory.CHARGING,
        icon = Icons.Filled.Bolt,
        keywords = "charge limit set target percent",
        aliases = listOf("charge to", "set charge target"),
        requiresArg = true,
        argLabel = "percentage (20-100)",
        isAvailable = { it.canCharge() },
    ),

    // CLIMATE COMMANDS
    CommandMetadata(
        id = "climate_on",
        title = "Start climate",
        description = "Start the climate control system",
        category = CommandCategory.CLIMATE,
        icon = Icons.Filled.AcUnit,
        keywords = "start climate ac heat cool",
        aliases = listOf(
            "start ac", "turn on climate", "start heating", "start cooling",
            "warm up the car", "cool down the car", "turn on ac"
        ),
    ),
    CommandMetadata(
        id = "climate_off",
        title = "Stop climate",
        description = "Stop the climate control",
        category = CommandCategory.CLIMATE,
        icon = Icons.Filled.AcUnit,
        keywords = "stop climate ac heat cool off",
        aliases = listOf("stop ac", "turn off climate", "turn off heat"),
    ),
    CommandMetadata(
        id = "climate_smart",
        title = "Smart climate",
        description = "Start climate based on current weather",
        category = CommandCategory.CLIMATE,
        icon = Icons.Filled.AcUnit,
        keywords = "smart climate smart ac",
        aliases = listOf("start smart climate", "smart climate on"),
    ),
    CommandMetadata(
        id = "defrost",
        title = "Defrost",
        description = "Clear windows and defrost the windscreen",
        category = CommandCategory.CLIMATE,
        icon = Icons.Filled.AcUnit,
        keywords = "defrost demist clear windscreen deice",
        aliases = listOf("defog windows", "clear ice", "defog the car"),
    ),

    // LIGHTS COMMANDS
    CommandMetadata(
        id = "lights",
        title = "Flash lights",
        description = "Flash the headlights",
        category = CommandCategory.LIGHTS,
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        keywords = "flash lights blink headlights",
        aliases = listOf("flash headlights", "blink lights", "lights on"),
    ),

    // HORN COMMANDS
    CommandMetadata(
        id = "horn",
        title = "Sound horn",
        description = "Sound the car horn and flash lights",
        category = CommandCategory.HORN,
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        keywords = "horn honk beep sound",
        aliases = listOf("honk", "beep", "find my car"),
    ),

    // ENGINE COMMANDS (future expansion)
    CommandMetadata(
        id = "engine_start",
        title = "Start engine",
        description = "Remote start the engine",
        category = CommandCategory.ENGINE,
        icon = Icons.Filled.Bolt,
        keywords = "start engine begin run fire up",
        aliases = listOf("start the car", "remote start"),
    ),
    CommandMetadata(
        id = "engine_stop",
        title = "Stop engine",
        description = "Stop the running engine",
        category = CommandCategory.ENGINE,
        icon = Icons.Filled.Bolt,
        keywords = "stop engine kill turn off",
        aliases = listOf("stop the car"),
    ),

    // TRUNK COMMANDS (future expansion)
    CommandMetadata(
        id = "trunk_open",
        title = "Open trunk",
        description = "Open the trunk/boot",
        category = CommandCategory.TRUNK,
        icon = Icons.Filled.Lock,
        keywords = "open trunk pop boot",
        aliases = listOf("open the trunk", "pop the trunk"),
    ),
)

/** The command index's own word splitter, compiled once -- the sibling of SettingsIndex.kt's
 *  [RxSearchTokens] (which is the same pattern plus `%`, and is what tokenises the QUERY side
 *  below). Separate rather than shared because the two genuinely differ: a percent sign is
 *  meaningful in a typed query ("charge to 80%") and is not a word character in a command's
 *  own title or aliases. */
private val RxCommandWords = Regex("[^a-z0-9]+")

/**
 * Command search scoring: how well a command matches a query.
 * Uses partial matching on title, keywords, and aliases.
 */
internal fun commandSearchScore(query: String, command: CommandMetadata, fuzzy: Boolean = false): Int? {
    val q = query.lowercase()
    val titleLower = command.title.lowercase()

    // Check exact matches and prefix matches first
    if (titleLower == q) return 1000
    if (titleLower.startsWith(q)) return 800

    // Check keywords
    val keywordsLower = command.keywords.lowercase()
    if (q in keywordsLower) return 600
    if (keywordsLower.startsWith(q)) return 500

    // Check aliases
    for (alias in command.aliases) {
        val aliasLower = alias.lowercase()
        if (aliasLower == q) return 750
        if (aliasLower.startsWith(q)) return 700
        if (q in aliasLower) return 550
    }

    // Partial matching
    if (q in titleLower) return 400
    for (alias in command.aliases) {
        if (q in alias.lowercase()) return 350
    }

    // Word-boundary matching (e.g., "start" matches "start climate" but not "restart")
    // RxCommandWords, not a fresh Regex(...) here: this is the innermost loop of the command
    // search -- once per catalog entry per query token, so ~17 x n per keystroke -- and
    // `Regex(...)` parses its pattern and builds a matcher on every CONSTRUCTION. Exactly the
    // cost SettingsIndex.kt's own Rx* block was extracted to kill on the settings half of the
    // same search bar; this file's copy of the same splitter had simply never been updated
    // with it. See that block's doc for the full reasoning and for why file scope (the pattern
    // is constant) rather than a `remember`.
    val words = (command.title + " " + command.keywords + " " + command.aliases.joinToString(" "))
        .lowercase()
        .split(RxCommandWords)

    for (word in words) {
        if (word.startsWith(q) && word.length > q.length) return 300
    }

    // Fuzzy matching as fallback
    if (fuzzy) {
        if (hasFuzzyWord(titleLower, q)) return 150
        if (hasFuzzyWord(keywordsLower, q)) return 100
    }

    return null
}

/**
 * Get commands available for the given vehicles.
 * Filters out commands that aren't available on any of the vehicles.
 */
internal fun getAvailableCommands(vehicles: List<Vehicle>): List<CommandMetadata> {
    if (vehicles.isEmpty()) return emptyList()

    return CommandCatalog.filter { command ->
        // Command is available if it's not per-vehicle, or if it's available on at least one vehicle
        !command.perVehicle || vehicles.any { command.isAvailable(it) }
    }
}

/**
 * Get commands that match the query, sorted by relevance.
 */
internal fun searchCommands(query: String, vehicles: List<Vehicle>, fuzzy: Boolean = false): List<CommandMetadata> {
    val available = getAvailableCommands(vehicles)
    // RxSearchTokens (SettingsIndex.kt), not a fresh Regex: this is the exact same pattern,
    // for the exact same job, as the settings half of this one search bar -- it had been
    // re-declared inline here, so the file-scope compile that fixed it there never applied on
    // this path at all.
    val tokens = query.lowercase().split(RxSearchTokens)
        .filter { it.isNotBlank() && it !in SearchStopwords }

    if (tokens.isEmpty()) return available

    val scored = available.mapNotNull { cmd ->
        // All tokens must match (AND logic, same as settings search)
        var totalScore = 0
        for (token in tokens) {
            val score = commandSearchScore(token, cmd, fuzzy) ?: return@mapNotNull null
            totalScore += score
        }
        cmd to totalScore
    }

    return scored.sortedByDescending { it.second }.map { it.first }
}

/**
 * Extension function to check if a vehicle supports charging.
 * This is a simplified check; the real app would check the powertrain/capabilities.
 */
internal fun Vehicle.canCharge(): Boolean {
    // In a real implementation, check the vehicle's powertrain (EV, Hybrid, PHEV)
    // For now, assume all vehicles can charge (the actual command will fail appropriately)
    return true
}

// commandCategoryIcon() and CommandCategory.displayName() were deleted here, both with zero
// call sites anywhere in the repo.
//
// commandCategoryIcon was a second, parallel icon mapping for something the data already
// carries: every [CommandMetadata] declares its OWN `icon`, which is what the one place that
// draws a command suggestion (SearchResults' `cmd.icon`) reads -- and a per-CATEGORY icon is
// necessarily coarser than the per-command one, so it could only ever have disagreed with it
// (LOCK and TRUNK both resolving to a padlock, HORN to a speaker). displayName was a set of
// category headings for a grouped command palette that was never built; commands render as a
// flat ranked list, the same as settings results.
//
// [CommandCategory] itself and [CommandMetadata.category] stay: the enum is the catalog's own
// declared taxonomy and every entry sets it, so it is real (if currently unread) data, not a
// leftover. It is only these two never-wired PRESENTATION mappings that go.
