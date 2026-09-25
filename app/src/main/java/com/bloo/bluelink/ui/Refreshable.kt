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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.derivedStateOf

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
