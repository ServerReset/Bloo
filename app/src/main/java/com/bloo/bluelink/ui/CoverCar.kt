@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import dev.chrisbanes.haze.HazeState

/**
 * One car on the flip cover, built from the app's standard pieces: a single scrolling column, so
 * there is no nested vertical pager and no scrubber rail to learn.
 *
 *  - The first item is the HOME card, exactly one cover-screen tall: the car's name and state, the
 *    big charge/range readout and the permanent action bar (lock, climate, horn, ...), over the car's
 *    photo when it has one.
 *  - Everything under it is the same pebbles the phone shows, expandable the same way.
 *  - Pull down anywhere to refresh, through the app-wide refresh indicator (replacing the old
 *    hold-the-edge gesture, which nothing about the screen explained).
 *
 * [CoverScaffold] keeps everything clear of the camera cut-out and the system insets.
 */
@Composable
internal fun CoverCar(
    v: Vehicle,
    state: State<UiState>,
    vm: AppViewModel,
    hazeState: HazeState,
) {
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    // The sections under the home card, in the user's own order: whatever the phone would show, minus
    // the summary (which IS the home card) and anything this screen cannot draw.
    val sections by remember(v.vin) {
        derivedStateOf {
            val s = state.value
            s.sectionsFor(v).filter { it != "summary" && it in CompactKnownTiles && s.isSectionAvailable(v, it) }
        }
    }
    CoverScaffold(reserveRailGutter = false) { metrics ->
        Refreshable(refreshing, onRefresh = { vm.refreshStatus(v) }, hazeState = hazeState) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = metrics.contentPadding,
                verticalArrangement = Arrangement.spacedBy(GapGroup),
            ) {
                item(key = "home") {
                    CoverHomeCard(v, state, vm, Modifier.fillMaxWidth().height(metrics.heightDp.dp))
                }
                items(sections, key = { it }) { section ->
                    SinglePebble(section, v, state, vm, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Lets a UI test find the cover's home card. */
internal const val COVER_HOME_TAG = "coverHomeCard"

/** The cover's home card: identity on top, readout in the middle, actions along the bottom. */
@Composable
private fun CoverHomeCard(v: Vehicle, state: State<UiState>, vm: AppViewModel, modifier: Modifier) {
    val status by remember(v.vin) { derivedStateOf { state.value.statusFor(v) } }
    val imageUrl by remember(v.vin) { derivedStateOf { state.value.imageUrls[v.vin] } }
    val hasBattery by remember(v.vin) { derivedStateOf { state.value.hasBattery(v) } }
    val hasFuel by remember(v.vin) { derivedStateOf { state.value.hasFuel(v) } }
    val drivingLabel by remember(v.vin) { derivedStateOf { state.value.drivingLabel(v) } }
    val metric = LocalAppearance.current.metricDistance
    val hasPhoto = !imageUrl.isNullOrBlank()
    val scheme = MaterialTheme.colorScheme
    // Over a photo the text is the light-on-scrim pair the hero uses; over the plain card it is the theme's.
    val onCard = if (hasPhoto) HeroOnPhoto else scheme.onSurface
    val stateLine = listOfNotNull(
        status?.doorLock?.let { if (it) "Locked" else "Unlocked" },
        if (status?.airCtrlOn == true) "Climate on" else null,
    ).joinToString(" · ")
    val unlocked = status?.doorLock == false
    val band = coverCutoutBand()
    val tiny = coverIsTiny()

    Box(modifier.clip(LargeShape).testTag(COVER_HOME_TAG)) {
        HeroPhotoBackdrop(v, imageUrl, height = 0.dp, corner = PebbleCornerExpanded, fill = true)
        CompositionLocalProvider(LocalContentColor provides onCard) {
            Column(
                Modifier.fillMaxSize().padding(if (tiny) GapRow else GapGroup),
                verticalArrangement = Arrangement.spacedBy(GapRow),
            ) {
                // The camera band already carries the car's name when the cover has one; say it here
                // only when there is no band to say it.
                if (band == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                        Icon(AppIcons.DirectionsCar, contentDescription = null, tint = onCard, modifier = Modifier.size(20.dp))
                        Text(
                            v.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onCard,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
                if (stateLine.isNotBlank() && !tiny) {
                    Text(
                        stateLine,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (unlocked) {
                            if (hasPhoto) Color(0xFFFF8A80) else scheme.error
                        } else {
                            onCard.copy(alpha = MutedContentAlpha)
                        },
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                    ChargeFuelBar(status, hasBattery, hasFuel, drivingLabel, metric = metric)
                }
                CoverActionBar(v, state, vm)
            }
        }
    }
}
