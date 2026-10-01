package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.sync.Mutex

// A corruption handler so a settings file damaged by an interrupted write / power
// loss resets to empty prefs instead of rethrowing IOException out of every read
// (which crashed the app on launch, since `appearance` is collected eagerly).
internal val Context.settingsDataStore by preferencesDataStore(
    name = "bloo_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)


// Process-wide serialization for performMainToMainSync(): the periodic worker and the
// auto-sync-on-refresh collector can both fire at nearly the same moment, and
// SettingsStore is instantiated fresh at each call
// site (not a singleton) — a per-instance lock wouldn't serialize anything, so
// this lives at module scope instead, same pattern as BlueLinkGate.statusMutex.
internal val mainToMainSyncMutex = Mutex()


// A stalled SAF/DocumentsProvider call previously had no bound and could hold
// mainToMainSyncMutex indefinitely; each Drive I/O step in performMainToMainSync() is
// capped at this long instead.
internal const val DRIVE_IO_TIMEOUT_MS = 20_000L


/**
 * Which seat heat/cool functions a specific car actually has (user-configured).
 *
 * The US remote-start climate command addresses four seat positions only —
 * driver, front passenger, rear-left and rear-right — so even on a 7-seater
 * those are the seats that can be controlled remotely. Each is independently
 * heat- and/or cool-capable.
 */
data class SeatConfig(
    val driverHeat: Boolean = true,
    val driverCool: Boolean = false,
    val passHeat: Boolean = true,
    val passCool: Boolean = false,
    val rearLeftHeat: Boolean = false,
    val rearLeftCool: Boolean = false,
    val rearRightHeat: Boolean = false,
    val rearRightCool: Boolean = false,
    /** Whether the car has a heated steering wheel (no reliable API flag). */
    val steeringWheel: Boolean = false,
) {
    val any: Boolean
        get() = driverHeat || driverCool || passHeat || passCool ||
            rearLeftHeat || rearLeftCool || rearRightHeat || rearRightCool
}


/** User-confirmed powertrain (the US API only exposes EV vs gas). */
enum class Powertrain { GAS, HYBRID, PHEV, EV }


/**
 * The ONE powertrain resolution rule, used by every surface in the app that
 * needs to know a vehicle's powertrain -- an explicit user override (stored
 * per-VIN; [SettingsStore.powertrain]/[SettingsStore.setPowertrain] persist
 * it, [UiState.powertrains] holds it in memory for the running app) always
 * wins; otherwise infer from the API's own [Vehicle.isEv] flag, which only
 * ever distinguishes EV from everything else.
 *
 * Before this, [UiState.powertrainOf] (in-memory, UI-facing) and the
 * background alert path ([CarAlerts.evaluate], no UiState to read) each
 * re-derived this same override-or-infer rule independently, and neither one
 * could see a hybrid/PHEV override the other correctly honoured -- exactly
 * the kind of drift a single shared rule is for. Both now call this.
 */
fun resolvePowertrain(v: Vehicle, override: Powertrain?): Powertrain =

    override ?: if (v.isEv) Powertrain.EV else Powertrain.GAS


/**
 * User-confirmed head-unit generation for a Hyundai/Genesis US vehicle --
 * the same GEN5W/ccNC split [com.bloo.bluelink.data.isGen5W] already infers
 * from the API's own `generation` field, made overridable the same way
 * [Powertrain] is: only Hyundai/Genesis US cars ever report a real
 * generation number (Kia US, every Canada brand, and Europe all resolve
 * [com.bloo.bluelink.data.isGen5W] to a fixed answer regardless of this
 * choice -- see that property's own doc), so this only has anything to
 * confirm for that same population.
 */
enum class VehiclePlatform { GEN5W, CCNC }


/** When the biometric app-lock re-engages after the app leaves the foreground. */
enum class LockTiming(val label: String) {
    /** Never re-lock after launch. */
    OFF("Off"),

    /** Re-lock only when the screen actually turns off while the app is away -- a brief
     *  backgrounding (a system prompt, a quick app switch) leaves it unlocked. */
    SCREEN_OFF("Screen off"),

    /** Re-lock the moment the app is backgrounded, however briefly. */
    IMMEDIATE("Immediate"),
}


/**
 * The wire-key form of a [LockTiming], matching the string vocabulary [shouldRelockAfter]
 * switches on. NOT the enum's persistence format -- LockTiming persists via `.name` -- purely
 * the bridge into the shared re-lock predicate. Exhaustive with no `else` on purpose: adding a
 * LockTiming value must fail to compile here until its key is chosen.
 */
val LockTiming.wireKey: String

    get() = when (this) {
        LockTiming.OFF -> "off"
        LockTiming.SCREEN_OFF -> "screen_off"
        LockTiming.IMMEDIATE -> "immediate"
    }


/** Reorderable detail sections (pebbles), in their default order. */
// "climate" ahead of "ai": pre-heating/cooling the car before walking out to
// it is the single most common "glance and go" action this app exists for,
// while AI summary is a passive, network-dependent read -- the old order put
// a "Summarize" button ahead of every actual control for anyone who hasn't
// customized their section order.
val DEFAULT_SECTIONS = listOf("summary", "update", "controls", "charge", "climate", "ai", "info", "location", "trips", "diagnostics")


/**
 * Collapse key for the hero card's photo, so it rides the same per-car
 * collapsed-sections set every pebble uses -- persisted, per car, and carried by
 * Drive sync for free, instead of a parallel preference that would behave subtly
 * differently from every other collapse in the app.
 *
 * Deliberately NOT in [DEFAULT_SECTIONS]: it is not a reorderable pebble, and
 * sectionOrder() filters saved lists against that list, so this key can never leak
 * into the pebble-order UI. Nothing filters the COLLAPSED set, which is what lets it
 * round-trip.
 */
const val HERO_PHOTO_SECTION = "hero"
