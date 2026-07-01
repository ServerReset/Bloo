package com.bloo.wear.ui

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import androidx.wear.compose.material3.ProgressIndicatorDefaults
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

/** Charge/fuel percentage as a ring with the value centred. The ring colour
 *  reflects state: green while charging, red when critically low, else accent. */
@Composable
fun ChargeRing(
    percent: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    charging: Boolean = false,
) {
    val ringDesc = percent?.let { "Charge $it percent" } ?: "Charge level unknown"
    val ringColor = when {
        charging -> WearColors.chargeGreen
        (percent ?: 100) < 15 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).semantics { contentDescription = ringDesc },
    ) {
        CircularProgressIndicator(
            progress = { (percent ?: 0).coerceIn(0, 100) / 100f },
            modifier = Modifier.size(size),
            colors = ProgressIndicatorDefaults.colors(indicatorColor = ringColor),
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
    // Tile coords + marker offset only depend on lat/lon — compute once per location.
    val tile = remember(lat, lon) {
        val z = 15
        val n = (1 shl z).toDouble()
        val latRad = Math.toRadians(lat)
        val xf = (lon + 180.0) / 360.0 * n
        val yf = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        val xt = xf.toInt()
        val yt = yf.toInt()
        Triple("https://tile.openstreetmap.org/$z/$xt/$yt.png", (xf - xt).toFloat(), (yf - yt).toFloat())
    }
    val url = tile.first
    val mx = tile.second
    val my = tile.third
    val marker = MaterialTheme.colorScheme.error
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHigh
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier
            .size(116.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(placeholder),
        contentAlignment = Alignment.Center,
    ) {
        // A pin shows even before the tile loads / when offline, so it's never blank.
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(28.dp),
        )
        AsyncImage(
            model = url,
            contentDescription = "Map of car location",
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

/** The app's pill→rounded-square morphing button, for Wear. Matches the phone's MorphButton.
 *  [secondaryLabel] adds a small caption line below [label] (e.g. a field name
 *  under its current value) — every button-shaped control in the wear app
 *  should go through this one component rather than a raw Wear Button/
 *  FilledTonalButton/OutlinedButton/SwitchButton, so contrast, press feedback,
 *  and the pill-morph motion stay consistent everywhere. */
@Composable
fun MorphButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    pending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme

    // 50% = true pill; 28% = rounded rectangle — phone's exact values with the same spring.
    val pct by animateFloatAsState(
        targetValue = if (active || pressed) 28f else 50f,
        animationSpec = spring(dampingRatio = com.bloo.uicommon.SoftDamping, stiffness = Spring.StiffnessMedium),
        label = "morphCorner",
    )
    // A quick, snappy press-punch independent of the (slower, shape-driven)
    // morph above — corner-radius alone was too subtle to register as "the
    // button reacted" against the dark, low-contrast card background.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "morphPressScale",
    )
    // Phone's buttonContainer() lerps from surfaceContainerHighest (the most
    // elevated tonal step) toward onSurface by 18-20%. Wear's ColorScheme has
    // no surfaceContainerHighest, so this based off surfaceContainerHigh (one
    // step darker) — lerping by the same 18% left buttons reading almost flat
    // against the card behind them. Lerp further to compensate for the lower
    // starting tone.
    val containerColor = lerp(scheme.surfaceContainerHigh, scheme.onSurface, 0.32f)
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
        modifier = modifier.fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .animateContentSize(
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            ),
        shape = RoundedCornerShape(percent = pct.roundToInt()),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = resolvedContent,
            disabledContainerColor = bg,
            disabledContentColor = resolvedContent.copy(alpha = 0.38f),
        ),
        border = if (active || pending) null else BorderStroke(1.dp, scheme.outline.copy(alpha = 0.6f)),
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
        secondaryLabel = secondaryLabel?.let { s ->
            { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        icon = {
            if (pending) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
    )
}

/** One option in a [MorphSegmented] control. */
data class WearSegmentOption(val key: String, val label: String)

/**
 * The watch equivalent of the phone's MorphSegmented: a full-width segmented
 * selector with a single sliding, bouncy highlight — same visual language as
 * MorphButton (pill-track container, spring-driven motion), just for a
 * one-of-N choice between equal alternatives instead of a single action.
 * Best suited to 2-4 short labels (e.g. brand pickers); a long or highly
 * variable option list should stay a vertical list instead.
 */
@Composable
fun MorphSegmented(
    options: List<WearSegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val selectedIndex = options.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val trackPad = 4.dp
    val gap = 4.dp
    val trackHeight = 40.dp
    val haptics = LocalHapticFeedback.current
    // Same fix as MorphButton: surfaceContainerHigh lerped by only 18% read
    // almost flat against the card behind it.
    val containerColor = lerp(scheme.surfaceContainerHigh, scheme.onSurface, 0.32f)
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(containerColor),
    ) {
        BoxWithConstraints(Modifier.padding(trackPad).height(trackHeight)) {
            val n = options.size
            val segWidth = (maxWidth - gap * (n - 1)) / n
            val density = LocalDensity.current
            val stepPx = with(density) { (segWidth + gap).toPx() }
            val maxXPx = with(density) { (segWidth * (n - 1) + gap * (n - 1)).toPx() }

            // Drag it and it bounces to wherever you let go — same interaction as
            // the phone's version, not just a tap target.
            var dragXPx by remember { mutableStateOf<Float?>(null) }
            val restingX = (segWidth + gap) * selectedIndex
            val indicatorX by animateDpAsState(
                targetValue = dragXPx?.let { with(density) { it.toDp() } } ?: restingX,
                animationSpec = if (dragXPx != null) snap()
                                else spring(dampingRatio = com.bloo.uicommon.SoftDamping, stiffness = Spring.StiffnessMediumLow),
                label = "wearSegIndicatorX",
            )

            fun indexFor(xPx: Float): Int = (xPx / stepPx).roundToInt().coerceIn(0, n - 1)

            Box(
                Modifier
                    .offset(x = indicatorX)
                    .width(segWidth)
                    .fillMaxHeight()
                    .background(scheme.primary, RoundedCornerShape(14.dp)),
            )
            Row(
                Modifier
                    .fillMaxSize()
                    .pointerInput(n, stepPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> dragXPx = offset.x.coerceIn(0f, maxXPx) },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                dragXPx = change.position.x.coerceIn(0f, maxXPx)
                            },
                            onDragEnd = {
                                val x = dragXPx ?: return@detectHorizontalDragGestures
                                val idx = indexFor(x)
                                dragXPx = null
                                if (options[idx].key != selectedKey) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(options[idx].key)
                                }
                            },
                            onDragCancel = { dragXPx = null },
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                options.forEachIndexed { i, opt ->
                    val selected = i == selectedIndex
                    val fg by animateColorAsState(
                        if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                        spring(stiffness = Spring.StiffnessMediumLow),
                        label = "wearSegFg",
                    )
                    Box(
                        modifier = Modifier
                            .width(segWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (opt.key != selectedKey) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelect(opt.key)
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            opt.label,
                            color = fg,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
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
