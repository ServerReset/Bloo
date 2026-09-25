@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.bloo.bluelink.data.ChargerFilters
import com.bloo.bluelink.data.ChargerStation
import com.bloo.bluelink.data.matches
import com.bloo.bluelink.data.GeoLocation
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The bottom sheet's shared body -- everything about presenting the expanded
 * map (slide-in/out, scrim, drag-to-dismiss, chrome) except how it's hosted.
 * Split out of ExpandableMap.kt since it's the largest single piece there.
 */

/**
 * The map, expanded into a bottom sheet -- reached from [CarMap]'s own corner
 * button via [CarMapSheet] (a Dialog). Everything about the sheet itself -- its
 * slide-in/out, scrim, drag-to-dismiss, chrome -- lives here, entirely separate
 * from how [CarMapSheet] hosts it.
 *
 * A hand-rolled overlay, NOT [androidx.compose.material3.ModalBottomSheet] -- that
 * was the second attempt here (the first was a hand-rolled [Dialog] driving its own
 * `graphicsLayer` scale/translate from a captured on-screen rect, reported as not
 * actually seamless, not full screen, and covering its own close button).
 * `ModalBottomSheet` fixed all of that, but turned out to have a problem of its own
 * that no configuration can reach: it is *itself* implemented as a `Dialog` under the
 * hood, and Android/Compose Dialogs adapt to large screens by centering themselves
 * and capping their width (`sheetMaxWidth`) -- so on a tablet or unfolded foldable
 * this rendered as a boxed, dialog-shaped card floating in the middle of the screen
 * with the app visible on all four sides, including BELOW its own bottom edge.
 * Reported directly from a screenshot: "why does it float like that? That's wrong."
 * `ModalBottomSheetProperties` exposes no override for that adaptive centering --
 * it's baked into the Dialog underneath, not a configurable behaviour of the sheet.
 *
 * [visible] (an [Animatable] the sheet's whole lifecycle runs on, 0 = slid fully off
 * the bottom edge, 1 = at rest) drives the slide-in/out and the scrim's fade
 * together, a tap on the scrim dismisses, and the drag handle (not the whole sheet
 * -- the map area already owns pan/pinch of its own) supports drag-to-dismiss via
 * [dragPx]. [CarMap] itself still grows in from [originBounds] so opening reads as
 * the map continuing to expand rather than a flat cut.
 *
 * The bottom [MapFeatureRow] is deliberately sparse today (recentre, open in the
 * system Maps app) -- see [MapFeature]'s own doc. This sheet, not a new screen in the
 * app's own navigation, is the FRAMEWORK request this shipped alongside: a
 * self-contained expanded surface future map features can build against (a drawn
 * route, live traffic, nearby search, saved places) without first having to plumb a
 * new destination through the rest of the app.
 */
@Composable
internal fun CarMapSheetBody(
    location: GeoLocation,
    vehicleName: String,
    deviceLocation: GeoLocation?,
    mapState: CarMapState,
    originBounds: Rect?,
    /** Always null: this body is only ever hosted via [CarMapSheet]'s Dialog, and
     *  Haze cannot reach across windows, so the scrim below always falls back to a
     *  plain darkened one. Kept as a real parameter (not hardcoded null internally)
     *  since a future non-Dialog host is exactly the kind of thing this shared body
     *  exists to support -- see this function's own doc. */
    hazeState: HazeState?,
    /** Null (the default) omits the refresh icon entirely -- see [MapTopBar]'s
     *  own doc. */
    onRefreshLocation: (() -> Unit)? = null,
    /** See [MapTopBar]'s own doc -- the real command-pending flag, not a guess. */
    refreshing: Boolean = false,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Its own HazeState, independent of [hazeState] (which sources the SCREEN
    // behind this sheet, for the scrim above) -- this one sources the map tiles
    // THEMSELVES, drawn inside this sheet, so the drag handle's own frosted chip
    // (below) can blur what's actually behind it regardless of whether this sheet
    // is hosted in a Dialog (where [hazeState] is always null) or GarageScreen's
    // own overlay.
    val mapHazeState = remember { HazeState() }

    // This map area's own full-size on-screen rect, captured once laid out --
    // constant for the life of this sheet (only the graphicsLayer transform below
    // moves, never the actual layout).
    var fullBounds by remember { mutableStateOf<Rect?>(null) }
    // 0 = fully hidden (slid off the bottom of the screen, or -- while there's an
    // origin to grow from -- sitting exactly over originBounds, what the small map
    // looked like the instant this opened), 1 = at rest / grown to this map area's
    // own natural size and position. One value drives BOTH the sheet's own
    // slide-in/out and the map's grow-from-the-pebble morph, so the two read as one
    // continuous motion instead of two separately-timed animations.
    val visible = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    // How far the drag handle has been pulled down from rest, in px -- 0 normally,
    // positive while a drag-to-dismiss gesture is in progress. Only the handle
    // itself feeds this (see its own pointerInput far below), never the sheet at
    // large: the map area already owns pan/pinch gestures of its own, and a
    // whole-sheet drag-to-dismiss would fight it for every downward pan.
    val dragPx = remember { Animatable(0f) }
    // Read here, at composable scope, not inline inside the scope.launch{} blocks below --
    // lowPowerAwareSpring is itself @Composable (it reads battery-saver state), so it can't
    // be called from inside a suspend lambda. Captured once and referenced from both.
    val dragSpring = lowPowerAwareSpring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium)
    // Same reason as dragSpring above -- read here, not inside the scope.launch{} in close().
    val closeSpring = lowPowerAwareSpring<Float>(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)
    // Same reason again -- read here, not inside the LaunchedEffect further down.
    // NoBouncy (critically damped, no overshoot) -- reported directly as wanting
    // the sheet to pull up "like a normal card... nice and smooth", not with the
    // springy pop this used to have.
    val openSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            // Both play at once so a mid-drag dismiss doesn't visibly snap dragPx
            // back to 0 before the slide-out starts. Use a spring for close to match
            // the bouncy open, but with much more damping so it settles quickly.
            val a = scope.launch { visible.animateTo(0f, closeSpring) }
            val b = scope.launch { dragPx.animateTo(0f, closeSpring) }
            a.join(); b.join()
            onDismiss()
        }
    }
    // System back plays the same animated close as the scrim tap/drag handle,
    // rather than [CarMapSheet]'s Dialog wrapper tearing this down immediately
    // with no animation at all via its own onDismissRequest (which still calls
    // onDismiss directly, as a fallback -- see its own doc).
    BackHandler(enabled = true) { close() }

    LaunchedEffect(originBounds) {
        if (originBounds != null) snapshotFlow { fullBounds }.filterNotNull().first()
        // Bouncier than the rest of the app's own SoftDamping default, specifically
        // for opening: the origin->full scale/position interpolation below (and the
        // sheet's own slide-in translation) is driven directly off this value with
        // no clamp, so letting the spring genuinely overshoot past 1 here is what
        // makes the map actually POP out of the pebble -- growing slightly past its
        // final size/position and settling back -- rather than smoothly easing into
        // place. Reported directly as wanting it to "pop out of the little map".
        visible.animateTo(1f, openSpring)
    }
    Box(Modifier.fillMaxSize()) {
        // The scrim -- dims (and, with a real hazeState, blurs) the app behind the
        // sheet, and a tap on it dismisses.
        Box(Modifier.fillMaxSize()) {
            // ScrimBlur (GlassChrome.kt): the exact same dim+blur scrim
            // ExpandableMapLayer's own full-screen overlay uses -- this sheet used to
            // carry a byte-for-byte identical copy of that Box chain, right down to
            // reading `visible`/`expandFraction` inside a draw-phase lambda instead
            // of at composition time (see ScrimBlur's own doc for why that distinction
            // is load-bearing here, not stylistic).
            ScrimBlur(hazeState = hazeState, progress = { visible.value })
            Box(Modifier.fillMaxSize().noRippleClickable { close() })
        }
        // The sheet itself: bottom-anchored and full-width on EVERY screen size --
        // see this function's own doc for why. `translationY` (not a plain offset)
        // so the same graphicsLayer that slides it in from fully off-screen at
        // [visible] == 0 also carries the live drag-to-dismiss gesture (`dragPx`)
        // without the two ever fighting over which owns the sheet's position.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 85% of the SCREEN (not counting the status bar). Sizing the SHEET
                // itself here, not the content inside it: this Box IS the sheet.
                .fillMaxHeight(0.85f)
                .graphicsLayer { translationY = (1f - visible.value) * size.height + dragPx.value }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                // Deliberately no background/Surface fill here -- painting a solid
                // tonal rectangle behind the sheet's ENTIRE bounds regardless of
                // content is what made a boxed dialog card read as a floating card
                // rather than a sheet. The map below fills this box edge to edge on
                // its own; nothing else here needs a background to sit on.
                .noRippleClickable { /* swallow taps so they don't fall through to the scrim behind */ },
        ) {
            // The map fills the WHOLE sheet, edge to edge -- no boxed-in margin --
            // with the header and toolbar floating semi-transparently ON TOP of it
            // instead of splitting the sheet into three stacked, non-overlapping
            // bands, the same "content flows behind floating elements" relationship
            // the rest of the app already gives its own scrolling content.
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { fullBounds = Rect(it.positionOnScreen(), it.size.toSize()) }
                    // Clips the growing content to this box's own bounds -- belt and
                    // braces against the translation below ever drawing outside its
                    // slot (an earlier attempt's reported "covers the close button"
                    // came from a similar transform on an UNCLIPPED Column, which
                    // Compose happily draws past its own measured bounds).
                    .clipToBounds()
                    .graphicsLayer {
                        val origin = originBounds
                        val full = fullBounds
                        if (origin != null && full != null && full.width > 0f && full.height > 0f) {
                            // Clamp t to [0, 1] so overshoot from the spring damping doesn't
                            // cause the scale to exceed 1.0 or the translation to reverse.
                            // This keeps the morphing feeling smooth and natural even as the
                            // spring overshoots on open.
                            val t = visible.value.coerceIn(0f, 1f)
                            val originScaleX = origin.width / full.width
                            val originScaleY = origin.height / full.height
                            scaleX = originScaleX + (1f - originScaleX) * t
                            scaleY = originScaleY + (1f - originScaleY) * t
                            translationX = (origin.center.x - full.center.x) * (1f - t)
                            translationY = (origin.center.y - full.center.y) * (1f - t)
                        } else {
                            // No origin to grow from (a caller that passed null) --
                            // the old fallback: a plain scale-in from a touch under
                            // full size rather than nothing at all.
                            val t = visible.value.coerceIn(0f, 1f)
                            scaleX = 0.92f + 0.08f * t
                            scaleY = 0.92f + 0.08f * t
                        }
                    },
            ) {
                CarMap(
                    location,
                    // hazeSource, not just fillMaxSize: marks the map's own tiles as
                    // blurrable content for the drag handle's own frosted chip below
                    // (mapHazeState) -- a SEPARATE HazeState from the sheet's own
                    // scrim blur ([hazeState] param), which sources the screen behind
                    // this sheet, not the map drawn on top of it. Blurring the handle
                    // against the map itself needs its own source regardless of
                    // whether this sheet is a Dialog or an in-tree overlay, so this
                    // works either way even when [hazeState] itself is null.
                    Modifier.fillMaxSize().hazeSource(mapHazeState),
                    state = mapState,
                    deviceLocation = deviceLocation,
                )
            }
            // One consolidated top bar -- name + drag handle, sharing one floating
            // pill background instead of two separate pieces of glass (see
            // MapTopBar's own doc). Refresh is whatever this sheet's own caller
            // passed in (null omits it entirely). Also the ONLY thing on this sheet
            // a user can pull down to dismiss (see [dragPx]'s own doc up top) -- the
            // whole bar is now the drag target, not just a slim strip above it.
            MapTopBar(
                vehicleName = vehicleName,
                mapHazeState = mapHazeState,
                onRefreshLocation = onRefreshLocation,
                refreshing = refreshing,
                dragModifier = Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            scope.launch { dragPx.snapTo((dragPx.value + amount).coerceAtLeast(0f)) }
                        },
                        onDragEnd = {
                            // Distance, not velocity -- a fixed 96dp pull is a
                            // close enough stand-in for "the user clearly meant
                            // to close this".
                            val thresholdPx = with(density) { 96.dp.toPx() }
                            if (dragPx.value > thresholdPx) {
                                close()
                            } else {
                                scope.launch {
                                    dragPx.animateTo(0f, dragSpring)
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { dragPx.animateTo(0f, dragSpring) }
                        },
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .graphicsLayer { alpha = visible.value.coerceIn(0f, 1f) },
            )
            // No close (X) button -- swipe-to-dismiss (or tapping the scrim above the
            // sheet) is already how this closes; a second, redundant affordance for
            // the same action was reported directly as unwanted clutter.
            MapFeatureRow(
                features = listOf(
                    MapFeature(Icons.Filled.MyLocation, "Recentre") { mapState.recenter() },
                    MapFeature(Icons.Filled.Map, "Open in Maps") {
                        openInExternalMaps(context, location, vehicleName)
                    },
                    // FRAMEWORK: append future map features here -- each is just an
                    // icon, a label and an action, e.g.:
                    //   MapFeature(Icons.Filled.AltRoute, "Directions") { ... }
                    //   MapFeature(Icons.Filled.Layers, "Traffic") { ... }
                    //   MapFeature(Icons.Filled.Search, "Nearby") { ... }
                    //   MapFeature(Icons.Filled.Share, "Share location") { ... }
                ),
                // No background band here either -- each pill already carries its own
                // opaque chrome (MorphButton's own container fill), which is plenty of
                // legibility on its own without a second, full-width tint behind them.
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .graphicsLayer { alpha = visible.value.coerceIn(0f, 1f) },
            )
        }
    }
}
