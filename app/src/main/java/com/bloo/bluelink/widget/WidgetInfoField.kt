package com.bloo.bluelink.widget

/**
 * A stat that can appear on an "info" mode widget below 3×3, selected the same
 * way [WidgetAction] buttons are for "controls" mode -- each tile tier checks
 * membership for the specific fields it has room for (see BlooWidget's
 * InfoTile/TallNarrowTile). 3×3-and-up widgets (LargeTile) ignore this
 * entirely and always show everything -- there's room for all of it, so a
 * picker would just be one more thing to configure for no visual gain.
 */
enum class WidgetInfoField(val key: String, val label: String) {
    NAME("name", "Car name"),
    PERCENT("percent", "Battery/fuel %"),
    RANGE("range", "Range"),
    LOCK("lock", "Lock status"),
    MODEL("model", "Model");

    companion object {
        /** All defined fields, in declaration order -- used to populate the field picker UI. */
        val ALL = entries

        /** Looks up a field by its persisted [key] string; returns null (not a crash) if the
         *  stored key doesn't match any current field, e.g. after a field is renamed/removed. */
        fun fromKey(key: String): WidgetInfoField? = entries.firstOrNull { it.key == key }

        /** Matches what the fixed layouts already showed before this was
         *  selectable, so upgrading doesn't change anyone's existing widget. */
        val DEFAULTS = listOf(NAME, PERCENT, RANGE, LOCK)
    }
}
