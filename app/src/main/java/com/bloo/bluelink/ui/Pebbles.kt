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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.connectedGroupShape
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.supportsHornLights
import com.bloo.bluelink.data.isGen5W
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt
import com.bloo.uicommon.ReorderColumn
import com.bloo.uicommon.animatePlacement
import dev.chrisbanes.haze.HazeState

/** A friendly label for a pebble/section id. */
internal fun sectionLabel(section: String): String = when (section) {
    "charge" -> "Charge / fuel"
    "climate" -> "Climate"
    "location" -> "Location"
    "trips" -> "Trips"
    "info" -> "Car info"
    "diagnostics" -> "Diagnostics"
    "controls" -> "Lock / climate"
    // Was falling through to the generic capitalise, which renders "Ai" -- visible in the
    // hot-spot slot's pin dropdown and its pinned-section caption, right next to a pebble
    // whose own title is "AI summary". The name of a section belongs in this one `when`, so
    // this is also where the SettingsCards copy of the exact same mapping (an 8-entry map
    // allocated fresh on every recomposition of the per-car "Sections shown" group, with these
    // same seven strings verbatim) now resolves through instead of re-declaring them.
    "ai" -> "AI summary"
    else -> section.replaceFirstChar { it.uppercase() }
}

/**
 * The dual-column "hot spot": slots under the car-info column for pinned pebbles.
 * Slot 1: Primary pebble (defaulting to "controls" with lights/horn, but removable)
 * Slot 2: Secondary user-selectable slot where they can pin additional pebbles
 */
@Composable
internal fun HotspotSlot(
    v: Vehicle,
    hotspots: List<String>,
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
    val haptics = LocalHaptics.current
    val hotDrag = LocalHotSeatDrag.current
    val hovered = hotDrag?.overSlot == true

    // Available pebbles for pinning (excludes "summary" and hidden pebbles)
    val allAvailable = remember(
        state.sectionOrders[v.vin], state.hiddenPebbles, state.aiEnabled, state.hasBattery(v),
        v.isGen5W, state.platforms[v.vin], state.updateAvailable, state.updateTileDismissed,
    ) {
        state.sectionsFor(v).filter {
            it != "summary" && state.isSectionAvailable(v, it)
        }
    }

    // Primary slot pebble (defaults to "controls" with lights/horn)
    val primaryPebble = hotspots.firstOrNull() ?: "controls"

    // Secondary slot pebble (if any)
    val secondaryPebble = hotspots.getOrNull(1)

    // Available pebbles not yet pinned
    val unpinned = remember(primaryPebble, secondaryPebble, allAvailable) {
        allAvailable.filter { it != primaryPebble && it != secondaryPebble }
    }

    // 12dp between the two slots, the SAME gap every other pair of pebbles in this view sits
    // at (ReorderColumn's own default spacing for the pebble list, and ExpandedCar's
    // `spacedBy(12.dp)` for the column this slot lives in). It was 2.dp, which is the one
    // spacing in the multi-column layout that made the pinned pebbles read as a stuck-together
    // pair of a different kind from the cards around them -- part of the same report that the
    // controls pebble looks unlike every other pebble there. The INNER column below keeps its
    // own 2dp: that gap is between the secondary slot's small pin/Remove label and the pebble
    // that label belongs to, where tight is the point.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // PRIMARY SLOT (Top) - Always "controls" (lights/horn), permanently pinned
        // No removal option - this slot is hardcoded and locked
        CompositionLocalProvider(LocalForceExpanded provides true) {
            SinglePebble(primaryPebble, v, stateSource, vm, Modifier)
        }

        // SECONDARY SLOT (Bottom) - User-selectable
        if (secondaryPebble != null) {
            // Secondary slot is occupied - show the pebble with removal option
            var lifted by remember(secondaryPebble) { mutableStateOf(false) }
            var dragY by remember(secondaryPebble) { mutableFloatStateOf(0f) }
            val lift by animateFloatAsState(if (lifted) 1.03f else 1f, label = "unpinLift")

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (lifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (lifted) "Release to unpin" else sectionLabel(secondaryPebble),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MorphTextButton("Remove", onClick = { vm.setHotspot(v, secondaryPebble) })
                }
                CompositionLocalProvider(LocalForceExpanded provides true) {
                    Box(
                        Modifier
                            .graphicsLayer { scaleX = lift; scaleY = lift }
                            .pointerInput(secondaryPebble) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragY = 0f; lifted = true; haptics?.tick() },
                                    onDrag = { change, amt -> change.consume(); dragY += abs(amt.x) + abs(amt.y) },
                                    onDragEnd = {
                                        lifted = false
                                        if (dragY > 56f) { haptics?.heavy(); vm.setHotspot(v, secondaryPebble) }
                                    },
                                    onDragCancel = { lifted = false },
                                )
                            },
                    ) {
                        SinglePebble(secondaryPebble, v, stateSource, vm, Modifier)
                    }
                }
            }
        } else {
            // Secondary slot is empty - show "Add a pebble" button if pebbles are available
            if (unpinned.isNotEmpty()) {
                var menu by remember { mutableStateOf(false) }
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
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        MorphButtonLabel(Icons.Filled.PushPin, if (hovered) "Release to pin" else "Add a pebble", pending = false)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        unpinned.forEach { sec ->
                            DropdownMenuItem(
                                text = { Text(sectionLabel(sec)) },
                                onClick = { vm.setHotspot(v, sec); menu = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How far the floating overlays (dots, buttons) slide down during a refresh. */
internal val RefreshPullShift = 96.dp

/**
 * The one shared "refreshing" badge: a [GlassSurface] circle (real backdrop
 * blur when [hazeState] is supplied, the same flat-tint fallback every other
 * glass surface uses otherwise) holding a spinner, sliding down from fully
 * off-screen above the content at `progress = 0` to just below the status bar
 * at `progress = 1`. Used by both [Refreshable]'s per-car pull gesture
 * (`progress` tracks the live pull distance, so it follows the user's finger)
 * and the multi-car grid's own "still refreshing" badge (GarageScreen.kt,
 * which has no pull gesture of its own and just animates `progress` between 0
 * and 1 off the plain `refreshing` boolean) -- previously two independently
 * hand-rolled indicators with different sizes, positioning math, and
 * containers (one a flat-tinted `PullToRefreshDefaults` widget, the other a
 * real-blur `GlassSurface`).
 *
 * [progress] is a LAMBDA, not a plain Float: [Refreshable] needs to read a
 * live drag distance (`ptrState.distanceFraction`) on every frame of a pull
 * gesture, and a plain parameter would be read at composition time -- which
 * would recompose the whole caller (its entire `content()`, one whole car
 * card) on every pixel of the drag. Deferring the read into this offset{}
 * lambda keeps that a pure layout-phase relayout of just this small badge.
 */
@Composable
internal fun RefreshIndicatorBadge(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    progress: () -> Float,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    GlassSurface(
        shape = CircleShape,
        hazeState = hazeState,
        modifier = modifier
            .size(HeaderButtonSize)
            .offset {
                val p = progress().coerceIn(0f, 1f)
                val offScreenPx = -(topInset + 56.dp).roundToPx()
                val onScreenPx = (topInset + 28.dp).roundToPx()
                IntOffset(0, offScreenPx + ((onScreenPx - offScreenPx) * p).roundToInt())
            },
    ) {
        LoadingIndicator()
    }
}

/**
 * Wraps content with the pull-to-refresh gesture with an overlay indicator.
 * Delegates the actual gesture recognition/animation state to Material 3's
 * [rememberPullToRefreshState] (`ptrState`); this composable's own job is
 * publishing that pull distance out to [LocalPullFraction] (so sibling
 * overlays elsewhere in [GarageScreen] can react to the live pull, not just
 * the boolean `state.refreshing`), and driving [RefreshIndicatorBadge] off
 * that same distance so it can slide fully off-screen above the content when
 * idle and only ease into view as the user pulls.
 *
 * [onRefresh] is a plain lambda, not a fixed `vm.refreshStatus(v)` call, so
 * this same wrapper also covers [GarageStatusCard] (Guard.kt) -- the "no
 * vehicles"/"no connection" page has no [Vehicle] to refresh, just
 * [AppViewModel.loadGarage] to retry, and reported directly as wanting to be
 * "just another card like the rest of them" rather than its own one-off
 * Reload button.
 */
@Composable
internal fun Refreshable(
    // The single UiState field this needs, and NOT the whole UiState: passing the state object
    // subscribed every caller's composition to every emission -- a weather tick for another car,
    // an AI probe, a log line -- for all three live pager pages at once, which is exactly what
    // the callers' State<UiState> indirection exists to avoid.
    refreshing: Boolean,
    onRefresh: () -> Unit,
    hideIndicator: Boolean = false,
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val ptrState = rememberPullToRefreshState()
    val haptics = LocalHaptics.current

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
                onRefresh = { haptics?.diceRoll(); onRefresh() },
            ),
    ) {
        // Content stays full-size and edge-to-edge; never shifted down.
        content()
        // Indicator floats above content as a z-elevated overlay. The
        // progress read (ptrState.distanceFraction) happens inside
        // RefreshIndicatorBadge's own offset{} lambda, which only runs in the
        // layout phase -- reading it directly in THIS composable's body would
        // recompose this entire Box (and everything content() renders, the
        // whole car card) on every pixel of the pull gesture, not just
        // re-layout the small indicator.
        if (!hideIndicator) {
            RefreshIndicatorBadge(
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.TopCenter),
            ) { if (refreshing) 1f else ptrState.distanceFraction }
        }
    }
}
/** Hero image + gauge (expanded view). */
@Composable
internal fun CriticalContent(v: Vehicle, stateSource: State<UiState>, vm: AppViewModel, onCollapse: (() -> Unit)? = null) {
    val state = stateSource.value
    val status = state.statusFor(v)
    val metric = LocalAppearance.current.unitSystem == "metric"
    val heroState = remember(
        status, state.imageUrls[v.vin], state.hasBattery(v), state.hasFuel(v),
        state.locations[v.vin], state.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
    ) { state }
    HeroHeader(
        v, status, heroState.imageUrls[v.vin], heroState.hasBattery(v), heroState.hasFuel(v), vm,
        heroState.drivingLabel(v), metric = metric,
        photoExpanded = heroState.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
        expandAction = onCollapse?.let {
            PebbleHeaderAction(label = "Back to all cars", icon = Icons.Filled.ArrowBack, onClick = it)
        },
    )
}

/**
 * The lock/unlock quick control: the morphing [StateControl] with its status on
 * the left, in a pebble-shaped card with no header row and no expand chevron.
 *
 * It CANNOT go through [Pebble]/[PebbleShell] -- everything that shell gives a
 * pebble is a header row (icon + title + chevron/action) plus a body that
 * discloses behind it, and this pebble has neither: its one control is always
 * visible, and its recent-commands history is revealed by pressing the card's own
 * background rather than by a chevron (see `showHistory` below). So it rolls its
 * own Surface -- but every part of the shell's look that ISN'T the header is
 * deliberately mirrored here, value for value, because it sits in the same column
 * as cards that DO come from PebbleShell and any drift reads as a different kind
 * of object: the same [pebbleCardEdge] treatment, the same containerColor and its
 * matching content colour, the same 1dp card shadow, the same corner radii on the
 * same springs, and the same content insets. Anything changed in PebbleShell's own
 * card (not its header) wants changing here too.
 *
 * It can still be long-pressed and dragged to reorder, like any pebble.
 */
@Composable
internal fun ControlsPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
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
    // This pebble is "expanded" in exactly the two senses PebbleShell means it: a body is
    // actually showing under the control (the revealed history), or it is in a context that
    // force-expands every pebble in it (the wide layout's HotspotSlot, which wraps both its
    // slots in LocalForceExpanded = true).
    //
    // The corner has to follow that, because it was the reported "the controls pebble looks
    // different from every other pebble" in the multi-column layout: this was a FIXED
    // PebbleCornerCollapsed (38dp) capsule, so in the hot-spot column it sat directly above a
    // force-expanded pebble drawn at PebbleCornerExpanded (20dp) -- two cards, one column, two
    // different silhouettes. Same two targets and the same two springs PebbleShell's own
    // `corner` uses, so the two cards are the same shape at the same time, in the same motion.
    //
    // 38dp stays a plain constant rather than PebbleShell's measured headerRowHeightPx / 2: the
    // reason that had to be measured is that a header row's height varies with its content, and
    // this pebble's resting content is hard-pinned to ControlHeight below, so half of it is
    // knowable up front and is exactly PebbleCornerCollapsed.
    val forceExpanded = LocalForceExpanded.current
    val expanded = forceExpanded || showHistory
    val corner by animateDpAsState(
        targetValue = if (expanded) PebbleCornerExpanded else PebbleCornerCollapsed,
        animationSpec = if (expanded) {
            lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        } else {
            lowPowerAwareSpring(dampingRatio = PebbleCloseDamping, stiffness = PebbleBounceStiffness)
        },
        label = "controlsPebbleCorner",
    )
    val shape = RoundedCornerShape(corner)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragHandle)
            .pointerInput(v.vin) { detectTapGestures { showHistory = !showHistory } }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(
                        if (showHistory) "Hide recent remote actions" else "Show recent remote actions",
                    ) { showHistory = !showHistory; true },
                )
            }
            .pebbleCardEdge(shape, pebbleOutline),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        // contentColorFor(the container), exactly as PebbleShell's own
        // CardDefaults.cardColors(...) resolves it -- so onSurfaceVariant, not the onSurface
        // this used to hardcode. Every muted label in the app is LocalContentColor at
        // MutedContentAlpha, so a card handing its content the WRONG base colour drifts every
        // one of them: "Locked"/"Unlocked" and the horn/lights icons here were rendering off a
        // brighter base than the identically-styled text one pebble below.
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceVariant),
        // Material's Card carries 1dp of elevation by default (CardTokens.ContainerElevation),
        // which every PebbleShell card therefore gets and a bare Surface does not -- and
        // pebbleCardEdge deliberately draws NO shadow in light mode, so light mode was the one
        // place nothing else was covering for it and this card read visibly flatter than its
        // neighbours. Tonal elevation is left at 0 on purpose: it would be a no-op anyway
        // (Surface only tints when the colour IS colorScheme.surface) and the shadow is the
        // part Card actually contributes here.
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Asymmetric padding to match pebble header alignment: more left, less right.
            // Height stays pinned here (not on the Surface) so the pebble keeps its exact
            // resting silhouette and only grows when the history is actually showing.
            Box(Modifier.fillMaxWidth().height(ControlHeight).padding(start = 12.dp, end = 4.dp)) {
                // PrimaryActions' own default start padding (26.dp) plus this
                // Box's 12.dp put the lock icon noticeably further right than
                // every other pebble's header icon (Charge, Climate, ...), which
                // only ever get Pebble's flat PebbleContentInset row padding. The 4.dp here
                // lines the two icons up: 4 + this Box's own 12 == PebbleContentInset.
                //
                // The end inset is split the same way and lands on 12dp (4 here + 8 below), not
                // the 16dp it used to total: PebbleShell's header row is deliberately asymmetric
                // -- `padding(start = 16.dp, end = 12.dp)` -- so a pebble's trailing control sits
                // 12dp from the card edge. At 16dp this pebble's button group stopped 4dp short
                // of where every other pebble's chevron/action stops, which reads as a narrower
                // card rather than as a different inset.
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

/** The reorderable pebble stack for a car.
 *
 * [pinHotspot] defaults to true (exclude "controls" + any secondary pin, on
 * the assumption the caller renders them separately via [HotspotSlot] --
 * true for the wide/dual-column [ExpandedCar]). [VehicleDetailContent]'s
 * single-column layout has no separate hero-column slot to pin anything
 * into, so it passes false: "controls" and any secondary pin then flow
 * through this list like any other pebble, landing in their normal
 * [DEFAULT_SECTIONS] position (right after "summary"/"update") instead of
 * vanishing from the list with nothing rendering them in their place. */
@Composable
internal fun PebbleList(
    v: Vehicle,
    state: State<UiState>,
    vm: AppViewModel,
    exclude: Set<String> = emptySet(),
    pinHotspot: Boolean = true,
    /** Forwarded to the "summary" hero pebble only -- see [HeroHeader]'s `expandAction`. */
    onExpand: (() -> Unit)? = null,
) {
    val sel = state.value
    val allSections = sel.sectionsFor(v)
    val hasBattery = sel.hasBattery(v)
    // Exclude any pebbles pinned to the hotspot (both primary and secondary slots)
    // -- but only when the caller is actually rendering them separately. See
    // [pinHotspot]'s own doc.
    val pinnedPebbles = if (pinHotspot) sel.hotspotFor(v.vin) else emptyList()
    val allExclude = remember(exclude, pinnedPebbles) {
        exclude + pinnedPebbles
    }
    val sections = remember(
        allSections, allExclude, sel.hiddenPebbles, sel.aiEnabled, hasBattery, v.isGen5W, sel.platforms[v.vin],
        sel.updateAvailable, sel.updateTileDismissed,
    ) {
        allSections.filter {
            it !in allExclude && sel.isSectionAvailable(v, it)
        }
    }
    val hotDrag = LocalHotSeatDrag.current
    // PERF: each car-pager page composes this whole pebble stack. Composing all
    // 8-10 pebbles eagerly (incl. ClimatePebble/ChargePebble's top-level effects,
    // which run BEFORE their Pebble() call regardless of collapsed state) on the
    // fling-settle frame is the biggest remaining car-swipe cost. So: compose the
    // hero + first EAGER_PEBBLES sections immediately (they're the only ones
    // above the fold), stub the rest with a collapsed-height placeholder, then
    // fill them in ONE AT A TIME, one per frame, rather than all at once.
    // Keyed on VIN so a disposed→recomposed page re-defers cheaply; a page kept
    // warm by beyondViewportPageCount=1 fills before the user ever swipes to it.
    // CRITICAL: `items` stays the FULL section list, so ReorderColumn's per-item
    // Box/key/animatePlacement/onSizeChanged/drag/semantics all exist from frame
    // one — only the body inside the content lambda is deferred, so the reorder
    // model is 100% intact and the off-screen stub→real swap is never visible.
    //
    // One-at-a-time, not the single "wait one frame, then fill everything" this used to
    // be: on a wide/foldable screen (GarageScreen's perPage > 1, multiple cars sharing one
    // page), every car's PebbleList ran that same one-frame defer independently -- so
    // frame 2 of a page settle didn't just fill ONE car's remaining 5-7 pebbles, it filled
    // EVERY visible car's remaining pebbles simultaneously, concentrating the exact cost
    // this mechanism exists to spread out into one single, now-bigger frame. Reported as
    // sluggish switching cars specifically on a large-screen foldable. Filling one pebble
    // per frame spreads that same total work across however many non-eager pebbles there
    // are, so no single frame pays for more than one pebble's worth of composition,
    // regardless of how many cars share the page.
    var filledCount by remember(v.vin) { mutableIntStateOf(0) }
    LaunchedEffect(v.vin, sections.size) {
        val remaining = (sections.size - EAGER_PEBBLES).coerceAtLeast(0)
        repeat(remaining) {
            withFrameNanos { }
            filledCount++
        }
    }
    val eager = remember(sections) { sections.take(EAGER_PEBBLES).toSet() }
    ReorderColumn(
        items = sections,
        keyOf = { it },
        onReorder = { newVisible ->
            // Merge the reordered visible items back into the full section order so
            // excluded ones (the pinned hot-spot pebbles, summary, hidden) keep
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
        // This section's own position among the NON-eager ones, so filledCount (which
        // ticks up by one per frame) can unlock them in list order, first-below-the-fold
        // first -- sections.indexOf is O(n) on an 8-10 item list, negligible next to the
        // pebble composition this whole mechanism exists to defer.
        val nonEagerIndex = sections.indexOf(section) - EAGER_PEBBLES
        if (section in eager || nonEagerIndex < filledCount) {
            SinglePebble(section, v, state, vm, dragHandle, onExpand = onExpand)
        } else {
            // Off-screen placeholder, up to a few frames now rather than always exactly
            // one: reserves ~collapsed pebble height so the list doesn't visibly jump
            // when the real body fills in, and carries the dragHandle so ReorderColumn's
            // item is fully formed. Below the fold, so this transient state is never
            // seen or interacted with.
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
internal fun SinglePebble(section: String, v: Vehicle, state: State<UiState>, vm: AppViewModel, dragHandle: Modifier, onExpand: (() -> Unit)? = null) {
    val status = state.value.statusFor(v)
    val metric = LocalAppearance.current.unitSystem == "metric"
    when (section) {
        "summary" -> {
            val heroState = stateSlice(
                state, status, state.value.imageUrls[v.vin], state.value.hasBattery(v), state.value.hasFuel(v),
                state.value.locations[v.vin], state.value.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
            )
            HeroHeader(
                v, status, heroState.imageUrls[v.vin], heroState.hasBattery(v), heroState.hasFuel(v), vm,
                heroState.drivingLabel(v), dragHandle = dragHandle, metric = metric,
                photoExpanded = heroState.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
                expandAction = onExpand?.let {
                    PebbleHeaderAction(label = "Expand to full screen", icon = Icons.Filled.Fullscreen, onClick = it)
                },
            )
        }
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
            val seats = state.value.seatConfigFor(v)
            val climateState = stateSlice(
                state, status, seats, state.value.isPending(v.vin, "climate"), state.value.climatePresets[v.vin],
                state.value.climateSync[v.vin], state.value.locations[v.vin], state.value.carWeather[v.vin],
                state.value.homeWeather, state.value.settingsMode, state.value.isPebbleExpanded(v.vin, "climate"),
                state.value.defaultClimatePresets[v.vin],
            )
            ClimatePebble(v, status, seats, climateState, vm, dragHandle)
        }
        "charge" -> if (state.value.hasBattery(v)) {
            val enabled = !state.value.loading
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
                state.value.carWeather[v.vin], state.value.deviceLocation, state.value.isPebbleExpanded(v.vin, "location"),
            )
            LocationPebble(v, locationState, vm, dragHandle)
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
            // Bulleted, not one raw string: the model's own prompt asks for a "* " bullet per
            // fact, and drawing that straight through showed the literal asterisk as text --
            // "* Daisy (2025) is charging..." -- confirmed from a real screenshot. Any line
            // that IS a bullet gets a real one; anything else (a stray lead-in sentence, if
            // the model ever writes one) prints as plain text, so this degrades safely rather
            // than assuming every summary is bulleted.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                summary.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                    val bulleted = line.startsWith("* ") || line.startsWith("- ")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (bulleted) {
                            Text(
                                "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                            )
                        }
                        Text(
                            if (bulleted) line.substring(2) else line,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
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
    // The row's own real, measured width -- onSizeChanged, not BoxWithConstraints. Both
    // report the same number, but BoxWithConstraints is a SubcomposeLayout under the hood:
    // its content is composed in a SEPARATE pass, deferred until the constraints are known,
    // which is real, avoidable overhead on something this exact composable is not rare --
    // it draws EVERY car's Lock/Unlock row, so every car page composing during a swipe
    // (beyondViewportPageCount = 1 means up to three at once) paid it. onSizeChanged is a
    // plain draw/layout callback on the SAME Row that was always here, no extra composition
    // pass at all -- the identical trade PebbleHeaderHeight's own rowHeightDp already makes
    // a few lines below in this same file's sibling function.
    //
    // Default a generous 1000.dp, not a small one: this value is read to CAP the button
    // group, so an inaccurate LOW default (before the real width lands, one frame after
    // first composition) would wrongly force it to compact on that first frame. A HIGH
    // default instead means the first frame renders exactly as it always did -- uncapped --
    // and the real cap takes over a frame later, imperceptible and safe in the direction
    // that matters (never a false compact, only a one-frame-late correct one).
    var rowWidthDp by remember { mutableStateOf(1000.dp) }
    val density = LocalDensity.current
    // Without this cap, the button group -- a plain (non-weighted) Row child -- is measured
    // against the row's FULL width, because Row measures non-weighted children BEFORE it
    // knows what a weighted sibling (the name/state column, `widthIn(min = 120.dp)` a few
    // lines down) will need. That min is a REQUEST, not a guarantee the Row's own weight
    // arithmetic honours on its own: when the row overall is too narrow for both, Row still
    // hands the column `Constraints(minWidth = share, maxWidth = share)` off its OWN
    // (button-first) division of space, and widthIn(min) cannot raise that ABOVE an
    // already-fixed, SMALLER incoming max -- it coerces down to fit the parent's hard upper
    // bound instead of forcing the parent to overflow, which is the opposite of what a
    // "floor" sounds like it should do. So on a narrow enough row, "Unlocked" truncated to
    // "Unlock…" while the button group beside it kept its own full, unconstrained width --
    // reported from a real screenshot, and the fix genuinely requires the button group's own
    // width to be bounded first, not a bigger number on the column's own floor (already tried
    // once, on the pebble-header version of this exact bug, and it only bought a bit more
    // room rather than fixing the actual priority).
    val groupMaxWidth = (rowWidthDp - 120.dp - 12.dp).coerceAtLeast(0.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(ControlHeight)
            .onSizeChanged { rowWidthDp = with(density) { it.width.toDp() } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Fill the button's height so the status reads as one tall control.
        // Standard spacing between label and buttons, matching other pebble controls.
        Column(Modifier.fillMaxHeight().widthIn(min = 120.dp), verticalArrangement = Arrangement.Center) {
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
        // 12dp spacing between text and buttons to match the previous spacedBy arrangement
        Spacer(Modifier.width(12.dp))
        val haptics = LocalHaptics.current
        // Any extra icon actions (horn/lights) plus the lock/unlock button
        // form one Material 3 "connected" button group -- a single Row (one
        // child of the outer Row, so its own 2dp spacing isn't also getting
        // the outer Row's 12dp spacedBy piled on top) instead of a separate
        // icon cluster sitting next to an unrelated pill.
        val segmentCount = groupActions.size + 1
        // Bigger, thumb-friendly hit targets on the cover screen (operated by a
        // thumb on a ~1-inch square) than on the phone (mouse-precise finger taps in
        // a full pebble).
        //
        // Gated on LocalPebbleFillHeight, NOT LocalForceExpanded: the comment here used to
        // claim LocalForceExpanded is "true only on the cover" and that was simply wrong --
        // the wide layout's HotspotSlot provides it too, for both of its slots (see
        // HotspotSlot). Only the cover provides LocalPebbleFillHeight (see its doc in
        // Widgets.kt), so that is the one that actually means "cover".
        //
        // This was the reported "the controls pebble looks different and worse" in the
        // multi-column layout, and it was visible ONLY there: ControlsPebble is this
        // composable's only caller, and "controls" has no cover tile at all (see
        // CompactCar's `tiles` mapping), so the cover-sized 58dp/26dp targets could never
        // reach the cover and only ever fired in the hot-spot column -- an over-filled band
        // of chunky buttons in a 76dp-tall card, 8dp taller and with 4dp bigger icons than
        // the identical control renders at in the single-column list, and eating 24dp more
        // of the row's width away from the "Locked"/"Unlocked" label beside it (see
        // groupMaxWidth). The phone sizes are the right ones for a pebble in a column,
        // which is what the hot-spot slot is.
        //
        // Kept as a branch rather than deleted: it is the correct treatment for a genuine
        // cover tile, and the cover already renders pebbles through this same SinglePebble
        // path, so a "controls" tile becoming available there needs no new plumbing.
        val coverTargets = LocalPebbleFillHeight.current
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
        // wrap = false: this is a CONNECTED group -- one silhouette with seams in it -- so a
        // seam appearing on the next line down would not be a control at all. Too narrow, and
        // the segments compact instead.
        val mainSource = remember { MutableInteractionSource() }
        // Lock/unlock button content: reserves width for both "Lock" and "Unlock" labels
        // so the button doesn't collapse during the optimistic state flip mid-release.
        // See MorphButton call below for why this matters.
        val lockContent: @Composable () -> Unit = {
            Box(contentAlignment = Alignment.Center) {
                val buttonIcon = if (isOn == true) (deactivateIcon ?: icon) else icon
                MorphButtonLabel(buttonIcon, if (isOn == true) turnOff else turnOn, pending, iconSize = actionIconSize)
                Box(Modifier.alpha(0f)) {
                    MorphButtonLabel(icon, if (isOn == true) turnOn else turnOff, false, iconSize = actionIconSize)
                }
            }
        }
        if (groupActions.isEmpty()) {
            // No ExpressiveButtonRow at all here -- most cars have no horn/lights support
            // (Kia's US API has none, see the doc above), so this is the common case, and it
            // was reported directly as "the unlock button doesn't animate when tapped": a
            // connected group's OWN defining rule is that its outer footprint never changes
            // on press -- growth is redistributed FROM a neighbour, never from thin air (see
            // ExpressiveButtonGroup's/ExpressivePressGrowth's own doc: "a member with none
            // carries nothing to reserve for"). A lone member has no neighbour to take width
            // from, so wrapping it in the group gave it a press fraction that was faithfully
            // computed and just as faithfully multiplied by a reserve of zero -- not a bug in
            // the animation itself, a design that only works with two or more real segments,
            // silently applied to the one-segment case too. SafeExpansiveButton's OTHER path
            // (outside a group) grows the button for real, which is exactly the fallback a
            // solitary button wants and every other lone MorphButton in the app already gets.
            SafeExpansiveButton(
                interactionSource = mainSource,
                enabled = enabled && !pending,
                // Same cap the group version applies via its own Modifier -- see groupMaxWidth's
                // doc above for why this can't be worked out from inside the button itself.
                modifier = Modifier.widthIn(max = groupMaxWidth),
            ) {
                MorphButton(
                    onClick = { if (isOn == true) onDeactivate() else onActivate() },
                    onClickHaptic = { haptics?.heavy() },
                    enabled = enabled && !pending,
                    interactionSource = mainSource,
                    active = highlighted,
                    activeContainerColor = highlightColor,
                    activeContentColor = highlightContentColor,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.heightIn(min = groupBtnSize),
                ) {
                    lockContent()
                }
            }
        } else {
            ExpressiveButtonRow(
                // Caps this group's own room at groupMaxWidth -- see that val's own doc above
                // for why the group cannot work this out for itself.
                modifier = Modifier.widthIn(max = groupMaxWidth),
                spacing = 3.dp,
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
                wrap = false,
            ) {
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
                        ) { Icon(action.icon, contentDescription = action.contentDescription, modifier = Modifier.size(actionIconSize)) }
                    }
                }
                // Pill when off, rounded rectangle + highlight colour when on - same
                // as the climate/charge controls -- except when it's part of a
                // group, where the connected shape takes over (see MorphButton's
                // shape param doc): a connected group's silhouette is static, not
                // something one segment morphs independently of the others.
                SafeExpansiveButton(interactionSource = mainSource, enabled = enabled && !pending) {
                    MorphButton(
                        onClick = { if (isOn == true) onDeactivate() else onActivate() },
                        onClickHaptic = { haptics?.heavy() },
                        enabled = enabled && !pending,
                        interactionSource = mainSource,
                        active = highlighted,
                        activeContainerColor = highlightColor,
                        activeContentColor = highlightContentColor,
                        shapeForCorner = { morph, cp -> connectedGroupShape(segmentCount - 1, segmentCount, cp, morph) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier.heightIn(min = groupBtnSize),
                    ) {
                        lockContent()
                    }
                }
            }
        }
    }
}




