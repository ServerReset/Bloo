package com.bloo.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AirlineSeatReclineNormal
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.curvedComposable
import androidx.wear.compose.foundation.curvedRow
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.WearWeather
import com.bloo.bluelink.data.degLabel
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.formatSpeed
import com.bloo.bluelink.data.links
import com.bloo.wear.CarView
import com.bloo.wear.WearRemote
import com.bloo.wear.WearPebbles
import com.bloo.wear.WearTiles
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import com.bloo.wear.seatStepLabels
import com.bloo.uicommon.dropShadow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Synthetic tile key for the alerts card (not part of the user-orderable set). */
private const val TILE_ALERTS = "alerts"

/** Number of active warnings (open doors/windows/trunk/hood + tire/fluid/key alerts). */
private val CarView.alertCount: Int
    get() = doorsOpen.size + windowsOpen.size +
        (if (trunkOpen) 1 else 0) + (if (hoodOpen) 1 else 0) +
        (if (tireWarning) 1 else 0) + (if (lowFuel) 1 else 0) +
        (if (washerLow) 1 else 0) + (if (brakeLow) 1 else 0) + (if (keyFobLow) 1 else 0)

@Composable
fun HomeScreen(vm: WearViewModel, ui: WearUi, onSettings: () -> Unit, onTrips: (String) -> Unit, onReorder: (String) -> Unit = {}) {
    if (ui.cars.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                Icon(
                    Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Text(
                    "No cars yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Open Bloo on your phone to sign in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }
    val count = ui.cars.size

    // Kept outside key() so scroll positions survive when the VIN list refreshes.
    val listStates = remember { mutableStateMapOf<String, ScalingLazyListState>() }
    LaunchedEffect(ui.cars) {
        // Evict scroll state for removed cars so the map can't grow unbounded
        // across a session of adding/removing cars.
        listStates.keys.retainAll(ui.cars.map { it.vin }.toSet())
    }

    key(ui.cars.map { it.vin }) {
        val carPager = rememberPagerState(initialPage = 0) { count }

        val activeCarIndex by remember { derivedStateOf { carPager.settledPage } }

        var lastShownVin by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(activeCarIndex) {
            val vin = ui.cars.getOrNull(activeCarIndex)?.vin
            if (vin != null && vin != lastShownVin) {
                lastShownVin = vin
                vm.onCarShown(vin)
            }
        }

        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = carPager,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                // Smooth fade + subtle squeeze as pages leave/enter.
                val pageOff by remember(page) {
                    derivedStateOf {
                        ((page - carPager.currentPage).toFloat() +
                            carPager.currentPageOffsetFraction).let { abs(it).coerceIn(0f, 1f) }
                    }
                }
                val car = ui.cars[page]
                // active = only the settled page claims rotary focus / input.
                val active = page == carPager.settledPage && !carPager.isScrollInProgress
                val carRoles = ui.settings?.carColors?.get(car.vin)
                val body: @Composable () -> Unit = {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha  = 1f - pageOff * 0.28f
                            scaleX = 1f - pageOff * 0.03f
                            scaleY = 1f - pageOff * 0.03f
                        }
                    ) {
                        CarColumn(vm, ui, car, listStates, onSettings, onTrips, onReorder, active)
                    }
                }
                if (carRoles != null) {
                    val carScheme = remember(carRoles) { schemeFrom(carRoles) }
                    MaterialTheme(colorScheme = carScheme) { body() }
                } else body()
            }
            CurvedIndicator(count, carPager.currentPage, anchor = 90f)
            // Shown once for the whole screen, above all pages.
            MessageSnackbar(ui.message, onDismiss = { vm.dismissMessage() })
        }
    }
}

/** The ordered tiles actually shown for this car (conditions resolved once). */
private fun visibleTiles(ui: WearUi, car: CarView): List<String> {
    val hasAlerts = car.alertCount > 0
    val out = ArrayList<String>()
    if (hasAlerts) out.add(TILE_ALERTS)
    // Tile order is derived from this car's pebble order, kept in sync with the
    // phone (one pebble can expand into several tiles); pebbles the user hid on
    // the phone are dropped so a hidden section doesn't still show up here.
    val hidden = ui.settings?.hiddenSections?.get(car.vin).orEmpty()
    for (key in WearPebbles.tilesFor(ui.pebbleOrderFor(car.vin), hidden)) {
        val show = when (key) {
            // Always shown so you can save the first preset from the watch.
            WearTiles.PRESETS -> true
            WearTiles.CHARGE, WearTiles.LIMITS -> car.hasBattery
            WearTiles.LOCATION -> car.lat != null && car.lon != null
            WearTiles.WEATHER, WearTiles.SMART_CLIMATE -> ui.extras.carWeather[car.vin] != null || ui.extras.homeWeather != null
            WearTiles.DIAGNOSTICS -> car.hasLiveStatus
            WearTiles.AI -> ui.settings?.aiEnabled == true
            else -> true // summary, lock, climate, comfort, info, assist, more
        }
        if (show) out.add(key)
    }
    return out
}

/**
 * One car's content as a morphing-scroll [ScalingLazyColumn]. Cards scale/fade
 * at the edges, the list wraps around for endless vertical scroll, and rotary
 * (bezel / crown) snaps exactly one tile per detent. The screen names the car
 * once you scroll away from its summary.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CarColumn(
    vm: WearViewModel,
    ui: WearUi,
    car: CarView,
    listStates: MutableMap<String, ScalingLazyListState>,
    onSettings: () -> Unit,
    onTrips: (String) -> Unit,
    onReorder: (String) -> Unit,
    active: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val round = LocalConfiguration.current.isScreenRound

    // Narrowly keyed on just what visibleTiles() actually reads -- keying on
    // the whole ui.settings/ui.extras objects meant any unrelated push (e.g.
    // another car's weather, an unrelated setting) recomputed this for every
    // on-screen car page.
    val hiddenForCar = ui.settings?.hiddenSections?.get(car.vin)
    val pebbleOrder = ui.pebbleOrderFor(car.vin)
    val hasCarWeather = ui.extras.carWeather[car.vin] != null
    val hasHomeWeather = ui.extras.homeWeather != null
    val aiEnabled = ui.settings?.aiEnabled
    val tiles = remember(
        car.vin,
        car.alertCount,
        hiddenForCar,
        pebbleOrder,
        car.hasBattery,
        car.lat,
        car.lon,
        hasCarWeather,
        hasHomeWeather,
        car.hasLiveStatus,
        aiEnabled,
    ) { visibleTiles(ui, car) }
    val tileCount = tiles.size
    val infinite = tileCount > 1
    // The wrap-around teleport guard below only ever lets the user drift
    // tileCount * 2 items from center before recentring, so a much smaller
    // buffer than 200 cycles covers the same usable range with far less
    // incidental list allocation.
    val cycles = if (infinite) 24 else 1
    val total = tileCount * cycles
    val summaryIdx = tiles.indexOf(WearTiles.SUMMARY).coerceAtLeast(0)
    val initialIndex = if (infinite) (cycles / 2) * tileCount + summaryIdx else summaryIdx

    val state = listStates.getOrPut(car.vin) { ScalingLazyListState(initialIndex) }
    // ScalingLazyListScope only has items(List<T>), not items(count). Build the
    // virtual index list once so the key lambda receives unique ints.
    val virtualList = remember(total) { List(total) { it } }

    // When tiles reorder or alerts appear/disappear, scroll back to summary so the
    // new order is immediately visible. `initialised` skips the first composition
    // (state was just initialised to initialIndex). Keys are stable primitives.
    var initialised by remember(car.vin) { mutableStateOf(false) }
    // Key on THIS car's own tile order (structurally compared), not the whole
    // pebbleOverride map — otherwise reordering one car snapped every other
    // on-screen car back to its Summary tile.
    LaunchedEffect(tiles) {
        if (initialised) {
            // Animated, not an instant snap -- everything else on this screen
            // (page transitions, dots, button morphs, card resizing) is
            // spring/tween animated, so a hard teleport here read as a glitch
            // by comparison against the rest of the screen's motion language.
            state.animateScrollToItem((cycles / 2) * tileCount + summaryIdx)
        }
        initialised = true
    }

    // Claim rotary focus when this page becomes active. Adjacent pre-composed
    // pages skip this so only the settled page owns the crown/bezel.
    LaunchedEffect(car.vin, active) {
        if (!active) return@LaunchedEffect
        delay(60)
        repeat(5) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }

    var rotaryJob: Job? by remember { mutableStateOf(null) }
    var rotaryTargetIdx by remember { mutableIntStateOf(-1) }
    // Accumulated rotary travel toward the next one-tile step (see the handler).
    var rotaryAccumPx by remember { mutableFloatStateOf(0f) }
    val rotaryStepPx = with(LocalDensity.current) { 24.dp.toPx() }

    val centerItemIndex by remember {
        derivedStateOf {
            // Under the library's default ScalingLazyListAnchorType.ItemCenter (never
            // overridden here), ScalingLazyListItemInfo.offset is ALREADY center-line
            // relative (0 == exactly centered) — convertToCenterOffset in the library
            // bakes itemSizeInPx/2 in itself. Adding it.size/2 again here was double-
            // counting, which systematically picked the tile ~half an item-height away
            // from center as "focused" instead of the one actually centered on screen.
            state.layoutInfo.visibleItemsInfo.minByOrNull { abs(it.offset) }?.index ?: 0
        }
    }
    val centerTile = if (tileCount > 0) tiles[centerItemIndex % tileCount] else ""

    // Wrap-around guardian: once the user drifts near the ends of the virtual
    // list, silently teleport to the equivalent position in the centre segment
    // so infinite scrolling never hits a hard stop. Keyed on the counts it
    // closes over: the visible tile set changes while this stays composed
    // (alerts appear/disappear, weather tiles arrive async), and a Unit-keyed
    // effect kept judging boundaries with the ORIGINAL tileCount/total - a
    // shrunken list could dead-end without teleporting, a grown one teleported
    // to the wrong tile.
    LaunchedEffect(tileCount, total) {
        if (!infinite) return@LaunchedEffect
        snapshotFlow { centerItemIndex to state.isScrollInProgress }
            .collect { (idx, scrolling) ->
                if (!scrolling && rotaryJob?.isActive != true) {
                    if (idx < tileCount * 2 || idx > total - tileCount * 2) {
                        val phase = idx % tileCount
                        state.scrollToItem((cycles / 2) * tileCount + phase)
                    }
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { e ->
                    if (!active) return@onRotaryScrollEvent false
                    // Accumulate travel instead of stepping per raw event: a low-res
                    // bezel emits one large event per detent (still exactly one tile
                    // per detent), but a high-res crown streams many small-delta
                    // events - stepping on each one made a gentle crown turn fly
                    // across the whole tile ring. Reset (not carry) on step so one
                    // oversized detent can never double-step.
                    rotaryAccumPx += e.verticalScrollPixels
                    if (abs(rotaryAccumPx) < rotaryStepPx) return@onRotaryScrollEvent true
                    val dir = if (rotaryAccumPx > 0) 1 else -1
                    rotaryAccumPx = 0f
                    val maxIdx = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    val base = if (rotaryTargetIdx >= 0) rotaryTargetIdx else centerItemIndex
                    var newTarget = if (infinite) base + dir else (base + dir).coerceIn(0, maxIdx)
                    // Remap near the virtual-list boundary immediately (not after the
                    // scroll settles) so a long continuous bezel roll never actually
                    // reaches index 0/total-1 and gets stuck — the previous idle-gated
                    // guardian only fired once isScrollInProgress went false, which a
                    // continuous roll never does, so the user had to reverse direction
                    // to unstick it.
                    if (infinite && (newTarget < tileCount * 2 || newTarget > total - tileCount * 2)) {
                        val phase = ((newTarget % tileCount) + tileCount) % tileCount
                        newTarget = (cycles / 2) * tileCount + phase
                    }
                    rotaryTargetIdx = newTarget
                    rotaryJob?.cancel()
                    rotaryJob = scope.launch {
                        state.animateScrollToItem(newTarget)
                        rotaryTargetIdx = -1
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(active),
            state = state,
            // No built-in per-item shrink/fade - tiles render at a flat, plain scale.
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
            // Horizontal inset keeps card content (headers, right-aligned values)
            // inside the round screen's safe area so nothing clips in the corners.
            contentPadding = PaddingValues(
                horizontal = if (round) 22.dp else 12.dp,
                vertical = 60.dp,
            ),
            // Tighter than the library default (12dp) - tiles are already full-size
            // now that the shrink-toward-the-edges effect is gone, so the gap
            // between them is what mostly determines how much fits on screen.
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items = virtualList, key = { it }) { i ->
                // Plain vertical scroll - tiles render at a fixed scale/alpha regardless
                // of position. A previous focus-zoom effect (shrink + fade toward the
                // edges as a tile scrolled off-center) read as tiles receding into the
                // background with dead space opening up before the next one arrived.
                TileContent(tiles[i % tileCount], vm, ui, car, onSettings, onTrips, onReorder)
            }
        }

        // Tile-progress dots that curve along the right bezel so the round face
        // never clips them.
        CurvedDotIndicator(
            total = tileCount,
            activeIndex = if (tileCount > 0) centerItemIndex % tileCount else 0,
        )

        // Name the car once you leave its summary tile.
        CarNameOverlay(
            name = car.name,
            visible = centerTile.isNotEmpty() && centerTile != WearTiles.SUMMARY,
            phoneConnected = ui.phoneConnected,
        )
    }
}

/**
 * Auto-dismissing status snackbar, error-tinted for failures. Lives at the
 * HomeScreen level (not inside CarColumn) so the pager's adjacent pre-composed
 * pages don't each spin up their own copy + dismiss timer.
 */
@Composable
internal fun BoxScope.MessageSnackbar(message: String?, onDismiss: () -> Unit) {
    if (message != null) {
        LaunchedEffect(message) {
            delay(3500)
            onDismiss()
        }
    }
    val isError = message?.let {
        it.contains("fail", ignoreCase = true) || it.contains("error", ignoreCase = true) ||
        it.contains("couldn't", ignoreCase = true) || it.contains("can't", ignoreCase = true) ||
        it.contains("denied", ignoreCase = true)
    } == true
    AnimatedVisibility(
        visible = message != null,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer)
                // liveRegion: this appears asynchronously (a command result, a
                // sync completion, an error) with no other cue, so without it
                // TalkBack never proactively announces the message at all.
                // contentDescription on the dismiss action: was an unlabelled
                // clickable region -- TalkBack announced the message text but
                // gave no indication tapping it dismisses the alert.
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "${message.orEmpty()}. Double tap to dismiss."
                }
                .clickable(onClickLabel = "Dismiss") { onDismiss() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                message ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Render a single tile by key. Plain composable so it can repeat for wrap-around. */
@Composable
private fun TileContent(
    key: String,
    vm: WearViewModel,
    ui: WearUi,
    car: CarView,
    onSettings: () -> Unit,
    onTrips: (String) -> Unit,
    onReorder: (String) -> Unit,
) {
    when (key) {
        TILE_ALERTS -> AlertsCard(car)
        WearTiles.SUMMARY -> SummaryCard(vm, ui, car)
        WearTiles.LOCK -> MorphButton(
            // The unlocked car is the noteworthy state, so it's the highlighted one
            // (consistent with the phone tile and the watch Tile).
            label = if (car.locked == true) "Locked" else "Unlocked",
            icon = if (car.locked == true) Icons.Filled.Lock else Icons.Filled.LockOpen,
            active = car.locked == false,
            activeColor = MaterialTheme.colorScheme.primary,
            pending = "${car.vin}:doors" in ui.pending,
            onClick = { vm.toggleLock(car.vin) },
        )
        WearTiles.CLIMATE -> ClimateCard(vm, ui, car)
        WearTiles.SMART_CLIMATE -> SmartClimateCard(vm, ui, car)
        WearTiles.COMFORT -> ComfortCard(vm, ui, car)
        WearTiles.PRESETS -> PresetsCard(vm, ui, car)
        WearTiles.CHARGE -> ChargeCard(vm, ui, car)
        WearTiles.LIMITS -> LimitsCard(vm, ui, car)
        WearTiles.LOCATION -> LocationCard(vm, ui, car)
        WearTiles.WEATHER -> WeatherCard(ui, car)
        WearTiles.INFO -> InfoCard(car, ui)
        WearTiles.DIAGNOSTICS -> DiagnosticsCard(car)
        WearTiles.AI -> AiCard(vm, ui, car)
        WearTiles.ASSIST -> AssistCard(car)
        WearTiles.MORE -> MoreCard(vm, ui, car, onSettings, onTrips, onReorder)
    }
}

/** Tile-progress dots laid along the right-hand arc of the (round) screen. */
@Composable
private fun CurvedDotIndicator(total: Int, activeIndex: Int) {
    if (total <= 1) return
    val shown = min(total, 12)
    val active = if (total <= shown) {
        activeIndex.coerceIn(0, shown - 1)
    } else {
        ((activeIndex.toFloat() / (total - 1)) * (shown - 1)).roundToInt().coerceIn(0, shown - 1)
    }
    val selected = MaterialTheme.colorScheme.primary
    val unselected = MaterialTheme.colorScheme.outlineVariant
    // anchor 0° = 3 o'clock; dots hug the right bezel and follow the curve.
    CurvedLayout(modifier = Modifier.fillMaxSize(), anchor = 0f, anchorType = AnchorType.Center) {
        curvedRow {
            repeat(shown) { i ->
                curvedComposable {
                    val isOn = i == active
                    val sz by animateDpAsState(if (isOn) 7.dp else 4.dp, tween(150), label = "cd$i")
                    val c by animateColorAsState(if (isOn) selected else unselected, tween(150), label = "cc$i")
                    Box(Modifier.padding(1.5.dp).size(sz).clip(CircleShape).background(c))
                }
            }
        }
    }
}

/** A small pill naming the car you're currently looking at. Sits below the
 *  system clock (Wear's TimeText owns the very top center) so the two don't
 *  overlap.
 *
 *  This used to also be a long-press-to-refresh control (an escalating-
 *  haptic hold gesture with its own expanding progress-ring animation), but
 *  that duplicated the plain "Refresh" button already in the More tile --
 *  same action, and this path had no accessible alternative for TalkBack/
 *  switch-access users (a raw pointerInput gesture with no semantics),
 *  unlike the button. Removed rather than fixed, since the button already
 *  covers the same need with far less code. */
@Composable
private fun BoxScope.CarNameOverlay(name: String, visible: Boolean, phoneConnected: Boolean = true) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp),
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Box(
            Modifier
                // Same real drop-shadow technique as the phone's floating
                // chrome (an offset, blurred silhouette) instead of Wear
                // Compose's own tonal shadow, which read as barely-there.
                .dropShadow(RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!phoneConnected) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            // The only cue (while scrolled away from the
                            // Summary tile, which has its own "Standalone"
                            // text row) that the phone is disconnected --
                            // purely colour/size otherwise, nothing for
                            // TalkBack to announce.
                            .semantics { contentDescription = "Phone disconnected, running standalone" }
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)),
                    )
                }
            }
        }
    }
}

// ---- Section cards -------------------------------------------------------
// SectionCard itself now lives in Components.kt (same package) so every
// screen -- not just Home -- shares one "card with an uppercase, bold,
// primary-tinted header" visual language instead of each screen rolling its
// own card styling.

/** Alert card — shown only when there are open doors/windows/warnings.
 *  Fades in with a subtle vertical slide when alerts appear. */
@Composable
private fun AlertsCard(car: CarView) {
    fun openSummary(items: List<String>) = if (items.size == 1) items.first() else "${items.size} open"
    val warnings = buildList {
        if (car.doorsOpen.isNotEmpty()) add("Doors" to openSummary(car.doorsOpen))
        if (car.windowsOpen.isNotEmpty()) add("Windows" to openSummary(car.windowsOpen))
        if (car.trunkOpen) add("Trunk" to "Open")
        if (car.hoodOpen) add("Hood" to "Open")
        if (car.tireWarning) add("Tires" to "Check")
        if (car.lowFuel) add("Fuel" to "Low")
        if (car.washerLow) add("Washer fluid" to "Low")
        if (car.brakeLow) add("Brake fluid" to "Low")
        if (car.keyFobLow) add("Key fob" to "Low battery")
    }
    if (warnings.isEmpty()) return
    val errColor = MaterialTheme.colorScheme.error
    AnimatedVisibility(visible = true, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 4 }) {
        SectionCard(null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(15.dp), tint = errColor)
                Text("ALERTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = errColor)
            }
            Spacer(Modifier.height(5.dp))
            warnings.forEach { (label, value) -> StatusRow(label, value, valueColor = errColor) }
        }
    }
}

@Composable
private fun SummaryCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard(null) {
    val alertCount = car.alertCount
    val isStale = car.fetchedAt != null && System.currentTimeMillis() - car.fetchedAt > 30 * 60 * 1000L
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(contentAlignment = Alignment.TopEnd) {
            ChargeRing(car.percent, size = 60.dp, charging = car.charging == true)
            if (alertCount > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp, end = 1.dp)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        // ChargeRing (the sibling this badge overlays) has its
                        // own contentDescription, but that doesn't cover this
                        // separate node -- without its own label, TalkBack
                        // read a lone digit or bare "!" with no indication it
                        // means "N open alerts."
                        .semantics {
                            contentDescription = "$alertCount ${if (alertCount == 1) "alert" else "alerts"}"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (alertCount > 9) "!" else "$alertCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
        Column {
            val metric = ui.localSettings.unitSystem == "metric"
            AnimatedValue(
                value = car.rangeMi?.let { formatDistance(it, metric) } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(if (car.hasBattery) "Battery" else "Fuel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                car.engineOn ->
                    Text("Driving", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                car.charging == true && car.timeToFullMin != null && car.timeToFullMin > 0 ->
                    Text("${fmtMinutes(car.timeToFullMin)} to full", style = MaterialTheme.typography.labelSmall, color = WearColors.chargeGreen)
                car.charging == true ->
                    Text("Charging", style = MaterialTheme.typography.labelSmall, color = WearColors.chargeGreen)
                car.pluggedIn == true ->
                    Text("Plugged in", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val rel = relativeLabel(car.fetchedAt)
            if (rel.isNotBlank()) {
                Text(
                    rel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isStale) MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!ui.phoneConnected) {
                Text(
                    "Standalone",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                )
            }
        }
    }
    // 12V battery hint row
    car.battery12v?.let { v12 ->
        Spacer(Modifier.height(4.dp))
        val c = if (v12 < 20) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        StatusRow("12V battery", "$v12%", valueColor = c)
    }
    // Quick actions right on the hero — the two most-used controls, so the first
    // screen is actionable without swiping to a dedicated tile.
    Spacer(Modifier.height(8.dp))
    val locked = car.locked == true
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MorphButton(
            label = if (locked) "Locked" else "Unlocked",
            icon = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            active = locked,
            activeColor = MaterialTheme.colorScheme.primary,
            pending = "${car.vin}:doors" in ui.pending,
            onClick = { vm.toggleLock(car.vin) },
            modifier = Modifier.weight(1f),
        )
        MorphButton(
            label = "Climate",
            icon = Icons.Filled.Thermostat,
            active = car.climateOn == true,
            activeColor = MaterialTheme.colorScheme.tertiary,
            pending = "${car.vin}:climate" in ui.pending,
            onClick = { vm.toggleClimate(car.vin) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ClimateCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Climate", Icons.Filled.Thermostat) {
    val d = ui.draftFor(car.vin)
    MorphButton(
        label = if (car.climateOn == true) "Climate on" else "Start climate",
        icon = Icons.Filled.Thermostat,
        active = car.climateOn == true,
        activeColor = MaterialTheme.colorScheme.tertiary,
        pending = "${car.vin}:climate" in ui.pending,
        onClick = { vm.toggleClimate(car.vin) },
    )
    Spacer(Modifier.height(6.dp))
    // 2°F steps, not 1° - the round screen only has room for so many dots before
    // they crowd into an unreadable smear; halving the count (11 vs 21) fixes that.
    val fahrenheit = ui.localSettings.unitSystem != "metric" || ui.settings?.useFahrenheit != false
    SliderRow("Temp", degLabel(d.tempF.toString(), fahrenheit), d.tempF, 62, 82, 2, accent = tempColor(d.tempF)) { vm.setClimateTemp(car.vin, it) }
    SliderRow("Run", "${d.duration} min", d.duration, 1, 10, 1) { vm.setClimateDuration(car.vin, it) }
    Spacer(Modifier.height(4.dp))
    MorphButton(
        label = if (d.defrost) "Defrost on" else "Defrost",
        icon = Icons.Filled.AcUnit,
        active = d.defrost,
        activeColor = MaterialTheme.colorScheme.tertiary,
        pending = false,
        onClick = { vm.toggleDefrost(car.vin) },
    )
}

@Composable
private fun SmartClimateCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Smart Climate", Icons.Filled.Thermostat) {
    val fahrenheit = ui.localSettings.unitSystem != "metric" || ui.settings?.useFahrenheit != false
    val weather: WearWeather? = ui.extras.carWeather[car.vin] ?: ui.extras.homeWeather
    val ambientF = weather?.let { ((it.tempC * 9.0 / 5.0) + 32).toInt() }
    val label = if (ambientF != null) {
        val action = if (ambientF >= 70) "Cool" else "Heat"
        if (car.climateOn == true) "Smart climate on" else "$action to ~${
            degLabel((if (ambientF >= 70) (ambientF - 10).coerceIn(60, 85) else (ambientF + 10).coerceIn(60, 85)).toString(), fahrenheit)
        }"
    } else {
        "No weather data"
    }
    MorphButton(
        label = label,
        icon = Icons.Filled.Thermostat,
        active = car.climateOn == true,
        activeColor = MaterialTheme.colorScheme.tertiary,
        pending = "${car.vin}:climate" in ui.pending || weather == null,
        onClick = { if (weather != null) vm.smartClimate(car.vin) },
    )
    // Guard on `weather` itself, not just the derived `ambientF`, so a future
    // change decoupling the two can't turn this into a real NPE.
    val currentWeather = weather
    if (ambientF != null && currentWeather != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Ambient: ${weatherTemp(currentWeather.tempC, fahrenheit)} · adjusts ±10°${if (fahrenheit) "F" else "C"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ComfortCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Comfort", Icons.Filled.AirlineSeatReclineNormal) {
    val d = ui.draftFor(car.vin)
    MorphButton(
        label = if (d.steering) "Steering heat on" else "Steering heat",
        icon = Icons.Filled.Whatshot,
        active = d.steering,
        activeColor = WearColors.heat,
        pending = false,
        onClick = { vm.toggleSteering(car.vin) },
    )
    Spacer(Modifier.height(4.dp))
    SliderRow("Driver seat", seatStepLabels[d.seatDriver], d.seatDriver, 0, 3, 1, accent = WearColors.heat) { vm.setSeatDriver(car.vin, it) }
    SliderRow("Passenger", seatStepLabels[d.seatPassenger], d.seatPassenger, 0, 3, 1, accent = WearColors.heat) { vm.setSeatPassenger(car.vin, it) }
    // Rear seats only when the live status shows they exist (non-null seatRl/Rr from a fetch).
    if (car.seatRl != null || car.seatRr != null) {
        Spacer(Modifier.height(2.dp))
        SliderRow("Rear left", seatStepLabels[d.seatRearLeft], d.seatRearLeft, 0, 3, 1, accent = WearColors.heat) { vm.setSeatRearLeft(car.vin, it) }
        SliderRow("Rear right", seatStepLabels[d.seatRearRight], d.seatRearRight, 0, 3, 1, accent = WearColors.heat) { vm.setSeatRearRight(car.vin, it) }
    }
    Spacer(Modifier.height(2.dp))
    Text("Applied when you start climate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PresetsCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Presets", Icons.Filled.Thermostat) {
    val list = ui.presets[car.vin].orEmpty()
    var confirmDeleteId by remember(car.vin) { mutableStateOf<String?>(null) }
    if (list.isEmpty()) {
        Text(
            "Save the current climate settings as a preset.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
    }
    list.forEach { preset ->
        val isActive = ui.draftFor(car.vin).activePresetId == preset.id && car.climateOn == true
        val confirming = confirmDeleteId == preset.id
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorphButton(
                label = preset.name,
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Thermostat,
                active = isActive,
                activeColor = MaterialTheme.colorScheme.tertiary,
                pending = "${car.vin}:climate" in ui.pending,
                onClick = {
                    confirmDeleteId = null
                    if (isActive) vm.toggleClimate(car.vin) else vm.applyPreset(car.vin, preset)
                },
            )
            Spacer(Modifier.width(4.dp))
            val delBg = if (confirming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            val delFg = if (confirming) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer
            // A real confirm/delete action with zero press feedback previously --
            // the same spring scale-punch every MorphButton gets, kept as a
            // circle here since MorphButton's pill/label shape doesn't fit an
            // icon-only two-state control like this one.
            val delInteraction = remember { MutableInteractionSource() }
            val delPressed by delInteraction.collectIsPressedAsState()
            val delScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (delPressed) 0.88f else 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessHigh,
                ),
                label = "presetDeleteScale",
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer { scaleX = delScale; scaleY = delScale }
                    .clip(CircleShape)
                    .background(delBg)
                    .clickable(interactionSource = delInteraction, indication = null) {
                        if (confirming) {
                            vm.deletePreset(car.vin, preset.id)
                            confirmDeleteId = null
                        } else {
                            confirmDeleteId = preset.id
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (confirming) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Confirm delete",
                        modifier = Modifier.size(16.dp),
                        tint = delFg,
                    )
                } else {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Delete preset",
                        modifier = Modifier.size(16.dp),
                        tint = delFg,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
    val saveInput = rememberWearTextInput("Preset name") { name -> vm.saveCurrentAsPreset(car.vin, name) }
    MorphButton(
        label = "Save preset",
        icon = Icons.Filled.Add,
        active = false,
        activeColor = MaterialTheme.colorScheme.primary,
        pending = false,
        onClick = saveInput,
    )
}

@Composable
private fun ChargeCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Charge", Icons.Filled.Bolt) {
    val metric = ui.localSettings.unitSystem == "metric"
    MorphButton(
        label = if (car.charging == true) "Charging — stop" else "Start charge",
        icon = Icons.Filled.Bolt,
        active = car.charging == true,
        activeColor = WearColors.chargeGreen,
        pending = "${car.vin}:charge" in ui.pending,
        onClick = { vm.toggleCharge(car.vin) },
    )
    Spacer(Modifier.height(4.dp))
    car.percent?.let { StatusRow("Battery", "$it%") }
    car.rangeMi?.let { StatusRow("Range", formatDistance(it, metric)) }
    StatusRow("Plug", car.chargerLabel ?: (if (car.pluggedIn == true) "Plugged in" else "Unplugged"))
    car.timeToFullMin?.takeIf { it > 0 }?.let { StatusRow("Time to full", fmtMinutes(it)) }
}

@Composable
private fun LimitsCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Charge limits", Icons.Filled.Bolt) {
    val draft = ui.chargeDraftFor(car.vin)
    val ac = draft.ac ?: car.acLimit ?: 80
    val dc = draft.dc ?: car.dcLimit ?: 90
    val isDirty = (draft.ac != null && draft.ac != car.acLimit) ||
                  (draft.dc != null && draft.dc != car.dcLimit)
    if (isDirty) {
        Text(
            "Unsaved changes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(2.dp))
    }
    SliderRow("AC", "$ac%", ac, 50, 100, 10) { vm.setAcLimit(car.vin, it) }
    SliderRow("DC", "$dc%", dc, 50, 100, 10) { vm.setDcLimit(car.vin, it) }
    Spacer(Modifier.height(4.dp))
    MorphButton(
        label = "Apply limits",
        icon = Icons.Filled.Bolt,
        active = false,
        activeColor = WearColors.chargeGreen,
        pending = "${car.vin}:chargeLimit" in ui.pending,
        onClick = { vm.applyChargeLimits(car.vin) },
    )
}

@Composable
private fun LocationCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Location", Icons.Filled.LocationOn) {
    // visibleTiles() only shows this card when both are non-null; guarded
    // explicitly here too so a future reordering can't silently render
    // "0.0, 0.0" instead of failing loudly.
    val lat = car.lat
    val lon = car.lon
    if (lat == null || lon == null) return@SectionCard
    val context = LocalContext.current
    // Resolve a human-readable place name the first time we have coordinates.
    LaunchedEffect(car.vin, car.lat, car.lon) {
        if (car.lat != null && car.lon != null) vm.ensurePlaceName(car.vin, car.lat, car.lon)
    }
    MapThumbnail(lat, lon)
    Spacer(Modifier.height(4.dp))
    Text(
        car.locationName ?: "%.4f, %.4f".format(lat, lon),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (car.engineOn) {
        Spacer(Modifier.height(2.dp))
        Text(
            "Engine running",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    val locRel = relativeLabel(car.fetchedAt)
    if (locRel.isNotBlank()) {
        Text(
            locRel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(6.dp))
    MorphButton(
        label = "Locate",
        icon = Icons.Filled.LocationOn,
        active = false,
        activeColor = MaterialTheme.colorScheme.primary,
        pending = "${car.vin}:refresh" in ui.pending,
        onClick = { vm.refreshStatus(car.vin) },
    )
    Spacer(Modifier.height(6.dp))
    MorphButton(
        label = "Open on phone",
        icon = Icons.Filled.OpenInNew,
        active = false,
        activeColor = MaterialTheme.colorScheme.primary,
        pending = false,
        onClick = { WearRemote.openOnPhone(context, "https://www.google.com/maps/search/?api=1&query=$lat,$lon") },
    )
}

@Composable
private fun WeatherCard(ui: WearUi, car: CarView) = SectionCard("Weather") {
    val w = ui.extras.carWeather[car.vin] ?: ui.extras.homeWeather
    if (w == null) {
        Text(
            "No weather data available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return@SectionCard
    }
    val f = ui.settings?.useFahrenheit != false
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(weatherIcon(w.code, w.isDay), contentDescription = null,
            tint = com.bloo.uicommon.weatherTint(w.code, w.isDay, MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.size(28.dp))
        Column {
            Text(weatherTemp(w.tempC, f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(weatherLabel(w.code), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // High / low column
        if (w.highC != null || w.lowC != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                w.highC?.let { Text("H: ${weatherTemp(it, f)}", style = MaterialTheme.typography.labelSmall) }
                w.lowC?.let { Text("L: ${weatherTemp(it, f)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    Spacer(Modifier.height(2.dp))
    StatusRow("Feels", weatherTemp(w.feelsLikeC, f))
    w.humidity?.let { StatusRow("Humidity", "$it%") }
    if (w.windKph > 0) StatusRow("Wind", formatSpeed(w.windKph.toDouble(), ui.localSettings.unitSystem == "metric"))
}

@Composable
private fun InfoCard(car: CarView, ui: WearUi) = SectionCard("Info", Icons.Filled.DirectionsCar) {
    val fahrenheit = ui.localSettings.unitSystem != "metric" || ui.settings?.useFahrenheit != false
    StatusRow("Engine", if (car.engineOn) "On" else "Off")
    car.tempSetting?.let { StatusRow("Set temp", degLabel(it, fahrenheit)) }
    StatusRow("Climate", if (car.climateOn == true) "On" else "Off")
    StatusRow("Defrost", if (car.defrostOn) "On" else "Off")
    StatusRow("Accessory", if (car.accessoryOn) "On" else "Off")
    val doorsLabel = when {
        car.doorsOpen.isEmpty() -> "All closed"
        car.doorsOpen.size == 1 -> car.doorsOpen.first()
        else -> "${car.doorsOpen.size} open"
    }
    StatusRow("Doors", doorsLabel, valueColor = if (car.doorsOpen.isNotEmpty()) MaterialTheme.colorScheme.error else null)
    val winsLabel = when {
        car.windowsOpen.isEmpty() -> "All closed"
        car.windowsOpen.size == 1 -> car.windowsOpen.first()
        else -> "${car.windowsOpen.size} open"
    }
    StatusRow("Windows", winsLabel, valueColor = if (car.windowsOpen.isNotEmpty()) MaterialTheme.colorScheme.error else null)
    if (car.trunkOpen) StatusRow("Trunk", "Open", valueColor = MaterialTheme.colorScheme.error)
    if (car.hoodOpen) StatusRow("Hood", "Open", valueColor = MaterialTheme.colorScheme.error)
    StatusRow("VIN", car.vin.takeLast(6))
    val metric = ui.localSettings.unitSystem == "metric"
    val odoInt = car.odometer?.replace(",", "")?.toDoubleOrNull()?.toInt()
    car.odometer?.let { StatusRow("Odometer", it) }
    car.licensePlate?.takeIf { it.isNotBlank() }?.let { StatusRow("Plate", it) }
    val lastSvc = car.lastServiceMiles
    val interval = car.serviceIntervalMiles
    if (lastSvc != null && interval != null) {
        val nextDue = lastSvc + interval
        val remaining = odoInt?.let { nextDue - it }
        StatusRow(
            "Service due",
            remaining?.let { "in ${formatDistance(it.coerceAtLeast(0), metric)}" } ?: "at ${formatDistance(nextDue, metric)}",
            valueColor = if (remaining != null && remaining <= 0) MaterialTheme.colorScheme.error else null,
        )
    }
}

@Composable
private fun DiagnosticsCard(car: CarView) = SectionCard("Diagnostics", Icons.Filled.Build) {
    val err = MaterialTheme.colorScheme.error
    val anyIndividualTire = car.tireFl || car.tireFr || car.tireRl || car.tireRr
    if (car.tireAll != null) {
        StatusRow("Tire avg", "${car.tireAll} psi")
    }
    if (anyIndividualTire) {
        if (car.tireFl) StatusRow("Tire FL", "Check", valueColor = err)
        if (car.tireFr) StatusRow("Tire FR", "Check", valueColor = err)
        if (car.tireRl) StatusRow("Tire RL", "Check", valueColor = err)
        if (car.tireRr) StatusRow("Tire RR", "Check", valueColor = err)
    } else if (car.tireWarning) {
        StatusRow("Tires", "Check pressure", valueColor = err)
    } else if (car.tireAll == null) {
        StatusRow("Tires", "OK")
    }
    car.battery12v?.let { v12 ->
        val h = car.battery12vHealth?.let { " · $it" } ?: ""
        StatusRow("12V", "$v12%$h", valueColor = if (v12 < 20) err else null)
    }
    car.fuelLevel?.let { StatusRow("Fuel", "$it%", valueColor = if (car.lowFuel) err else null) }
    if (car.washerLow) StatusRow("Washer", "Low", valueColor = err)
    if (car.brakeLow) StatusRow("Brake fluid", "Low", valueColor = err)
    if (car.keyFobLow) StatusRow("Key fob", "Low battery", valueColor = err)
    if (car.steerHeat) StatusRow("Steering", "Heating", valueColor = WearColors.heat)
    if (car.mirrorHeat) StatusRow("Mirrors", "Heating", valueColor = WearColors.heat)
    if (car.rearDefrost) StatusRow("Rear defrost", "On")
    seatLabel(car.seatFl)?.let { StatusRow("Seat FL", it) }
    seatLabel(car.seatFr)?.let { StatusRow("Seat FR", it) }
    seatLabel(car.seatRl)?.let { StatusRow("Seat RL", it) }
    seatLabel(car.seatRr)?.let { StatusRow("Seat RR", it) }
    car.timeToFullMin?.takeIf { it > 0 }?.let { StatusRow("Time to full", fmtMinutes(it)) }
    if (car.chargerLabel != null) StatusRow("Charger", car.chargerLabel)
}

@Composable
private fun AiCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("AI Summary", Icons.Filled.AutoAwesome) {
    val summary = ui.extras.ai[car.vin]
    val busy = ui.aiBusy == car.vin
    if (summary != null && !busy) {
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
    } else if (busy) {
        // Without this, the card had nothing above the button at all while
        // summarizing -- only the button's own tiny 18dp spinner communicated
        // that anything was happening, which on a round watch face read as a
        // blank, possibly-broken card mid-tap.
        Text(
            "Thinking…",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    } else {
        Text(
            "A quick plain-English rundown of your car, written on your phone.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
    MorphButton(
        label = if (busy) "Summarizing…" else if (summary != null) "Refresh" else "Summarize",
        icon = Icons.Filled.AutoAwesome,
        active = false,
        activeColor = MaterialTheme.colorScheme.primary,
        pending = busy,
        onClick = { vm.requestAiSummary(car.vin) },
    )
}

@Composable
private fun AssistCard(car: CarView) = SectionCard("Assist", Icons.Filled.Call) {
    val context = LocalContext.current
    val links = car.brand.links
    val accent = MaterialTheme.colorScheme.primary
    MorphButton(
        label = "Roadside",
        icon = Icons.Filled.Call,
        active = false,
        activeColor = accent,
        pending = false,
        onClick = { WearRemote.dialOnPhone(context, links.roadsidePhone) },
    )
    Spacer(Modifier.height(6.dp))
    MorphButton(
        label = "Schedule service",
        icon = Icons.Filled.Build,
        active = false,
        activeColor = accent,
        pending = false,
        onClick = { WearRemote.openOnPhone(context, links.serviceScheduleUrl) },
    )
    Spacer(Modifier.height(6.dp))
    MorphButton(
        label = "Owner site",
        icon = Icons.Filled.OpenInNew,
        active = false,
        activeColor = accent,
        pending = false,
        onClick = { WearRemote.openOnPhone(context, links.ownersUrl) },
    )
}

@Composable
private fun MoreCard(vm: WearViewModel, ui: WearUi, car: CarView, onSettings: () -> Unit, onTrips: (String) -> Unit, onReorder: (String) -> Unit) = SectionCard("More", Icons.Filled.Settings) {
    val accent = MaterialTheme.colorScheme.primary
    val alertCount = car.alertCount
    if (alertCount > 0) {
        StatusRow(
            "Alerts",
            "$alertCount open",
            valueColor = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(4.dp))
    }
    MorphButton(
        label = "Refresh",
        icon = Icons.Filled.Refresh,
        active = false,
        activeColor = accent,
        pending = "${car.vin}:refresh" in ui.pending,
        onClick = { vm.refreshStatus(car.vin) },
    )
    if (car.hasBattery && car.tripsSupported) {
        Spacer(Modifier.height(6.dp))
        MorphButton(
            label = "Trips",
            icon = Icons.Filled.Route,
            active = false,
            activeColor = accent,
            pending = false,
            onClick = { onTrips(car.vin) },
        )
    }
    Spacer(Modifier.height(6.dp))
    MorphButton(
        label = "Settings",
        icon = Icons.Filled.Settings,
        active = false,
        activeColor = accent,
        pending = false,
        onClick = onSettings,
    )
    Spacer(Modifier.height(6.dp))
    MorphButton(
        label = "Reorder tiles",
        icon = Icons.Filled.DragHandle,
        active = false,
        activeColor = accent,
        pending = false,
        onClick = { onReorder(car.vin) },
    )
    // Wear OS has no reliable on-device sideload flow, so tapping this opens
    // the build's GitHub Actions page on the connected phone instead of
    // downloading/installing anything on the watch itself. "Remind me later"
    // is the only dismiss offered here (vs. phone's "Not now" + snooze) -
    // this banner sits passively in an already-scrollable list rather than
    // interrupting like a dialog, so simply scrolling past it already serves
    // as a lightweight "not now"; full opt-out is the Settings toggle.
    if (ui.updateRun != null) {
        Spacer(Modifier.height(10.dp))
        MorphButton(
            label = "Update available",
            icon = Icons.Filled.SystemUpdate,
            active = true,
            activeColor = accent,
            pending = false,
            onClick = { vm.openUpdateOnPhone() },
        )
        Spacer(Modifier.height(4.dp))
        MorphButton(
            label = "Remind me",
            icon = Icons.Filled.Close,
            active = false,
            activeColor = accent,
            pending = false,
            onClick = { vm.snoozeUpdate() },
        )
    }
}

// ---- Shared bits ---------------------------------------------------------

private fun seatLabel(v: Int?): String? = v?.takeIf { it != 0 }?.let { SeatLevel.fromApi(it).label }

/** A page indicator whose dots curve along the round screen's edge. */
@Composable
private fun CurvedIndicator(count: Int, current: Int, anchor: Float) {
    if (count <= 1) return
    val selected = MaterialTheme.colorScheme.primary
    val unselected = MaterialTheme.colorScheme.outlineVariant
    CurvedLayout(modifier = Modifier.fillMaxSize(), anchor = anchor, anchorType = AnchorType.Center) {
        curvedRow {
            repeat(count) { i ->
                curvedComposable {
                    Box(
                        Modifier
                            .padding(1.5.dp)
                            .size(if (i == current) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (i == current) selected else unselected)
                    )
                }
            }
        }
    }
}

/**
 * Reorder this car's pebble *groups* (so the multiple watch tiles a pebble owns
 * always move as one unit). Long-press a row and drag. On drop, the new order is
 * applied instantly and pushed to the phone as this car's section order, keeping
 * both devices in lock-step. Summary is pinned first, like the phone.
 */
@Composable
fun TileReorderScreen(vm: WearViewModel, ui: WearUi, vin: String) {
    val synced = WearPebbles.reorderable(ui.pebbleOrderFor(vin))
    var order by remember(vin) { mutableStateOf(synced) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }

    // Adopt incoming changes from the phone unless the user is mid-drag.
    LaunchedEffect(synced) {
        if (draggingKey == null) order = synced
    }

    val state = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Persist (summary first) to the car's pebble order, synced to the phone.
    fun commit() {
        vm.savePebbleOrder(vin, listOf("summary") + order)
        // Redraw the glanceable Tile now so the new order takes effect
        // immediately, instead of waiting for the phone to echo the order
        // back or the Tile's own next freshness-interval poll.
        vm.refreshTileWidgets()
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { e ->
                scope.launch { state.scrollBy(e.verticalScrollPixels) }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        state = state,
        scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                ListHeader { Text("Reorder tiles", textAlign = TextAlign.Center) }
                Text(
                    "Long-press a row then drag to reorder",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        items(order, key = { it }) { key ->
            val dragging = draggingKey == key
            val lift by animateFloatAsState(if (dragging) 1.04f else 1f, label = "lift")

            // Rows displaced by the drag (not the dragged row itself) had no
            // placement animation at all — they teleported straight to their new
            // slot the instant the swap threshold was crossed. Slide them in from
            // their previous slot instead, since ScalingLazyColumn doesn't support
            // Modifier.animateItem() the way LazyColumn does.
            val idx = order.indexOf(key)
            var prevIdx by remember(key) { mutableIntStateOf(idx) }
            val slideOffset = remember(key) { Animatable(0f) }
            val haptics = LocalHapticFeedback.current
            LaunchedEffect(idx) {
                if (!dragging && idx != prevIdx && prevIdx >= 0) {
                    val rowH = (heights[key] ?: 64).toFloat()
                    slideOffset.snapTo((prevIdx - idx) * rowH)
                    slideOffset.animateTo(
                        0f,
                        spring(dampingRatio = com.bloo.uicommon.SoftDamping, stiffness = Spring.StiffnessMediumLow),
                    )
                }
                prevIdx = idx
            }

            Box(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) offsetY else slideOffset.value
                        scaleX = lift; scaleY = lift
                    }
                    .onSizeChanged { heights[key] = it.height }
            ) {
                    val cardTint by animateColorAsState(
                        targetValue = if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                        label = "reorderRowTint",
                    )
                    Card(
                        onClick = {},
                        colors = CardDefaults.cardColors(containerColor = cardTint),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            // The drag gesture below has no TalkBack equivalent
                            // at all -- this row was reachable but a double-tap
                            // did nothing (onClick = {}), so reordering was
                            // completely unusable for screen-reader users.
                            // Additive "Move up"/"Move down" actions alongside
                            // the drag, sharing the same reorder + commit logic.
                            .semantics {
                                val cur = order.indexOf(key)
                                customActions = listOfNotNull(
                                    if (cur > 0) CustomAccessibilityAction("Move up") {
                                        order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                        commit()
                                        true
                                    } else null,
                                    if (cur in 0 until order.lastIndex) CustomAccessibilityAction("Move down") {
                                        order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                        commit()
                                        true
                                    } else null,
                                )
                            }
                            .pointerInput(key) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingKey = key; offsetY = 0f
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    },
                                onDragEnd = { draggingKey = null; offsetY = 0f; commit() },
                                onDragCancel = { draggingKey = null; offsetY = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetY += dragAmount.y
                                    val cur = order.indexOf(key)
                                    if (cur >= 0) {
                                        if (offsetY > 0 && cur < order.lastIndex) {
                                            val nextH = heights[order[cur + 1]] ?: 0
                                            if (nextH > 0 && offsetY > nextH / 2f) {
                                                order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                                offsetY -= nextH
                                            }
                                        } else if (offsetY < 0 && cur > 0) {
                                            val prevH = heights[order[cur - 1]] ?: 0
                                            if (prevH > 0 && -offsetY > prevH / 2f) {
                                                order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                                offsetY += prevH
                                            }
                                        }
                                    }
                                },
                            )
                        },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            WearPebbles.LABELS[key] ?: key,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
