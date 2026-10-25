package com.bloo.bluelink.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

/*
 * The phone's tilt, read once for the whole app. The aurora backdrop drifts with it and the pebbles' glass edges catch light with
 * it, so there is one sensor listener and one pair of numbers, not one per consumer. Both are small, eased and high-passed
 * (a rested hold reads as zero, a change of grip as a brief tilt), and both are READ in the draw phase, so a sample redraws what
 * reads it and recomposes nothing.
 */

/** Tilt gain per m/s^2, the most the tilt may reach (fraction of the screen), and the easing between samples. */
private const val TiltGain = 0.012f
internal const val TiltMax = 0.06f
private const val TiltEase = 0.12f

/** The held-angle reading: m/s^2 of gravity that counts as fully leaned, the easing per sample, and the smallest write. */
private const val HoldRange = 6f
private const val HoldEase = 0.2f
private const val HoldStep = 0.01f

/** A write only when it has moved by more than the eye can see: each write redraws every reader. */
private const val TiltStep = 0.004f

/** The eased, high-passed tilt on each axis, -[TiltMax]..[TiltMax], and whether sampling is frozen (the search panel's IME animation). */
@Stable
internal class TiltState {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)

    /** How the phone is HELD, -1..1 per axis (lean right / lean forward): slow and not high-passed, so a pebble's sheen sits where the angle puts it. */
    var holdX by mutableFloatStateOf(0f)
    var holdY by mutableFloatStateOf(0f)

    /** Plain, not state: the sensor callback reads it, nothing draws from it. */
    @Volatile var paused: Boolean = false
}

/** The app's tilt, or null where none is provided (previews, the watch-less widgets): readers then see a phone lying still. */
internal val LocalTilt = compositionLocalOf<TiltState?> { null }

/**
 * Listens to the accelerometer while the app is on screen and [active], feeding the returned [TiltState]. Never listens under
 * Battery Saver or a throttling phone (pass `PowerPolicy.ambientMotion` as [active]), nor once the app has sat untouched past
 * the idle mark ([ambient]): a phone left on the garage then has nothing running, not even a wake-up to ask.
 */
@Composable
internal fun rememberTilt(active: Boolean, ambient: AmbientActivity?): TiltState {
    val tilt = remember { TiltState() }
    if (!active) {
        LaunchedEffect(Unit) { tilt.x = 0f; tilt.y = 0f; tilt.holdX = 0f; tilt.holdY = 0f }
        return tilt
    }
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(ctx, lifecycleOwner, ambient) {
        val mgr = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // Raw accelerometer values include gravity (~±9.8) from how the phone is held. raw* tracks the sensor, base* a much slower
        // average; tilt is their difference, so it stays centred whatever the resting angle.
        var rawX = 0f; var rawY = 0f
        var baseX = 0f; var baseY = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // No writes while paused: samples stall instead of invalidating.
                if (tilt.paused) return
                // And none once the app has gone idle: a tilt only moves what is already not moving, so past the idle mark the
                // samples are pure redraw for nothing.
                if (ambient != null && android.os.SystemClock.uptimeMillis() - ambient.lastTouchMs > AmbientIdleMs) return
                val fastAlpha = 0.08f
                val slowAlpha = 0.01f
                val x = -event.values[0]
                val y = event.values[1]
                rawX = rawX * (1 - fastAlpha) + x * fastAlpha
                rawY = rawY * (1 - fastAlpha) + y * fastAlpha
                baseX = baseX * (1 - slowAlpha) + x * slowAlpha
                baseY = baseY * (1 - slowAlpha) + y * slowAlpha
                // A subtle parallax, never a displacement: small, capped, and eased so a change of grip glides instead of jumping.
                val targetX = ((rawX - baseX) * TiltGain).coerceIn(-TiltMax, TiltMax)
                val targetY = ((rawY - baseY) * TiltGain).coerceIn(-TiltMax, TiltMax)
                val nx = tilt.x + (targetX - tilt.x) * TiltEase
                val ny = tilt.y + (targetY - tilt.y) * TiltEase
                if (abs(nx - tilt.x) > TiltStep) tilt.x = nx
                if (abs(ny - tilt.y) > TiltStep) tilt.y = ny
                // The held angle: gravity's share on each axis (about 6 m/s^2 is a steep lean), eased over a second or so.
                val hx = tilt.holdX + ((x / HoldRange).coerceIn(-1f, 1f) - tilt.holdX) * HoldEase
                val hy = tilt.holdY + ((y / HoldRange).coerceIn(-1f, 1f) - tilt.holdY) * HoldEase
                if (abs(hx - tilt.holdX) > HoldStep) tilt.holdX = hx
                if (abs(hy - tilt.holdY) > HoldStep) tilt.holdY = hy
            }
            override fun onAccuracyChanged(s: Sensor, acc: Int) {}
        }
        // Listening only while the app is on screen: the observer replays ON_START on attach, so this registers at once when
        // already started. NORMAL (~5Hz), not UI (~15Hz): the tilt is a slow parallax, so a third of the samples feeds the same
        // motion for a third of the wake-ups.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (sensor != null) mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                Lifecycle.Event.ON_STOP -> mgr.unregisterListener(listener)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mgr.unregisterListener(listener)
        }
    }
    return tilt
}
