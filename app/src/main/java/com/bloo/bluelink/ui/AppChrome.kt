package com.bloo.bluelink.ui

import kotlin.math.sin
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bloo.bluelink.data.SettingsStore
import kotlin.math.max
import kotlinx.coroutines.delay
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.LocalPowerPolicy
import com.bloo.uicommon.MotionMedium
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.GapBlock
import com.bloo.uicommon.content

/** Shared app-wide chrome: the glass dialog shell and the animated aurora background. */

/**
 * The app's shared pop-up dialog shell: one elevated card (icon, headline, content, stacked
 * actions) per the M3 basic-dialog layout, not AlertDialog's slots, which render as disconnected
 * clipped boxes.
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
        // Drawn by the app's dialog layer (DialogMotion.kt): glass over the live app, animating in
        // and out.
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
            modifier = Modifier.padding(horizontal = GapBlock).widthIn(max = 560.dp).fillMaxWidth(),
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
    // The card grows to fit whatever is in it, up to what the screen can hold; only past that does
    // the body scroll -- and then it shows a scrollbar, so "there is more below" is visible rather
    // than something you discover by trying to drag.
    val screenHDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val bodyScroll = rememberScrollState()
    // Animating the card's own size means a dialog whose body changes (a step, an error line, a progress
    // bar) grows or shrinks smoothly instead of snapping.
    Column(
        Modifier
            .animateContentSize(spring(dampingRatio = GentleDamping, stiffness = Spring.StiffnessMediumLow))
            .heightIn(max = (screenHDp * 0.9f))
            .padding(GapBlock),
    ) {
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
            RevealText(
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
            // weight(fill = false): wrap the body while it fits, take only the leftover (and scroll)
            // once the card has grown to its cap.
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(bodyScroll)
                .dialogScrollbar(bodyScroll),
            verticalArrangement = Arrangement.spacedBy(GapGroup),
            content = text,
        )
        Spacer(Modifier.height(GapSection))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapHairline), content = buttons)
    }
}

/**
 * A thin scrollbar on a scrolling dialog body, shown only while there is more to scroll: a body that
 * fits has none, and one that does not says so plainly instead of relying on a drag.
 */
@Composable
private fun Modifier.dialogScrollbar(state: ScrollState): Modifier {
    val thumb = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    return drawWithContent {
        drawContent()
        val max = state.maxValue
        if (max <= 0) return@drawWithContent
        val trackH = size.height
        val contentH = trackH + max
        val w = 4.dp.toPx()
        val thumbH = (trackH * (trackH / contentH)).coerceIn(24.dp.toPx(), trackH)
        val y = (trackH - thumbH) * (state.value.toFloat() / max).coerceIn(0f, 1f)
        drawRoundRect(
            color = thumb,
            topLeft = Offset(size.width - w - 3.dp.toPx(), y),
            size = Size(w, thumbH),
            cornerRadius = CornerRadius(w / 2f),
        )
    }
}

/** Tilt-parallax of the aurora blobs: gain per m/s^2 of tilt, the most they may shift (fraction of the screen), and the easing. */
private const val TiltGain = 0.012f
private const val TiltMax = 0.06f
private const val TiltEase = 0.12f

/** Animated gradient-blob backdrop for login, onboarding and (optionally) the garage. */
@Composable
internal fun AuroraBackground(
    modifier: Modifier = Modifier,
    appearance: SettingsStore.Appearance? = null,
    refreshing: Boolean = false,
    /**
     * Freezes the ambient drift and tilt sensor while true; the search panel pauses it to spare
     * blur redraws during the IME animation.
     */
    paused: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // Pre-S (where the glass cannot run): one flat surface fill. Under battery saver the aurora is still
    // drawn, but STANDS STILL: no drift, no tilt sensor, no animation of any kind.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
        Box(modifier.fillMaxSize().background(scheme.surface))
        return
    }
    // Lean (Battery Saver or a throttling phone): no drift, no tilt sensor. See PowerPolicy.ambientMotion.
    val saver = !LocalPowerPolicy.current.ambientMotion
    // The "app is in use" clock (see AmbientActivity). Once it goes stale the drift -- AND the tilt
    // sensor that feeds it -- stop entirely: a phone left on the garage then has nothing at all
    // running, not even a wake-up to ask whether it should keep running.
    val ambient = LocalAmbientActivity.current

    // "Motion" follows the phone's tilt (like a lock-screen wallpaper parallax); "Static" ignores
    // tilt entirely and instead gets its own slow, small ambient drift (see p1/p2/p3 below) so it
    // still reads as alive when the phone is sitting still, rather than a literally frozen frame.
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    // Live pause flag: the sensor callback and coroutines start once and must see the newest value.
    val currentPaused by rememberUpdatedState(paused)
    // The drift keeps moving while the car pager is dragged (freezing it read as a stall and jumped on
    // resume); it stands still only while the app is not on screen.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    // Tilt-follow and drift always run, except under battery saver.
    val motionActive = !saver
    if (motionActive) {
        val ctx = LocalContext.current
        DisposableEffect(ctx, lifecycleOwner) {
            val mgr = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            // Raw accelerometer values include gravity (~±9.8) from how the phone is held. raw*
            // tracks the sensor, base* a much slower average; tilt is their difference, so it stays
            // centred whatever the resting angle.
            var rawX = 0f; var rawY = 0f
            var baseX = 0f; var baseY = 0f
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // No writes while paused: samples stall instead of invalidating.
                    if (currentPaused) return
                    // And none once the backdrop has gone idle: a tilt only moves a backdrop that is
                    // already not moving, so past the idle mark the samples are pure redraw for
                    // nothing. The whole sensor path is cheap, but its WRITES are a full-screen
                    // redraw plus a re-blur under every card, which is exactly the idle heat.
                    if (ambient != null &&
                        android.os.SystemClock.uptimeMillis() - ambient.lastTouchMs > AmbientIdleMs
                    ) return
                    val fastAlpha = 0.08f
                    val slowAlpha = 0.01f
                    val x = -event.values[0]
                    val y = event.values[1]
                    rawX = rawX * (1 - fastAlpha) + x * fastAlpha
                    rawY = rawY * (1 - fastAlpha) + y * fastAlpha
                    baseX = baseX * (1 - slowAlpha) + x * slowAlpha
                    baseY = baseY * (1 - slowAlpha) + y * slowAlpha
                    // A subtle parallax, never a displacement: the old gain (0.06 per m/s^2, unbounded) pushed the
                    // blobs up to half a screen away whenever the hold on the phone changed -- the aurora "left"
                    // the moment a finger lifted, then drifted back as the slow baseline caught up. Now small,
                    // capped, and eased so a change of grip glides instead of jumping.
                    val targetX = ((rawX - baseX) * TiltGain).coerceIn(-TiltMax, TiltMax)
                    val targetY = ((rawY - baseY) * TiltGain).coerceIn(-TiltMax, TiltMax)
                    val nx = tiltX + (targetX - tiltX) * TiltEase
                    val ny = tiltY + (targetY - tiltY) * TiltEase
                    // Writes only when it has actually moved, and only past a threshold the eye can
                    // see: every write redraws the whole backdrop AND re-blurs it under every glass
                    // card, so a jitter of a thousandth of a screen was pure heat for no visible
                    // change.
                    if (kotlin.math.abs(nx - tiltX) > 0.004f) tiltX = nx
                    if (kotlin.math.abs(ny - tiltY) > 0.004f) tiltY = ny
                }
                override fun onAccuracyChanged(s: Sensor, acc: Int) {}
            }
            // Listening only while the app is on screen: the observer replays ON_START on attach, so this
            // registers at once when already started.
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START ->
                        // NORMAL (~5Hz), not UI (~15Hz): the tilt is a slow parallax on huge, slow
                        // blobs, so a third of the samples feeds the exact same motion while costing
                        // three times the wake-ups.
                        if (sensor != null) mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> mgr.unregisterListener(listener)
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mgr.unregisterListener(listener)
            }
        }
    } else {
        // Not tracking tilt in Static mode -- reset so a mode switch away from Motion doesn't leave
        // the blobs stuck at a stale offset.
        LaunchedEffect(motionActive) { tiltX = 0f; tiltY = 0f }
    }

    // Remembered on the inputs the derivation actually reads, so the HSV round-trips don't re-run
    // on every frame of the pull-to-refresh explosion animation (this composable recomposes each of
    // those frames because it reads explosion.value below; the blob colours don't depend on the
    // animation, so they shouldn't ride along with it).
    val (basePrimary, baseTertiary, baseSecondary) = remember(scheme.tertiary, scheme.secondary, scheme.surface) {
        val primary = run {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(scheme.surface.toArgb(), hsv)
            hsv[0] = (hsv[0] + 180f) % 360f
            Color(android.graphics.Color.HSVToColor(hsv))
        }
        Triple(primary, scheme.tertiary, scheme.secondary)
    }

    // A guaranteed grow-then-shrink pulse rather than a value that just chases the raw refreshing
    // boolean: a quick refresh (cache hit, or a refresh that resolves in well under a second)
    // flipped refreshing back to false before the spring had visibly moved, which read as the
    // background just snapping to its resting size instead of animating.
    val explosion = remember { Animatable(0f) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            explosion.animateTo(1f, spring(dampingRatio = PopDamping, stiffness = Spring.StiffnessMedium))
            delay(220)
        }
        explosion.animateTo(0f, spring(dampingRatio = SettleDamping, stiffness = Spring.StiffnessMediumLow))
    }
    // Fades the blobs in from nothing on the first frame this composable exists -- for a cold start,
    // that IS the very first frame the app paints at all (LoadingScreen/LoginScreen both use this).
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(MotionMedium)) }
    // Read inside drawBehind, not here, so the refresh spring never recomposes AuroraBackground.
    val explodeAlpha = { 1f + explosion.value * 2.5f }
    val explodeSize = { 1f + explosion.value * 0.8f }
    val explodeSpread = { 1f + explosion.value * 0.3f }
    // The aurora's clock, in seconds of ACTIVE time (a pause never makes it jump). Ticked at ~9fps,
    // not per frame: the blobs are big and slow, and -- because the aurora is the Haze source behind
    // every glass card -- every tick also re-captures and re-blurs the backdrop under each one. A
    // per-frame tick here was the app's costliest idle draw (and its battery/heat drain on a phone
    // that is only sitting on the garage), and even 14fps was enough to keep a phone warm; the blobs
    // move so slowly that nine looks identical and costs a third less.
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var seconds = 0f
        var last = System.nanoTime()
        fun idleNow() = ambient != null &&
            android.os.SystemClock.uptimeMillis() - ambient.lastTouchMs > AmbientIdleMs
        while (true) {
            val now = System.nanoTime()
            if (currentPaused || saver ||
                !lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
            ) {
                last = now
                delay(120)
                continue
            }
            // Idle: the drift has stopped and nothing is being touched. SUSPEND until the next touch
            // rather than polling -- otherwise an idle phone woke every 120ms, forever, just to ask
            // whether it should still be awake. The clock is not advanced across the gap, so the
            // blobs resume exactly where they froze.
            if (idleNow()) {
                last = now
                androidx.compose.runtime.snapshotFlow { ambient!!.lastTouchMs }
                    .first { android.os.SystemClock.uptimeMillis() - it <= AmbientIdleMs }
                last = System.nanoTime()
                continue
            }
            seconds += ((now - last) / 1_000_000_000f).coerceAtMost(0.2f)
            last = now
            clock = seconds
            delay(110)
        }
    }

    // Touch: the blobs swarm toward a finger and a faint glow follows it, so the aurora feels alive
    // under the hand. (No rings: navigation and touch read as one soft glow, not a ripple.) The
    // finger is observed (never consumed) by the garage; see AuroraTouch.
    //
    // The glow is PUBLISHED IN STEPS, not read straight off its springs. The aurora is the Haze
    // source behind every glass card, so any change to it re-blurs every one of them -- and a finger
    // spring changes it on every frame of an entire drag. That made an ordinary scroll cost ~60 glass
    // re-blurs a second, which is exactly a "smooth, then stuttery" feel. The glow is a huge soft
    // radial and the swarm only a gentle pull, so a stepped position and brightness is
    // indistinguishable and costs a fraction of it.
    val touch = LocalAuroraTouch.current
    val glow = remember { mutableFloatStateOf(0f) }
    val glowAt = remember { mutableStateOf(Offset.Zero) }
    if (touch != null && !saver) {
        LaunchedEffect(touch) {
            val energy = Animatable(0f)
            val follow = Animatable(Offset.Zero, Offset.VectorConverter)
            launch {
                snapshotFlow { touch.pos.value }.collectLatest { p ->
                    if (p != null) {
                        if (energy.value < 0.02f) { follow.snapTo(p); glowAt.value = p }
                        coroutineScope {
                            launch { energy.animateTo(1f, tween(160)) }
                            launch { follow.animateTo(p, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) }
                        }
                    } else {
                        energy.animateTo(0f, tween(1100))
                    }
                }
            }
            // The publisher: runs only while there is a glow to show (a finger down, or still fading).
            snapshotFlow { touch.pos.value != null || energy.value > 0.001f }.collectLatest { active ->
                if (!active) return@collectLatest
                while (true) {
                    delay(90)
                    val e = energy.value.coerceIn(0f, 1f)
                    if (kotlin.math.abs(e - glow.floatValue) >= 0.08f) glow.floatValue = e
                    val f = follow.value
                    if ((f - glowAt.value).getDistance() >= 22f) glowAt.value = f
                }
            }
        }
    }
    val auroraWhite = Color.White
    // Navigation reaction: one sprung pulse per app action (a page settling, a refresh). The lean is
    // read straight off the shared signals in the draw phase, so a page turn never recomposes this.
    val reactions = LocalAuroraReactions.current
    val kick = remember { Animatable(0f) }
    // What the draw reads: the kick in steps, so its springs repaint the backdrop (and every glass surface
    // blurring it) a handful of times, not on every frame for most of a second.
    val kickStep = remember { mutableFloatStateOf(0f) }
    if (reactions != null && !saver) {
        LaunchedEffect(kick) {
            snapshotFlow { kick.value }.collect { v ->
                if (kotlin.math.abs(v - kickStep.floatValue) >= 0.08f || (v == 0f && kickStep.floatValue != 0f)) kickStep.floatValue = v
            }
        }
        LaunchedEffect(reactions) {
            snapshotFlow { reactions.kick.intValue }.collect { k ->
                if (k > 0) {
                    kick.snapTo(0f)
                    kick.animateTo(1f, spring(dampingRatio = PopDamping, stiffness = Spring.StiffnessMedium))
                    kick.animateTo(0f, spring(dampingRatio = SettleDamping, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }
    Box(
        modifier
            .fillMaxSize()
            // The base tone is STATIC, so it is a background node drawn once -- not a full-screen
            // opaque fill re-rasterized on every tick, which it used to be, sitting under the blobs.
            .background(scheme.surface)
            // No Modifier.blur here. A full-screen 44dp blur, rebuilt on every tick, was the app's costliest
            // steady draw; the blobs are soft radial gradients instead, so the aurora reads just as soft with
            // no RenderEffect to re-render.
            .drawBehind {
                val t = clock
                val minDim = size.minDimension
                val a = appear.value
                val e = glow.floatValue
                val finger = glowAt.value
                // Navigation lean (-1..1) and the action pulse (0..1), both read here in the draw
                // phase. Battery saver freezes the aurora outright, so both are zero there.
                val leanX = if (saver) 0f else (reactions?.leanX?.floatValue ?: 0f)
                val leanY = if (saver) 0f else (reactions?.leanY?.floatValue ?: 0f)
                val k = if (saver) 0f else kickStep.floatValue
                // Five blobs on slow, uneven loops (sines at different rates, so the pattern never repeats
                // quickly), each breathing in size, and each pulled a little toward a finger.
                fun blob(c: Color, bx: Float, by: Float, ax: Float, ay: Float, f1: Float, f2: Float, ph: Float, r: Float, alpha: Float) {
                    // The lean is OPPOSITE the navigation: scroll down and the glow drifts to the top,
                    // swipe right and it drifts left, so the aurora answers the gesture. Added to the
                    // tilt parallax, so it also dances with how the phone is held.
                    var cx = size.width * ((bx + ax * sin(t * f1 + ph) + tiltX * (if (ph > 2f) -1f else 1f)) * explodeSpread()) - size.width * leanX * 0.11f
                    var cy = size.height * ((by + ay * sin(t * f2 + ph * 1.3f) + tiltY * (if (ph > 2f) -1f else 1f)) * explodeSpread()) - size.height * leanY * 0.11f
                    if (e > 0.001f) {
                        val dx = finger.x - cx
                        val dy = finger.y - cy
                        val pull = e * 0.22f * kotlin.math.exp(-kotlin.math.sqrt(dx * dx + dy * dy) / (minDim * 0.9f))
                        cx += dx * pull
                        cy += dy * pull
                    }
                    val radius = minDim * r * (1f + 0.09f * sin(t * 0.55f + ph * 2f)) * explodeSize() * (1f + 0.18f * e) * (1f + 0.22f * k)
                    val col = c.copy(alpha = (alpha * (1f + 0.5f * e) * explodeAlpha() * a * (1f + 0.35f * k)).coerceIn(0f, 1f))
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(col, col.copy(alpha = col.alpha * 0.55f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = radius,
                        ),
                        radius = radius,
                        center = Offset(cx, cy),
                    )
                }
                blob(basePrimary, 0.50f, 0.42f, 0.30f, 0.20f, 0.34f, 0.27f, 0.0f, 0.56f, 0.66f)
                blob(baseTertiary, 0.50f, 0.52f, 0.28f, 0.22f, 0.29f, 0.41f, 1.7f, 0.48f, 0.58f)
                blob(baseSecondary, 0.50f, 0.46f, 0.30f, 0.24f, 0.38f, 0.31f, 3.1f, 0.46f, 0.52f)
                if (e > 0.001f) {
                    // A soft glow under the finger that shifts between the palette's colours, with a
                    // hot core. It is the one reactive light: it dances with the touch and fades back
                    // into the drift when the finger lifts.
                    val mixT = 0.5f + 0.5f * sin(t * 1.3f)
                    val glow = lerp(basePrimary, baseTertiary, mixT)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(lerp(glow, auroraWhite, 0.3f).copy(alpha = 0.6f * e), glow.copy(alpha = 0.22f * e), Color.Transparent),
                            center = finger, radius = minDim * 0.34f,
                        ),
                        radius = minDim * 0.34f, center = finger,
                    )
                }
            },
    )
}

/**
 * The finger on the garage, observed (not consumed) so the aurora's glow can follow it: [pos] while
 * pressed, null once released.
 */
internal class AuroraTouch {
    val pos = mutableStateOf<Offset?>(null)
}

internal val LocalAuroraTouch = androidx.compose.runtime.staticCompositionLocalOf<AuroraTouch?> { null }

/**
 * How navigation nudges the aurora, beyond its own drift and the tilt sensor: a bounded lean
 * ([leanX] from the car pager's page offset, [leanY] from a page's scroll) and a [kick] raised by an
 * app action (a page settling, a refresh) that springs the whole backdrop once. Published by the
 * screens and read in the draw phase, so a page turn never recomposes the aurora.
 */
internal class AuroraReactions {
    /** -1..1 horizontal lean (a page turn). */
    val leanX = mutableFloatStateOf(0f)
    /** -1..1 vertical lean (scrolling down a page). */
    val leanY = mutableFloatStateOf(0f)
    /** Bumped on an app action; each change springs one pulse. */
    val kick = mutableIntStateOf(0)
}

internal val LocalAuroraReactions = androidx.compose.runtime.staticCompositionLocalOf<AuroraReactions?> { null }

/**
 * How far the navigation lean must move before it is written to [AuroraReactions]. The lean is read
 * in the aurora's DRAW phase, so every write repaints the whole full-screen backdrop; writing it per
 * frame meant a backdrop repaint on every swipe/scroll frame -- the swipe's own lag and heat. The
 * lean is a subtle background shift, so a step this size (about half a percent of the screen once
 * scaled) is invisible, while it turns ~120 repaints per swipe into ~20.
 */
internal const val AuroraLeanStep = 0.05f

/**
 * When the user last touched anything, in uptime millis. The aurora's drift freezes once the app has
 * been left alone for [AmbientIdleMs] (see [AuroraBackground]): a phone sitting on the garage was
 * otherwise redrawing a full-screen animated backdrop -- and re-blurring it under every glass card --
 * forever, which is exactly the idle heat. Any touch resumes the drift from the same phase, so
 * nothing jumps.
 */
internal class AmbientActivity {
    // Seeded to "now", so a freshly composed aurora (cold start, login) drifts immediately rather
    // than starting life already idle.
    var lastTouchMs by androidx.compose.runtime.mutableLongStateOf(android.os.SystemClock.uptimeMillis())
}

internal val LocalAmbientActivity = androidx.compose.runtime.staticCompositionLocalOf<AmbientActivity?> { null }

/** How long with no touch before the ambient drift stops, sparing the idle phone the redraw. */
private const val AmbientIdleMs = 8_000L

/**
 * Runs for every pointer event anywhere in the app WITHOUT consuming it (the Initial pass sees all
 * touches first; nothing is consumed), so [AmbientActivity] knows the app is being used. Writes are
 * rate-limited: a write per move event would be pure churn for a "was there a touch recently" flag.
 */
internal fun Modifier.observeUserActivity(activity: AmbientActivity?): Modifier =
    if (activity == null) this else this.pointerInput(activity) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                val now = android.os.SystemClock.uptimeMillis()
                if (now - activity.lastTouchMs > 250L) activity.lastTouchMs = now
            }
        }
    }
