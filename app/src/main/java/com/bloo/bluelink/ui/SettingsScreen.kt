package com.bloo.bluelink.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.setSettingsMode
import com.bloo.bluelink.data.settingsMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.max
import kotlinx.coroutines.launch

// --- Settings -------------------------------------------------------------

/**
 * The Settings screen: one scrolling list of [SettingsCard]s, with the search bar hoisted outside
 * the scroll. Advanced-only cards animate in staggered via staggeredAdvancedVisible; leaving
 * Advanced does not stagger.
 */

/**
 * Settings is a page inside the garage car pager, always the one after the last car. Swiping is how
 * you reach and leave it, so no back arrow or scrim; system back exits the app.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    vm: AppViewModel,
    /** Shared with GarageScreen's car pages so the floating search bar gets one blur source. */
    hazeState: HazeState = remember { HazeState() },
) {
    val appearance = LocalAppearance.current
    val notif by vm.notifications.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    // Keyed on the log's version counter: the snapshot (a list copy) is only taken when the log
    // actually changed, and only while this screen is collecting.
    val logsVersion by vm.logsVersion.collectAsStateWithLifecycle()
    val logs = remember(logsVersion) { vm.logSnapshot() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    // LocalClipboard's set API is suspend; one scope for the two copy buttons below (Logs card's
    // Copy, Debug panel's report copy).
    val clipboardScope = rememberCoroutineScope()
    val canBio = remember { vm.canUseBiometrics() }
    // LazyColumn: many heavy collapsible sections, unlike a car page's pebble list.
    val settingsListState = rememberLazyListState()
    var pickTarget by remember { mutableStateOf<String?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    // System photo picker (crash-free), then our own Compose crop step.
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && pickTarget != null) cropUri = uri
    }

  val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  // Search is an app-root element (SearchLayer) that handles its own back. This is the last pager
  // page, so system back exits the app; no BackHandler needed.
  BackdropHost {
        // LazyColumn instead of a plain Column for the same reason a car page could get away with a
        // plain one and this can't: Settings holds many more, and heavier,
        // independently-collapsible sections than a single car's pebble list, so composing every
        // one of them unconditionally (a plain Column's only option) would be real, avoidable work
        // paid on every visit regardless of which sections are actually open.
        Box(
            Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
        // Hoisted out of the list content (not a composable scope) so an advanced-only card is
        // skipped as an item: an empty item still keeps its slot and item spacing.
        val advVisible = rememberAdvancedVisibility(state.settingsMode == "advanced", ADVANCED_CARD_COUNT)
        // remember() needs a composable scope, so the gated-card transitions are hoisted here.
        val advTransition0 = rememberGridItemVisibility(advVisible[0])
        val advTransition1 = rememberGridItemVisibility(advVisible[1])
        LazyColumn(
            state = settingsListState,
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxWidth()
                .hazeSource(hazeState)
                // Edge-to-edge disables adjustResize; imePadding shrinks the list so focused fields
                // scroll into view.
                .imePadding()
                .padding(horizontal = ScreenGutter),
            verticalArrangement = Arrangement.spacedBy(GapGroup),
        ) {
            // Leading spacer, not a card.
            item {
                // Clears the Simple/Advanced tab that hangs below the status bar (its corner gap +
                // its height) with a breath to spare, so the first card never sits under it.
                Spacer(Modifier.height(topInset + HeaderCornerGap + HeaderButtonSize + 14.dp))
            }
            // Page hero: app identity, version and build.
            item {
                SettingsHeroCard(state, vm)
            }
            run {
                val advanced = state.settingsMode == "advanced"
            // Advanced-only cards share expandEnter()/expandExit() with staggeredAdvancedVisible so
            // revealing many cascades instead of overshooting in lockstep.
            item {
            // Accounts (one per brand; Hyundai + Genesis can both be signed in).
                AccountsCardContent(state, vm)
            }
            // Gates the item itself: an empty item still holds a slot and spacing.
            if (state.aiSupported) item {

            // Only when the device supports Gemini Nano; always shown, not advanced-only.
            run {
                AiCardContent(state, vm)
            }
            }
            if (advTransition0.targetState || !advTransition0.isIdle) item {

            // App-icon shortcuts (long-press the launcher icon)
            AnimatedVisibility(visibleState = advTransition0, enter = expandEnterSized(), exit = expandExitSized()) {
                AppShortcutsCardContent(state, vm)
            }
            }
            item {

            // Backup / Sync
            BackupSyncCardContent(state, vm, context, advanced)
            }
            // Gates the item: with no cars yet it would still hold a slot and gap.
            if (state.vehicles.isNotEmpty()) item {

            // Cars: drag to reorder, tap to expand setup + photo. Shown in Simple and Advanced;
            // power-user groups gate themselves.
            CarsCardContent(
                state = state,
                vm = vm,
                pick = { vin ->
                    pickTarget = vin
                    photoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            }
            if (advTransition1.targetState || !advTransition1.isIdle) item {

            // Debug -- device info and the activity log together, for support troubleshooting. A
            // power-user card, so it only exists in Advanced mode.
            AnimatedVisibility(visibleState = advTransition1, enter = expandEnterSized(), exit = expandExitSized()) {
            DebugCardContent(logs, vm, clipboardScope, clipboard)
            }
            }
            item {

            // Visuals: theme, colour, font, units, size
            VisualsCardContent(appearance, advanced, vm)
            }
            item {

            // Location -- was titled "Weather" and talked only about weather, even though choosing
            // "My location" here does more than that: it sets Appearance.weatherFollowsDevice, and
            // AppViewModel.refreshDeviceLocation() (the same place that keeps the map's own "you
            // are here" dot and the Location pebble's distance-to-car current) re-syncs this
            // location to that live fix on every refresh -- see
            // WeatherController.refreshDeviceLocationForWeather's own doc.
            LocationCardContent(appearance, vm)
            }
            item {

            // Notifications
            NotificationsCardContent(notif, state, advanced, vm)
            }
            item {

            // Security
            SecurityCardContent(state, vm, canBio, context, appearance)
            }
            item {

            // Sounds & vibration One switch, rendered as a single row (SettingsCard inlineSetting).
            SoundsVibrationCardContent(appearance, vm)
            }
        }
            item {
                // Credits for third-party projects/APIs; OpenStreetMap's tile policy expects
                // visible attribution.
                CreditsCardContent(vm)
            }
          // Trailing footer, not a card.
          item {
          Column {
          // Installed build (the update tile shows the available one); buildLabel is the shared
          // formatter.
          Spacer(Modifier.height(GapRow))
          Text(
              "Bloo · " + com.bloo.bluelink.data.buildLabel(vm.currentBuildNumber, com.bloo.bluelink.BuildConfig.BUILD_BRANCH),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
          )
          // Reserve the floating search bar's measured height, not a flat guess.
          Spacer(Modifier.height(searchBarClearance(fallback = bottomInset + 132.dp)))
          }
          }
        }
        } // Box (wide-screen centering)
        // No StatusBarScrim or back arrow: the garage pager already draws one over every page, and
        // leaving is a swipe. Simple/Advanced tab hanging from the status bar, top-right.
        SettingsModeTab(
            settingsMode = state.settingsMode,
            onSettingsModeChange = { vm.setSettingsMode(it) },
            hazeState = hazeState,
        )
        cropUri?.let { uri ->
            val target = pickTarget
            if (target != null) {
                CropScreen(
                    vin = target,
                    uriString = uri.toString(),
                    onCancel = { cropUri = null; pickTarget = null },
                    onSave = { path -> vm.setVehicleImage(target, path); cropUri = null; pickTarget = null },
                )
            }
        }
  }
}
