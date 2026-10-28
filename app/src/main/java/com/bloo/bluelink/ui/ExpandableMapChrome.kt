package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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

/**
 * The one-bar header over an expanded map sheet: the car (its photo and name) at the start, the drag handle centred, and its charge and
 * range at the end. [dragModifier] carries the drag-to-dismiss gesture, built by the caller because each call site closes over its own
 * drag state.
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
) {
    // Press-and-hold pop on the whole pill. requireUnconsumed = false observes down/up without
    // consuming, so dragModifier and the refresh click still receive the touch.
    var pillPressed by remember { mutableStateOf(false) }
    val pillScale by animateFloatAsState(
        targetValue = if (pillPressed) 1.04f else 1f,
        animationSpec = bounceSpring(stiffness = Spring.StiffnessLow),
        label = "mapBarPop",
    )
    GlassSurface(
        // A true pill, like the toasts and the search bar.
        shape = CircleShape,
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
        // No contentColor override: the map follows the app theme (dark filter only in dark mode),
        // so a forced white glyph vanishes on a light map. GlassSurface's onSurface default tracks
        // the theme and custom palettes.
    ) {
        // Three parts: the car at the start, the drag handle in the middle, and its charge and range at the end. Equal weights either
        // side keep the handle centred, whatever the name or the figures are.
        Row(
            Modifier.fillMaxWidth().then(dragModifier).heightIn(min = 60.dp).padding(horizontal = GapPage),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
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
            // Frosted drag handle, centred. Purely visual: the pop and drag belong to the whole bar.
            GlassSurface(
                shape = CircleShape,
                modifier = Modifier.size(width = 44.dp, height = 18.dp),
                hazeState = mapHazeState,
                shadow = false,
            ) {
                Box(
                    Modifier
                        .size(width = 24.dp, height = 4.dp)
                        .background(LocalContentColor.current.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
                )
            }
            // The charge and the range on one line at the end, so the car's figures read as a pair.
            Row(
                Modifier.weight(1f),
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
