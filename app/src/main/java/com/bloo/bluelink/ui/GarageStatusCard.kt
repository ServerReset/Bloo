@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max

// --- Garage status card -----------------------------------------------

/**
 * Folded into the garage's own pager (GarageScreen.kt's collapsed pager,
 * CompactGarage's cover pager) as a page in place of a car when there are
 * none -- exactly the way Settings is folded in as the page after it. Used
 * to be a full standalone screen (`Screen.Empty`) with its own "Bloo" title
 * bar, floating Reload/Settings icons, and buttons that jumped to a separate
 * standalone Settings route. Reported directly: this -- and the "API is
 * down" / "no connection" states it can show -- should "just be another
 * card like the rest of them," reached and left the same way every other
 * page in the pager is: swipe, not a button, a menu, or a back press.
 *
 * GarageScreen/CompactGarage already provide the Aurora backdrop, status-bar
 * scrim, and blur source this card sits on top of, so this composable is
 * only the card's own content -- the same division VehicleDetailContent and
 * CompactCar keep for a real car page. [Refreshable] (Pebbles.kt) supplies
 * the retry action: pulling down here calls [AppViewModel.loadGarage] the
 * exact same way pulling down on a car page calls
 * [AppViewModel.refreshStatus] -- one standard gesture, not a one-off Reload
 * button this page alone had.
 */
@Composable
internal fun GarageStatusCard(state: State<UiState>, vm: AppViewModel, hazeState: HazeState? = null) {
    // Derived reads rather than `val s = state.value`: this card is a pager page, so a body
    // read subscribed it (and its first-paint Animatable effects) to every UiState emission.
    // It draws exactly four fields; only those four can invalidate it now.
    val accounts by remember { derivedStateOf { state.value.accounts } }
    val garageLoadError by remember { derivedStateOf { state.value.garageLoadError } }
    val garageLoadOffline by remember { derivedStateOf { state.value.garageLoadOffline } }
    val loading by remember { derivedStateOf { state.value.loading } }
    val scheme = MaterialTheme.colorScheme

    // Four distinct causes used to collapse into the same "No vehicles found" /
    // "Not signed in" copy -- including a real network/API failure (including
    // the manufacturer's own servers being down), which then looked exactly
    // like the app had silently signed the user out. Each now gets its own
    // icon and headline so the actual cause is always clear. A true
    // connectivity failure (garageLoadOffline) gets its own plain "no
    // connection" copy instead of a raw exception message -- there's nothing
    // actionable in that message beyond "check your connection", which the
    // dedicated copy already says -- and, gated the other way, an error that
    // happens while the device IS online (an auth failure, Hyundai/Kia's own
    // API returning a 500, etc.) keeps the more specific message since that
    // one might actually help.
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

    // Fade + slide up on first composition, matching HeroHeader and every
    // other first-paint card elsewhere in the app.
    val contentAlpha = remember { Animatable(0f) }
    val contentOffset = remember { Animatable(16f) }
    LaunchedEffect(Unit) {
        launch { contentAlpha.animateTo(1f, tween(400)) }
        launch { contentOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    }

    // loadGarage() sets state.loading (not state.refreshing, which only ever
    // covers a single car's own status fetch) -- that's the flag this card's
    // pull gesture needs to reflect for the indicator/release behaviour to
    // track the request it actually triggers.
    Refreshable(refreshing = loading, onRefresh = { vm.loadGarage() }, hazeState = hazeState) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            // GlassSurface (GlassChrome.kt): the same card shell every other piece of
            // content in the app sits on top of (Garage's pebbles, Settings' cards, the
            // lock overlay's own PIN card) -- blurring the real Aurora behind it, exactly
            // like a real car page's own content does.
            GlassSurface(
                shape = ExtraLargeShape,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = contentAlpha.value
                        // .dp.toPx(), not the raw Animatable value: translationY is in
                        // PIXELS, so feeding it 16f slid this 16px -- about 5dp on a
                        // 3x-density phone, and a different distance on every device.
                        translationY = contentOffset.value.dp.toPx()
                    },
                hazeState = hazeState,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(GapSection),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                ) {
                    // Same tonal icon-badge every SettingsCard header uses (StatusHeaderRow,
                    // SettingsHeader.kt) -- one badge treatment for "an icon summarizing this
                    // card's state," not a one-off radial-gradient glow of its own.
                    val badgeTint = if (loadFailed) scheme.error else scheme.onSurfaceVariant
                    IconBadge(
                        icon,
                        tint = badgeTint,
                        containerColor = badgeTint.copy(alpha = 0.15f),
                        size = 64.dp,
                        iconSize = 32.dp,
                    )
                    Text(
                        headline,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
