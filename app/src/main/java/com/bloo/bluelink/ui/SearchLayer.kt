package com.bloo.bluelink.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.chrisbanes.haze.HazeState

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
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var focused by rememberSaveable { mutableStateOf(false) }
    val open = focused || query.isNotEmpty()
    var dockName by rememberSaveable { mutableStateOf(SearchDock.RIGHT.name) }
    val dock = SearchDock.valueOf(dockName)
    // The app's own haptic vocabulary (Haptics.kt), not the platform LocalHapticFeedback.
    val haptics = LocalHaptics.current

    BackHandler(enabled = open) { query = ""; focused = false }
    // Report open-state so the aurora can pause (see AuroraBackground `paused`).
    SideEffect { onOpenChanged?.invoke(open) }
    // Click on open, tick on close, on the frame the shape starts moving. Armed after first
    // composition so a LaunchedEffect born false does not buzz on launch.
    var hapticArmed by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (hapticArmed) {
            if (open) haptics?.click() else haptics?.tick()
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
        val barH = SearchElementHeight
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
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .noRippleClickable { query = ""; focused = false },
            )
        }

        // Results / suggestions, anchored to the bottom rather than to the element: the element is
        // always bottom-centre while open, so this never has to chase a bubble around the screen.
        AnimatedVisibility(
            visible = open,
            enter = expandEnter(Alignment.Bottom),
            exit = expandExit(Alignment.Bottom),
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = barH + SearchEdge + bottomInset + GapRow),
        ) {
            GlassSurface(
                shape = ExtraLargeShape,
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
                        SettingsSearchResults(
                            query, submitted, vm, state.value, appearance, notif,
                            limit = if (keyboardUp) 4 else Int.MAX_VALUE,
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
    val bubble = SearchElementHeight
    val targetW = when (form) {
        SearchForm.BAR -> barWidth
        SearchForm.PILL -> pillWidth
        SearchForm.BUBBLE -> bubble
    }
    val targetH = SearchElementHeight

    // Resting position (top-left) for the current form/dock.
    val minX = SearchEdge
    val maxX = (containerWidth - bubble - SearchEdge).coerceAtLeast(SearchEdge)
    val bubbleX = if (dock == SearchDock.LEFT) minX else maxX
    val bubbleY = (containerHeight - bubble - SearchEdge - bottomInset).coerceAtLeast(SearchEdge)
    val barX = (containerWidth - targetW) / 2
    val barY = containerHeight - targetH - SearchEdge - bottomInset
    val restX = if (form == SearchForm.BUBBLE) bubbleX else barX
    val restY = if (form == SearchForm.BUBBLE) bubbleY else barY

    // Drag: a plain float written straight from the gesture, local to this element.
    var dragging by remember { mutableStateOf(false) }
    var dragX by remember { mutableStateOf(Float.NaN) }
    var lastDx by remember { mutableFloatStateOf(0f) }

    // Two springs: SIZE overshoots a little, POSITION is bouncier still so the edge-snap reads as
    // landing. Numbers are the shared PebbleBounce tokens.
    val sizeSpec = lowPowerAwareSpring<Dp>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
    val w = animateDpAsState(targetW, sizeSpec, label = "searchW").value
    val h = animateDpAsState(targetH, sizeSpec, label = "searchH").value
    // A spring is wrong for a drag (it trails the finger); position snaps 1:1 while the finger is
    // down.
    val posSpec = if (dragging) snap<Dp>() else {
        lowPowerAwareSpring<Dp>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
    }
    val targetX = if (dragging && !dragX.isNaN()) dragX.dp.coerceIn(minX, maxX) else restX
    val x = animateDpAsState(targetX, posSpec, label = "searchX").value
    val y = animateDpAsState(restY, posSpec, label = "searchY").value

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
        width = w,
        height = h,
        onQueryChange = onQueryChange,
        onFocusChange = onFocusChange,
        onSubmit = onSubmit,
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
            val widthNow = if (form == SearchForm.PILL) pillWidth else bubble
            val center = (if (dragX.isNaN()) x else dragX.dp) + widthNow / 2
            onDockChange(SearchDock.landing((center / containerWidth).coerceIn(0f, 1f), lastDx, dock))
            dragX = Float.NaN
            dragging = false
        },
        modifier = Modifier.align(Alignment.TopStart).offset(x, y),
        hazeState = hazeState,
    )
}
