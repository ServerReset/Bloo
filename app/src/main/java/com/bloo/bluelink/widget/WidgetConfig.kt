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
    /** Whether to show the location map on large sizes.
     *
     *  Defaults ON. It used to default off, which meant the big layouts
     *  reserved a weighted slot for it and then rendered nothing there --
     *  a large widget was mostly empty space with the buttons pushed to the
     *  bottom edge. Where the car is happens to be exactly the thing worth
     *  showing once there's room for it, so the space and the content now
     *  agree. Still no-ops gracefully when the car has no coordinates. */
    val showMap: Boolean = true,
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
        // (no THEMES aggregate: nothing enumerates the theme options; the picker lists them itself)

        const val PRIORITY_INFO = "info"
        const val PRIORITY_CONTROLS = "controls"

        const val CORNER_SHARP = "sharp"
        const val CORNER_SOFT = "soft"
        const val CORNER_ROUND = "round"
        const val CORNER_PILL = "pill"
        // (no CORNERS aggregate: nothing enumerates the corner options; the picker lists them itself)

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
    // Appended rather than slotted beside LOCK: WidgetConfig.actions is a
    // saved ordinal-sorted list (see WidgetConfigActivity's own save
    // comment), so inserting mid-enum would reshuffle an existing widget's
    // button order. The explicit verb LOCK's own toggle already reaches --
    // this is for someone who wants Unlock as its own always-there button
    // rather than relying on Lock's toggle to land on the right state, the
    // same distinction the app's own quick actions make.
    UNLOCK("unlock", "Unlock", com.bloo.bluelink.data.WearAction.UNLOCK, Kind.TOGGLE),
    REFRESH("refresh", "Refresh", null, Kind.REFRESH),
    OPEN("open", "Open app", null, Kind.NAV),

    // Appended for UNLOCK's ordinal reason, which also means these sort after
    // "Open app" in the rendered button order. Same motivation as UNLOCK too: a
    // one-way button you can hit without first knowing which way the toggle will
    // go -- the toggle direction comes from the last snapshot, which on a stale
    // widget can be the wrong guess.
    //
    // No new plumbing was needed for any of these. All four verbs already exist
    // in the shared WearAction contract, and they are ALREADY the only verbs the
    // climate/charge path ever executes: resolveToggle turns TOGGLE_CLIMATE into
    // CLIMATE_ON/CLIMATE_OFF before anything runs, and passes an explicit verb
    // through untouched (`else -> action`). So stateFor, optimistic, the
    // "can't start climate while driving" gate and WidgetCommandWorker's revert
    // all handle them today.
    //
    // Kind.TOGGLE is correct rather than MOMENTARY: each has a snapshot field to
    // flip optimistically and revert if the command fails.
    //
    // SET_CHARGE_LIMITS is still excluded -- it needs acLimit/dcLimit values, so
    // it is a screen, not a button.
    CLIMATE_ON("climate_on", "Climate on", com.bloo.bluelink.data.WearAction.CLIMATE_ON, Kind.TOGGLE),
    CLIMATE_OFF("climate_off", "Climate off", com.bloo.bluelink.data.WearAction.CLIMATE_OFF, Kind.TOGGLE),
    CHARGE_ON("charge_on", "Charge on", com.bloo.bluelink.data.WearAction.CHARGE_ON, Kind.TOGGLE),
    CHARGE_OFF("charge_off", "Charge off", com.bloo.bluelink.data.WearAction.CHARGE_OFF, Kind.TOGGLE);

    enum class Kind { TOGGLE, MOMENTARY, REFRESH, NAV }

    companion object {
        /** Curated defaults — the three status toggles + refresh, the everyday set. */
        val DEFAULTS = listOf(LOCK.key, CLIMATE.key, CHARGE.key, REFRESH.key)
        /** The small set offered in SIMPLE mode (just the core toggles). */
        val SIMPLE_CHOICES = listOf(LOCK, CLIMATE, CHARGE)
        /** Everything, for ADVANCED mode's multi-select. */
        val ALL = entries.toList()

        /** Every action that only means something on a car with a chargeable pack;
         *  hidden outright on a gas car (see CarWidget.resolvedActions).
         *
         *  A set rather than an `it != CHARGE` test because that test was already
         *  written once and would have silently kept letting CHARGE_ON/CHARGE_OFF
         *  through onto a gas car's widget as buttons that do nothing. Anyone adding
         *  a fourth charge verb needs one edit, here, next to the entries themselves. */
        val NEEDS_BATTERY = setOf(CHARGE, CHARGE_ON, CHARGE_OFF)
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
    MODEL("model", "Model"),
    // Appended for the same reason as the three above: saved configs sort by
    // ordinal, so a mid-enum insert would reshuffle an existing widget's rows.
    LIMIT("limit", "Limit"),

    // Also appended, same ordinal reason. These three are the last VehicleSnapshot
    // fields a user would want on the home screen and had no way to put there; the
    // rest of the snapshot is either already above or internal (vin, regId,
    // generation, brandIndicator, isEv, hasBattery).
    //
    // Keyed "charging" rather than "charge" so the stored key can never be confused
    // with WidgetAction.CHARGE's "charge". They live in separate config lists, so a
    // clash would not actually break anything -- this is purely so a reader of the
    // persisted config can tell which enum a key came from.
    //
    // Worth recording what is NOT here and why, since it will be asked again: 12V
    // battery, tyre pressure, doors/windows open, fuel level and charge-time-remaining
    // all live on VehicleStatus / EvStatus, which the widget never receives -- it only
    // ever gets a VehicleSnapshot. Adding those means widening a serialized payload
    // the watch also reads plus snapshotOf() on the phone: a change across three
    // surfaces, not a new enum entry.
    ENGINE("engine", "Engine"),
    CHARGING("charging", "Charge"),
    LOCATION("location", "Location");

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
