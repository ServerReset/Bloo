@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.connectedGroupShape
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.supportsHornLights
import com.bloo.bluelink.data.isGen5W
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt
import com.bloo.uicommon.ReorderColumn
import com.bloo.uicommon.animatePlacement

/** A friendly label for a pebble/section id. */
internal fun sectionLabel(section: String): String = when (section) {
    "charge" -> "Charge / fuel"
    "climate" -> "Climate"
    "location" -> "Location"
    "weather" -> "Weather"
    "trips" -> "Trips"
    "info" -> "Car info"
    "diagnostics" -> "Diagnostics"
    "controls" -> "Lock / climate"
    else -> section.replaceFirstChar { it.uppercase() }
}

/**
 * The dual-column "hot spot": a fixed slot under the car-info column. When a
 * pebble is pinned here it renders non-collapsible (always open); otherwise it's
 * a chooser to pin one. Pinning moves the pebble out of the scrolling list.
 */
@Composable
internal fun HotspotSlot(
    v: Vehicle,
    hotspot: String?,
    /**
     * State SOURCE. It was already immediately re-wrapped into a rememberUpdatedState here,
     * so nothing downstream ever wanted the snapshot -- but taking the snapshot meant the
     * CALLER had to read state.value in its own body, subscribing the whole dual-column view
     * to every UiState emission. Reading it here confines that to this slot.
     */
    stateSource: State<UiState>,
    vm: AppViewModel,
) {
    val state = stateSource.value
    if (hotspot != null) {
        val haptics = LocalHaptics.current
        // Drag the pinned pebble away (long-press, then drag past a threshold) to
        // unpin - the mirror of dragging a pebble onto the slot to pin. The Unpin
        // button does the same thing for discoverability.
        var lifted by remember(hotspot) { mutableStateOf(false) }
        var dragY by remember(hotspot) { mutableFloatStateOf(0f) }
        val lift by animateFloatAsState(if (lifted) 1.03f else 1f, label = "unpinLift")
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (lifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (lifted) "Release to unpin" else "Pinned",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                MorphTextButton("Unpin", onClick = { vm.setHotspot(v, null) })
            }
            CompositionLocalProvider(LocalForceExpanded provides true) {
                Box(
                    Modifier
                        .graphicsLayer { scaleX = lift; scaleY = lift }
                        .pointerInput(hotspot) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragY = 0f; lifted = true; haptics?.tick() },
                                onDrag = { change, amt -> change.consume(); dragY += abs(amt.x) + abs(amt.y) },
                                onDragEnd = {
                                    lifted = false
                                    if (dragY > 56f) { haptics?.heavy(); vm.setHotspot(v, null) }
                                },
                                onDragCancel = { lifted = false },
                            )
                        },
                ) {
                    SinglePebble(hotspot, v, stateSource, vm, Modifier)
                }
            }
        }
    } else {
        var menu by remember { mutableStateOf(false) }
        // Memoized on the exact slices the predicate reads, mirroring the sibling PebbleList
        // (which documents the same fix). HotspotSlot takes the whole UiState, so it recomposes
        // on every emission; without this it re-allocated the filtered list AND a fresh setOf()
        // literal on every refresh/command tick for the visible car. The two `!=` checks replace
        // the per-pass set allocation.
        val options = remember(
            state.sectionOrders[v.vin], state.hiddenPebbles, state.aiEnabled, state.hasBattery(v),
            v.isGen5W, state.platforms[v.vin], state.updateAvailable, state.updateTileDismissed,
        ) {
            state.sectionsFor(v).filter {
                it != "summary" && it != "controls" && state.isSectionAvailable(v, it)
            }
        }
        val hotDrag = LocalHotSeatDrag.current
        val hovered = hotDrag?.overSlot == true
        // The empty slot is both a drop target (drag any pebble onto it to pin)
        // and a tap target (tap to pick one from a menu). It highlights while a
        // dragged pebble hovers over it.
        Box(
            Modifier.onGloballyPositioned {
                hotDrag?.let { d -> d.slotTopLeft = it.localToWindow(Offset.Zero); d.slotSize = it.size }
            },
        ) {
            MorphButton(
                onClick = { menu = true },
                modifier = Modifier.fillMaxWidth(),
                active = hovered,
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                contentPadding = PaddingValues(16.dp),
            ) {
                Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (hovered) "Release to pin" else "Pin a pebble here", style = MaterialTheme.typography.bodyMedium)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                options.forEach { sec ->
                    DropdownMenuItem(
                        text = { Text(sectionLabel(sec)) },
                        onClick = { vm.setHotspot(v, sec); menu = false },
                    )
                }
            }
        }
    }
}

/** How far the floating overlays (dots, buttons) slide down during a refresh. */
internal val RefreshPullShift = 96.dp

/**
 * Wraps content with the pull-to-refresh gesture with an overlay indicator.
 * Delegates the actual gesture recognition/animation state to Material 3's
 * [rememberPullToRefreshState] (`ptrState`); this composable's own job is
 * publishing that pull distance out to [LocalPullFraction] (so sibling
 * overlays elsewhere in [GarageScreen] can react to the live pull, not just
 * the boolean `state.refreshing`), and manually positioning the loading
 * indicator by hand rather than letting Material lay it out, so it can
 * slide fully off-screen above the content when idle and only ease into
 * view as the user pulls.
 */
@Composable
internal fun Refreshable(
    v: Vehicle,
    // The single UiState field this needs, and NOT the whole UiState: passing the state object
    // subscribed every caller's composition to every emission -- a weather tick for another car,
    // an AI probe, a log line -- for all three live pager pages at once, which is exactly what
    // the callers' State<UiState> indirection exists to avoid.
    refreshing: Boolean,
    vm: AppViewModel,
    hideIndicator: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val ptrState = rememberPullToRefreshState()
    val haptics = LocalHaptics.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Publish the pull distance so GarageScreen's overlays track the pull live.
    val pullFractionState = LocalPullFraction.current
    LaunchedEffect(ptrState) {
        snapshotFlow { ptrState.distanceFraction }.collect { pullFractionState.value = it }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = refreshing,
                state = ptrState,
                onRefresh = { haptics?.diceRoll(); vm.refreshStatus(v) },
            ),
    ) {
        // Content stays full-size and edge-to-edge; never shifted down.
        content()
        // Indicator floats above content as a z-elevated overlay. The whole
        // indicatorProgress/indicatorY calc used to live directly in this
        // composable's body, reading ptrState.distanceFraction on every frame
        // of the drag -- that's a *composition*-phase read, so it recomposed
        // this entire Box (and everything content() renders, the whole car
        // card) on every pixel of the pull gesture, not just re-laid-out the
        // small indicator. Moved into the offset{} lambda, which only runs in
        // the layout phase, so a live drag now costs one indicator relayout
        // per frame instead of a full recomposition of the car's content.
        if (!hideIndicator) {
            PullToRefreshDefaults.LoadingIndicator(
                state = ptrState,
                isRefreshing = refreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        val indicatorProgress = if (refreshing) 1f else ptrState.distanceFraction.coerceIn(0f, 1f)
                        val offScreenPx = -(topInset + 56.dp).roundToPx()
                        val onScreenPx = (topInset + 28.dp).roundToPx()
                        IntOffset(0, offScreenPx + ((onScreenPx - offScreenPx) * indicatorProgress).roundToInt())
                    },
            )
        }
    }
}
/** Hero image + gauge, then the primary lock/charge controls (expanded view). */
@Composable
internal fun CriticalContent(v: Vehicle, stateSource: State<UiState>, vm: AppViewModel) {
    // Read here rather than at the call site, for the same reason as HotspotSlot above: this
    // does need most of UiState, but its caller does not, and a read in the caller's body
    // subscribes the caller's whole subtree.
    val state = stateSource.value
    val status = state.statusFor(v)
    val hMetric = LocalAppearance.current.unitSystem == "metric"
    // Same fix as SinglePebble's "summary" branch, same reasoning: HeroHeader takes no
    // `state` itself, so what's memoized is the derived arguments built here.
    val heroState = remember(
        status, state.imageUrls[v.vin], state.hasBattery(v), state.hasFuel(v),
        state.locations[v.vin], state.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
    ) { state }
    HeroHeader(
        v, status, heroState.imageUrls[v.vin], heroState.hasBattery(v), heroState.hasFuel(v), vm,
        heroState.drivingLabel(v), metric = hMetric,
        photoExpanded = heroState.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
    )
    // Update tile lives in the "pebbles" column's PebbleList as its own
    // reorderable/pinnable "update" section now, not hardcoded into this
    // fixed critical-info column -- see SinglePebble.
    // PrimaryActions is called bare here, unlike its other callers (ControlsPebble,
    // CompactMainTile) which always wrap it in a Surface that establishes a
    // readable contentColor. StateControl's status label falls back to
    // LocalContentColor when not highlighted/off-tinted, and Compose's own
    // default for that (when nothing upstream ever sets it - the dual-column
    // controls column isn't itself Surfaced) is opaque black, invisible against
    // this app's dark theme. That's what read as "no status text next to the
    // button" here even though the exact same StateControl shows it fine
    // everywhere else.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        PrimaryActions(v, state, vm)
    }
}

/**
 * The lock/unlock quick control. Deliberately *not* styled like the other
 * pebbles - it's just the morphing StateControl with its status on the left,
 * with no card, header or expand chevron. It can still be long-pressed and
 * dragged to reorder, like a pebble, even though it doesn't look like one.
 */
@Composable
internal fun ControlsPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val shape = RoundedCornerShape(PebbleCornerCollapsed)
    // Was frostedRim unconditionally -- every other pebble instead gates a
    // bolder dedicated border on the pebbleOutline setting (see Pebble()),
    // frostedRim's alpha being tuned for chrome over a car photo and nearly
    // invisible against a flat pebble background either way. This pebble
    // rolls its own Surface instead of going through Pebble(), so it had been
    // missed -- the setting simply did nothing here.
    val pebbleOutline = LocalAppearance.current.pebbleOutline
    // Recent remote commands for this car, revealed by pressing this pebble's own background.
    // Deliberately undiscoverable-by-chrome: no chevron, no header, no affordance of any kind --
    // the history is a thing you find, not a control the pebble advertises. Kept per-car and NOT
    // rememberSaveable: reopening the app should land on the plain lock control, not on whatever
    // was last revealed.
    var showHistory by remember(v.vin) { mutableStateOf(false) }
    val history = state.remoteActionHistory[v.vin].orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth().then(dragHandle)
            // AFTER dragHandle, and on the same node rather than on a child. Order matters both
            // ways: detectDragGesturesAfterLongPress does not consume the initial down until a
            // long press actually fires, so a quick tap falls through to this detector, while a
            // long press is claimed by the drag and never toggles. On a CHILD it would instead
            // consume the press outright and silently kill drag-to-reorder for this pebble.
            .pointerInput(v.vin) { detectTapGestures { showHistory = !showHistory } }
            // The tap above is a bare pointerInput, which contributes NO semantics -- so to a
            // screen reader the history simply did not exist. "Hidden" here means hidden from
            // the visual chrome, not withheld from TalkBack, and a custom action is exactly the
            // right shape for that: it adds nothing on screen and no extra focus stop, but the
            // gesture is announced and invokable through the actions menu. Guarded on there
            // being history to show, so it isn't offered when it would do nothing.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(
                        if (showHistory) "Hide recent remote actions" else "Show recent remote actions",
                    ) { showHistory = !showHistory; true },
                )
            }
            .dropShadow(shape, blurRadius = 12.dp, offsetY = 4.dp)
            .then(
                if (pebbleOutline) {
                    Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), shape)
                } else Modifier,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Asymmetric padding to match pebble header alignment: more left, less right.
            // Height stays pinned here (not on the Surface) so the pebble keeps its exact
            // resting silhouette and only grows when the history is actually showing.
            Box(Modifier.fillMaxWidth().height(ControlHeight).padding(start = 12.dp, end = 8.dp)) {
                // PrimaryActions' own default start padding (26.dp) plus this
                // Box's 12.dp put the lock icon noticeably further right than
                // every other pebble's header icon (Charge, Climate, ...), which
                // only ever get Pebble's flat PebbleContentInset row padding. The 4.dp here
                // lines the two icons up: 4 + this Box's own 12 == PebbleContentInset.
                PrimaryActions(
                    v, state, vm,
                    contentPadding = PaddingValues(start = PebbleContentInset - 12.dp, end = 8.dp),
                )
            }
            // Gated on the toggle ALONE, not on there being history. RemoteActionsInline draws
            // its own empty state, and a reveal that silently stays shut on a car with no
            // history yet is indistinguishable from the gesture not existing.
            AnimatedVisibility(
                visible = showHistory,
                enter = collapseEnter(Alignment.Top),
                exit = collapseExit(Alignment.Top),
            ) {
                RemoteActionsInline(history)
            }
        }
    }
}

/** The reorderable pebble stack for a car. */
@Composable
internal fun PebbleList(v: Vehicle, state: State<UiState>, vm: AppViewModel, exclude: Set<String> = emptySet()) {
    val sel = state.value
    val allSections = sel.sectionsFor(v)
    // Memoized on the exact slices the predicate reads (the `eager` set below was
    // already remembered; this sibling filter was missed). PebbleList takes the STATE
    // SOURCE and reads by slice, so the filter re-allocates only when one of its own
    // keys changes rather than on every emission.
    val hasBattery = sel.hasBattery(v)
    // state.updateTileDismissed is in the key because isSectionAvailable now reads it: without
    // it this memo would keep the stale section list and the dismissed tile's phantom slot
    // would survive until some unrelated key changed. Every input the predicate reads has to be
    // a key, which is the contract this line already follows for the other six.
    val sections = remember(
        allSections, exclude, sel.hiddenPebbles, sel.aiEnabled, hasBattery, v.isGen5W, sel.platforms[v.vin],
        sel.updateAvailable, sel.updateTileDismissed,
    ) {
        allSections.filter {
            it !in exclude && sel.isSectionAvailable(v, it)
        }
    }
    val hotDrag = LocalHotSeatDrag.current
    // PERF: each car-pager page composes this whole pebble stack. Composing all
    // 8-10 pebbles eagerly (incl. ClimatePebble/ChargePebble's top-level effects,
    // which run BEFORE their Pebble() call regardless of collapsed state) on the
    // fling-settle frame is the biggest remaining car-swipe cost. So: compose the
    // hero + first EAGER_PEBBLES sections immediately (they're the only ones
    // above the fold), stub the rest with a collapsed-height placeholder for ONE
    // frame, then fill them in once idle (`filled` flips after the first frame).
    // Keyed on VIN so a disposed→recomposed page re-defers cheaply; a page kept
    // warm by beyondViewportPageCount=1 fills before the user ever swipes to it.
    // CRITICAL: `items` stays the FULL section list, so ReorderColumn's per-item
    // Box/key/animatePlacement/onSizeChanged/drag/semantics all exist from frame
    // one — only the body inside the content lambda is deferred, so the reorder
    // model is 100% intact and the off-screen stub→real swap is never visible.
    var filled by remember(v.vin) { mutableStateOf(false) }
    LaunchedEffect(v.vin) { withFrameNanos { }; filled = true }
    val eager = remember(sections) { sections.take(EAGER_PEBBLES).toSet() }
    ReorderColumn(
        items = sections,
        keyOf = { it },
        onReorder = { newVisible ->
            // Merge the reordered visible items back into the full section order so
            // excluded ones (the pinned hot-spot, summary, controls, hidden) keep
            // their slots instead of being dropped.
            val visibleSet = sections.toSet()
            val full = (allSections + com.bloo.bluelink.data.DEFAULT_SECTIONS).distinct()
            val queue = ArrayDeque(newVisible)
            val merged = full.map { s ->
                if (s in visibleSet && queue.isNotEmpty()) queue.removeFirst() else s
            }
            vm.setSectionOrder(v, merged)
        },
        // In the dual-column view, dragging a pebble onto the hot-spot slot pins it.
        onDragMove = hotDrag?.let { d ->
            { key, pointer -> d.section = key as String; d.pointer = pointer }
        },
        onDragRelease = hotDrag?.let { d ->
            { key ->
                val pin = d.overSlot
                d.section = null
                if (pin) { vm.setHotspot(v, key as String); true } else false
            }
        },
        staggerInOnColdStart = true,
        introKey = v.vin,
    ) { section, dragHandle, _ ->
        if (filled || section in eager) {
            SinglePebble(section, v, state, vm, dragHandle)
        } else {
            // One-frame off-screen placeholder: reserves ~collapsed pebble height so
            // the list doesn't visibly jump when the real body fills in, and carries
            // the dragHandle so ReorderColumn's item is fully formed. Below the fold,
            // so this transient state is never seen or interacted with.
            Box(Modifier.fillMaxWidth().height(PebbleHeaderHeight).then(dragHandle))
        }
    }
}

/** How many pebbles (from the top, incl. the hero summary) the per-car stack
 *  composes eagerly; the rest fill in one frame later, off the swipe. 3 comfortably
 *  covers everything above the fold on a phone so the visible region never stubs. */
internal const val EAGER_PEBBLES = 3

/**
 * Renders one pebble by section name (used by the list and the hot spot).
 *
 * Every pebble function below takes the WHOLE [UiState], not just the fields it
 * reads -- `state` is a data class, so its equality (and therefore Compose's
 * recomposition-skip check) fails on ANY field changing anywhere in the app, not
 * just the fields a given pebble actually uses. A weather refresh for a car
 * that isn't even on screen, an AI probe finishing, another car's status
 * arriving -- every one of those forced every visible pebble on every visible
 * car page to recompose, which is a big part of why the whole app reads as
 * laggy for several seconds after cold start or a car switch: that's exactly
 * the window where the most independent state updates land in quick
 * succession (cached-status restore, per-car status fetches, AI/Shizuku/update
 * probes, weather).
 *
 * Each branch below wraps the `state` it hands its pebble in
 * `remember(<the exact fields that pebble reads>) { state }` -- when none of
 * those keys changed since last time, `remember` returns the SAME state
 * reference as before, so the pebble sees an unchanged parameter and Compose
 * skips recomposing it, even though a genuinely newer `state` exists one frame
 * up. The pebble's own body is untouched; only what gets handed to it here is
 * cached. Keys were catalogued by reading every pebble function's body in
 * full (including what its own helper calls like `statusFor`/`isPending`
 * transitively read) rather than guessed -- a missed key would be a real
 * stale-UI bug, so each list below is the pebble's complete, verified
 * dependency set, not a guess at "probably enough."
 */
/** Memoized single-value slice of [state] keyed on exactly what the row reads:
 *  `remember(*keys) { state.value }`. SinglePebble's dispatch uses this for every
 *  branch so each row recomposes only when ITS keys change -- the same memo
 *  every branch hand-wrote before, without the block repeated twelve times. */
@Composable
internal fun stateSlice(state: State<UiState>, vararg keys: Any?): UiState =
    remember(*keys) { state.value }

@Composable
internal fun SinglePebble(section: String, v: Vehicle, state: State<UiState>, vm: AppViewModel, dragHandle: Modifier) {
    val status = state.value.statusFor(v)
    val seats = state.value.seatConfigFor(v)
    val enabled = !state.value.loading
    val mSingle = LocalAppearance.current.unitSystem == "metric"
    when (section) {
        "summary" -> {
            // HeroHeader itself takes no `state` param -- its dependency is entirely
            // in the derived arguments built here, so THOSE are what's memoized.
            val heroState = stateSlice(
                state, status, state.value.imageUrls[v.vin], state.value.hasBattery(v), state.value.hasFuel(v),
                state.value.locations[v.vin], state.value.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
            )
            HeroHeader(
                v, status, heroState.imageUrls[v.vin], heroState.hasBattery(v), heroState.hasFuel(v), vm,
                heroState.drivingLabel(v), dragHandle = dragHandle, metric = mSingle,
                photoExpanded = heroState.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
            )
        }
        // Its own reorderable/pinnable slot now, like every other pebble --
        // only actually present in the list while state.value.updateAvailable != null
        // (see PebbleList's filter and the two hotspot-eligibility checks). Global,
        // not per-car fields, but still worth memoizing: this section is rendered
        // on every car page, so an unrelated per-car state change (another car's
        // status, weather, AI) would otherwise recompose it just as often as any
        // other pebble.
        "update" -> {
            val updateState = stateSlice(
                state, state.value.updateAvailable, state.value.updateTileDismissed, state.value.shizukuAvailable,
                state.value.updateInstalling, state.value.updateDownloading, state.value.updateApkReady,
                state.value.updatePendingDismiss,
            )
            UpdateAvailableTile(updateState, vm, dragHandle)
        }
        "controls" -> {
            val controlsState = stateSlice(state, status, state.value.isPending(v.vin, "doors"), state.value.isPending(v.vin, "hornLights"))
            ControlsPebble(v, controlsState, vm, dragHandle)
        }
        "climate" -> {
            val climateState = stateSlice(
                state, status, seats, state.value.isPending(v.vin, "climate"), state.value.climatePresets[v.vin],
                state.value.climateSync[v.vin], state.value.locations[v.vin], state.value.carWeather[v.vin],
                state.value.homeWeather, state.value.settingsMode, state.value.isPebbleExpanded(v.vin, "climate"),
                state.value.defaultClimatePresets[v.vin],
            )
            ClimatePebble(v, status, seats, climateState, vm, dragHandle)
        }
        // The "charge" slot is the powertrain's energy pebble: charging for an
        // EV/PHEV, a fuel readout for a gas/hybrid car (no charge UI at all).
        "charge" -> if (state.value.hasBattery(v)) {
            val chargeState = stateSlice(
                state, status, enabled, state.value.isPending(v.vin, "charge"), state.value.isPending(v.vin, "chargeLimit"),
                state.value.hasBattery(v), state.value.hasFuel(v), state.value.locations[v.vin],
                state.value.isPebbleExpanded(v.vin, "charge"),
            )
            ChargePebble(v, status, enabled, chargeState, vm, dragHandle)
        } else {
            val fuelState = stateSlice(state, status, state.value.refreshing, state.value.isPebbleExpanded(v.vin, "charge"))
            FuelPebble(v, status, fuelState, vm, dragHandle)
        }
        "location" -> {
            val locationState = stateSlice(
                state, state.value.locations[v.vin], state.value.placeNames[v.vin], state.value.isPending(v.vin, "locate"),
                state.value.carWeather[v.vin], state.value.isPebbleExpanded(v.vin, "location"),
            )
            LocationPebble(v, locationState, vm, dragHandle)
        }
        "weather" -> {
            val weatherState = stateSlice(state, state.value.homeWeather, state.value.isPebbleExpanded(v.vin, "weather"))
            WeatherPebble(v, weatherState, vm, dragHandle)
        }
        // Trip history rides on the EV trip-details endpoint, so EVs only.
        "trips" -> {
            val tripsState = stateSlice(state, state.value.trips[v.vin], state.value.isPending(v.vin, "trips"), state.value.isPebbleExpanded(v.vin, "trips"))
            TripsPebble(v, tripsState, vm, dragHandle)
        }
        "info" -> {
            val infoState = stateSlice(
                state, status, state.value.locations[v.vin], state.value.licensePlates[v.vin], state.value.lastServiceMiles[v.vin],
                state.value.serviceIntervalMiles[v.vin], state.value.refreshing, state.value.hasBattery(v),
                state.value.placeNames[v.vin], state.value.fetchedAt(v), state.value.isPebbleExpanded(v.vin, "info"),
            )
            InfoPebble(v, status, infoState, vm, dragHandle)
        }
        "diagnostics" -> {
            val diagnosticsState = stateSlice(state, status, state.value.hasBattery(v), state.value.isPebbleExpanded(v.vin, "diagnostics"))
            DiagnosticsPebble(v, status, diagnosticsState, vm, dragHandle)
        }
        "ai" -> {
            val aiState = stateSlice(state, v.vin in state.value.aiBusy, state.value.aiSummaries[v.vin], state.value.isPebbleExpanded(v.vin, "ai"))
            AiPebble(v, aiState, vm, dragHandle)
        }
        else -> Spacer(Modifier.fillMaxWidth())
    }
}

/** Optional on-device Gemini Nano summary of the car's last-refreshed status. */
@Composable
internal fun AiPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val busy = v.vin in state.aiBusy
    val summary = state.aiSummaries[v.vin]
    Pebble(
        v, "ai", "AI summary", Icons.Filled.AutoAwesome, state, vm, dragHandle,
        // What the tile can tell you, not what engine it runs on. This was the constant string
        // "On-device Gemini Nano", which as a collapsed summary -- and, on the cover, as the
        // tile's whole headline -- spent the most prominent line saying something that is true
        // of this pebble forever and answers nothing. The engine is still named in the body copy
        // ("generated privately on your device"), where a fact you read once belongs.
        summary = when {
            busy -> "Summarizing…"
            summary != null -> "Summary ready"
            else -> "Not summarized yet"
        },
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        // The one pebble whose subject is not a part of the car, so it is the one
        // that earns a different surface: a gradient marks "this was generated"
        // rather than measured, the same way the Summarize action is the only
        // header action that makes something rather than sending a command.
        //
        // Built from the scheme's own container roles rather than fixed hues, so it
        // follows the user's accent, their vibrancy setting and light/dark with no
        // second palette to maintain -- the mistake ChargeGreen's phone-side
        // re-declaration made, which is why colours live in tokens here.
        //
        // containerColor stays tertiaryContainer underneath. The gradient paints
        // over it, but it is what contentColorFor() reads to pick the text colour,
        // and all three stops are container-toned, so the contrast that colour was
        // chosen for holds across the whole sweep.
        background = {
            val scheme = MaterialTheme.colorScheme
            val brush = remember(scheme.tertiaryContainer, scheme.primaryContainer, scheme.secondaryContainer) {
                Brush.linearGradient(
                    // Diagonal rather than vertical: a pebble is much wider than it
                    // is tall when collapsed, so a vertical sweep would compress to
                    // a flat band and read as a slightly-off solid fill.
                    0f to scheme.tertiaryContainer,
                    0.55f to scheme.primaryContainer.copy(alpha = 0.55f),
                    1f to scheme.secondaryContainer.copy(alpha = 0.65f),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            }
            Spacer(Modifier.matchParentSize().background(brush))
        },
        headerAction = PebbleHeaderAction(
            label = "Summarize",
            icon = Icons.Filled.AutoAwesome,
            onClick = { vm.summarizeCar(v) },
            pending = busy,
        ),
        // NOT alwaysExpandedInSimpleMode -- see the note on LocationPebble. This tile has a
        // summary paragraph and a footnote, not a single setting, and the flag costs it its
        // chevron entirely.
    ) {
        // On the flip cover this tile fills the screen; two short text lines centred
        // in it read as a big empty purple void. Lead with a proper glance hero (big
        // icon + heading + status line) like the other cover tiles, then the copy.
        if (LocalForceExpanded.current) {
            // Shared CoverHero rhythm (converged 34dp icon + headline + status subline),
            // so the AI tile matches Climate/Info/Diagnostics/etc instead of its old
            // ad-hoc 48dp centered column.
            // No cover hero: its value was the tile TITLE verbatim ("AI summary") and its
            // subline was the tile subtitle verbatim ("On-device Gemini Nano"). Four lines
            // carrying two strings, before a word of the actual summary. CoverTile's headline
            // covers it; what follows is the summary itself, which is the point of the tile.
        }
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "Summarize this car's last-refreshed status, generated privately on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
            )
        }
        Text(
            "Reflects the last refresh. Tap Summarize to update.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
        )
    }
}

/**
 * The lock/unlock [StateControl] plus its brand-conditional grouped
 * Flash-lights/Horn-and-lights icon actions -- shared by every place a
 * car's primary quick-action needs to render (the dual-column critical
 * column, [ControlsPebble], and the cover screen's main tile), each
 * supplying its own [contentPadding] to line the icon up with that
 * particular container's own inset convention.
 */
@Composable
internal fun PrimaryActions(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    contentPadding: PaddingValues = PaddingValues(start = 26.dp, end = 8.dp),
) {
    val status = state.statusFor(v)
    Column(Modifier.fillMaxWidth().padding(contentPadding)) {
        StateControl(
            name = "",
            isOn = status?.doorLock,
            stateOn = "Locked", stateOff = "Unlocked",
            turnOn = "Lock", turnOff = "Unlock",
            icon = Icons.Filled.Lock, deactivateIcon = Icons.Filled.LockOpen,
            pending = state.isPending(v.vin, "doors"),
            onActivate = { vm.lock(v) }, onDeactivate = { vm.unlock(v) },
            highlightWhenOff = true,
            offTextColor = MaterialTheme.colorScheme.error,
            // Kia's US API has no equivalent endpoint (see Vehicle.supportsHornLights),
            // so these only appear for Hyundai/Genesis, matching what those apps show.
            // A connected M3 button group with the Lock/Unlock button (see
            // StateControl/connectedGroupShape) -- icon-only, since a labelled
            // "Lights"/"Horn" pill this size squeezed the weighted name/state
            // column (the "Locked"/"Unlocked" label) down to nothing. contentDescription
            // keeps them labelled for TalkBack even with no visible text.
            groupActions = if (v.supportsHornLights) {
                val hlPending = state.isPending(v.vin, "hornLights")
                listOf(
                    GroupIconAction(Icons.Filled.FlashOn, "Flash lights", !hlPending) { vm.flashLights(v) },
                    GroupIconAction(Icons.Filled.Campaign, "Horn & lights", !hlPending) { vm.hornAndLights(v) },
                )
            } else emptyList(),
        )
    }
}
/**
 * A chunky stateful control: shows the current state and a button offering the
 * *opposite* action. The button is always a clearly filled control that morphs
 * from a pill (calm) to a rounded square (highlighted).
 */
@Composable
internal fun StateControl(
    name: String,
    isOn: Boolean?,
    stateOn: String,
    stateOff: String,
    turnOn: String,
    turnOff: String,
    icon: ImageVector,
    deactivateIcon: ImageVector? = null,
    pending: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    enabled: Boolean = true,
    disabledNote: String? = null,
    highlightWhenOff: Boolean = false,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    highlightContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    offTextColor: Color? = null,
    groupActions: List<GroupIconAction> = emptyList(),
) {
    // Which state is the "highlighted" (on) one.
    val highlighted = enabled && (if (highlightWhenOff) isOn == false else isOn == true)
    Row(
        Modifier.fillMaxWidth().height(ControlHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Fill the button's height so the status reads as one tall control.
        Column(Modifier.weight(1f).fillMaxHeight().widthIn(min = 120.dp), verticalArrangement = Arrangement.Center) {
            if (name.isNotBlank()) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            val stateText = when {
                !enabled && disabledNote != null -> disabledNote
                pending -> "Sending…"
                isOn == true -> stateOn
                isOn == false -> stateOff
                else -> "Unknown"
            }
            val stateColorTarget = when {
                !enabled -> LocalContentColor.current.copy(alpha = MutedContentAlpha)
                isOn == false && offTextColor != null -> offTextColor
                highlighted -> highlightColor
                else -> LocalContentColor.current.copy(alpha = MutedContentAlpha)
            }
            val stateColor by androidx.compose.animation.animateColorAsState(
                stateColorTarget,
                animationSpec = tween(250),
                label = "stateColor",
            )
            when {
                // With no title, the lock state is the headline — icon AND word, side by side.
                name.isBlank() -> {
                    val stateIcon = when (isOn) {
                        true -> icon
                        false -> Icons.Filled.LockOpen
                        else -> icon
                    }
                    if (pending) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LoadingIndicator(Modifier.size(22.dp))
                            Text(
                                "Sending…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = stateColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        AnimatedContent(
                            targetState = Pair(stateIcon, stateText),
                            transitionSpec = {
                                (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200))) togetherWith
                                (fadeOut(tween(150)) + scaleOut(targetScale = 1.1f, animationSpec = tween(150)))
                            },
                            // Default is TopStart: "Locked"/"Unlocked" render at
                            // slightly different intrinsic heights, so without
                            // this the old and new icon+label rows didn't align
                            // to the same vertical center during the crossfade,
                            // reading as the whole control nudging on toggle.
                            contentAlignment = Alignment.CenterStart,
                            label = "lockStateAnim",
                        ) { (ic, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // null, not `label`: the Text right after already
                                // carries the same words, so a non-null
                                // description here was a redundant swipe stop
                                // ("Locked" from the icon, then "Locked" again
                                // from the text) -- purely decorative now that
                                // the label is announced once.
                                Icon(ic, contentDescription = null, tint = stateColor, modifier = Modifier.size(22.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = stateColor,
                                    fontWeight = FontWeight.Bold,
                                    // If this column ever gets squeezed tight
                                    // again (groupActions content changes,
                                    // narrower screens), ellipsize instead of
                                    // wrapping mid-word ("Locke"/"d" on two
                                    // lines) -- a clipped label at least still
                                    // reads as one intact word.
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                else -> AnimatedContent(
                    targetState = stateText,
                    transitionSpec = {
                        fadeIn(tween(200)) + slideInVertically { -it / 3 } togetherWith
                        fadeOut(tween(150)) + slideOutVertically { it / 3 }
                    },
                    label = "stateTextAnim",
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = stateColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        val haptics = LocalHaptics.current
        // Any extra icon actions (horn/lights) plus the lock/unlock button
        // form one Material 3 "connected" button group -- a single Row (one
        // child of the outer Row, so its own 2dp spacing isn't also getting
        // the outer Row's 12dp spacedBy piled on top) instead of a separate
        // icon cluster sitting next to an unrelated pill.
        val segmentCount = groupActions.size + 1
        // Bigger, thumb-friendly hit targets on the cover screen (operated by a
        // thumb on a ~1-inch square) than on the phone (mouse-precise finger taps in
        // a full pebble). LocalForceExpanded is true only on the cover.
        val coverTargets = LocalForceExpanded.current
        val groupBtnSize = if (coverTargets) 58.dp else 50.dp
        val actionIconSize = if (coverTargets) 26.dp else 22.dp
        // Standard gap between connected button elements (matches SplitExpandButton's
        // own 3dp gap for visual consistency across all grouped controls).
        // ExpressiveButtonRow, not a plain Row. THIS is the app's clearest "several buttons
        // sharing one space" -- a connected group whose silhouette is one pill -- so it is
        // exactly where pressing one segment should widen it and squeeze the others, with the
        // group's own outer width never changing. Every segment was a bare MorphButton with no
        // press wrapper at all, which is why no amount of work on SafeExpansiveButton ever made
        // this cluster move: there was nothing here to animate.
        //
        // Modifier.size on a segment is not in the way: fixed-size constraints are still
        // coerced into the ones the parent hands down, so the width the group assigns wins.
        ExpressiveButtonRow(spacing = 3.dp, verticalAlignment = Alignment.CenterVertically) {
            groupActions.forEachIndexed { i, action ->
                val actionSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(interactionSource = actionSource, enabled = action.enabled) {
                    MorphButton(
                        onClick = action.onClick,
                        enabled = action.enabled,
                        interactionSource = actionSource,
                        contentPadding = PaddingValues(0.dp),
                        shapeForCorner = { morph, cp -> connectedGroupShape(i, segmentCount, cp, morph) },
                        modifier = Modifier.size(groupBtnSize),
                        // Stated, not derived: contentPadding is zero here, so the padding rule
                        // would say this segment has nothing to give -- which is precisely why
                        // the horn/lights segments never moved. The room is around the glyph
                        // inside the fixed box, not in contentPadding.
                        compressible = IconButtonCompressible,
                    ) { Icon(action.icon, contentDescription = action.contentDescription, modifier = Modifier.size(actionIconSize)) }
                }
            }
            // Pill when off, rounded rectangle + highlight colour when on - same
            // as the climate/charge controls -- except when it's part of a
            // group, where the connected shape takes over (see MorphButton's
            // shape param doc): a connected group's silhouette is static, not
            // something one segment morphs independently of the others.
            val mainSource = remember { MutableInteractionSource() }
            SafeExpansiveButton(interactionSource = mainSource, enabled = enabled && !pending) {
                MorphButton(
                    onClick = { if (isOn == true) onDeactivate() else onActivate() },
                    onClickHaptic = { haptics?.heavy() },
                    enabled = enabled && !pending,
                    interactionSource = mainSource,
                    active = highlighted,
                    activeContainerColor = highlightColor,
                    activeContentColor = highlightContentColor,
                    shapeForCorner = if (groupActions.isNotEmpty()) {
                        { morph, cp -> connectedGroupShape(segmentCount - 1, segmentCount, cp, morph) }
                    } else {
                        null
                    },
                    // Same pill height as the pebble header actions (the row stays
                    // ControlHeight tall, so the button is vertically centred in it);
                    // taller on the cover for a thumb.
                    modifier = Modifier.heightIn(min = groupBtnSize),
                ) {
                    val buttonIcon = if (isOn == true) (deactivateIcon ?: icon) else icon
                    MorphButtonLabel(buttonIcon, if (isOn == true) turnOff else turnOn, pending, iconSize = actionIconSize)
                }
            }
        }
    }
}




