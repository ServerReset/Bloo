package com.bloo.bluelink.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.composed
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.max
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.withFrameNanos
import com.bloo.bluelink.data.platform
import androidx.compose.ui.window.DialogProperties

/** Shared app-wide chrome: the glass dialog shell and the animated aurora background. */

/**
 * The app's shared pop-up dialog shell: one elevated card (icon, headline, content, stacked actions) per the
 * M3 basic-dialog layout, not AlertDialog's slots, which render as disconnected clipped boxes.
 */
@Composable
internal fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: @Composable ColumnScope.() -> Unit,
    buttons: @Composable ColumnScope.() -> Unit,
    // Optional leading icon in a 48dp primaryContainer circle; omitted entirely when null.
    icon: ImageVector? = null,
    // Optional trailing action in the title row (e.g. a delete button).
    titleTrailing: (@Composable () -> Unit)? = null,
) {
    val host = LocalDialogHost.current
    val card: @Composable () -> Unit = { DialogCard(icon, title, titleTrailing, text, buttons) }
    if (host != null) {
        // Drawn by the app's dialog layer (DialogMotion.kt): glass over the live app, animating in and out.
        val entry = remember { DialogEntry() }
        SideEffect {
            entry.onDismiss = onDismissRequest
            entry.content = card
        }
        DisposableEffect(entry) {
            host.entries.add(entry)
            onDispose { entry.leaving = true }
        }
        return
    }
    // No layer to draw into (a preview, a secondary window): an ordinary platform dialog.
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassSurface(
            shape = ExtraLargeShape,
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp).fillMaxWidth(),
            tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
        ) { card() }
    }
}

/** The inside of every dialog: optional icon badge, title row, scrolling body, then the buttons. */
@Composable
private fun DialogCard(
    icon: ImageVector?,
    title: String,
    titleTrailing: (@Composable () -> Unit)?,
    text: @Composable ColumnScope.() -> Unit,
    buttons: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(24.dp)) {
        if (icon != null) {
            Box(
                Modifier.size(48.dp).background(scheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(GapSection))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (titleTrailing != null) {
                Spacer(Modifier.width(GapRow))
                titleTrailing()
            }
        }
        Spacer(Modifier.height(GapRow))
        Column(
            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(GapGroup),
            content = text,
        )
        Spacer(Modifier.height(GapSection))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapHairline), content = buttons)
    }
}

/** Triangle wave in [0,1]: rises for [periodMs], falls for [periodMs], repeats. */
internal fun triangleWave(elapsedMs: Long, periodMs: Long): Float {
    val phase = elapsedMs % (2 * periodMs)
    return if (phase < periodMs) phase.toFloat() / periodMs else 2f - phase.toFloat() / periodMs
}

/**
 * Animated gradient-blob backdrop for login, onboarding and (optionally) the garage.
 * Blob hues derive from the surface colour rotated 180° plus tertiary/secondary. [motionMode] `static` drifts
 * on its own; `motion` adds accelerometer tilt, isolated from how the phone is held by subtracting a slow
 * moving average. `refreshing` drives a one-shot grow/hold/shrink pulse.
 */
@Composable
internal fun AuroraBackground(
    modifier: Modifier = Modifier,
    appearance: SettingsStore.Appearance? = null,
    refreshing: Boolean = false,
    /** Freezes the ambient drift and tilt sensor while true; the search panel pauses it to spare blur redraws during the IME animation. */
    paused: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // Battery saver (or pre-S, where the blur can't run): one flat surface fill, no sensor, drift or blur.
    if (!canBlurBackdrops()) {
        Box(modifier.fillMaxSize().background(scheme.surface))
        return
    }
    val motionMode = appearance?.auroraMotion ?: "static"

    // Motion follows the phone's tilt; Static gets a slow ambient drift.
    // A low smoothing alpha keeps tilt from jittering on hand tremor.
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    // Live pause flag: the sensor callback and coroutines start once and must see the newest value.
    val currentPaused by rememberUpdatedState(paused)
    val motionActive = motionMode == "motion"
    if (motionActive) {
        val ctx = LocalContext.current
        DisposableEffect(ctx) {
            val mgr = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            // Raw accelerometer values include gravity (~±9.8) from how the phone is held. raw* tracks the sensor,
            // base* a much slower average; tilt is their difference, so it stays centred whatever the resting angle.
            var rawX = 0f; var rawY = 0f
            var baseX = 0f; var baseY = 0f
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // No writes while paused: samples stall instead of invalidating.
                    if (currentPaused) return
                    val fastAlpha = 0.08f
                    val slowAlpha = 0.01f
                    val x = -event.values[0]
                    val y = event.values[1]
                    rawX = rawX * (1 - fastAlpha) + x * fastAlpha
                    rawY = rawY * (1 - fastAlpha) + y * fastAlpha
                    baseX = baseX * (1 - slowAlpha) + x * slowAlpha
                    baseY = baseY * (1 - slowAlpha) + y * slowAlpha
                    tiltX = (rawX - baseX) * 0.06f
                    tiltY = (rawY - baseY) * 0.06f
                }
                override fun onAccuracyChanged(s: Sensor, acc: Int) {}
            }
            if (sensor != null) mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { mgr.unregisterListener(listener) }
        }
    } else {
        // Reset so leaving Motion mode doesn't strand the blobs at a stale offset.
        LaunchedEffect(motionActive) { tiltX = 0f; tiltY = 0f }
    }

    // Keyed on its inputs so the HSV round-trips don't re-run on every explosion frame.
    val (basePrimary, baseTertiary, baseSecondary) = remember(scheme.tertiary, scheme.secondary, scheme.surface) {
        val primary = run {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(scheme.surface.toArgb(), hsv)
            hsv[0] = (hsv[0] + 180f) % 360f
            Color(android.graphics.Color.HSVToColor(hsv))
        }
        Triple(primary, scheme.tertiary, scheme.secondary)
    }

    // Guaranteed grow-then-shrink pulse with a brief hold at the peak, so a quick refresh still visibly animates.
    val explosion = remember { Animatable(0f) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            explosion.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
            delay(220)
        }
        explosion.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    // Fade the blobs in so the heaviest draw (full-screen blur) isn't at full cost on the cold-start first frame.
    // Alpha is read in drawBehind (draw phase only).
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(MotionMedium)) }
    // Defer the blur one frame: the blobs are invisible at first, so blurring a flat surface is wasted GPU.
    var blurOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        blurOn = true
    }
    // Read inside drawBehind, not composition: reading it here would recompose and rebuild the full-screen RenderEffect every frame.
    val explodeAlpha = { 1f + explosion.value * 2.5f }
    val explodeSize = { 1f + explosion.value * 0.8f }
    val explodeSpread = { 1f + explosion.value * 0.3f }
    // Both modes run this ambient drift (Motion adds tilt on top) so a still phone isn't a frozen frame.
    // Hand-ticked at ~12fps, not Compose's per-frame clock, which forced a full-screen blur redraw every vsync.
    var p1 by remember { mutableFloatStateOf(0.5f) }
    var p2 by remember { mutableFloatStateOf(0.5f) }
    var p3 by remember { mutableFloatStateOf(0.5f) }
    // Ticks in both modes; wide enough to be clearly visible under the heavy blur.
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            if (currentPaused) {
                delay(120)
                continue
            }
            val elapsed = System.currentTimeMillis() - start
            p1 = 0.32f + (0.68f - 0.32f) * triangleWave(elapsed, 9_000L)
            p2 = 0.68f + (0.32f - 0.68f) * triangleWave(elapsed, 7_000L)
            p3 = 0.35f + (0.65f - 0.35f) * triangleWave(elapsed, 6_000L)
            delay(80)
        }
    }
    fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f
    Box(
        modifier
            .fillMaxSize()
            // 44dp: heavier blur washes the blobs out and redraws at every drift tick (the app's costliest steady draw).
            // Constant radius: animating it rebuilds the RenderEffect each frame; the pulse rides on blob alpha/size/spread.
            .then(if (blurOn) Modifier.blur(44.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded) else Modifier)
            .drawBehind {
                drawRect(scheme.surface)
                fun blob(c: Color, fx: Float, fy: Float, r: Float) =
                    drawCircle(c, radius = size.minDimension * r, center = Offset(size.width * fx, size.height * fy))
                val a = appear.value
                blob(basePrimary.copy(alpha = (0.30f * explodeAlpha() * a).coerceIn(0f, 1f)), (mix(0.26f, 0.74f, p1) + tiltX) * explodeSpread(), (mix(0.30f, 0.65f, p2) + tiltY) * explodeSpread(), 0.45f * explodeSize())
                blob(baseTertiary.copy(alpha = (0.25f * explodeAlpha() * a).coerceIn(0f, 1f)), (mix(0.32f, 0.68f, p2) - tiltX) * explodeSpread(), (mix(0.35f, 0.70f, p3) - tiltY) * explodeSpread(), 0.40f * explodeSize())
                blob(baseSecondary.copy(alpha = (0.20f * explodeAlpha() * a).coerceIn(0f, 1f)), (mix(0.32f, 0.68f, p3) + tiltX) * explodeSpread(), (mix(0.28f, 0.62f, p1) + tiltY) * explodeSpread(), 0.38f * explodeSize())
            },
    )
}
