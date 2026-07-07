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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
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
import kotlin.math.PI
import kotlin.math.abs
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

/** A small OSM map thumbnail centred on the car, with a marker. Shows a
 *  loading indicator while the tile downloads and an error state on failure
 *  with tap-to-retry. */
@Composable
fun MapThumbnail(lat: Double, lon: Double, modifier: Modifier = Modifier) {
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
    var retryKey by remember(url) { mutableStateOf(0) }
    val loadUrl = if (retryKey > 0) "$url?retry=$retryKey" else url
    val asyncPainter = coil.compose.rememberAsyncImagePainter(
        model = loadUrl,
        imageLoader = com.bloo.wear.WearImage.loader(context),
    )
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    val paintState = asyncPainter.state
    val isError = paintState is coil.compose.AsyncImagePainter.State.Error
    val isLoading = paintState is coil.compose.AsyncImagePainter.State.Loading
    Box(
        modifier
            .size(116.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(placeholder)
            .clickable(enabled = isError) { retryKey++ },
        contentAlignment = Alignment.Center,
    ) {
        if (isError) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Tap to retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp),
            )
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
            androidx.compose.foundation.Image(
                painter = asyncPainter,
                contentDescription = "Map of car location",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Canvas(Modifier.matchParentSize()) {
                drawCircle(marker, radius = 6.dp.toPx(), center = Offset(mx * size.width, my * size.height))
            }
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
        onStepTick = { haptics.tick() },
        onSettle = { haptics.click() },
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
    // Match the phone's buttonContainer(): push the highest surface tone a small
    // step toward onSurface so the button reads clearly against its card background
    // without needing a border (the phone uses surfaceContainerHighest + 0.18f for
    // dark themes; Wear's scheme has no surfaceContainerHighest, so surfaceContainerHigh
    // fills that role — it's already one step darker, so the lerp stays the same).
    val containerColor = lerp(scheme.surfaceContainerHigh, scheme.onSurface, 0.18f)
    val bg by animateColorAsState(
        targetValue = if (active) activeColor else containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "morphBg",
    )
    val resolvedContent = if (active) scheme.onPrimary else scheme.onSurface

    Button(
        onClick = { haptics.click(); onClick() },
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
        border = if (active || pending) null else BorderStroke(1.5.dp, scheme.outline.copy(alpha = 0.85f)),
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
    val trackHeight = 48.dp
    val haptics = LocalHapticFeedback.current
    // pointerInput below is keyed on (n, stepPx) only, so its gesture-handling
    // coroutine is launched once and keeps running (awaitEachGesture loops
    // internally) across many recompositions without restarting. A plain
    // closure over selectedKey/onSelect/haptics would freeze at whatever those
    // were on that first launch, so every subsequent tap/drag would compare
    // against the ORIGINAL selection forever — exactly "stops working after a
    // couple of taps, can't reselect the original option." rememberUpdatedState
    // keeps these reads live without relaunching (and thereby interrupting) an
    // in-progress gesture.
    val currentSelectedKey by rememberUpdatedState(selectedKey)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentHaptics by rememberUpdatedState(haptics)
    // Match the phone's buttonContainer() formula — same rationale as MorphButton.
    val containerColor = lerp(scheme.surfaceContainerHigh, scheme.onSurface, 0.18f)
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
                // MediumBouncy (0.4) at StiffnessMedium overshot noticeably on every
                // selection change. LowBouncy keeps the same quick settle speed with
                // just a light touch of overshoot instead, matching the phone version.
                animationSpec = if (dragXPx != null) snap()
                                else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                label = "wearSegIndicatorX",
            )

            val segWidthPx = with(density) { segWidth.toPx() }
            // Raw touch X is where the finger is; the indicator's rendered position
            // is its LEFT edge. Using raw touch X as that left-edge offset directly
            // (the old behavior) made the box jump so its edge, not its center, sat
            // under the finger — up to half a segment off, which is exactly "tracks
            // your finger but not really." Centering the indicator on the touch
            // point instead.
            fun offsetFor(touchXPx: Float): Float = (touchXPx - segWidthPx / 2f).coerceIn(0f, maxXPx)

            // indexFor expects an X already in "indicator left-edge" terms (already
            // run through offsetFor), so a segment's own center lands on an exact
            // multiple of stepPx. Applying /stepPx rounding to a RAW touch position
            // instead put the rounding boundary at each segment's trailing edge
            // rather than its center — a tap in roughly the back third of a segment
            // rounded up into the next one (e.g. tapping option 3 landing on 4).
            fun indexFor(offsetXPx: Float): Int = (offsetXPx / stepPx).roundToInt().coerceIn(0, n - 1)

            // Which segment reads as "selected" (bold, primary-tinted text) while
            // dragging — was always selectedIndex (the prop, which only actually
            // changes once onSelect fires on release), so mid-drag the indicator
            // pill visibly slid under the finger while the label highlight stayed
            // frozen on wherever it started. Same fix as the phone version.
            val visualIndex = dragXPx?.let { indexFor(it) } ?: selectedIndex

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
                    // Same touch-slop race the phone version uses (mirroring the shared
                    // AnimatedSlider's own gesture handling): don't consume anything
                    // until the gesture is confirmed. detectHorizontalDragGestures used
                    // to negotiate slop with ancestors (SwipeDismissableNavHost, the
                    // bezel scroll) and could lose that race in one direction only; an
                    // "always consume from frame one" fix broke scrolling the settings
                    // list past this control instead. This only claims the gesture once
                    // movement is confirmed horizontal (dx > slop && dx >= dy); a release
                    // before that is a tap (selects immediately at that position), and
                    // confirmed vertical movement (dy > slop) cedes the whole gesture to
                    // the ancestor scroll.
                    .pointerInput(n, stepPx) {
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
                                        val idx = indexFor(offsetFor(down.position.x))
                                        if (options[idx].key != currentSelectedKey) {
                                            currentHaptics.tick()
                                            currentOnSelect(options[idx].key)
                                        }
                                    }
                                    break
                                }
                                if (!claimed) {
                                    val dx = abs(change.position.x - down.position.x)
                                    val dy = abs(change.position.y - down.position.y)
                                    when {
                                        dx > slop && dx >= dy -> {
                                            claimed = true
                                            change.consume()
                                            dragXPx = offsetFor(change.position.x)
                                        }
                                        dy > slop -> break
                                    }
                                } else if (change.positionChanged()) {
                                    change.consume()
                                    dragXPx = offsetFor(change.position.x)
                                }
                            }
                            if (claimed) {
                                val x = dragXPx ?: offsetFor(down.position.x)
                                val idx = indexFor(x)
                                dragXPx = null
                                if (options[idx].key != currentSelectedKey) {
                                    currentHaptics.tick()
                                    currentOnSelect(options[idx].key)
                                }
                            }
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                options.forEachIndexed { i, opt ->
                    val selected = i == visualIndex
                    val fg by animateColorAsState(
                        if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                        spring(stiffness = Spring.StiffnessMediumLow),
                        label = "wearSegFg",
                    )
                    Box(
                        modifier = Modifier
                            .width(segWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp)),
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
