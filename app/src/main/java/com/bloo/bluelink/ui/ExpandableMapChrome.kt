package com.bloo.bluelink.ui

import com.bloo.uicommon.RevealText
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.layout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import com.bloo.uicommon.lowPowerCalm
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance
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
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.animation.core.animate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.LocalPowerPolicy
import com.bloo.uicommon.LocalReduceMotion

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
    val slopPx = 10.dp.toPx()
    var raw = 0f
    var lastNotch = 0

    // The offsets this drag has set, kept here rather than read back from the Animatables: the gesture below cannot call a suspend
    // function, and the next finger event must already know where the sheet was put.
    var shownDrag = 0f
    var shownPull = 0f

    fun onDrag(amount: Float) {
        if (raw > 0f || (amount < 0f && shownDrag <= 0f)) {
            raw = (raw - amount).coerceAtLeast(0f)
            // 1:1 up to the top of the screen, then a short rubber band past it.
            val over = (raw - gapPx).coerceAtLeast(0f)
            val travel = minOf(raw, gapPx) + overPx * over / (over + overPx)
            shownPull = travel
            scope.launch { pull.up.snapTo(travel) }
            val notch = (raw / notchPx).toInt()
            if (notch > lastNotch) haptics?.ramp(raw / gapPx)
            lastNotch = notch
        } else {
            shownDrag = (shownDrag + amount).coerceAtLeast(0f)
            scope.launch { dragPx.snapTo(shownDrag) }
        }
    }

    fun onEnd() {
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
            if (shownDrag > thresholdPx) close() else scope.launch { dragPx.animateTo(0f, spring) }
        }
    }

    // Read in the INITIAL pass, like the sideways swipe: the expanded map's pan and the sheet behind the bar otherwise took the
    // drag before this saw it, so the handle did not move. A tap never claims anything; a drag claims only once it is clearly
    // vertical, and a sideways drag is left to the swipe above.
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var totalX = 0f
        var totalY = 0f
        var claimed = false
        var lastPosition = down.position
        shownDrag = dragPx.value
        shownPull = pull.up.value
        var lastCarry = 0f
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (claimed) onEnd()
                break
            }
            // The bar moves with the sheet and its pull, so its own coordinates have shifted by what the last event set. Add that back:
            // what is left is the finger's movement alone.
            val local = change.position - lastPosition
            lastPosition = change.position
            val delta = local + Offset(0f, lastCarry)
            totalX += delta.x
            totalY += delta.y
            val beforeDrag = shownDrag
            val beforePull = shownPull
            if (!claimed) {
                if (abs(totalY) > slopPx && abs(totalY) > abs(totalX)) {
                    claimed = true
                    change.consume()
                    onDrag(totalY)
                } else if (abs(totalX) > slopPx) {
                    return@awaitEachGesture
                }
            } else {
                change.consume()
                onDrag(delta.y)
            }
            lastCarry = (shownDrag - beforeDrag) - (shownPull - beforePull)
        }
    }
}

/**
 * The car's charge and its range for the map bar's end, each as its own line; null where it has none yet. The same text the hero's
 * readout shows (see [chargeReadoutOf]), worked out without the readout's theme colours, so it is a plain function: the map host derives
 * every car's bar from it outside composition, and so recomposes only when the text really changes.
 */
internal fun mapChargeStats(state: UiState, v: com.bloo.bluelink.data.Vehicle, metric: Boolean): Pair<String?, String?> {
    val status = state.statusFor(v)
    val hasBattery = state.hasBattery(v)
    val pctText = status?.percentFor(hasBattery)?.let { "$it%" } ?: "--"
    val rangeText = status?.rangeMiFor(hasBattery)?.let { formatDistance(it, metric) }
    return pctText.takeIf { it.isNotBlank() } to rangeText?.takeIf { it.isNotBlank() }
}
