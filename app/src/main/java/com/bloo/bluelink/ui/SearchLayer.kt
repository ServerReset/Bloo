package com.bloo.bluelink.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.max
import com.bloo.bluelink.data.platform
import com.bloo.bluelink.data.searchBubblePosition
import com.bloo.bluelink.data.setSearchBubblePosition

/** Which shape the search control is in: a bubble, a pill, or the full bar. */
internal enum class SearchForm { BUBBLE, PILL, BAR }

/**
 * THE search element, hoisted to the app root so it is a single object that
 * OUTLIVES the screen transition.
 *
 * It used to be instantiated separately by Settings, the garage and the cover,
 * which meant three of them: leaving Settings destroyed one and created
 * another, so the only transition available was a cross-fade between two
 * different things that happened to look alike. Hosted here, above the
 * screen-switching AnimatedContent, there is exactly one -- so moving between
 * the garage and Settings genuinely morphs it, a circle in the corner growing
 * into the bar across the bottom and back, because it is the same Surface the
 * whole way.
 *
 * The shape follows the screen, not the user:
 *  - Settings: the full bar, centred at the bottom. This is a screen you came
 *    to in order to find something.
 *  - Garage: a small circle in the bottom-right corner, icon only. Search is
 *    available, not advertised; the car is what you came to look at.
 *  - Cover: the same circle, smaller, and DRAGGABLE -- on a one-inch screen
 *    anything parked in a corner is covering something, and which corner is
 *    free depends on the tile you are on, so the answer has to be the user's.
 *  - Open, anywhere: the bar, because at that point it is a text field.
 */
@Composable
internal fun SearchLayer(
    vm: AppViewModel,
    state: State<UiState>,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
    onSettings: Boolean,
    /** Reports whether the search UI is open (pill/panel showing) -- the
     *  ambient blurred aurora behind it pauses while this is true, so the
     *  keyboard/typing frames don't contend with a full-screen blur redraw. */
    onOpenChanged: ((Boolean) -> Unit)? = null,
    /** Screens.kt's own shared instance -- the same one whichever of
     *  GarageScreen/SettingsScreen is actually showing underneath marks its own
     *  content with. Null (the old, silent default here) meant the bar/panel's own
     *  glass fill had no real blur source to ask for regardless of which screen was
     *  showing, so it fell back to a flat, more opaque tint that visibly didn't
     *  match every other piece of glass chrome in the app -- reported directly. */
    hazeState: HazeState? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var focused by rememberSaveable { mutableStateOf(false) }
    val open = focused || query.isNotEmpty()
    // Where the bubble was dragged, in dp from the top-left (NaN = never, so it rests in its corner).
    // Saved so it survives rotation. mutableStateOf, not mutableFloatStateOf: rememberSaveable's
    // guaranteed Saver path; this changes twice a gesture, so the boxing doesn't matter.
    @Suppress("AutoboxingStateCreation")
    var dragX by rememberSaveable { mutableStateOf(Float.NaN) }
    // Normal (phone) layout only: which of three docks the bubble sits in, and how fast the last drag move was.
    var dockName by rememberSaveable { mutableStateOf(SearchDock.RIGHT.name) }
    val dock = SearchDock.valueOf(dockName)
    var lastDx by remember { mutableFloatStateOf(0f) }
    // True only between finger-down and finger-up on the bubble. The position
    // animation is BYPASSED while it is true -- see the spec choice below.
    var dragging by remember { mutableStateOf(false) }
    // The app's own tuned vocabulary (Haptics.kt), not the generic platform
    // LocalHapticFeedback this used to reach for -- search is one of the most
    // prominent, most-animated surfaces in the app (it morphs shape, position AND
    // opens a whole panel) and was the one major interaction still running on a
    // borrowed system-default feel instead of the app's own composed effects
    // everything else (pebbles, toggles, buttons) uses.
    val haptics = LocalHaptics.current

    BackHandler(enabled = open) { query = ""; focused = false }
    // Say when the open-state flips, so the aurora behind this layer can
    // pause while the panel is up (see AuroraBackground's `paused`).
    SideEffect { onOpenChanged?.invoke(open) }
    // A click when it opens, a tick when it closes -- the same asymmetry
    // PebbleShell's own header tap uses (expand is the weightier confirm; collapse
    // is the lighter step), so search reads as one more instance of the app's
    // single expand/collapse feel rather than its own separate gesture language.
    // The morph is the visual half of a state change the user just caused; the
    // haptic is the half they feel, and it lands on the frame the shape starts
    // moving rather than when it arrives, so the gesture reads as having been
    // received immediately.
    // Armed only after the first composition: a LaunchedEffect keyed on a
    // boolean also runs when that boolean is simply born false, so without
    // this the app buzzes once on launch, for nothing happening.
    var hapticArmed by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (hapticArmed) {
            if (open) haptics?.click() else haptics?.tick()
        }
        hapticArmed = true
    }
    // Drop any stale AI answer once the box is cleared, and forget the last
    // submission with it -- otherwise reopening search shows the previous
    // question's answer under an empty field.
    LaunchedEffect(query.isBlank()) {
        if (query.isBlank()) { vm.clearAiReply(); submitted = "" }
    }

    // IME/nav observation, hoisted OUT of the BoxWithConstraints lambda
    // below. The insets API works by snapshot reads: each read site is one
    // subscription, and the reads happen ONCE per SearchLayer recomposition
    // here instead of once per re-run of the BoxWithConstraints lambda
    // (whose scope re-runs on every relevant state change a keystroke
    // produces). One reader slot, one subscriber, and the box only re-runs
    // when the value it actually drew from changes -- the panel re-measures
    // when the keyboard crosses the open/closed threshold, not on every
    // keystroke tick. (The older claim that inline reads accumulate N
    // WINDOW LISTENERS per keystroke no longer holds with the modern
    // siteless insets API, but the hoist is exactly right for the same
    // reason: the inner lambda runs for unrelated recompositions, and
    // subscribing to IME state inside it feeds those recompositions with
    // fake insets changes every time any of them happens.)
    val keyboardUp = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 80.dp
    val bottomInset = WindowInsets.navigationBars.union(WindowInsets.ime)
        .asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // With the keyboard up, the panel and the bar together are competing
        // for the sliver of screen that is left -- on a phone that is a couple
        // of hundred dp, not the 360 the panel would otherwise take. Measure
        // what is actually free rather than guessing: the panel gets what
        // remains above the bar, minus a margin so it never looks wedged.
        // (The heavy cost that used to make this read as "laggy while typing"
        // -- the blurred aurora redrawing underneath every IME frame -- is
        // handled at the source: AuroraBackground's `paused`, which the root
        // drives from this layer's `onOpenChanged`.)
        val edge = 16.dp
        val bubble = 52.dp
        val barW = minOf(maxWidth - edge * 2, 640.dp)
        val barH = 52.dp
        val freeAbovePill = (maxHeight - bottomInset - barH - edge * 2 - 24.dp).coerceAtLeast(96.dp)
        // A medium pill: wide enough for the icon and the word with room
        // around them, and nowhere near the bar's span.
        val pillW = minOf(168.dp, barW)
        val form = when {
            open -> SearchForm.BAR
            onSettings || dock == SearchDock.CENTER -> SearchForm.PILL
            else -> SearchForm.BUBBLE
        }
        // Publish where the search element is so the toast stack can clear it: a corner dock
        // (beside the bubble) or CENTER (above the pill OR the open bottom bar). While the
        // search is HIDDEN, null. Crucially the OPEN bar publishes CENTER too -- it used to
        // publish null here, so an open bottom search bar got no clearance at all and a toast
        // landed right over it. The bar is a bottom-centered element just like the pill, so
        // CENTER is the correct clearance for both. SideEffect, not a bare write: this runs
        // after composition, and the registry is state other composables read.
        val floatingRegistry = LocalFloatingRegistry.current
        SideEffect {
            floatingRegistry.searchDock = if (open) SearchDock.CENTER else dock
        }
        DisposableEffect(Unit) { onDispose { floatingRegistry.searchDock = null } }

        // Resting corner for the bubble, and the drag bounds that keep it on
        // screen no matter where it was left.
        val minX = edge
        val maxX = (maxWidth - bubble - edge).coerceAtLeast(edge)
        val minY = edge
        val maxY = (maxHeight - bubble - edge - bottomInset).coerceAtLeast(edge)
        val restX = maxX
        val restY = maxY
        LaunchedEffect(Unit) {
            vm.searchBubblePosition()?.let { (xFrac, _) -> dockName = SearchDock.fromFrac(xFrac).name }
        }
        val bubbleX = when {
            dragging && !dragX.isNaN() -> dragX.dp.coerceIn(minX, maxX)
            dock == SearchDock.LEFT -> minX
            else -> restX
        }
        val bubbleY = restY

        val targetW = when (form) {
            SearchForm.BAR -> barW
            SearchForm.PILL -> pillW
            SearchForm.BUBBLE -> bubble
        }
        val targetH = if (form == SearchForm.BUBBLE) bubble else barH
        val targetX = when {
            form == SearchForm.BUBBLE -> bubbleX
            // A pill being carried follows the finger; at rest it is centred.
            dragging && !dragX.isNaN() -> dragX.dp.coerceIn(minX, (maxWidth - targetW - edge).coerceAtLeast(minX))
            else -> (maxWidth - targetW) / 2
        }
        val targetY = if (form == SearchForm.BUBBLE) bubbleY else maxHeight - barH - edge - bottomInset

        // Two springs: SIZE overshoots a little so the pill arrives with some give, POSITION stays
        // critically damped (one bouncy spring made the whole element slide past its rest and come
        // back). Width and height share theirs so the shape stays coherent. The numbers are the shared
        // PebbleBounceDamping/PebbleBounceStiffness tokens, so this is the same spring as the pebbles.
        val sizeSpec = lowPowerAwareSpring<Dp>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        // A spring is right for the morph and WRONG for a drag: routing the
        // finger's position through one meant the bubble trailed behind the
        // touch for the whole gesture and then coasted past it on release --
        // it felt like dragging something on elastic, not like moving it.
        // While the finger is down the position snaps (1:1 with touch); the
        // moment it lifts, the spring is back to carry the settle.
        val posSpec = if (dragging) snap<Dp>() else {
            // Bouncier than a critically-damped snap, and deliberately so: this is
            // what plays when the bubble snaps to an edge on release, and a snap
            // with no overshoot reads as the value being SET, not as the bubble
            // landing somewhere. A bit of give past the edge and back is what
            // makes it read as physical contact -- it bounced off the edge --
            // rather than a UI correcting a number. Same shared bounce spring as
            // `sizeSpec` above, for the same "one system" reason.
            lowPowerAwareSpring<Dp>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        }
        // key(compact) so entering or leaving flip mode RESTARTS these
        // animations at their new target rather than animating to it. The cover
        // screen's resting corner is a different point in a differently-sized
        // box, so without this the ball crawled across the whole screen from
        // wherever the other layout had left it -- a long slide that had
        // nothing to do with anything the user just did. Restarted, it is
        // already home when the mode appears, and the entrance spring inside
        // SearchPill is what you see instead.
        val w = animateDpAsState(targetW, sizeSpec, label = "searchW").value
        val h = animateDpAsState(targetH, sizeSpec, label = "searchH").value
        val x = animateDpAsState(targetX, posSpec, label = "searchX").value
        val y = animateDpAsState(targetY, posSpec, label = "searchY").value

        // Dismiss scrim. Below the pill in this Box, so it never eats its taps. Same
        // effects spec collapseEnter/collapseExit use for every pebble's own fade,
        // not its own hand-picked tween durations (180ms/140ms) -- one fade curve
        // for "something is fading" across the whole app, not a slightly different
        // one wherever a fade happened to get added separately.
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .noRippleClickable { query = ""; focused = false },
            )
        }

        // Results / suggestions, anchored to the bottom rather than to the
        // pill: the pill is always at the bottom centre while open, so this
        // never has to chase a bubble around the screen.
        AnimatedVisibility(
            visible = open,
            enter = expandEnter(Alignment.Bottom),
            exit = expandExit(Alignment.Bottom),
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = barH + edge + bottomInset + 10.dp),
        ) {
            val panelShape = ExtraLargeShape
            // GlassSurface (GlassChrome.kt): the one shared fill/rim/shadow, replacing
            // this panel's own one-off alpha and its own separately-hand-rolled flat
            // BorderStroke rim (yet another divergent one, next to appGlassRim's shared
            // gradient rim) -- no more per-site variations.
            GlassSurface(
                shape = panelShape,
                modifier = Modifier.width(barW),
                hazeState = hazeState,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = minOf(360.dp, freeAbovePill))
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (query.isNotBlank()) {
                        // Fewer results while the keyboard is up. This is what
                        // the new ranking buys: cutting to the top few is only
                        // honest when the top few really are the best ones, and
                        // a scrollable list you cannot see the bottom of is
                        // worse than a short list you can.
                        SettingsSearchResults(
                            query, submitted, vm, state.value, appearance, notif,
                            // The cover screen with the keyboard up is the
                            // hard case: a ~260dp square, most of it keyboard.
                            // Two results that are fully visible beat six you
                            // have to scroll blind through.
                            limit = when {
                                keyboardUp -> 4
                                else -> Int.MAX_VALUE
                            },
                            hazeState = hazeState,
                        )
                    } else {
                        SearchSuggestions(state.value, compact = keyboardUp) { picked ->
                            query = picked
                            submitted = picked
                        }
                    }
                }
            }
        }

        SearchPill(
            query = query,
            focused = focused,
            form = form,
            width = w,
            height = h,
            onQueryChange = { query = it },
            onFocusChange = { focused = it },
            onSubmit = { submitted = query },
            // Dragging only exists for the bubble. A bar spans the screen --
            // there is nowhere to move it to -- and while it is a text field
            // a drag would fight the keyboard and the panel above it.
            // Docked into a camera band, there is nowhere to drag it TO --
            // the whole point of the fixed spot is that it's the one place
            // guaranteed not to cover something else.
            onDrag = if (form != SearchForm.BAR) {
                { dx, _ ->
                    dragX = ((if (dragX.isNaN()) x else dragX.dp) + dx).coerceIn(minX, maxX).value
                    lastDx = dx.value
                }
            } else null,
            onDragStart = {
                dragging = true
                dragX = Float.NaN
                lastDx = 0f
            },
            onDragEnd = {
                // Land in a dock: thrown the way it was flung, or dropped where it was let go.
                val widthNow = if (form == SearchForm.PILL) pillW else bubble
                val center = (if (dragX.isNaN()) x else dragX.dp) + widthNow / 2
                val next = SearchDock.landing((center / maxWidth).coerceIn(0f, 1f), lastDx, dock)
                dockName = next.name
                dragX = Float.NaN
                vm.setSearchBubblePosition(next.xFrac, 1f)
                dragging = false
                // click(), not the generic platform feedback this used to fire --
                // matches the edge-snap spring's own "bounced off the edge" physical
                // read (see posSpec's doc above) with a real confirm-weight landing
                // instead of a borrowed system default.
                haptics?.click()
            },
            modifier = Modifier.align(Alignment.TopStart).offset(x = x, y = y),
            hazeState = hazeState,
        )
    }
}
