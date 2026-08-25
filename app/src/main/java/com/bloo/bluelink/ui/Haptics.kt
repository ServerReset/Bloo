package com.bloo.bluelink.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A carefully-tuned haptic vocabulary. Each interaction gets its own *distinct*
 * feel, built from [VibrationEffect] composition primitives on capable motors
 * (API 31+) and graceful waveform fallbacks below that.
 *
 * Intensity is intentionally left to the system/motor; the character of each
 * effect (rhythm, rise/fall, deceleration) is what makes them recognisable.
 */
class Haptics(context: Context) {

    // API 31 (S) moved vibrator access behind VibratorManager; below that the vibrator
    // is fetched directly from the Context (deprecated but still the only path pre-S).
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Volatile
    var enabled: Boolean = true

    /** Rich composition primitives are available (API 31+ with hardware support). */
    private val composes: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (vibrator?.hasVibrator() == true) &&
            runCatching {
                vibrator?.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_TICK,
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                ) == true
            }.getOrDefault(false)

    /** Whether the motor can vary vibration strength, not just on/off -- gates whether
     *  [oneShot]/[waveform] pass through a real amplitude or fall back to DEFAULT_AMPLITUDE. */
    private val hasAmplitude = vibrator?.hasAmplitudeControl() == true

    /** Central gate every effect funnels through: skips entirely if haptics are disabled,
     *  there's no effect to play, or the device genuinely has no vibrator motor. Any
     *  platform exception from the actual vibrate() call is swallowed since a missed
     *  haptic is never worth crashing over. */
    private fun play(effect: VibrationEffect?) {
        if (!enabled || effect == null) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(effect) }
    }

    /** A one-shot fallback for pre-31 devices, honouring amplitude when possible.
     *  No SDK guard: createOneShot exists since O and the module minSdk is 26. */
    private fun oneShot(ms: Long, amplitude: Int) {
        val amp = if (hasAmplitude) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        play(VibrationEffect.createOneShot(ms, amp))
    }

    /** Multi-step fallback for devices without composition primitives: [timings] and
     *  [amplitudes] are parallel arrays alternating off/on segments in milliseconds and
     *  0-255 strength (the `-1` argument means "don't repeat, play once end-to-end"). */
    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        // No SDK guard, same reason as oneShot (minSdk 26 = O).
        if (hasAmplitude) play(VibrationEffect.createWaveform(timings, amplitudes, -1))
        else play(VibrationEffect.createWaveform(timings, -1))
    }

    // --- The vocabulary --------------------------------------------------

    /** Light, crisp step — slider notches, list ticks. */
    fun tick() {
        if (composes) composed { add(VibrationEffect.Composition.PRIMITIVE_TICK, 0.45f, 0) } else oneShot(8, 50)
    }

    /** Standard confirm — taps, expand/collapse, page settle. */
    fun click() {
        if (composes) composed { add(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 0) } else oneShot(12, 110)
    }

    /** Weighty confirm — lock/unlock landed, command sent. */
    fun heavy() {
        if (composes) composed {
            add(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 0)
            add(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 40)
        } else oneShot(22, 200)
    }

    /** Toggle on: a quick rise into a click. */
    fun toggleOn() {
        if (composes) composed {
            add(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f, 0)
            add(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 0)
        } else waveform(longArrayOf(0, 10, 30, 14), intArrayOf(0, 80, 0, 160))
    }

    /** Toggle off: a click falling away. */
    fun toggleOff() {
        if (composes) composed {
            add(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 0)
            add(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.7f, 0)
        } else waveform(longArrayOf(0, 14, 20, 10), intArrayOf(0, 150, 0, 70))
    }

    /**
     * Pull-to-refresh release: a tumbling "shake the dice" burst — irregular
     * ticks of varied weight that land on a final knock.
     */
    fun diceRoll() {
        if (composes) composed {
            add(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 0)
            add(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f, 26)
            add(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.55f, 30)
            add(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 18)
            add(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f, 38)
            add(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 44)
        } else waveform(
            longArrayOf(0, 12, 26, 8, 30, 14, 18, 10, 38, 22),
            intArrayOf(0, 120, 0, 70, 0, 150, 0, 90, 0, 230),
        )
    }

    /**
     * Slot-machine settle: rapid ticking that decelerates and stops — used when
     * refreshed numbers roll into place.
     */
    fun slotSettle() {
        if (composes) {
            // 16 ticks, each one both later (delay *= 1.22, capped at 150ms) and weaker
            // (scale *= 0.92, floored at 0.25) than the last -- the growing gap plus
            // shrinking strength together read as something spinning down to a stop.
            val c = VibrationEffect.startComposition()
            var delay = 16
            var scale = 0.85f
            repeat(16) { i ->
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale.coerceAtLeast(0.25f), if (i == 0) 0 else delay)
                delay = (delay * 1.22f).toInt().coerceAtMost(150)
                scale *= 0.92f
            }
            play(c.compose())
        } else {
            // Decelerating ticks: timings grow each cycle.
            val timings = ArrayList<Long>()
            val amps = ArrayList<Int>()
            var gap = 16L
            var amp = 200
            timings.add(0); amps.add(0)
            repeat(14) {
                timings.add(7); amps.add(amp)
                timings.add(gap); amps.add(0)
                gap = (gap * 1.22f).toLong().coerceAtMost(150)
                amp = (amp * 0.92f).toInt().coerceAtLeast(60)
            }
            waveform(timings.toLongArray(), amps.toIntArray())
        }
    }

    /**
     * A short left-to-right "sweep" (soft → strong rise), looped by the UI while
     * something is loading so progress is felt, not just seen.
     */
    fun loadingSweep() {
        if (composes) composed {
            add(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f, 0)
            add(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.6f, 24)
        } else {
            waveform(longArrayOf(0, 8, 18, 16, 16, 22), intArrayOf(0, 40, 0, 90, 0, 160))
        }
    }

    /** Celebration: a boom that scatters into crackling pops, then trails off. */
    fun fireworks() {
        if (composes) {
            val c = VibrationEffect.startComposition()
            c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 0) // the boom
            var delay = 70
            var scale = 0.8f
            repeat(12) {
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale.coerceAtLeast(0.25f), delay)
                delay = (40 + (Math.random() * 120).toInt())
                scale *= 0.93f
            }
            play(c.compose())
        } else {
            waveform(
                longArrayOf(0, 30, 60, 8, 50, 8, 40, 8, 90, 8, 70, 8, 120, 8),
                intArrayOf(0, 240, 0, 150, 0, 110, 0, 160, 0, 90, 0, 120, 0, 80),
            )
        }
    }

    // --- Composition helpers --------------------------------------------

    /** Small DSL so each effect above can declare its primitives as a plain `add(...)`
     *  list instead of manually managing a [VibrationEffect.Composition] builder; wraps
     *  the whole built sequence into one [play] call once [build] finishes adding steps. */
    private inline fun composed(build: CompositionBuilder.() -> Unit) {
        // startComposition/addPrimitive are API 30+; the `composes` gate above
        // only lets callers through on API 31+, but the gate lives in a
        // separate property and lint can't track that -- explicit guard keeps
        // the invariant self-contained and absolutely crash-proof on
        // API 26-29 devices (oneShot/waveform covers those).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val c = VibrationEffect.startComposition()
        CompositionBuilder(c).build()
        play(c.compose())
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.R)
    private class CompositionBuilder(val c: VibrationEffect.Composition) {
        /** [scale] is per-primitive strength (0..1); [delayMs] is the gap before this
         *  primitive starts, relative to the previous one finishing. */
        fun add(primitive: Int, scale: Float, delayMs: Int) {
            c.addPrimitive(primitive, scale, delayMs)
        }
    }

}

/** Lets any composable reach the haptics engine. Defaults to a no-op (disabled). */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }
