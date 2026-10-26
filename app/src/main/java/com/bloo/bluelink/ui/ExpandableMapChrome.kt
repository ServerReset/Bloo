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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.rememberSpinAngle
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
 * uses ([ExpressiveButtonRow] of [MorphActionButton]s) -- the app's standard labelled-button row.
 */
@Composable
internal fun MapFeatureRow(
    features: List<MapFeature>,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(horizontal = GapGroup, vertical = GapRow)) {
        ExpressiveButtonRow(
            modifier = Modifier.fillMaxWidth(),
            spacing = GapRow,
            lineSpacing = GapRow,
        ) {
            features.forEach { feature ->
                MorphActionButton(
                    label = feature.label,
                    icon = feature.icon,
                    onClick = feature.onClick,
                    enabled = feature.enabled,
                )
            }
        }
    }
}

/**
 * The one-bar header over an expanded map sheet: name (and status) at start, drag handle centred,
 * refresh at end, positioned independently in one [Box] so the handle stays centred whatever the
 * name length. [onRefreshLocation] null omits the refresh side. [dragModifier] carries the
 * drag-to-dismiss gesture, built by the caller because each call site closes over its own drag
 * state.
 */
@Composable
internal fun MapTopBar(
    vehicleName: String,
    mapHazeState: HazeState,
    modifier: Modifier = Modifier,
    dragModifier: Modifier,
    /** "88% · 415 km": the car's charge and range, under its name. Null leaves the name alone. */
    statusLine: String? = null,
    onRefreshLocation: (() -> Unit)? = null,
    /**
     * True while the refresh from [onRefreshLocation] is in flight (the real command-pending flag);
     * drives the icon spin.
     */
    refreshing: Boolean = false,
) {
    // Press-and-hold pop on the whole pill. requireUnconsumed = false observes down/up without
    // consuming, so dragModifier and the refresh click still receive the touch.
    var pillPressed by remember { mutableStateOf(false) }
    val pillScale by animateFloatAsState(
        targetValue = if (pillPressed) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
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
        Box(
            Modifier
                .fillMaxWidth()
                .then(dragModifier)
                .heightIn(min = 60.dp)
                .padding(horizontal = GapPage),
        ) {
            // Reserves room on the end for the refresh icon (never under it) regardless of
            // alignment, since both float independently in this Box rather than sharing a Row's own
            // space-distribution. The car icon, its name, and under it the charge and range: the
            // pill says which car this map is and how it is doing, not just what it is called.
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = if (onRefreshLocation != null) 48.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GapGroup),
            ) {
                Icon(AppIcons.DirectionsCar, contentDescription = null, modifier = Modifier.size(24.dp))
                Column {
                    RollingNumber(vehicleName, style = PrimaryTextStyle(), fontWeight = FontWeight.Bold)
                    if (statusLine != null) {
                        RollingNumber(
                            statusLine,
                            style = PrimaryTextStyle(),
                            fontWeight = FontWeight.Medium,
                            color = mutedContentColor(),
                        )
                    }
                }
            }
            // Frosted drag handle, centre-aligned. Purely visual: the pop and drag belong to the
            // whole bar (`dragModifier`).
            GlassSurface(
                shape = CircleShape,
                modifier = Modifier.align(Alignment.Center).size(width = 44.dp, height = 18.dp),
                hazeState = mapHazeState,
                shadow = false,
            ) {
                Box(
                    Modifier
                        .size(width = 24.dp, height = 4.dp)
                        .background(LocalContentColor.current.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
                )
            }
            if (onRefreshLocation != null) {
                // Icon-only: a refresh indicator that spins while it is actually working (the real
                // pending flag), in the same ramp-up/steady-spin language as every in-progress
                // icon.
                val angle = rememberSpinAngle(refreshing)
                // 40dp circle, centred in the bar like the handle and name; end padding keeps it
                // off the edge.
                GlassSurface(
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = GapGroup).size(40.dp),
                    hazeState = mapHazeState,
                    onClick = onRefreshLocation,
                    contentDescription = "Refresh location",
                    // Over the same theme-following map, so it inherits GlassSurface's onSurface
                    // default. Nested in the bar's elevated GlassSurface, so it skips the second
                    // shadow (see glassEdge, GlassChrome.kt).
                    shadow = false,
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = angle.value },
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
                scope.launch { pull.up.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = PopDamping, stiffness = Spring.StiffnessMediumLow)) }
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

/** "88% · 415 km": a car's charge and range for the map pill, or null when it has neither yet. */
// chargeReadoutOf is @Composable (it reads MaterialTheme/LocalContentColor for the state-colour
// pair it derives), so this wrapper must be too -- not for composition itself, but because a plain
// function cannot resolve those locals.
@Composable
internal fun mapStatusLine(state: UiState, v: com.bloo.bluelink.data.Vehicle, metric: Boolean): String? {
    val r = chargeReadoutOf(state.statusFor(v), state.hasBattery(v), state.hasFuel(v), state.drivingLabel(v), metric)
    return listOfNotNull(r.pctText.takeIf { it.isNotBlank() }, r.rangeText?.takeIf { it.isNotBlank() })
        .joinToString(" · ").ifBlank { null }
}
