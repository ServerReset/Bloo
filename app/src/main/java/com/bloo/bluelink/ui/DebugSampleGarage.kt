package com.bloo.bluelink.ui

import android.content.Context
import com.bloo.bluelink.BuildConfig
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.setThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.Battery12V
import com.bloo.bluelink.data.Dte
import com.bloo.bluelink.data.EvStatus
import com.bloo.bluelink.data.TempValue
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus

/**
 * A sample garage, for store screenshots and nothing else.
 *
 * The real garage is only ever reached after a saved login is found (`coldStartAutoLogin`), and a
 * saved login lives in the Keystore-backed vault - so there is no way to photograph the garage on a
 * device that has never signed in, and signing in is the one thing that would put a real car, its
 * name and its location on a public listing.
 *
 * So this fabricates the *state* instead: one car that belongs to nobody, with no VIN worth having,
 * **no location at all**, and a charge/lock status so the hero and the pebbles actually render rather
 * than sitting empty. The UI is the real UI; only the data is invented.
 *
 * Everything here is behind `BuildConfig.DEBUG` and the flag is only ever written by the debug
 * source set's receiver, so a release build reads `false` at the one call site and R8 removes the
 * whole object. The check is in `coldStartAutoLogin`, which is the one place that decides
 * Login-versus-Garage.
 *
 * Commands (debug build only):
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd garage
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd lock
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd off
 */
internal object DebugSampleGarage {

    private const val PREFS = "bloo_debug_fixtures"
    private const val KEY_GARAGE = "sample_garage"
    private const val KEY_LOCK = "sample_lock"

    /** The car's synthetic identity. Deliberately not a valid VIN, and never displayed. */
    const val SAMPLE_VIN = "SAMPLEVIN00000001"

    fun enabled(context: Context): Boolean =
        BuildConfig.DEBUG && prefs(context).getBoolean(KEY_GARAGE, false)

    /** Whether the fabricated state should also come up locked, for the lock-screen capture. */
    fun locked(context: Context): Boolean =
        BuildConfig.DEBUG && prefs(context).getBoolean(KEY_LOCK, false)

    fun set(context: Context, garage: Boolean?, lock: Boolean?) {
        if (!BuildConfig.DEBUG) return
        prefs(context).edit().apply {
            garage?.let { putBoolean(KEY_GARAGE, it) }
            lock?.let { putBoolean(KEY_LOCK, it) }
        }.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Make sure a PIN record exists, so the lock screen has a keypad to draw.
     *
     * The two state flags are not enough: with `appPinSet` true but nothing in the vault, the lock
     * renders its dead end ("Bloo is locked / Please try opening Bloo again") instead of the PIN
     * screen. This writes a real record through the app's own store, which is legitimate here
     * because it runs inside the app - and the value is irrelevant, since a screenshot never needs
     * to unlock anything. Debug builds only.
     */
    fun ensurePin(context: Context) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val store = com.bloo.bluelink.data.CredentialStore(context.applicationContext)
            if (store.getPinRecord() != null) return
            val salt = com.bloo.bluelink.data.PinCrypto.newSalt()
            val iterations = com.bloo.bluelink.data.PinCrypto.PIN_DEFAULT_ITERATIONS
            val record = com.bloo.bluelink.data.PinRecord(
                salt = salt,
                iterations = iterations,
                hash = com.bloo.bluelink.data.PinCrypto.hash(SAMPLE_PIN, salt, iterations),
            )
            store.setPinRecord(record.encode())
            AppLog.log("DebugSampleGarage: wrote a sample PIN record so the lock screen can render")
        }
    }

    /** Irrelevant - only the presence of a record matters, and the lock is never unlocked. */
    private const val SAMPLE_PIN = "1234"

    /**
     * Set the app's own display mode.
     *
     * The system night mode does NOT move this app: its stored theme is an explicit value (it was
     * AMOLED here), so `cmd uimode night yes/no` changes nothing and two "different" captures come
     * out byte-identical. Whatever the screenshots need, it has to be written here.
     */
    fun setTheme(context: Context, theme: String) {
        if (!BuildConfig.DEBUG) return
        val mode = when (theme.lowercase()) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            "system" -> ThemeMode.SYSTEM
            else -> null
        } ?: return
        // The setter is suspend; a receiver is not. One throwaway scope is enough for a debug switch
        // whose whole job is "write a preference, then restart the app".
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                SettingsStore(context.applicationContext).setThemeMode(mode)
                AppLog.log("DebugSampleGarage: theme set to $mode")
            }
        }
    }

    /**
     * The fabricated state. No `locations` and no `placeNames` are set - that is what keeps the
     * Location pebble empty, and the car's position out of the screenshot.
     */
    fun apply(state: UiState, locked: Boolean): UiState {
        val car = Vehicle(
            vin = SAMPLE_VIN,
            regId = "sample-vehicle",
            name = "My Car",
            model = "IONIQ 5",
            generation = "1",
            brandIndicator = "H",
            isEv = true,
            odometer = "12,480 mi",
        )
        val status = VehicleStatus(
            doorLock = true,
            airCtrlOn = false,
            engine = false,
            acc = false,
            trunkOpen = false,
            hoodOpen = false,
            defrost = false,
            dte = Dte(value = 214.0, unit = 1),
            airTemp = TempValue(value = "21", unit = 0),
            battery = Battery12V(batSoc = 88, batState = 0),
            evStatus = EvStatus(
                batteryCharge = false,
                batteryStatus = 72,
                batteryPlugin = 0,
            ),
        )
        return state.copy(
            screen = Screen.Garage,
            vehicles = listOf(car),
            statuses = mapOf(car.vin to status),
            lastFetched = mapOf(car.vin to System.currentTimeMillis()),
            // The lock screen renders the PIN keypad from these two flags alone; nothing in the
            // vault is touched, because a screenshot never needs the PIN to be *correct*.
            appPinSet = true,
            locked = locked,
            loading = false,
            refreshing = false,
            garageLoadError = null,
        )
    }
}
