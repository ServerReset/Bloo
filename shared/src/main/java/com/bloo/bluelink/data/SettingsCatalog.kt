package com.bloo.bluelink.data

/**
 * The one description of every stored setting, read by BOTH halves of the app that used to keep
 * their own idea of what a setting is: Drive sync (what travels between devices, via
 * [SyncMerge.isDeviceLocal]) and search (what can be found, and what words find it, via
 * [searchable]). A setting that is added here is classified once and the two can no longer disagree.
 *
 * Settings are stored as a flat key/value bag. An app-wide setting is one exact key; a per-car
 * setting is a key family: [Family.key] followed by the car's VIN (and, for a few, more after it).
 * Anything the catalog does not know about still syncs by default, so a forgotten entry never loses
 * data -- it just does not appear in search until it is added.
 */
object SettingsCatalog {
    enum class Scope { APP, CAR }

    /** What shape the value has, which is what search and the natural-language resolver need. */
    enum class Kind { TOGGLE, NUMBER, TEXT, CHOICE, LIST, IMAGE, STATE }

    /**
     * One setting or family of settings.
     *
     * @param key exact key for [Scope.APP]; the key prefix (before the VIN) for [Scope.CAR].
     * @param syncs false for state that belongs to one device only (never exported, imported,
     * merged or hashed -- see [SyncMerge.isDeviceLocal]).
     * @param searchable whether a person might look for this by name. Runtime flags and sync
     * bookkeeping are not.
     */
    data class Family(
        val key: String,
        val scope: Scope,
        val kind: Kind,
        val title: String = "",
        val keywords: String = "",
        val syncs: Boolean = true,
        val searchable: Boolean = syncs && title.isNotEmpty(),
        /** True when the app already has a real control for it in search (a toggle, slider or picker). */
        val control: Boolean = false,
    )

    private fun app(key: String, kind: Kind, title: String = "", keywords: String = "") =
        Family(key, Scope.APP, kind, title, keywords)

    private fun car(key: String, kind: Kind, title: String = "", keywords: String = "") =
        Family(key, Scope.CAR, kind, title, keywords)

    private fun local(key: String, scope: Scope = Scope.APP) = Family(key, scope, Kind.STATE, syncs = false)

    private val rawFamilies: List<Family> = listOf(
        // --- App-wide, synced and searchable -------------------------------------------------
        app(PrefKeys.AI_ENABLED, Kind.TOGGLE, "On-device AI", "gemini nano ai summary assistant privacy on-device"),
        app(PrefKeys.AI_AUTO_SUMMARIZE, Kind.TOGGLE, "Summarize when I open the app", "ai automatic automatically summary open launch gemini nano"),
        app(PrefKeys.AURORA_BACKGROUND, Kind.TOGGLE, "Aurora background", "gradient animated theme background glow"),
        app(PrefKeys.BIOMETRIC_LOCK, Kind.TOGGLE, "App lock", "biometric fingerprint face lock security"),
        app(PrefKeys.COLOR_PALETTE, Kind.CHOICE, "Colour palette", "color theme palette accent blue violet teal forest amber rose bloo"),
        app(PrefKeys.CUSTOM_PALETTES, Kind.LIST, "Custom palettes", "color theme palette accent custom saved"),
        app(PrefKeys.ACTIVE_CUSTOM_PALETTE_ID, Kind.CHOICE),
        // Edited on the watch, so no title: it syncs but is not offered as a phone search result.
        app(PrefKeys.WATCH_FACE_SLOTS, Kind.LIST, "", "watch wear face quarters controls tile buttons layout"),
        app(PrefKeys.WATCH_REDUCE_EFFECTS, Kind.TOGGLE, "", "watch wear effects animations blur motion battery"),
        app(PrefKeys.WATCH_AUTOLOCK, Kind.TOGGLE, "", "watch wear autolock walk away lock motion"),
        app(PrefKeys.COLUMNS_FLIPPED, Kind.TOGGLE, "Flip columns", "layout columns swap sides left right wide screen tablet hot seat pebbles controls"),
        app(PrefKeys.DISTANCE_UNIT, Kind.CHOICE, "Distance unit", "distance mileage odometer range miles kilometres km units"),
        app(PrefKeys.TEMP_UNIT, Kind.CHOICE, "Temperature unit", "temperature celsius fahrenheit degrees c f units"),
        app(PrefKeys.UNIT_SYSTEM, Kind.CHOICE, "Units", "unit system metric imperial temperature distance speed miles km"),
        app(PrefKeys.DYNAMIC_COLOR, Kind.TOGGLE, "Dynamic colour", "material you wallpaper theme color"),
        app(PrefKeys.ENABLED_SHORTCUTS, Kind.LIST, "App shortcuts", "launcher icon long press quick action"),
        app(PrefKeys.FONT_CHOICE, Kind.CHOICE, "Font", "typeface atkinson hyperlegible google sans accessibility low vision text"),
        app(PrefKeys.GLASS_CLARITY, Kind.NUMBER, "Glass clarity", "liquid glass transparency frosted medium soft clear crystal ultra refraction edge blur backing everywhere"),
        app(PrefKeys.HAPTICS_ENABLED, Kind.TOGGLE, "Haptic feedback", "vibration vibrate buzz sound"),
        app(PrefKeys.LOCK_TIMING, Kind.CHOICE, "App lock timing", "biometric grace re-lock screen off immediate"),
        app(PrefKeys.WATCH_LOCK_TIMING, Kind.CHOICE, "Watch lock timing", "watch wear pin lock ask passcode security open commands"),
        app(AlertKeys.AUTOLOCK, Kind.TOGGLE, "AutoLock alerts", "notification autolock locked dry run test"),
        app(AlertKeys.CHARGE_COMPLETE, Kind.TOGGLE, "Charge-complete alerts", "notification charging finished full done battery"),
        app(AlertKeys.CHARGING, Kind.TOGGLE, "Live charging updates", "notification charging live progress ongoing bar ev limit"),
        app(AlertKeys.DOOR, Kind.TOGGLE, "Door-left-open alerts", "notification door open"),
        app(AlertKeys.DOOR_MINUTES, Kind.NUMBER, "Door-open alert delay", "minutes before alerting door open notification delay wait"),
        app(AlertKeys.RUNNING, Kind.TOGGLE, "Car-running alerts", "notification engine climate running left on"),
        app(AlertKeys.RUNNING_MINUTES, Kind.NUMBER, "Car-running alert delay", "minutes before alerting engine running notification delay wait"),
        app(AlertKeys.SERVICE, Kind.TOGGLE, "Service due alerts", "notification reminder service"),
        app(AlertKeys.CAR_STARTED, Kind.TOGGLE, "Car-started alerts", "notification engine started remote start someone"),
        app(AlertKeys.UNLOCKED, Kind.TOGGLE, "Left-unlocked alerts", "notification unlocked lock left open"),
        app(AlertKeys.UNLOCKED_MINUTES, Kind.NUMBER, "Left-unlocked alert delay", "minutes before alerting unlocked notification delay wait"),
        app(AlertKeys.WATCH_LOW_BATTERY, Kind.TOGGLE, "Watch low-battery alerts", "watch wear notification low battery under 20 percent"),
        app(PrefKeys.SETTINGS_MODE, Kind.CHOICE, "Simple or advanced settings", "simple advanced mode power user more options fewer options settings"),
        app(PrefKeys.THEME_MODE, Kind.CHOICE, "Display mode", "theme light dark amoled oled system appearance"),
        app(PrefKeys.UI_SCALE, Kind.NUMBER, "Text & layout scale", "display size zoom bigger smaller text interface density"),
        app(PrefKeys.ULTRA_GLASS, Kind.TOGGLE, "Ultra glass", "liquid glass edge refraction"),
        app(PrefKeys.CLASSIC_HEADINGS, Kind.TOGGLE, "Classic headings", "font serif classical headings titles display elegant old style"),
        app(PrefKeys.MOTION_BLUR, Kind.TOGGLE, "Motion blur", "blur motion moving sliders pebbles floating buttons animation smear battery saver"),
        app(PrefKeys.REDUCE_MOTION, Kind.TOGGLE, "Reduce motion", "accessibility less motion animation bounce vestibular calm still battery saver"),
        app(PrefKeys.KEYBOARD_SHORTCUTS, Kind.TOGGLE, "Keyboard shortcuts", "keyboard shortcuts hotkeys ctrl desktop keys accessibility"),
        app(PrefKeys.VIBRANCY, Kind.NUMBER, "Colour vibrancy", "color saturation vivid material you monochrome best buy tv muted"),
        app(PrefKeys.VEHICLE_ORDER, Kind.LIST, "Car order", "garage cars order reorder sort"),
        app(PrefKeys.WEATHER_FOLLOWS_DEVICE, Kind.TOGGLE, "Weather follows my location", "weather location device gps"),
        app(PrefKeys.WEATHER_LABEL, Kind.TEXT, "Weather location", "weather place city location forecast"),
        app(PrefKeys.WEATHER_LAT, Kind.NUMBER),
        app(PrefKeys.WEATHER_LON, Kind.NUMBER),
        app(PrefKeys.SEARCH_BUBBLE_X, Kind.NUMBER),
        app(PrefKeys.SEARCH_BUBBLE_Y, Kind.NUMBER),
        app(PrefKeys.ONBOARDING_SEEN, Kind.STATE),
        app(PrefKeys.AUTOLOCK_VINS, Kind.LIST),

        // --- Per-car, synced (suffix: the car's VIN) -----------------------------------------
        car("img_", Kind.IMAGE, "Car photo", "photo picture image hero"),
        // Set on each car's own card in Settings, so it has no search title of its own. It still syncs.
        car("text_clear_", Kind.TOGGLE, "", "floating photo text clear padding hero card edge"),
        car("plate_", Kind.TEXT, "Licence plate", "plate licence registration"),
        car("platform_", Kind.CHOICE, "Head-unit generation", "gen5w ccnc platform generation"),
        car("ptrain_", Kind.CHOICE, "Powertrain", "ev gas hybrid phev"),
        car("seat_", Kind.TOGGLE, "Seat heat and cooling", "seat heat cool ventilated climate steering wheel"),
        car("svc_last_", Kind.NUMBER, "Last service", "service maintenance mileage serviced"),
        car("svc_interval_", Kind.NUMBER, "Service interval", "service maintenance interval due"),
        car("hotspots_", Kind.CHOICE, "Pinned pebble", "hot seat pinned pebble"),
        car("sections_", Kind.LIST, "Pebble order", "pebbles sections order"),
        car("collapsed_", Kind.LIST, "Collapsed pebbles", "pebbles sections collapsed"),
        car("car_configured_", Kind.STATE),
        car("autolock_enabled_", Kind.TOGGLE, "AutoLock", "autolock automatic lock walk away"),
        car("autolock_dry_run_", Kind.TOGGLE, "AutoLock dry run", "autolock dry run test"),
        car("autolock_grace_", Kind.NUMBER, "AutoLock grace period", "autolock grace seconds countdown"),
        car("autolock_device_addr_", Kind.TEXT),
        // Picked in a dialog from the car's AutoLock card, so it is not offered as a search result of its own.
        car("autolock_device_name_", Kind.TEXT),

        // --- Device-local: never travel ------------------------------------------------------
        local("sync_uri"), local("sync_last_ms"), local("sync_last_error"), local("sync_wifi"),
        local("sync_dirty_keys"), local("sync_device_id"), local("sync_device_name"),
        local("sync_last_hash"), local("sync_synced_ever"), local("sync_devices_cache"),
        local("sync_pull_primary"), local("sync_primary_cache"), local("sync_file_id"),
        local("sync_primary_pending"), local("sync_pending_removed_device_ids"),
        // Whether THIS device installs updates silently via Shizuku: Shizuku may not exist elsewhere,
        // so it never syncs -- but it is still a setting a person searches for, so it carries a
        // title and keywords like any other, and the app reads them from here.
        Family(
            "seamless_install_shizuku", Scope.APP, Kind.STATE,
            title = "Install updates seamlessly", keywords = "shizuku silent install update",
            syncs = false,
        ),
        // Which car is on screen right now: transient view state.
        local("last_vehicle_vin"),
        // Which version of the settings schema this install last migrated to (see [SyncSchema]).
        local("settings_schema"),
        // "Already fired" flags and wall-clock episode starts. A peer's flag would suppress this
        // device's notification, and a timestamp means nothing in another device's clock domain.
        local("alert_", Scope.CAR), local("door_since_", Scope.CAR), local("engine_since_", Scope.CAR),
        local("unlocked_since_", Scope.CAR), local("tile_refreshed_", Scope.CAR),
        // The charging-notification "already sent" flags: the same kind of runtime flag as alert_*,
        // which used to roam, so one device's "sent" silenced the other's alert.
        local("notif_start_sent_", Scope.CAR), local("notif_complete_sent_", Scope.CAR),
        // Dismissing a live charging bar on one device says nothing about another.
        local("live_dismissed_", Scope.CAR),
    )

    /** Settings that already have their own control in search; everything else is shown read-only. */
    private val withControl = setOf(
        "ai_enabled", "ai_auto_summarize", "aurora_background", "biometric_lock",
        "distance_unit", "temp_unit", "unit_system", "dynamic_color", "font_choice", "glass_clarity",
        "haptics_enabled", AlertKeys.AUTOLOCK, AlertKeys.CHARGE_COMPLETE, AlertKeys.CHARGING, AlertKeys.DOOR,
        AlertKeys.DOOR_MINUTES, AlertKeys.RUNNING, AlertKeys.RUNNING_MINUTES, AlertKeys.SERVICE, AlertKeys.CAR_STARTED,
        AlertKeys.UNLOCKED, AlertKeys.UNLOCKED_MINUTES, AlertKeys.WATCH_LOW_BATTERY, "theme_mode",
        "ui_scale", "vibrancy", "enabled_shortcuts", "color_palette", "columns_flipped", "settings_mode",
        // The accessibility suite's own switches (AccessibilityGroup in Settings, ToggleSpec in search).
        PrefKeys.MOTION_BLUR, PrefKeys.REDUCE_MOTION, PrefKeys.KEYBOARD_SHORTCUTS, PrefKeys.CLASSIC_HEADINGS,
        "vehicle_order", "custom_palettes", "weather_label", "weather_follows_device",
        "hotspots_", "sections_", "collapsed_", "img_",
        // Covered by an existing control: the App lock row (timing), the Glass stops (Ultra).
        "lock_timing", "ultra_glass", "watch_lock_timing",
        "plate_", "platform_", "ptrain_", "seat_", "svc_last_", "svc_interval_",
        "autolock_enabled_", "autolock_dry_run_", "autolock_grace_",
    )

    val families: List<Family> = rawFamilies.map { if (it.key in withControl) it.copy(control = true) else it }

    private val appKeys: Map<String, Family> = families.filter { it.scope == Scope.APP }.associateBy { it.key }
    private val carFamilies: List<Family> = families.filter { it.scope == Scope.CAR }
        // Longest prefix first, so "autolock_device_name_" is never shadowed by a shorter one.
        .sortedByDescending { it.key.length }

    /** The family a stored preference key belongs to, or null if the catalog does not know it. */
    fun familyOf(prefName: String): Family? =
        appKeys[prefName] ?: carFamilies.firstOrNull { prefName.startsWith(it.key) }

    /**
     * The one title for a setting key, or null if the catalog has none. This is THE source of truth:
     * the settings UI, search and the natural-language resolver all read it from here rather than
     * declaring their own copy.
     */
    fun titleOf(key: String): String? = familyOf(key)?.title?.takeIf { it.isNotBlank() }

    /** The one keyword set for a setting key ("" if the catalog has none); see [titleOf]. */
    fun keywordsOf(key: String): String = familyOf(key)?.keywords.orEmpty()

    /** Keys that must never travel: every exact device-local app key. */
    val deviceLocalKeys: Set<String> =
        families.filter { !it.syncs && it.scope == Scope.APP }.mapTo(LinkedHashSet()) { it.key }

    /** Per-car prefixes that must never travel. */
    val deviceLocalPrefixes: List<String> =
        families.filter { !it.syncs && it.scope == Scope.CAR }.map { it.key }

    /** False only for what the catalog explicitly marks device-local; unknown keys sync. */
    fun syncs(prefName: String): Boolean =
        prefName !in deviceLocalKeys && deviceLocalPrefixes.none { prefName.startsWith(it) }

    /** Everything a person can look for by name, for search and the natural-language resolver. */
    val searchable: List<Family> = families.filter { it.searchable }

    /**
     * What search shows for a stored value: the plain-language reading of whatever the preference
     * holds, for the settings that have no control of their own to draw. Null [raw] means unset.
     */
    fun describe(family: Family, raw: Any?): String = when {
        raw == null -> "Not set"
        raw is Boolean -> if (raw) "On" else "Off"
        family.kind == Kind.IMAGE -> if (raw.toString().isBlank()) "Not set" else "Set"
        family.kind == Kind.LIST -> raw.toString().split(',').count { it.isNotBlank() }.let {
            if (it == 0) "None" else if (it == 1) "1 item" else "$it items"
        }
        raw.toString().isBlank() -> "Not set"
        else -> raw.toString()
    }
}
