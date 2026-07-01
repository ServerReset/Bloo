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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.WearWeather
import com.bloo.bluelink.data.links
import com.bloo.wear.CarView
import com.bloo.wear.WearRemote
import com.bloo.wear.WearPebbles
import com.bloo.wear.WearTiles
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import com.bloo.wear.seatStepLabels
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
    // phone (one pebble can expand into several tiles).
    for (key in WearPebbles.tilesFor(ui.pebbleOrderFor(car.vin))) {
        val show = when (key) {
            // Always shown so you can save the first preset from the watch.
            WearTiles.PRESETS -> true
            WearTiles.CHARGE, WearTiles.LIMITS -> car.hasBattery
            WearTiles.LOCATION -> car.lat != null && car.lon != null
            WearTiles.WEATHER, WearTiles.SMART_CLIMATE -> ui.extras.carWeather[car.vin] != null || ui.extras.homeWeather != null
            WearTiles.DIAGNOSTICS -> car.hasLiveStatus
            else -> true // summary, lock, climate, comfort, info, ai, assist, more
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

    val tiles = remember(ui.pebbleOverride, ui.settings, ui.presets, ui.extras, car) { visibleTiles(ui, car) }
    val tileCount = tiles.size
    val infinite = tileCount > 1
    val cycles = if (infinite) 200 else 1
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
    LaunchedEffect(ui.pebbleOverride, tileCount) {
        if (initialised) {
            state.scrollToItem((cycles / 2) * tileCount + summaryIdx)
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
    // so infinite scrolling never hits a hard stop.
    LaunchedEffect(Unit) {
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
                    val maxIdx = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    val dir = if (e.verticalScrollPixels > 0) 1 else -1
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
            // Flatten the built-in SLC scaling so our own focus-zoom is the single,
            // predictable source of the shrink/fade (no double-scaling).
            scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
            // Horizontal inset keeps card content (headers, right-aligned values)
            // inside the round screen's safe area so nothing clips in the corners.
            contentPadding = PaddingValues(
                horizontal = if (round) 22.dp else 12.dp,
                vertical = 60.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = virtualList, key = { it }) { i ->
                // Focus-zoom: each tile's whole content is largest when centred and
                // smoothly shrinks + fades toward BOTH the top and bottom edges, driven
                // purely by screen position (not card size). `centrality` is a State read
                // INSIDE graphicsLayer so the per-frame effect runs in the draw phase only
                // — no recomposition or remeasure of any tile while scrolling.
                val centrality = remember {
                    derivedStateOf {
                        val info = state.layoutInfo
                        val vh = info.viewportSize.height.toFloat()
                        if (vh == 0f) return@derivedStateOf 0f
                        val vc = vh / 2f
                        val item = info.visibleItemsInfo.firstOrNull { it.index == i }
                            ?: return@derivedStateOf 0f
                        // item.offset is already center-relative under the library's
                        // default ItemCenter anchoring (see centerItemIndex above) — 0
                        // means exactly centered, so no size/2 re-addition is needed.
                        (1f - (abs(item.offset.toFloat()) / vc)).coerceIn(0f, 1f)
                    }
                }
                val edgeScale = if (round) 0.74f else 0.84f
                // Bold white text at the old 0.45 was still legible enough that a
                // barely-peeking adjacent tile near the top/bottom edge (behind the
                // always-on-top CarNameOverlay pill and the system clock) read as
                // overlapping "ghost" content rather than a faded-out neighbor.
                val edgeAlpha = 0.16f
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            // Smoothstep the position curve for a softer focus falloff.
                            val c = centrality.value
                            val eased = c * c * (3f - 2f * c)
                            val s = edgeScale + (1f - edgeScale) * eased
                            scaleX = s
                            scaleY = s
                            alpha = edgeAlpha + (1f - edgeAlpha) * eased
                        },
                ) {
                    TileContent(tiles[i % tileCount], vm, ui, car, onSettings, onTrips, onReorder)
                }
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
            onRefresh = { vm.refreshStatus(car.vin) },
        )
    }
}

/**
 * Auto-dismissing status snackbar, error-tinted for failures. Lives at the
 * HomeScreen level (not inside CarColumn) so the pager's adjacent pre-composed
 * pages don't each spin up their own copy + dismiss timer.
 */
@Composable
private fun BoxScope.MessageSnackbar(message: String?, onDismiss: () -> Unit) {
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
                .clickable { onDismiss() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                message ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
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
        WearTiles.SUMMARY -> SummaryCard(car, ui)
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
        WearTiles.INFO -> InfoCard(car)
        WearTiles.DIAGNOSTICS -> DiagnosticsCard(car)
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
 *  overlap. Long-press to trigger a status refresh with an expanding animation. */
@Composable
private fun BoxScope.CarNameOverlay(name: String, visible: Boolean, phoneConnected: Boolean = true, onRefresh: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    // 0f = pill only, 1f = full screen filled
    val expandProgress = remember { Animatable(0f) }

    // Circular progress ring that fills clockwise as the user holds.
    // Only composed while the hold is active (saves a layer at rest).
    if (expandProgress.value > 0.001f) {
        val progressColor = MaterialTheme.colorScheme.primary
        CircularProgressIndicator(
            progress = { expandProgress.value.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (expandProgress.value * 3f).coerceIn(0f, 1f) },
            strokeWidth = 4.dp,
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = progressColor,
                trackColor = progressColor.copy(alpha = 0.15f),
            ),
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp),
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Box(
            Modifier
                .shadow(4.dp, RoundedCornerShape(50), clip = false)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var holdJob: Job? = null
                        var completed = false
                        var hapticJob: Job? = null
                        hapticJob = scope.launch {
                            var delayMs = 250L
                            var count = 0
                            while (isActive) {
                                // Escalate: start gentle, go strong
                                val type = when {
                                    count < 3  -> HapticFeedbackType.TextHandleMove
                                    count < 7  -> HapticFeedbackType.LongPress
                                    else       -> HapticFeedbackType.LongPress
                                }
                                hapticFeedback.performHapticFeedback(type)
                                if (count >= 3) {
                                    // Double-pulse for stronger feel
                                    delay(40L)
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                count++
                                delay(delayMs)
                                delayMs = (delayMs * 0.65f).toLong().coerceAtLeast(25L)
                            }
                        }
                        holdJob = scope.launch {
                            expandProgress.animateTo(
                                1f,
                                keyframes {
                                    durationMillis = 1000
                                    0f at 0
                                    1.06f at 800 using FastOutSlowInEasing
                                    1f at 1000
                                }
                            )
                            hapticJob?.cancel()
                            completed = true
                            onRefresh()
                            delay(600)
                            expandProgress.animateTo(0f, tween(400))
                        }
                        // Wait for finger up or cancellation
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            hapticJob?.cancel()
                            if (!completed) {
                                holdJob?.cancel()
                                scope.launch {
                                    expandProgress.animateTo(
                                        0f,
                                        tween(300)
                                    )
                                }
                            }
                        }
                    }
                }
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
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!phoneConnected) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)),
                    )
                }
            }
        }
    }
}

// ---- Section cards -------------------------------------------------------

@Composable
private fun SectionCard(
    title: String?,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Card had no internal padding at all — title/icon/content sat flush against
    // its edges, which the round bezel's curvature then clipped whenever the tile
    // scrolled away from dead-center (the corners narrow fastest there). The
    // outer ScalingLazyColumn already reserves a round-safe horizontal inset
    // (22dp), so this only needs a small top-up — a big horizontal value here
    // double-stacks with that and starves button labels of width, truncating
    // text like "Heat to ~65°F" mid-number. Vertical is the one that actually
    // matters for the curve, since it affects how a tile fits at any scroll
    // position, not just at rest.
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(5.dp))
            }
            content()
        }
    }
}

/** Alert card — shown only when there are open doors/windows/warnings. */
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

@Composable
private fun SummaryCard(car: CarView, ui: WearUi) = SectionCard(null) {
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
                        .background(MaterialTheme.colorScheme.error),
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
            AnimatedValue(
                value = car.rangeMi?.let { "$it mi" } ?: "—",
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
    SliderRow("Temp", "${d.tempF}°F", d.tempF, 62, 82, 1, accent = tempColor(d.tempF)) { vm.setClimateTemp(car.vin, it) }
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
    val weather: WearWeather? = ui.extras.carWeather[car.vin] ?: ui.extras.homeWeather
    val ambientF = weather?.let { ((it.tempC * 9.0 / 5.0) + 32).toInt() }
    val label = if (ambientF != null) {
        val action = if (ambientF >= 70) "Cool" else "Heat"
        if (car.climateOn == true) "Smart climate on" else "$action to ~${
            if (ambientF >= 70) (ambientF - 10).coerceIn(60, 85) else (ambientF + 10).coerceIn(60, 85)
        }°F"
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
    if (ambientF != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Ambient: $ambientF°F · adjusts ±10°F",
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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(delBg)
                    .clickable {
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
    car.rangeMi?.let { StatusRow("Range", "$it mi") }
    StatusRow("Plug", car.chargerLabel ?: (if (car.pluggedIn == true) "Plugged in" else "Unplugged"))
    car.timeToFullMin?.takeIf { it > 0 }?.let { StatusRow("Time to full", fmtMinutes(it)) }
}

@Composable
private fun LimitsCard(vm: WearViewModel, ui: WearUi, car: CarView) = SectionCard("Charge limits", Icons.Filled.Bolt) {
    val ac = ui.acLimitDraft ?: car.acLimit ?: 80
    val dc = ui.dcLimitDraft ?: car.dcLimit ?: 90
    val isDirty = (ui.acLimitDraft != null && ui.acLimitDraft != car.acLimit) ||
                  (ui.dcLimitDraft != null && ui.dcLimitDraft != car.dcLimit)
    if (isDirty) {
        Text(
            "Unsaved changes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(2.dp))
    }
    SliderRow("AC", "$ac%", ac, 50, 100, 10) { vm.setAcLimit(it) }
    SliderRow("DC", "$dc%", dc, 50, 100, 10) { vm.setDcLimit(it) }
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
    val lat = car.lat ?: 0.0
    val lon = car.lon ?: 0.0
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
    if (w.windKph > 0) StatusRow("Wind", "${w.windKph.toInt()} km/h")
}

@Composable
private fun InfoCard(car: CarView) = SectionCard("Info", Icons.Filled.DirectionsCar) {
    StatusRow("Engine", if (car.engineOn) "On" else "Off")
    car.tempSetting?.let { StatusRow("Set temp", "$it°") }
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
    car.odometer?.let { StatusRow("Odometer", it) }
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
    // Informational only: Wear OS has no reliable on-device sideload flow, so
    // this points the user at the phone rather than offering to install
    // anything itself (see WearViewModel's update check).
    if (ui.updateAvailable) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(
                "A watch update is ready — open Bloo on your phone to install it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
    fun commit() = vm.savePebbleOrder(vin, listOf("summary") + order)

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
        // Without this, the list's own built-in centre-scaling fights the drag's
        // own scale/translation feedback — a row visibly grows/shrinks as it
        // crosses the vertical middle mid-drag, independent of the actual drag,
        // which read as "buggy". Flatten it so drag feedback is the only motion.
        scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f, edgeAlpha = 1f),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { ListHeader { Text("Reorder Tiles", textAlign = TextAlign.Center) } }
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
                Card(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingKey = key; offsetY = 0f },
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
