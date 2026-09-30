@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.launch
import kotlin.math.max
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import com.bloo.bluelink.data.platform
import com.bloo.bluelink.data.setSettingsMode
import com.bloo.bluelink.data.settingsMode
/**
 * The whole Settings screen.
 *
 * History: this began as a 3.4k-line slice peeled out of Screens.kt (the
 * 14.6k-line monolith), and then had its pure search/index logic, its card
 * bodies, its search surface and its settings-widget cluster extracted into
 * SettingsIndex.kt, SettingsSearch.kt, SettingsCards.kt and
 * SettingsWidgets.kt. What remains is the screen itself: the page hero card
 * (SettingsHeroCard), the simple/advanced mode harness and its stagger, and
 * the one long scrolling [Column] of [SettingsCard]s that is what this file
 * exists to own.
 *
 * The file's import list is still the (deduplicated) copy of the original
 * Screens.kt import list carried through the splits: unused imports are
 * warnings, not errors, and pruning each file's list is a separate,
 * individually-verifiable pass.
 */

// --- Settings -------------------------------------------------------------

/**
 * The whole Settings screen: one long scrolling [Column] of [SettingsCard]s
 * (Accounts, AI, App shortcuts, Cars, Backup & sync, Appearance, Quick
 * Settings tiles, and more further down), plus a floating search bar hoisted
 * outside the scroll so it can stay pinned to the bottom of the screen.
 *
 * Two things apply globally across the whole screen:
 *  - Simple vs. Advanced mode (`state.settingsMode`): every advanced-only
 *    card/section is wrapped in `AnimatedVisibility(visible =
 *    staggeredAdvancedVisible(advanced, index), enter = expandEnter(),
 *    exit = expandExit())` -- the SAME bounce-open/calm-close springs every
 *    pebble in the garage uses, with `staggeredAdvancedVisible` giving each
 *    card a small index-based head start so switching into Advanced mode
 *    cascades card by card instead of all seven overshooting on the same
 *    frame. Leaving Advanced mode has no stagger -- see that function's own
 *    doc for why hiding in sequence reads as broken rather than polished.
 *  - Settings search: `query` (live, updates every keystroke, purely for
 *    filtering the on-screen list of matching settings) is intentionally
 *    kept separate from `submittedQuery` (only set on an explicit
 *    submit/tap), since a mis-typed partial query must never itself trigger
 *    a real command or an AI request -- only a deliberate submission does.
 *
 * [BackHandler] is layered: while the search pill is expanded or has text,
 * back collapses/clears search first (matching how every other "expanded
 * surface" in the app treats back); only once search is already idle does
 * back return to the garage.
 */

/** Same idea as [staggeredAdvancedVisible], for [SettingsSearchResults]'s result cards
 *  instead of the Advanced-mode cards: each result gets a small index-based head start
 *  once [resetKey] (the ranked result set) changes, so a fresh search reads as results
 *  arriving one after another rather than the whole list snapping in at once. No stagger
 *  on the way OUT here either -- there is no "way out" to stagger, since a result that's
 *  no longer in the list is simply never composed again; there's nothing to hide in
 *  sequence the way [staggeredAdvancedVisible]'s own doc warns against. */

/**
 * Always a page inside a car pager now (GarageScreen's collapsed block/window pager,
 * or CompactGarage's cover pager -- via CoverSettingsGate there), always the one right
 * after the last real page (a car, or the status card with none). There is no
 * standalone Settings route or screen any more, for any vehicle count -- swiping IS
 * how you reach it and how you leave it. Swiping to a car IS "back", so this skips the
 * screen-navigation chrome that only ever made sense standalone (a floating "back to
 * the app" arrow, its own status-bar scrim). System-back has no page left of it to
 * land on, so it exits the app on a single press like any other terminal screen --
 * this used to arm a "press back again to close" guard instead, cut as unnecessary
 * ceremony for a gesture every Android user already expects to just work.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    vm: AppViewModel,
    /** True on the flip cover, where every dimension is precious: tighter
     *  gutters, a slimmer header, closer card spacing. The grid still
     *  scrolls exactly as it does on the phone -- compactness here is
     *  density, not reachability. */
    compact: Boolean = false,
    /** GarageScreen passes its own shared instance (the same one its car pages
     *  get) so the floating search bar/results panel hosted ABOVE it gets one
     *  real blur source regardless of which page is actually showing. Cover
     *  callers (CoverSettingsGate) get their own fresh default instead. */
    hazeState: HazeState = remember { HazeState() },
) {
    val appearance = LocalAppearance.current
    val notif by vm.notifications.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    // Keyed on the log's version counter: the snapshot (a list copy) is only taken when the
    // log actually changed, and only while this screen is collecting.
    val logsVersion by vm.logsVersion.collectAsStateWithLifecycle()
    val logs = remember(logsVersion) { vm.logSnapshot() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    // LocalClipboard's set API is suspend; one scope for the two copy buttons
    // below (Logs card's Copy, Debug panel's report copy).
    val clipboardScope = rememberCoroutineScope()
    val canBio = remember { vm.canUseBiometrics() }
    // LazyColumn, not a plain scrolling Column -- see the list's own comment
    // below for why (many more, and heavier, collapsible sections than a car
    // page's own pebble list).
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

  // Search no longer lives on this screen -- it is one app-root element now
  // (see SearchLayer), so its query, its focus and its own back handling went
  // with it. SearchLayer composes after this screen, so while search is open
  // ITS handler is the one that runs first.
  //
  // The Settings pager page is the LAST page -- there is no car page left of it
  // for system-back to land on, so a single back press exits the app here, same
  // as any other terminal screen. No BackHandler of its own needed for that;
  // this used to arm a "press back again to close" guard first, cut as
  // unnecessary ceremony for a gesture every Android user already expects to
  // just work.
  // hazeState is now a parameter (see this function's own doc) -- backs the
  // StatusBarScrim call far below with a REAL backdrop blur of the settings grid,
  // same pattern GarageScreen.kt uses for its own two pagers. See StatusBarScrim's
  // own doc for why plain Modifier.blur never worked here.
  BackdropHost {
        // A single scrolling column, exactly the shape VehicleDetailContent's own
        // (CarHeaderRow + PebbleList) page is -- Settings is a page in the same car
        // pager, not a differently-built screen, and the pager already pins every one
        // of its pages (this one included) to a single car-column's width, so the
        // wide-screen multi-column masonry grid this used to be had no width left to
        // ever actually spread into. LazyColumn instead of a plain Column for the same
        // reason a car page could get away with a plain one and this can't: Settings
        // holds many more, and heavier, independently-collapsible sections than a
        // single car's pebble list, so composing every one of them unconditionally
        // (a plain Column's only option) would be real, avoidable work paid on every
        // visit regardless of which sections are actually open.
        Box(
            Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
        // Hoisted OUT of the list's item content, which is not a composable scope and so could
        // never have called this. That hoist is what lets an advanced-only card be skipped as a
        // list ITEM rather than merely rendered empty -- see rememberAdvancedVisibility for why
        // an empty item is not free (it keeps its slot, and the list's own item spacing with
        // it, which is the gap left behind all over simple mode).
        val advVisible = rememberAdvancedVisibility(state.settingsMode == "advanced", ADVANCED_CARD_COUNT)
        // Same reason advVisible itself lives out here and not in the list content below:
        // rememberGridItemVisibility calls remember(), so it needs a real composable scope,
        // which LazyListScope's content lambda is not. One transition per gated
        // card, hoisted together so none of the five item{} sites below has to break that rule.
        val advTransition0 = rememberGridItemVisibility(advVisible[0])
        val advTransition1 = rememberGridItemVisibility(advVisible[1])
        val advTransition2 = rememberGridItemVisibility(advVisible[2])
        LazyColumn(
            state = settingsListState,
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxWidth()
                .hazeSource(hazeState)
                // The app runs edge-to-edge (MainActivity's enableEdgeToEdge()), which turns
                // off the manifest's own adjustResize for every surface -- without this, a
                // text field low in this list (license plate, a custom weather location) sat
                // right where the keyboard covered it, with nothing left to shrink the list's
                // own visible area and let Compose's built-in "scroll the focused field into
                // view" behavior actually reveal it. imePadding shrinks the list itself when
                // the keyboard opens, same fix as ExpandableMapLayer's own bottom column got
                // for its charger API key field, reported from the same screenshot.
                .imePadding()
                .padding(horizontal = if (compact) 10.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            // Content scrolls behind the status bar; clear the floating back-arrow/
            // segmented-toggle bar above. This is the list's own leading spacer, not
            // a card, but a single-column list needs no separate "full width" marker
            // for that the way the old grid's span did.
            item {
                Spacer(Modifier.height(topInset + (if (compact) 42.dp else 56.dp)))
            }
            // Settings' own page hero -- the same role a car page's hero photo card
            // plays (a glanceable top card), here showing the app identity, version and
            // build instead of a car's photo and charge. Makes this read as another
            // standard page in the pager rather than a differently-designed screen.
            item {
                SettingsHeroCard(state, vm, compact)
            }
            run {
                val advanced = state.settingsMode == "advanced"
            // Every advanced-only card now uses the SAME expandEnter()/expandExit()
            // every pebble in the garage uses (see UiTokens.kt) -- this used to be its
            // own bespoke, calmer spec (AdvancedModeStiffness) kept deliberately apart
            // from the pebble bounce because "it reveals a lot at once." That reasoning
            // predates the fix below: what actually made revealing a lot at once feel
            // chaotic was seven cards animating in perfect lockstep, all overshooting
            // together on the same frame -- not the bounce itself. staggeredAdvancedVisible
            // fixes THAT directly (each card gets a small index-based head start), which
            // is what lets this share the real bounce spring instead of avoiding it.
            // No `Arrangement.spacedBy` -- SettingsCard carries the gap itself, see there.
            //
            // And no `animateContentSize` on any of it either. Every card that changes
            // height here already animates its own (expandEnter/expandExit's
            // expandVertically/shrinkVertically, plus PebbleShell's own internal reveal --
            // SettingsCard is a thin wrapper around it now). Stacking a second,
            // independently-sprung height animation over one of these `item {}` blocks
            // would make each frame of the inner one a fresh "content size changed" event
            // for the outer one to chase, so it would lag behind its own content and then
            // catch up -- which is the other half of what looked like a snap when this
            // used to be one plain Column instead of grid items. PebbleShell documents the
            // identical trap; this was the same mistake, once, here.
            //
            // Each card below is its own `item {}` rather than a bare child of a Column --
            // see the LazyColumn this whole sequence now lives in, above -- so only the
            // cards actually scrolled into view are ever composed, same as every other
            // lazy list in the app. `advanced`, declared here in this enclosing `run {}`,
            // stays in scope for all of them exactly as it did before.
            item {
            // Accounts (one per brand; Hyundai + Genesis can both be signed in).
                AccountsCardContent(state, vm)
            }
            // The support check gates the ITEM, not just its contents. A grid item that
            // composes nothing is not free: it still takes a slot and the grid's
            // verticalItemSpacing with it, so a device without Gemini Nano got a phantom gap
            // where the AI card would be. Same mechanism, same fix as the advanced-only cards
            // (see rememberAdvancedVisibility); this condition had simply been missed.
            if (state.aiSupported) item {

            // On-device AI - only when the device supports Gemini Nano. Always
            // shown (not advanced-only): it's a headline feature, not a power-
            // user knob, and hiding it behind Advanced made it easy to miss.
            run { // scope kept so the gate above is the only edit; the check now lives on `item`
                AiCardContent(state, advanced, vm)
            }
            }
            if (advTransition0.targetState || !advTransition0.isIdle) item {

            // Updates used to have its own card here; it's folded into SettingsHeroCard
            // at the top of this grid now -- see that composable's own doc for why.

            // App-icon shortcuts (long-press the launcher icon)
            AnimatedVisibility(visibleState = advTransition0, enter = expandEnterSized(), exit = expandExitSized()) {
                AppShortcutsCardContent(state, vm)
            }
            }
            item {

            // Backup / Sync
            BackupSyncCardContent(state, vm, context, advanced)
            }
            // Gates the ITEM for the same reason the AI card does: with no cars yet
            // (fresh install, before the first sign-in) this composed nothing but still held a
            // slot and a gap open at the top of Settings.
            if (state.vehicles.isNotEmpty()) item {

            // Cars: drag to reorder, tap a car to expand its setup + photo. With a
            // single car there's nothing to order, so it's just shown expanded.
            // Always visible, in both Simple and Advanced -- this used to be
            // wrapped in the same advanced-only AnimatedVisibility as the
            // power-user cards below it, which hid the whole section (photo,
            // powertrain, seat/climate features, everything) from anyone in
            // Simple mode, the app's default. The two genuinely power-user
            // groups inside CarSettingsCard (default climate preset, palette
            // override) already have their own `state.settingsMode ==
            // "advanced"` checks, so gating the section as a whole here was
            // redundant with those AND too broad.
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

            // Debug -- app/device diagnostics for support troubleshooting. A power-user
            // diagnostic card like Logs, and it takes its OWN slot in this screen's stagger
            // sequence rather than sharing Logs': the two animate independently.
            AnimatedVisibility(visibleState = advTransition1, enter = expandEnterSized(), exit = expandExitSized()) {
            DebugCardContent(vm, clipboardScope, clipboard)
            }
            }
            item {

            // Display scale
            DisplayCardContent(appearance, advanced, vm)
            }
            item {

            // Font
            // SIMPLE, not advanced. This card is where Atkinson Hyperlegible
            // lives -- a typeface designed for low vision -- and an
            // accessibility choice behind a mode called "advanced" is a
            // choice the people who need it are least likely to find. The
            // rest of the card costs nothing to show alongside it.
            FontCardContent(appearance, vm)
            }
            item {

            // Location -- was titled "Weather" and talked only about weather, even though
            // choosing "My location" here does more than that: it sets
            // Appearance.weatherFollowsDevice, and AppViewModel.refreshDeviceLocation()
            // (the same place that keeps the map's own "you are here" dot and the Location
            // pebble's distance-to-car current) re-syncs this location to that live fix on
            // every refresh -- see WeatherController.refreshDeviceLocationForWeather's own
            // doc. So "My location" was already one location feeding both weather and the
            // map; this card just never said so.
            LocationCardContent(appearance, vm)
            }
            if (advTransition2.targetState || !advTransition2.isIdle) item {

            // Logs
            AnimatedVisibility(visibleState = advTransition2, enter = expandEnterSized(), exit = expandExitSized()) {
            LogsCardContent(logs, vm, clipboardScope, clipboard)
            }
            }
            item {

            // Map & Navigation
            MapNavigationCardContent(appearance, vm)
            }
            item {

            // Notifications
            NotificationsCardContent(notif, vm)
            }
            item {

            // Security
            SecurityCardContent(state, vm, canBio, context, appearance)
            }
            item {

            // Sounds & vibration
            // The whole card is one switch, so it renders as one row: title on the left, the
            // switch on the right, no chevron and nothing to expand into. See SettingsCard's
            // inlineSetting.
            SoundsVibrationCardContent(appearance, vm)
            }
            item {

            // Theme
            ThemeCardContent(appearance, advanced, vm)
            }
        }
            item {
                // Every third-party project/API this app draws on, in one place -- moved
                // here from a Surface+Text that used to sit inside AutoLock's own settings
                // (see AutoLockSettingsUi.kt's own comment), which was the ONLY place any
                // of them were credited and only showed up while that one feature happened
                // to be enabled. OpenStreetMap's own tile usage policy in particular expects
                // a visible attribution; this is that, even if it isn't literally overlaid
                // on the map itself.
                CreditsCardContent(vm)
            }
          // Same reason as the leading spacer above: this is the list's own
          // trailing footer, not a card.
          item {
          Column {
          // About / installed build — the one place the phone shows which build it's
          // running (the update tile shows the AVAILABLE build; this shows the current
          // one). Based on the GitHub Actions run number baked in at CI build time;
          // "dev build" for a local build. buildLabel is the canonical formatter shared
          // with the update tile's delta.
          Spacer(Modifier.height(GapRow))
          Text(
              "Bloo · " + com.bloo.bluelink.data.buildLabel(vm.currentBuildNumber, com.bloo.bluelink.BuildConfig.BUILD_BRANCH),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
          )
          // The search bar itself now floats fixed to the screen's bottom
          // edge (see below, outside this scrolling column) -- reserve exactly as much
          // space as its own live reported bounds say it needs, not a flat guess.
          Spacer(Modifier.height(searchBarClearance(fallback = bottomInset + 132.dp)))
          }
          }
        }
        } // Box (wide-screen centering)
        // Same blurred scrim GarageScreen uses behind the system clock/battery
        // icons -- this content scrolls behind the status bar too (see the
        // No more floating "Settings" corner badge -- removed as unwanted UI (see the floating
        // car-name pill's own removal). The "Bloo" title is real, static content on
        // SettingsHeroCard now; it just scrolls off with the rest of the grid.
        //
        // No StatusBarScrim or floating back-arrow here at all any more: this page always
        // sits inside GarageScreen's/CompactGarage's own HorizontalPager now, which already
        // draws its own StatusBarScrim on top of every page in it (cars and the status card
        // included) -- a second one here stacked as a subtly darker/hazier status-bar band
        // than every other page beside it. A back arrow makes even less sense: reaching this
        // page IS swiping, so leaving it is swiping back, not tapping anything.
        //
        // Settings mode toggle as a tab-like element below the status bar,
        // positioned at the top-right, styled like it's hanging from the status bar.
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
