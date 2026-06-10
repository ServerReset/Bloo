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

    private val hasAmplitude = vibrator?.hasAmplitudeControl() == true

    private fun play(effect: VibrationEffect?) {
        if (!enabled || effect == null) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(effect) }
    }

    /** A one-shot fallback for pre-31 devices, honouring amplitude when possible. */
    private fun oneShot(ms: Long, amplitude: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amp = if (hasAmplitude) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            play(VibrationEffect.createOneShot(ms, amp))
        }
    }

    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (hasAmplitude) play(VibrationEffect.createWaveform(timings, amplitudes, -1))
            else play(VibrationEffect.createWaveform(timings, -1))
        }
    }

    // --- The vocabulary --------------------------------------------------

    /** Light, crisp step — slider notches, list ticks. */
    fun tick() {
        if (composes) composed { add(PRIM_TICK, 0.45f, 0) } else oneShot(8, 50)
    }

    /** Standard confirm — taps, expand/collapse, page settle. */
    fun click() {
        if (composes) composed { add(PRIM_CLICK, 0.6f, 0) } else oneShot(12, 110)
    }

    /** Weighty confirm — lock/unlock landed, command sent. */
    fun heavy() {
        if (composes) composed {
            add(PRIM_CLICK, 1f, 0)
            add(PRIM_TICK, 0.5f, 40)
        } else oneShot(22, 200)
    }

    /** Toggle on: a quick rise into a click. */
    fun toggleOn() {
        if (composes) composed {
            add(PRIM_QUICK_RISE, 0.7f, 0)
            add(PRIM_CLICK, 0.7f, 0)
        } else waveform(longArrayOf(0, 10, 30, 14), intArrayOf(0, 80, 0, 160))
    }

    /** Toggle off: a click falling away. */
    fun toggleOff() {
        if (composes) composed {
            add(PRIM_CLICK, 0.6f, 0)
            add(PRIM_QUICK_FALL, 0.7f, 0)
        } else waveform(longArrayOf(0, 14, 20, 10), intArrayOf(0, 150, 0, 70))
    }

    /** Energy "flowing" feel when a charge starts — a slow rise with two pulses. */
    fun chargeStart() {
        if (composes) composed {
            add(PRIM_SLOW_RISE, 0.8f, 0)
            add(PRIM_TICK, 0.5f, 60)
            add(PRIM_TICK, 0.6f, 90)
        } else waveform(longArrayOf(0, 40, 40, 12, 40, 12), intArrayOf(0, 120, 0, 80, 0, 110))
    }

    /**
     * Pull-to-refresh release: a tumbling "shake the dice" burst — irregular
     * ticks of varied weight that land on a final knock.
     */
    fun diceRoll() {
        if (composes) composed {
            add(PRIM_TICK, 0.6f, 0)
            add(PRIM_TICK, 0.4f, 26)
            add(PRIM_CLICK, 0.55f, 30)
            add(PRIM_TICK, 0.5f, 18)
            add(PRIM_TICK, 0.7f, 38)
            add(PRIM_CLICK, 1f, 44)
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
            val c = VibrationEffect.startComposition()
            var delay = 16
            var scale = 0.85f
            repeat(16) { i ->
                c.addPrimitive(PRIM_TICK, scale.coerceAtLeast(0.25f), if (i == 0) 0 else delay)
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
            add(PRIM_TICK, 0.3f, 0)
            add(PRIM_QUICK_RISE, 0.6f, 24)
        } else {
            waveform(longArrayOf(0, 8, 18, 16, 16, 22), intArrayOf(0, 40, 0, 90, 0, 160))
        }
    }

    /** Success chime — rise then crisp click. */
    fun success() {
        if (composes) composed {
            add(PRIM_QUICK_RISE, 0.6f, 0)
            add(PRIM_CLICK, 0.8f, 10)
        } else waveform(longArrayOf(0, 16, 24, 16), intArrayOf(0, 120, 0, 200))
    }

    /** Error buzz — two firm knocks. */
    fun error() {
        if (composes) composed {
            add(PRIM_CLICK, 1f, 0)
            add(PRIM_CLICK, 1f, 120)
        } else waveform(longArrayOf(0, 24, 110, 24), intArrayOf(0, 230, 0, 230))
    }

    // --- Composition helpers --------------------------------------------

    private inline fun composed(build: CompositionBuilder.() -> Unit) {
        val c = VibrationEffect.startComposition()
        CompositionBuilder(c).build()
        play(c.compose())
    }

    private class CompositionBuilder(val c: VibrationEffect.Composition) {
        fun add(primitive: Int, scale: Float, delayMs: Int) {
            c.addPrimitive(primitive, scale, delayMs)
        }
    }

    private companion object {
        // Resolved once; only referenced on API 31+ paths.
        val PRIM_TICK = VibrationEffect.Composition.PRIMITIVE_TICK
        val PRIM_CLICK = VibrationEffect.Composition.PRIMITIVE_CLICK
        val PRIM_QUICK_RISE = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        val PRIM_SLOW_RISE = VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
        val PRIM_QUICK_FALL = VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
    }
}

/** Lets any composable reach the haptics engine. Defaults to a no-op (disabled). */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }
