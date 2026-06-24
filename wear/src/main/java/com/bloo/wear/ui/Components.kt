package com.bloo.wear.ui

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.progressSemantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import kotlin.math.roundToInt

/**
 * The Wear text-entry pattern: tapping launches the system input overlay
 * (keyboard / voice / handwriting) and the typed text comes back via RemoteInput.
 * Returns a lambda to trigger it.
 */
@Composable
fun rememberWearTextInput(label: String, onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)?.getCharSequence(KEY)?.toString()
        if (!text.isNullOrBlank()) onResult(text)
    }
    return {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(
            intent,
            listOf(RemoteInput.Builder(KEY).setLabel(label).build()),
        )
        launcher.launch(intent)
    }
}

private const val KEY = "bloo_input"

/** Charge/fuel percentage as a ring with the value centred. */
@Composable
fun ChargeRing(percent: Int?, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        CircularProgressIndicator(
            progress = { (percent ?: 0).coerceIn(0, 100) / 100f },
            modifier = Modifier.size(size),
        )
        AnimatedContent(
            targetState = percent?.let { "$it%" } ?: "—",
            transitionSpec = { (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it/3 }) togetherWith (fadeOut(tween(150)) + slideOutVertically(tween(150)) { it/3 }) },
            label = "pct",
        ) { v -> Text(v, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
    }
}

/** A small OSM map thumbnail centred on the car, with a marker. */
@Composable
fun MapThumbnail(lat: Double, lon: Double, modifier: Modifier = Modifier) {
    val z = 15
    val n = (1 shl z).toDouble()
    val latRad = Math.toRadians(lat)
    val xf = (lon + 180.0) / 360.0 * n
    val yf = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
    val xt = xf.toInt()
    val yt = yf.toInt()
    val url = "https://tile.openstreetmap.org/$z/$xt/$yt.png"
    val mx = (xf - xt).toFloat()
    val my = (yf - yt).toFloat()
    val marker = MaterialTheme.colorScheme.error
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(modifier.size(116.dp).clip(RoundedCornerShape(18.dp))) {
        AsyncImage(
            model = url,
            contentDescription = "Map",
            contentScale = ContentScale.Crop,
            imageLoader = com.bloo.wear.WearImage.loader(context),
            modifier = Modifier.matchParentSize(),
        )
        Canvas(Modifier.matchParentSize()) {
            drawCircle(marker, radius = 6.dp.toPx(), center = Offset(mx * size.width, my * size.height))
        }
    }
}

/** Relative "x min ago" for a wall-clock timestamp. */
fun relativeLabel(ms: Long?): String {
    if (ms == null || ms <= 0) return ""
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> "just now"
        d < 3_600_000 -> "${d / 60_000} min ago"
        d < 86_400_000 -> "${d / 3_600_000} hr ago"
        else -> "${d / 86_400_000} d ago"
    }
}

/** "1h 20m" / "45 min". */
fun fmtMinutes(min: Int): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "$min min"

/** A label → value row used in the details card. */
@Composable
fun StatusRow(label: String, value: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A labelled value + the phone's exact custom slider (ported). */
@Composable
fun SliderRow(
    label: String,
    valueLabel: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    accent: Color? = null,
    onValue: (Int) -> Unit,
) {
    val fill = accent ?: MaterialTheme.colorScheme.primary
    val steps = ((max - min) / step - 1).coerceAtLeast(0)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        AnimatedSlider(
            value = value.toFloat(),
            onValueChange = { onValue(it.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = steps,
            accent = fill,
        )
    }
}

/** Map a 62–82°F setpoint to the phone's blue→green→warm slider colour. */
@Composable
fun tempColor(tempF: Int): Color {
    val t = ((tempF - 62) / 20f).coerceIn(0f, 1f)
    val cool = Color(0xFF2E78FF)
    val mid = Color(0xFF2EBD59)
    val warm = Color(0xFFE5484D)
    return if (t < 0.5f) lerp(cool, mid, t * 2f) else lerp(mid, warm, (t - 0.5f) * 2f)
}

/**
 * The phone app's fully custom slider (ported verbatim from Screens.kt's
 * AnimatedSlider): hand-drawn track + tall thumb + tick dots, live finger
 * tracking with a small overshoot, and a soft spring settle onto the nearest
 * step. Claims only horizontal drags, so vertical scrolling/paging still works.
 */
@Composable
fun AnimatedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var widthPx by remember { mutableFloatStateOf(0f) }

    val anim = remember { Animatable(value) }
    var dragging by remember { mutableStateOf(false) }
    var prevStep by remember { mutableFloatStateOf(snapToStep(value, valueRange, steps)) }

    LaunchedEffect(value) {
        if (!dragging && !anim.isRunning && anim.value != value) anim.snapTo(value)
    }

    val trackThickness = 14.dp
    val thumbW = 6.dp
    val thumbH = 44.dp
    val gap = 6.dp
    val dotR = 2.5.dp
    val edgePad = 14.dp
    val edgePadPx = with(density) { edgePad.toPx() }

    val inactiveColor = scheme.surfaceContainerHigh
    val dotOnActive = scheme.onPrimary.copy(alpha = 0.7f)
    val dotOnInactive = scheme.onSurfaceVariant.copy(alpha = 0.5f)

    fun rawForX(x: Float): Float {
        val travel = (widthPx - 2 * edgePadPx).coerceAtLeast(1f)
        val frac = (x - edgePadPx) / travel
        return valueRange.start + frac * (valueRange.endInclusive - valueRange.start)
    }
    fun trackTo(x: Float) {
        val raw = rawForX(x)
        val span = (valueRange.endInclusive - valueRange.start)
        val overshoot = span * 0.045f
        val visual = raw.coerceIn(valueRange.start - overshoot, valueRange.endInclusive + overshoot)
        scope.launch { anim.snapTo(visual) }
        val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
        val s = snapToStep(clamped, valueRange, steps)
        if (steps > 0 && s != prevStep) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            prevStep = s
        }
        onValueChange(s)
    }
    fun settleTo(target: Float) {
        prevStep = target
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onValueChange(target)
        scope.launch {
            anim.animateTo(target, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(thumbH)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(valueRange, steps) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var claimed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!claimed) {
                                change.consume()
                                settleTo(snapToStep(rawForX(down.position.x), valueRange, steps))
                            }
                            break
                        }
                        if (!claimed) {
                            val dx = kotlin.math.abs(change.position.x - down.position.x)
                            val dy = kotlin.math.abs(change.position.y - down.position.y)
                            when {
                                dx > slop && dx >= dy -> {
                                    claimed = true
                                    dragging = true
                                    change.consume()
                                    trackTo(change.position.x)
                                }
                                dy > slop -> break
                            }
                        } else if (change.positionChanged()) {
                            trackTo(change.position.x)
                            change.consume()
                        }
                    }
                    if (claimed) {
                        dragging = false
                        settleTo(snapToStep(anim.value, valueRange, steps))
                    }
                }
            }
            .progressSemantics(anim.value, valueRange, steps),
    ) {
        Canvas(Modifier.fillMaxWidth().height(thumbH)) {
            val span2 = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
            val frac2 = (anim.value - valueRange.start) / span2
            val halfThumb = thumbW.toPx() / 2f
            val gapPx = gap.toPx()
            val padPx = edgePad.toPx()
            val travel = (size.width - 2 * padPx).coerceAtLeast(0f)
            val thumbX = padPx + travel * frac2
            val cy = size.height / 2f
            val th = trackThickness.toPx()
            val top = cy - th / 2f
            val radius = CornerRadius(th / 2f)
            val cut = halfThumb + gapPx

            val inStart = (thumbX + cut).coerceAtMost(size.width)
            if (inStart < size.width) {
                drawRoundRect(inactiveColor, topLeft = Offset(inStart, top), size = Size(size.width - inStart, th), cornerRadius = radius)
            }
            val acEnd = (thumbX - cut).coerceAtLeast(0f)
            if (acEnd > 0f) {
                drawRoundRect(accent, topLeft = Offset(0f, top), size = Size(acEnd, th), cornerRadius = radius)
            }
            if (steps > 0) {
                val n = steps + 2
                val rPx = dotR.toPx()
                for (i in 0 until n) {
                    val tf = i.toFloat() / (n - 1)
                    val x = padPx + travel * tf
                    if (kotlin.math.abs(x - thumbX) < cut) continue
                    drawCircle(if (x <= thumbX) dotOnActive else dotOnInactive, rPx, Offset(x, cy))
                }
            }
            val twPx = thumbW.toPx()
            drawRoundRect(accent, topLeft = Offset(thumbX - twPx / 2f, 0f), size = Size(twPx, size.height), cornerRadius = CornerRadius(twPx / 2f))
        }
    }
}

private fun snapToStep(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    if (steps <= 0) return v.coerceIn(range.start, range.endInclusive)
    val inc = (range.endInclusive - range.start) / (steps + 1)
    val snapped = range.start + Math.round((v - range.start) / inc) * inc
    return snapped.coerceIn(range.start, range.endInclusive)
}

/** The app's pill→rounded-square morphing button, for Wear. */
@Composable
fun MorphButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    pending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    // Match the phone's MorphButton: pill (50%) ↔ rounded square (28%) with a
    // soft expressive spring.
    val corner by animateDpAsState(
        targetValue = if (active || pending) 12.dp else 50.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "morphCorner",
    )
    Button(
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        enabled = !pending,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(corner),
        colors = if (active) {
            ButtonDefaults.buttonColors(containerColor = activeColor, contentColor = MaterialTheme.colorScheme.onPrimary)
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        border = if (!active && !pending) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) else null,
        label = { Text(if (pending) "Sending…" else label, maxLines = 1) },
        icon = { Icon(icon, contentDescription = null) },
    )
}

// ---- Weather helpers (mirror the phone's WeatherCode mapping) -------------

fun weatherLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1, 2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77, 85, 86 -> "Snow"
    80, 81, 82 -> "Showers"
    95, 96, 99 -> "Thunderstorm"
    else -> "—"
}

fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when (code) {
    0 -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.Nightlight
    1, 2 -> Icons.Filled.WbCloudy
    3, 45, 48 -> Icons.Filled.Cloud
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Filled.Umbrella
    71, 73, 75, 77, 85, 86 -> Icons.Filled.AcUnit
    95, 96, 99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.Cloud
}

fun weatherTemp(tempC: Double, fahrenheit: Boolean): String =
    if (fahrenheit) "${(tempC * 9 / 5 + 32).toInt()}°F" else "${tempC.toInt()}°C"

@Composable
fun AnimatedValue(
    value: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 3 }) togetherWith
            (fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 })
        },
        label = "animVal",
    ) { v -> Text(v, style = style, color = color, fontWeight = fontWeight, maxLines = maxLines) }
}
