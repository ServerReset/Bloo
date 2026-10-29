package com.bloo.bluelink.ui

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import com.bloo.uicommon.lowPowerSoft
import com.bloo.uicommon.bounceSpring
import com.bloo.uicommon.LocalPowerPolicy
import com.bloo.uicommon.LocalReduceMotion
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.derivedStateOf
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.lowPowerAwareSpring
import com.bloo.uicommon.SoftDamping

/**
 * One entry in the full-screen map's bottom toolbar -- an icon, a label and an action, nothing
 * else.
 */
internal data class MapFeature(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** How long the map takes to open and to close, and the curve: a ramp up and a slowing down, with no overshoot: the map does not bounce. */
private const val MapOpenMs = 520
private const val MapCloseMs = 440
private val MapOpenEasing = CubicBezierEasing(0.32f, 0f, 0.2f, 1f)
private val MapCloseEasing = CubicBezierEasing(0.32f, 0f, 0.2f, 1f)

/** The pebble map's corner radius (its clip, StandardShape) and the sheet's top corners, which the map's own corners ease between. */
private val PebbleMapCorner = 16.dp
private val SheetCorner = 28.dp

@Composable
internal fun ExpandableMapLayer(
    isExpanded: Boolean,
    originBounds: Rect,
    location: GeoLocation,
    vehicleName: String,
    /** The car's charge and range for the bar's end (see [mapChargeStats]). */
    chargePercent: String? = null,
    chargeRange: String? = null,
    /** The car's other details (year, trim and so on), shown when the bar is opened. */
    details: List<Pair<String, String>> = emptyList(),
    /** The car's stored photo, at the bar's start in place of the car glyph. */
    photo: String? = null,
    deviceLocation: GeoLocation?,
    mapState: CarMapState,
    /** The car's one map ([ExpandedMapState.mapFor]), moved here from its pebble. */
    map: @Composable (Modifier, MapInputs) -> Unit,
    onRefreshLocation: () -> Unit,
    /** See [MapTopBar]'s own doc -- the real command-pending flag, not a guess. */
    refreshing: Boolean = false,
    /** Asks for the phone's location so its dot can be drawn; see [DeviceLocationRequest]. */
    deviceRequest: DeviceLocationRequest? = null,
    /**
     * The status bar's frost source. The bar is part of it, so as the bar comes down from above the screen it is frosted by the status
     * strip, not cut off at the strip's lower edge.
     */
    frostHaze: HazeState? = null,
    onDismiss: () -> Unit,
) {
    com.bloo.bluelink.perf.TrackRecompositions("ExpandableMapLayer")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val mapHazeState = remember { HazeState() }
    // Opening the map asks for location once per run if there's no fix yet, AND always grabs a
    // fresh fix so the "you are here" dot is current rather than whatever was last seen.
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            deviceRequest?.askOnce?.invoke()
            deviceRequest?.refresh?.invoke()
        }
    }
    val showMe = rememberShowMyLocation(location, deviceLocation, mapState, deviceRequest)

    // The overlay's own place on screen: the pebble's rect is in screen coordinates, and the map is placed in this box's.
    var rootPos by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val windowSize = LocalWindowInfo.current.containerSize

    // Animate from pebble size to full screen.
    val expandFraction = remember { Animatable(0f) }
    // The chrome (top bar + bottom row) has its OWN track, so its arrival and departure can be
    // sequenced against the sheet's: it comes in AFTER the map has grown, and leaves BEFORE the map
    // shrinks -- the sheet morphing back into its pebble is the part the eye should follow out.
    val chrome = remember { Animatable(0f) }
    // Read here, at composable scope, not inside the LaunchedEffect below. A ramp up, a slowing down, and a few percent of
    // overshoot before it settles; under reduce-motion or power saving, a short plain ease instead.
    val plainMotion = LocalReduceMotion.current || LocalPowerPolicy.current.lean
    val openSpec: AnimationSpec<Float> = if (plainMotion) tween(160, easing = LinearEasing) else tween(MapOpenMs, easing = MapOpenEasing)
    val closeSpec: AnimationSpec<Float> = if (plainMotion) tween(160, easing = LinearEasing) else tween(MapCloseMs, easing = MapCloseEasing)
    // The bars' spring is read here, not inside the coroutine: spring() is composable, and so only callable during composition.
    // The gap the top bar leaves above the screen's edge, read here: tokens are composable, so not inside the draw block below.
    val barGapPx = with(density) { GapSection.toPx() * 2f }
    // The bar's gutter, from the screen's edges and from the top, read here for the same reason.
    val barInsetPx = with(density) { GapSection.toPx() }
    val chromeInSpec: AnimationSpec<Float> = if (plainMotion) tween(120) else bounceSpring(stiffness = Spring.StiffnessLow)

    val placed = rootPos != null
    LaunchedEffect(placed) {
        if (!placed) return@LaunchedEffect
        // The pin stays where it was on screen: the map is panned by how far the pebble's middle is from the sheet's, and that pan ramps in
        // with the opening (see MapInputs.panWeight), so the map opens off-centre and just gets bigger. Closing ramps it back out, which is
        // what carries the pin to the middle of the collapsed map.
        val root = rootPos ?: return@LaunchedEffect
        val o = originBounds.translate(-root.x, -root.y)
        val w = windowSize.width.toFloat()
        val h = windowSize.height.toFloat()
        val k = mapState.scale.coerceAtLeast(0.5f)
        mapState.panTo((o.center.x - w / 2f) / k, (o.center.y - (h - h * 0.85f / 2f)) / k)
        com.bloo.bluelink.perf.PerfMonitor.scope("map-open", 1_000)
        // The sheet grows and the bar flies in together: the bar comes down from above the screen, under the status bar, and
        // overshoots its place a little before it settles.
        launch { expandFraction.animateTo(1f, animationSpec = openSpec) }
        launch { chrome.animateTo(1f, animationSpec = chromeInSpec) }
    }

    // How far the drag handle has actually been pulled down, in px -- the SAME real drag-to-dismiss
    // CarMapSheetBody's own handle uses (its own `dragPx`; see that doc).
    val dragPx = remember { Animatable(0f) }
    val pull = remember { MapPullState() }
    // The gap above the sheet: how far it can rise before it meets the top of the screen.
    val gapPx = LocalWindowInfo.current.containerSize.height * 0.15f
    val refreshIndicator = LocalRefreshIndicator.current
    // Keep the shared indicator spinning for as long as the location request is actually running.
    DisposableEffect(refreshing) {
        refreshIndicator?.setRefreshing(pull, refreshing)
        onDispose { refreshIndicator?.setRefreshing(pull, false) }
    }
    // Read here, at composable scope, not inline inside the scope.launch{} blocks below --
    // lowPowerAwareSpring is itself @Composable (it reads battery-saver state), so it can't be
    // called from inside a suspend lambda.
    val dragSpring = lowPowerSoft<Float>(Spring.StiffnessMedium)
    // How far down the pull has gone, 0..1 over 40% of the screen. The sheet shrinks toward its
    // pebble as it is dragged, so letting go carries the SAME motion on, instead of the sheet snapping
    // back to full size and then shrinking.
    val closeDistPx = LocalWindowInfo.current.containerSize.height * 0.4f
    // How far open the map is, 0 (the pebble) to 1 (the sheet), a little past either end while it overshoots. Part-way to the pebble already
    // while dragged down; the rest on release.
    fun progress(): Float {
        val drag = (dragPx.value / closeDistPx).coerceIn(0f, 1f)
        return expandFraction.value * (1f - 0.5f * drag)
    }
    var closing by remember { mutableStateOf(false) }
    // The top bar and bottom buttons exist only once the chrome's own track has left zero. A derived
    // boolean, so the sheet recomposes when it FLIPS, not on every frame of the springs.
    val chromeShown by remember { derivedStateOf { chrome.value > 0.01f } }
    fun close() {
        if (closing) return
        closing = true
        com.bloo.bluelink.perf.PerfMonitor.scope("map-close", 1_000)
        scope.launch {
            // One motion: the bar retreats up while the sheet shrinks, and the bar's width follows the sheet from the first frame.
            val retreat = launch {
                if (plainMotion) {
                    chrome.animateTo(0f, tween(120))
                } else {
                    // A small bounce out, then off the top it came from.
                    chrome.animateTo(1.07f, tween(90, easing = FastOutSlowInEasing))
                    chrome.animateTo(0f, tween(MapCloseMs - 90, easing = FastOutLinearInEasing))
                }
            }
            val shrink = launch { expandFraction.animateTo(0f, animationSpec = closeSpec) }
            val drag = launch { dragPx.animateTo(0f, animationSpec = closeSpec) }
            retreat.join(); shrink.join(); drag.join()
            // The pin has arrived at the middle of the collapsed map; the pebble's map starts from there.
            mapState.panTo(0f, 0f)
            onDismiss()
        }
    }

    // Back handler for closing
    BackHandler(enabled = isExpanded) { close() }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootPos = it.positionOnScreen() }) {
        // Clear, not blurred: the app behind the map is not dimmed or blurred. This covers the whole screen, so a tap in the strip
        // above the sheet closes it too.
        if (isExpanded) {
            Box(Modifier.fillMaxSize().noRippleClickable { close() })
        }

        // THE MAP. The very element that was in the pebble, moved here, and sized by hand every frame from the pebble's rectangle to
        // the sheet's: its tiles stay at their own scale and the car stays at its centre, so what grows is the map's own box, not a picture
        // of it. Closing runs the same rectangle back down to the pebble's, where the map is put back.
        val sheetCornerPx = with(density) { SheetCorner.toPx() }
        val pebbleCornerPx = with(density) { PebbleMapCorner.toPx() }
        val mapInputs = MapInputs(location, deviceLocation, interactive = isExpanded, tileArea = windowSize, panWeight = { progress().coerceIn(0f, 1f) })
        map(
            Modifier
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth.toFloat()
                    val h = constraints.maxHeight.toFloat()
                    val sheetH = (h * 0.85f + pull.up.value).coerceIn(h * 0.85f, h)
                    val sheet = Rect(0f, h - sheetH + dragPx.value, w, h + dragPx.value)
                    val root = rootPos
                    val origin = if (root == null) sheet else originBounds.translate(-root.x, -root.y)
                    val r = lerp(origin, sheet, progress())
                    val placeable = measurable.measure(
                        androidx.compose.ui.unit.Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(r.left.roundToInt(), r.top.roundToInt()) }
                }
                .graphicsLayer {
                    // Not drawn until it knows where the overlay is, rather than for a frame in the wrong place.
                    alpha = if (rootPos == null) 0f else 1f
                    val t = progress().coerceIn(0f, 1f)
                    val top = lerp(pebbleCornerPx, sheetCornerPx, t)
                    val bottom = lerp(pebbleCornerPx, 0f, t)
                    shape = RoundedCornerShape(
                        topStart = CornerSize(top), topEnd = CornerSize(top), bottomEnd = CornerSize(bottom), bottomStart = CornerSize(bottom),
                    )
                    clip = true
                }
                .hazeSource(mapHazeState),
            mapInputs,
        )

        // The bars. The sheet's own frame, where it comes to rest (85% of the screen, taller as it is pulled up): it holds only the
        // bars now, and moves only with the pull-down.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                // 85% of the screen, plus however far it has been pulled up: the sheet's top rises with the finger while its bottom
                // stays pinned to the screen bottom. Read in the measure phase.
                .layout { measurable, constraints ->
                    val base = (constraints.maxHeight * 0.85f).roundToInt()
                    val h = (base + pull.up.value.roundToInt()).coerceIn(base, constraints.maxHeight)
                    val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(p.width, p.height) { p.place(0, 0) }
                }
                .graphicsLayer { translationY = dragPx.value },
        ) {
            // One consolidated top bar -- name, drag handle, and refresh, all inside one shared
            // floating pill background instead of three separate glass pieces (see MapTopBar's own
            // doc).
            if (isExpanded && chromeShown) {
                MapTopBar(
                    vehicleName = vehicleName,
                    photo = photo,
                    chargePercent = chargePercent,
                    chargeRange = chargeRange,
                    details = details,
                    mapHazeState = mapHazeState,
                    // No refresh button: pull the bar up to full tension and let go instead.
                    dragModifier = Modifier.pullToRefreshOrDismiss(
                        dragPx, pull, gapPx, scope, density, dragSpring, LocalHaptics.current, ::close,
                        {
                            // The disc drops in from the top (as for any pull-to-refresh) and holds
                            // while the location request is in flight.
                            refreshIndicator?.requested = true
                            onRefreshLocation()
                        },
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        // The bar's width follows the sheet's: as wide as the pebble at the start, the sheet's side gutters when open,
                        // and back again on close, from the first frame of either.
                        .then(if (frostHaze != null) Modifier.hazeSource(frostHaze) else Modifier)
                        .layout { measurable, constraints ->
                            val p = progress().coerceIn(0f, 1f)
                            val root = rootPos ?: androidx.compose.ui.geometry.Offset.Zero
                            val o = originBounds.translate(-root.x, -root.y)
                            val left = lerp(o.left, barInsetPx, p)
                            val right = lerp(o.right, constraints.maxWidth - barInsetPx, p)
                            val w = (right - left).roundToInt().coerceAtLeast(1)
                            val bar = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                            layout(constraints.maxWidth, bar.height + barInsetPx.roundToInt()) {
                                bar.place(left.roundToInt(), barInsetPx.roundToInt())
                            }
                        }
                        .graphicsLayer {
                            // 0 is off the top of the screen, 1 its place; the bounce takes it a little past 1 either way. It stays solid as it goes.
                            translationY = -(1f - chrome.value) * (windowSize.height * 0.15f + size.height + barGapPx)
                        },
                )
            }

            // Bottom buttons, which appear once the map is expanded.
            if (isExpanded && chromeShown) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                        .imePadding()
                        .graphicsLayer {
                            // 0 is off the bottom of the screen, 1 its place; the bounce takes it a little past 1 either way. It stays solid as it goes.
                            translationY = (1f - chrome.value) * (size.height + 120.dp.toPx())
                        },
                ) {
                    MapFeatureRow(
                        features = listOf(
                            MapFeature(AppIcons.DirectionsCar, "Car") { mapState.recenter() },
                            MapFeature(Icons.Filled.MyLocation, "Me") { showMe() },
                            MapFeature(Icons.Filled.Refresh, "Refresh", enabled = !refreshing) { onRefreshLocation() },
                            MapFeature(Icons.Filled.Share, "Share") {
                                shareLocation(context, location, vehicleName)
                            },
                            MapFeature(Icons.Filled.Map, "Open in Maps") {
                                openInExternalMaps(context, location, vehicleName)
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
