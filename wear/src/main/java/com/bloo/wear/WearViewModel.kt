package com.bloo.wear

import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.BlueLinkGate
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.DoorOpen
import com.bloo.bluelink.data.EvStatus
import com.bloo.bluelink.data.EvTrip
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.StatusCache
import com.bloo.bluelink.data.UPDATE_SNOOZE_MS
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleRepository
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.WearClimateState
import com.bloo.bluelink.data.WindowOpen
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.maskEmail
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.repositoryFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Top-level screen the watch is showing: initial data load, no logged-in
 *  account, or the normal car-list/detail UI. */
enum class WearScreen { Loading, SignedOut, Ready }

// How often runUpdateCheck() is allowed to hit the GitHub Actions API on its
// own (no manual "check now" exists) -- gates the cold-start check in init so
// a restart-happy user doesn't hammer the endpoint every launch.
private const val UPDATE_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000L // 12h

/** See [WearViewModel.submitPin]'s doc comment: consecutive-wrong-PIN lockout. */
private const val PIN_MAX_ATTEMPTS = 5
private const val PIN_LOCKOUT_MS = 30_000L // 30s

/** A fully resolved per-car view, merging live status with the phone snapshot. */
data class CarView(
    val vin: String,
    val name: String,
    val model: String,
    val brand: Brand,
    val hasBattery: Boolean,
    val percent: Int?,
    val rangeMi: Int?,
    val locked: Boolean?,
    val climateOn: Boolean?,
    val charging: Boolean?,
    val pluggedIn: Boolean?,
    val chargerLabel: String?,
    val timeToFullMin: Int?,
    val acLimit: Int?,
    val dcLimit: Int?,
    val fetchedAt: Long?,
    val doorsOpen: List<String>,
    val windowsOpen: List<String>,
    val trunkOpen: Boolean,
    val hoodOpen: Boolean,
    val tireWarning: Boolean,
    val battery12v: Int?,
    val lowFuel: Boolean,
    val washerLow: Boolean,
    val brakeLow: Boolean,
    val keyFobLow: Boolean,
    val odometer: String?,
    val lat: Double?,
    val lon: Double?,
    val locationName: String?,
    val tripsSupported: Boolean,
    val engineOn: Boolean,
    val accessoryOn: Boolean,
    val defrostOn: Boolean,
    val tempSetting: String?,
    val tireAll: Int?,
    val tireFl: Boolean,
    val tireFr: Boolean,
    val tireRl: Boolean,
    val tireRr: Boolean,
    val steerHeat: Boolean,
    val mirrorHeat: Boolean,
    val rearDefrost: Boolean,
    val seatFl: Int?,
    val seatFr: Int?,
    val seatRl: Int?,
    val seatRr: Int?,
    val battery12vHealth: String?,
    val fuelLevel: Int?,
    val hasLiveStatus: Boolean,
    /** User-entered license plate and service-due tracking, synced from phone
     *  Settings (never entered directly on the watch). */
    val licensePlate: String?,
    val lastServiceMiles: Int?,
    val serviceIntervalMiles: Int?,
)

/** The editable climate settings for one car (seats are 0–3 heat steps). */
data class ClimateDraft(
    val tempF: Int = com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F,
    val duration: Int = com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN,
    val defrost: Boolean = false,
    val steering: Boolean = false,
    val seatDriver: Int = 0,   // 0 off, 1 low, 2 med, 3 high (heat)
    val seatPassenger: Int = 0,
    val seatRearLeft: Int = 0,
    val seatRearRight: Int = 0,
    /** Which saved preset (if any) is currently applied; cleared once a control
     *  drifts away from it. */
    val activePresetId: String? = null,
)

/** Unsaved AC/DC charge-limit slider values for one car; null = "show the car's
 *  actual current limit" (the sliders only override once the user drags them). */
data class ChargeLimitDraft(val ac: Int? = null, val dc: Int? = null)

/**
 * The single immutable snapshot of everything the watch UI renders, exposed
 * via [WearViewModel.ui]. Rebuilt wholesale by [WearViewModel.publish] every
 * time any underlying source (vehicle status, snapshots, settings, drafts,
 * pending commands, etc.) changes -- Compose screens just collect [WearViewModel.ui]
 * and never touch the ViewModel's private mutable state directly.
 */
data class WearUi(
    val screen: WearScreen = WearScreen.Loading,
    val cars: List<CarView> = emptyList(),
    val trips: Map<String, List<EvTrip>> = emptyMap(),
    val pending: Set<String> = emptySet(),
    val busy: Boolean = false,
    val message: String? = null,
    val presets: Map<String, List<ClimatePreset>> = emptyMap(),
    val extras: com.bloo.bluelink.data.WearExtras = com.bloo.bluelink.data.WearExtras(),
    /** VIN currently waiting on an AI summary from the phone, for a spinner. */
    val aiBusy: String? = null,
    /** True while a "Sync now" (Drive) request is waiting on the phone's reply. */
    val driveSyncBusy: Boolean = false,
    val accounts: List<String> = emptyList(),
    val phoneConnected: Boolean = false,
    /** Per-car climate draft (sliders/toggles), so each car remembers its own. */
    val climateDrafts: Map<String, ClimateDraft> = emptyMap(),
    /** Per-car charge-limit sliders, unsaved until Apply. Per-VIN so dragging on
     *  one car can't bleed onto another's Limits tile. */
    val chargeLimitDrafts: Map<String, ChargeLimitDraft> = emptyMap(),
    val settings: com.bloo.bluelink.data.WearSettingsPayload? = null,
    val localSettings: WearLocalSettings = WearLocalSettings(),
    /** Optimistic per-car pebble orders the watch just set, held until the phone
     *  echoes the same order back via [settings]. */
    val pebbleOverride: Map<String, List<String>> = emptyMap(),
    /** Optimistic overrides for settings fields the watch itself just changed
     *  (aiEnabled/auroraEnabled/auroraColorMode), held until the phone's echo
     *  matches -- same reasoning as [pebbleOverride]. Without this, any OTHER
     *  PATH_SETTINGS publish landing between the optimistic toggle and its
     *  own echo (a concurrent theme change, or one of the many other events
     *  that already trigger a settings republish) carried the pre-toggle
     *  value and stomped the toggle back to stale. */
    val settingsOverride: Map<String, Any?> = emptyMap(),
    /** A newer CI build than what's installed, if found and not snoozed/disabled.
     *  Checked entirely independently of the phone -- see runUpdateCheck. */
    val updateRun: com.bloo.bluelink.data.WorkflowRun? = null,
    /** True while the update APK is downloading (see downloadAndInstallUpdate). */
    val updateDownloading: Boolean = false,
    /** True when the PIN lock gate (see PinLockScreen) is covering the app. */
    val pinLocked: Boolean = false,
    /** True while a manual "Sync from phone" (resync) is in flight -- every
     *  other network-triggered button in this screen (lock, refresh, Drive
     *  sync, AI summary, charge limits) shows a busy/disabled state; this one
     *  didn't, so it could be tapped repeatedly with no feedback that
     *  anything was happening. */
    val resyncBusy: Boolean = false,
    /** VINs whose last trip fetch failed (network/API error), so TripsScreen
     *  can show "Couldn't load trips" instead of the exact same "No recent
     *  trips" text a car with a genuinely empty history would show. Cleared
     *  by the next successful fetch for that VIN. */
    val tripsErrors: Set<String> = emptySet(),
) {
    /** This car's climate draft, or a fresh default one if it's never been touched/synced. */
    fun draftFor(vin: String): ClimateDraft = climateDrafts[vin] ?: ClimateDraft()
    /** This car's charge-limit slider draft, or a fresh (unset) one if untouched. */
    fun chargeDraftFor(vin: String): ChargeLimitDraft = chargeLimitDrafts[vin] ?: ChargeLimitDraft()

    /** This car's effective pebble order: a pending local change wins, else the
     *  phone-synced order, else the default. */
    fun pebbleOrderFor(vin: String): List<String> =
        pebbleOverride[vin] ?: settings?.pebbleOrders?.get(vin) ?: WearPebbles.DEFAULT_ORDER
}

/** Map the watch UI's 0–3 heat step (Off/Low/Med/High) to the API's
 *  [SeatLevel] enum. The watch has no cooling control, only heat. */
private fun seatLevelOf(step: Int): SeatLevel = when (step) {
    1 -> SeatLevel.LOW_HEAT
    2 -> SeatLevel.MED_HEAT
    3 -> SeatLevel.HIGH_HEAT
    else -> SeatLevel.OFF
}

/** Build a [ClimateRequest] from this draft. [tempF]/[defrost] default to the
 *  draft's own but can be overridden (smart climate supplies a computed temp). */
private fun ClimateDraft.toRequest(tempF: Int = this.tempF, defrost: Boolean = this.defrost) =
    ClimateRequest(
        tempF = tempF,
        defrost = defrost,
        durationMinutes = duration,
        steeringWheelHeat = steering,
        seatFrontLeft = seatLevelOf(seatDriver),
        seatFrontRight = seatLevelOf(seatPassenger),
        seatRearLeft = seatLevelOf(seatRearLeft),
        seatRearRight = seatLevelOf(seatRearRight),
    )

/** Inverse of [seatLevelOf]: map a seat level back to the watch's 0–3 heat step
 *  (the watch UI is heat-only, so cooling collapses to off). */
private fun seatStepOf(level: SeatLevel): Int = when (level) {
    SeatLevel.LOW_HEAT -> 1
    SeatLevel.MED_HEAT -> 2
    SeatLevel.HIGH_HEAT -> 3
    else -> 0
}

/** Display labels for the 0–3 seat-heat steps used by [seatStepOf]/[seatLevelOf]. */
val seatStepLabels = listOf("Off", "Low", "Med", "High")

/**
 * The Wear OS app's single ViewModel. Every user-visible piece of state (car
 * list, climate drafts, presets, settings, PIN lock, update banner) flows
 * through the single [_ui] StateFlow exposed as [ui].
 *
 * The central architectural quirk of this ViewModel, relative to a normal
 * single-device ViewModel, is that almost every command (lock/unlock, climate,
 * charge, charge limits, horn/lights) can execute in one of two completely
 * different ways depending on whether a phone is currently reachable over the
 * Wearable Data Layer:
 *
 *  - "Relayed": the command is serialized into a [com.bloo.bluelink.data.WearCommand]
 *    and sent to the phone (see [WearComms.send]), which owns the real
 *    BlueLink/network session and actually talks to the car. The watch has no
 *    ack channel for the phone's own execution result here -- it only knows
 *    whether the SEND succeeded, not whether the phone's subsequent API call
 *    did. A later out-of-band failure notice can arrive via
 *    [WearCommandEvents.results] (wired up in [init]) and triggers a
 *    corrective status refresh rather than trying to hand-roll a revert.
 *  - "Standalone": when no phone is reachable, the watch has its own signed-in
 *    BlueLink session (see [repoFor]/[repos]) and calls the vehicle API
 *    directly, exactly like the phone app would.
 *
 * Both paths apply the SAME "optimistic" local-state update pattern before any
 * network confirmation comes back: the relay path patches [snapshots]/
 * [statuses] via [com.bloo.bluelink.data.WearCommandRunner.optimistic] inside
 * [command], while the standalone path patches [statuses] via [flip] once its
 * own suspend block returns successfully. Either way, [publish] is called
 * immediately afterward so the UI reacts instantly instead of waiting on a
 * round trip. All shared command logic funnels through the private [command]
 * helper near the bottom of this file.
 */
class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    private val sessionStore = SessionStore(ctx)
    private val credentialStore = CredentialStore(ctx)
    private val snapshotStore = SnapshotStore(ctx)
    private val statusCache = StatusCache(ctx)
    private val repos = mutableMapOf<Brand, VehicleRepository>()
    private val localStore = WearLocalStore(ctx)

    // These @Volatile fields are the ViewModel's real source of truth; [_ui] is
    // just a derived projection rebuilt by [publish] (via [buildCarView])
    // whenever one of them changes. They're plain vars (not StateFlows)
    // because they're written from both the main-thread command functions and
    // background coroutines (snapshot collection, status fetches), and are
    // read synchronously by buildCarView on every publish() -- @Volatile gives
    // safe publication across threads without needing a full Mutex for what
    // are simple map-replacement writes.
    /** The garage's vehicle list, either fetched live (standalone) or derived
     *  from phone-synced [snapshots] (see [loadGarage]). */
    @Volatile
    private var vehicles: List<Vehicle> = emptyList()
    /** Live, richly-detailed status per VIN as fetched directly from the
     *  vehicle API (standalone commands) or synced down; [buildCarView]
     *  prefers this over [snapshots] wherever both have a field. */
    @Volatile
    private var statuses: Map<String, VehicleStatus> = emptyMap()
    /** The phone's lightweight, always-available view of each car -- what the
     *  UI falls back to when [statuses] hasn't been fetched yet for a VIN. */
    @Volatile
    private var snapshots: Map<String, com.bloo.bluelink.data.VehicleSnapshot> = emptyMap()
    /** Wall-clock time (ms) each VIN's [statuses] entry was last fetched, for
     *  the "last updated" display. */
    @Volatile
    private var fetchedAt: Map<String, Long> = emptyMap()
    @Volatile
    private var trips: Map<String, List<EvTrip>> = emptyMap()
    /** Reverse-geocoded place names per VIN, filled in lazily by
     *  [ensurePlaceName] and never persisted -- recomputed each app session. */
    @Volatile
    private var placeNames: Map<String, String> = emptyMap()
    @Volatile
    // Ref-counted rather than a plain Set: two overlapping calls with the same
    // key (e.g. a manual "Refresh" tap while onCarShown's own refresh for
    // that car is still in flight) used to collapse to one Set entry, so the
    // FIRST call's completion unconditionally cleared the key even though the
    // SECOND call's block() was still running -- the pending spinner/disabled
    // state vanished, and the button became tappable again, mid-request.
    private var pendingCounts: Map<String, Int> = emptyMap()
    // Exposed as a Set (just the keys) because that's all the UI needs to
    // know -- "is this key busy" -- the refcount itself is [mark]'s private
    // bookkeeping for handling overlapping in-flight calls correctly.
    private val pending: Set<String> get() = pendingCounts.keys

    // Cars whose status we've already fetched this session, so paging back and
    // forth doesn't re-hit the (rate-limited, battery-hungry) network each time.
    private val sessionFetched = mutableSetOf<String>()
    private val tripsFetched = mutableSetOf<String>()
    // Coords we've already attempted to reverse-geocode (per session), keyed by vin.
    private val geocoded = mutableSetOf<String>()

    private val _ui = MutableStateFlow(WearUi())
    val ui = _ui.asStateFlow()

    /** Lazily create (and cache) the standalone [VehicleRepository] for
     *  [brand], used by the watch's own direct/standalone command path. One
     *  repository instance is reused per brand for the life of the ViewModel
     *  so its underlying session/token state persists across calls. */
    private fun repoFor(brand: Brand) =
        repos.getOrPut(brand) { repositoryFor(brand, sessionStore, credentialStore) }

    init {
        // Mirrors the phone-synced WearSettingsPayload (pebble orders, AI/aurora
        // toggles, etc.) into ui.settings on every push. The tricky part is
        // reconciling this against the two *local* optimistic-override maps
        // (pebbleOverride, settingsOverride) that savePebbleOrder/setAiEnabled/
        // setAuroraEnabled/setAuroraColorMode write to instantly, before the
        // phone has echoed the change back: for each overridden key, compare
        // the incoming synced value to what's overridden -- if they now match,
        // the phone has caught up and the override is dropped (stillPending /
        // stillPendingSettings); if they don't match yet, the override is kept
        // and effectiveSettings substitutes it back in over the stale synced
        // value, so a settings push that lands *between* an optimistic toggle
        // and its own echo can't stomp the toggle back to its old value.
        viewModelScope.launch {
            WearSettingsStore(ctx).flow.collect { s ->
                _ui.update { u ->
                    // Drop any optimistic override once the phone has echoed the
                    // same order back, so the synced value takes over cleanly.
                    val stillPending = u.pebbleOverride.filterKeys { vin ->
                        WearPebbles.normalize(s?.pebbleOrders?.get(vin) ?: emptyList()) != u.pebbleOverride[vin]
                    }
                    val stillPendingSettings = u.settingsOverride.filterKeys { key ->
                        val incoming = when (key) {
                            "aiEnabled" -> s?.aiEnabled
                            "auroraEnabled" -> s?.auroraEnabled
                            "auroraColorMode" -> s?.auroraColorMode
                            else -> null
                        }
                        incoming != u.settingsOverride[key]
                    }
                    val effectiveSettings = s?.copy(
                        aiEnabled = (stillPendingSettings["aiEnabled"] as? Boolean) ?: s.aiEnabled,
                        auroraEnabled = (stillPendingSettings["auroraEnabled"] as? Boolean) ?: s.auroraEnabled,
                        auroraColorMode = (stillPendingSettings["auroraColorMode"] as? String) ?: s.auroraColorMode,
                    )
                    u.copy(settings = effectiveSettings, pebbleOverride = stillPending, settingsOverride = stillPendingSettings)
                }
            }
        }
        // Presets are stored on-disk (WearPresetsStore, a DataStore-backed
        // store) and synced with the phone; this just mirrors whatever is
        // currently persisted into ui.presets so both the watch's own writes
        // (saveCurrentAsPreset/deletePreset) and phone-originated syncs show
        // up the same way, through the same collector.
        viewModelScope.launch {
            WearPresetsStore(ctx).flow.collect { p -> _ui.update { it.copy(presets = p.byVin) } }
        }
        viewModelScope.launch {
            WearExtrasStore(ctx).flow.collect { e ->
                _ui.update { u ->
                    // extras also carries weather/photo updates on the same push,
                    // so only clear aiBusy when a NEW summary actually landed for
                    // the car we're waiting on -- otherwise an unrelated weather
                    // refresh silently kills the spinner and the real AI result
                    // (or failure) that arrives later gets dropped by WearAiEvents'
                    // own "still waiting on this vin" guard.
                    val busyVin = u.aiBusy
                    val gotNewSummary = busyVin != null && e.ai[busyVin] != null && e.ai[busyVin] != u.extras.ai[busyVin]
                    u.copy(extras = e, aiBusy = if (gotNewSummary) null else u.aiBusy)
                }
            }
        }
        // WearLocalSettings (font scale, unit system, PIN-lock config) lives
        // entirely on-device -- it's never synced FROM the phone, only pushed
        // TO it (see pushLocalPinSettings/setFontScale/setUnitSystem) so the
        // phone can show the watch's preferences too. This just keeps ui
        // mirrored to whatever's currently on disk.
        viewModelScope.launch {
            localStore.flow.collect { s -> _ui.update { it.copy(localSettings = s) } }
        }
        // Reply channel for syncDrive(): the phone posts its Drive-sync
        // result here once its own work finishes, which both clears the busy
        // spinner and surfaces a message. If the phone never replies,
        // syncDrive's own delay(15_000) safety net clears driveSyncBusy
        // instead so this collector effectively races that timeout.
        viewModelScope.launch {
            WearSyncEvents.results.collect { r ->
                _ui.update { it.copy(driveSyncBusy = false, message = r.message ?: if (r.ok) "Settings synced" else "Sync failed") }
            }
        }
        viewModelScope.launch {
            WearAiEvents.results.collect { r ->
                // Only touch state if we're still waiting on this exact car -- a
                // stale/duplicate reply (e.g. after a timeout already cleared it)
                // shouldn't clobber unrelated busy state or messages.
                _ui.update { if (it.aiBusy == r.vin) it.copy(aiBusy = null, message = r.message) else it }
            }
        }
        viewModelScope.launch {
            WearCommandEvents.results.collect { r ->
                // command()'s relay branch has no ack channel of its own -- it
                // applies its optimistic flip the instant the message SEND
                // succeeds, not once the phone's execution actually does. On
                // a real failure (the phone's BlueLink/Kia call itself
                // errored), re-pull the car's real status rather than trying
                // to hand-invert the optimistic value here -- FLASH_LIGHTS/
                // HORN_AND_LIGHTS have no state to invert at all, and a fresh
                // fetch is correct for every action uniformly.
                if (!r.ok) {
                    _ui.update { it.copy(message = r.message ?: "Command failed") }
                    refreshStatus(r.vin, surface = false)
                }
            }
        }
        viewModelScope.launch {
            WearClimateStore(ctx).flow.collect { remote -> mergeRemoteClimate(remote) }
        }
        viewModelScope.launch {
            // Live-collect snapshot updates: WearListenerService persists phone
            // pushes into SnapshotStore, and standalone command results land there
            // too. The store used to be read exactly once at bootstrap (and on
            // manual resync), so a relayed Refresh spun briefly and the screen then
            // kept showing the old data until an app restart.
            snapshotStore.payload.collect { data ->
                if (data.vehicles.isEmpty()) {
                    // A genuine push asserting zero vehicles (all cars removed
                    // on the phone, or a sign-out that didn't route through
                    // this watch's own signOutAll()) -- reconcile local state
                    // instead of silently keeping stale cars around forever.
                    // Only acts when there's actually something stale to
                    // clear, so repeated empty pushes are cheap no-ops.
                    if (snapshots.isNotEmpty() || vehicles.isNotEmpty()) {
                        snapshots = emptyMap()
                        vehicles = emptyList()
                        statuses = emptyMap()
                        trips = emptyMap()
                        publish()
                    }
                    return@collect
                }
                snapshots = data.vehicles.associateBy { it.vin }
                // buildCarView prefers a cached in-memory status over the snapshot,
                // so fold the snapshot's core fields into any status we hold -
                // otherwise the fresh push stays masked for lock/climate/charge.
                data.vehicles.forEach { snap ->
                    statuses[snap.vin]?.let { s ->
                        statuses = statuses + (snap.vin to s.copy(
                            doorLock = snap.locked ?: s.doorLock,
                            airCtrlOn = snap.climateOn ?: s.airCtrlOn,
                            evStatus = s.evStatus?.let { ev ->
                                ev.copy(batteryCharge = snap.charging ?: ev.batteryCharge)
                            },
                        ))
                    }
                }
                // Fresh data just landed for these VINs -- advance their "last
                // updated" stamps so buildCarView's fetchedAt label tracks it
                // (covers both the loadGarage and the plain publish path below).
                markFetched(data.vehicles.map { it.vin })
                if (vehicles.isEmpty() && sessionStore.loggedInBrands().isNotEmpty()) loadGarage()
                else publish()
            }
        }
        // Cold-start update check -- see runUpdateCheck's own doc comment.
        viewModelScope.launch { runUpdateCheck(force = false) }
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            // Resolve the PIN-lock setting BEFORE anything below can reach the
            // Ready screen with real car data. This used to run in a separate,
            // independently-scheduled coroutine racing this one -- if
            // bootstrap's phone-IPC calls (pullLatest, node lookup) happened to
            // resolve before that other coroutine's first DataStore emission,
            // the Ready screen could render, briefly showing real vehicle data,
            // before pinLocked ever flipped true.
            val settings = runCatching { localStore.flow.first() }.getOrNull()
            if (settings?.pinLockEnabled == true && settings.hasPin) {
                _ui.update { it.copy(pinLocked = true) }
            }
            runCatching { WearComms.pullLatest(ctx) }
            refreshConnection()
            snapshots = runCatching { snapshotStore.current().vehicles.associateBy { it.vin } }.getOrElse { snapshots }
            runCatching {
                val cached = statusCache.load()
                statuses = cached.statuses
                fetchedAt = cached.fetched
            }
            val brands = sessionStore.loggedInBrands()
            if (brands.isEmpty()) {
                _ui.update { it.copy(screen = WearScreen.SignedOut) }
            } else {
                // Keystore-backed EncryptedSharedPreferences init is disk + crypto IO —
                // do it off the main thread, and resolve before update{} so a lost CAS
                // race can't re-run the blocking read inside the retry lambda.
                val emails = withContext(Dispatchers.IO) {
                    runCatching { credentialStore.loadAll().map { c -> c.email } }.getOrDefault(emptyList())
                }
                _ui.update { it.copy(accounts = emails) }
                loadGarage()
            }
        }
    }

    /** Re-checks whether a phone is currently reachable over the Wearable Data
     *  Layer and updates [WearUi.phoneConnected]. This is the flag that most
     *  screens use to decide whether to describe a command as "relayed" vs
     *  "standalone" in their own copy -- the actual command dispatch in
     *  [command] doesn't consult this cached flag though, it always attempts
     *  a fresh relay send first and falls back to standalone only if that
     *  send itself fails, so this value can be a beat stale without breaking
     *  correctness. */
    fun refreshConnection() {
        viewModelScope.launch {
            // Resolve the (up to 10s) node lookup BEFORE update{}, so a lost CAS race
            // can't re-run the network round-trip inside the inline retry lambda.
            val connected = WearComms.phoneNodeId(ctx) != null
            _ui.update { it.copy(phoneConnected = connected) }
        }
    }

    // ---- Sign in / out ----------------------------------------------------

    /**
     * Signs the watch itself into a BlueLink/Genesis account (this is the
     * "standalone" credential path -- separate from whatever the phone is
     * signed into). Kia is refused here because Kia's login flow needs a
     * phone-side captcha/2FA step this watch screen can't render; Kia
     * accounts only ever reach the watch by syncing down from the phone.
     * On success the credentials are saved to [credentialStore] (so
     * [repoFor] can rebuild a session after a process death) and the garage
     * is (re)loaded.
     */
    fun login(brand: Brand, email: String, password: String, pin: String) {
        if (email.isBlank() || password.isBlank() || pin.isBlank()) {
            _ui.update { it.copy(message = "Email, password and PIN are required") }
            return
        }
        if (brand == Brand.KIA) {
            _ui.update { it.copy(message = "Sign in to Kia on your phone, then it syncs here") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            runCatching {
                val repo = repoFor(brand) as? BlueLinkRepository
                    ?: error("Sign-in isn't supported for ${brand.label} yet")
                repo.login(email.trim(), password, pin.trim())
                credentialStore.save(Credentials(email.trim(), password, pin.trim(), brand))
            }.onSuccess {
                AppLog.log("Watch sign-in: ${maskEmail(email.trim())} (${brand.label})")
                // Keystore-backed EncryptedSharedPreferences read is disk + crypto IO —
                // resolve it off the main thread into a val BEFORE update{} so a lost CAS
                // race can't re-run the blocking read inside the retry lambda (mirrors bootstrap()).
                val emails = withContext(Dispatchers.IO) {
                    runCatching { credentialStore.loadAll().map { c -> c.email } }.getOrDefault(emptyList())
                }
                _ui.update {
                    it.copy(busy = false, screen = WearScreen.Ready, accounts = emails)
                }
                loadGarage()
            }.onFailure { e ->
                _ui.update { it.copy(busy = false, message = e.message ?: "Sign-in failed") }
            }
        }
    }

    /** Logs out of every brand this watch has a standalone session for, and
     *  wipes all in-memory and on-disk vehicle state (vehicles/statuses/trips/
     *  snapshots) back to empty so a subsequent sign-in to a different
     *  account can't show the previous account's cars. */
    fun signOutAll() {
        viewModelScope.launch {
            sessionStore.loggedInBrands().forEach { b ->
                runCatching { repoFor(b).logout() }
                runCatching { credentialStore.clear(b) }
            }
            repos.clear()
            sessionFetched.clear(); tripsFetched.clear()
            vehicles = emptyList(); statuses = emptyMap(); trips = emptyMap()
            // Also drop the snapshots (in-memory AND on disk): loadGarage builds
            // the garage from them, so leaving the old account's cars here made a
            // later sign-in to a DIFFERENT account show the previous owner's
            // vehicles, states and locations.
            snapshots = emptyMap()
            runCatching { snapshotStore.saveVehicles(emptyList()) }
            requestWidgetUpdates()
            _ui.update { it.copy(screen = WearScreen.SignedOut, cars = emptyList(), trips = emptyMap(), accounts = emptyList()) }
        }
    }

    // ---- Loading ----------------------------------------------------------

    /**
     * Populates [vehicles] (the garage list) and moves the screen to [WearScreen.Ready].
     * Prefers whatever the phone has already synced into [snapshots] (the
     * common "companion" case); only falls back to hitting the vehicle-list
     * API directly (standalone) when there are no snapshots AND no phone is
     * currently reachable -- in that fallback it also seeds [snapshotStore]
     * with lightweight [com.bloo.bluelink.data.VehicleSnapshot]s built from the
     * fetch, so later app restarts have something to show even before a phone
     * connection is ever made. Per-car live [statuses] are deliberately NOT
     * fetched here -- that happens lazily per car via [onCarShown].
     */
    private suspend fun loadGarage() {
        // Companion mode: rely on phone-synced snapshots. Request a push if we have nothing.
        vehicles = if (snapshots.isNotEmpty()) {
            snapshots.values.map { it.toVehicle() }
        } else {
            runCatching { WearComms.requestSync(ctx, "", refresh = false) }
            // Standalone (no phone to push snapshots): fetch the vehicle list over
            // the watch's own connection and seed the snapshot store - otherwise a
            // watch-only sign-in landed on a permanently empty garage, since
            // nothing else on the watch ever calls the vehicle-list API.
            if (WearComms.phoneNodeId(ctx) == null) {
                val fetched = sessionStore.loggedInBrands().flatMap { b ->
                    runCatching { BlueLinkGate.statusMutex.withLock { repoFor(b).vehicles() } }.getOrDefault(emptyList())
                }
                if (fetched.isNotEmpty()) {
                    val snaps = fetched.map {
                        com.bloo.bluelink.data.VehicleSnapshot(
                            vin = it.vin, name = it.name, model = it.model, isEv = it.isEv,
                            regId = it.regId, generation = it.generation,
                            brandIndicator = it.brandIndicator,
                        )
                    }
                    runCatching { snapshotStore.saveVehicles(snaps) }
                    snapshots = snaps.associateBy { s -> s.vin }
                }
                fetched
            } else emptyList()
        }
        publish(WearScreen.Ready)
        // Status is fetched lazily, per car, as pages are shown (see onCarShown).
    }

    /** Called when a car page becomes visible — fetch its status once per session. */
    fun onCarShown(vin: String) {
        // Persist the viewed car as the snapshot store's selection: "Follow
        // selected" tiles/complications resolve from SnapshotStore.selected, and
        // nothing on the watch ever wrote it (persistState deliberately ignores
        // the phone's), so saveVehicles' first-car default was permanent - every
        // follow-selected surface showed (and commanded!) car #1 forever.
        viewModelScope.launch {
            runCatching { snapshotStore.setSelected(vin) }
            requestWidgetUpdates()
        }
        if (vin in sessionFetched) return
        if (vehicles.none { it.vin == vin }) return
        sessionFetched.add(vin)
        refreshStatus(vin, surface = false)
    }

    /** Force a fresh status fetch for every car in the garage (Settings'
     *  "Refresh all cars" action), plus a phone-connection recheck. */
    fun refreshAll() {
        // sessionFetched.clear() + only ever refreshing vehicles.firstOrNull()
        // meant this silently refreshed just the first car in the garage --
        // every other car quietly waited for its page to be scrolled to
        // despite Settings' "Refresh all cars" button implying otherwise.
        sessionFetched.clear()
        sessionFetched.addAll(vehicles.map { it.vin })
        vehicles.forEach { refreshStatus(it.vin, surface = false) }
        refreshConnection()
    }

    /** Re-pull snapshots, sessions and settings the phone has published. */
    fun resync() {
        if (_ui.value.resyncBusy) return
        viewModelScope.launch {
            _ui.update { it.copy(resyncBusy = true) }
            try {
                // requestSync's own return value means "the phone actually got
                // this" specifically (not "we got fresh data by any means") --
                // see its doc comment. A standalone fallback now runs whenever
                // the phone can't be reached, so this resync isn't a total
                // no-op when that happens, but `requested` staying false still
                // correctly drives the "bring your phone nearby" message below.
                val requested = runCatching { WearComms.requestSync(ctx, "", refresh = false) }.getOrDefault(false)
                runCatching { WearComms.pullLatest(ctx) }
                snapshots = snapshotStore.current().vehicles.associateBy { it.vin }
                markFetched(snapshots.keys)
                refreshConnection()
                if (vehicles.isEmpty() && sessionStore.loggedInBrands().isNotEmpty()) loadGarage() else publish()
                if (!requested) _ui.update { it.copy(message = "Bring your phone nearby to sync") }
            } finally {
                _ui.update { it.copy(resyncBusy = false) }
            }
        }
    }

    /** Ask the phone to run a Drive sync and re-publish settings. Shows a busy
     *  spinner until the phone's [com.bloo.bluelink.data.WearSyncResult] reply
     *  arrives (via [WearSyncEvents]), or times out. */
    fun syncDrive() {
        viewModelScope.launch {
            _ui.update { it.copy(driveSyncBusy = true, message = null) }
            val sent = runCatching {
                val node = com.bloo.wear.WearComms.phoneNodeId(ctx)
                if (node != null) {
                    val cmd = com.bloo.bluelink.data.WearCommand(vin = "", action = com.bloo.bluelink.data.WearAction.DRIVE_SYNC)
                    com.google.android.gms.tasks.Tasks.await(
                        com.google.android.gms.wearable.Wearable.getMessageClient(ctx).sendMessage(
                            node, com.bloo.bluelink.data.WearSync.PATH_SYNC_REQUEST,
                            com.bloo.bluelink.data.WearSync.encodeCommand(cmd).toByteArray(),
                        ), 10, java.util.concurrent.TimeUnit.SECONDS,
                    )
                    true
                } else false
            }.getOrDefault(false)
            if (!sent) {
                _ui.update { it.copy(driveSyncBusy = false, message = "Bring your phone nearby to sync") }
                return@launch
            }
            // Safety net: if the phone never replies (dropped connection mid-
            // request), don't leave the busy spinner stuck forever. If the reply
            // already arrived, the WearSyncEvents collector already cleared
            // driveSyncBusy, so this no-ops.
            delay(15_000)
            _ui.update { if (it.driveSyncBusy) it.copy(driveSyncBusy = false, message = "Sync timed out") else it }
            runCatching { com.bloo.wear.WearComms.pullLatest(ctx) }
        }
    }

    /**
     * Refreshes one car's live status. Unlike the command functions below,
     * this has no standalone fallback of its own -- it only ever asks the
     * phone to refresh and push updated data via [WearComms.requestSync].
     * (A watch with its own standalone session still gets fresh data,
     * indirectly, through the various command() calls' own refreshStatus/
     * flip calls after a standalone action succeeds.) [surface] controls
     * whether a failed relay shows a user-visible message -- background
     * refreshes (e.g. from [onCarShown]) pass false to stay silent.
     */
    fun refreshStatus(vin: String, surface: Boolean = true) {
        mark("$vin:refresh") {
            // Companion-first: ask the phone to refresh and push updated data.
            val relayed = runCatching { WearComms.requestSync(ctx, vin, refresh = true) }.isSuccess
            if (!relayed && surface) _ui.update { it.copy(message = "Couldn't refresh") }
        }
    }

    /** Recent EV trips, fetched lazily the first time the Trips screen opens. */
    fun loadTrips(vin: String) {
        if (vin in tripsFetched) return
        val v = vehicles.firstOrNull { it.vin == vin } ?: return
        tripsFetched.add(vin)
        mark("$vin:trips") {
            runCatching { BlueLinkGate.statusMutex.withLock { repoFor(v.brand).trips(v) } }
                .onSuccess { list ->
                    trips = trips + (vin to list)
                    _ui.update { it.copy(tripsErrors = it.tripsErrors - vin) }
                    publish()
                }
                .onFailure {
                    tripsFetched.remove(vin)
                    AppLog.log("Watch trips failed: ${it.message}")
                    _ui.update { u -> u.copy(tripsErrors = u.tripsErrors + vin) }
                }
        }
    }

    // ---- Commands ---------------------------------------------------------

    /** Ask the phone to generate an AI status summary for [vin]. The phone can't
     *  be reached from the watch's own connection for this, so it's a phone-only
     *  relay; the summary arrives asynchronously via the extras push and lands in
     *  ui.extras.ai[vin]. */
    fun requestAiSummary(vin: String) {
        viewModelScope.launch {
            _ui.update { it.copy(aiBusy = vin, message = null) }
            val ok = runCatching {
                WearComms.relayToPhone(ctx, com.bloo.bluelink.data.WearCommand(vin, com.bloo.bluelink.data.WearAction.AI_SUMMARY))
            }.getOrDefault(false)
            if (!ok) {
                _ui.update { it.copy(aiBusy = null, message = "Bring your phone nearby to summarize") }
                return@launch
            }
            // Safety net: the relay reached the phone, but if the phone never
            // replies (killed mid-request, message lost) the spinner would
            // otherwise spin forever.
            kotlinx.coroutines.delay(15_000)
            _ui.update { if (it.aiBusy == vin) it.copy(aiBusy = null, message = "Summary timed out") else it }
        }
    }

    /** Ask the phone to set its home weather location from its own device
     *  GPS (mirrors the phone Settings screen's "My location" action) and
     *  push fresh weather back -- the watch has no location/weather fetch of
     *  its own, it only ever displays what the phone last published. */
    fun setWeatherFromDeviceLocation() {
        viewModelScope.launch {
            val sent = runCatching {
                WearComms.relayToPhone(ctx, com.bloo.bluelink.data.WearCommand(vin = "", action = com.bloo.bluelink.data.WearAction.WEATHER_DEVICE_LOCATION))
            }.getOrDefault(false)
            _ui.update {
                it.copy(message = if (sent) "Asked your phone to update the weather location" else "Bring your phone nearby to set this")
            }
        }
    }

    /**
     * Lock/unlock toggle. Delegates entirely to [command], which tries the
     * relay-to-phone path first (see [command]'s doc comment for the full
     * relay-vs-standalone mechanism); the lambda here only runs on the
     * STANDALONE path, i.e. when relaying failed or no phone is reachable.
     * [st] is the watch's last-known [VehicleStatus] for this car (may be
     * null if nothing's been fetched yet), used to decide lock vs unlock.
     * [flip] applies the optimistic local-state update once the standalone
     * API call itself has returned successfully.
     */
    fun toggleLock(vin: String) = command(vin, "doors") { v, repo, st ->
        if (st?.doorLock == true) { repo.unlock(v); flip(vin) { it.copy(doorLock = false) } }
        else { repo.lock(v); flip(vin) { it.copy(doorLock = true) } }
    }

    // Hyundai/Genesis only -- see Vehicle.supportsHornLights. Passed as
    // `explicit` (not inferred by toWearCommand from the action string) since
    // command() always tries relaying to the phone FIRST -- without an
    // explicit command here the relay path would've silently sent a REFRESH
    // instead (toWearCommand's fallback for any action it doesn't recognize).
    fun flashLights(vin: String) = command(
        vin, "hornLights",
        explicit = com.bloo.bluelink.data.WearCommand(vin, com.bloo.bluelink.data.WearAction.FLASH_LIGHTS),
        // Momentary and otherwise invisible on the watch itself (the lights
        // are on the car, not the wrist) -- with no successMessage, a
        // successful tap gave zero acknowledgement at all.
        successMessage = "Lights flashed",
    ) { v, repo, _ -> repo.flashLights(v) }

    fun hornAndLights(vin: String) = command(
        vin, "hornLights",
        explicit = com.bloo.bluelink.data.WearCommand(vin, com.bloo.bluelink.data.WearAction.HORN_AND_LIGHTS),
        successMessage = "Horn & lights sent",
    ) { v, repo, _ -> repo.hornAndLights(v) }

    /**
     * Climate on/off toggle -- same relay-first/standalone-fallback dispatch
     * as [toggleLock] via [command]. The block below only executes on the
     * standalone path. Turning OFF is unconditional; turning ON is gated by
     * [VehicleStatus.isDriving] (see the comment on that check below), and
     * always clears [ClimateDraft.activePresetId] since the resulting
     * on/off state is a plain manual toggle, not a saved preset being
     * (re)applied -- see [applyPreset] for the preset-aware equivalent.
     */
    fun toggleClimate(vin: String) = command(vin, "climate") { v, repo, st ->
        if (st?.airCtrlOn == true) {
            repo.stopClimate(v); flip(vin) { it.copy(airCtrlOn = false) }
            updateDraft(vin) { it.copy(activePresetId = null) }
        } else {
            // The car rejects remote climate commands while it's moving --
            // this standalone (non-relayed) path is watch-connectivity-direct
            // and had no such gate at all, unlike the main phone UI's own
            // Start button.
            if (st?.isDriving == true) error("Can't start climate while driving")
            val d = _ui.value.draftFor(vin)
            repo.startClimate(v, d.toRequest())
            // A manual start isn't a saved preset.
            flip(vin) { it.copy(airCtrlOn = true) }
            updateDraft(vin) { it.copy(activePresetId = null) }
        }
    }

    /** Charge start/stop toggle -- same relay-first/standalone-fallback
     *  dispatch as [toggleLock] via [command]; the block runs only on the
     *  standalone path. [wasCharging] is read from the last-known status,
     *  and [flip] optimistically records the inverse right after the
     *  standalone API call succeeds (see the comment below on why this one
     *  needed the flip added after the fact). */
    fun toggleCharge(vin: String) = command(vin, "charge") { v, repo, st ->
        val wasCharging = st?.evStatus?.batteryCharge == true
        if (wasCharging) repo.stopCharge(v) else repo.startCharge(v)
        // Unlike toggleLock/toggleClimate, this never flipped local state --
        // on the standalone (no-phone) path the button stayed on its old
        // state until the follow-up refreshStatus() network round-trip
        // landed, a visible lag Lock/Climate don't have.
        flip(vin) { it.copy(evStatus = (it.evStatus ?: EvStatus()).copy(batteryCharge = !wasCharging)) }
    }

    /** Apply a saved climate preset (start climate with its exact settings). Also
     *  seeds the sliders so the controls reflect what's running. */
    fun applyPreset(vin: String, preset: ClimatePreset) {
        val r = preset.request
        // Captured so a standalone (no-phone) failure can restore exactly what
        // was showing before this preset was optimistically applied, instead
        // of leaving the sliders/active-highlight showing a preset that the
        // car never actually received.
        val previousDraft = _ui.value.draftFor(vin)
        // Seed the sliders + active highlight up front: the relay path never runs
        // the block below, so seeding inside it never happened with a phone
        // connected - the preset started but the UI never showed it as active.
        updateDraft(vin) {
            it.copy(
                activePresetId = preset.id,
                tempF = r.tempF,
                duration = r.durationMinutes,
                defrost = r.defrost,
                steering = r.steeringWheelHeat,
                seatDriver = seatStepOf(r.seatFrontLeft),
                seatPassenger = seatStepOf(r.seatFrontRight),
                seatRearLeft = seatStepOf(r.seatRearLeft),
                seatRearRight = seatStepOf(r.seatRearRight),
            )
        }
        command(
            vin, "climate",
            // Explicit CLIMATE_ON with the preset's full settings. The generic
            // toggle relay used to turn climate OFF when it was already running
            // (preset tap while on -> phone saw climateOn=true -> stopClimate) and
            // carried only the old draft's temp/defrost even when it started.
            explicit = com.bloo.bluelink.data.WearCommand(
                vin = vin,
                action = com.bloo.bluelink.data.WearAction.CLIMATE_ON,
                tempF = r.tempF,
                durationMinutes = r.durationMinutes,
                defrost = r.defrost,
                steeringWheelHeat = r.steeringWheelHeat,
                seatFrontLeft = r.seatFrontLeft.apiValue,
                seatFrontRight = r.seatFrontRight.apiValue,
                seatRearLeft = r.seatRearLeft.apiValue,
                seatRearRight = r.seatRearRight.apiValue,
            ),
            onFailure = { updateDraft(vin) { previousDraft } },
        ) { v, repo, st ->
            // Same driving gate as toggleClimate's start branch, and for the
            // same reason: this is the STANDALONE path (the car's own direct
            // connection, not relayed through the phone), so it has to
            // enforce the "no remote climate while moving" rule itself --
            // throwing here is caught by command()'s runCatching, which
            // surfaces the message and fires onFailure (restoring the
            // pre-preset draft) instead of ever calling startClimate.
            if (st?.isDriving == true) error("Can't start climate while driving")
            repo.startClimate(v, preset.request)
            flip(vin) { it.copy(airCtrlOn = true) }
        }
    }

    /**
     * Reverse-geocode a car's coordinates to a human place name, once per car per
     * session. Called by the Location card when it has coordinates. Uses the
     * non-blocking Geocoder API on API 33+ (the legacy overload can hang) with a
     * hard timeout, and is a no-op where no geocoder backend is available.
     */
    fun ensurePlaceName(vin: String, lat: Double, lon: Double) {
        if (vin in geocoded || placeNames.containsKey(vin)) return
        if (!Geocoder.isPresent()) return
        geocoded.add(vin)
        viewModelScope.launch {
            // Release the guard on ANY non-success outcome, not just a clean
            // null result -- a thrown exception (geocoder service hiccup) used
            // to leave `vin` stuck in `geocoded` forever, since remove() only
            // ran inside the try block's else branch. That permanently
            // disabled reverse-geocoding for that car for the rest of the
            // app session, with the Location card silently falling back to
            // raw lat/lon and never retrying.
            val name = runCatching { reverseGeocode(lat, lon) }.getOrNull()
            if (!name.isNullOrBlank()) {
                placeNames = placeNames + (vin to name)
                publish()
            } else {
                geocoded.remove(vin)
            }
        }
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double): String? {
        val geocoder = Geocoder(ctx, Locale.getDefault())
        // Was a local closure missing the phone's .distinct() -- could render
        // "Springfield, Springfield" when locality == adminArea. Now shared.
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(6000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull()?.let { com.bloo.bluelink.data.formatPlaceName(it) })
                        }
                        override fun onError(message: String?) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(6000) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.let { com.bloo.bluelink.data.formatPlaceName(it) } }.getOrNull()
                }
            }
        }
    }

    /**
     * Push the AC/DC charge-limit sliders to the car. Goes through [command]
     * with an explicit [com.bloo.bluelink.data.WearAction.SET_CHARGE_LIMITS]
     * command (see the comment below on why a generic action string wasn't
     * enough here), so it gets the same relay-first/standalone-fallback
     * dispatch as the toggle commands. The chargeLimitDrafts entry for this
     * VIN is cleared up front (optimistically, before the network call even
     * starts) so the sliders immediately reflect "applied" instead of
     * lingering as an unsaved draft; [onFailure] restores it if the
     * standalone path's own API call fails.
     */
    fun applyChargeLimits(vin: String) {
        val u = _ui.value
        // Send exactly what LimitsCard DISPLAYS: this car's draft, else its actual
        // current limit. The old `draft ?: 80/90` fallback silently changed the
        // untouched slider - e.g. car reporting DC 100%, user adjusts only AC,
        // taps Apply, and the DC limit drops from the displayed 100 to 90.
        val car = u.cars.firstOrNull { it.vin == vin }
        val draft = u.chargeDraftFor(vin)
        val ac = draft.ac ?: car?.acLimit ?: 80
        val dc = draft.dc ?: car?.dcLimit ?: 90
        // Applied - drop this car's draft so the sliders track the car's fresh state.
        // Kept so a standalone (no-phone) failure can restore it: dropping the
        // draft synchronously here, before the command even runs, meant a
        // failure silently reverted the sliders to the car's OLD limit with
        // only a transient snackbar as evidence -- no "unsaved changes" state
        // left to retry from.
        val previousDraft = u.chargeLimitDrafts[vin]
        _ui.update { it.copy(chargeLimitDrafts = it.chargeLimitDrafts - vin) }
        command(
            vin, "chargeLimit",
            // Explicit verb: "chargeLimit" fell into toWearCommand's else branch
            // and relayed as a plain REFRESH, so with a phone connected the
            // limits were never actually applied - the phone just re-fetched
            // status while the block holding setChargeTargets never ran.
            successMessage = "Charge limits applied",
            explicit = com.bloo.bluelink.data.WearCommand(
                vin = vin,
                action = com.bloo.bluelink.data.WearAction.SET_CHARGE_LIMITS,
                acLimit = ac,
                dcLimit = dc,
            ),
            onFailure = {
                if (previousDraft != null) {
                    _ui.update { it.copy(chargeLimitDrafts = it.chargeLimitDrafts + (vin to previousDraft)) }
                }
            },
        ) { v, repo, _ ->
            repo.setChargeTargets(v, ac, dc)
        }
    }

    /** Central mutator for a car's [ClimateDraft]: applies [f] to the current
     *  draft (or a fresh default one), writes it back into [WearUi], and
     *  immediately pushes the WHOLE drafts map to the phone via
     *  [publishClimateDrafts] so every slider/toggle change on the watch is
     *  mirrored there in near-real-time, not just on command send. Every
     *  slider/toggle setter below (setClimateTemp, toggleDefrost, etc.) and
     *  the higher-level preset/smart-climate flows all funnel through this. */
    private fun updateDraft(vin: String, f: (ClimateDraft) -> ClimateDraft) {
        _ui.update { u -> u.copy(climateDrafts = u.climateDrafts + (vin to f(u.draftFor(vin)))) }
        publishClimateDrafts()
    }

    /** Mirror the phone's climate draft in, without re-publishing (no echo). */
    private fun mergeRemoteClimate(remote: WearClimateState) {
        if (remote.byVin.isEmpty()) return
        _ui.update { u ->
            val merged = u.climateDrafts.toMutableMap()
            remote.byVin.forEach { (vin, cs) ->
                merged[vin] = ClimateDraft(
                    tempF = cs.tempF,
                    duration = cs.durationMinutes,
                    defrost = cs.defrost,
                    steering = cs.steering,
                    seatDriver = seatStepOf(SeatLevel.fromApi(cs.seatFrontLeft)),
                    seatPassenger = seatStepOf(SeatLevel.fromApi(cs.seatFrontRight)),
                    seatRearLeft = seatStepOf(SeatLevel.fromApi(cs.seatRearLeft)),
                    seatRearRight = seatStepOf(SeatLevel.fromApi(cs.seatRearRight)),
                    activePresetId = cs.activePresetId,
                )
            }
            u.copy(climateDrafts = merged)
        }
    }

    /** Save the current draft as a new named preset, then sync it to the phone.
     *  Skips saving if an identical preset already exists for this car. */
    fun saveCurrentAsPreset(vin: String, name: String) {
        val d = _ui.value.draftFor(vin)
        val request = d.toRequest()
        // Don't create a duplicate if the current draft matches an existing preset.
        val existing = _ui.value.presets[vin].orEmpty()
        if (existing.any { it.request == request }) return
        val preset = ClimatePreset(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Preset" },
            request = request,
        )
        val updated = _ui.value.presets + (vin to (existing + preset))
        _ui.update { it.copy(presets = updated) }
        persistAndPublishPresets(updated)
    }

    /** Remove a saved preset for [vin] by id, then persist + sync the change,
     *  same as [saveCurrentAsPreset]. A no-op (writes an unchanged map back)
     *  if [id] isn't found. */
    fun deletePreset(vin: String, id: String) {
        val updated = _ui.value.presets + (vin to _ui.value.presets[vin].orEmpty().filter { it.id != id })
        _ui.update { it.copy(presets = updated) }
        persistAndPublishPresets(updated)
    }


    /** Shared tail of [saveCurrentAsPreset]/[deletePreset]: writes the full
     *  updated preset map to on-disk [WearPresetsStore] (so it survives a
     *  process restart before any phone sync happens) AND pushes it to the
     *  phone over the Data Layer, independently -- either can fail without
     *  affecting the other, and ui.presets has already been updated by the
     *  caller before this runs, so the UI reflects the change immediately
     *  regardless of how the persist/publish calls turn out. */
    private fun persistAndPublishPresets(byVin: Map<String, List<ClimatePreset>>) {
        val wp = com.bloo.bluelink.data.WearPresets(byVin)
        viewModelScope.launch {
            runCatching { WearPresetsStore(ctx).save(com.bloo.bluelink.data.WearSync.encodePresets(wp)) }
            runCatching { WearComms.publishPresets(ctx, wp) }
        }
    }

    /** Push every car's draft to the phone over the shared climate channel. */
    private fun publishClimateDrafts() {
        val byVin = _ui.value.climateDrafts.mapValues { (_, d) ->
            com.bloo.bluelink.data.ClimateSync(
                activePresetId = d.activePresetId,
                tempF = d.tempF,
                durationMinutes = d.duration,
                defrost = d.defrost,
                steering = d.steering,
                seatFrontLeft = seatLevelOf(d.seatDriver).apiValue,
                seatFrontRight = seatLevelOf(d.seatPassenger).apiValue,
                seatRearLeft = seatLevelOf(d.seatRearLeft).apiValue,
                seatRearRight = seatLevelOf(d.seatRearRight).apiValue,
            )
        }
        viewModelScope.launch { runCatching { WearComms.publishClimate(ctx, WearClimateState(byVin)) } }
    }

    // The climate slider/toggle setters below all follow the same shape: clamp
    // the incoming value to its valid range, write it into this car's draft via
    // updateDraft (which also re-syncs the whole draft to the phone), and clear
    // activePresetId since any manual adjustment means the draft no longer
    // exactly matches whichever preset (if any) was last applied.
    fun setClimateTemp(vin: String, value: Int) = updateDraft(vin) { it.copy(tempF = value.coerceIn(62, 82), activePresetId = null) }
    fun setClimateDuration(vin: String, value: Int) = updateDraft(vin) { it.copy(duration = value.coerceIn(1, 10), activePresetId = null) }
    fun toggleDefrost(vin: String) = updateDraft(vin) { it.copy(defrost = !it.defrost, activePresetId = null) }
    fun toggleSteering(vin: String) = updateDraft(vin) { it.copy(steering = !it.steering, activePresetId = null) }
    fun setSeatDriver(vin: String, step: Int) = updateDraft(vin) { it.copy(seatDriver = step.coerceIn(0, 3), activePresetId = null) }
    fun setSeatPassenger(vin: String, step: Int) = updateDraft(vin) { it.copy(seatPassenger = step.coerceIn(0, 3), activePresetId = null) }
    fun setSeatRearLeft(vin: String, step: Int) = updateDraft(vin) { it.copy(seatRearLeft = step.coerceIn(0, 3), activePresetId = null) }
    fun setSeatRearRight(vin: String, step: Int) = updateDraft(vin) { it.copy(seatRearRight = step.coerceIn(0, 3), activePresetId = null) }
    fun setAcLimit(vin: String, value: Int) = updateChargeDraft(vin) { it.copy(ac = value.coerceIn(com.bloo.bluelink.data.CHARGE_LIMIT_RANGE)) }
    fun setDcLimit(vin: String, value: Int) = updateChargeDraft(vin) { it.copy(dc = value.coerceIn(com.bloo.bluelink.data.CHARGE_LIMIT_RANGE)) }

    /** Mutator for a car's [ChargeLimitDraft], mirroring [updateDraft]'s shape
     *  but WITHOUT a phone push -- charge-limit sliders are local-only until
     *  [applyChargeLimits] is tapped, unlike climate drafts which sync live. */
    private fun updateChargeDraft(vin: String, f: (ChargeLimitDraft) -> ChargeLimitDraft) {
        _ui.update { u -> u.copy(chargeLimitDrafts = u.chargeLimitDrafts + (vin to f(u.chargeDraftFor(vin)))) }
    }
    /** Clears the current snackbar/status message, e.g. once the user has seen it. */
    fun dismissMessage() { _ui.update { it.copy(message = null) } }

    // --- App self-update (GitHub Actions builds; Bloo isn't on the Play Store) ---

    /** "Remind me in a few days": persists a snooze that outlasts the checker's
     *  normal debounce window too. */
    fun snoozeUpdate() {
        _ui.update { it.copy(updateRun = null) }
        viewModelScope.launch { localStore.setUpdateSnoozeUntil(System.currentTimeMillis() + UPDATE_SNOOZE_MS) }
    }

    /** The single update-check path: runs once at cold start (see init below),
     *  entirely independent of the phone -- same GitHub Actions endpoint the
     *  phone uses (in :shared), compared against this watch's OWN build
     *  number, not the phone's. No manual "Check now" any more -- this is the
     *  only check, and its result (updateRun) drives the More tile's banner
     *  automatically. Returns whether a newer build was surfaced. */
    private suspend fun runUpdateCheck(force: Boolean): Boolean {
        if (com.bloo.wear.BuildConfig.BUILD_RUN_NUMBER <= 0) return false
        val settings = localStore.flow.first()
        val now = System.currentTimeMillis()
        if (!force && now - settings.updateLastCheckedAt < UPDATE_CHECK_INTERVAL_MS) return false
        if (!force && now < settings.updateSnoozeUntil) return false
        // Same-branch comparison + consume the 12h window only on a successful
        // fetch - mirrors UpdateChecker.checkPhone (see there for why).
        val branch = com.bloo.wear.BuildConfig.BUILD_BRANCH
            .ifBlank { com.bloo.bluelink.data.UpdateApi.DEFAULT_BRANCH }
        val run = runCatching {
            com.bloo.bluelink.data.UpdateApi.fetchLatestSuccessfulRun(branch)
        }.getOrNull() ?: return false
        localStore.setUpdateLastCheckedAt(now)
        return if (run.runNumber > com.bloo.wear.BuildConfig.BUILD_RUN_NUMBER) {
            _ui.update { it.copy(updateRun = run) }
            true
        } else false
    }

    /** The GitHub Actions build number this watch app was compiled from. */
    val currentBuildNumber: Int get() = com.bloo.wear.BuildConfig.BUILD_RUN_NUMBER

    /** Downloads the watch's OWN update APK (Bloo-Wear.apk) and hands it
     *  straight to the system installer, entirely on-device -- no phone
     *  needed at all. Falls back to opening the release page on the phone
     *  only if this particular release has no wear asset (an old release
     *  published before wearApkUrl existed, or a failed asset upload). */
    fun downloadAndInstallUpdate() {
        val run = _ui.value.updateRun ?: return
        val url = run.wearApkUrl
        if (url == null) {
            com.bloo.wear.WearRemote.openOnPhone(ctx, run.htmlUrl)
            return
        }
        if (_ui.value.updateDownloading) return
        _ui.update { it.copy(updateDownloading = true) }
        viewModelScope.launch {
            val dest = java.io.File(java.io.File(ctx.cacheDir, "apk"), "Bloo-Wear.apk")
            val ok = com.bloo.bluelink.data.UpdateApi.downloadApk(url, dest) { }
            _ui.update { it.copy(updateDownloading = false) }
            if (!ok) {
                _ui.update { it.copy(message = "Download failed — check your connection and try again.") }
                return@launch
            }
            runCatching {
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dest)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }.onFailure {
                _ui.update { it2 -> it2.copy(message = "Downloaded, but couldn't open the installer.") }
            }
        }
    }

    /** Set the watch's own display font scale (local-only setting, never read
     *  from the phone), clamp it to a sane range, persist it, then push the
     *  FULL local-settings bundle (font scale + unit system + PIN config) to
     *  the phone in one message so it can display the watch's current
     *  preferences too. Re-reads [localStore] right after writing rather than
     *  reusing [clamped] directly for the other fields, so the pushed bundle
     *  reflects whatever the other fields' latest persisted values are. */
    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            val clamped = scale.coerceIn(0.8f, 1.4f)
            localStore.setFontScale(clamped)
            val ls = localStore.flow.first()
            WearComms.publishLocalSettings(ctx, clamped, ls.unitSystem, ls.pinLockEnabled, ls.pinLockTiming)
        }
    }

    /** Set the unit system locally and push to phone. */
    fun setUnitSystem(value: String) {
        viewModelScope.launch {
            localStore.setUnitSystem(value)
            val ls = localStore.flow.first()
            WearComms.publishLocalSettings(ctx, ls.fontScale, value, ls.pinLockEnabled, ls.pinLockTiming)
        }
    }

    // --- PIN lock ------------------------------------------------------------

    // In-memory only -- resets on process death/app restart, which is an
    // accepted tradeoff. This is a brute-force-slowing cooldown for a 4-digit
    // on-device PIN (see WearLocalStore.verifyPin's own doc comment noting
    // there was previously no attempt-counting or lockout anywhere in this
    // flow at all), not a hard security boundary that needs to survive a
    // process restart.
    private var pinFailCount = 0
    private var pinLockedUntilMs = 0L

    /** Called from MainActivity's onStart with the timestamp the app was
     *  backgrounded at (0 if it was never backgrounded this process), mirroring
     *  the phone's AppViewModel.maybeRelock. */
    fun maybeRelock(backgroundedAtMs: Long) {
        val ls = _ui.value.localSettings
        if (_ui.value.pinLocked || !ls.pinLockEnabled || !ls.hasPin || backgroundedAtMs == 0L) return
        val elapsed = System.currentTimeMillis() - backgroundedAtMs
        val shouldLock = when (ls.pinLockTiming) {
            "off" -> false
            "immediate" -> true
            "1min" -> elapsed >= 60_000L
            "5min" -> elapsed >= 5 * 60_000L
            "10min" -> elapsed >= 10 * 60_000L
            else -> true
        }
        if (shouldLock) _ui.update { it.copy(pinLocked = true) }
    }

    /**
     * Attempt to unlock with an entered PIN. Reports success via the first
     * [onResult] param; the second is an optional user-facing message (a
     * lockout countdown) the caller should show instead of its own generic
     * "Wrong PIN" text.
     *
     * After [PIN_MAX_ATTEMPTS] consecutive wrong guesses, further attempts
     * are rejected outright -- without even calling [WearLocalStore.verifyPin]
     * -- until [PIN_LOCKOUT_MS] has passed, closing the "every call is an
     * independent, unrate-limited comparison" gap that store's own doc
     * comment used to call out. A correct PIN (whether it's the first try or
     * after some wrong ones) resets the fail count back to zero.
     */
    fun submitPin(pin: String, onResult: (ok: Boolean, lockoutMessage: String?) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (now < pinLockedUntilMs) {
                val secondsLeft = (pinLockedUntilMs - now) / 1000L + 1
                onResult(false, "Too many attempts. Wait ${secondsLeft}s")
                return@launch
            }
            val ok = localStore.verifyPin(pin)
            if (ok) {
                pinFailCount = 0
                pinLockedUntilMs = 0L
                _ui.update { it.copy(pinLocked = false) }
                onResult(true, null)
            } else {
                pinFailCount++
                if (pinFailCount >= PIN_MAX_ATTEMPTS) {
                    pinLockedUntilMs = now + PIN_LOCKOUT_MS
                    pinFailCount = 0
                    onResult(false, "Too many attempts. Wait ${PIN_LOCKOUT_MS / 1000}s")
                } else {
                    onResult(false, null)
                }
            }
        }
    }

    /** Verify an entered PIN without unlocking -- used by the settings screen's
     *  "confirm your current PIN before changing/removing it" step. */
    fun verifyPinForManagement(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(localStore.verifyPin(pin)) }
    }

    /** Turn the PIN lock feature on/off, persist it, and push the change to
     *  the phone (via [pushLocalPinSettings]) so it shows the same setting. */
    fun setPinLockEnabled(enabled: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            localStore.setPinLockEnabled(enabled)
            pushLocalPinSettings()
            onDone()
        }
    }

    /** Change how soon backgrounding the app re-locks it (off/immediate/1min/
     *  5min/10min -- see [maybeRelock]'s use of this value), and sync to phone. */
    fun setPinLockTiming(value: String) {
        viewModelScope.launch {
            localStore.setPinLockTiming(value)
            pushLocalPinSettings()
        }
    }

    /** Set (or replace) the PIN and arm the lock. [onDone] reports whether the
     *  format was valid (exactly 4 digits) -- caller is responsible for asking
     *  twice and comparing before calling this. */
    fun setPin(pin: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val valid = pin.length == 4 && pin.all { it.isDigit() }
            if (valid) {
                localStore.setPin(pin)
                pushLocalPinSettings()
            }
            onDone(valid)
        }
    }

    /** Remove the stored PIN entirely (does not by itself disable the lock
     *  feature flag -- callers are expected to call [setPinLockEnabled] too
     *  if they want it fully turned off), and sync to phone. */
    fun clearPin(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            localStore.clearPin()
            pushLocalPinSettings()
            onDone()
        }
    }

    /** Shared tail used by every local-settings mutator above: re-reads the
     *  now-persisted [WearLocalSettings] bundle (so it always reflects the
     *  very latest write, whichever field just changed) and pushes all four
     *  fields to the phone together in one message. */
    private suspend fun pushLocalPinSettings() {
        val ls = localStore.flow.first()
        WearComms.publishLocalSettings(ctx, ls.fontScale, ls.unitSystem, ls.pinLockEnabled, ls.pinLockTiming)
    }

    /** Turn AI summaries on/off. Optimistically flips the synced flag so the
     *  toggle and AI tile react instantly; the phone's echo (or a future
     *  settings push) settles it for real. */
    fun setAiEnabled(enabled: Boolean) {
        _ui.update { u ->
            u.copy(settings = u.settings?.copy(aiEnabled = enabled), settingsOverride = u.settingsOverride + ("aiEnabled" to enabled))
        }
        viewModelScope.launch { WearComms.publishAiToggle(ctx, enabled) }
    }

    /** Turn the watch's own aurora background on/off. Same optimistic-update +
     *  phone-echo pattern as [setAiEnabled]. */
    fun setAuroraEnabled(enabled: Boolean) {
        _ui.update { u ->
            u.copy(settings = u.settings?.copy(auroraEnabled = enabled), settingsOverride = u.settingsOverride + ("auroraEnabled" to enabled))
        }
        viewModelScope.launch { WearComms.publishAuroraToggle(ctx, enabled) }
    }

    /** Set the aurora colour mode ("complementary"/"material"/"custom") from
     *  the watch -- same optimistic-update + phone-echo pattern as
     *  [setAiEnabled], carried on the same push as the enabled flag (whatever
     *  it currently is) so this never accidentally turns the background on
     *  or off as a side effect of just changing its colour. */
    fun setAuroraColorMode(mode: String) {
        val enabled = _ui.value.settings?.auroraEnabled ?: return
        _ui.update { u ->
            u.copy(settings = u.settings?.copy(auroraColorMode = mode), settingsOverride = u.settingsOverride + ("auroraColorMode" to mode))
        }
        viewModelScope.launch { WearComms.publishAuroraToggle(ctx, enabled, colorMode = mode) }
    }

    /** Choose which action chips the glanceable Tile shows, then redraw it. */
    fun setTileActions(actions: List<String>) {
        viewModelScope.launch {
            localStore.setTileActions(actions)
            requestWidgetUpdates()
        }
    }

    /** Redraw the glanceable Tile/complications now, without waiting for a
     *  phone echo -- e.g. right after a pebble reorder, so the new tile order
     *  takes effect immediately instead of on the next freshness-interval
     *  poll. */
    fun refreshTileWidgets() = requestWidgetUpdates()

    /** Pin pool slot [index]'s Tile to a car (null = follow selected), then redraw it. */
    fun setTileCarVin(index: Int, vin: String?) {
        viewModelScope.launch {
            localStore.setTileCarVin(index, vin)
            requestWidgetUpdates()
        }
    }

    /**
     * Persist a car's reordered pebble order: apply it optimistically so the
     * tiles rearrange instantly, then push it to the phone, which saves it as
     * that car's section order and mirrors it back to every device.
     */
    fun savePebbleOrder(vin: String, order: List<String>) {
        val normalized = WearPebbles.normalize(order)
        _ui.update { it.copy(pebbleOverride = it.pebbleOverride + (vin to normalized)) }
        viewModelScope.launch {
            val ok = runCatching { WearComms.publishPebbleOrder(ctx, vin, normalized) }.getOrDefault(false)
            if (!ok) {
                // The phone never received this order, so its echo can never match
                // and the exact-match clear in init would hold the override forever
                // - masking every later phone-side reorder for the rest of the
                // session. Drop it and fall back to the synced order.
                _ui.update { u -> u.copy(pebbleOverride = u.pebbleOverride - vin) }
            }
        }
    }

    /**
     * Smart climate: reads the weather at the car's location (or home weather as
     * fallback), then starts climate at a target picked by
     * [com.bloo.bluelink.data.smartClimateTargetF] -- shared with the phone and
     * the widget/QS tile so all three pick the exact same target, clamped to
     * what the car's climate range actually accepts.
     */
    fun smartClimate(vin: String) {
        val extras = _ui.value.extras
        val weather = extras.carWeather[vin] ?: extras.homeWeather ?: run {
            _ui.update { it.copy(message = "No weather data — can't run smart climate") }
            return
        }
        val ambientF = com.bloo.bluelink.data.ambientFahrenheit(weather.tempC)
        val targetF = com.bloo.bluelink.data.smartClimateTargetF(ambientF)
        // Decide on/off HERE (same statuses-then-snapshots priority buildCarView
        // uses) rather than inside the block: the relay path never runs the block,
        // so the computed targetF used to be silently discarded - the phone got a
        // generic toggle at the stale draft temp (or just turned climate off).
        val isOn = statuses[vin]?.airCtrlOn ?: snapshots[vin]?.climateOn ?: false
        val d = _ui.value.draftFor(vin)
        // Same rollback rationale as applyPreset: restore exactly what the
        // draft showed before this optimistic change if the standalone
        // command actually fails.
        val previousDraft = d
        if (isOn) {
            updateDraft(vin) { it.copy(activePresetId = null) }
            command(
                vin, "climate",
                explicit = com.bloo.bluelink.data.WearCommand(vin, com.bloo.bluelink.data.WearAction.CLIMATE_OFF),
                onFailure = { updateDraft(vin) { previousDraft } },
            ) { v, repo, _ ->
                repo.stopClimate(v); flip(vin) { it.copy(airCtrlOn = false) }
            }
        } else {
            updateDraft(vin) { it.copy(tempF = targetF, activePresetId = null) }
            command(
                vin, "climate",
                explicit = com.bloo.bluelink.data.WearCommand(
                    vin = vin,
                    action = com.bloo.bluelink.data.WearAction.CLIMATE_ON,
                    tempF = targetF,
                    durationMinutes = d.duration,
                    defrost = false,
                    steeringWheelHeat = d.steering,
                    seatFrontLeft = seatLevelOf(d.seatDriver).apiValue,
                    seatFrontRight = seatLevelOf(d.seatPassenger).apiValue,
                    seatRearLeft = seatLevelOf(d.seatRearLeft).apiValue,
                    seatRearRight = seatLevelOf(d.seatRearRight).apiValue,
                ),
                onFailure = { updateDraft(vin) { previousDraft } },
            ) { v, repo, st ->
                // Third of the three climate-start call sites gated on
                // isDriving (with toggleClimate and applyPreset) -- same
                // standalone-only enforcement, same reasoning: relayed
                // commands rely on the phone's own UI/repository to apply
                // this gate, but the standalone path talks to the car
                // directly and has to check it here itself.
                if (st?.isDriving == true) error("Can't start climate while driving")
                repo.startClimate(v, d.toRequest(tempF = targetF, defrost = false))
                flip(vin) { it.copy(airCtrlOn = true) }
            }
        }
    }

    /**
     * The shared dispatcher every user-facing command (lock, climate, charge,
     * horn/lights, charge limits) funnels through. This is where the
     * relay-vs-standalone branching described in the class doc comment
     * actually happens:
     *
     * 1. Marks `"$vin:$action"` as pending via [mark] (drives per-button busy
     *    spinners/disabled states through [WearUi.pending]).
     * 2. Builds the [com.bloo.bluelink.data.WearCommand] to send -- either
     *    the caller-supplied [explicit] one (needed whenever a generic
     *    toggle verb isn't precise enough, e.g. a preset needs its exact
     *    settings carried, not just "toggle climate") or one derived from
     *    the string [action] via [toWearCommand].
     * 3. Attempts to relay it to the phone with [WearComms.send]. If that
     *    SEND succeeds (note: this only confirms the message reached the
     *    phone, not that the phone's own BlueLink call to the car
     *    succeeded):
     *    - Applies an optimistic patch to [snapshots] via
     *      [com.bloo.bluelink.data.WearCommandRunner.optimistic], then folds
     *      the same inferred new lock/climate/charge state into [statuses]
     *      too (so buildCarView's "prefer statuses over snapshots" rule
     *      doesn't mask the optimistic change behind stale live status).
     *    - Calls [publish] immediately so the UI reflects the guessed
     *      outcome right away, shows [successMessage] if provided, and
     *      forces the next [onCarShown] visit to re-fetch real status
     *      ([sessionFetched] entry removed) since the optimistic guess isn't
     *      authoritative.
     *    - A later async failure notice for this same relay (the phone's
     *      OWN execution actually erroring) arrives out-of-band via
     *      [WearCommandEvents.results], wired up in [init] -- there's no
     *      revert path here for that case, only a corrective re-fetch.
     * 4. If the relay send itself failed (no phone reachable, or the send
     *    threw), falls back to STANDALONE: runs [block] with the watch's own
     *    [VehicleRepository] for this car's brand, serialized against
     *    [BlueLinkGate.statusMutex] (shared with other status-touching calls
     *    to avoid racing concurrent standalone requests for the same car).
     *    On success: publishes, shows [successMessage], clears
     *    [sessionFetched] and kicks a real [refreshStatus] (standalone calls
     *    don't get an authoritative status back from [block] itself, so this
     *    pulls the real post-command state). On failure: surfaces the
     *    error message and invokes [onFailure] so the caller can roll back
     *    any optimistic draft/UI change it made before calling [command].
     *
     * [onFailure] only ever fires on this standalone failure branch -- see
     * its own inline comment for why the relay branch can't support it.
     */
    private fun command(
        vin: String,
        action: String,
        explicit: com.bloo.bluelink.data.WearCommand? = null,
        successMessage: String? = null,
        // Only fires on the STANDALONE (non-relayed) failure path -- once a
        // command is relayed to the phone, the watch has no ack channel back
        // for whether the phone's own execution actually succeeded, so an
        // optimistic draft change made for a relayed command can't be
        // reverted here regardless.
        onFailure: (() -> Unit)? = null,
        block: suspend (Vehicle, VehicleRepository, VehicleStatus?) -> Unit,
    ) {
        val v = vehicles.firstOrNull { it.vin == vin } ?: return
        mark("$vin:$action") {
            val wearCommand = explicit ?: toWearCommand(vin, action)
            val relayed = runCatching { WearComms.send(ctx, wearCommand) }.isSuccess
            if (relayed) {
                AppLog.log("Watch: $action relayed to phone")
                val currentSnap = snapshots[vin]
                if (currentSnap != null) {
                    val newSnap = com.bloo.bluelink.data.WearCommandRunner.optimistic(currentSnap, wearCommand.action)
                    snapshots = snapshots + (vin to newSnap)
                    statuses[vin]?.let { s ->
                        statuses = statuses + (vin to when (wearCommand.action) {
                            com.bloo.bluelink.data.WearAction.TOGGLE_LOCK,
                            com.bloo.bluelink.data.WearAction.LOCK,
                            com.bloo.bluelink.data.WearAction.UNLOCK ->
                                s.copy(doorLock = newSnap.locked)
                            com.bloo.bluelink.data.WearAction.TOGGLE_CLIMATE,
                            com.bloo.bluelink.data.WearAction.CLIMATE_ON,
                            com.bloo.bluelink.data.WearAction.CLIMATE_OFF ->
                                s.copy(airCtrlOn = newSnap.climateOn)
                            com.bloo.bluelink.data.WearAction.TOGGLE_CHARGE,
                            com.bloo.bluelink.data.WearAction.CHARGE_ON,
                            com.bloo.bluelink.data.WearAction.CHARGE_OFF ->
                                s.copy(evStatus = (s.evStatus ?: com.bloo.bluelink.data.EvStatus()).copy(batteryCharge = newSnap.charging ?: false))
                            else -> s
                        })
                    }
                }
                // The relayed optimistic flip just updated this car's displayed
                // state, so advance its "last updated" stamp alongside it.
                markFetched(listOf(vin))
                publish()
                if (successMessage != null) _ui.update { it.copy(message = successMessage) }
                sessionFetched.remove(vin)
                requestWidgetUpdates()
            } else {
                runCatching {
                    BlueLinkGate.statusMutex.withLock { block(v, repoFor(v.brand), statuses[vin]) }
                }.onSuccess {
                    AppLog.log("Watch: $action ok")
                    // The standalone block just applied fresh live state (via flip)
                    // straight from the car API -- stamp it so the "updated X ago"
                    // label reflects this arrival, not a stale cache seed.
                    markFetched(listOf(vin))
                    publish()
                    if (successMessage != null) _ui.update { it.copy(message = successMessage) }
                    sessionFetched.remove(vin)
                    refreshStatus(vin, surface = false)
                    requestWidgetUpdates()
                }.onFailure { e ->
                    AppLog.log("⚠ Watch command $action failed: ${e.message}")
                    _ui.update { it.copy(message = e.message ?: "Command failed") }
                    onFailure?.invoke()
                }
            }
        }
    }

    /**
     * Builds the [com.bloo.bluelink.data.WearCommand] sent to the phone for
     * [command]'s three generic actions ("doors"/"climate"/"charge" map to
     * their TOGGLE_* [com.bloo.bluelink.data.WearAction]s; anything else
     * falls back to a plain REFRESH). Used only when the caller didn't pass
     * an `explicit` command. Always carries the car's CURRENT full
     * [ClimateDraft] (temp, duration, defrost, steering, all four seats) on
     * every command regardless of action, not just for "climate" -- so if a
     * relayed climate toggle turns climate ON, the phone has the complete
     * settings to start it with rather than just the wire protocol's bare
     * defaults.
     */
    private fun toWearCommand(vin: String, action: String): com.bloo.bluelink.data.WearCommand {
        // Carry the FULL climate draft, not just temp/defrost - a relayed climate
        // start used to run for the wire default of 10 minutes with no steering or
        // seat heat no matter what the user had set on the watch.
        val d = _ui.value.draftFor(vin)
        return com.bloo.bluelink.data.WearCommand(
            vin = vin,
            action = when (action) {
                "doors" -> com.bloo.bluelink.data.WearAction.TOGGLE_LOCK
                "climate" -> com.bloo.bluelink.data.WearAction.TOGGLE_CLIMATE
                "charge" -> com.bloo.bluelink.data.WearAction.TOGGLE_CHARGE
                else -> com.bloo.bluelink.data.WearAction.REFRESH
            },
            tempF = d.tempF,
            durationMinutes = d.duration,
            defrost = d.defrost,
            steeringWheelHeat = d.steering,
            seatFrontLeft = seatLevelOf(d.seatDriver).apiValue,
            seatFrontRight = seatLevelOf(d.seatPassenger).apiValue,
            seatRearLeft = seatLevelOf(d.seatRearLeft).apiValue,
            seatRearRight = seatLevelOf(d.seatRearRight).apiValue,
        )
    }

    /** The STANDALONE path's optimistic-update primitive (the relay path's
     *  equivalent is the inline [com.bloo.bluelink.data.WearCommandRunner.optimistic]
     *  patch inside [command]): applies [change] to this VIN's current
     *  [VehicleStatus] (or a blank default one if nothing's cached yet) and
     *  writes the result back into [statuses]. Called by each command
     *  function's block AFTER its own repository call has returned
     *  successfully, so the UI updates immediately rather than waiting on
     *  the follow-up [refreshStatus] call that [command] triggers next. Does
     *  NOT call [publish] itself -- callers rely on [command]'s own publish()
     *  right after the block returns. */
    private fun flip(vin: String, change: (VehicleStatus) -> VehicleStatus) {
        val cur = statuses[vin] ?: VehicleStatus()
        statuses = statuses + (vin to change(cur))
    }

    /** Stamp each [vins] entry's "last updated" time to now, so buildCarView's
     *  [CarView.fetchedAt]-based "updated X ago" label tracks each real data
     *  arrival (snapshot push, standalone command success, relayed refresh)
     *  instead of being frozen at whatever the on-disk cache seeded in bootstrap. */
    private fun markFetched(vins: Collection<String>) {
        if (vins.isEmpty()) return
        val now = System.currentTimeMillis()
        fetchedAt = fetchedAt + vins.associateWith { now }
    }

    /** Nudge every pool Tile and the watch-face complications to re-read the
     *  updated snapshot. */
    private fun requestWidgetUpdates() = com.bloo.wear.tile.refreshWearGlanceables(ctx)

    // ---- Plumbing ---------------------------------------------------------

    /**
     * Runs [block] (launched fire-and-forget in [viewModelScope]) while
     * marking [key] as "pending" for the duration, so [WearUi.pending]
     * reflects it and buttons/spinners bound to that key can disable/spin.
     * Uses a per-key REFERENCE COUNT (in [pendingCounts]) rather than a
     * plain Set, specifically to handle two overlapping calls sharing the
     * same key correctly (e.g. a manual refresh tap while [onCarShown]'s own
     * refresh for that same car is still in flight): incrementing on entry
     * and decrementing in a `finally` means the key only actually leaves
     * [pendingCounts] once every overlapping call for it has finished, not
     * just the first one to complete -- see the comment on [pendingCounts]
     * itself for the bug this fixed. [publish] is called both immediately
     * (so the busy state shows right away) and again once the count drops
     * back out, whether [block] succeeded or threw.
     */
    private fun mark(key: String, block: suspend () -> Unit) {
        pendingCounts = pendingCounts + (key to (pendingCounts[key] ?: 0) + 1)
        publish()
        viewModelScope.launch {
            try { block() } finally {
                val remaining = (pendingCounts[key] ?: 1) - 1
                pendingCounts = if (remaining <= 0) pendingCounts - key else pendingCounts + (key to remaining)
                publish()
            }
        }
    }

    /**
     * Rebuilds the exposed [WearUi] snapshot from the ViewModel's private
     * @Volatile source-of-truth fields ([vehicles], [statuses], [snapshots],
     * [trips], [pendingCounts] via [pending]) and pushes it into [_ui]. This
     * is the ONLY place [WearUi.cars]/[WearUi.trips]/[WearUi.pending] get
     * updated -- every mutation to the underlying fields elsewhere in this
     * class must call this afterward (directly, or via [mark]/[command]) for
     * the UI to actually reflect the change. [screen] optionally also
     * transitions [WearUi.screen] (e.g. to Ready once the garage loads);
     * passing null leaves the current screen untouched.
     */
    private fun publish(screen: WearScreen? = null) {
        _ui.update { cur ->
            cur.copy(
                screen = screen ?: cur.screen,
                cars = vehicles.map { buildCarView(it) },
                trips = trips,
                pending = pending,
            )
        }
    }

    /**
     * Merges the two data sources this app has for one car into the single
     * [CarView] the UI actually renders: [statuses] (rich, freshly-fetched
     * live status, either from a standalone repository call or synced down)
     * and [snapshots] (the phone's lightweight always-on view). The general
     * rule, applied per-field via `s?.field ?: snap?.field`, is that live
     * [statuses] wins whenever present and [snapshots] is only the fallback
     * for fields [statuses] doesn't have yet (or has never been fetched for
     * this VIN at all) -- e.g. [percent]/[rangeMi]/[locked]/[climateOn]/
     * [charging]. [hasBattery] is the one field that inverts this
     * preference deliberately (see its own inline comment): it prefers the
     * phone's manually-corrected [snap]?.hasBattery over the vehicle's raw
     * `isEv` flag, since a PHEV the API misreports as gas-only still needs
     * the Charge tile shown.
     */
    private fun buildCarView(v: Vehicle): CarView {
        val s = statuses[v.vin]
        val snap = snapshots[v.vin]
        // Prefer the phone's manually-corrected powertrain (a PHEV the API
        // misreports as gas still needs the Charge tile) over the raw isEv
        // flag; only standalone mode with no synced snapshot yet falls back.
        val hasBattery = snap?.hasBattery ?: v.isEv
        val ev = s?.evStatus
        val coord = s?.vehicleLocation?.coord
        val lamp = s?.tirePressureLamp
        val gen = v.generation.trim().toIntOrNull() ?: 3
        val gen5w = v.brand != Brand.KIA && gen < 3
        val seats = s?.seatHeaterVentState
        return CarView(
            vin = v.vin,
            name = v.name,
            model = v.model,
            brand = v.brand,
            hasBattery = hasBattery,
            percent = s?.percentFor(hasBattery) ?: snap?.percent,
            rangeMi = s?.rangeMiFor(hasBattery) ?: snap?.rangeMi,
            locked = s?.doorLock ?: snap?.locked,
            climateOn = s?.airCtrlOn ?: snap?.climateOn,
            charging = ev?.batteryCharge ?: snap?.charging,
            pluggedIn = ev?.batteryPlugin?.let { it != 0 },
            chargerLabel = com.bloo.bluelink.data.chargerLabel(ev?.batteryPlugin),
            timeToFullMin = ev?.remainTime2?.atc?.value?.toInt(),
            acLimit = ev?.reservChargeInfos?.level(1),
            dcLimit = ev?.reservChargeInfos?.level(0),
            fetchedAt = fetchedAt[v.vin],
            doorsOpen = (s?.doorOpen ?: DoorOpen()).openLabels(),
            windowsOpen = (s?.windowOpen ?: WindowOpen()).openLabels(),
            trunkOpen = s?.trunkOpen == true,
            hoodOpen = s?.hoodOpen == true,
            tireWarning = s?.tirePressureLamp?.hasWarning == true,
            battery12v = s?.battery?.batSoc,
            lowFuel = s?.lowFuelLight == true,
            washerLow = s?.washerFluidStatus == true,
            brakeLow = s?.breakOilStatus == true,
            keyFobLow = s?.smartKeyBatteryWarning == true,
            odometer = v.odometer,
            lat = coord?.lat,
            lon = coord?.lon,
            locationName = placeNames[v.vin],
            tripsSupported = !gen5w,
            engineOn = s?.engine == true,
            accessoryOn = s?.acc == true,
            defrostOn = s?.defrost == true,
            tempSetting = s?.airTemp?.value,
            tireAll = s?.tirePressure?.all,
            tireFl = (lamp?.frontLeft ?: 0) != 0,
            tireFr = (lamp?.frontRight ?: 0) != 0,
            tireRl = (lamp?.rearLeft ?: 0) != 0,
            tireRr = (lamp?.rearRight ?: 0) != 0,
            steerHeat = (s?.steerWheelHeat ?: 0) != 0,
            mirrorHeat = (s?.sideMirrorHeat ?: 0) != 0,
            rearDefrost = (s?.sideBackWindowHeat ?: 0) != 0,
            seatFl = seats?.flSeatHeatState,
            seatFr = seats?.frSeatHeatState,
            seatRl = seats?.rlSeatHeatState,
            seatRr = seats?.rrSeatHeatState,
            battery12vHealth = s?.battery?.health,
            fuelLevel = s?.fuelLevel,
            hasLiveStatus = s != null,
            licensePlate = snap?.licensePlate,
            lastServiceMiles = snap?.lastServiceMiles,
            serviceIntervalMiles = snap?.serviceIntervalMiles,
        )
    }
}
