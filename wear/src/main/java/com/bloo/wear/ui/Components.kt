package com.bloo.wear.ui

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

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
    val ringDesc = percent?.let { "Charge $it percent" } ?: "Charge level unknown"
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).semantics { contentDescription = ringDesc },
    ) {
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
fun relativeLabel(ms: Long?): String = com.bloo.bluelink.data.relativeLabel(ms)

/** "1h 20m" / "45 min". */
fun fmtMinutes(min: Int): String = com.bloo.bluelink.data.fmtMinutes(min)

/** A label → value row used in the details card. Both sides truncate so a long
 *  value (efficiency, address, kWh) can never collide with the label on a round face. */
@Composable
fun StatusRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
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
 * The app's fully custom slider — now a thin wrapper over the single shared
 * implementation in :uicommon so the hand-drawn track/thumb/tick logic lives in
 * exactly one place.
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
    com.bloo.uicommon.AnimatedSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        accent = accent,
        inactiveColor = scheme.surfaceContainerHigh,
        dotOnActive = scheme.onPrimary.copy(alpha = 0.7f),
        dotOnInactive = scheme.onSurfaceVariant.copy(alpha = 0.5f),
        reduceMotion = LocalReduceMotion.current,
        onStepTick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        onSettle = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
    )
}

@Composable
fun WiggleText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
) {
    val resolvedColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    val mergedStyle = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style
    com.bloo.uicommon.WiggleText(
        text = text,
        style = mergedStyle.copy(color = resolvedColor),
        maxLines = maxLines,
        reduceMotion = LocalReduceMotion.current,
    )
}

/** The app's pill→rounded-square morphing button, for Wear. Matches the phone's MorphButton. */
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
    val scheme = MaterialTheme.colorScheme

    // 50% = true pill; 28% = rounded rectangle — phone's exact values with the same spring.
    val pct by animateFloatAsState(
        targetValue = if (active || pressed) 28f else 50f,
        animationSpec = spring(dampingRatio = com.bloo.uicommon.SoftDamping, stiffness = Spring.StiffnessLow),
        label = "morphCorner",
    )
    // Inactive background mirrors buttonContainer() from Theme.kt.
    // Wear ColorScheme has surfaceContainerHigh but not surfaceContainerHighest.
    val containerColor = lerp(scheme.surfaceContainerHigh, scheme.onSurface, 0.18f)
    val bg by animateColorAsState(
        targetValue = if (active) activeColor else containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "morphBg",
    )
    val resolvedContent = if (active) scheme.onPrimary else scheme.onSurface

    Button(
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        enabled = !pending,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().animateContentSize(
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        ),
        shape = RoundedCornerShape(percent = pct.roundToInt()),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = resolvedContent,
            disabledContainerColor = bg,
            disabledContentColor = resolvedContent.copy(alpha = 0.38f),
        ),
        border = if (active || pending) null else BorderStroke(1.dp, scheme.outline.copy(alpha = 0.4f)),
        label = { Text(label, maxLines = 1, fontWeight = FontWeight.SemiBold) },
        icon = {
            if (pending) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
    )
}

// ---- Weather helpers (mirror the phone's WeatherCode mapping) -------------

fun weatherLabel(code: Int): String = com.bloo.bluelink.data.weatherLabel(code)

fun weatherIcon(code: Int, isDay: Boolean): ImageVector =
    com.bloo.uicommon.weatherIcon(code, isDay)

fun weatherTemp(tempC: Double, fahrenheit: Boolean): String =
    com.bloo.bluelink.data.weatherTemp(tempC, fahrenheit)

@Composable
fun AnimatedValue(
    value: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
) {
    val resolvedColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    val mergedStyle = style.copy(
        color = resolvedColor,
        fontWeight = fontWeight ?: style.fontWeight,
    )
    com.bloo.uicommon.AnimatedValue(
        value = value,
        style = mergedStyle,
        maxLines = maxLines,
        reduceMotion = LocalReduceMotion.current,
    )
}
