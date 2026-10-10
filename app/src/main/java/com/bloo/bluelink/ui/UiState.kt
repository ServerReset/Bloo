package com.bloo.bluelink.ui

/**
 * The app's immutable UI state snapshot, its screen dispatcher, and the pure decision functions
 * that answer "what shows for this car". Plain Kotlin over plain data (no Android, no coroutines)
 * so it runs as JVM unit tests.
 */
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.PinLockout
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.DEFAULT_SECTIONS
import com.bloo.bluelink.data.EvTrip
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.VehiclePlatform
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

sealed interface Screen {
    /**
     * The bootstrapping state, and ONLY the bootstrapping state: shown for the brief window between
     * process start and the cold-start auto-login coroutine (see AppViewModel's init block)
     * determining whether this is a genuinely logged-out device (-> Login) or a returning signed-in
     * one (-> whatever loadGarage resolves once its network call returns).
     */
    data object Loading : Screen
    data object Login : Screen
    /** First-run deck of welcome cards: restore, setup, look and feel, and a card per car. */
    data object Onboarding : Screen
    /** The same deck, with just a card per newly detected car (post-first-run). */
    data class CarSetup(val vins: List<String>) : Screen
    /**
     * Main screen: the car carousel/grid. Zero-vehicle accounts land here too, via GarageStatusCard
     * (Guard.kt) as a pager page.
     */
    data object Garage : Screen
}

/**
 * Reserved pseudo-VIN for Settings cards so they share car pebbles' collapse state. No real VIN
 * collides.
 */
internal const val SETTINGS_CARD_VIN = "__settings__"

/**
 * Placeholder [Vehicle] so [SettingsCard] can reuse [AppViewModel.togglePebble] keyed on `v.vin`.
 * Only `vin` is read; the other fields are empty placeholders and never shown.
 */
internal val SettingsPseudoVehicle = Vehicle(
    vin = SETTINGS_CARD_VIN,
    regId = "",
    name = "",
    model = "",
    generation = "",
    brandIndicator = "",
    isEv = false,
)

/**
 * Keeps UiState STABLE for Compose: its List/Map/Set fields are interfaces, so without this every
 * pebble taking it would be non-skippable and recompose with each parent frame.
 */
@androidx.compose.runtime.Immutable
data class UiState(
    // Loading, not Login: see Screen.Loading.
    val screen: Screen = Screen.Loading,
    /**
     * Biometric app-lock overlay (real app renders blurred behind it). True when biometric lock is
     * usable or an app PIN is installed ([appPinSet]).
     */
    val locked: Boolean = false,
    /** An app PIN is installed (4-8 digits), mirrored from CredentialStore. */
    val appPinSet: Boolean = false,
    /** Live mirror of the PIN wrong-attempt lockout policy (failures + rejection deadline). */
    val pinLockout: PinLockout = PinLockout(),
    /**
     * A PIN verify just rejected the attempt; the overlay shows the error, then calls
     * [AppViewModel.acknowledgePinRejection].
     */
    val pinAttemptRejected: Boolean = false,
    /**
     * Bumped every time a PIN verify SUCCEEDS (the settings dialogs listen for the tick to advance
     * past the "enter your current PIN" stage -- success itself is otherwise silent, since the app
     * may already be unlocked when it happens in Settings).
     */
    val pinAcceptedTick: Int = 0,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    // currentIndex is deliberately NOT here; see AppViewModel.currentIndex.
    /** On large screens, the index expanded to full screen (null = grid view). */
    val expandedIndex: Int? = null,
    val statuses: Map<String, VehicleStatus> = emptyMap(),
    /** Wall-clock millis the app last pulled status from the server, keyed by VIN. */
    val lastFetched: Map<String, Long> = emptyMap(),
    val locations: Map<String, GeoLocation> = emptyMap(),
    /**
     * The DEVICE's own last-known position (one value, not per-VIN). Refreshed on cold start,
     * pull-to-refresh and [AppViewModel.locate]; never polled on its own.
     */
    val deviceLocation: GeoLocation? = null,
    /**
     * A short human-readable name for [deviceLocation] (reverse-geocoded on refresh), shown as the
     * phone-side location label in the Location & Weather pebble. Null until a fix has been
     * geocoded -- geocoding fails routinely and is never treated as an error.
     */
    val devicePlace: String? = null,
    /** Recent EV trips by VIN (loaded lazily when the Trips pebble is shown). */
    val trips: Map<String, List<EvTrip>> = emptyMap(),
    /** User-named climate presets by VIN. */
    val climatePresets: Map<String, List<ClimatePreset>> = emptyMap(),
    /**
     * Live climate draft by VIN, shared between simultaneous compositions of one car's climate
     * pebble.
     */
    val climateSync: Map<String, com.bloo.bluelink.data.ClimateSync> = emptyMap(),
    val seatConfigs: Map<String, SeatConfig> = emptyMap(),
    val powertrains: Map<String, Powertrain> = emptyMap(),
    /** User-confirmed head-unit generation, by VIN -- see [platformOf]. */
    val platforms: Map<String, VehiclePlatform> = emptyMap(),
    val sectionOrders: Map<String, List<String>> = emptyMap(),
    val imageUrls: Map<String, String> = emptyMap(),
    val placeNames: Map<String, String> = emptyMap(),
    /**
     * Compact form of [placeNames] (street + ZIP) for space-constrained glance layouts. In-memory
     * only; not persisted to statusCache.
     */
    val placeZips: Map<String, String> = emptyMap(),
    /** Current weather at the user's configured "home" location, if loaded. */
    val homeWeather: Weather? = null,
    /** Current weather at each car's last-known location, keyed by VIN. */
    val carWeather: Map<String, Weather> = emptyMap(),
    /**
     * Weather at the PHONE's own position ([deviceLocation]); always tracks the live device fix,
     * unlike [homeWeather].
     */
    val phoneWeather: Weather? = null,
    val licensePlates: Map<String, String> = emptyMap(),
    val lastServiceMiles: Map<String, Int> = emptyMap(),
    val serviceIntervalMiles: Map<String, Int> = emptyMap(),
    /** In-flight commands, keyed "vin:action", so each control can show its own spinner. */
    val pending: Set<String> = emptySet(),
    /**
     * Recent remote commands by VIN, newest first, a rolling [REMOTE_ACTION_HISTORY_DAYS]-day
     * window written by [AppViewModel.runCommand]. Shown by RemoteActionsInline.
     */
    val remoteActionHistory: Map<String, List<RemoteAction>> = emptyMap(),
    /** Collapsed pebbles, keyed "vin:section". Absent = expanded. */
    val collapsedPebbles: Set<String> = emptySet(),
    /**
     * Per-VIN pebbles pinned to the dual-column hotspot. Primary slot is always "controls" (not
     * stored); the secondary slot is user-selected, a String or null.
     */
    val hotspotSections: Map<String, String?> = emptyMap(),
    /** Enabled app-icon shortcut ids ("cmd_vin"); null = show all. */
    val shortcutSet: Set<String>? = null,
    /** Shizuku installed + running, so the "seamless install" toggle is worth showing. */
    val shizukuAvailable: Boolean = false,
    /** On-device Gemini Nano availability + opt-in, and produced summaries. */
    val aiSupported: Boolean = false,
    val aiEnabled: Boolean = false,
    val aiSummaries: Map<String, String> = emptyMap(),
    /** In-flight AI work: VINs being summarized, plus "search" for the query box. */
    val aiBusy: Set<String> = emptySet(),
    val aiSearchReply: String? = null,
    /**
     * The garage's collapsed pager is settled on its Settings slot (always the last page). The only
     * "looking at Settings" signal; drives SearchLayer's bubble/pill morph.
     */
    val onSettingsPageSlot: Boolean = false,
    /**
     * A car's full-screen map overlay is expanded. Hides the floating search bubble, which shares a
     * corner with the map's bottom action row.
     */
    val mapExpanded: Boolean = false,
    /**
     * Gentle hint shown on the garage right after onboarding, nudging the user toward Settings to
     * fine-tune each car.
     */
    val showSettingsHint: Boolean = false,
    /** The welcome cards, summoned again from Settings (see [showWelcomeCards]). */
    val welcomeCardsOpen: Boolean = false,
    /** All signed-in accounts (one per brand). */
    val accounts: List<Credentials> = emptyList(),
    /** Showing the login form to add another account while already signed in. */
    val addingAccount: Boolean = false,
    /**
     * True when the user backed out of the biometric prompt to the login screen. Cancelling in this
     * state must re-lock rather than navigate to the garage.
     */
    val lockedToLogin: Boolean = false,
    /** Kia sign-in only: a pending one-time-code challenge. */
    val kiaOtp: KiaOtpUi? = null,
    /** Canada sign-in only (Hyundai/Genesis/Kia): a pending one-time-code challenge. */
    val canadaOtp: CanadaOtpUi? = null,
    val message: String? = null,
    /** Controls the snackbar's colour, icon and dwell time. */
    val messageType: ToastKind = ToastKind.ERROR,
    /** A newer CI build than installed, if found. Drives the update tile; null means no tile. */
    val updateAvailable: com.bloo.bluelink.update.UpdateInfo? = null,
    /** "Remind me" also sets this, plus a snooze and a 1-day reminder worker. */
    val updateTileDismissed: Boolean = false,
    /** True while the update APK downloads in-app (see AppViewModel.downloadUpdateInBackground). */
    val updateDownloading: Boolean = false,
    // Download progress is NOT here: it ticks per 64KB chunk and would recompose every live pebble.
    // It lives in AppViewModel.updateDownloadProgress and only the update tile collects it.
    /**
     * True once the update APK has finished downloading and is sitting in the cache ready to hand
     * to the installer -- lets the update tile's button do "tap to download, tap again to install".
     * Reset by a fresh update check finding a different/no build available.
     */
    val updateApkReady: Boolean = false,
    /**
     * True while a Shizuku seamless install is running, so a second Install tap (or a
     * permission-grant retry) can't spawn a concurrent PackageInstaller session.
     */
    val updateInstalling: Boolean = false,
    /**
     * True while the pre-install guide is up: shown before handing the APK to the system installer
     * when Shizuku (seamless install) is off, so the Play-Protect steps are seen first.
     */
    val showInstallGuide: Boolean = false,
    /**
     * A manual "Check for updates" is in flight (spinner + disabled button). Background checks
     * don't set it.
     */
    val updateChecking: Boolean = false,
    /** Settings mode: "simple" (essential settings) or "advanced" (all settings). */
    val settingsMode: String = "simple",
    /** Per-VIN default preset ID for the one-tap climate Start button. */
    val defaultClimatePresets: Map<String, String> = emptyMap(),
    /** Drive URI (content://...) for auto-backup; null when not configured. */
    val syncUri: String? = null,
    val lastSyncMs: Long = 0L,
    /** Wi-Fi only sync (true) or any network (false). */
    val syncWifiOnly: Boolean = true,
    /** Why the last Drive sync didn't fully succeed; null if it did or hasn't run. */
    val syncError: String? = null,
    /**
     * Devices sharing this sync file (name/model/last-seen), for the Settings "your devices" list.
     * From the last sync's merged registry / cache.
     */
    val syncDevices: List<com.bloo.bluelink.data.SyncMerge.SyncDevice> = emptyList(),
    /** The device id designated primary (source of truth), or null if none. */
    val syncPrimaryId: String? = null,
    /** This device's own sync id, to mark "This device" and hide "Make primary" on self. */
    val thisDeviceId: String? = null,
    /** This device's friendly sync name (editable in Settings). */
    val syncDeviceName: String = "",
    /** When a paired watch asks for the app PIN. Device-local; see WatchLockTiming. */
    val watchLockTiming: com.bloo.bluelink.data.WatchLockTiming = com.bloo.bluelink.data.WatchLockTiming.OFF,
    /**
     * Short biometric of the Drive file this device syncs to; differing values mean different
     * files. Null if sync is off.
     */
    val syncFileFingerprint: String? = null,
    /**
     * Set when the garage fetch came back empty because a request actually failed (network/API
     * error), not because the account genuinely has zero vehicles. Distinguishes a real failure
     * from "not signed in" / "no vehicles" on the status card GarageScreen folds in as a page when
     * there are no vehicles (see GarageStatusCard, Guard.kt).
     */
    val garageLoadError: String? = null,
    /**
     * Whether [garageLoadError] happened while the device had no real internet connectivity, as
     * opposed to a live network reporting an actual API/auth failure (Hyundai/Kia's own servers
     * down for maintenance, an expired token, etc).
     */
    val garageLoadOffline: Boolean = false,
) {
    fun statusFor(v: Vehicle): VehicleStatus? = statuses[v.vin]

    fun fetchedAt(v: Vehicle): Long? = lastFetched[v.vin]

    fun isPending(vin: String, action: String): Boolean = "$vin:$action" in pending

    fun isPebbleExpanded(vin: String, section: String): Boolean = "$vin:$section" !in collapsedPebbles

    /**
     * Pebbles pinned to the hotspot as a list for rendering. Primary slot is always "controls"
     * (lights/horn) - permanently pinned. Secondary slot is user-selectable from hotspotSections.
     * Returns a list: ["controls"] or ["controls", userSelectedPebble]
     */
    fun hotspotFor(vin: String): List<String> {
        val secondary = hotspotSections[vin]
        return if (secondary.isNullOrEmpty()) listOf("controls") else listOf("controls", secondary)
    }

    fun isShortcutEnabled(vin: String, cmd: String): Boolean =
        shortcutSet?.contains("${cmd}_$vin") ?: true

    fun seatConfigFor(v: Vehicle): SeatConfig = seatConfigs[v.vin] ?: SeatConfig()

    fun sectionsFor(v: Vehicle): List<String> = sectionOrders[v.vin] ?: DEFAULT_SECTIONS

    /**
     * Effective powertrain: user override, else EV/gas inferred from the API. Delegates to
     * [com.bloo.bluelink.data.resolvePowertrain], the one shared rule every powertrain-dependent
     * surface in the app (this, and the background alert path in
     * [com.bloo.bluelink.data.CarAlerts]) resolves through -- see that function's own doc.
     */
    fun powertrainOf(v: Vehicle): Powertrain =
        com.bloo.bluelink.data.resolvePowertrain(v, powertrains[v.vin])

    /** Has a high-voltage battery you can charge (EV or plug-in hybrid). */
    fun hasBattery(v: Vehicle): Boolean = powertrainOf(v) == Powertrain.EV || powertrainOf(v) == Powertrain.PHEV

    /** Burns fuel (everything except a pure EV). */
    fun hasFuel(v: Vehicle): Boolean = powertrainOf(v) != Powertrain.EV

    /**
     * Effective head-unit generation: user override, else API inference. Not overridable where
     * [com.bloo.bluelink.data.platformOverridable] is false.
     */
    fun platformOf(v: Vehicle): VehiclePlatform =
        platforms[v.vin] ?: if (v.isGen5W) VehiclePlatform.GEN5W else VehiclePlatform.CCNC

    /** [com.bloo.bluelink.data.isGen5W] honouring the user override; all UI gates read this. */
    fun isGen5WEffective(v: Vehicle): Boolean = platformOf(v) == VehiclePlatform.GEN5W

    /**
     * [com.bloo.bluelink.data.supportsConnectedStore], honouring the same override
     * [isGen5WEffective] does -- mirrors that property's own Kia-is-always-eligible special case.
     */
    fun supportsConnectedStoreEffective(v: Vehicle): Boolean =
        v.brand == com.bloo.bluelink.data.Brand.KIA || (v.platformOverridable && platformOf(v) == VehiclePlatform.CCNC)

    /**
     * Whether [section] has anything to show for [v]; the one predicate every section list filters
     * through. Caller-specific gates (the "charge" hasBattery check, CompactKnownTiles membership)
     * are applied on top.
     */
    fun isSectionAvailable(v: Vehicle, section: String): Boolean {
        return when (section) {
            "ai" -> aiEnabled
            // The trip-details feed is EV-only, Gen5W head units don't serve it at all, and only
            // Hyundai/Genesis US actually has the endpoint (see Brand.supportsTrips) -- Kia US,
            // every Canada brand and Europe all route to repositories with no trips() override, so
            // they'd render nothing too.
            "trips" -> hasBattery(v) && !isGen5WEffective(v) && v.brand.supportsTrips
            // `!updateTileDismissed`: a dismissed tile renders nothing and would leave a phantom
            // slot.
            "update" -> updateAvailable != null && !updateTileDismissed
            else -> true
        }
    }

    /**
     * Whether the car is moving/on, for the header. Speed (from a location fix) is the strongest
     * signal; otherwise fall back to the ignition state. Null when we genuinely can't tell (e.g. an
     * EV that's never been located).
     */
    fun drivingLabel(v: Vehicle): String? {
        val status = statusFor(v)
        // Charging means parked; never show a driving badge.
        if (status?.evStatus?.batteryCharge == true) return null
        val engine = status?.engine
        return when {
            // Use isDriving, not a separate speed lookup, so the badge and the climate gate agree.
            isDriving(v) -> "Driving"
            engine == true -> "Running"
            engine == false -> "Parked"
            else -> null
        }
    }

    /** True when the car is moving; makes climate read-only (the car rejects it while driving). */
    fun isDriving(v: Vehicle): Boolean = (speedOf(v) ?: 0.0) > 0.0

    /**
     * The car's best-known speed: the tracked location's, else the last status's. One accessor so
     * [isDriving] and [drivingLabel] agree; `locations` is fresher but only written with lat AND
     * lon.
     */
    fun speedOf(v: Vehicle): Double? =
        locations[v.vin]?.speed ?: statusFor(v)?.vehicleLocation?.speed?.value

    /** Powertrain label for the header. */
    fun powertrainLabel(v: Vehicle): String = when (powertrainOf(v)) {
        Powertrain.GAS -> "Gas"
        Powertrain.HYBRID -> "Hybrid"
        Powertrain.PHEV -> "PHEV"
        Powertrain.EV -> "EV"
    }
}
