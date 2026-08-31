package com.bloo.bluelink.ui

/**
 * The app's immutable UI state snapshot, its screen dispatcher, and the pure
 * decision functions that answer "what shows for this car".
 *
 * Extracted out of AppViewModel.kt so the pure part of the state layer
 * compiles and pins independently of the ViewModel's networking: this file
 * is where values are ANSWERED (section order, expand/hide/pending keys,
 * powertrain/platform inference, section availability); AppViewModel is where
 * they COME from. Everything here is plain Kotlin over plain data -- no
 * Android, no coroutines -- which is why the tests in UiStateLogicTest run
 * as JVM unit tests.
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    /** The bootstrapping state, and ONLY the bootstrapping state: shown for
     *  the brief window between process start and the cold-start auto-login
     *  coroutine (see AppViewModel's init block) determining whether this is
     *  a genuinely logged-out device (-> Login) or a returning signed-in one
     *  (-> whatever loadGarage resolves once its network call returns). Used
     *  to be UiState.screen's own default was Login itself -- meaning a
     *  returning, already-authenticated user saw the full interactive login
     *  form (fields, brand picker) render first on EVERY cold start, then
     *  get slid/faded away once the real screen resolved a network round
     *  trip later. That read as both a flash (a screen that doesn't belong
     *  appearing then animating away) and the reported launch lag (staring
     *  at an irrelevant form for however long the network takes). Renders as
     *  just the app's own background -- see its own render branch
     *  (Screens.kt) for why nothing else belongs here. */
    data object Loading : Screen
    data object Login : Screen
    /** No vehicles enrolled (or still loading the first time). */
    data object Empty : Screen
    /** First-run welcome wizard that sets up car features before reaching the app. */
    data object Onboarding : Screen
    /** Feature-setup wizard for one or more newly-detected cars (post-first-run). */
    data class CarSetup(val vins: List<String>) : Screen
    /** Main screen: the car carousel/grid. */
    data object Garage : Screen
    data object Settings : Screen
}

/**
 * PERF, and it matters more than it looks: every pebble on every live car-pager
 * page takes this whole object as a parameter. Kotlin's `List`/`Map`/`Set` are
 * interfaces, so without this annotation the Compose compiler infers UiState as
 * UNSTABLE, which makes every one of those composables NON-SKIPPABLE — they
 * re-execute whenever their parent recomposes, even when the state instance is
 * bit-for-bit the same one.
 *
 * That was the car-swipe lag. GarageScreen (the pager's parent) reads several
 * per-frame animation values in composition scope (the dots fade, the pull
 * shift), so a single 200ms fade recomposed GarageScreen ~12 times, and each of
 * those recomposed all ~30 pebbles across the three pages
 * beyondViewportPageCount=1 keeps live — for a state object that never changed.
 *
 * The promise this annotation makes is real here: every property is a `val`, and
 * every collection is rebuilt by `copy()` (`_state.update { it.copy(...) }`)
 * rather than mutated in place, so `equals` is a sound recomposition signal.
 * Keep it that way — never put a MutableList/mutableStateListOf field here.
 *
 * Four setters used to reach that same end by a route that read like the thing
 * the line above forbids — toMutableList(), edit the copy, store it. Sound,
 * since the copy was fresh and dropped straight after, but it left the file
 * arguing with itself about its own invariant. They build the new value
 * outright now, so the rule and the code agree.
 */
@androidx.compose.runtime.Immutable
data class UiState(
    // Screen.Loading, NOT Screen.Login -- see Screen.Loading's own doc for
    // why defaulting straight to Login was a real, every-launch bug for any
    // returning signed-in user.
    val screen: Screen = Screen.Loading,
    /** Biometric app-lock overlay: the real app renders (blurred) behind it.
     *  True when the biometric lock is ON and usable, or when an app PIN is
     *  installed (see [UiState.appPinSet]) -- the overlay then decides which
     *  mechanism to present. */
    val locked: Boolean = false,
    /** An app PIN is installed (device unlock PIN, 4-8 digits) -- see the
     *  PIN APIs on AppViewModel. Mirrored from CredentialStore. */
    val appPinSet: Boolean = false,
    /** Live mirror of the PIN wrong-attempt lockout policy (consecutive
     *  failures + rejection-window deadline). Written by AppViewModel on
     *  every verify attempt and on cold start. */
    val pinLockout: PinLockout = PinLockout(),
    /** True just after a PIN verify rejected the attempt (wrong PIN, or
     *  rejected while the window is open) -- the overlay shows the error +
     *  remaining-attempts line, then calls
     *  [AppViewModel.acknowledgePinRejection] to clear it. */
    val pinAttemptRejected: Boolean = false,
    /** Bumped every time a PIN verify SUCCEEDS (the settings dialogs listen
     *  for the tick to advance past the "enter your current PIN" stage --
     *  success itself is otherwise silent, since the app may already be
     *  unlocked when it happens in Settings). */
    val pinAcceptedTick: Int = 0,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    // currentIndex is deliberately NOT here -- see AppViewModel.currentIndex.
    /** On large screens, the index expanded to full screen (null = grid view). */
    val expandedIndex: Int? = null,
    val statuses: Map<String, VehicleStatus> = emptyMap(),
    /** Wall-clock millis the app last pulled status from the server, keyed by VIN. */
    val lastFetched: Map<String, Long> = emptyMap(),
    val locations: Map<String, GeoLocation> = emptyMap(),
    /** Recent EV trips by VIN (loaded lazily when the Trips pebble is shown). */
    val trips: Map<String, List<EvTrip>> = emptyMap(),
    /** User-named climate presets by VIN. */
    val climatePresets: Map<String, List<ClimatePreset>> = emptyMap(),
    /** Live climate draft mirrored from the watch, by VIN (two-way climate sync). */
    val climateSync: Map<String, com.bloo.bluelink.data.ClimateSync> = emptyMap(),
    val seatConfigs: Map<String, SeatConfig> = emptyMap(),
    val powertrains: Map<String, Powertrain> = emptyMap(),
    /** User-confirmed head-unit generation, by VIN -- see [platformOf]. */
    val platforms: Map<String, VehiclePlatform> = emptyMap(),
    val sectionOrders: Map<String, List<String>> = emptyMap(),
    val imageUrls: Map<String, String> = emptyMap(),
    val placeNames: Map<String, String> = emptyMap(),
    /** Current weather at the user's configured "home" location, if loaded. */
    val homeWeather: Weather? = null,
    /** Current weather at each car's last-known location, keyed by VIN. */
    val carWeather: Map<String, Weather> = emptyMap(),
    val licensePlates: Map<String, String> = emptyMap(),
    val lastServiceMiles: Map<String, Int> = emptyMap(),
    val serviceIntervalMiles: Map<String, Int> = emptyMap(),
    /** In-flight commands, keyed "vin:action", so each control can show its own spinner. */
    val pending: Set<String> = emptySet(),
    /** Recent remote commands (Lock/Unlock/Climate/etc.) by VIN, newest first,
     *  capped at [REMOTE_ACTION_HISTORY_LIMIT] entries per car -- written by
     *  [AppViewModel.runCommand] as the single choke point every remote
     *  command already passes through. Surfaced in RemoteActionsHistoryCard. */
    val remoteActionHistory: Map<String, List<RemoteAction>> = emptyMap(),
    /** Collapsed pebbles, keyed "vin:section". Absent = expanded. */
    val collapsedPebbles: Set<String> = emptySet(),
    /** Hidden pebbles, keyed "vin:section". */
    val hiddenPebbles: Set<String> = emptySet(),
    /** Per-VIN pebble pinned to the dual-column "hot spot" (under car info). */
    val hotspotSections: Map<String, String> = emptyMap(),
    /** Quick-tile assignments: index -> (vin, command), or null if unassigned. */
    val tileConfigs: List<Pair<String, String>?> = List(12) { null },
    /** Optional per-tile custom names (index -> label or null). */
    val tileLabels: List<String?> = List(12) { null },
    /** Per-tile climate target: "default", "smart", or a preset id. */
    val tileClimateTargets: List<String> = List(12) { "default" },
    /** Quick tiles run the command in the background (vs opening the app). */
    val tileBackground: Boolean = false,
    /** Quick tiles kick a throttled status refresh when shown. */
    val tileLiveRefresh: Boolean = false,
    /** Enabled app-icon shortcut ids ("cmd_vin"); null = show all. */
    val shortcutSet: Set<String>? = null,
    /** Whether Shizuku is installed + running, so the "seamless install" toggle is
     *  worth showing. The toggle itself lives on Appearance (seamlessInstallShizuku). */
    val shizukuAvailable: Boolean = false,
    /** On-device Gemini Nano availability + opt-in, and produced summaries. */
    val aiSupported: Boolean = false,
    val aiEnabled: Boolean = false,
    /** Run AI summaries automatically on open/refresh/command (manual still works). */
    val aiAuto: Boolean = false,
    val aiSummaries: Map<String, String> = emptyMap(),
    /** In-flight AI work: VINs being summarized, plus "search" for the query box. */
    val aiBusy: Set<String> = emptySet(),
    val aiSearchReply: String? = null,
    /** First-run coach mark on the Settings screen (points at the back arrow). */
    val showSettingsCoach: Boolean = false,
    /** One-shot: set by [AppViewModel.closeSettings] when Settings itself just
     *  switched INTO page mode and asked to be followed there, so flipping
     *  Appearance.settingsAsPage from inside Settings reads as one continuous
     *  move (still looking at Settings, just presented differently) instead of
     *  "close Settings, land on whichever car was last selected, then go find
     *  the page yourself." Consumed once by GarageScreen's collapsed pager
     *  (see its own LaunchedEffect) via [AppViewModel.consumeLandOnSettingsPage]. */
    val landOnSettingsPage: Boolean = false,
    /** Whether the garage's collapsed pager is currently settled on its
     *  Settings slot (Appearance.settingsAsPage) -- kept in sync by
     *  GarageScreen's own pager-settle effect. AppRoot ORs this into the same
     *  "are we looking at Settings" signal `screen == Screen.Settings` already
     *  drives, so SearchLayer's floating bubble/pill morph reflects reality
     *  while the embedded page is showing too, not just the standalone route.
     *  Without it the search element stayed a garage "bubble" the whole time
     *  the embedded Settings page was on screen, then visibly snapped to a
     *  "pill" only once you swiped back off it and the flag caught up on
     *  something else entirely -- exactly the kind of un-seamless style
     *  transition this exists to prevent. */
    val onSettingsPageSlot: Boolean = false,
    /** Gentle hint shown on the garage right after onboarding, nudging the user
     *  toward Settings to fine-tune each car. */
    val showSettingsHint: Boolean = false,
    /** All signed-in accounts (one per brand). */
    val accounts: List<Credentials> = emptyList(),
    /** Showing the login form to add another account while already signed in. */
    val addingAccount: Boolean = false,
    /** True when the user backed out of the biometric prompt to the login screen.
     *  Cancelling in this state must re-lock rather than navigate to the garage. */
    val lockedToLogin: Boolean = false,
    /** Kia sign-in only: a pending one-time-code challenge. */
    val kiaOtp: KiaOtpUi? = null,
    /** Canada sign-in only (Hyundai/Genesis/Kia): a pending one-time-code challenge. */
    val canadaOtp: CanadaOtpUi? = null,
    val message: String? = null,
    /** "error" (default), "success", or "info" — controls snackbar colour. */
    val messageType: String = "error",
    /** A newer CI build than what's installed, if the update checker found
     *  one. Drives the standalone update tile that's pinned right below the
     *  hero tile -- null means no tile, regardless of [updateTileDismissed]. */
    val updateAvailable: com.bloo.bluelink.update.UpdateInfo? = null,
    /** "Not now" on the update tile: hides it until the NEXT update check (any
     *  Available result clears it — see checkForUpdate). "Remind me" also sets
     *  this but pairs it with a snooze + a 1-day reminder worker. */
    val updateTileDismissed: Boolean = false,
    /** True during the brief undo/call-back window after the user dismisses the
     *  update tile: the tile stays visible with an "Undo" strip; a short timer
     *  then commits [updateTileDismissed] unless the user calls it back. */
    val updatePendingDismiss: Boolean = false,
    /** True while the update APK is being downloaded in-app (see
     *  AppViewModel.downloadUpdateInBackground). */
    val updateDownloading: Boolean = false,
    // Download progress (0-1) is NOT here: it ticks per 64KB chunk, which would recompose every
    // pebble on the live pager pages hundreds of times per download. It lives in
    // AppViewModel.updateDownloadProgress (a StateFlow) and is collected only by the tile that
    // shows it. updateDownloading (this low-frequency boolean) stays and gates that display.
    /** True once the update APK has finished downloading and is sitting in
     *  the cache ready to hand to the installer -- lets the update tile's
     *  button do "tap to download, tap again to install". Reset by a fresh
     *  update check finding a different/no build available. */
    val updateApkReady: Boolean = false,
    /** True while a Shizuku seamless install is running, so a second Install tap (or a
     *  permission-grant retry) can't spawn a concurrent PackageInstaller session. */
    val updateInstalling: Boolean = false,
    /** True while a manual "Check for updates" is in flight (drives the Settings
     *  button's spinner + disables it). Auto/background checks don't set this. */
    val updateChecking: Boolean = false,
    /** Settings mode: "simple" (essential settings) or "advanced" (all settings). */
    val settingsMode: String = "simple",
    /** Per-VIN default preset ID for the one-tap climate Start button. */
    val defaultClimatePresets: Map<String, String> = emptyMap(),
    /** Drive URI (content://...) for auto-backup; null when not configured. */
    val syncUri: String? = null,
    /** Last time settings were synced with Drive (ms), for merge decisions. */
    val lastSyncMs: Long = 0L,
    /** Wi-Fi only sync (true) or any network (false). */
    val syncWifiOnly: Boolean = true,
    /** Reason the last Drive sync attempt didn't fully succeed, or null if it did
     *  (or hasn't run yet). Cleared by the next attempt that succeeds. */
    val syncError: String? = null,
    /** Devices sharing this sync file (name/model/last-seen), for the Settings
     *  "your devices" list. From the last sync's merged registry / cache. */
    val syncDevices: List<com.bloo.bluelink.data.SyncMerge.SyncDevice> = emptyList(),
    /** The device id designated primary (source of truth), or null if none. */
    val syncPrimaryId: String? = null,
    /** This device's own sync id, so the UI can mark "This device" and hide
     *  "Make primary" on self. */
    val thisDeviceId: String? = null,
    /** This device's friendly sync name (editable in Settings). */
    val syncDeviceName: String = "",
    /** Short fingerprint of the actual Drive file this device syncs to. Two
     *  devices showing DIFFERENT fingerprints are on different files (the main
     *  reason sync doesn't converge). Null when sync isn't set up. */
    val syncFileFingerprint: String? = null,
    /** Set when the garage fetch came back empty because a request actually
     *  failed (network/API error), not because the account genuinely has zero
     *  vehicles. Distinguishes a real failure from "not signed in" / "no
     *  vehicles" in [Screen.Empty]. Cleared by the next successful load. */
    val garageLoadError: String? = null,
) {
    fun statusFor(v: Vehicle): VehicleStatus? = statuses[v.vin]

    fun fetchedAt(v: Vehicle): Long? = lastFetched[v.vin]

    fun isPending(vin: String, action: String): Boolean = "$vin:$action" in pending

    fun isPebbleExpanded(vin: String, section: String): Boolean = "$vin:$section" !in collapsedPebbles

    fun isPebbleHidden(vin: String, section: String): Boolean = "$vin:$section" in hiddenPebbles

    fun hotspotFor(vin: String): String? = hotspotSections[vin]

    fun isShortcutEnabled(vin: String, cmd: String): Boolean =
        shortcutSet?.contains("${cmd}_$vin") ?: true

    fun seatConfigFor(v: Vehicle): SeatConfig = seatConfigs[v.vin] ?: SeatConfig()

    fun sectionsFor(v: Vehicle): List<String> = sectionOrders[v.vin] ?: DEFAULT_SECTIONS

    /** Effective powertrain: user override, else EV/gas inferred from the API. */
    fun powertrainOf(v: Vehicle): Powertrain =
        powertrains[v.vin] ?: if (v.isEv) Powertrain.EV else Powertrain.GAS

    /** Has a high-voltage battery you can charge (EV or plug-in hybrid). */
    fun hasBattery(v: Vehicle): Boolean = powertrainOf(v) == Powertrain.EV || powertrainOf(v) == Powertrain.PHEV

    /** Burns fuel (everything except a pure EV). */
    fun hasFuel(v: Vehicle): Boolean = powertrainOf(v) != Powertrain.EV

    /** Effective head-unit generation: user override, else inferred from the
     *  API the same way [com.bloo.bluelink.data.isGen5W] always has. For a
     *  vehicle where [com.bloo.bluelink.data.platformOverridable] is false
     *  (Kia US, Canada, Europe) there is nothing to override -- any stored
     *  [platforms] entry for it is simply never written (the picker that
     *  writes one is gated the same way), so this always falls through to
     *  the API inference for those. */
    fun platformOf(v: Vehicle): VehiclePlatform =
        platforms[v.vin] ?: if (v.isGen5W) VehiclePlatform.GEN5W else VehiclePlatform.CCNC

    /** [com.bloo.bluelink.data.isGen5W], but honouring a user override --
     *  every UI gate that used to read the raw property directly (Trips
     *  availability, the connected-store link, Digital Car Key's DK1/DK2
     *  branch, the Gen5W EV seat-climate hide) reads this instead, so all of
     *  them move together when the user corrects their car's generation. */
    fun isGen5WEffective(v: Vehicle): Boolean = platformOf(v) == VehiclePlatform.GEN5W

    /** [com.bloo.bluelink.data.supportsConnectedStore], honouring the same
     *  override [isGen5WEffective] does -- mirrors that property's own
     *  Kia-is-always-eligible special case. */
    fun supportsConnectedStoreEffective(v: Vehicle): Boolean =
        v.brand == com.bloo.bluelink.data.Brand.KIA || (v.platformOverridable && platformOf(v) == VehiclePlatform.CCNC)

    /**
     * Whether [section] has anything to show for [v] -- the one predicate every list of
     * pebbles/sections filters through, so they can't disagree about what exists.
     *
     * There were four hand-maintained copies of this over in the Screens file -- the
     * cover-screen tile list, the two-column hotspot slot, the hotspot picker menu, and
     * PebbleList -- and no two carried the same set of gates. The AI gate was missing from two of
     * them, so the two-column layout's hotspot slot and its picker offered -- and would
     * pin -- the AI pebble on a car with AI turned off, while the ordinary pebble list
     * correctly hid it.
     *
     * Two differences between those copies were deliberate and are deliberately NOT
     * folded in here, because they belong to one caller each:
     *
     *  - "charge" is gated on hasBattery only by the cover screen. Everywhere else
     *    SinglePebble renders a FuelPebble instead for a car with no battery, so the
     *    section still has something to show. The cover has no such fallback tile.
     *  - Membership of CompactKnownTiles is the cover's own business.
     *
     * Callers keep applying those on top of this.
     */
    fun isSectionAvailable(v: Vehicle, section: String): Boolean {
        if (isPebbleHidden(v.vin, section)) return false
        return when (section) {
            "ai" -> aiEnabled
            // The trip-details feed is EV-only, Gen5W head units don't serve it at all,
            // and only Hyundai/Genesis US actually has the endpoint (see
            // Brand.supportsTrips) -- Kia US, every Canada brand and Europe all route
            // to repositories with no trips() override, so they'd render nothing too.
            // On the phone that leaves a phantom slot with a spacedBy gap on either
            // side of it; on the cover screen's swipeable pager it is worse -- a
            // completely blank page a user can swipe straight into, with the page-dots
            // rail still showing a dot for it. TripsPebble's own `!v.brand.supportsTrips
            // -> return` guard only stops it from drawing anything, not from being
            // offered a slot in the first place.
            "trips" -> hasBattery(v) && !isGen5WEffective(v) && v.brand.supportsTrips
            // `!updateTileDismissed` too: UpdateAvailableTile renders nothing once dismissed,
            // so the section stayed "available" and left a zero-height slot with a spacedBy gap
            // on either side of it -- the same phantom-slot shape the "trips" line above
            // documents guarding against.
            "update" -> updateAvailable != null && !updateTileDismissed
            else -> true
        }
    }

    /**
     * Whether the car is moving/on, for the header. Speed (from a location fix)
     * is the strongest signal; otherwise fall back to the ignition state. Null
     * when we genuinely can't tell (e.g. an EV that's never been located).
     */
    fun drivingLabel(v: Vehicle): String? {
        val status = statusFor(v)
        // If it's charging it's definitely parked — don't show a driving badge.
        if (status?.evStatus?.batteryCharge == true) return null
        val engine = status?.engine
        return when {
            // isDriving, NOT a second speed lookup. This used to read
            // `locations[v.vin]?.speed` on its own, with no fall back to the status's
            // own vehicleLocation.speed the way isDriving has -- and `locations` is
            // only populated when the status carried lat AND lon, while speed arrives
            // independently of them. So a status reporting movement without
            // coordinates made isDriving true and this label fall through to the
            // engine flag: the header read "Parked" while the Climate pebble was
            // simultaneously disabled as "can't start while driving".
            isDriving(v) -> "Driving"
            engine == true -> "Running"
            engine == false -> "Parked"
            else -> null
        }
    }

    /** True when the car is moving — used to make climate read-only (the car
     *  rejects remote climate commands while driving). */
    fun isDriving(v: Vehicle): Boolean = (speedOf(v) ?: 0.0) > 0.0

    /**
     * The car's best-known speed: the tracked location's if we have one, else whatever
     * the last status reported. One accessor so [isDriving] and [drivingLabel] cannot
     * disagree about which source wins -- they used to, and the badge and the climate
     * gate contradicted each other as a result.
     *
     * The two orderings are not interchangeable: `locations` is the fresher of the
     * pair, since a Locate updates it without a full status fetch, but it is only
     * written when a status carried lat AND lon, so the status is the fallback rather
     * than the other way round.
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
