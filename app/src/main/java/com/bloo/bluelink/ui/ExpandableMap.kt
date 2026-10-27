package com.bloo.bluelink.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.ui.unit.toSize
import com.bloo.bluelink.data.GeoLocation
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.MotionMedium
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

/** The pebble map's corner radius (its clip, StandardShape) and the sheet's top corners, which the map's own corners ease between. */
private val PebbleMapCorner = 16.dp
private val SheetCorner = 28.dp

@Composable
internal fun ExpandableMapLayer(
    isExpanded: Boolean,
    originBounds: Rect,
    location: GeoLocation,
    vehicleName: String,
    statusLine: String? = null,
    deviceLocation: GeoLocation?,
    mapState: CarMapState,
    /** The car's one map ([ExpandedMapState.mapFor]), moved here from its pebble. */
    map: @Composable (Modifier, MapInputs) -> Unit,
    hazeState: HazeState?,
    onRefreshLocation: () -> Unit,
    /** See [MapTopBar]'s own doc -- the real command-pending flag, not a guess. */
    refreshing: Boolean = false,
    /** Asks for the phone's location so its dot can be drawn; see [DeviceLocationRequest]. */
    deviceRequest: DeviceLocationRequest? = null,
    onDismiss: () -> Unit,
) {
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

    // Animate from pebble size to full screen.
    val expandFraction = remember { Animatable(0f) }
    // The chrome (top bar + bottom row) has its OWN track, so its arrival and departure can be
    // sequenced against the sheet's: it comes in AFTER the map has grown, and leaves BEFORE the map
    // shrinks -- the sheet morphing back into its pebble is the part the eye should follow out.
    val chrome = remember { Animatable(0f) }
    // Read here, at composable scope, not inside the LaunchedEffect below -- lowPowerAwareSpring is
    // itself @Composable, so it can't be called from a suspend lambda.
    val openSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(Unit) {
        // Open in two beats: the sheet grows from the pebble to full screen first, THEN the bars
        // arrive. Previously they faded in from the very first frame (expandFraction > 0.1), so the
        // chrome was already there while the map was still unfolding.
        expandFraction.animateTo(1f, animationSpec = openSpring)
        chrome.animateTo(1f, animationSpec = tween(MotionMedium))
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
    val dragSpring = lowPowerAwareSpring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium)
    // Same reason as dragSpring above -- read here, not inside the scope.launch{} in close().
    val closeSpring = lowPowerAwareSpring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    // How far down the pull has gone, 0..1 over 40% of the screen. The sheet shrinks toward its
    // pebble as it is dragged, so letting go carries the SAME motion on, instead of the sheet snapping
    // back to full size and then shrinking.
    val closeDistPx = LocalWindowInfo.current.containerSize.height * 0.4f
    var closing by remember { mutableStateOf(false) }
    // The top bar and bottom buttons exist only once the chrome's own track has left zero. A derived
    // boolean, so the sheet recomposes when it FLIPS, not on every frame of the springs.
    val chromeShown by remember { derivedStateOf { chrome.value > 0.01f } }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            // Chrome first (a quick retreat), then the sheet morphs back into its pebble, and the
            // drag offset eases away with it so a mid-drag dismiss doesn't visibly snap dragPx to 0.
            scope.launch { chrome.animateTo(0f, animationSpec = tween(MotionFast)) }.join()
            val a = scope.launch { expandFraction.animateTo(0f, animationSpec = closeSpring) }
            val b = scope.launch { dragPx.animateTo(0f, animationSpec = closeSpring) }
            a.join(); b.join()
            onDismiss()
        }
    }

    // The overlay's own place on screen: the pebble's rect is in screen coordinates, and the map is placed in this box's.
    var rootPos by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val windowSize = LocalWindowInfo.current.containerSize

    // Back handler for closing
    BackHandler(enabled = isExpanded) { close() }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootPos = it.positionOnScreen() }) {
        // Scrim background (dims/blurs content behind) -- covers the WHOLE screen, not just the
        // sheet: this is what shows through the top 15% strip the sheet itself doesn't reach.
        if (isExpanded) {
            ScrimBlur(hazeState = hazeState, progress = { expandFraction.value })
            Box(Modifier.fillMaxSize().noRippleClickable { close() })
        }

        // THE MAP. The very element that was in the pebble, moved here, and sized by hand every frame from the pebble's rectangle to
        // the sheet's: its tiles stay at their own scale and the car stays at its centre, so what grows is the map's own box, not a picture
        // of it. Closing runs the same rectangle back down to the pebble's, where the map is put back.
        val sheetCornerPx = with(density) { SheetCorner.toPx() }
        val pebbleCornerPx = with(density) { PebbleMapCorner.toPx() }
        val mapInputs = MapInputs(location, deviceLocation, interactive = isExpanded, tileArea = windowSize)
        map(
            Modifier
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth.toFloat()
                    val h = constraints.maxHeight.toFloat()
                    val sheetH = (h * 0.85f + pull.up.value).coerceIn(h * 0.85f, h)
                    val sheet = Rect(0f, h - sheetH + dragPx.value, w, h + dragPx.value)
                    val root = rootPos
                    val origin = if (root == null) sheet else originBounds.translate(-root.x, -root.y)
                    // Part-way to the pebble already while dragged down; the rest on release.
                    val drag = (dragPx.value / closeDistPx).coerceIn(0f, 1f)
                    val t = (expandFraction.value * (1f - 0.5f * drag)).coerceIn(0f, 1f)
                    val r = lerp(origin, sheet, t)
                    val placeable = measurable.measure(
                        androidx.compose.ui.unit.Constraints.fixed(r.width.roundToInt().coerceAtLeast(1), r.height.roundToInt().coerceAtLeast(1)),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(r.left.roundToInt(), r.top.roundToInt()) }
                }
                .graphicsLayer {
                    // Not drawn until it knows where the overlay is, rather than for a frame in the wrong place.
                    alpha = if (rootPos == null) 0f else 1f
                    val drag = (dragPx.value / closeDistPx).coerceIn(0f, 1f)
                    val t = (expandFraction.value * (1f - 0.5f * drag)).coerceIn(0f, 1f)
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
                    statusLine = statusLine,
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
                        .align(Alignment.TopCenter)
                        .padding(start = GapSection, end = GapSection, top = GapSection)
                        .graphicsLayer {
                            val t = chrome.value.coerceIn(0f, 1f)
                            alpha = t * (1f - 0.7f * (dragPx.value / closeDistPx).coerceIn(0f, 1f))
                            // Slides down into place as it arrives, back up as it leaves.
                            translationY = (1f - t) * -12.dp.toPx()
                        },
                )
            }

            // Bottom buttons, which appear once the map is expanded.
            if (isExpanded && chromeShown) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                        .imePadding()
                        .graphicsLayer {
                            val t = chrome.value.coerceIn(0f, 1f)
                            alpha = t * (1f - 0.7f * (dragPx.value / closeDistPx).coerceIn(0f, 1f))
                            // Rises into place as it arrives, back down as it leaves.
                            translationY = (1f - t) * 12.dp.toPx()
                        },
                ) {
                    MapFeatureRow(
                        features = listOf(
                            MapFeature(AppIcons.DirectionsCar, "Car") { mapState.recenter() },
                            MapFeature(Icons.Filled.MyLocation, "Me") { showMe() },
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
