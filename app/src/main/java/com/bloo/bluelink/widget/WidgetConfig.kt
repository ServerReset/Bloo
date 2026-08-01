package com.bloo.bluelink.widget

/**
 * Per-widget configuration for the adaptive home-screen car widget.
 *
 * One [WidgetConfig] exists per placed widget instance (keyed by its appWidgetId
 * in [WidgetConfigStore]). It is deliberately DEVICE-LOCAL — it is NOT part of the
 * Drive settings backup and never roams to another device, because a widget lives
 * on one launcher on one phone and its layout/car binding is meaningless anywhere
 * else.
 *
 * The config is intentionally small and every field has a sensible default, so a
 * freshly-dropped widget renders something useful before the user ever opens the
 * config screen. The config *screen* (WidgetConfigActivity) shows more or fewer of
 * these knobs depending on the app's simple/advanced mode — but the underlying
 * model is the same either way; advanced mode just exposes more of it.
 */
data class WidgetConfig(
    /** VIN this widget is pinned to, or null to follow the app's currently-selected
     *  car (so a single "follow" widget tracks whatever the user last looked at). */
    val vin: String? = null,
    /** Ordered action buttons to show (subject to available size). Keys from [WidgetAction]. */
    val actions: List<String> = WidgetAction.DEFAULTS,
    /** Ordered glanceable info fields to show (subject to size). Keys from [WidgetInfoField]. */
    val infoFields: List<String> = WidgetInfoField.DEFAULTS,
    /** Whether to draw the charge/fuel status ring when there's room for it. */
    val showRing: Boolean = true,
    /** Whether to show the location map thumbnail on large sizes (advanced-only knob). */
    val showMap: Boolean = false,
    /** Semantic accent override: one of [WidgetAccent] keys, or null = follow theme primary. */
    val accent: String? = null,
    /** Theme override for this widget: "auto" (system), "light", or "dark". */
    val theme: String = THEME_AUTO,
) {
    companion object {
        const val THEME_AUTO = "auto"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        val THEMES = listOf(THEME_AUTO, THEME_LIGHT, THEME_DARK)
    }
}

/**
 * The tappable actions a widget can surface. Each maps to a stable string key
 * (persisted in config) plus the WearAction verb the command engine understands.
 * `toggle` actions flip current state; `momentary` fire once; `nav` opens the app.
 */
enum class WidgetAction(
    val key: String,
    val label: String,
    /** The [com.bloo.bluelink.data.WearAction] verb, or null for app-nav/refresh handled locally. */
    val wearAction: String?,
    val kind: Kind,
) {
    LOCK("lock", "Lock", com.bloo.bluelink.data.WearAction.TOGGLE_LOCK, Kind.TOGGLE),
    CLIMATE("climate", "Climate", com.bloo.bluelink.data.WearAction.TOGGLE_CLIMATE, Kind.TOGGLE),
    CHARGE("charge", "Charge", com.bloo.bluelink.data.WearAction.TOGGLE_CHARGE, Kind.TOGGLE),
    FLASH("flash", "Flash", com.bloo.bluelink.data.WearAction.FLASH_LIGHTS, Kind.MOMENTARY),
    HORN("horn", "Horn", com.bloo.bluelink.data.WearAction.HORN_AND_LIGHTS, Kind.MOMENTARY),
    REFRESH("refresh", "Refresh", null, Kind.REFRESH),
    OPEN("open", "Open app", null, Kind.NAV);

    enum class Kind { TOGGLE, MOMENTARY, REFRESH, NAV }

    companion object {
        /** Curated defaults — the three status toggles + refresh, the everyday set. */
        val DEFAULTS = listOf(LOCK.key, CLIMATE.key, CHARGE.key, REFRESH.key)
        /** The small set offered in SIMPLE mode (just the core toggles). */
        val SIMPLE_CHOICES = listOf(LOCK, CLIMATE, CHARGE)
        /** Everything, for ADVANCED mode's multi-select. */
        val ALL = entries.toList()
        fun fromKey(key: String?): WidgetAction? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A glanceable read-only stat the widget can show below/around the ring. Same
 * ordered-keys pattern as [WidgetAction], but for info instead of controls.
 */
enum class WidgetInfoField(val key: String, val label: String) {
    RANGE("range", "Range"),
    PERCENT("percent", "Battery %"),
    ODOMETER("odometer", "Odometer"),
    PLATE("plate", "License plate"),
    SERVICE("service", "Service due"),
    UPDATED("updated", "Last updated");

    companion object {
        val DEFAULTS = listOf(RANGE.key, UPDATED.key)
        val ALL = entries.toList()
        fun fromKey(key: String?): WidgetInfoField? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Optional semantic accent overrides offered in advanced mode. `null` config accent
 * means "use the theme's primary". These map to [com.bloo.bluelink.data.BlooColors] ARGB ints.
 */
enum class WidgetAccent(val key: String, val label: String, val argb: Int) {
    GREEN("green", "Charge green", com.bloo.bluelink.data.BlooColors.chargeGreen),
    HEAT("heat", "Warm", com.bloo.bluelink.data.BlooColors.heat),
    COOL("cool", "Cool", com.bloo.bluelink.data.BlooColors.cool),
    WARN("warn", "Amber", com.bloo.bluelink.data.BlooColors.warn);

    companion object {
        val ALL = entries.toList()
        fun fromKey(key: String?): WidgetAccent? = entries.firstOrNull { it.key == key }
    }
}
