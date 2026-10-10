package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.max
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// --- Garage status card -----------------------------------------------

/** The garage pager's page shown in place of a car when there are none, like Settings after it. */
@Composable
internal fun GarageStatusCard(state: State<UiState>, vm: AppViewModel, hazeState: HazeState? = null) {
    // Derived reads of the four fields it draws, so other UiState emissions don't recompose this
    // pager page.
    val accounts by remember { derivedStateOf { state.value.accounts } }
    val garageLoadError by remember { derivedStateOf { state.value.garageLoadError } }
    val garageLoadOffline by remember { derivedStateOf { state.value.garageLoadOffline } }
    val loading by remember { derivedStateOf { state.value.loading } }
    val scheme = MaterialTheme.colorScheme

    // A true connectivity failure (garageLoadOffline) gets its own plain "no connection" copy
    // instead of a raw exception message -- there's nothing actionable in that message beyond
    // "check your connection", which the dedicated copy already says -- and, gated the other way,
    // an error that happens while the device IS online (an auth failure, Hyundai/Kia's own API
    // returning a 500, etc.) keeps the more specific message since that one might actually help.
    val loadFailed = accounts.isNotEmpty() && garageLoadError != null
    val offline = loadFailed && garageLoadOffline
    val (icon, headline, body) = when {
        accounts.isEmpty() -> Triple(
            Icons.Filled.CloudOff,
            "Not signed in",
            "Sign in to your Hyundai, Kia, or Genesis account. Swipe right for Settings.",
        )
        offline -> Triple(
            Icons.Filled.WifiOff,
            "No connection",
            "No internet. You're still signed in. Check your connection and pull to retry.",
        )
        loadFailed -> Triple(
            Icons.Filled.WifiOff,
            "Couldn't load your vehicles",
            "${garageLoadError}\n\nPull down to try again.",
        )
        else -> Triple(
            Icons.Filled.DirectionsCar,
            "No vehicles found",
            "No vehicles found on this account.\n\nRegister your car in the BlueLink / UVO app, then pull to reload.",
        )
    }

    // Fade + slide up on first composition, like other first-paint cards.
    val contentAlpha = remember { Animatable(0f) }
    val contentOffset = remember { Animatable(16f) }
    LaunchedEffect(Unit) {
        launch { contentAlpha.animateTo(1f, tween(MotionLong)) }
        launch { contentOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    }

    // loadGarage() sets state.loading (not state.refreshing, which only ever covers a single car's
    // own status fetch) -- that's the flag this card's pull gesture needs to reflect for the
    // indicator/release behaviour to track the request it actually triggers.
    Refreshable(refreshing = loading, onRefresh = { vm.loadGarage() }) {
        Box(
            Modifier
                .fillMaxSize()
                // Scrollable so the pull-to-refresh gesture reaches the Refreshable above: a
                // PullToRefreshBox only sees the drag through a scrollable child's nested scroll, and
                // a plain (non-scrolling) card swallowed it -- which is why "pull down to try again"
                // did nothing here while it worked on a car page.
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = GapPage),
            contentAlignment = Alignment.Center,
        ) {
            // The shared GlassSurface card shell, blurring the real Aurora behind it.
            GlassSurface(
                shape = ExtraLargeShape,
                liquid = false,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = contentAlpha.value
                        // .dp.toPx(): translationY is in pixels, not dp.
                        translationY = contentOffset.value.dp.toPx()
                    },
                hazeState = hazeState,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(GapSection),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = GapHero),
                ) {
                    // The tonal icon badge shared with SettingsCard headers.
                    val badgeTint = if (loadFailed) scheme.error else scheme.onSurfaceVariant
                    IconBadge(
                        icon,
                        tint = badgeTint,
                        containerColor = badgeTint.copy(alpha = 0.15f),
                        size = 64.dp,
                        iconSize = 32.dp,
                    )
                    RollingNumber(
                        headline,
                        style = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    // The body says "pull down to try again"; this makes the retry an actual, visible
                    // control rather than a gesture someone has to already know.
                    if (loadFailed) {
                        SafeMorphTextButton(
                            text = "Try again",
                            onClick = { vm.loadGarage() },
                            emphasis = ButtonEmphasis.Confirm,
                        )
                    }
                }
            }
        }
    }
}
