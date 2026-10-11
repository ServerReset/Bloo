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
    )

    private fun app(key: String, kind: Kind, title: String = "", keywords: String = "") =
        Family(key, Scope.APP, kind, title, keywords)

    private fun car(key: String, kind: Kind, title: String = "", keywords: String = "") =
        Family(key, Scope.CAR, kind, title, keywords)

    private fun local(key: String, scope: Scope = Scope.APP) = Family(key, scope, Kind.STATE, syncs = false)

    val families: List<Family> = listOf(
        // --- App-wide, synced and searchable -------------------------------------------------
        app("ai_enabled", Kind.TOGGLE, "On-device AI", "gemini nano ai summary assistant privacy on-device"),
        app("ai_auto_summarize", Kind.TOGGLE, "Summarize when I open the app", "ai automatic automatically summary open launch gemini nano"),
        app("aurora_background", Kind.TOGGLE, "Aurora background", "gradient animated theme background glow"),
        app("aurora_motion", Kind.CHOICE, "Aurora motion", "aurora background animated static motion"),
        app("biometric_lock", Kind.TOGGLE, "App lock", "biometric fingerprint face lock security"),
        app("color_palette", Kind.CHOICE, "Colour palette", "color theme palette accent"),
        app("custom_palettes", Kind.LIST, "Custom palettes", "color theme palette accent custom"),
        app("active_custom_palette_id", Kind.CHOICE),
        app("columns_flipped", Kind.TOGGLE, "Flip columns", "layout columns swap sides left right"),
        app("distance_unit", Kind.CHOICE, "Distance unit", "miles kilometres km metric imperial units"),
        app("temp_unit", Kind.CHOICE, "Temperature unit", "celsius fahrenheit units"),
        app("unit_system", Kind.CHOICE, "Units", "metric imperial units"),
        app("dynamic_color", Kind.TOGGLE, "Dynamic color", "material you wallpaper theme color"),
        app("enabled_shortcuts", Kind.LIST, "App shortcuts", "launcher icon long press quick action"),
        app("font_choice", Kind.CHOICE, "Font", "typeface text font"),
        app("glass_clarity", Kind.NUMBER, "Glass clarity", "liquid glass transparency frosted clear crystal ultra"),
        app("haptics_enabled", Kind.TOGGLE, "Haptic feedback", "vibration vibrate buzz"),
        app("lock_timing", Kind.CHOICE, "App lock timing", "biometric grace re-lock"),
        app("watch_lock_timing", Kind.CHOICE, "Watch lock timing", "watch wear re-lock"),
        app("notify_autolock", Kind.TOGGLE, "AutoLock alerts", "notification autolock locked"),
        app("notify_charge_complete", Kind.TOGGLE, "Charge-complete alerts", "notification charging finished full"),
        app("notify_charging", Kind.TOGGLE, "Live charging updates", "notification charging live progress"),
        app("notify_door", Kind.TOGGLE, "Door-left-open alerts", "notification door open"),
        app("notify_door_min", Kind.NUMBER, "Door-open alert delay", "minutes before alerting door open"),
        app("notify_running", Kind.TOGGLE, "Car-running alerts", "notification engine climate running"),
        app("notify_running_min", Kind.NUMBER, "Car-running alert delay", "minutes before alerting engine running"),
        app("notify_service", Kind.TOGGLE, "Service due alerts", "notification reminder service"),
        app("notify_start", Kind.TOGGLE, "Car-started alerts", "notification engine started remote start"),
        app("notify_unlocked", Kind.TOGGLE, "Left-unlocked alerts", "notification unlocked left"),
        app("notify_unlocked_min", Kind.NUMBER, "Left-unlocked alert delay", "minutes before alerting unlocked"),
        app("notify_watch_low", Kind.TOGGLE, "Watch low-battery alerts", "watch wear notification low battery"),
        app("pebble_outline", Kind.TOGGLE, "Pebble outline", "border rim card theme"),
        app("settings_mode", Kind.CHOICE, "Simple or advanced settings", "simple advanced mode"),
        app("theme_mode", Kind.CHOICE, "Display mode", "theme light dark amoled system appearance"),
        app("ui_scale", Kind.NUMBER, "Text & layout scale", "display size zoom bigger smaller text"),
        app("ultra_glass", Kind.TOGGLE, "Ultra glass", "liquid glass edge refraction"),
        app("vibrancy", Kind.NUMBER, "Colour vibrancy", "color saturation vivid muted"),
        app("vehicle_order", Kind.LIST, "Car order", "garage cars order reorder sort"),
        app("weather_follows_device", Kind.TOGGLE, "Weather follows my location", "weather location device gps"),
        app("weather_label", Kind.TEXT, "Weather location", "weather place city"),
        app("weather_lat", Kind.NUMBER),
        app("weather_lon", Kind.NUMBER),
        app("search_bubble_x", Kind.NUMBER),
        app("search_bubble_y", Kind.NUMBER),
        app("onboarding_seen", Kind.STATE),
        app("autolock_vins", Kind.LIST),

        // --- Per-car, synced (suffix: the car's VIN) -----------------------------------------
        car("img_", Kind.IMAGE, "Car photo", "photo picture image hero"),
        car("plate_", Kind.TEXT, "License plate", "plate licence registration"),
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
        car("autolock_device_name_", Kind.TEXT, "AutoLock Bluetooth device", "autolock bluetooth car device"),

        // --- Device-local: never travel ------------------------------------------------------
        local("sync_uri"), local("sync_last_ms"), local("sync_last_error"), local("sync_wifi"),
        local("sync_dirty_keys"), local("sync_device_id"), local("sync_device_name"),
        local("sync_last_hash"), local("sync_synced_ever"), local("sync_devices_cache"),
        local("sync_pull_primary"), local("sync_primary_cache"), local("sync_file_id"),
        local("sync_primary_pending"),
        // Whether THIS device installs updates silently via Shizuku: Shizuku may not exist elsewhere.
        local("seamless_install_shizuku"),
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

    private val appKeys: Map<String, Family> = families.filter { it.scope == Scope.APP }.associateBy { it.key }
    private val carFamilies: List<Family> = families.filter { it.scope == Scope.CAR }
        // Longest prefix first, so "autolock_device_name_" is never shadowed by a shorter one.
        .sortedByDescending { it.key.length }

    /** The family a stored preference key belongs to, or null if the catalog does not know it. */
    fun familyOf(prefName: String): Family? =
        appKeys[prefName] ?: carFamilies.firstOrNull { prefName.startsWith(it.key) }

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
     * The VIN a per-car key belongs to, or null for an app-wide key. [CarKeys] normalises it the way
     * the stores do, so a lookup is not defeated by case or stray whitespace.
     */
    fun vinOf(prefName: String): String? {
        val family = carFamilies.firstOrNull { prefName.startsWith(it.key) } ?: return null
        // A VIN has no underscore; per-car keys may append more after it (seat_<vin>_<flag>).
        return CarKeys.normaliseVin(prefName.removePrefix(family.key).substringBefore('_'))
    }
}

/** How a VIN appears in a key: trimmed and upper-case, so two devices never split one car in two. */
object CarKeys {
    fun normaliseVin(raw: String): String = raw.trim().uppercase()
}
