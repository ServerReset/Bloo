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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.drawable.toDrawable
import dev.chrisbanes.haze.HazeState
import com.bloo.bluelink.data.GeoLocation
import kotlinx.coroutines.flow.first

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
 * The buttons under a map: one standard [MorphActionButton] per [MapFeature] in the app's standard
 * [ExpressiveButtonRow] -- as many to a line as fit, each line balanced and filled edge to edge,
 * a press pushing its neighbours, a button alone on its line resting on the start edge and widening
 * when pressed. Nothing here is special to maps: it is the same row every other group of buttons is.
 */
@Composable
internal fun MapFeatureRow(
    features: List<MapFeature>,
    modifier: Modifier = Modifier,
) {
    ExpressiveButtonRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = GapRow),
        spacing = GapRow,
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

/**
 * The map, expanded into a bottom sheet -- reached from [CarMap]'s own corner button.
 * Its own [CarMapState] ([rememberCarMapState]), independent of whatever the small
 * inline map is panned/zoomed to: expanding is a bigger canvas to look at the SAME
 * car on, not a continuation of one specific gesture.
 *
 * A hand-rolled overlay on a plain [Dialog], NOT [androidx.compose.material3.ModalBottomSheet]
 * -- that was the SECOND attempt here (the first was a hand-rolled [Dialog] driving
 * its own `graphicsLayer` scale/translate from a captured on-screen rect, reported as
 * not actually seamless, not full screen, and covering its own close button).
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
 * So this goes one level lower: a plain [Dialog] with `usePlatformDefaultWidth =
 * false` gets NONE of that adaptive treatment -- it's just a full-screen surface this
 * draws its own true bottom-anchored, full-width sheet onto, identically regardless
 * of how wide the window is. Everything `ModalBottomSheet` used to give for free now
 * lives here instead: [visible] (an [Animatable] the sheet's whole lifecycle runs on,
 * 0 = slid fully off the bottom edge, 1 = at rest) drives the slide-in/out and the
 * scrim's fade together, a tap on the scrim dismisses, and the drag handle (not the
 * whole sheet -- the map area already owns pan/pinch of its own) supports drag-to-
 * dismiss via [dragPx]. The one thing genuinely lost versus `ModalBottomSheet` is its
 * built-in fling-velocity dismiss; a plain distance threshold stands in for it below.
 *
 * [CarMap] itself still grows in from [originBounds] (see `morph`'s own doc, now
 * folded into [visible]) so opening reads as the map continuing to expand rather than
 * a flat cut.
 *
 * The bottom [MapFeatureRow] is deliberately sparse today (recentre, open in the
 * system Maps app) -- see [MapFeature]'s own doc. This sheet, not a new screen in the
 * app's own navigation, is the FRAMEWORK request this shipped alongside: a
 * self-contained expanded surface future map features can build against (a drawn
 * route, live traffic, nearby search, saved places) without first having to plumb a
 * new destination through the rest of the app.
 */
@Composable
internal fun CarMapSheet(
    location: GeoLocation,
    vehicleName: String,
    deviceLocation: GeoLocation?,
    /**
     * The small map's own on-screen rect (absolute screen coordinates -- see the
     * call site's own doc) at the moment it was tapped. The sheet's own map area
     * morphs from this rect to its natural size/position (a `graphicsLayer` scale +
     * translate driven by one shared [Animatable]) instead of just fading/scaling in
     * from its own centre -- reported directly as wanting the card to expand FROM
     * the map pebble, not materialise over the bottom of the screen. Null (measured
     * too late, or the caller has no origin to offer) falls back to the plain
     * scale-from-a-touch-under-full-size CarMapSheet always had.
     */
    originBounds: Rect?,
    /** Null (the default) omits the refresh icon entirely -- see [MapTopBar]'s
     *  own doc. */
    onRefreshLocation: (() -> Unit)? = null,
    /** See [MapTopBar]'s own doc -- the real command-pending flag, not a guess. */
    refreshing: Boolean = false,
    deviceRequest: DeviceLocationRequest? = null,
    onDismiss: () -> Unit,
) {
    // The Dialog-based fallback for hosts that don't provide a LocalExpandedMap
    // (the flip-cover screen) -- see ExpandedMapState's own doc. A genuinely
    // separate CarMap/CarMapState of its own, not the compact map's, since there's
    // no shared state to reach here.
    Dialog(
        // A fallback only -- CarMapSheetBody's own BackHandler intercepts system
        // back first and runs its animated close() before this ever fires. Direct,
        // with no animation, since close() itself lives inside CarMapSheetBody and
        // isn't reachable from here.
        onDismissRequest = onDismiss,
        // false: a full-screen canvas, not a Dialog sized/positioned by the
        // platform's own adaptive rules -- see CarMapSheetBody's own doc for why
        // ModalBottomSheet (itself a Dialog) couldn't avoid that. decorFitsSystemWindows
        // = false so this draws genuinely edge-to-edge and positions its own content
        // (the map area's own insets/padding already handle the status/nav bars)
        // rather than having the window itself carve out a smaller content area.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // As in GlassAlertDialog: this Dialog owns its own platform Window, entirely
        // separate from the main Activity window, so the platform's own default
        // dim/background behind it has to be turned off explicitly -- CarMapSheetBody
        // draws its own scrim instead. Also why hazeState is null here: Haze can only
        // blur content that's actually in the SAME window/composition as its source,
        // and this Dialog's window is not that -- see ExpandedMapState's own doc.
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0f)
            window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        }
        CarMapSheetBody(
            location, vehicleName, deviceLocation,
            mapState = rememberCarMapState(),
            originBounds = originBounds,
            hazeState = null,
            onRefreshLocation = onRefreshLocation,
            refreshing = refreshing,
            deviceRequest = deviceRequest,
            onDismiss = onDismiss,
        )
    }
}

/**
 * THE single CarMap instance, repositionable between pebble and full-screen.
 * Literally one Box growing from the pebble location to a SHEET -- the bottom
 * 85% of the screen, not the whole thing -- with the top 15% left showing the
 * blurred/dimmed app behind it, same shape [CarMapSheetBody] always used.
 * Reported directly: the sheet was covering the entire screen edge to edge,
 * and its drag handle sat glued to the literal top of the screen instead of
 * near the top of the sheet itself.
 *
 * Getting the "grow from the pebble, no distortion" morph right against a
 * TARGET smaller than the full screen means the map's own Box must already,
 * for real (not via a graphicsLayer trick), be laid out at that final 85%-
 * height/bottom-anchored size -- exactly [CarMapSheetBody]'s own technique.
 * A graphicsLayer scale toward a box whose OWN natural size is the full
 * screen (this composable's very first version) can only ever reach 1.0,
 * i.e. the full screen, at rest -- there is no way to "scale down" to a
 * smaller resting size without visibly squashing the map along the way.
 */
/**
 * Everything that used to float over the top of an expanded map sheet as three
 * separate glass pills -- the vehicle-name pill (top-left), the drag handle
 * (top-center), and the refresh chip (top-right) -- consolidated into ONE bar
 * with one shared floating pill background. They used to each carry their own
 * hand-rolled (or near-identical) [GlassSurface], each with its own blur, its
 * own shadow, its own rim -- three separate pieces of glass chrome doing what
 * reads, and should always have read, as a single control strip. Giving up on
 * making that strip feel "uniform" with every other floating surface in the
 * app in some deeper sense and just building it as one plain bar was the
 * actual ask: a name on the left, a drag handle centered above a divider, and
 * a refresh action on the right, all inside one rounded rect.
 *
 * [onRefreshLocation] null (the [CarMapSheet]/[CarMapSheetBody] default) omits
 * the refresh side entirely rather than showing a dead button.
 *
 * [dragModifier] carries the vertical-drag-to-dismiss gesture -- built by the
 * caller, since the two call sites each close over their own [dragPx]/`scope`/
 * `close()`, and passing the finished modifier in is simpler than exporting a
 * matching set of callback params for the same thing.
 *
 * ONE line, not the old two-row layout (a drag-handle row stacked above a
 * name/refresh row) -- name at [Alignment.CenterStart], the drag handle nub
 * genuinely centred on the bar regardless of the name's length or whether a
 * refresh icon is showing, and the refresh icon at [Alignment.CenterEnd], all
 * three positioned independently in one [Box] rather than flowing through a
 * [Row]. Reported directly as wanting one line with bigger text and the
 * handle in the middle.
 */
@Composable
internal fun MapTopBar(
    vehicleName: String,
    mapHazeState: HazeState,
    modifier: Modifier = Modifier,
    dragModifier: Modifier,
    onRefreshLocation: (() -> Unit)? = null,
    /** True while a refresh this bar's own [onRefreshLocation] kicked off is
     *  still in flight -- the real command-pending flag from the caller
     *  (state.isPending(vin, "locate")), not a locally-faked timer, so the
     *  icon's spin genuinely tracks "still fetching" rather than a guessed
     *  duration. */
    refreshing: Boolean = false,
) {
    // Press-and-hold "pop" on the WHOLE pill -- not just the drag handle nub --
    // the instant any part of it is touched, before any actual drag motion,
    // reading as "I feel you, go ahead" rather than staying inert until it
    // moves. Reported directly as wanting the entire bar (name, handle AND
    // refresh indicator together) to pop, not just the nub in isolation.
    // requireUnconsumed = false: this OBSERVES the down/up sequence without
    // consuming it, so the same touch still reaches dragModifier's own gesture
    // detector -- popping must never steal the drag it's advertising, and the
    // refresh button's own click still fires normally (a click is a down+up
    // with no net movement, exactly what this passes through unconsumed).
    var pillPressed by remember { mutableStateOf(false) }
    val pillScale by animateFloatAsState(
        targetValue = if (pillPressed) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "mapBarPop",
    )
    GlassSurface(
        shape = LargeShape,
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
        // No contentColor override: [GlassSurface]'s own default (onSurface) is the
        // right answer here, and the `Color.White` this used to force was wrong in
        // light mode. This bar is glass over the MAP, and the map follows the app's
        // theme -- CarMap dark-filters these same OSM tiles only when
        // appIsDarkTheme() (see darkMapFilter's own doc), so in light mode the
        // backdrop behind this pill is a BRIGHT raster map. Unlike the hero's photo,
        // there is no dark scrim under it to make white legible: the only fill is
        // glassTint, which in light mode is surfaceContainer at 0.08-0.12 alpha, so
        // the car's name, the drag nub and the refresh glyph were near-white on
        // near-white -- the bar read as empty. onSurface tracks the same theme the
        // map filter does, so it is dark-on-light map and light-on-dark map by
        // construction, and it honours an active custom palette the way
        // the pin beside it already does. It is also what the map's own
        // MapFeatureRow buttons below already use.
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(dragModifier)
                // 48dp: tall enough for the bigger titleLarge name and a comfortable
                // touch target for both the drag handle and the refresh icon, all on
                // one line -- the old two-row layout (a 14dp handle strip stacked
                // above a name/refresh row) took noticeably more vertical space for
                // the same content.
                .height(48.dp)
                .padding(horizontal = 16.dp),
        ) {
            // Name and bigger, titleLarge (was titleMedium) -- reported directly as
            // wanting bigger text. Reserves room on the end for the refresh icon
            // (never under it) regardless of alignment, since both float independently
            // in this Box rather than sharing a Row's own space-distribution. Reserves
            // the SAME 40dp the refresh circle itself occupies (see below) -- previously
            // this reserved 44dp against a 36dp circle, a mismatched pair that was the
            // "refresh button is in the incorrect place, not equal" report: the circle
            // sat 8dp closer to the edge than the space carved out for it implied, so it
            // read as off-centre against its own reserved slot.
            Text(
                vehicleName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = if (onRefreshLocation != null) 40.dp else 0.dp),
            )
            // The drag handle nub -- purely visual now, no pointerInput of its own:
            // the pop above already tracks press for the whole pill, so a second,
            // separate press-tracker here would just be redundant (and, worse, could
            // read a DIFFERENT pressed state than the pill around it if the two ever
            // drifted, which is exactly the "parts of the pill disagree" look this is
            // meant to avoid).
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 32.dp, height = 4.dp)
                    // The bar's own inherited content tone, halved -- not a fixed
                    // white. It sits inside the GlassSurface above, whose
                    // CompositionLocalProvider already resolved the one colour that
                    // reads against this backdrop in both themes, so the nub cannot
                    // disagree with the name and the refresh glyph either side of it
                    // (a fixed white nub survived the contentColor fix above as a
                    // pale smudge on a light map). Same idiom as ChargeSegmentBar's
                    // own track tints (HeroReadout.kt): inherit the reader's colour
                    // and mute it, rather than guessing a colour here.
                    .background(LocalContentColor.current.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
            )
            if (onRefreshLocation != null) {
                // Icon-only (was a text chip: "Updated Xm ago" / "Refresh" beside a
                // static icon) -- reported directly as wanting a refresh INDICATOR,
                // not a label, that animates while it's actually working. Same
                // ramp-up/steady-spin language MorphButtonGlyph (Morph.kt) already
                // uses for every other in-progress icon in the app, driven by the
                // real pending flag rather than a guessed duration -- see
                // [refreshing]'s own doc.
                val angle = remember { Animatable(0f) }
                LaunchedEffect(refreshing) {
                    if (refreshing) {
                        angle.animateTo(angle.value + 360f, tween(850, easing = FastOutLinearInEasing))
                        while (true) {
                            angle.animateTo(angle.value + 360f, tween(600, easing = LinearEasing))
                        }
                    } else if (angle.value != 0f) {
                        val target = kotlin.math.ceil(angle.value / 360f) * 360f
                        angle.animateTo(target, tween(700, easing = LinearOutSlowInEasing))
                        angle.snapTo(0f)
                    }
                }
                // 40dp, matching the Text's own reserved end space above exactly --
                // was 36dp against a 44dp reservation, a mismatched pair (see the
                // Text's own comment). Centred in the 48dp-tall bar the same way the
                // drag handle and the name both are, so all three read as one row.
                // Extra right padding pushes the button further right (away from the edges).
                GlassSurface(
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(40.dp),
                    hazeState = mapHazeState,
                    onClick = onRefreshLocation,
                    contentDescription = "Refresh location",
                    // Same reason the bar around it dropped its own white override
                    // (see the outer GlassSurface): this nested circle is over the
                    // same theme-following map, and a forced white glyph vanished on
                    // a light one. Inherits GlassSurface's onSurface default.
                    // Nested inside the bar's own already-elevated GlassSurface --
                    // see glassEdge's own doc for why a nested panel skips the second
                    // shadow (GlassChrome.kt).
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


/**
 * The pull-down-to-close drag both map sheets use on their top bar: the sheet follows the finger
 * ([dragPx]), and on release a pull of 96dp or more closes it while anything less springs back.
 * Distance, not velocity: a fixed 96dp pull is a close enough stand-in for "clearly meant to close".
 */
internal fun Modifier.pullDownToDismiss(
    dragPx: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    scope: kotlinx.coroutines.CoroutineScope,
    density: androidx.compose.ui.unit.Density,
    spring: androidx.compose.animation.core.AnimationSpec<Float>,
    close: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    detectVerticalDragGestures(
        onVerticalDrag = { change, amount ->
            change.consume()
            scope.launch { dragPx.snapTo((dragPx.value + amount).coerceAtLeast(0f)) }
        },
        onDragEnd = {
            val thresholdPx = with(density) { 96.dp.toPx() }
            if (dragPx.value > thresholdPx) close() else scope.launch { dragPx.animateTo(0f, spring) }
        },
        onDragCancel = { scope.launch { dragPx.animateTo(0f, spring) } },
    )
}
