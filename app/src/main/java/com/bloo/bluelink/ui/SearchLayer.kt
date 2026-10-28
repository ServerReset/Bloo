package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import com.bloo.uicommon.BounceDamping
import dev.chrisbanes.haze.HazeState
import com.bloo.uicommon.MotionMedium
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.canBlurBackdrops
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.lowPowerAwareSpring

/** Which shape the search control is in: a bubble, a pill, or the full bar. */
internal enum class SearchForm { BUBBLE, PILL, BAR }

/** The screen-edge inset the search element rests at. */
private val SearchEdge = 16.dp

/** The widest the open search bar gets. */
private val MaxSearchBarWidth = 640.dp

/** The width of the medium "pill" form. */
private val SearchPillWidth = 168.dp

/**
 * The search UI: a scrim, a results panel, and the one morphing search element (bubble / pill /
 * bar). Hoisted to the app root so the element outlives screen transitions.
 *
 * The scrim and the panel read only `open`/`query`/`submitted` -- never the element's motion -- and
 * the element owns all of its own drag/position/size state, so dragging the bubble or morphing the
 * element can never re-lay-out (or re-blur) the panel behind it.
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
    com.bloo.bluelink.perf.TrackRecompositions("SearchLayer")
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var focused by rememberSaveable { mutableStateOf(false) }
    val open = focused || query.isNotEmpty()
    val anchorForToasts = LocalSearchAnchor.current
    SideEffect { anchorForToasts.searchOpen = open }
    var dockName by rememberSaveable { mutableStateOf(SearchDock.RIGHT.name) }
    val dock = SearchDock.valueOf(dockName)
    // The app's own haptic vocabulary (Haptics.kt), not the platform LocalHapticFeedback.
    val haptics = LocalHaptics.current

    BackHandler(enabled = open) { query = ""; focused = false }
    // Ctrl+K / Ctrl+F: the keyboard's way into the search field.
    LaunchedEffect(vm) {
        vm.shortcutEvents.collect { if (it == Shortcut.Search) focused = true }
    }
    // Report open-state so the aurora can pause (see AuroraBackground `paused`).
    SideEffect { onOpenChanged?.invoke(open) }
    // Leaving composition while open (lock engaged, map expanded) must not leave the toasts parked and the
    // aurora paused.
    DisposableEffect(Unit) { onDispose { anchorForToasts.searchOpen = false; onOpenChanged?.invoke(false) } }
    // Click on open, tick on close, on the frame the shape starts moving. Armed after first
    // composition so a LaunchedEffect born false does not buzz on launch.
    var hapticArmed by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (hapticArmed) {
            if (open) haptics?.click() else haptics?.tick()
            com.bloo.bluelink.perf.PerfMonitor.scope(if (open) "search-open" else "search-close", 1_200)
        }
        hapticArmed = true
    }
    // Drop any stale AI answer once the box is cleared, and forget the last submission with it.
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
        val barH = ChromeBarHeight
        val barW = minOf(maxWidth - SearchEdge * 2, MaxSearchBarWidth)
        val freeAbovePill = (maxHeight - bottomInset - barH - SearchEdge * 2 - 24.dp).coerceAtLeast(96.dp)
        val form = when {
            open -> SearchForm.BAR
            onSettings || dock == SearchDock.CENTER -> SearchForm.PILL
            else -> SearchForm.BUBBLE
        }
        // Restore the persisted dock once.
        LaunchedEffect(Unit) {
            vm.searchBubblePosition()?.let { (xFrac, _) -> dockName = SearchDock.fromFrac(xFrac).name }
        }

        // Dismiss scrim, below the element so it never eats the element's taps.
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()),
        ) {
            // The whole search interface sits on ONE uniform frost: a full blur of the page plus an even veil of the theme's own
            // surface, so whatever is behind (pebbles, photos, the aurora) is softened the same everywhere and the pills read
            // cleanly on top of it. Without the blur (older phones, battery saver) the veil alone is denser.
            val frostScrim = hazeState != null && canBlurBackdrops()
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (frostScrim) Modifier.appHazeEffect(hazeState) else Modifier)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = if (frostScrim) 0.46f else 0.9f))
                    .noRippleClickable { query = ""; focused = false },
            )
        }

        // Results / suggestions, anchored to the bottom rather than to the element: the element is always bottom-centre while
        // open, so this never has to chase a bubble around the screen. Every result is its own floating pill (see SearchPills.kt),
        // so there is no panel: the pills move, enter and leave one at a time over the frosted page.
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) + slideInVertically(tween(MotionMedium)) { it / 8 },
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) + slideOutVertically(tween(MotionFast)) { it / 8 },
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = barH + SearchEdge + bottomInset + GapRow),
        ) {
            val panel = Modifier.width(barW).heightIn(max = minOf(560.dp, freeAbovePill))
            // Suggestions and results are two states of one list: they cross-fade into each other.
            AnimatedContent(
                targetState = query.isNotBlank(),
                transitionSpec = {
                    (fadeIn(tween(MotionMedium, delayMillis = MotionFast)) +
                        slideInVertically(tween(MotionMedium, delayMillis = MotionFast)) { it / 14 }) togetherWith
                        fadeOut(tween(MotionFast)) using SizeTransform(clip = false)
                },
                label = "searchPanelState",
            ) { hasQuery ->
                if (hasQuery) {
                    SettingsSearchResults(
                        query, submitted, vm, state.value, appearance, notif,
                        limit = if (keyboardUp) 6 else Int.MAX_VALUE,
                        hazeState = hazeState,
                        onPick = { text, run -> query = text; if (run) submitted = text },
                        modifier = panel,
                    )
                } else {
                    SearchSuggestions(state.value, panel, compact = keyboardUp, hazeState = hazeState) { picked, run ->
                        query = picked
                        if (run) submitted = picked
                    }
                }
            }
        }

        SearchElement(
            form = form,
            dock = dock,
            query = query,
            focused = focused,
            containerWidth = maxWidth,
            containerHeight = maxHeight,
            bottomInset = bottomInset,
            barWidth = barW,
            pillWidth = minOf(SearchPillWidth, barW),
            onQueryChange = { query = it },
            onFocusChange = { focused = it },
            onSubmit = { submitted = query },
            onDockChange = { next ->
                dockName = next.name
                vm.setSearchBubblePosition(next.xFrac, 1f)
                haptics?.click()
            },
            hazeState = hazeState,
        )
    }
}

/**
 * The one search element. Owns all of its own motion (drag, position, size) and writes only its
 * RESTING rect to [LocalSearchAnchor] so the toasts and page spacers agree with it without either
 * side observing a layout pass. It is the floating button (bubble), the settings pill, and the open
 * bar as one morphing node.
 */
@Composable
private fun BoxScope.SearchElement(
    form: SearchForm,
    dock: SearchDock,
    query: String,
    focused: Boolean,
    containerWidth: Dp,
    containerHeight: Dp,
    bottomInset: Dp,
    barWidth: Dp,
    pillWidth: Dp,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onDockChange: (SearchDock) -> Unit,
    hazeState: HazeState?,
) {
    val bubble = ChromeBarHeight
    val targetW = when (form) {
        SearchForm.BAR -> barWidth
        SearchForm.PILL -> pillWidth
        SearchForm.BUBBLE -> bubble
    }
    val targetH = ChromeBarHeight

    // Resting position (top-left) for the current form/dock.
    val minX = SearchEdge
    val maxX = (containerWidth - bubble - SearchEdge).coerceAtLeast(SearchEdge)
    val bubbleX = if (dock == SearchDock.LEFT) minX else maxX
    // How far right a drag may take the element's left edge: by ITS width. The pill is wider than the bubble, and
    // clamping it by the bubble's width let its right edge be dragged off the screen.
    val dragMaxX = (containerWidth - (if (form == SearchForm.PILL) pillWidth else bubble) - SearchEdge).coerceAtLeast(minX)
    val bubbleY = (containerHeight - bubble - SearchEdge - bottomInset).coerceAtLeast(SearchEdge)
    val barX = (containerWidth - targetW) / 2
    val barY = containerHeight - targetH - SearchEdge - bottomInset
    val restX = if (form == SearchForm.BUBBLE) bubbleX else barX
    val restY = if (form == SearchForm.BUBBLE) bubbleY else barY

    // Drag: a plain float written straight from the gesture, local to this element.
    var dragging by remember { mutableStateOf(false) }
    var dragX by remember { mutableStateOf(Float.NaN) }
    var lastDx by remember { mutableFloatStateOf(0f) }

    // The four springs are read in layout and draw (lambdas below), never here, so a morph or a drag moves the pill
    // without recomposing it every frame.
    // Two springs: SIZE overshoots a little, POSITION is bouncier still so the edge-snap reads as
    // landing. Numbers are the shared BounceDamping and its stiffness.
    val sizeSpec = lowPowerAwareSpring<Dp>(dampingRatio = BounceDamping, stiffness = Spring.StiffnessLow)
    val w = animateDpAsState(targetW, sizeSpec, label = "searchW")
    val h = animateDpAsState(targetH, sizeSpec, label = "searchH")
    // A spring is wrong for a drag (it trails the finger); position snaps 1:1 while the finger is
    // down.
    val posSpec = if (dragging) snap<Dp>() else {
        lowPowerAwareSpring<Dp>(dampingRatio = BounceDamping, stiffness = Spring.StiffnessLow)
    }
    val targetX = if (dragging && !dragX.isNaN()) dragX.dp.coerceIn(minX, dragMaxX) else restX
    val x = animateDpAsState(targetX, posSpec, label = "searchX")
    val y = animateDpAsState(restY, posSpec, label = "searchY")

    // Publish only the RESTING rect to the shared anchor. Never the dragged position, so a drag
    // never invalidates anything that reads the anchor.
    val anchor = LocalSearchAnchor.current
    val density = LocalDensity.current
    DisposableEffect(anchor) { onDispose { anchor.publish(null, dock) } }
    SideEffect {
        val left = with(density) { restX.toPx() }
        val top = with(density) { restY.toPx() }
        val right = left + with(density) { targetW.toPx() }
        val bottom = top + with(density) { targetH.toPx() }
        anchor.publish(Rect(left, top, right, bottom), dock)
    }

    SearchPill(
        query = query,
        focused = focused,
        form = form,
        width = { w.value },
        height = { h.value },
        onQueryChange = onQueryChange,
        onFocusChange = onFocusChange,
        onSubmit = onSubmit,
        onDrag = if (form != SearchForm.BAR) {
            { dx, _ ->
                dragX = ((if (dragX.isNaN()) x.value else dragX.dp) + dx).coerceIn(minX, dragMaxX).value
                lastDx = dx.value
            }
        } else null,
        onDragStart = {
            dragging = true
            dragX = Float.NaN
            lastDx = 0f
        },
        onDragEnd = {
            val widthNow = if (form == SearchForm.PILL) pillWidth else bubble
            val center = (if (dragX.isNaN()) x.value else dragX.dp) + widthNow / 2
            onDockChange(SearchDock.landing((center / containerWidth).coerceIn(0f, 1f), lastDx, dock))
            dragX = Float.NaN
            dragging = false
        },
        modifier = Modifier.align(Alignment.TopStart).offset { IntOffset(x.value.roundToPx(), y.value.roundToPx()) },
        hazeState = hazeState,
    )
}
