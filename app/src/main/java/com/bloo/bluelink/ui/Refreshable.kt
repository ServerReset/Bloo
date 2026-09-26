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
 * Wraps content with the pull-to-refresh gesture -- no visual indicator of its
 * own any more (removed app-wide after several rounds of real, reported visual
 * bugs across its different call sites: a stuck "blob" look, bleed-through
 * under the expanded map, and the same shared badge misfiring elsewhere). The
 * gesture and [onRefresh] still fire normally; only the badge is gone.
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
    }
}
