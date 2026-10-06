package com.bloo.bluelink.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.MaterialTheme
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
 * The single search element, hoisted to the app root so it outlives screen transitions and morphs
 * between them.
 */
@Composable
internal fun SearchLayer(
    vm: AppViewModel,
    state: State<UiState>,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
    onSettings: Boolean,
    /**
     * Reports whether the search UI is open; the ambient aurora pauses while true so typing frames
     * stay cheap.
     */
    onOpenChanged: ((Boolean) -> Unit)? = null,
    /**
     * Screens.kt's shared instance that the underlying screens mark their content with; null leaves
     * the glass fill without a blur source (flat tint).
     */
    hazeState: HazeState? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var focused by rememberSaveable { mutableStateOf(false) }
    val open = focused || query.isNotEmpty()
    // Where the bubble was dragged, in dp from the top-left (NaN = never, so it rests in its
    // corner). Saved so it survives rotation. mutableStateOf, not mutableFloatStateOf:
    // rememberSaveable's guaranteed Saver path; this changes twice a gesture, so the boxing doesn't
    // matter.
    @Suppress("AutoboxingStateCreation")
    var dragX by rememberSaveable { mutableStateOf(Float.NaN) }
    // Normal (phone) layout only: which of three docks the bubble sits in, and how fast the last
    // drag move was.
    var dockName by rememberSaveable { mutableStateOf(SearchDock.RIGHT.name) }
    val dock = SearchDock.valueOf(dockName)
    var lastDx by remember { mutableFloatStateOf(0f) }
    // True only between finger-down and finger-up on the bubble. The position animation is BYPASSED
    // while it is true -- see the spec choice below.
    var dragging by remember { mutableStateOf(false) }
    // The app's own haptic vocabulary (Haptics.kt), not the platform LocalHapticFeedback.
    val haptics = LocalHaptics.current

    BackHandler(enabled = open) { query = ""; focused = false }
    // Report open-state so the aurora can pause (see AuroraBackground `paused`).
    SideEffect { onOpenChanged?.invoke(open) }
    // Click on open, tick on close (same asymmetry as PebbleShell's header), on the frame the shape
    // starts moving. Armed after first composition so a LaunchedEffect born false does not buzz on
    // launch.
    var hapticArmed by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (hapticArmed) {
            if (open) haptics?.click() else haptics?.tick()
        }
        hapticArmed = true
    }
    // Drop any stale AI answer once the box is cleared, and forget the last submission with it --
    // otherwise reopening search shows the previous question's answer under an empty field.
    LaunchedEffect(query.isBlank()) {
        if (query.isBlank()) { vm.clearAiReply(); submitted = "" }
    }

    // IME/nav insets are read here, outside the BoxWithConstraints lambda, so keystroke-driven
    // re-runs of that lambda do not resubscribe; the panel re-measures only when the keyboard
    // crosses open/closed.
    val keyboardUp = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 80.dp
    val bottomInset = WindowInsets.navigationBars.union(WindowInsets.ime)
        .asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // With the keyboard up, the panel and the bar together are competing for the sliver of
        // screen that is left -- on a phone that is a couple of hundred dp, not the 360 the panel
        // would otherwise take. Measure what is actually free rather than guessing: the panel gets
        // what remains above the bar, minus a margin so it never looks wedged.
        val edge = 16.dp
        val bubble = 52.dp
        val barW = minOf(maxWidth - edge * 2, 640.dp)
        val barH = 52.dp
        val freeAbovePill = (maxHeight - bottomInset - barH - edge * 2 - 24.dp).coerceAtLeast(96.dp)
        // A medium pill: wide enough for icon and word, far short of the bar.
        val pillW = minOf(168.dp, barW)
        val form = when {
            open -> SearchForm.BAR
            onSettings || dock == SearchDock.CENTER -> SearchForm.PILL
            else -> SearchForm.BUBBLE
        }
        // Publish the search element's location so toasts clear it: a corner dock or CENTER (pill
        // or open bar); null while hidden. SideEffect because the registry is state read by other
        // composables.
        val floatingRegistry = LocalFloatingRegistry.current
        SideEffect {
            floatingRegistry.searchDock = if (open) SearchDock.CENTER else dock
        }
        DisposableEffect(Unit) { onDispose { floatingRegistry.searchDock = null } }

        // Resting corner for the bubble and drag bounds that keep it on screen.
        val minX = edge
        val maxX = (maxWidth - bubble - edge).coerceAtLeast(edge)
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

        // Two springs: SIZE overshoots a little, POSITION is critically damped (one bouncy spring
        // slides past rest). Width and height share theirs; numbers are the shared PebbleBounce
        // tokens.
        val sizeSpec = lowPowerAwareSpring<Dp>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        // A spring is wrong for a drag (trails the finger); position snaps 1:1 while the finger is
        // down.
        val posSpec = if (dragging) snap<Dp>() else {
            // Bouncy on purpose: the edge-snap should read as landing, with the same shared bounce
            // as `sizeSpec`.
            lowPowerAwareSpring<Dp>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        }
        // key(compact) restarts these animations at the new target so the ball does not crawl
        // across the screen when the layout flips; SearchPill's entrance spring takes over.
        val w = animateDpAsState(targetW, sizeSpec, label = "searchW").value
        val h = animateDpAsState(targetH, sizeSpec, label = "searchH").value
        val x = animateDpAsState(targetX, posSpec, label = "searchX").value
        val y = animateDpAsState(targetY, posSpec, label = "searchY").value

        // Dismiss scrim, below the pill so it never eats its taps; uses the shared collapse effects
        // spec for the fade.
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

        // Results / suggestions, anchored to the bottom rather than to the pill: the pill is always
        // at the bottom centre while open, so this never has to chase a bubble around the screen.
        AnimatedVisibility(
            visible = open,
            enter = expandEnter(Alignment.Bottom),
            exit = expandExit(Alignment.Bottom),
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = barH + edge + bottomInset + GapRow),
        ) {
            val panelShape = ExtraLargeShape
            // GlassSurface (GlassChrome.kt): the shared fill/rim/shadow.
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
                        .padding(GapSection),
                    verticalArrangement = Arrangement.spacedBy(GapRow),
                ) {
                    if (query.isNotBlank()) {
                        // Fewer results while the keyboard is up. This is what the new ranking
                        // buys: cutting to the top few is only honest when the top few really are
                        // the best ones, and a scrollable list you cannot see the bottom of is
                        // worse than a short list you can.
                        SettingsSearchResults(
                            query, submitted, vm, state.value, appearance, notif,
                            // Cover with keyboard up (~260dp square, mostly keyboard): two visible
                            // results beat six to scroll through.
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
            // Dragging only exists for the bubble. A bar spans the screen -- there is nowhere to
            // move it to -- and while it is a text field a drag would fight the keyboard and the
            // panel above it.
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
                val widthNow = if (form == SearchForm.PILL) pillW else bubble
                val center = (if (dragX.isNaN()) x else dragX.dp) + widthNow / 2
                val next = SearchDock.landing((center / maxWidth).coerceIn(0f, 1f), lastDx, dock)
                dockName = next.name
                dragX = Float.NaN
                vm.setSearchBubblePosition(next.xFrac, 1f)
                dragging = false
                // click() for a confirm-weight landing, matching the edge-snap bounce.
                haptics?.click()
            },
            modifier = Modifier.align(Alignment.TopStart).offset(x = x, y = y),
            hazeState = hazeState,
        )
    }
}
