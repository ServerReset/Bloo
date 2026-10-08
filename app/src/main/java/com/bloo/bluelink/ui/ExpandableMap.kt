package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.semantics.onClick
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.bloo.bluelink.data.GeoLocation
import kotlinx.coroutines.launch

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

@Composable
internal fun ExpandableMapLayer(
    isExpanded: Boolean,
    originBounds: Rect,
    location: GeoLocation,
    vehicleName: String,
    statusLine: String? = null,
    deviceLocation: GeoLocation?,
    mapState: CarMapState,
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
    // Opening the map with no phone fix yet asks for location (once per run), so the dot just
    // appears.
    LaunchedEffect(isExpanded) { if (isExpanded && deviceLocation == null) deviceRequest?.askOnce?.invoke() }
    val showMe = rememberShowMyLocation(location, deviceLocation, mapState, deviceRequest)

    // Animate from pebble size to full screen.
    val expandFraction = remember { Animatable(0f) }
    // Read here, at composable scope, not inside the LaunchedEffect below -- lowPowerAwareSpring is
    // itself @Composable, so it can't be called from a suspend lambda.
    val openSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(Unit) {
        expandFraction.animateTo(1f, animationSpec = openSpring)
    }

    // How far the drag handle has actually been pulled down, in px -- the SAME real drag-to-dismiss
    // CarMapSheetBody's own handle uses (its own `dragPx`; see that doc).
    val dragPx = remember { Animatable(0f) }
    // Read here, at composable scope, not inline inside the scope.launch{} blocks below --
    // lowPowerAwareSpring is itself @Composable (it reads battery-saver state), so it can't be
    // called from inside a suspend lambda.
    val dragSpring = lowPowerAwareSpring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium)
    // Same reason as dragSpring above -- read here, not inside the scope.launch{} in close().
    val closeSpring = lowPowerAwareSpring<Float>(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            // Both play at once so a mid-drag dismiss doesn't visibly snap dragPx back to 0 before
            // the sheet itself starts shrinking away.
            val a = scope.launch { expandFraction.animateTo(0f, animationSpec = closeSpring) }
            val b = scope.launch { dragPx.animateTo(0f, animationSpec = closeSpring) }
            a.join(); b.join()
            onDismiss()
        }
    }

    // Captured at the SHEET's own real layout -- 85% height, bottom-anchored, full width -- not the
    // whole screen. This is the "full" target the morph below scales the pebble up to.
    var sheetBounds by remember { mutableStateOf<Rect?>(null) }

    // Back handler for closing
    BackHandler(enabled = isExpanded) { close() }

    Box(Modifier.fillMaxSize()) {
        // Scrim background (dims/blurs content behind) -- covers the WHOLE screen, not just the
        // sheet: this is what shows through the top 15% strip the sheet itself doesn't reach.
        if (isExpanded) {
            ScrimBlur(hazeState = hazeState, progress = { expandFraction.value })
            Box(Modifier.fillMaxSize().noRippleClickable { close() })
        }

        // Splitting it the way CarMapSheetBody's own (working) sheet always has fixes this: THIS
        // outer box carries the sheet's own entrance -- an UNCLAMPED slide up from fully
        // below-screen, free to genuinely overshoot past rest and settle back, which is what
        // actually sells "pop" -- with no `clipToBounds()`, so the whole sheet (its rounded-corner
        // shape included) moves as one rigid unit with nothing to hit a wall against.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .graphicsLayer {
                    translationY = (1f - expandFraction.value) * size.height + dragPx.value
                }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        ) {
            // The map itself: fills the whole sheet, clamped pebble-to-full morph, clipped to its
            // own bounds so the GROWING-from-pebble content never spills past the sheet's edges on
            // the way up -- independent of the outer sheet's own entrance bounce above.
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { sheetBounds = Rect(it.positionOnScreen(), it.size.toSize()) }
                    .clipToBounds()
                    .graphicsLayer {
                        val full = sheetBounds
                        val t = expandFraction.value.coerceIn(0f, 1f)
                        if (full != null && full.width > 0f && full.height > 0f) {
                            val originScaleX = originBounds.width / full.width
                            val originScaleY = originBounds.height / full.height
                            scaleX = originScaleX + (1f - originScaleX) * t
                            scaleY = originScaleY + (1f - originScaleY) * t
                            translationX = (originBounds.center.x - full.center.x) * (1f - t)
                            translationY = (originBounds.center.y - full.center.y) * (1f - t)
                        } else {
                            scaleX = 0.92f + 0.08f * t
                            scaleY = 0.92f + 0.08f * t
                        }
                    },
            ) {
                CarMap(
                    location,
                    Modifier.fillMaxSize().hazeSource(mapHazeState),
                    state = mapState,
                    deviceLocation = deviceLocation,
                )
            }

            // One consolidated top bar -- name, drag handle, and refresh, all inside one shared
            // floating pill background instead of three separate glass pieces (see MapTopBar's own
            // doc).
            if (isExpanded && expandFraction.value > 0.1f) {
                MapTopBar(
                    vehicleName = vehicleName,
                    statusLine = statusLine,
                    mapHazeState = mapHazeState,
                    onRefreshLocation = onRefreshLocation,
                    refreshing = refreshing,
                    dragModifier = Modifier.pullDownToDismiss(dragPx, scope, density, dragSpring, ::close),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = GapSection, end = GapSection, top = GapSection)
                        .graphicsLayer { alpha = expandFraction.value.coerceIn(0f, 1f) },
                )
            }

            // Bottom buttons, which appear once the map is expanded.
            if (isExpanded && expandFraction.value > 0.1f) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .graphicsLayer { alpha = expandFraction.value.coerceIn(0f, 1f) },
                ) {
                    MapFeatureRow(
                        features = listOf(
                            MapFeature(Icons.Filled.DirectionsCar, "Car") { mapState.recenter() },
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
