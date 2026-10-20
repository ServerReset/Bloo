package com.bloo.bluelink.data

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the device itself reports about its resources, read straight from the system. */
data class DeviceState(
    /** The system Battery Saver switch ([PowerManager.isPowerSaveMode]). */
    val batterySaver: Boolean = false,
    /** The phone is running hot enough that Android is throttling ([PowerManager.THERMAL_STATUS_SEVERE] or worse). */
    val thermalThrottled: Boolean = false,
    /** The device is locked (screen lock or lockdown): nobody has proven who they are right now. */
    val deviceLocked: Boolean = false,
    /** The wearer asked for reduced effects (the watch's own switch): everything runs lean, as under Battery Saver. */
    val manualLean: Boolean = false,
)

/**
 * EVERYTHING that changes when the device is short on power or heat, in one place. Nothing else in
 * the app reads Battery Saver (or the thermal state) directly: visuals, animation, sensors,
 * background polling, prefetching and the AI all ask this policy, so a switch in Quick Settings
 * changes them all at once and they can never disagree.
 */
data class PowerPolicy(
    /** The system Battery Saver switch, as the device reports it. */
    val batterySaver: Boolean,
    /** Battery Saver on OR the phone throttling: the app runs lean. Every field below follows this. */
    val lean: Boolean,
) {
    /** Real backdrop blur on glass surfaces (solid tinted fills when lean). */
    val blurBackdrops: Boolean get() = !lean

    /** Springs and bounces (short plain fades when lean). */
    val bouncySprings: Boolean get() = !lean

    /** Anything that moves on its own: the aurora drift, the tilt sensor, charge shimmer. */
    val ambientMotion: Boolean get() = !lean

    /** Warm the next map tiles before they are needed. */
    val prefetchMapTiles: Boolean get() = !lean

    /** Summarise a car with the on-device AI on its own (still available by tapping). */
    val autoSummarize: Boolean get() = !lean

    /** GPS-grade location fixes (balanced, network-assisted ones when lean). */
    val highAccuracyLocation: Boolean get() = !lean

    /** Multiplies the wait between background polls and refreshes: 1 normally, 3 when lean. */
    val pollIntervalMultiplier: Int get() = if (lean) 3 else 1

    companion object {
        fun of(state: DeviceState) = PowerPolicy(
            batterySaver = state.batterySaver,
            lean = state.batterySaver || state.thermalThrottled || state.manualLean,
        )
    }
}

/**
 * The live device state and the [PowerPolicy] derived from it, kept current by one set of system
 * listeners for the whole process. Works in the phone app, its workers and receivers, and the watch.
 */
object DeviceStatus {
    private val _state = MutableStateFlow(DeviceState())
    val state: StateFlow<DeviceState> = _state

    private val _policy = MutableStateFlow(PowerPolicy.of(DeviceState()))
    val policy: StateFlow<PowerPolicy> = _policy

    @Volatile private var started = false

    /** The wearer's manual "reduce effects" switch; it joins the device's own reasons to run lean (see [PowerPolicy.of]). */
    @Volatile private var manualLean = false

    /** Turns the manual reduced-effects mode on or off; the policy follows at once. */
    fun setManualLean(on: Boolean) {
        manualLean = on
        publish(_state.value.copy(manualLean = on))
    }

    /** Starts the listeners (once) and returns the current policy. Safe from any thread or entry point. */
    fun policy(context: Context): PowerPolicy {
        ensureInitialized(context)
        return _policy.value
    }

    fun ensureInitialized(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            val app = context.applicationContext
            publish(read(app))
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) { publish(read(app)) }
            }
            // ContextCompat: past the API 33 cutover a context receiver must declare its exportedness.
            ContextCompat.registerReceiver(
                app, receiver,
                IntentFilter().apply {
                    addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    app.getSystemService(PowerManager::class.java)
                        ?.addThermalStatusListener(ContextCompat.getMainExecutor(app)) { publish(read(app)) }
                }
            }
            started = true
        }
    }

    private fun publish(s: DeviceState) {
        val changed = s != _state.value
        _state.value = s
        _policy.value = PowerPolicy.of(s)
        // Logged on every CHANGE, so the app's own log shows exactly why it went lean (and what the
        // device actually reported) instead of it looking like a phantom battery saver.
        if (changed) {
            AppLog.log("power: battery saver=${s.batterySaver} thermalThrottled=${s.thermalThrottled} manual=${s.manualLean} -> lean=${_policy.value.lean}")
        }
    }

    private fun read(context: Context): DeviceState {
        val pm = context.getSystemService(PowerManager::class.java)
        val km = context.getSystemService(KeyguardManager::class.java)
        return DeviceState(
            batterySaver = pm?.isPowerSaveMode == true,
            // CRITICAL or worse (4+ of 6), not SEVERE (3): many devices report SEVERE for an
            // ordinary warm phone, which was making the app run lean (no blur, no motion, no text
            // reveal) while the user had neither battery saver nor a genuinely hot device on.
            thermalThrottled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (pm?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_CRITICAL,
            deviceLocked = km?.isDeviceLocked == true,
            manualLean = manualLean,
        )
    }
}
