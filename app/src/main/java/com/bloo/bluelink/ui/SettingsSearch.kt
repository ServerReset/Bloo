@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import com.bloo.uicommon.dropShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max



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
    state: UiState,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
    onSettings: Boolean,
    compact: Boolean,
    /** Reports whether the search UI is open (pill/panel showing) -- the
     *  ambient blurred aurora behind it pauses while this is true, so the
     *  keyboard/typing frames don't contend with a full-screen blur redraw. */
    onOpenChanged: ((Boolean) -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var focused by rememberSaveable { mutableStateOf(false) }
    val open = focused || query.isNotEmpty()
    // Where the user has dragged the bubble, in dp from the top-left. NaN =
    // never dragged, so it rests in its default corner. Saved, because having
    // to re-park it after every rotation would make dragging it pointless.
    // mutableStateOf, not mutableFloatStateOf: rememberSaveable needs a Saver
    // for whatever it is handed, and the boxed-Float one is the guaranteed
    // path. This changes twice a gesture, not twice a frame -- the boxing is
    // not worth a runtime "no Saver found" on some Compose version.
    var dragX by rememberSaveable { mutableStateOf(Float.NaN) }
    var dragY by rememberSaveable { mutableStateOf(Float.NaN) }
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
        val edge = if (compact) 8.dp else 16.dp
        // On the cover, a camera band beside the island (see coverCutoutBand)
        // is real, unoccluded space with nothing else fixed in it once the
        // name has taken its share -- a better home for search than a corner
        // it would otherwise float over. Docked there it's a fixed CoverBand-
        // SearchDock circle, matching the reservation CompactGarage's own
        // band Row leaves for it; undocked (no band, or not compact) it's the
        // free-floating, draggable circle this always was. "Fixed when the
        // space is there, floating when it isn't."
        val band = if (compact) coverCutoutBand() else null
        val bubble = if (band != null) CoverBandSearchDock else if (compact) 40.dp else 52.dp
        val barW = minOf(maxWidth - edge * 2, 640.dp)
        val barH = 52.dp
        val freeAbovePill = (maxHeight - bottomInset - barH - edge * 2 - 24.dp).coerceAtLeast(96.dp)
        // A medium pill: wide enough for the icon and the word with room
        // around them, and nowhere near the bar's span.
        val pillW = minOf(if (compact) 132.dp else 168.dp, barW)
        val form = when {
            open -> SearchForm.BAR
            onSettings -> SearchForm.PILL
            else -> SearchForm.BUBBLE
        }

        // Resting corner for the bubble, and the drag bounds that keep it on
        // screen no matter where it was left.
        val minX = edge
        val maxX = (maxWidth - bubble - edge).coerceAtLeast(edge)
        val minY = edge
        val maxY = (maxHeight - bubble - edge - bottomInset).coerceAtLeast(edge)
        val restX = maxX
        val restY = maxY
        // This Box sits inside BlooApp's own `.padding(padding)` (the
        // Scaffold's safeDrawing content padding), while coverCutoutBand()
        // reports coordinates in the WINDOW's own space -- the same gap
        // CompactGarage's band Row doesn't have to close because it draws
        // full-bleed, ignoring that padding entirely (see its own comment).
        // Subtracting the same inset back out here is what puts this bubble
        // in the same coordinate space as that Row, so the two agree on
        // where the band actually is.
        val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
        val insetLeftDp = safeDrawing.calculateLeftPadding(LocalLayoutDirection.current).value
        val insetTopDp = safeDrawing.calculateTopPadding().value
        // Flush against whichever end of the band the camera touches --
        // exactly the edge CompactGarage's own Arrangement groups its
        // dockSpacer reservation against -- and vertically centred in it.
        val dockedX = band?.let {
            (if (it.nearCameraAtEnd) it.xDp + it.widthDp - 6f - CoverBandSearchDock.value
            else it.xDp + 6f) - insetLeftDp
        }
        val dockedY = band?.let { (it.yDp + (it.heightDp - CoverBandSearchDock.value) / 2f) - insetTopDp }
        // Restore the user's last-parked spot from durable storage, once -- if this
        // composition doesn't already have one in memory. rememberSaveable's dragX/
        // dragY survive a LIVE session (rotation, a mode switch while the process
        // stays alive), but not a killed-and-restarted process, which is routine for
        // a flip phone that's been closed a while: reported as "drag it, leave flip
        // mode, come back -- it's not where I put it", which a purely in-memory Saver
        // can't fix on its own. Stored and restored as FRACTIONS of the drag range
        // (see SettingsStore's own doc), not raw dp, so this stays correct even if
        // minX/maxX/minY/maxY come out slightly different than when it was saved.
        // Plain arithmetic rather than androidx.compose.ui.unit.lerp -- two
        // multiplies don't need an import.
        LaunchedEffect(Unit) {
            if (dragX.isNaN()) {
                vm.searchBubblePosition()?.let { (xFrac, yFrac) ->
                    dragX = (minX + (maxX - minX) * xFrac).value
                    dragY = (minY + (maxY - minY) * yFrac).value
                }
            }
        }
        // dragX/dragY are ONLY consulted in compact (flip) mode. They are one
        // shared pair of saved floats -- dragging is only possible in flip
        // mode, but before this gate the normal-mode bubble read the exact
        // same state, so once the flip bubble had ever been moved, switching
        // to the normal phone screen showed it wherever flip mode had left it
        // instead of the fixed bottom-right corner. Normal mode now always
        // rests at restX/restY, full stop -- what dragX/dragY hold is purely
        // flip mode's memory, and normal mode has no memory of its own by
        // design (there's nowhere on that screen to remember: one fixed spot
        // is the whole point).
        // Docked beats dragged beats resting: a band, when there's one to dock
        // into, always wins over wherever the bubble was last left -- fixed
        // when the space is there, floating (and rememberable) only when it
        // isn't.
        val bubbleX = when {
            dockedX != null -> dockedX.dp
            compact && !dragX.isNaN() -> dragX.dp.coerceIn(minX, maxX)
            else -> restX
        }
        val bubbleY = when {
            dockedY != null -> dockedY.dp
            compact && !dragY.isNaN() -> dragY.dp.coerceIn(minY, maxY)
            else -> restY
        }

        val targetW = when (form) {
            SearchForm.BAR -> barW
            SearchForm.PILL -> pillW
            SearchForm.BUBBLE -> bubble
        }
        val targetH = if (form == SearchForm.BUBBLE) bubble else barH
        val targetX = if (form == SearchForm.BUBBLE) bubbleX else (maxWidth - targetW) / 2
        val targetY = if (form == SearchForm.BUBBLE) bubbleY else maxHeight - barH - edge - bottomInset

        // Two springs, not one, and this is the part that makes the resize
        // read well: SIZE gets a little overshoot so the pill arrives with
        // some give, while POSITION stays critically damped. Sharing one
        // bouncy spring meant the whole element slid past its resting place
        // and came back -- the wobble that made growing into the bar look
        // loose rather than deliberate. Width and height still share their
        // spring, so the shape stays coherent while it changes.
        //
        // PebbleBounceDamping/PebbleBounceStiffness, not this element's own
        // hand-tuned numbers -- this used to carry its own separately-picked
        // damping ratios (0.62 here, 0.72 below), close to but not actually the
        // same values the pebble bounce settled on, which is exactly what "not
        // standard across every surface" was pointing at: two controls that both
        // bounce but by measurably different amounts read as two different
        // design systems, not one. Reusing the literal shared tokens is what
        // makes this ACTUALLY the same spring, not just a similar-looking one.
        val sizeSpec = spring<Dp>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
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
            spring<Dp>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        }
        // key(compact) so entering or leaving flip mode RESTARTS these
        // animations at their new target rather than animating to it. The cover
        // screen's resting corner is a different point in a differently-sized
        // box, so without this the ball crawled across the whole screen from
        // wherever the other layout had left it -- a long slide that had
        // nothing to do with anything the user just did. Restarted, it is
        // already home when the mode appears, and the entrance spring inside
        // SearchPill is what you see instead.
        val w = key(compact) { animateDpAsState(targetW, sizeSpec, label = "searchW").value }
        val h = key(compact) { animateDpAsState(targetH, sizeSpec, label = "searchH").value }
        val x = key(compact) { animateDpAsState(targetX, posSpec, label = "searchX").value }
        val y = key(compact) { animateDpAsState(targetY, posSpec, label = "searchY").value }

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
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { query = ""; focused = false },
            )
        }

        // Results / suggestions, anchored to the bottom rather than to the
        // pill: the pill is always at the bottom centre while open, so this
        // never has to chase a bubble around the screen.
        AnimatedVisibility(
            visible = open,
            enter = collapseEnter(Alignment.Bottom),
            exit = collapseExit(Alignment.Bottom),
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = barH + edge + bottomInset + 10.dp),
        ) {
            val panelShape = RoundedCornerShape(if (compact) 20.dp else 28.dp)
            Surface(
                shape = panelShape,
                // Shared default, not its own 0.98 -- see glassContainerAlpha's own
                // doc for why every frosted surface takes the one value now.
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = glassContainerAlpha()),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.width(barW).dropShadow(panelShape, blurRadius = 16.dp, offsetY = 6.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = minOf(if (compact) 180.dp else 360.dp, freeAbovePill))
                        .verticalScroll(rememberScrollState())
                        .padding(if (compact) 10.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                ) {
                    if (query.isNotBlank()) {
                        // Fewer results while the keyboard is up. This is what
                        // the new ranking buys: cutting to the top few is only
                        // honest when the top few really are the best ones, and
                        // a scrollable list you cannot see the bottom of is
                        // worse than a short list you can.
                        SettingsSearchResults(
                            query, submitted, vm, state, appearance, notif,
                            // The cover screen with the keyboard up is the
                            // hard case: a ~260dp square, most of it keyboard.
                            // Two results that are fully visible beat six you
                            // have to scroll blind through.
                            limit = when {
                                compact && keyboardUp -> 2
                                keyboardUp -> 4
                                else -> Int.MAX_VALUE
                            },
                        )
                    } else {
                        SearchSuggestions(state, compact = compact || keyboardUp) { picked ->
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
            compact = compact,
            onQueryChange = { query = it },
            onFocusChange = { focused = it },
            onSubmit = { submitted = query },
            // Dragging only exists for the bubble. A bar spans the screen --
            // there is nowhere to move it to -- and while it is a text field
            // a drag would fight the keyboard and the panel above it.
            // Docked into a camera band, there is nowhere to drag it TO --
            // the whole point of the fixed spot is that it's the one place
            // guaranteed not to cover something else.
            onDrag = if (form == SearchForm.BUBBLE && compact && band == null) {
                { dx, dy ->
                    dragX = ((if (dragX.isNaN()) bubbleX else dragX.dp) + dx).coerceIn(minX, maxX).value
                    dragY = ((if (dragY.isNaN()) bubbleY else dragY.dp) + dy).coerceIn(minY, maxY).value
                }
            } else null,
            onDragStart = { dragging = true },
            onDragEnd = {
                // Snaps to the NEAREST of the four edges, not a fixed corner
                // and not wherever the finger happened to be. A version of
                // this once sprang back to one specific corner on every
                // release, which defeated the entire point of dragging it --
                // this is the middle ground: it can be parked anywhere ALONG
                // an edge, freely, but it never rests out in the open middle
                // of the screen, where a floating circle covers whatever a
                // one-inch display was showing there with nothing to be
                // gained by it sitting exactly there rather than at the edge
                // just past it.
                //
                // Only the axis PERPENDICULAR to the chosen edge moves; the
                // position along that edge is whatever the drag ended at, so
                // "somewhere along the left edge, a third of the way down" is
                // a real resting place this remembers, not just the four
                // corners.
                if (!dragX.isNaN() && !dragY.isNaN()) {
                    val cx = dragX.dp
                    val cy = dragY.dp
                    val toLeft = cx - minX
                    val toRight = maxX - cx
                    val toTop = cy - minY
                    val toBottom = maxY - cy
                    // minOf has no 4-argument overload in the stdlib -- nested
                    // 2-argument calls, not a 4-element list, to avoid an
                    // allocation on every drag release for four numbers.
                    val nearest = minOf(minOf(toLeft, toRight), minOf(toTop, toBottom))
                    when {
                        nearest == toLeft -> dragX = minX.value
                        nearest == toRight -> dragX = maxX.value
                        nearest == toTop -> dragY = minY.value
                        else -> dragY = maxY.value
                    }
                    // Persisted durably (see SettingsStore.setSearchBubblePosition),
                    // not just left in rememberSaveable -- once per gesture release,
                    // not per drag frame. Guarded against a zero-width/height range
                    // (a degenerate tiny screen) rather than dividing by it.
                    val spanX = (maxX - minX).value
                    val spanY = (maxY - minY).value
                    if (spanX > 0f && spanY > 0f) {
                        vm.setSearchBubblePosition(
                            ((dragX - minX.value) / spanX).coerceIn(0f, 1f),
                            ((dragY - minY.value) / spanY).coerceIn(0f, 1f),
                        )
                    }
                }
                dragging = false
                // click(), not the generic platform feedback this used to fire --
                // matches the edge-snap spring's own "bounced off the edge" physical
                // read (see posSpec's doc above) with a real confirm-weight landing
                // instead of a borrowed system default.
                haptics?.click()
            },
            modifier = Modifier.align(Alignment.TopStart).offset(x = x, y = y),
        )
    }
}

/**
 * The pill itself: one Surface at whatever [width]/[height] [SearchLayer] has
 * animated it to, with a glow behind it and its content chosen by [form].
 *
 * Sized by the caller rather than by a width FRACTION of its own parent, which
 * is what it used to do. A fraction cannot express "a circle in that corner"
 * and "a bar across the bottom" as the same element, and it is the sameness
 * that makes the screen-to-screen morph possible at all.
 */
@Composable
internal fun SearchPill(
    query: String,
    focused: Boolean,
    form: SearchForm,
    width: Dp,
    height: Dp,
    compact: Boolean,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onDrag: ((Dp, Dp) -> Unit)?,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val expanded = focused || query.isNotEmpty()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Focus is DRIVEN by `focused`, both ways. It used to only ever be
    // requested, never released: dismissing the bar -- scrim tap, close
    // button, back -- collapsed the pill and left the keyboard standing over
    // it, because nothing ever told the field to let go. Clearing focus here
    // is safe in a way that listening for blur is not (see the note on the
    // text field's modifier): this reacts to the state that OWNS the bar,
    // not to a transient focus event that arrives before the field is ready.
    LaunchedEffect(focused) {
        if (focused) {
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Springs in on first appearance -- and because SearchLayer keys the
    // animations on the layout mode, "first appearance" includes arriving on
    // the cover screen. The ball lands in its corner rather than sliding to it.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    // DampingRatioMediumBouncy (0.5) on BOTH of these compounded badly: they multiply into
    // the same scaleX/scaleY below, so a press landing anywhere near the entrance pop (or
    // just the two overshoots being visually close together on a small, frequently-tapped
    // control) read as noticeably more bounce than either spring alone would suggest --
    // reported as "overly bouncy," and this is the same lesson the pebble bounce work
    // already paid for: 0.5 reads as a lot on a real device, repeatedly, not occasionally.
    // Entrance keeps some spring (it plays once, arriving) but now on the literal shared
    // PebbleBounceDamping/Stiffness tokens rather than its own separately-tuned numbers --
    // an "arrival" pop is the same kind of event a pebble opening is, so it gets the exact
    // same spring, not a lookalike. Press feedback drops nearly all bounce (PebbleCloseDamping,
    // i.e. no overshoot) and stays on its own fast StiffnessHigh -- a frequent, repeated
    // micro-interaction is exactly where extra bounce stops feeling playful and starts
    // feeling like noise, and it no longer compounds with the entrance spring above it.
    val entrance by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.55f,
        animationSpec = spring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness),
        label = "searchEntrance",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = PebbleCloseDamping, stiffness = Spring.StiffnessHigh),
        label = "searchPress",
    )
    // No ambient glow. This used to carry a travelling-hotspot bloom that
    // swept the pill's rim and breathed continuously the entire time the
    // search element was on screen, plus a separate fade-and-shrink once it
    // went idle. Reported as bad-looking and distracting, and it earned
    // that: a permanent light show on a control that is visible almost all
    // the time competes with everything the user is actually looking at. The
    // pill's affordance is now just its filled shape and border below --
    // legible without needing to move to prove it's there.
    Box(
        modifier.size(width, height).graphicsLayer {
            val k = pressScale * entrance
            scaleX = k
            scaleY = k
        },
    ) {
        Surface(
            onClick = { if (!expanded) onFocusChange(true) },
            shape = RoundedCornerShape(50),
            // Nearly opaque on the cover screen. A translucent 40dp circle over
            // a photo hero picks up whatever is behind it and stops looking
            // like a control at all; at this size there is not enough of it for
            // the glass effect to read as glass.
            //
            // The Settings-screen collapsed pill (form == PILL && !expanded) used to
            // get its own opaque tonal container (secondaryContainer/onSecondaryContainer,
            // no border) instead of this standard frosted fill -- reported as an
            // inconsistent, different-looking search control on that one screen.
            // The original reason for the override no longer applies: it was fixed
            // there because a near-transparent surface with a hairline and
            // onSurfaceVariant text read as "indistinguishable from a disabled
            // control", but the standard treatment below already reads onSurface
            // (not the dimmer onSurfaceVariant) with a visible gradient-lit border --
            // the same legibility fix, just applied consistently instead of as a
            // one-screen special case.
            color = scheme.surfaceContainerHighest.copy(
                alpha = if (compact) glassContainerAlpha(0.97f) else glassContainerAlpha(),
            ),
            contentColor = scheme.onSurface,
            tonalElevation = if (expanded) 10.dp else 6.dp,
            border = BorderStroke(
                if (expanded) 1.5.dp else 1.dp,
                // Static. This is an argument to Surface, i.e. COMPOSITION
                // scope -- it used to multiply in glowPulse, which meant every
                // 33ms tick of the glow clock recomposed this whole composable
                // and the text field inside it, thirty times a second, for the
                // entire time the app was open. The moving light belongs in
                // the drawBehind gradients above, where a tick invalidates
                // draw and nothing else; the rim just needs to be lit.
                Brush.verticalGradient(
                    listOf(
                        scheme.primary.copy(alpha = if (expanded) 0.65f else 0.4f),
                        scheme.primary.copy(alpha = 0.05f),
                    ),
                ),
            ),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxSize()
                // The cover screen gets neither the drop shadow nor the glass
                // rim. Both are tuned for a 52dp pill or a full-width bar; on a
                // 40dp circle they are a soft dark halo and a bright outline
                // stacked on a shape barely wider than the two of them, which
                // is what made this read as a smudge rather than a button. The
                // border below plus the glow behind carry it there.
                .then(if (compact) Modifier else Modifier.dropShadow(RoundedCornerShape(50)))
                .then(if (compact) Modifier else Modifier.appGlassRim(RoundedCornerShape(50)))
                .then(
                    if (onDrag != null) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                            ) { change, amount ->
                                change.consume()
                                with(density) { onDrag(amount.x.toDp(), amount.y.toDp()) }
                            }
                        }
                    } else Modifier,
                ),
        ) {
            AnimatedContent(
                targetState = form to expanded,
                transitionSpec = {
                    // Cross-fade only, fast, and NOT delayed.
                    //
                    // This used to scale the content in from 0.9 after a 90ms
                    // hold. Both were wrong for what is happening around it:
                    // the container is already springing to a new size, so a
                    // second scale on the content inside it is two different
                    // rates of growth fighting over the same pixels, and the
                    // delay meant the shape arrived somewhere before its
                    // contents admitted they were moving. The old content
                    // leaving quickly and the new one arriving over the top,
                    // while the shape carries the motion, is the whole effect.
                    fadeIn(tween(140)) togetherWith fadeOut(tween(90))
                },
                label = "searchContentMorph",
            ) { (shape, isOpen) ->
                when {
                    isOpen -> Row(
                        Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
                        Spacer(Modifier.width(if (compact) 6.dp else 10.dp))
                        Box(Modifier.weight(1f)) {
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                                cursorBrush = SolidColor(scheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                // Submitting puts the keyboard away. The answer
                                // to what you just asked appears in the panel
                                // directly above this bar, which is exactly
                                // where the keyboard was covering.
                                keyboardActions = KeyboardActions(onSearch = { onSubmit(); keyboard?.hide() }),
                                // No auto-collapse on blur: onFocusChanged fires
                                // with isFocused = false the instant this field
                                // composes, before the requestFocus above lands,
                                // and that false positive used to close the bar in
                                // the same beat it opened.
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) {
                                        Text(
                                            if (compact) "Search" else "Search settings & car data",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = scheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    inner()
                                },
                            )
                        }
                        MorphIconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onFocusChange(false) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = if (query.isNotEmpty()) "Clear" else "Close",
                            )
                        }
                    }
                    // Closed PILL (Settings): the icon plus the word, centred.
                    shape == SearchForm.PILL -> Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Search", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    }
                    // Closed BUBBLE: the glyph alone. contentDescription is on
                    // the icon rather than the label, since there isn't one.
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search",
                            // 18dp inside a 40dp circle left a ring of empty
                            // surface wider than the glyph; the button read as
                            // a blob with something small in it.
                            modifier = Modifier.size(if (compact) 21.dp else 22.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Example queries shown while the search bar is focused but empty --
 * without these there's no way to discover that search answers data
 * questions ("what's my odometer") and runs commands ("lock my car"), not
 * just finds settings by name.
 */
@Composable
internal fun SearchSuggestions(state: UiState, compact: Boolean = false, onPick: (String) -> Unit) {
    // Plain Surface chips, not MorphButton -- so unlike most taps in this app they
    // don't get a click() automatically and needed it wired in by hand.
    val haptics = LocalHaptics.current
    val carName = state.vehicles.firstOrNull()?.name
    // Short forms when the room is short -- on a cover screen with the keyboard
    // up, "odometer for Ioniq 5" wraps to two lines and pushes the next chip
    // off the panel, so a hint about what you can ask costs you the ability to
    // see what else you can ask. The long forms teach the syntax; the short
    // ones just have to fit and still work when tapped.
    val examples = buildList {
        if (compact) {
            add("odometer")
            add("battery")
            add("lock my car")
            add("haptics")
        } else {
            add("odometer" + (carName?.let { " for $it" } ?: ""))
            add("battery level")
            add("lock" + (carName?.let { " my $it" } ?: " my car"))
            // Teaches the temperature syntax, which is not guessable: nothing
            // else on screen says a command can carry a value.
            add("start climate at the coldest temperature" + (carName?.let { " on $it" } ?: ""))
            add("haptic feedback")
            if (state.vehicles.any { state.hasBattery(it) }) add("start smart climate")
        }
    }
    Text(
        "Try asking",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        // Floating directly over the aurora/scrolling content behind it with
        // nothing opaque underneath -- onSurfaceVariant (a deliberately muted
        // secondary-text tone) read as low-contrast there. Full-strength
        // onSurface instead.
        color = MaterialTheme.colorScheme.onSurface,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Same staggered pop as the search RESULT cards (staggeredResultVisible), reused
        // as-is: this list is just as much a "search UI" element as the results below it,
        // and giving one a cascade while the other snaps in flat is exactly the kind of
        // per-surface inconsistency that was reported.
        val examplesKey = examples.joinToString("|")
        examples.forEachIndexed { i, example ->
            PopVisible(visible = staggeredResultVisible(examplesKey, i)) {
                // Same MorphButton every selector chip in the app uses, with
                // the search screen's tonal fill kept as its standard colours.
                val exampleSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = exampleSource,
                    enabled = true,
                ) {
                    MorphButton(
                        onClick = { onPick(example) },
                        interactionSource = exampleSource,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        minHeight = 0.dp,
                        modifier = Modifier.dropShadow(RoundedCornerShape(50), blurRadius = 8.dp, offsetY = 3.dp),
                    ) {
                        Text(
                            example,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
