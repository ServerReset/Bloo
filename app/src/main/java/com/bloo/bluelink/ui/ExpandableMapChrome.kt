package com.bloo.bluelink.ui

import com.bloo.uicommon.RevealText
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.layout
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.IntSize
import com.bloo.uicommon.lowPowerCalm
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bloo.bluelink.data.GeoLocation
import com.bloo.uicommon.bounceSpring
import com.bloo.uicommon.ExpressiveButtonGroup
import com.bloo.uicommon.PrimaryTextStyle
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapPage
import com.bloo.uicommon.MorphActionButton
import com.bloo.uicommon.Haptics
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import kotlinx.coroutines.CoroutineScope

/**
 * The "Me" button's action: centre the map on the phone. With no fix yet it asks for location (see
 * [DeviceLocationRequest]) and centres as soon as the first fix lands.
 */
@Composable
internal fun rememberShowMyLocation(
    location: GeoLocation,
    deviceLocation: GeoLocation?,
    mapState: CarMapState,
    request: DeviceLocationRequest?,
): () -> Unit {
    var pending by remember { mutableStateOf(false) }
    LaunchedEffect(pending, deviceLocation) {
        val d = deviceLocation
        if (pending && d != null) {
            mapState.showDevice(location.latitude, location.longitude, d.latitude, d.longitude)
            pending = false
        }
    }
    return {
        val d = deviceLocation
        if (d != null) {
            mapState.showDevice(location.latitude, location.longitude, d.latitude, d.longitude)
        } else {
            pending = true
            request?.ask?.invoke()
        }
    }
}

/**
 * The buttons under a map: one action button per [MapFeature], in the same row the car info pebble
 * uses ([ExpressiveButtonGroup] of [MorphActionButton]s) -- the app's standard labelled-button row.
 */
@Composable
internal fun MapFeatureRow(features: List<MapFeature>, modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = GapGroup, vertical = GapRow)) {
        // Never wraps onto a second line: when the labels do not fit, the buttons drop to their icons.
        ExpressiveButtonGroup(modifier = Modifier.fillMaxWidth(), spacing = GapRow, wrap = false) {
            features.forEach { feature ->
                MorphActionButton(
                    label = feature.label, icon = feature.icon, onClick = feature.onClick, enabled = feature.enabled,
                )
            }
        }
    }
}

/** The top bar's corner when its details are open: a rounded panel, so the details' text is not cut by a pill's curve. */
private val DetailsPanelCorner = 28.dp

/** What the top bar shows: only its handle (no car selected), the selected car's single line, or that line with its details open. */
internal enum class MapBarMode { Handle, Line, Details }

/**
 * The bar's corner, between the pill it is as a handle or a line (radius half its height) and the rounded panel its details open into
 * ([DetailsPanelCorner]). [detailsT] is 0 for the pill and 1 for the panel; the value is animated by the caller.
 */
private class CornerBlendShape(private val detailsT: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val pill = size.height / 2f
        val panel = with(density) { DetailsPanelCorner.toPx() }
        val radius = (pill + (panel - pill) * detailsT).coerceIn(0f, size.minDimension / 2f)
        return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(radius)))
    }
}

/** The handle-only bar's width: the handle with a little glass around it. The bar grows from this to the single line when a car is selected. */
internal val MapBarHandleWidth = 64.dp

/**
 * The bar over an expanded map sheet, in three states (see [MapBarMode]):
 *  - no car selected: only the drag handle, a small floating pill;
 *  - a car selected: one line, the car's photo and name at the start, its charge and range at the end;
 *  - that line tapped: the car's other details open below it.
 *
 * The whole bar moves on one timeline. The grow from handle to line ([lineProgress], read in layout), the corners and the details all
 * run on the same spring, and every size, offset and fade is computed from that progress in layout or draw, so the bar is never
 * re-composed while it moves. [dragModifier] carries the drag-to-dismiss and pull-to-refresh gesture, built by the caller because each
 * call site closes over its own drag state.
 */
@Composable
internal fun MapTopBar(
    vehicleName: String,
    mapHazeState: HazeState,
    modifier: Modifier = Modifier,
    dragModifier: Modifier,
    /** The car's photo at the bar's start, or null for the car glyph. */
    photo: String? = null,
    /** The car's charge, and its range, at the bar's end. Null leaves that line out. */
    chargePercent: String? = null,
    chargeRange: String? = null,
    /** The rest of the car's details, shown when the single line is tapped open: label to value. */
    details: List<Pair<String, String>> = emptyList(),
    /** False once the car has scrolled off screen: the details fold back to the line. */
    carOnScreen: Boolean = true,
    /** What the bar shows: its handle, the selected car's line, or that line with the details open. */
    mode: MapBarMode = MapBarMode.Handle,
    /** Asks for the details to open or fold; the caller holds the state, so a dismiss can fold them first. */
    onDetailsOpenChange: (Boolean) -> Unit = {},
    /** How far the bar has grown from its handle to its line, 0 to 1. */
    lineProgress: () -> Float = { 1f },
) {
    val selected = mode != MapBarMode.Handle
    val detailsOpen = mode == MapBarMode.Details
    LaunchedEffect(carOnScreen) { if (!carOnScreen) onDetailsOpenChange(false) }
    LaunchedEffect(selected) { if (!selected) onDetailsOpenChange(false) }
    // The same spring as the grow from the handle (see ExpandableMap), so the details, the corners and the grow land together.
    val spring = lowPowerCalm<Float>(Spring.StiffnessMediumLow)
    val detailsT by animateFloatAsState(targetValue = if (detailsOpen) 1f else 0f, animationSpec = spring, label = "mapBarDetails")
    // Press-and-hold pop on the whole pill. requireUnconsumed = false observes down/up without consuming, so the drag and the tap still
    // receive the touch.
    var pillPressed by remember { mutableStateOf(false) }
    val pillScale by animateFloatAsState(
        targetValue = if (pillPressed) 1.04f else 1f,
        animationSpec = bounceSpring(stiffness = Spring.StiffnessLow),
        label = "mapBarPop",
    )
    val density = LocalDensity.current
    val handleHeightPx = with(density) { 22.dp.toPx() }
    val lineHeightPx = with(density) { 60.dp.toPx() }
    val gutterPx = with(density) { GapPage.toPx() }
    val slidePx = with(density) { 24.dp.toPx() }

    GlassSurface(
        // The corners ease between the pill and the rounded details panel on the same spring.
        shape = CornerBlendShape(detailsT),
        // The bar is liquid glass: the refraction and specular sheen of the app's floating glass, drawn over the map behind it.
        liquid = true,
        modifier = modifier.fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pillPressed = true
                    waitForUpOrCancellation()
                    pillPressed = false
                }
            }
            .graphicsLayer { scaleX = pillScale; scaleY = pillScale },
        hazeState = mapHazeState,
        // No contentColor override: the map follows the app theme (dark filter only in dark mode), so a forced white glyph vanishes on a
        // light map. GlassSurface's onSurface default tracks the theme and custom palettes.
    ) {
        Column {
            // The header: the car at the start, the drag handle in the middle, its charge and range at the end. Its height and gutters
            // grow with the line, in layout, so the header never jumps between the handle and the line.
            Row(
                Modifier
                    .then(dragModifier)
                    .then(if (selected) Modifier.clickable { onDetailsOpenChange(!detailsOpen) } else Modifier)
                    .layout { measurable, constraints ->
                        val p = lineProgress().coerceIn(0f, 1f)
                        val minHeight = (handleHeightPx + (lineHeightPx - handleHeightPx) * p).roundToInt()
                        val gutter = (gutterPx * p).roundToInt()
                        val inner = measurable.measure(
                            constraints.copy(
                                minWidth = (constraints.minWidth - 2 * gutter).coerceAtLeast(0),
                                maxWidth = (constraints.maxWidth - 2 * gutter).coerceAtLeast(0),
                                minHeight = max(constraints.minHeight, minHeight),
                            ),
                        )
                        val width = inner.width + 2 * gutter
                        layout(width.coerceAtMost(constraints.maxWidth), inner.height) { inner.place(gutter, 0) }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The car's side: it fades and slides in from the start as the bar grows, and is silent while nothing is selected.
                Box(
                    Modifier
                        .weight(1f)
                        .clipToBounds()
                        .graphicsLayer {
                            val p = lineProgress()
                            alpha = p
                            translationX = -(1f - p) * slidePx
                        }
                        .then(if (selected) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GapGroup),
                    ) {
                        if (photo != null) {
                            AsyncImage(
                                model = rememberPhotoModel(photo),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 48.dp, height = 36.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        } else {
                            Icon(AppIcons.DirectionsCar, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                        RollingNumber(
                            vehicleName,
                            style = PrimaryTextStyle(),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                // The drag handle, centred: a short bar on the bar's own glass, with no glass of its own inside. Purely visual.
                Box(Modifier.size(width = 36.dp, height = 18.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(width = 24.dp, height = 4.dp)
                            .background(LocalContentColor.current.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
                    )
                }
                // The charge and the range at the end: they fade and slide in from the end, as the car's side does from the start.
                Box(
                    Modifier
                        .weight(1f)
                        .clipToBounds()
                        .graphicsLayer {
                            val p = lineProgress()
                            alpha = p
                            translationX = (1f - p) * slidePx
                        }
                        .then(if (selected) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    // The charge and the range on one line at the end, so the car's figures read as a pair.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (chargePercent != null) {
                            RollingNumber(chargePercent, style = PrimaryTextStyle(), fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        if (chargePercent != null && chargeRange != null) Spacer(Modifier.width(GapGroup))
                        if (chargeRange != null) {
                            RollingNumber(
                                chargeRange,
                                style = PrimaryTextStyle(),
                                fontWeight = FontWeight.Medium,
                                color = mutedContentColor(),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            // The details reveal from the header down, in layout: the panel is measured in full and shown as far as the spring has opened it,
            // so the bar's height follows the same motion as its corners, with no size animation of its own.
            Box(
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val full = measurable.measure(constraints.copy(minHeight = 0))
                        val shown = (full.height * detailsT).roundToInt()
                        layout(full.width, shown) { full.place(0, 0) }
                    },
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = GapPage).padding(bottom = GapGroup),
                    verticalArrangement = Arrangement.spacedBy(GapRow),
                ) {
                    details.forEach { (label, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SecondaryText(label, modifier = Modifier.weight(1f))
                            RevealText(value, style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** How far the whole map sheet has been pulled up, in px (see [pullToRefreshOrDismiss]). */
internal class MapPullState {
    val up = Animatable(0f)
}

/**
 * The map bar's drag: down dismisses once pulled 96dp or more (and springs back before that); UP lifts the whole sheet toward the
 * top of the screen, ticking harder the closer it gets ([gapPx] is the room above it). Let go near
 * the top and [refresh] fires; let go earlier and it just settles back.
 */
internal fun Modifier.pullToRefreshOrDismiss(
    dragPx: Animatable<Float, AnimationVector1D>,
    pull: MapPullState,
    gapPx: Float,
    scope: CoroutineScope,
    density: Density,
    spring: AnimationSpec<Float>,
    haptics: Haptics?,
    close: () -> Unit,
    refresh: () -> Unit,
): Modifier = this.pointerInput(gapPx) {
    val overPx = with(density) { 28.dp.toPx() }
    val notchPx = (gapPx / 14f).coerceAtLeast(1f)
    var raw = 0f
    var lastNotch = 0
    detectVerticalDragGestures(
        onVerticalDrag = { change, amount ->
            change.consume()
            if (raw > 0f || (amount < 0f && dragPx.value <= 0f)) {
                raw = (raw - amount).coerceAtLeast(0f)
                // 1:1 up to the top of the screen, then a short rubber band past it.
                val over = (raw - gapPx).coerceAtLeast(0f)
                val travel = minOf(raw, gapPx) + overPx * over / (over + overPx)
                scope.launch { pull.up.snapTo(travel) }
                val notch = (raw / notchPx).toInt()
                if (notch > lastNotch) haptics?.ramp(raw / gapPx)
                lastNotch = notch
            } else {
                scope.launch { dragPx.snapTo((dragPx.value + amount).coerceAtLeast(0f)) }
            }
        },
        onDragEnd = {
            if (raw > 0f) {
                if (raw >= gapPx * 0.8f) {
                    haptics?.diceRoll()
                    refresh()
                }
                raw = 0f
                lastNotch = 0
                scope.launch { pull.up.animateTo(0f, bounceSpring(stiffness = Spring.StiffnessLow)) }
            } else {
                val thresholdPx = with(density) { 96.dp.toPx() }
                if (dragPx.value > thresholdPx) close() else scope.launch { dragPx.animateTo(0f, spring) }
            }
        },
        onDragCancel = {
            raw = 0f
            lastNotch = 0
            scope.launch { pull.up.animateTo(0f, spring) }
            scope.launch { dragPx.animateTo(0f, spring) }
        },
    )
}

/** The car's charge and its range for the map bar's end, each as its own line; null where it has none yet. */
// chargeReadoutOf is @Composable, so this is too.
@Composable
internal fun mapChargeStats(state: UiState, v: com.bloo.bluelink.data.Vehicle, metric: Boolean): Pair<String?, String?> {
    val r = chargeReadoutOf(state.statusFor(v), state.hasBattery(v), state.hasFuel(v), state.drivingLabel(v), metric)
    return r.pctText.takeIf { it.isNotBlank() } to r.rangeText?.takeIf { it.isNotBlank() }
}
