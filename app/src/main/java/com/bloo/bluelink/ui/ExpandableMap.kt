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
 * One entry in the full-screen map's bottom toolbar -- an icon, a label and an
 * action, nothing else. This IS the "framework" for future map features (a traffic
 * layer, turn-by-turn directions, nearby search, a saved-places list, sharing a live
 * location...): each new capability is just another [MapFeature] appended to the list
 * [CarMapFullScreenDialog] builds, never a change to the row itself, the button
 * styling, or the layout around it. Two real ones exist today -- recentre and open in
 * the system Maps app -- and every future one is exactly this same shape.
 */
internal data class MapFeature(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The map sheet's bottom toolbar: one [MorphButton] pill per [MapFeature], sharing an
 * [ExpressiveButtonRow] -- the same connected-group framework the lock/horn cluster
 * and every other multi-button row in the app uses, so pressing one pill takes width
 * FROM its neighbour (both keep their own independent pill shape; only the WIDTH
 * trades) instead of each growing independently into free space. `equalWidths = true`
 * keeps both pills the same width regardless, matching the even split this row always
 * had. `wrap = true` (not a scrolling Row): a future feature list long enough to
 * overflow one line wraps to a second instead of needing its own horizontal-scroll
 * affordance.
 *
 * Icon AND label, via the shared [MorphActionButton] -- these two buttons (Expand, Open
 * in Maps) used to be icon-only with no visible label or border, reported directly as
 * wanting names and outlines like the app's other buttons. The outlined-tonal treatment
 * they were given here is now THE standard action-button look, so it lives in Morph.kt
 * rather than being re-typed inline here.
 */
@Composable
internal fun MapFeatureRow(
    features: List<MapFeature>,
    modifier: Modifier = Modifier,
    /** False for three-plus features: rather than wrapping a lone last button onto
     *  its own full-width second line (reported directly from a screenshot, once
     *  "Chargers" became a third feature here), the whole row compacts to icon-only
     *  buttons instead -- see [ExpressiveButtonRow]'s own `wrap` doc. Still true (the
     *  default) for the two-feature rows this always fit fine. */
    wrap: Boolean = true,
    /** [Alignment.CenterHorizontally] when the row can compact to icon-only glyphs (three
     *  or more features): once those glyphs are capped at their own small size (see
     *  [ExpressiveButtonGroup]'s equalWidths doc) rather than stretched to fill the row, a
     *  left-packed cluster reads as broken/half-empty -- centering it in the full row width
     *  reads as one intentional, compact strip instead. Two-feature rows never compact, so
     *  their default `Start` never differs from Center in practice; left as `Start` (the
     *  group's own default) rather than changed for every existing caller. */
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    ExpressiveButtonRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        spacing = 10.dp,
        equalWidths = true,
        wrap = wrap,
        horizontalAlignment = horizontalAlignment,
    ) {
        features.forEach { feature ->
            val source = remember { MutableInteractionSource() }
            SafeExpansiveButton(
                interactionSource = source,
                enabled = feature.enabled,
            ) {
                MorphActionButton(
                    label = feature.label,
                    icon = feature.icon,
                    onClick = feature.onClick,
                    interactionSource = source,
                    enabled = feature.enabled,
                )
            }
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
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        CarMapSheetBody(
            location, vehicleName, deviceLocation,
            mapState = rememberCarMapState(),
            originBounds = originBounds,
            hazeState = null,
            onRefreshLocation = onRefreshLocation,
            refreshing = refreshing,
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
    dragModifier: Modifier,
    modifier: Modifier = Modifier,
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
        shape = RoundedCornerShape(24.dp),
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
                    modifier = Modifier.align(Alignment.CenterEnd).size(40.dp).padding(end = 12.dp),
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

@Composable
internal fun ExpandableMapLayer(
    isExpanded: Boolean,
    originBounds: Rect,
    location: GeoLocation,
    vehicleName: String,
    deviceLocation: GeoLocation?,
    mapState: CarMapState,
    hazeState: HazeState?,
    onRefreshLocation: () -> Unit,
    /** See [MapTopBar]'s own doc -- the real command-pending flag, not a guess. */
    refreshing: Boolean = false,
    onDismiss: () -> Unit,
    /** The "Chargers" [MapFeature] -- see [ChargerFinder]'s own doc for the whole
     *  group. All default to "off"/"nothing loaded" so every OTHER existing caller's
     *  behaviour is unchanged; GarageScreen's own call site is the only one that
     *  wires these to real state today. */
    chargersVisible: Boolean = false,
    chargersLoading: Boolean = false,
    /** Set only on a genuine fetch failure (network/auth/parse) -- see
     *  [com.bloo.bluelink.data.ChargerApi.nearby]'s own doc for why that's kept
     *  distinct from [chargers] simply being empty. */
    chargersError: String? = null,
    chargers: List<ChargerStation> = emptyList(),
    chargerFilters: ChargerFilters = ChargerFilters(),
    onToggleChargersVisible: () -> Unit = {},
    onRetryChargers: () -> Unit = {},
    onSetChargerMinKw: (Int) -> Unit = {},
    onToggleChargerNetwork: (String) -> Unit = {},
    onSetChargerApiKey: (String?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val mapHazeState = remember { HazeState() }

    // Animate from pebble size to full screen. Always starts at 0f: GarageScreen
    // only ever composes this with isExpanded=true (it stops rendering the whole
    // composable on close, rather than passing false) -- so `isExpanded` never
    // actually changes across this composable's lifetime, and initializing the
    // Animatable from it (`Animatable(if (isExpanded) 1f else 0f)`, the previous
    // version here) meant it started AT 1f on every mount, skipping the pop-out-
    // of-the-pebble open animation entirely. LaunchedEffect(Unit), not
    // keyed on isExpanded, for the same reason: that key never changes, so this
    // still runs exactly once per mount -- i.e. once per expand -- which is
    // exactly the intent.
    val expandFraction = remember { Animatable(0f) }
    // Read here, at composable scope, not inside the LaunchedEffect below --
    // lowPowerAwareSpring is itself @Composable, so it can't be called from a
    // suspend lambda. NoBouncy (critically damped, no overshoot) -- reported
    // directly as wanting the sheet to pull up "like a normal card... nice and
    // smooth", not with the springy pop this used to have.
    val openSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(Unit) {
        expandFraction.animateTo(1f, animationSpec = openSpring)
    }

    // How far the drag handle has actually been pulled down, in px -- the SAME
    // real drag-to-dismiss CarMapSheetBody's own handle uses (its own `dragPx`;
    // see that doc). The handle's own pointerInput below is the only thing that
    // feeds this: the map area already owns pan/pinch of its own, and a whole-
    // sheet drag-to-dismiss would fight it on every downward pan. Reported
    // directly as wanting the handle to be "genuinely a pull-down" -- the
    // previous version here ignored the drag delta entirely and closed on ANY
    // touch-up on the handle, including a tiny accidental tap.
    val dragPx = remember { Animatable(0f) }
    // Read here, at composable scope, not inline inside the scope.launch{} blocks below --
    // lowPowerAwareSpring is itself @Composable (it reads battery-saver state), so it can't
    // be called from inside a suspend lambda. Captured once and referenced from both.
    val dragSpring = lowPowerAwareSpring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium)
    // Same reason as dragSpring above -- read here, not inside the scope.launch{} in close().
    val closeSpring = lowPowerAwareSpring<Float>(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)
    var closing by remember { mutableStateOf(false) }
    // Which charger pin (if any) the user just tapped -- see the info card below.
    // Cleared whenever the layer itself is hidden so reopening it never shows a
    // stale popup for a pin that isn't even drawn any more.
    var selectedCharger by remember { mutableStateOf<ChargerStation?>(null) }
    LaunchedEffect(chargersVisible) { if (!chargersVisible) selectedCharger = null }
    // Also cleared the moment the selected pin itself stops matching the active
    // filters (or drops out of a fresh fetch entirely) -- its pin is no longer
    // drawn on the map at that point (see the `chargers.filter { it.matches(...) }`
    // passed to CarMap below), so leaving the info card up would keep describing a
    // charger the user can no longer even see.
    LaunchedEffect(chargerFilters, chargers) {
        selectedCharger?.let { sel -> if (sel !in chargers || !sel.matches(chargerFilters)) selectedCharger = null }
    }
    // Memoized, not recomputed inline at the CarMap call site below: this composable
    // also recomposes on drag/pan/expand-animation state that has nothing to do with
    // chargers, chargersVisible or chargerFilters, and re-filtering the whole list on
    // every one of those frames would be pure waste -- the same reasoning ChargerFilterBar
    // (below) already applies to its own equivalent filter/count.
    val visibleChargers = remember(chargersVisible, chargers, chargerFilters) {
        if (chargersVisible) chargers.filter { it.matches(chargerFilters) } else emptyList()
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            // Both play at once so a mid-drag dismiss doesn't visibly snap dragPx
            // back to 0 before the sheet itself starts shrinking away.
            val a = scope.launch { expandFraction.animateTo(0f, animationSpec = closeSpring) }
            val b = scope.launch { dragPx.animateTo(0f, animationSpec = closeSpring) }
            a.join(); b.join()
            onDismiss()
        }
    }

    // Captured at the SHEET's own real layout -- 85% height, bottom-anchored,
    // full width -- not the whole screen. This is the "full" target the morph
    // below scales the pebble up to.
    var sheetBounds by remember { mutableStateOf<Rect?>(null) }

    // Back handler for closing
    BackHandler(enabled = isExpanded) { close() }

    Box(Modifier.fillMaxSize()) {
        // Scrim background (dims/blurs content behind) -- covers the WHOLE
        // screen, not just the sheet: this is what shows through the top 15%
        // strip the sheet itself doesn't reach.
        if (isExpanded) {
            ScrimBlur(hazeState = hazeState, progress = { expandFraction.value })
            Box(Modifier.fillMaxSize().noRippleClickable { close() })
        }

        // The sheet: bottom-anchored, full-width, 85% of the screen's height --
        // its OWN real layout size (not a graphicsLayer trick), so the morph
        // below scales toward a target that's actually this size instead of
        // the whole screen. Everything belonging to "the sheet" (map, name
        // pill, buttons, drag handle) lives inside it now, so their alignments
        // anchor to the SHEET's own edges, not the screen's.
        //
        // Two graphicsLayer transforms, on two nested boxes, not one -- reported
        // directly as the open animation being "janky... hits a hard limit on
        // the outside of how big it can go". That was this OUTER box and the
        // pebble-to-full scale morph both being driven by the same clamped `t`
        // on a single layer: a bouncy spring overshoots past its target and
        // then dips back below it before settling (that dip is the actual
        // bounce), but clamping scale to [0, 1] hides the overshoot half of
        // that motion entirely while still fully showing the undershoot half --
        // so it read as "grows to full size, hits a wall, visibly shrinks back
        // a little, grows again to settle". Splitting it the way
        // CarMapSheetBody's own (working) sheet always has fixes this: THIS
        // outer box carries the sheet's own entrance -- an UNCLAMPED slide up
        // from fully below-screen, free to genuinely overshoot past rest and
        // settle back, which is what actually sells "pop" -- with no
        // `clipToBounds()`, so the whole sheet (its rounded-corner shape
        // included) moves as one rigid unit with nothing to hit a wall
        // against. The pebble-to-full SCALE morph moves to a separate INNER
        // box below, still clamped to [0, 1] (scale over 100% would still
        // need somewhere to go), but now decoupled from this entrance --
        // the two motions layer together instead of fighting over one value.
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
            // The map itself: fills the whole sheet, clamped pebble-to-full morph,
            // clipped to its own bounds so the GROWING-from-pebble content never
            // spills past the sheet's edges on the way up -- independent of the
            // outer sheet's own entrance bounce above.
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
                    // Filtered here, not passed raw: CarMap draws exactly what's handed to
                    // it and has no notion of ChargerFilters of its own -- keeping that
                    // narrowing at this call site is what lets every OTHER caller (the
                    // compact pebble map, the cover screen) stay on CarMap's plain
                    // empty-list default with nothing to opt out of.
                    chargers = visibleChargers,
                    onChargerClick = { selectedCharger = it },
                )
            }

            // One consolidated top bar -- name, drag handle, and refresh, all inside
            // one shared floating pill background instead of three separate glass
            // pieces (see MapTopBar's own doc). Positioned close to the sheet's own
            // top edge (16dp) rather than the old name pill's 40dp -- the drag
            // handle's nub now lives INSIDE this same bar instead of needing its
            // own 40dp-tall floating strip above it.
            if (isExpanded && expandFraction.value > 0.1f) {
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
                                // to close this". Same threshold CarMapSheetBody's
                                // own handle uses.
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
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .graphicsLayer { alpha = expandFraction.value.coerceIn(0f, 1f) },
                )
            }

            // Bottom buttons (appear when expanded), plus -- above them, in the same
            // bottom-anchored column -- the charger info popup and/or filter bar,
            // whichever are relevant right now. Stacking them in one Column rather
            // than each with its own hand-placed padding is what lets the filter
            // bar's own height (it wraps, so it's taller with a network row than
            // without) push the buttons down naturally instead of the two
            // overlapping whenever the bar grows.
            if (isExpanded && expandFraction.value > 0.1f) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        // This whole app runs edge-to-edge (MainActivity's enableEdgeToEdge()),
                        // which turns off the manifest's own adjustResize for every surface --
                        // each one has to lift itself above the keyboard explicitly now. This
                        // column is the one that actually needs it: the charger API key field
                        // inside ChargerFilterBar sits right where the keyboard covers it,
                        // reported directly from a screenshot.
                        .imePadding()
                        .graphicsLayer { alpha = expandFraction.value.coerceIn(0f, 1f) },
                ) {
                    // lastSelectedCharger, not selectedCharger directly, inside the PopVisible
                    // content below: PopVisible's exit animation still has to render SOMETHING
                    // while it fades/shrinks out, and selectedCharger itself goes null the
                    // instant it's dismissed -- rendering that null directly would blank the
                    // card the moment the exit starts instead of letting it visibly fade away.
                    var lastSelectedCharger by remember { mutableStateOf<ChargerStation?>(null) }
                    LaunchedEffect(selectedCharger) {
                        selectedCharger?.let { lastSelectedCharger = it }
                    }
                    PopVisible(
                        visible = selectedCharger != null,
                        sizeAnimated = true,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        lastSelectedCharger?.let { charger ->
                            ChargerInfoCard(
                                charger = charger,
                                mapHazeState = mapHazeState,
                                onDismiss = { selectedCharger = null },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    PopVisible(
                        visible = chargersVisible,
                        sizeAnimated = true,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        ChargerFilterBar(
                            chargers = chargers,
                            filters = chargerFilters,
                            loading = chargersLoading,
                            error = chargersError,
                            mapHazeState = mapHazeState,
                            onSetMinKw = onSetChargerMinKw,
                            onToggleNetwork = onToggleChargerNetwork,
                            onRetry = onRetryChargers,
                            onSetApiKey = onSetChargerApiKey,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    MapFeatureRow(
                        features = listOf(
                            MapFeature(Icons.Filled.MyLocation, "Recentre") { mapState.recenter() },
                            MapFeature(Icons.Filled.EvStation, "Chargers") { onToggleChargersVisible() },
                            MapFeature(Icons.Filled.Map, "Open in Maps") {
                                openInExternalMaps(context, location, vehicleName)
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        wrap = false,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                }
            }
        }
    }
}

