package com.bloo.wear.ui

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.compose.animation.core.animateFloatAsState
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
        Text(
            percent?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
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
    Box(modifier.size(116.dp).clip(RoundedCornerShape(18.dp))) {
        AsyncImage(
            model = url,
            contentDescription = "Map",
            contentScale = ContentScale.Crop,
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

/** A labelled value + draggable slider + fine –/+ buttons. The slider consumes
 *  horizontal drags, so dragging it never switches cars in the pager. */
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
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valueLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniButton("–") { onValue(value - step) }
            SliderTrack(value, min, max, step, fill, Modifier.weight(1f), onValue)
            MiniButton("+") { onValue(value + step) }
        }
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

/** A small circular –/+ control, matching the phone's round buttons. */
@Composable
private fun MiniButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(),
        label = { Text(text) },
    )
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
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Match the phone's MorphButton: pill (50%) ↔ rounded square (28%) with a
    // soft expressive spring.
    val pct by animateFloatAsState(
        targetValue = if (active || pressed) 28f else 50f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow),
        label = "morphCorner",
    )
    Button(
        onClick = onClick,
        enabled = !pending,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(percent = pct.roundToInt()),
        colors = if (active) {
            ButtonDefaults.buttonColors(containerColor = activeColor, contentColor = MaterialTheme.colorScheme.onPrimary)
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        label = { Text(if (pending) "Sending…" else label, maxLines = 1) },
        icon = { Icon(icon, contentDescription = null) },
    )
}

@Composable
private fun SliderTrack(value: Int, min: Int, max: Int, step: Int, fillColor: Color, modifier: Modifier, onValue: (Int) -> Unit) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val range = (max - min).coerceAtLeast(1)
    var width by remember { mutableIntStateOf(1) }
    Canvas(
        modifier
            .height(26.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(min, max, step) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val raw = min + frac * range
                    onValue(((raw / step).roundToInt() * step).coerceIn(min, max))
                }
            }
    ) {
        val cy = size.height / 2f
        val r = size.height / 2f
        val thickness = size.height * 0.5f
        drawLine(trackColor, Offset(r, cy), Offset(size.width - r, cy), strokeWidth = thickness, cap = StrokeCap.Round)
        val frac = (value - min).toFloat() / range
        val x = r + (size.width - 2 * r) * frac
        drawLine(fillColor, Offset(r, cy), Offset(x, cy), strokeWidth = thickness, cap = StrokeCap.Round)
        drawCircle(fillColor, radius = size.height * 0.45f, center = Offset(x, cy))
    }
}
