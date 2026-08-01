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
    /** Use the car's own photo (blurred, full-bleed) as the widget background
     *  instead of a flat themed surface. No-ops gracefully back to the themed
     *  surface when the car has no photo set. */
    val photoBackground: Boolean = false,
    /** At the MICRO/COMPACT_WIDE/COMPACT_TALL tiers only (below roughly 2x2
     *  cells -- see CarWidget.Tier) there isn't room to show both the status
     *  ring/stats AND the action buttons at a usable size, so this decides
     *  which one wins outright at those sizes: [PRIORITY_INFO] (the default)
     *  shows the ring/name/stat, [PRIORITY_CONTROLS] replaces it with the
     *  configured action buttons filling the same space. Sizes with more room
     *  (MEDIUM and up) always show both regardless of this setting. */
    val priority: String = PRIORITY_INFO,
    /** Extreme corner rounding -- a true pill/stadium shape instead of the
     *  usual rounded square. Only visibly different from the normal corner at
     *  small sizes (roughly 2x2 cells or under); see CarWidget.Content, which
     *  silently no-ops back to the normal corner above that so a pill doesn't
     *  clip a large widget's own content into a lens shape.
     *
     *  Superseded by [corner] but kept so existing widgets keep their shape:
     *  a config saved before [corner] existed still round-trips, and
     *  [effectiveCorner] treats it as a request for [CORNER_PILL]. */
    val pillShape: Boolean = false,
    /** Corner treatment: one of [CORNER_SHARP], [CORNER_SOFT], [CORNER_ROUND],
     *  [CORNER_PILL]. Finer control than the original pill on/off, which only
     *  ever offered "normal" or "extreme". */
    val corner: String = CORNER_SOFT,
    /** Background opacity, 0f..1f. Below 1 the themed surface goes
     *  translucent so the wallpaper reads through -- the single most-requested
     *  thing a launcher widget can offer. Ignored when [photoBackground] is
     *  on, which brings its own backdrop. */
    val backgroundOpacity: Float = 1f,
    /** Multiplies every font size the widget derives from its measured size
     *  (see CarWidget.Scale). Lets a user trade information density for
     *  legibility without resizing the widget itself. */
    val textScale: Float = 1f,
    /** Show the car name / status header where the layout has room for one.
     *  Off buys that space back for the ring and stats. */
    val showHeader: Boolean = true,
    /** Show the "Updated <n> min ago" footer where the layout has room. */
    val showFooter: Boolean = true,
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

        const val PRIORITY_INFO = "info"
        const val PRIORITY_CONTROLS = "controls"

        const val CORNER_SHARP = "sharp"
        const val CORNER_SOFT = "soft"
        const val CORNER_ROUND = "round"
        const val CORNER_PILL = "pill"
        val CORNERS = listOf(CORNER_SHARP, CORNER_SOFT, CORNER_ROUND, CORNER_PILL)

        /** Smallest opacity offered. Fully transparent would leave a widget
         *  that looks broken rather than styled -- text and buttons floating
         *  with no surface to sit on -- so the floor keeps a visible tint. */
        const val MIN_BACKGROUND_OPACITY = 0.2f

        /** Text scale bounds. Wide enough to matter, tight enough that the
         *  layouts still hold: past these, a scaled label starts driving
         *  FitText into its wrap and shrink fallbacks on every tier rather
         *  than only the cramped ones. */
        const val MIN_TEXT_SCALE = 0.8f
        const val MAX_TEXT_SCALE = 1.4f
    }

    /** The corner treatment actually in force, honouring a [pillShape] saved
     *  before [corner] existed so an upgrade never silently restyles a widget
     *  the user had already set up. */
    val effectiveCorner: String
        get() = if (pillShape && corner == CORNER_SOFT) CORNER_PILL else corner

    /** [backgroundOpacity] clamped to what the UI actually offers, so a value
     *  hand-edited or left over from another version can't render the widget
     *  invisible. */
    val safeBackgroundOpacity: Float
        get() = backgroundOpacity.coerceIn(MIN_BACKGROUND_OPACITY, 1f)

    /** [textScale] clamped for the same reason. */
    val safeTextScale: Float
        get() = textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)
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
    // Was "Battery %" -- the value itself already reads "82%", so the label
    // repeating the % sign was both redundant and the single longest label
    // in the set, worst-case for InfoStack's narrow-width row.
    PERCENT("percent", "Battery"),
    ODOMETER("odometer", "Odometer"),
    // Was "License plate" (13 chars, the longest label here) and "Service
    // due" -- shortened both; "Last updated" -> "Updated" also now matches
    // FooterRow's own wording for the same value elsewhere in the widget.
    PLATE("plate", "Plate"),
    SERVICE("service", "Service"),
    UPDATED("updated", "Updated"),
    // Appended rather than slotted in beside the other status-ish fields on
    // purpose: saved configs are sorted by ordinal, so inserting mid-enum
    // would reshuffle the row order of any widget the user later re-saves.
    //
    // Lock and climate state already appear in the header subtitle, but that
    // header is now optional (WidgetConfig.showHeader) and on several tiers
    // there is no room for one at all -- as selectable rows they can be shown
    // wherever the user actually wants them.
    LOCK("lock", "Lock"),
    CLIMATE("climate", "Climate"),
    MODEL("model", "Model");

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
