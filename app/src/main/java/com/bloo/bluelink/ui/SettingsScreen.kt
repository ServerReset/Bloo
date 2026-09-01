@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.content.Intent
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.SettingsStore
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.links
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import com.bloo.uicommon.ReorderColumn
/**
 * The whole Settings screen.
 *
 * History: this began as a 3.4k-line slice peeled out of Screens.kt (the
 * 14.6k-line monolith), and then had its pure search/index logic, its card
 * bodies, its search surface and its settings-widget cluster extracted into
 * SettingsIndex.kt, SettingsSearch.kt, SettingsCards.kt and
 * SettingsWidgets.kt. What remains is the screen itself: the floating
 * SettingsHeaderRow, the simple/advanced mode harness and its stagger, and
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
 *    staggeredAdvancedVisible(advanced, index), enter = collapseEnter(),
 *    exit = collapseExit())` -- the SAME bounce-open/calm-close springs every
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
 * [embedded] is true when this is rendered as GarageScreen's own extra pager page
 * (Appearance.settingsAsPage) rather than the separate `Screen.Settings` route --
 * swiping to a car IS "back" in that mode, so the screen-navigation chrome that only
 * makes sense standalone (the BackHandler that closes a route which was never opened,
 * the floating "back to the app" arrow) is skipped. Nothing else about this composable
 * changes: same cards, same search integration, same everything -- it's genuinely the
 * same screen, just reached a different way.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsScreen(
    vm: AppViewModel,
    embedded: Boolean = false,
    // Non-null ONLY for GarageScreen's single-car-per-page pager's embedded
    // Settings slot, when it's the currently SETTLED page AND that page has
    // reported itself DOCKED -- mirrors VehicleDetailContent's own `hoisted`
    // param exactly, including why "settled" alone is no longer enough. See
    // HoistedIdentityFlight's own doc.
    hoisted: HoistedIdentityFlight? = null,
    // Mirrors VehicleDetailContent's own `onDockedChanged` exactly -- reports
    // this slot's own live docked state up to GarageScreen on every change,
    // regardless of whether `hoisted` is currently null.
    onDockedChanged: ((Boolean) -> Unit)? = null,
    // Mirrors VehicleDetailContent's own `pageLabel` exactly -- see that
    // parameter's own doc (Screens.kt) for why this local badge needs the
    // identical "N / M" label the shared hoisted badge shows.
    pageLabel: String? = null,
    /** True on the flip cover, where every dimension is precious: tighter
     *  gutters, a slimmer header, closer card spacing. The grid still
     *  scrolls exactly as it does on the phone -- compactness here is
     *  density, not reachability. */
    compact: Boolean = false,
) {
    val appearance = LocalAppearance.current
    val notif by vm.notifications.collectAsState()
    val state by vm.state.collectAsState()
    val logs by vm.logs.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val canBio = remember { vm.canUseBiometrics() }
    // LazyVerticalStaggeredGrid, not a plain scrolling Column -- see the grid's
    // own comment below for why. StaggeredGridCells.Adaptive naturally resolves
    // to exactly one column on a phone-width screen (the same visual result the
    // old Column gave every existing install) and multiple side by side once
    // there's genuinely room, so this one grid covers both instead of two
    // separate layouts to keep in sync.
    val settingsGridState = rememberLazyStaggeredGridState()
    val settingsScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    // === PULL-TO-REFRESH FOR SETTINGS ===
    // Settings uses Material 3's native PullToRefresh on the LazyVerticalStaggeredGrid.
    // When the user drags from the top, it triggers vm.syncNow() to sync with Google Drive.
    // The loading indicator floats at the top with spring animation, appearing only once
    // System back returns to the garage, not out of the app.
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
  // with it. Back here means only what it always meant underneath: return to
  // the garage. SearchLayer composes after this screen, so while search is
  // open ITS handler is the one that runs first.
  //
  // Skipped when embedded: there is no separate route here to close (this IS
  // the garage, just parked on its own pager page), so the default system-back
  // behaviour underneath (GarageScreen's own handling, or the app backgrounding)
  // is what should run instead.
  if (!embedded) BackHandler { vm.closeSettings() }
  if (embedded) {
      // The Settings pager page is the LAST page -- there is no car page
      // left of it for system-back to land on, so a single back press would
      // slam the whole app shut. Double-back instead: the first press arms
      // a two-second window and says so, the second press inside it really
      // closes. Kept to the pager page only -- the standalone Settings
      // screen's back returns to the garage (above).
      var backArmed by remember { mutableStateOf(false) }
      LaunchedEffect(backArmed) {
          if (backArmed) {
              delay(2000)
              backArmed = false
          }
      }
      BackHandler {
          if (backArmed) {
              haptics?.heavy()
              (context as? android.app.Activity)?.finish()
          } else {
              haptics?.tick()
              backArmed = true
              android.widget.Toast.makeText(
                  context,
                  "Press back one more time to close",
                  android.widget.Toast.LENGTH_SHORT,
              ).show()
          }
      }
  }
  // Built UNCONDITIONALLY now -- mirrors VehicleDetailContent's identical
  // `local` exactly, including why: this slot needs a live, continuously
  // up-to-date flight of its OWN even before (or without ever) becoming the
  // hoisted one, both so `onDockedChanged` below has something real to read
  // and so there's no stale-position gap at the moment it DOES take over
  // the shared corner badge.
  val topInsetPx = with(density) { topInset.toPx() }
  // remember(Unit) + SideEffect, not remember(topInsetPx) -- see
  // Screens.kt's VehicleDetailContent/GarageScreen/ExpandedCar
  // construction sites for the full reasoning: a keyed remember here
  // discarded all of this flight's accumulated dock/position state on
  // every inset change (rotation, fold/unfold, multi-window resize)
  // instead of just picking up the new inset value.
  val flight = remember { HeroTitleFlight(topInsetPx, with(density) { TitleDockHysteresis.toPx() }) }
  SideEffect { flight.topInsetPx = topInsetPx }
  val local = LocalSettingsPillState(flight)
  // Mirrors VehicleDetailContent's identical `liveFlight` -- see that
  // composable's own doc for the full reasoning. dockedPages is driven
  // entirely off whichever TitleFlightOverlay is actually live's own
  // `onSettledChanged` now, in BOTH directions -- used to also report
  // undocking immediately off the raw `liveFlight.docked` flag here, which
  // cut the shared hoisted badge off from further position updates while
  // its own exit spring was often still mid-flight (see onSettledChanged's
  // own doc, Screens.kt, for the full reasoning).
  val liveFlight = hoisted?.flight ?: local.flight
  if (hoisted != null) {
      // Register this page as the one actually driving the hoisted badge.
      // Idempotent -- see VehicleDetailContent's own identical guard for
      // why re-running it every recomposition is harmless.
      hoisted.scrollToTop.value = { settingsGridState.animateScrollToItem(0) }
  }
  BackdropHost {
        // A real multi-column grid on wide screens (tablets, landscape, foldables
        // unfolded) instead of one narrow centred column with empty space on
        // both sides -- Adaptive(380.dp) resolves to exactly one column at
        // ordinary phone widths (the same layout every existing install already
        // had) and grows to two or three side by side once there's genuinely
        // room, so cards actually use the space a tablet has instead of just
        // stretching the same single stack wider. Staggered (masonry), not a
        // fixed-row grid: every SettingsCard is independently collapsible, so
        // neighbouring cards are almost never the same height, and a fixed-row
        // grid would force every card in a row to the tallest one's height --
        // exactly the "wrong tool" a masonry layout exists to avoid.
        Box(
            Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
        CompositionLocalProvider(LocalHeroTitleFlight provides liveFlight) {
        // Hoisted OUT of the grid's item content, which is not a composable scope and so could
        // never have called this. That hoist is what lets an advanced-only card be skipped as a
        // grid ITEM rather than merely rendered empty -- see rememberAdvancedVisibility for why
        // an empty item is not free (it keeps its slot, and the grid's verticalItemSpacing with
        // it, which is the gap left behind all over simple mode).
        val advVisible = rememberAdvancedVisibility(state.settingsMode == "advanced", ADVANCED_CARD_COUNT)
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(minSize = 380.dp),
            state = settingsGridState,
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxWidth()
                .padding(horizontal = if (compact) 10.dp else 16.dp),
            verticalItemSpacing = if (compact) 8.dp else 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Content scrolls behind the status bar; clear the floating back-arrow/
            // segmented-toggle bar above. Full-line: this is the grid's own leading
            // spacer, not a card, so it needs the full row rather than being squeezed
            // into one column.
            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(Modifier.height(topInset + (if (compact) 42.dp else 56.dp)))
            }
            // Settings' own in-content header -- same visual weight a car page's own
            // CarHeaderRow has, so this reads as another page in the pager instead of
            // a differently-designed screen bolted on.
            item(span = StaggeredGridItemSpan.FullLine) {
                SettingsHeaderRow(state, compact)
            }
            run {
                val advanced = state.settingsMode == "advanced"
            // Every advanced-only card now uses the SAME collapseEnter()/collapseExit()
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
            // height here already animates its own (collapseEnter/collapseExit's
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
            // see the LazyVerticalStaggeredGrid this whole sequence now lives in, above --
            // so the grid can place it in whichever column has room, independent of every
            // other card's height. `advanced`, declared here in this enclosing `run {}`,
            // stays in scope for all of them exactly as it did before.
            item {
            // Accounts (one per brand; Hyundai + Genesis can both be signed in).
            SettingsCard("Accounts", Icons.Filled.Person, vm) {
                // Same icon-badge + status-line header as every other card that's had
                // this pass applied -- was straight into "Not signed in" or a wall of
                // per-account blocks with nothing summarizing how many were connected.
                val acctTint = if (state.accounts.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                StatusHeaderRow(
                    icon = Icons.Filled.Person,
                    tint = acctTint,
                    title = "Signed in",
                    status = if (state.accounts.isEmpty()) "No accounts" else "${state.accounts.size} account${if (state.accounts.size == 1) "" else "s"}",
                )
                Spacer(Modifier.height(14.dp))
                if (state.accounts.isEmpty()) {
                    Text(
                        "Not signed in",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.accounts.forEachIndexed { i, creds ->
                    if (i > 0) Spacer(Modifier.height(16.dp))
                    var pin by remember(creds.brand, creds.pin) { mutableStateOf(creds.pin) }
                    // Was a single un-confirmed tap that signed the account out
                    // immediately -- same "tap again to confirm" + 4s
                    // auto-reset pattern used for the climate preset/palette
                    // deletes above, so every destructive action in the app
                    // now asks for the same second tap instead of some firing
                    // instantly and others not.
                    var confirmSignOut by remember(creds.brand) { mutableStateOf(false) }
                    LaunchedEffect(confirmSignOut) {
                        if (confirmSignOut) {
                            delay(4000)
                            confirmSignOut = false
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(creds.brand.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        StatusRow("Email", creds.email)
                        SecretRow("Password", creds.password)
                        // Kia US has no service PIN; commands are session-keyed.
                        if (creds.brand.requiresPin) {
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it },
                                label = { Text("Service PIN") },
                                singleLine = true,
                                shape = FieldShape,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        ExpressiveButtonRow(spacing = 8.dp) {
                            if (creds.brand.requiresPin) {
                                val pinSource = remember { MutableInteractionSource() }
                                SafeExpansiveButton(
                                    interactionSource = pinSource,
                                    enabled = pin.isNotBlank() && pin != creds.pin,
                                ) {
                                    MorphTextButton(
                                        "Update PIN",
                                        onClick = { vm.updatePin(creds.brand, pin) },
                                        enabled = pin.isNotBlank() && pin != creds.pin,
                                        interactionSource = pinSource,
                                    )
                                }
                            }
                            val signOutSource = remember { MutableInteractionSource() }
                            SafeExpansiveButton(
                                interactionSource = signOutSource,
                                enabled = true,
                            ) {
                                MorphTextButton(
                                    if (confirmSignOut) "Tap again to confirm" else "Sign out",
                                    onClick = {
                                        if (confirmSignOut) { vm.logout(creds.brand); confirmSignOut = false }
                                        else confirmSignOut = true
                                    },
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    interactionSource = signOutSource,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                val addAccountSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = addAccountSource,
                    enabled = true,
                ) {
                    MorphTextButton(
                        "Add another account",
                        onClick = { vm.beginAddAccount() },
                        interactionSource = addAccountSource,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "If commands fail with a locked PIN, fix the Service PIN above. Too " +
                        "many wrong-PIN attempts lock it for a few minutes server-side.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            }
            // Gates the ITEM for the same reason the AI card above does: with no cars yet
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
            run { // scope kept so the gate above is the only edit; the check now lives on `item`
                var expandedCar by remember { mutableStateOf<String?>(null) }
                val single = state.vehicles.size == 1
                val pick: (String) -> Unit = { vin ->
                    pickTarget = vin
                    photoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                if (single) {
                    // With one car, CarSettingsCard IS the section's card --
                    // forceExpanded already gives it the exact same always-open,
                    // no-chevron header every other top-level SettingsCard has.
                    // Wrapping it in another SettingsCard("Car") on top used to
                    // stack two pebble headers both announcing the same car for
                    // no reason (one titled "Car", the other the car's own
                    // name) -- redundant chrome with nothing to expand,
                    // collapse or reorder underneath it.
                    val v = state.vehicles[0]
                    // Same gap-lives-inside-the-card fix SettingsCard's own wrapper
                    // uses (see its doc comment) and the same heading() semantics,
                    // reproduced by hand since this bypasses SettingsCard itself.
                    Box(Modifier.fillMaxWidth().padding(bottom = SettingsCardGap).semantics { heading() }) {
                        CarSettingsCard(
                            v = v, state = state, vm = vm,
                            expanded = true, dragging = false, dragHandle = Modifier,
                            collapsible = false,
                            onToggle = {}, onPickPhoto = { pick(v.vin) },
                        )
                    }
                } else {
                    SettingsCard("Cars", vm = vm) {
                        ReorderColumn(
                            items = state.vehicles,
                            keyOf = { it.vin },
                            onReorder = { vm.reorderVehicles(it) },
                            spacing = 8.dp,
                        ) { v, dragHandle, dragging ->
                            CarSettingsCard(
                                v = v, state = state, vm = vm,
                                expanded = expandedCar == v.vin, dragging = dragging, dragHandle = dragHandle,
                                onToggle = { expandedCar = if (expandedCar == v.vin) null else v.vin },
                                onPickPhoto = { pick(v.vin) },
                            )
                        }
                    }
                }
            }
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
                SettingsCard("AI", Icons.Filled.AutoAwesome, vm) {
                    // Same icon-badge + status-line header as the rest of this pass.
                    val aiTint = if (state.aiEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    StatusHeaderRow(
                        icon = Icons.Filled.AutoAwesome,
                        tint = aiTint,
                        title = "On-device AI",
                        status = when {
                            !state.aiEnabled -> "Off"
                            state.aiAuto -> "On · auto-summarize"
                            else -> "On"
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    ToggleRow("On-device AI (Gemini Nano)", state.aiEnabled) { vm.setAiEnabled(it) }
                    Text(
                        "Adds an AI summary pebble to each car and lets you ask the search " +
                            "box plain questions like \"what's the odometer\". Everything runs " +
                            "privately on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Advanced-only: a power-user nuance on top of the basic
                    // AI toggle above, not something a novice needs to see. PopVisible,
                    // not a bare `if` -- was snapping in/out with the toggle above with
                    // no animation at all.
                    PopVisible(visible = state.aiEnabled && advanced) {
                      Column {
                        ToggleRow("Summarize automatically", state.aiAuto) { vm.setAiAuto(it) }
                        Text(
                            if (state.aiAuto) {
                                "Summaries refresh on their own when you open a car, refresh its " +
                                    "status, or send a command. You can still tap Summarize anytime."
                            } else {
                                "Summaries only run when you tap Summarize on a car."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                }
            }
            }
            if (advVisible[0]) item {

            // Announcements -- currently sourced from the one real signal this
            // app has for it (an available build, same state.updateAvailable
            // the Updates card above renders) rather than a synthetic feed.
            // AnnouncementHistory's LazyColumn needs an explicit height cap:
            // it's placed inside this screen's own LazyVerticalStaggeredGrid
            // item{}, an unbounded-height vertical container, and a nested
            // LazyColumn with no height constraint crashes there (same class
            // of bug the Logs card's heightIn(max = 300.dp) above guards
            // against, just via Modifier.verticalScroll there instead of a
            // second lazy layout).
            AnimatedVisibility(visibleState = rememberAppearedState(), enter = collapseEnter(), exit = collapseExit()) {
            SettingsCard("Announcements", Icons.Filled.Campaign, vm) {
                val update = state.updateAvailable
                val announcements = remember(update) {
                    if (update == null) {
                        emptyList()
                    } else {
                        listOf(
                            Announcement(
                                id = "update-${update.run.runNumber}",
                                title = "Build #${update.run.runNumber} available",
                                message = update.run.releaseNotes?.trim().takeUnless { it.isNullOrBlank() }
                                    ?: "A new build is ready to view.",
                                severity = if (state.updateApkReady) AnnouncementSeverity.WARNING else AnnouncementSeverity.INFO,
                                timestamp = java.time.Instant.now().toString(),
                                actionLabel = "View",
                                onAction = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(update.run.htmlUrl))
                                                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                                        )
                                    }
                                },
                            ),
                        )
                    }
                }
                AnnouncementHistory(
                    announcements = announcements,
                    modifier = Modifier.heightIn(max = 300.dp),
                )
            }
            }
            }
            if (advVisible[1]) item {

            // (The "Updates" card now lives after Notifications — its natural home —
            // ungated so its controls show with or without Shizuku. See below.)

            // App-icon shortcuts (long-press the launcher icon)
            AnimatedVisibility(visibleState = rememberAppearedState(), enter = collapseEnter(), exit = collapseExit()) {
                SettingsCard("App shortcuts", Icons.Filled.Bolt, vm) {
                    // No inner MorphExpandButton any more -- this used to have its
                    // own second chevron gating the per-vehicle toggles below,
                    // stacked directly under the card's own PebbleShell chevron
                    // (which didn't exist yet when this was written; SettingsCard
                    // was a static, always-open Card back then, and the inner
                    // toggle was the ONLY way to fold this away). Now that the
                    // card itself opens and closes, a second tap just to see the
                    // toggles it opened FOR was two controls doing one job.
                    Text(
                        "Quick-access shortcuts from the launcher icon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    state.vehicles.forEach { v ->
                        Spacer(Modifier.height(4.dp))
                        Text(v.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        com.bloo.bluelink.Shortcuts.ACTIONS.forEach { cmd ->
                            ToggleRow(
                                com.bloo.bluelink.Shortcuts.actionLabel(cmd),
                                state.isShortcutEnabled(v.vin, cmd),
                            ) { vm.setShortcutEnabled(v.vin, cmd, it) }
                        }
                    }
                }
            }
            }
            item {

            // Backup / Sync
            SettingsCard("Backup & sync", Icons.Filled.CloudSync, vm) {
                var showDriveDialog by remember { mutableStateOf(false) }
                val settingsImportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent(),
                ) { uri -> uri?.let { vm.importSettings(context, it) } }
                val driveSaveLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json"),
                ) { uri -> uri?.let { vm.setSyncUri(it) } }
                val driveOpenLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri -> uri?.let { vm.importSettingsAndSync(context, it) } }

                // Icon + status caption up front, matching the icon-led header
                // every other multi-row card in Settings uses (Quick tiles, AI)
                // -- this card was the one still opening on two stacked lines
                // of plain text with no at-a-glance state.
                val driveConfigured = state.syncUri != null
                val driveIcon = when {
                    driveConfigured && state.syncError != null -> Icons.Filled.CloudOff
                    driveConfigured -> Icons.Filled.CloudDone
                    else -> Icons.Filled.CloudSync
                }
                val driveTint = when {
                    driveConfigured && state.syncError != null -> MaterialTheme.colorScheme.error
                    driveConfigured -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                // At-a-glance status header: the state icon in a tonal circle
                // (matching the app's card-header language) + a bold title and a
                // colour-coded one-line state. Icon included: driveIcon itself
                // changes (cloud-sync/cloud-done/cloud-off) along with the tint,
                // so StatusHeaderRow's own icon crossfade covers it too.
                StatusHeaderRow(
                    icon = driveIcon,
                    tint = driveTint,
                    title = "Automatic Drive sync",
                    status = when {
                        !driveConfigured -> "Not set up"
                        state.syncError != null -> "Sync failed"
                        else -> com.bloo.bluelink.data.relativeLabel(state.lastSyncMs).takeIf { it.isNotBlank() }?.let { "Synced $it" } ?: "Active"
                    },
                )
                Spacer(Modifier.height(14.dp))
                if (showDriveDialog) {
                    DriveSyncSetupDialog(
                        onDismissRequest = { showDriveDialog = false },
                        onSaveToDrive = { showDriveDialog = false; driveSaveLauncher.launch("bloo_settings.json") },
                        onOpenFromDrive = { showDriveDialog = false; driveOpenLauncher.launch(arrayOf("application/json")) },
                        // Already syncing, or aware of another device → creating a new
                        // file here would split the fleet across two files. Warn + steer
                        // to "Open from Drive".
                        hasExistingSync = state.syncUri != null || state.syncDevices.size > 1,
                    )
                }
                if (state.syncUri == null) {
                    // Not configured: one unmissable primary CTA, nothing else to
                    // read past. The old layout led with a paragraph explaining
                    // Drive sync and put setup in a quiet text button beside it.
                    val setupSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = setupSource,
                        enabled = true,
                    ) {
                        MorphButton(
                            onClick = { showDriveDialog = true },
                            interactionSource = setupSource,
                            modifier = Modifier.fillMaxWidth(),
                            active = true,
                        ) { MorphButtonLabel(icon = Icons.Filled.CloudSync, label = "Set up auto-sync", pending = false) }
                    }
                } else {
                    // Configured: "Sync now" is THE daily control, so it leads —
                    // ahead of the device registry and the setup/teardown pair,
                    // which are both occasional by comparison.
                    val syncSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = syncSource,
                        enabled = true,
                    ) {
                        MorphButton(
                            onClick = { vm.syncNow() },
                            interactionSource = syncSource,
                            modifier = Modifier.fillMaxWidth(),
                            active = true,
                        ) { MorphButtonLabel(icon = Icons.Filled.CloudSync, label = "Sync now", pending = false) }
                    }
                    // A live failure is the one fact that never hides behind the
                    // diagnostics disclosure below — if sync is broken, say so here.
                    state.syncError?.let { err ->
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    // The synced-devices registry: a drag-to-reorder list where the
                    // TOP device is primary (source of truth). See SyncDevicesSection.
                    SyncDevicesSection(state = state, vm = vm)
                    Spacer(Modifier.height(12.dp))
                    MorphSegmented(
                        options = listOf(
                            SegmentOption("wifi", "Wi-Fi only", null),
                            SegmentOption("any", "Any network", null),
                        ),
                        selectedKey = if (state.syncWifiOnly) "wifi" else "any",
                        onSelect = { vm.setSyncWifiOnly(it == "wifi") },
                    )
                    Spacer(Modifier.height(8.dp))
                    ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                        val changeFileSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = changeFileSource,
                            enabled = true,
                        ) {
                            MorphTextButton(
                                "Change Drive file",
                                interactionSource = changeFileSource,
                                onClick = { showDriveDialog = true },
                            )
                        }
                        val disableSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = disableSource,
                            enabled = true,
                        ) {
                            MorphTextButton(
                                "Disable",
                                interactionSource = disableSource,
                                onClick = { vm.clearSyncUri() },
                            )
                        }
                    }
                    // Troubleshooting tools, not daily controls: the last-synced
                    // stamp (already summarised in the header above), the file
                    // fingerprint, and the two repair actions all fold away by
                    // default so the card stops reading as a wall of equal pills.
                    Spacer(Modifier.height(8.dp))
                    var showSyncDiagnostics by rememberSaveable { mutableStateOf(false) }
                    val diagnosticsSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = diagnosticsSource,
                        enabled = true,
                    ) {
                        MorphTextButton(
                            if (showSyncDiagnostics) "Hide diagnostics" else "Diagnostics",
                            modifier = Modifier.fillMaxWidth(),
                            interactionSource = diagnosticsSource,
                            onClick = { showSyncDiagnostics = !showSyncDiagnostics },
                        )
                    }
                    AnimatedVisibility(
                        visible = showSyncDiagnostics,
                        enter = collapseEnter(Alignment.Bottom),
                        exit = collapseExit(Alignment.Bottom),
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val lastSyncLabel = com.bloo.bluelink.data.relativeLabel(state.lastSyncMs)
                                SyncInfoRow("Last synced", if (lastSyncLabel.isNotBlank()) lastSyncLabel else "—")
                                // File-identity fingerprint: two phones truly on the SAME Drive
                                // file show the SAME code. If they differ, they picked different
                                // files (Drive allows duplicate names) — the #1 reason sync
                                // doesn't converge, now checkable at a glance across phones.
                                state.syncFileFingerprint?.let { fp ->
                                    SyncInfoRow("File ID", fp, valueMono = true)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                                // Non-destructive real-provider round-trip so the user can confirm
                                // sync actually works.
                                val testSyncSource = remember { MutableInteractionSource() }
                                SafeExpansiveButton(
                                    interactionSource = testSyncSource,
                                    enabled = true,
                                ) {
                                    MorphTextButton(
                                        "Test sync",
                                        interactionSource = testSyncSource,
                                        onClick = { vm.testSync() }
                                    )
                                }
                                // "Pull from primary now": force this device to adopt the
                                // primary's full settings — only when a primary exists AND it
                                // isn't this device (pulling from yourself is a no-op). When not
                                // shown, Test sync spans the row on its own.
                                if (state.syncPrimaryId != null && state.syncPrimaryId != state.thisDeviceId) {
                                    val pullSource = remember { MutableInteractionSource() }
                                    SafeExpansiveButton(
                                        interactionSource = pullSource,
                                        enabled = true,
                                    ) {
                                        MorphTextButton(
                                            "Pull from primary",
                                            interactionSource = pullSource,
                                            onClick = { vm.pullFromPrimary() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Advanced-only: a one-shot export/import file is a power-user
                // fallback (moving settings by hand, a local backup outside
                // Drive) next to the always-on automatic sync above, which is
                // what most people actually want and shouldn't be buried.
                AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 1), enter = collapseEnter(), exit = collapseExit()) {
                  Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Manual backup", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A one-time snapshot file. Credentials are never included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                        val exportSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = exportSource,
                            enabled = true,
                        ) {
                            MorphTextButton(
                                "Export",
                                interactionSource = exportSource,
                                onClick = { vm.exportSettings(context) },
                            )
                        }
                        val restoreSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = restoreSource,
                            enabled = true,
                        ) {
                            MorphTextButton(
                                "Restore",
                                interactionSource = restoreSource,
                                onClick = { settingsImportLauncher.launch("application/json") },
                            )
                        }
                    }
                  }
                }
            }
            }
            if (advVisible[2]) item {

            // Debug -- app/device diagnostics for support troubleshooting.
            // Placed right after Logs, both power-user diagnostic cards --
            // shares this screen's stagger sequence (index 7, the next
            // unused slot) rather than reusing Logs' index 3, since the two
            // cards animate independently.
            AnimatedVisibility(visibleState = rememberAppearedState(), enter = collapseEnter(), exit = collapseExit()) {
            SettingsCard("Debug", Icons.Filled.BugReport, vm) {
                DebugSettingsPanel(
                    onCopyToClipboard = { text -> clipboard.setText(AnnotatedString(text)) },
                )
            }
            }
            }
            item {

            // Display scale
            SettingsCard("Display", Icons.Filled.Straighten, vm) {
                // Advanced-only: a power-user knob, unlike the Units picker
                // below it which every user needs regardless of mode. PopVisible,
                // not a bare `if` -- was snapping in/out with the mode switch.
                PopVisible(visible = advanced) {
                  Column {
                    var uiScaleDraft by remember(appearance.uiScale) { mutableFloatStateOf(appearance.uiScale) }
                    StepRow("Text & layout scale", "${(uiScaleDraft * 100).roundToInt()}%")
                    AnimatedSlider(
                        value = uiScaleDraft,
                        onValueChange = { uiScaleDraft = it },
                        valueRange = 0.8f..1.3f,
                        steps = 4,
                        onValueSettled = { uiScaleDraft = (it * 10).roundToInt() / 10f; vm.setUiScaleSoon(uiScaleDraft) },
                    )
                    Spacer(Modifier.height(12.dp))
                  }
                }
                // SIMPLE, not advanced: this changes what is on the car screen
                // every time you open the app, which is the test for whether a
                // switch belongs in the small set. The text-scale slider above
                // it is advanced by the same test -- it is a knob you set once.
                ToggleRow("Search on the car screen", appearance.showSearch) { vm.setShowSearch(it) }
                Text(
                    "A search bubble at the bottom of the car screen and the cover screen. " +
                        "Ask about the car (\"battery level\"), run a command (\"lock my car\"), " +
                        "or jump to a setting. Settings always has it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                // SIMPLE, not advanced -- same test as Search above: this changes
                // how you get to Settings every single time, not a knob set once.
                //
                // Flipping this while actually standing in Settings used to leave
                // you exactly where you were until the NEXT time you left and came
                // back -- turn it on from the standalone route and nothing visibly
                // happened; turn it off from the embedded page and the pager's own
                // correction (see Screens.kt) stranded you on whichever car the
                // block math now resolved to. Both branches below follow Settings
                // to its new presentation immediately instead, so the switch reads
                // as "Settings just changed shape" rather than "go find it again":
                // turning on from the standalone route closes it landing straight
                // on the pager's new Settings slot (closeSettings' own
                // landOnSettingsPage, consumed once by GarageScreen); turning off
                // from the embedded page opens the standalone route in its place,
                // back arrow included, before the pager gets a chance to bounce
                // you to a car instead.
                ToggleRow("Settings as a swipeable page", appearance.settingsAsPage) { turningOn ->
                    vm.setSettingsAsPage(turningOn)
                    if (turningOn && !embedded) {
                        vm.closeSettings(landOnSettingsPage = true)
                    } else if (!turningOn && embedded) {
                        vm.openSettings()
                    }
                }
                Text(
                    "Reach Settings by swiping past your last car instead of the gear " +
                        "button -- one continuous pager, with Settings as its own page at " +
                        "the end instead of a separate screen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                // Unit system: controls temperature, distance, and speed display.
                SettingsSegmentedRow(
                    label = "Units",
                    options = listOf(
                        SegmentOption("imperial", "Imperial", null),
                        SegmentOption("metric", "Metric", null),
                    ),
                    selectedKey = appearance.unitSystem,
                    onSelect = { vm.setUnitSystem(it) },
                )
            }
            }
            item {

            // Font
            // SIMPLE, not advanced. This card is where Atkinson Hyperlegible
            // lives -- a typeface designed for low vision -- and an
            // accessibility choice behind a mode called "advanced" is a
            // choice the people who need it are least likely to find. The
            // rest of the card costs nothing to show alongside it.
            SettingsCard("Font", Icons.Filled.TextFields, vm) {
                val labels = mapOf(
                    FontChoice.SYSTEM to "System default",
                    FontChoice.ATKINSON to "Atkinson Hyperlegible",
                    FontChoice.GOOGLE_SANS to "Google Sans",
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontChoice.entries.forEach { choice ->
                        ChoiceRow(labels.getValue(choice), appearance.fontChoice == choice) { vm.setFontChoice(choice) }
                    }
                }
            }
            }
            if (advVisible[3]) item {

            // Links
            AnimatedVisibility(visibleState = rememberAppearedState(), enter = collapseEnter(), exit = collapseExit()) {
            SettingsCard("Links", Icons.Filled.OpenInNew, vm) {
                SettingsSegmentedRow(
                    label = "Open links",
                    options = listOf(
                        SegmentOption("app", "In app", null),
                        SegmentOption("browser", "Browser", null),
                    ),
                    selectedKey = if (appearance.linksInApp) "app" else "browser",
                    onSelect = { vm.setLinksInApp(it == "app") },
                )
            }
            }
            }
            if (advVisible[4]) item {

            // Logs
            AnimatedVisibility(visibleState = rememberAppearedState(), enter = collapseEnter(), exit = collapseExit()) {
            SettingsCard("Logs", Icons.Filled.Info, vm) {
                // No local expand state any more. The card's OWN chevron (PebbleShell's, via
                // SettingsCard) already governs this body -- nothing inside a collapsed card is
                // composed at all -- so the "Show"/"Hide" button that used to live on this row
                // was a second disclosure for the same content: open the card, then open the log
                // again. One control, the outer one.
                val lineCount = logs.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Activity log  ·  $lineCount lines",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // Always present now: they used to appear only once the inner disclosure
                    // was opened, which is exactly the second step that made this card feel
                    // like it opened twice.
                    ExpressiveButtonRow(spacing = 0.dp) {
                            val copySource = remember { MutableInteractionSource() }
                            SafeExpansiveButton(
                                interactionSource = copySource,
                                enabled = true,
                            ) {
                                MorphTextButton(
                                    "Copy",
                                    onClick = {
                                        clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                                    },
                                    interactionSource = copySource,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            val clearSource = remember { MutableInteractionSource() }
                            SafeExpansiveButton(
                                interactionSource = clearSource,
                                enabled = true,
                            ) {
                                MorphTextButton(
                                    "Clear",
                                    onClick = { vm.clearLogs() },
                                    interactionSource = clearSource,
                                )
                            }
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Column {
                    Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(4.dp))
                        val logScroll = rememberScrollState()
                        SelectionContainer {
                            Text(
                                text = logs.joinToString("\n").ifBlank { "No activity yet." },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .fadingEdges(logScroll)
                                    .verticalScroll(logScroll),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        if (lineCount > 0) {
                            Text(
                                "Earliest entries at the top. The newest $lineCount lines are shown.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
            }
            item {

            // Notifications
            SettingsCard("Notifications", Icons.Filled.Notifications, vm) {
                // Icon-badge + status-line header, matching Backup & sync/Updates --
                // this card used to open straight into a wall of toggles with no
                // at-a-glance read of how many alerts were actually live.
                val alertToggles = listOf(notif.charging, notif.service, notif.doorOpen, notif.running, notif.unlocked)
                val alertsOn = alertToggles.count { it }
                val notifTint = if (alertsOn > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                // Icons.Filled.Notifications only -- NotificationsActive/Off aren't in
                // this project's icon set (confirmed by CI), so the on/off read comes
                // from the tint + status line alone, same as every other header here
                // that doesn't have a distinct icon per state.
                StatusHeaderRow(
                    icon = Icons.Filled.Notifications,
                    tint = notifTint,
                    title = "Alerts",
                    status = if (alertsOn == 0) "All off" else "$alertsOn of ${alertToggles.size} on",
                )
                Spacer(Modifier.height(14.dp))
                // First, not last: every other switch in this card is an
                // ALERT the user hopes never fires. This is a live surface
                // they watch on purpose while the car charges.
                ToggleRow("Live charging updates", notif.charging) { vm.setNotifyCharging(it) }
                Text(
                    "A progress bar in the shade and, on Android 16+, in the status bar and " +
                        "lock screen while the car charges -- with the charge limit marked and " +
                        "a Stop button.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                // Whether it actually promotes to the status-bar chip is a
                // system decision this app cannot force -- Android 16+ has a
                // real API to check the user's per-app toggle for it, though,
                // so query it instead of guessing.
                // PopVisible, not a bare `if` -- same consistency fix as the minute
                // fields below: this whole troubleshooting block used to snap in/out
                // with the charging toggle with no animation at all.
                PopVisible(visible = notif.charging) {
                    var showTroubleshoot by remember { mutableStateOf(false) }
                    Column {
                    // Version-independent, unlike the chip-promotion check below: this is
                    // about whether the background poll that would post/update the bar at
                    // all gets to run while the app isn't open, which matters on every
                    // Android version this app supports.
                    run {
                        val ctx = LocalContext.current
                        if (!LiveCharge.isBackgroundUnrestricted(ctx)) {
                            Text(
                                "Not starting when charging begins? Tap to fix",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { LiveCharge.requestBackgroundUnrestricted(ctx) }
                                    .padding(vertical = 4.dp)
                                    .padding(bottom = 4.dp),
                            )
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 36) {
                        val ctx = LocalContext.current
                        if (!LiveCharge.isPromotable(ctx)) {
                            Text(
                                "Not showing in the status bar? Tap to fix",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { LiveCharge.openLiveUpdateSettings(ctx) }
                                    .padding(vertical = 4.dp)
                                    .padding(bottom = 4.dp),
                            )
                        }
                    }
                    // Still shown even when isPromotable is already true: that check only
                    // covers the generic Android permission, and at least one real OEM (see
                    // LiveUpdateTroubleshootDialog) gates the chip behind a second switch that
                    // permission can't see -- confirmed on a real device this app had no way
                    // to detect from here.
                    Text(
                        "Live update not showing up? Troubleshooting steps",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { showTroubleshoot = true }
                            .padding(vertical = 4.dp)
                            .padding(bottom = 6.dp),
                    )
                    if (showTroubleshoot) {
                        LiveUpdateTroubleshootDialog(onDismiss = { showTroubleshoot = false })
                    }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(10.dp))

                ToggleRow("Service due alerts", notif.service) { vm.setNotifyService(it) }
                ToggleRow("Door-left-open alerts", notif.doorOpen) { vm.setNotifyDoor(it) }
                // PopVisible, not a bare `if` -- these three minute fields used to snap in
                // and out with zero animation, the one inconsistency left in a card whose
                // header now springs and whose sibling cards (the update pebble, search
                // results) all pop their own conditional rows the same way.
                PopVisible(visible = notif.doorOpen) {
                    MinutesField(notif.doorOpenMinutes, "Door-open minutes", vm::setDoorOpenMinutes)
                }
                ToggleRow("Car-running alerts", notif.running) { vm.setNotifyRunning(it) }
                PopVisible(visible = notif.running) {
                    MinutesField(notif.runningMinutes, "Running minutes", vm::setRunningMinutes)
                }
                ToggleRow("Left-unlocked alerts", notif.unlocked) { vm.setNotifyUnlocked(it) }
                PopVisible(visible = notif.unlocked) {
                    MinutesField(notif.unlockedMinutes, "Unlocked minutes", vm::setUnlockedMinutes)
                }
                Text(
                    "Background checks run roughly every 30 minutes, so alerts may " +
                        "arrive a little after your set time. Door and running alerts " +
                        "include a one-tap action to lock or turn the car off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
            if (advVisible[5]) item {

            // Quick Settings tiles -- per-tile config is power-user territory,
            // same tier as App shortcuts/Cars above.
            AnimatedVisibility(visibleState = rememberAppearedState(), enter = collapseEnter(), exit = collapseExit()) {
            SettingsCard("Quick tiles", Icons.Filled.Dashboard, vm) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Each car can have up to 12 tiles in your Quick Settings shade. " +
                            "Configure below, then tap \"Add to Quick Settings\" to place each one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))

                InlineSegmentedRow(
                    label = "On tap:",
                    caption = if (state.tileBackground) "Tiles fire the command directly and show a confirmation."
                        else "Tiles briefly open Bloo to send the command, then close.",
                    options = listOf(
                        SegmentOption("background", "Run in background", Icons.Filled.Bolt),
                        SegmentOption("open", "Open the app", Icons.Filled.OpenInNew),
                    ),
                    selectedKey = if (state.tileBackground) "background" else "open",
                    onSelect = { vm.setTileBackground(it == "background") },
                )

                Spacer(Modifier.height(12.dp))
                InlineSegmentedRow(
                    label = "Refresh:",
                    caption = "Pulls the car's latest state when the tile appears (throttled to once a minute per car).",
                    options = listOf(
                        SegmentOption("off", "Off", null),
                        SegmentOption("on", "On", Icons.Filled.Refresh),
                    ),
                    selectedKey = if (state.tileLiveRefresh) "on" else "off",
                    onSelect = { vm.setTileLiveRefresh(it == "on") },
                )
                Spacer(Modifier.height(12.dp))
                QuickTilesManager(state, vm)
            }
            }
            }
            item {

            // Security
            SettingsCard("Security", Icons.Filled.Lock, vm) {
                // Same icon-badge + status-line header Notifications/Backup & sync use --
                // this card used to open straight into a segmented row with no glanceable
                // read of whether the app lock is actually on.
                val locked = canBio && appearance.biometricLock
                val securityTint = if (locked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                val securityStatus = when {
                    !canBio -> "No fingerprint enrolled"
                    locked -> "Locked · ${appearance.lockTiming.label}"
                    else -> "Not locked"
                }
                StatusHeaderRow(
                    icon = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    tint = securityTint,
                    title = "App lock",
                    status = securityStatus,
                )
                Spacer(Modifier.height(14.dp))
                if (canBio) {
                    SettingsSegmentedRow(
                        label = "Require fingerprint to open",
                        options = listOf(
                            SegmentOption("off", "Off", null),
                            SegmentOption("on", "On", null),
                        ),
                        selectedKey = if (appearance.biometricLock) "on" else "off",
                        onSelect = { key ->
                            if (key == "on") {
                                context.findFragmentActivity()?.let { activity ->
                                    showBiometricPrompt(
                                        activity = activity,
                                        title = "Enable fingerprint lock",
                                        subtitle = "Confirm to require it on launch",
                                        onSuccess = { vm.setBiometricLock(true) },
                                        onError = { },
                                    )
                                }
                            } else {
                                // Turning the lock OFF now needs the same
                                // authentication turning it on does. It used to be a
                                // bare setBiometricLock(false) -- one tap, no prompt
                                // -- which had the confirmation on the wrong
                                // direction: enabling a lock is the harmless half.
                                //
                                // Reaching this screen does NOT prove the person
                                // holding the phone ever authenticated. LockTiming.OFF
                                // never re-locks after launch at all, and the longest
                                // grace setting is ten minutes, so an app that is open
                                // or was recently backgrounded is simply past the lock.
                                // From there a single unauthenticated tap removed it
                                // permanently, including on future cold launches --
                                // turning momentary physical access to an unlocked
                                // phone into standing access to unlocking someone's
                                // car, starting its climate, and reading where it is.
                                //
                                // No new lockout risk: the overlay that gates entering
                                // the app uses this same prompt, so anyone who cannot
                                // satisfy it cannot get in here to begin with, and
                                // un-enrolling biometrics makes canUseBiometrics()
                                // false, which stops the lock applying at all. That
                                // remains the escape hatch it always was.
                                val activity = context.findFragmentActivity()
                                if (activity == null) {
                                    // Fail closed -- keep the lock -- but say so,
                                    // rather than leaving the control looking stuck.
                                    vm.reportInfo("Couldn't verify it's you. The lock is still on.")
                                } else {
                                    showBiometricPrompt(
                                        activity = activity,
                                        title = "Disable fingerprint lock",
                                        subtitle = "Confirm to stop requiring it",
                                        onSuccess = { vm.setBiometricLock(false) },
                                        onError = { },
                                    )
                                }
                            }
                        },
                    )
                    // PopVisible, not a bare `if` -- same consistency fix as the
                    // Notifications card's minute fields right above this one.
                    PopVisible(visible = appearance.biometricLock) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            SettingsSegmentedRow(
                                label = "Lock the app",
                                options = LockTiming.entries.map { t -> SegmentOption(t.name, t.label, null) },
                                selectedKey = appearance.lockTiming.name,
                                onSelect = { key -> runCatching { vm.setLockTiming(LockTiming.valueOf(key)) } },
                            )
                        }
                    }
                } else {
                    Text(
                        "No fingerprint/biometric is enrolled on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // --- App PIN ---
                // The device unlock PIN: the required mechanism on devices with
                // no biometrics, an optional backup on those that have them.
                // Separate from the biometric rows above because it is a second,
                // independent mechanism, not a mode of the first one.
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(6.dp))
                var pinDialog by remember { mutableStateOf<String?>(null) }
                val pinSet = state.appPinSet
                StatusHeaderRow(
                    icon = if (pinSet) Icons.Filled.Lock else Icons.Filled.Pin,
                    tint = if (pinSet) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "App PIN",
                    status = if (pinSet) "On · 4-8 digits" else "Off",
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (canBio)
                        "A 4-8 digit PIN that works as a backup when fingerprints aren't available."
                    else
                        "This device has no fingerprints, so the app unlocks with this PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ExpressiveButtonRow(spacing = 8.dp) {
                    val pinSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = pinSource,
                        enabled = true,
                    ) {
                        MorphButton(
                            onClick = { pinDialog = "set" },
                            interactionSource = pinSource,
                        ) {
                            Icon(
                                if (pinSet) Icons.Filled.LockReset else Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (pinSet) "Change PIN" else "Set up PIN", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (pinSet) {
                        val removeSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = removeSource,
                            enabled = true,
                        ) {
                            MorphTextButton(
                                "Remove",
                                onClick = { pinDialog = "remove" },
                                interactionSource = removeSource,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                PinDialogs(
                    mode = pinDialog,
                    onDismiss = { pinDialog = null },
                    vm = vm,
                    state = state,
                    canBio = canBio,
                )
            }
            }
            item {

            // Sounds & vibration
            // The whole card is one switch, so it renders as one row: title on the left, the
            // switch on the right, no chevron and nothing to expand into. See SettingsCard's
            // inlineSetting.
            SettingsCard(
                "Sounds & vibration",
                Icons.Filled.Vibration,
                vm,
                inlineSetting = {
                    Switch(
                        checked = appearance.hapticsEnabled,
                        onCheckedChange = { vm.setHapticsEnabled(it) },
                    )
                },
            ) {}
            }
            item {

            // Theme
            SettingsCard("Theme", Icons.Filled.Palette, vm) {
                // Same icon-badge + status-line header as the rest of this pass.
                val themeTint = MaterialTheme.colorScheme.tertiary
                val themeLabel = when (appearance.themeMode) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.SYSTEM_AMOLED -> "System +AMOLED"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                    ThemeMode.AMOLED -> "AMOLED"
                }
                StatusHeaderRow(
                    icon = Icons.Filled.Palette,
                    tint = themeTint,
                    title = "Display mode",
                    status = if (appearance.auroraBackground) "$themeLabel · Aurora" else themeLabel,
                )
                Spacer(Modifier.height(14.dp))
                // Short segment labels; AMOLED is "pure black" for OLED screens.
                // "+AMOLED" follows the system light/dark switch exactly like
                // System does, but swaps in AMOLED's true-black surfaces for its
                // dark half instead of the normal dark palette -- system-driven
                // day/night, with OLED-friendly black once it's dark. Placed
                // directly next to System (rather than after the standalone
                // AMOLED option) since it's really System plus that one addition,
                // not a variant of AMOLED.
                SettingsSegmentedRow(
                    label = "Appearance",
                    options = listOf(
                        SegmentOption(ThemeMode.SYSTEM.name, "System", null),
                        SegmentOption(ThemeMode.SYSTEM_AMOLED.name, "+AMOLED", null),
                        SegmentOption(ThemeMode.LIGHT.name, "Light", null),
                        SegmentOption(ThemeMode.DARK.name, "Dark", null),
                        SegmentOption(ThemeMode.AMOLED.name, "AMOLED", null),
                    ),
                    selectedKey = appearance.themeMode.name,
                    onSelect = { vm.setThemeMode(ThemeMode.valueOf(it)) },
                )
                // Advanced-only, same tier as the dynamic-color block below --
                // Aurora's motion/colour-mode/custom-hex sub-options are
                // power-user territory, not something a simple-mode user needs
                // (the built-in solid-surface background covers everyone else).
                AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 5), enter = collapseEnter(), exit = collapseExit()) {
                  Column {
                    Spacer(Modifier.height(10.dp))
                    ToggleRow("Aurora background", appearance.auroraBackground) { vm.setAuroraBackground(it) }
                    Text(
                        "Show a gradient aurora behind the content instead of a solid surface.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Same AnimatedVisibility-wraps-a-Column idiom as the dynamic-
                    // color section below, instead of a bare `if` -- this whole
                    // Motion/Colour block otherwise just materialized the instant
                    // the toggle above flipped on.
                    AnimatedVisibility(
                        visible = appearance.auroraBackground,
                        enter = collapseEnter(),
                        exit = collapseExit(),
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text("Motion", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            MorphSegmented(
                                options = listOf(
                                    SegmentOption("static", "Static", null),
                                    SegmentOption("motion", "Motion", null),
                                ),
                                selectedKey = appearance.auroraMotion,
                                onSelect = { vm.setAuroraMotion(it) },
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Colour", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            MorphSegmented(
                                options = listOf(
                                    SegmentOption("complementary", "Complementary", null),
                                    SegmentOption("material", "Material You", null),
                                    SegmentOption("custom", "Custom", null),
                                ),
                                selectedKey = appearance.auroraColorMode,
                                onSelect = { vm.setAuroraColorMode(it) },
                            )
                            AnimatedVisibility(
                                visible = appearance.auroraColorMode == "custom",
                                enter = collapseEnter(),
                                exit = collapseExit(),
                            ) {
                                Column {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = appearance.auroraCustomColor ?: "",
                                        onValueChange = { vm.setAuroraCustomColor(it.take(7).takeIf { it.matches(RxHexColorDraft) } ?: appearance.auroraCustomColor) },
                                        label = { Text("Hex colour") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                  }
                }
                AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 6), enter = collapseEnter(), exit = collapseExit()) {
                  // AnimatedVisibility lays out a single child, not an implicit
                  // Column of its content lambda's composables -- without this
                  // wrapper the Spacer/Divider/Toggle/Slider siblings below would
                  // all stack on top of each other instead of flowing vertically.
                  Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(10.dp))
                    ToggleRow("Dynamic color (Material You)", appearance.dynamicColor) { vm.setDynamicColor(it) }
                    Text(
                        "Uses your wallpaper palette on Android 12+. Turn off to choose a built-in palette below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedVisibility(
                        visible = !appearance.dynamicColor,
                        enter = collapseEnter(),
                        exit = collapseExit(),
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text("Built-in palettes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColorPalette.entries.forEach { palette ->
                                    PaletteSwatch(
                                        palette = palette,
                                        selected = appearance.activeCustomPaletteId == null && appearance.colorPalette == palette,
                                        onClick = { vm.setColorPalette(palette); vm.setActiveCustomPaletteId(null) },
                                    )
                                }
                            }
                            // Custom palettes: the create/edit dialog and per-palette
                            // selection existed (SettingsStore + AppViewModel) but had
                            // no entry point anywhere in the UI after the old Color
                            // card was merged into this Theme card -- restore it here.
                            Spacer(Modifier.height(10.dp))
                            Text("Custom palettes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            var editingPalette by remember { mutableStateOf<CustomPaletteData?>(null) }
                            var showPaletteEditor by remember { mutableStateOf(false) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                appearance.customPalettes.forEach { palette ->
                                    CustomPaletteSwatch(
                                        palette = palette,
                                        selected = appearance.activeCustomPaletteId == palette.id,
                                        onClick = { vm.setActiveCustomPaletteId(palette.id) },
                                        onEdit = { editingPalette = palette; showPaletteEditor = true },
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            .clickable { editingPalette = null; showPaletteEditor = true },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "New custom palette")
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("New", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (showPaletteEditor) {
                                PaletteEditorDialog(
                                    editing = editingPalette,
                                    onSave = { vm.saveCustomPalette(it); vm.setActiveCustomPaletteId(it.id); showPaletteEditor = false },
                                    onDelete = { vm.deleteCustomPalette(it); showPaletteEditor = false },
                                    onDismiss = { showPaletteEditor = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    VibrancySlider(appearance, vm)
                    Spacer(Modifier.height(10.dp))
                    ToggleRow("Pebble outline", appearance.pebbleOutline) { vm.setPebbleOutline(it) }
                  }
                }
            }
            }
            item {

            // Updates — always shown (the update tile still auto-appears under the
            // hero, but this is the manual home: which build you're on, a force-check,
            // a browser fallback source, and the optional Shizuku silent-install toggle
            // gated to just its row so the card itself never vanishes).
            SettingsCard("Updates", Icons.Filled.SystemUpdate, vm) {
                // The installed build number carries the card as a hero stat, the
                // same big-number language the garage hero uses for %/range —
                // instead of a label/value row buried under a paragraph. The state
                // rides alongside as a tonal chip rather than a second text line.
                //
                // Its own tonal Surface, not a bare Row on the card's own background --
                // gives the hero stat visual depth/separation from the buttons below it,
                // the same "carved-out" treatment the update PEBBLE gives its own numbers.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            RollingNumber(
                                text = if (vm.currentBuildNumber > 0) "${vm.currentBuildNumber}" else "dev",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            // Branch only when it isn't the mainline the app normally
                            // builds from — buildLabel already encodes that rule, so
                            // reuse it rather than re-deriving "is this main?" here.
                            val branchSuffix = com.bloo.bluelink.data
                                .buildLabel(vm.currentBuildNumber, com.bloo.bluelink.BuildConfig.BUILD_BRANCH)
                                .substringAfter(" · ", "")
                            Text(
                                if (branchSuffix.isNotBlank()) "this build · $branchSuffix" else "this build",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        UpdateStatusChip(state)
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Both update sources share one row instead of two stacked
                // full-width pills: the in-app checker (primary) and the GitHub
                // Releases page (a second source that still works when the
                // checker says up-to-date or GitHub's API is flaky).
                ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                    val checkSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = checkSource,
                        enabled = !state.updateChecking,
                    ) {
                        MorphButton(
                            onClick = { vm.checkForUpdateManually() },
                            interactionSource = checkSource,
                            enabled = !state.updateChecking,
                            active = true,
                        ) {
                            MorphButtonLabel(
                                icon = Icons.Filled.Refresh,
                                label = if (state.updateChecking) "Checking…" else "Check",
                                pending = state.updateChecking,
                            )
                        }
                    }
                    val githubSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = githubSource,
                        enabled = true,
                    ) {
                        MorphTextButton(
                            "GitHub",
                            interactionSource = githubSource,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(com.bloo.bluelink.data.UpdateApi.RELEASES_URL))
                                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                                    )
                                }
                            },
                        )
                    }
                }
                // Shizuku silent-install: the ROW is gated on Shizuku being present, but
                // the card is not — so the update controls above always show.
                if (state.shizukuAvailable) {
                    Spacer(Modifier.height(4.dp))
                    ToggleRow("Install updates seamlessly (Shizuku)", appearance.seamlessInstallShizuku) {
                        vm.setSeamlessInstallShizuku(it)
                    }
                }
                // --- The full download -> install flow, right here in the card ---
                // The old card stopped at "Check": it could tell you an update existed
                // and then point at GitHub. Now the card drives the same state machine
                // the update pebble does -- download, progress, install -- rendered
                // through the shared UpdateStatusLine so neither surface can drift.
                val updateInfo = state.updateAvailable
                if (updateInfo != null && !state.updateTileDismissed) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Update available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        UpdateStatusChip(state)
                    }
                    val newLabel = com.bloo.bluelink.data.buildLabel(updateInfo.run.runNumber)
                    val deltaLabel = if (vm.currentBuildNumber > 0) {
                        "${com.bloo.bluelink.data.buildLabel(vm.currentBuildNumber)} → $newLabel"
                    } else newLabel
                    val seamless = appearance.seamlessInstallShizuku && state.shizukuAvailable
                    Spacer(Modifier.height(6.dp))
                    UpdateStatusLine(deltaLabel, seamless, state, vm)
                    Spacer(Modifier.height(12.dp))
                    val updateSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = updateSource,
                        enabled = !state.updateInstalling && !state.updateDownloading,
                    ) {
                        MorphButton(
                            onClick = {
                                when {
                                    state.updateApkReady -> vm.installDownloadedUpdate()
                                    updateInfo.run.phoneApkUrl != null -> vm.downloadUpdateInBackground()
                                    else -> {
                                        val opened = runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.run.htmlUrl))
                                                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                                            )
                                        }.isSuccess
                                        if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
                                    }
                                }
                            },
                            active = state.updateApkReady,
                            activeContainerColor = com.bloo.bluelink.ui.ChargeGreen,
                            activeContentColor = Color.White,
                            enabled = !state.updateInstalling && !state.updateDownloading,
                            interactionSource = updateSource,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                when {
                                    state.updateApkReady -> Icons.Filled.CheckCircle
                                    state.updateDownloading -> Icons.Filled.Download
                                    else -> Icons.Filled.SystemUpdate
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    state.updateInstalling -> "Installing…"
                                    state.updateApkReady -> if (seamless) "Install now" else "Install"
                                    updateInfo.run.phoneApkUrl != null -> "Download"
                                    else -> "Open release page"
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    val notes = updateInfo.run.releaseNotes
                    if (notes != null) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "What's new",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val notesSource = remember { MutableInteractionSource() }
                                    SafeExpansiveButton(
                                        interactionSource = notesSource,
                                        enabled = true,
                                    ) {
                                        MorphTextButton(
                                            "Full notes",
                                            interactionSource = notesSource,
                                            onClick = {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.run.htmlUrl))
                                                            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                                Text(
                                    notes.trim(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        val notNowSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = notNowSource,
                            enabled = !state.updateDownloading && !state.updateInstalling,
                        ) {
                            MorphTextButton(
                                "Not now",
                                interactionSource = notNowSource,
                                onClick = vm::dismissUpdate,
                                enabled = !state.updateDownloading && !state.updateInstalling,
                            )
                        }
                    }
                }
            }
            }
            item {

            // Weather
            SettingsCard("Weather", Icons.Filled.WbSunny, vm) {
                var weatherQuery by remember { mutableStateOf("") }
                val locationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) vm.useDeviceLocationForWeather()
                    else vm.reportError("Location permission denied. Type a place instead")
                }
                // PopVisible, not a bare `?.let` -- was snapping in/out with zero
                // animation whenever a place got set or cleared.
                PopVisible(visible = appearance.weatherLabel != null) {
                  Column(Modifier.fillMaxWidth()) {
                    // fillMaxWidth() on the Row, not just the Column: a weighted child
                    // needs its immediate parent to actually claim the available width,
                    // not just an ancestor further up -- without it here, the place-name
                    // Text (weight(1f)) had no real width to size against and wrapped
                    // character-by-character ("S/u/n/n/y/v/a/l/e" one letter per line),
                    // ballooning the whole card's height. Same class of bug StatusRow's
                    // own doc warns about; maxLines/ellipsis added as the same guard.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            appearance.weatherLabel.orEmpty(),
                            Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val weatherClearSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = weatherClearSource,
                            enabled = true,
                        ) {
                            MorphTextButton(
                                "Clear",
                                onClick = { vm.clearWeatherLocation() },
                                interactionSource = weatherClearSource,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                  }
                }
                OutlinedTextField(
                    value = weatherQuery,
                    onValueChange = { weatherQuery = it },
                    label = { Text("City or place") },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                )
                Spacer(Modifier.height(8.dp))
                // FlowRow, not a fixed 50/50 Row: at a large display/font size each
                // half was too narrow for "Set place" / "My location", clipping them
                // to "Set a…". FlowRow keeps them side-by-side when they fit and wraps
                // the second button onto its own full-width line when they don't, so
                // the labels stay whole at any font scale.
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val setPlaceSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = setPlaceSource,
                        enabled = weatherQuery.isNotBlank(),
                    ) {
                        MorphTextButton(
                            "Set place",
                            modifier = Modifier.weight(1f),
                            interactionSource = setPlaceSource,
                            enabled = weatherQuery.isNotBlank(),
                            onClick = { vm.setWeatherPlace(weatherQuery); weatherQuery = "" },
                        )
                    }
                    val myLocationSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = myLocationSource,
                        enabled = true,
                    ) {
                        MorphButton(
                            onClick = { locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
                            modifier = Modifier.weight(1f),
                            interactionSource = myLocationSource,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Icon(Icons.Filled.MyLocation, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("My location", fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
            }
        }
          // Full-line, same reason as the leading spacer above: this is the
          // grid's own trailing footer, not a card.
          item(span = StaggeredGridItemSpan.FullLine) {
          Column {
          // About / installed build — the one place the phone shows which build it's
          // running (the update tile shows the AVAILABLE build; this shows the current
          // one). Based on the GitHub Actions run number baked in at CI build time;
          // "dev build" for a local build. buildLabel is the canonical formatter shared
          // with the watch About footer and the update tile's delta.
          Spacer(Modifier.height(8.dp))
          Text(
              "Bloo · " + com.bloo.bluelink.data.buildLabel(vm.currentBuildNumber, com.bloo.bluelink.BuildConfig.BUILD_BRANCH),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth(),
          )
          // The search bar itself now floats fixed to the screen's bottom
          // edge (see below, outside this scrolling column) -- reserve space
          // here so scrolled content never sits behind it.
          Spacer(Modifier.height(bottomInset + 132.dp))
          }
          }
        }
        } // CompositionLocalProvider(LocalHeroTitleFlight)
        } // Box (wide-screen centering)
        // Same blurred scrim GarageScreen uses behind the system clock/battery
        // icons -- this content scrolls behind the status bar too (see the
        // comment above the Column's top spacer). Skipped on a folding
        // phone's compact cover screen, matching GarageScreen/LockOverlay:
        // that tiny layout doesn't draw content under the status bar at all.
        // Also skipped when embedded: this page sits inside GarageScreen's own
        // HorizontalPager, which already draws its own StatusBarScrim on top of
        // every page in it (cars included) -- drawing a second one here stacked
        // the same scrim twice for exactly this one page, reading as a subtly
        // darker/hazier status-bar band than every car page beside it.
        if (!isCompactCoverScreen() && !embedded) StatusBarScrim()
        // The floating "Settings" badge, once its own header has scrolled
        // out of view -- same TitleFlightOverlay every car page uses, sourced
        // from SettingsHeaderRow's title instead of a hero photo card's.
        // Hoisted mode (this slot is settled AND docked) renders NO badge of
        // its own here at all -- GarageScreen renders ONE shared badge
        // covering every page, including this one. Every other state --
        // standalone route, or this slot before it's scrolled into the
        // docked state -- renders its own "Settings" title here instead.
        // See `hoisted`'s own doc.
        // AnimatedVisibility, not a bare `if` -- mirrors VehicleDetailContent's
        // identical wrapping (same 160ms fade GarageScreen's shared hoisted
        // badge uses) for the same reason: without it, this side of the
        // hand-off cut out/in instantly while the hoisted badge ramped over
        // 160ms, leaving a dip/flash right at the dock/undock threshold.
        AnimatedVisibility(
            visible = hoisted == null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            // FloatingNamePill resolves cornerX/reserveEnd/maxWidth/textColor
            // from the context enum -- SETTINGS_EMBEDDED and SETTINGS map to
            // the exact same values this call site used to hand-compute
            // (16dp/60dp cornerX, 192dp reserveEnd, onSurface text). See
            // FloatingNameContext.config for the single source of truth.
            FloatingNamePill(
                context = if (embedded) FloatingNameContext.SETTINGS_EMBEDDED else FloatingNameContext.SETTINGS,
                flight = local.flight,
                screenWidth = screenWidth,
                topInset = topInset,
                onScrollToTop = { settingsScope.launch { settingsGridState.animateScrollToItem(0) } },
                // See `liveFlight`'s own doc just above -- mirrors
                // VehicleDetailContent's identical wiring.
                onSettledChanged = { atRest -> onDockedChanged?.invoke(atRest) },
                // See `pageLabel`'s own doc -- matches the shared hoisted
                // badge's own extraContent so the hand-off has no width to pop.
                extraContent = pageLabel?.let { label ->
                    {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            ) {
                Text(
                    "Settings",
                    // headlineSmall -- matches every other TitleFlightOverlay
                    // content Text (see Screens.kt's identical fixes on the
                    // hoisted/VehicleDetailContent/ExpandedCar badges) for
                    // consistency across every surface this overlay covers.
                    // Settings' own titleScale never actually varies (only
                    // HeroHeader's hero-photo pebble writes titleScale), so
                    // this alone doesn't fix a visible grow/shrink bug here
                    // the way it does on a car page -- it's a font-weight-
                    // consistency fix, not a scale-correctness one.
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Floating back-arrow + "Settings" label + simple/advanced button.
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopStart).statusBarsPadding()
                // FloatingIcon's own 12dp outer padding is what has always kept
                // the "Settings" pill clear of the true screen edge -- but that
                // Icon is skipped entirely when embedded, and this Row has no
                // start padding of its own to fall back on, so the pill sat
                // flush against the edge (and the device's own rounded corner/
                // cutout) with nothing reserving room for it. Reproduces the
                // same 12dp by hand only when there's no FloatingIcon here to
                // provide it for free.
                .then(if (embedded) Modifier.padding(start = HeaderCornerGap) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // No back arrow when embedded -- there's no separate screen it would be
            // returning FROM (swiping to a car does that), and "Back to the app"
            // literally isn't true here: this already is the app's main screen.
            if (!embedded) FloatingIcon(Icons.Filled.ArrowBack, "Back to the app", { vm.closeSettings() })
            Spacer(Modifier.weight(1f))
            // A real segmented control (not a single button that only ever
            // names the OTHER mode) so the CURRENT mode is always obvious at a
            // glance -- the old single-label button was easy to misread as "the
            // mode you're already in" and tap the wrong way.
            Box(
                Modifier
                    .width(172.dp)
                    // Was 20.dp -- MorphSegmented's own track corner is 16.dp,
                    // so the outline ring drawn here never actually matched
                    // the pill's real corners underneath it.
                    .ambientRing(RoundedCornerShape(16.dp))
                    .dropShadow(RoundedCornerShape(16.dp))
                    .frostedRim(RoundedCornerShape(16.dp)),
            ) {
                // Match the "Settings" title pill right next to it (same glass
                // treatment, same track height) instead of the ordinary
                // button-track color/size every other MorphSegmented uses --
                // they're both floating chrome in the same row. MorphSegmented
                // has no backdrop slot of its own, so the blur is drawn here,
                // behind it, at the same corner radius it clips its own
                // background to (20.dp).
                MorphSegmented(
                    options = listOf(
                        SegmentOption("simple", "Simple", null),
                        SegmentOption("advanced", "Advanced", null),
                    ),
                    selectedKey = state.settingsMode,
                    onSelect = { vm.setSettingsMode(it) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
                    // HeaderButtonSize (48dp), not its own one-off 44dp -- the
                    // comment above already says this is meant to match the
                    // "Settings" pill/FloatingIcon's own height in the same
                    // row; it just hadn't actually been set to the same value.
                    trackHeight = HeaderButtonSize,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        // First-run coach mark pointing at the back arrow.
        if (state.showSettingsCoach) {
            val coachAlpha = remember { Animatable(0f) }
            val coachOffset = remember { Animatable(-20f) }
            LaunchedEffect(Unit) {
                launch { coachAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
                launch { coachOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
            }
            Surface(
                onClick = { vm.dismissSettingsCoach() },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 60.dp, end = 12.dp)
                    .graphicsLayer {
                        alpha = coachAlpha.value
                        // .dp.toPx() -- see EmptyScreen's own note; the raw -20f
                        // was 20 PIXELS, not 20dp.
                        translationY = coachOffset.value.dp.toPx()
                    }
                    .dropShadow(RoundedCornerShape(16.dp)),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "That arrow takes you into the app when you're done here. Tap to dismiss.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
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
