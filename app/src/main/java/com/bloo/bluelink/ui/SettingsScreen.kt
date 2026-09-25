@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.os.Build
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
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
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.links
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import com.bloo.uicommon.ReorderColumn
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
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
            // see the LazyColumn this whole sequence now lives in, above -- so only the
            // cards actually scrolled into view are ever composed, same as every other
            // lazy list in the app. `advanced`, declared here in this enclosing `run {}`,
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
                Spacer(Modifier.height(SettingsGapGroup))
                if (state.accounts.isEmpty()) {
                    Text(
                        "Not signed in",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.accounts.forEachIndexed { i, creds ->
                    if (i > 0) Spacer(Modifier.height(SettingsGapSection))
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
                    Column(verticalArrangement = Arrangement.spacedBy(SettingsGapRow)) {
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
                Spacer(Modifier.height(SettingsGapGroup))
                val addAccountSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = addAccountSource,
                    enabled = true,
                ) {
                    // Content-width, not fillMaxWidth() -- a single CTA reads as oversized
                    // stretched edge-to-edge, the same fix already made for Backup & sync's and
                    // Updates' own primary buttons this session; this one was missed then.
                    MorphTextButton(
                        "Add another account",
                        onClick = { vm.beginAddAccount() },
                        interactionSource = addAccountSource,
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
                SettingsCard(
                    "AI",
                    Icons.Filled.AutoAwesome,
                    vm,
                    // The card is inline whenever the CURRENT mode leaves it holding one
                    // setting. In simple mode the auto-summarize toggle below is hidden, so
                    // everything this card can do is the one switch -- and a chevron that opens
                    // a card to reveal a single switch is the disclosure this treatment exists
                    // to remove. Advanced mode has two, so it stays a real card there.
                    //
                    // Gated on what is VISIBLE, not on what the card contains. That distinction
                    // is the bug: counting everything the card could ever show says "two
                    // settings" in both modes, and the card never collapses in either.
                    inlineSetting = if (advanced) {
                        null
                    } else {
                        { InlineToggle(state.aiEnabled) { vm.setAiEnabled(it) } }
                    },
                ) {
                    // Same icon-badge + status-line header as the rest of this pass.
                    val aiTint = if (state.aiEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    StatusHeaderRow(
                        icon = Icons.Filled.AutoAwesome,
                        tint = aiTint,
                        title = "On-device AI",
                        status = if (state.aiEnabled) "On" else "Off",
                    )
                    Spacer(Modifier.height(SettingsGapGroup))
                    // Names the ENGINE. The card is titled "AI" and the header right above
                    // says "On-device AI" with its own on/off status, so a third "On-device
                    // AI" here said the same thing a third time and told you nothing new.
                    ToggleRow("Gemini Nano", state.aiEnabled) { vm.setAiEnabled(it) }
                    Text(
                        "Adds an AI summary pebble to each car and lets you ask the search " +
                            "box plain questions like \"what's the odometer\". Summaries refresh " +
                            "on their own when you open a car, refresh its status, or send a " +
                            "command -- everything runs privately on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
            if (advTransition0.targetState || !advTransition0.isIdle) item {

            // Updates used to have its own card here; it's folded into SettingsHeroCard
            // at the top of this grid now -- see that composable's own doc for why.

            // App-icon shortcuts (long-press the launcher icon)
            AnimatedVisibility(visibleState = advTransition0, enter = collapseEnter(), exit = collapseExit()) {
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
                    Spacer(Modifier.height(SettingsGapRow))
                    state.vehicles.forEach { v ->
                        Spacer(Modifier.height(SettingsGapHairline))
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

            // Map & Navigation
            SettingsCard("Map & Navigation", Icons.Filled.Map, vm) {
                Text(
                    "Open Charge Map API Key",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Required to show nearby EV chargers on the expanded map. Get a free key at openchargemap.org (My Profile → My Apps).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SettingsGapRow))
                var keyInput by remember { mutableStateOf(appearance.chargerApiKey ?: "") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        placeholder = { Text("Paste API key here") },
                        singleLine = true,
                        shape = FieldShape,
                        colors = borderlessFieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            vm.setChargerApiKey(if (keyInput.isBlank()) null else keyInput, null)
                        }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    MorphTextButton(
                        "Save",
                        onClick = { vm.setChargerApiKey(if (keyInput.isBlank()) null else keyInput, null) },
                        enabled = keyInput.isNotBlank() && keyInput != (appearance.chargerApiKey ?: ""),
                        showIcon = false,
                    )
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
                // every other multi-row card in Settings uses (AI) -- this card
                // was the one still opening on two stacked lines of plain text
                // with no at-a-glance state.
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
                Spacer(Modifier.height(SettingsGapGroup))
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
                    //
                    // Sized like every other button in the app -- content width, standard
                    // padding -- not stretched to the card's own width. `active` still marks
                    // it as the primary action (same colour language "Stop" and "Install"
                    // use); fillMaxWidth on TOP of that was the oversized, one-off treatment
                    // reported from a real screenshot, not a second thing this control needs.
                    val setupSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = setupSource,
                        enabled = true,
                    ) {
                        MorphButton(
                            onClick = { showDriveDialog = true },
                            interactionSource = setupSource,
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
                        // Same fix as "Set up auto-sync" a few lines up: content width, not
                        // stretched to the card.
                        MorphButton(
                            onClick = { vm.syncNow() },
                            interactionSource = syncSource,
                            active = true,
                        ) { MorphButtonLabel(icon = Icons.Filled.CloudSync, label = "Sync now", pending = false) }
                    }
                    // A live failure is the one fact that never hides behind the
                    // diagnostics disclosure below — if sync is broken, say so here.
                    state.syncError?.let { err ->
                        Spacer(Modifier.height(SettingsGapRow))
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
                    Spacer(Modifier.height(SettingsGapGroup))
                    MorphSegmented(
                        options = listOf(
                            SegmentOption("wifi", "Wi-Fi only", null),
                            SegmentOption("any", "Any network", null),
                        ),
                        selectedKey = if (state.syncWifiOnly) "wifi" else "any",
                        onSelect = { vm.setSyncWifiOnly(it == "wifi") },
                    )
                    Spacer(Modifier.height(SettingsGapRow))
                    // equalWidths: this row sits directly under the Wi-Fi only/Any network
                    // segmented control, which splits its full width evenly -- left otherwise,
                    // the two buttons packed to their own content width and read as a mismatched
                    // pair next to the evenly-split control right above them.
                    ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp, equalWidths = true) {
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
                    Spacer(Modifier.height(SettingsGapRow))
                    var showSyncDiagnostics by rememberSaveable { mutableStateOf(false) }
                    val diagnosticsSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = diagnosticsSource,
                        enabled = true,
                    ) {
                        // Content-width, matching the equivalent "Troubleshooting steps" toggle
                        // in the Notifications card -- this one was still stretched full width.
                        MorphTextButton(
                            if (showSyncDiagnostics) "Hide diagnostics" else "Diagnostics",
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
                            Spacer(Modifier.height(SettingsGapRow))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    // glassTint, not a one-off surfaceContainerHighest/0.5f literal --
                                    // the same shared neutral fill every other glass surface in the
                                    // app uses, not a fourth slightly-different copy of the same idea.
                                    .background(glassTint(blurred = false))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val lastSyncLabel = com.bloo.bluelink.data.relativeLabel(state.lastSyncMs)
                                StatusRow("Last synced", if (lastSyncLabel.isNotBlank()) lastSyncLabel else "—")
                                // File-identity fingerprint: two phones truly on the SAME Drive
                                // file show the SAME code. If they differ, they picked different
                                // files (Drive allows duplicate names) — the #1 reason sync
                                // doesn't converge, now checkable at a glance across phones.
                                state.syncFileFingerprint?.let { fp ->
                                    StatusRow("File ID", fp, valueMono = true)
                                }
                            }
                            Spacer(Modifier.height(SettingsGapRow))
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
                    Spacer(Modifier.height(SettingsGapGroup))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(SettingsGapGroup))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThemedIcon(Icons.Filled.Description, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Manual backup", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(SettingsGapRow))
                    Text(
                        "A one-time snapshot file. Credentials are never included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(SettingsGapRow))
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
            if (advTransition1.targetState || !advTransition1.isIdle) item {

            // Debug -- app/device diagnostics for support troubleshooting. A power-user
            // diagnostic card like Logs, and it takes its OWN slot in this screen's stagger
            // sequence rather than sharing Logs': the two animate independently.
            AnimatedVisibility(visibleState = advTransition1, enter = collapseEnter(), exit = collapseExit()) {
            SettingsCard("Debug", Icons.Filled.BugReport, vm) {
                DebugSettingsPanel(
                    onCopyToClipboard = { text ->
                        clipboardScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("bloo debug", text)))
                        }
                    },
                )
            }
            }
            }
            item {

            // Display scale
            SettingsCard(
                "Display",
                Icons.Filled.Straighten,
                vm,
                status = if (appearance.unitSystem == "metric") "Metric" else "Imperial",
            ) {
                // Advanced-only: a power-user knob, unlike the Units picker
                // below it which every user needs regardless of mode. PopVisible,
                // not a bare `if` -- was snapping in/out with the mode switch.
                PopVisible(visible = advanced) {
                  Column {
                    UiScaleSlider(appearance, vm)
                    Spacer(Modifier.height(SettingsGapGroup))
                  }
                }
                // SIMPLE, not advanced: this changes what is on the car screen
                // every time you open the app, which is the test for whether a
                // switch belongs in the small set. The text-scale slider above
                // it is advanced by the same test -- it is a knob you set once.
                ToggleRow(
                    "Search on the car screen",
                    appearance.showSearch,
                    description = "A search bubble at the bottom of the car screen and the cover screen. " +
                        "Ask about the car (\"battery level\"), run a command (\"lock my car\"), " +
                        "or jump to a setting. Settings always has it.",
                ) { vm.setShowSearch(it) }
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
            SettingsCard(
                "Font",
                Icons.Filled.TextFields,
                vm,
                // Only cards WITHOUT a StatusHeaderRow of their own get a title-row status --
                // on the ones that have one it would be the same fact twice, ten dp apart,
                // which is exactly the duplication the cover tiles were just cured of.
                status = when (appearance.fontChoice) {
                    FontChoice.ATKINSON -> "Atkinson"
                    FontChoice.GOOGLE_SANS -> "Google Sans"
                    else -> "System"
                },
            ) {
                val labels = mapOf(
                    FontChoice.SYSTEM to "System default",
                    FontChoice.ATKINSON to "Atkinson Hyperlegible",
                    FontChoice.GOOGLE_SANS to "Google Sans",
                )
                Column(verticalArrangement = Arrangement.spacedBy(SettingsGapRow)) {
                    FontChoice.entries.forEach { choice ->
                        ChoiceRow(labels.getValue(choice), appearance.fontChoice == choice) { vm.setFontChoice(choice) }
                    }
                }
            }
            }
            if (advTransition2.targetState || !advTransition2.isIdle) item {

            // Logs
            AnimatedVisibility(visibleState = advTransition2, enter = collapseEnter(), exit = collapseExit()) {
            SettingsCard("Logs", Icons.Filled.Info, vm) {
                // No local expand state any more. The card's OWN chevron (PebbleShell's, via
                // SettingsCard) already governs this body -- nothing inside a collapsed card is
                // composed at all -- so the "Show"/"Hide" button that used to live on this row
                // was a second disclosure for the same content: open the card, then open the log
                // again. One control, the outer one.
                val lineCount = logs.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThemedIcon(Icons.Filled.Info, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp)
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
                                        clipboardScope.launch {
                                            clipboard.setClipEntry(
                                                ClipEntry(ClipData.newPlainText("bloo logs", logs.joinToString("\n"))),
                                            )
                                        }
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
                    Spacer(Modifier.height(SettingsGapHairline))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(SettingsGapHairline))
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
                        Spacer(Modifier.height(SettingsGapHairline))
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
                val alertToggles = listOf(notif.charging, notif.service, notif.doorOpen, notif.running, notif.unlocked, notif.carStarted, notif.chargeComplete)
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
                Spacer(Modifier.height(SettingsGapGroup))
                // First, not last: every other switch in this card is an
                // ALERT the user hopes never fires. This is a live surface
                // they watch on purpose while the car charges.
                ToggleRow(
                    "Live charging updates",
                    notif.charging,
                    description = "A progress bar in the shade and, on Android 16+, in the status bar and " +
                        "lock screen while the car charges -- with the charge limit marked and " +
                        "a Stop button.",
                ) { vm.setNotifyCharging(it) }
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
                        // Real buttons, not clickable labels. These three were bare lines of
                        // small text whose only affordance was the words "Tap to fix" -- the
                        // one place in Settings where something important to press did not
                        // look pressable, and an 8dp-tall target when it was.
                        if (!LiveCharge.isBackgroundUnrestricted(ctx)) {
                            // The question is a caption and the action is a button, rather
                            // than one long sentence inside the button: a standard button label
                            // is one line that never wraps (see MorphButtonLabel), and "Not
                            // starting when charging begins? Tap to fix" does not fit one
                            // inside a Settings card.
                            SettingsCaption("Not starting when charging begins?", bottomGap = SettingsGapHairline)
                            MorphTextButton(
                                text = "Allow background",
                                onClick = { LiveCharge.requestBackgroundUnrestricted(ctx) },
                                contentColor = MaterialTheme.colorScheme.primary,
                                icon = Icons.Filled.Warning,
                            )
                            Spacer(Modifier.height(SettingsGapRow))
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 36) {
                        val ctx = LocalContext.current
                        if (!LiveCharge.isPromotable(ctx)) {
                            SettingsCaption("Not showing in the status bar?", bottomGap = SettingsGapHairline)
                            MorphTextButton(
                                text = "Open system settings",
                                onClick = { LiveCharge.openLiveUpdateSettings(ctx) },
                                contentColor = MaterialTheme.colorScheme.primary,
                                icon = Icons.Filled.Warning,
                            )
                            Spacer(Modifier.height(SettingsGapRow))
                        }
                    }
                    // Still shown even when isPromotable is already true: that check only
                    // covers the generic Android permission, and at least one real OEM (see
                    // LiveUpdateTroubleshootDialog) gates the chip behind a second switch that
                    // permission can't see -- confirmed on a real device this app had no way
                    // to detect from here.
                    MorphTextButton(
                        text = "Troubleshooting steps",
                        onClick = { showTroubleshoot = true },
                        icon = Icons.Filled.Info,
                    )
                    Spacer(Modifier.height(SettingsGapRow))
                    if (showTroubleshoot) {
                        LiveUpdateTroubleshootDialog(onDismiss = { showTroubleshoot = false })
                    }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(SettingsGapRow))

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
                ToggleRow("Car started notifications", notif.carStarted) { vm.setNotifyCarStarted(it) }
                ToggleRow("Charge complete notifications", notif.chargeComplete) { vm.setNotifyChargeComplete(it) }
                Text(
                    "Background checks run roughly every 30 minutes, so alerts may " +
                        "arrive a little after your set time. Door and running alerts " +
                        "include a one-tap action to lock or turn the car off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                Spacer(Modifier.height(SettingsGapGroup))
                if (canBio) {
                    // One control, three states. This used to be a "require fingerprint"
                    // on/off toggle plus a separate lock-timing row, which let people save
                    // contradictory combinations (lock ON with "never re-lock", or a lock
                    // timing shown while the lock itself was OFF). A single group keeps the
                    // flag and its timing as one decision:
                    //   Off        -> no lock at all
                    //   Screen off -> lock on launch, re-lock when the screen turns off
                    //   Immediate  -> lock on launch, re-lock the moment it's backgrounded
                    val timingOn = appearance.biometricLock
                    SettingsSegmentedRow(
                        label = "App lock",
                        options = listOf(
                            SegmentOption("off", LockTiming.OFF.label, null),
                            SegmentOption("screen_off", LockTiming.SCREEN_OFF.label, null),
                            SegmentOption("immediate", LockTiming.IMMEDIATE.label, null),
                        ),
                        selectedKey = when {
                            !timingOn -> "off"
                            appearance.lockTiming == LockTiming.IMMEDIATE -> "immediate"
                            else -> "screen_off"
                        },
                        onSelect = { key ->
                            when (key) {
                                "off" -> {
                                    // Turning the lock OFF needs the same authentication
                                    // turning it on does, otherwise reaching Settings from an
                                    // already-unlocked app lets a single unauthenticated tap
                                    // permanently remove the lock on future cold launches --
                                    // turning momentary physical access into standing access
                                    // to unlocking the car. Failing to authenticate keeps the
                                    // lock on.
                                    val activity = context.findFragmentActivity()
                                    // The cold-start lock decision is (biometricLock && canBio)
                                    // OR a PIN being set (see AppViewModel's own doc) -- clearing
                                    // ONLY biometricLock here used to leave the app locking on
                                    // every launch anyway whenever a PIN was also set, reported
                                    // directly as "turn off locking, it still asks every time".
                                    // "App lock: Off" is this control's own promise that the app
                                    // stops asking at all, so it has to clear BOTH mechanisms --
                                    // the same biometric confirmation already required to get
                                    // here is treated as proof of identity everywhere else in
                                    // this screen (enabling/disabling the lock itself), so
                                    // reusing it to also drop the PIN isn't a weaker gate than
                                    // the PIN removal dialog's own "enter the current PIN" check,
                                    // just a different, already-trusted proof of the same thing.
                                    val pinAlsoSet = state.appPinSet
                                    if (activity == null) {
                                        // Fail closed -- keep the lock -- but say so,
                                        // rather than leaving the control looking stuck.
                                        vm.reportInfo("Couldn't verify it's you. The lock is still on.")
                                    } else {
                                        showBiometricPrompt(
                                            activity = activity,
                                            title = "Turn off app lock",
                                            subtitle = if (pinAlsoSet) {
                                                "Confirm to stop requiring it -- this also removes your PIN"
                                            } else {
                                                "Confirm to stop requiring it"
                                            },
                                            onSuccess = {
                                                vm.setBiometricLock(false)
                                                if (pinAlsoSet) vm.removeAppPin()
                                            },
                                            onError = { },
                                        )
                                    }
                                }
                                else -> {
                                    val timing = if (key == "immediate") LockTiming.IMMEDIATE else LockTiming.SCREEN_OFF
                                    if (timingOn) {
                                        // Already locked: changing *when* it re-locks needs no
                                        // extra proof -- the user just proved who they are to
                                        // be in here, and tightening the timing is harmless.
                                        vm.setLockTiming(timing)
                                    } else {
                                        // Turning the lock ON proves who you are first, then
                                        // both arms it and sets when it re-locks.
                                        val activity = context.findFragmentActivity()
                                        if (activity == null) {
                                            vm.reportInfo("Couldn't verify it's you. The lock wasn't turned on.")
                                        } else {
                                            showBiometricPrompt(
                                                activity = activity,
                                                title = "Enable fingerprint lock",
                                                subtitle = "Confirm to require it on launch",
                                                onSuccess = {
                                                    vm.setBiometricLock(true)
                                                    vm.setLockTiming(timing)
                                                },
                                                onError = { },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
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
                Spacer(Modifier.height(SettingsGapGroup))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(SettingsGapHairline))
                var pinDialog by remember { mutableStateOf<String?>(null) }
                val pinSet = state.appPinSet
                StatusHeaderRow(
                    icon = if (pinSet) Icons.Filled.Lock else Icons.Filled.Pin,
                    tint = if (pinSet) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "App PIN",
                    status = if (pinSet) "On · 4-8 digits" else "Off",
                )
                Spacer(Modifier.height(SettingsGapHairline))
                Text(
                    if (canBio)
                        "A 4-8 digit PIN that works as a backup when fingerprints aren't available."
                    else
                        "This device has no fingerprints, so the app unlocks with this PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SettingsGapGroup))
                ExpressiveButtonRow(spacing = 8.dp) {
                    val pinSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = pinSource,
                        enabled = true,
                    ) {
                        // The shared action button. "Remove" beside it keeps its errorContainer
                        // tone on purpose -- red is the one difference in this row that carries
                        // meaning, so the OTHER half is what had to move onto the standard look.
                        MorphActionButton(
                            label = if (pinSet) "Change PIN" else "Set up PIN",
                            icon = if (pinSet) Icons.Filled.LockReset else Icons.Filled.Lock,
                            onClick = { pinDialog = "set" },
                            interactionSource = pinSource,
                        )
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
                inlineSetting = { InlineToggle(appearance.hapticsEnabled) { vm.setHapticsEnabled(it) } },
            ) {}
            }
            item {

            // Theme
            SettingsCard("Theme", Icons.Filled.Palette, vm) {
                // Same icon-badge + status-line header as the rest of this pass.
                val themeTint = MaterialTheme.colorScheme.tertiary
                val themeLabel = when (appearance.themeMode) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }
                StatusHeaderRow(
                    icon = Icons.Filled.Palette,
                    tint = themeTint,
                    title = "Display mode",
                    status = if (appearance.auroraBackground) "$themeLabel · Aurora" else themeLabel,
                )
                Spacer(Modifier.height(SettingsGapGroup))
                // Dark is always true black (OLED-friendly) now -- see blooColorScheme's
                // own doc. This used to also offer separate "AMOLED"/"+AMOLED" options
                // alongside plain Dark/System; there was no reason to make dark mode
                // dimmer than it needs to be by default, so that's just what Dark/System
                // (while dark) do now, with no extra choice to make.
                SettingsSegmentedRow(
                    label = "Appearance",
                    options = listOf(
                        SegmentOption(ThemeMode.SYSTEM.name, "System", null),
                        SegmentOption(ThemeMode.LIGHT.name, "Light", null),
                        SegmentOption(ThemeMode.DARK.name, "Dark", null),
                    ),
                    selectedKey = appearance.themeMode.name,
                    onSelect = { vm.setThemeMode(ThemeMode.valueOf(it)) },
                )
                // Advanced-only, same tier as the dynamic-color block below --
                // Aurora's motion sub-option is power-user territory, not
                // something a simple-mode user needs (the built-in solid-surface
                // background covers everyone else).
                AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 5), enter = collapseEnter(), exit = collapseExit()) {
                  Column {
                    Spacer(Modifier.height(SettingsGapRow))
                    ToggleRow("Aurora background", appearance.auroraBackground) { vm.setAuroraBackground(it) }
                    Text(
                        "Show a gradient aurora behind the content instead of a solid surface.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Same AnimatedVisibility-wraps-a-Column idiom as the dynamic-
                    // color section below, instead of a bare `if` -- this whole
                    // Motion block otherwise just materialized the instant the
                    // toggle above flipped on.
                    AnimatedVisibility(
                        visible = appearance.auroraBackground,
                        enter = collapseEnter(),
                        exit = collapseExit(),
                    ) {
                        Column {
                            Spacer(Modifier.height(SettingsGapRow))
                            Text("Motion", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(SettingsGapHairline))
                            MorphSegmented(
                                options = listOf(
                                    SegmentOption("static", "Static", null),
                                    SegmentOption("motion", "Motion", null),
                                ),
                                selectedKey = appearance.auroraMotion,
                                onSelect = { vm.setAuroraMotion(it) },
                            )
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
                    Spacer(Modifier.height(SettingsGapGroup))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(SettingsGapRow))
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
                            Spacer(Modifier.height(SettingsGapRow))
                            LabelText("Built-in palettes")
                            Spacer(Modifier.height(SettingsGapHairline))
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
                            Spacer(Modifier.height(SettingsGapRow))
                            LabelText("Custom palettes")
                            Spacer(Modifier.height(SettingsGapHairline))
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
                                    Spacer(Modifier.height(SettingsGapHairline))
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
                    Spacer(Modifier.height(SettingsGapRow))
                    VibrancySlider(appearance, vm)
                    Spacer(Modifier.height(SettingsGapRow))
                    ToggleRow("Pebble outline", appearance.pebbleOutline) { vm.setPebbleOutline(it) }
                  }
                }
            }
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
            SettingsCard("Location", Icons.Filled.LocationOn, vm) {
                Text(
                    "Where \"my location\" points for weather -- and, once set that way, the " +
                        "same live position the map's own device dot and \"distance to car\" use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SettingsGapRow))
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
                        ThemedIcon(Icons.Filled.LocationOn, tint = MaterialTheme.colorScheme.primary, size = 18.dp)
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
                    Spacer(Modifier.height(SettingsGapRow))
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
                Spacer(Modifier.height(SettingsGapRow))
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
                    // weight on the WRAPPER: it is the FlowRow's child, and the button inside
                    // it is not, so the weight there was read by nobody and these two never
                    // split the line evenly the way the FlowRow note above assumes.
                    SafeExpansiveButton(
                        interactionSource = setPlaceSource,
                        enabled = weatherQuery.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        // Both halves of this pair are the shared MorphActionButton now. They
                        // sit in one row doing the same kind of thing, and were a text button
                        // beside a default-filled one -- the mismatch reads as two different
                        // controls when it is one choice with two answers.
                        MorphActionButton(
                            label = "Set place",
                            icon = Icons.Filled.Place,
                            modifier = Modifier.fillMaxWidth(),
                            interactionSource = setPlaceSource,
                            enabled = weatherQuery.isNotBlank(),
                            onClick = { vm.setWeatherPlace(weatherQuery); weatherQuery = "" },
                        )
                    }
                    val myLocationSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = myLocationSource,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    ) {
                        MorphActionButton(
                            label = "My location",
                            icon = Icons.Filled.MyLocation,
                            onClick = { locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
                            modifier = Modifier.fillMaxWidth(),
                            interactionSource = myLocationSource,
                        )
                    }
                }
            }
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
                SettingsCard("Credits", Icons.Filled.Info, vm) {
                    Column {
                        val credits = remember {
                            listOf(
                                CreditEntry(
                                    "Coil",
                                    "Image loading throughout the app -- car photos, map tiles, everything.",
                                    "https://github.com/coil-kt/coil",
                                    Icons.Filled.Image,
                                ),
                                CreditEntry(
                                    "Haze",
                                    "Real backdrop blur behind the status bar and the full-screen map sheet.",
                                    "https://github.com/chrisbanes/haze",
                                    Icons.Filled.BlurOn,
                                ),
                                CreditEntry(
                                    "i5-AutoLock",
                                    "AutoLock ported from Vel-San's original reference implementation.",
                                    "https://github.com/Vel-San/i5-AutoLock",
                                    Icons.Filled.Lock,
                                ),
                                CreditEntry(
                                    "Jetpack Compose",
                                    "The UI toolkit this entire app -- every screen, every pebble, every animation -- is built with.",
                                    "https://developer.android.com/jetpack/compose",
                                    Icons.Filled.Widgets,
                                ),
                                CreditEntry(
                                    "Kotlin",
                                    "The language everything here, front to back, is written in.",
                                    "https://kotlinlang.org",
                                    Icons.Filled.Code,
                                ),
                                CreditEntry(
                                    "kotlinx.serialization",
                                    "Every persisted setting and cached response.",
                                    "https://github.com/Kotlin/kotlinx.serialization",
                                    Icons.Filled.DataObject,
                                ),
                                CreditEntry(
                                    "OkHttp",
                                    "Every network request this app makes.",
                                    "https://square.github.io/okhttp",
                                    Icons.Filled.Language,
                                ),
                                CreditEntry(
                                    "OpenStreetMap",
                                    "Map tiles for the car's and device's location, on the phone and the flip cover. © OpenStreetMap contributors.",
                                    "https://www.openstreetmap.org/copyright",
                                    Icons.Filled.Map,
                                ),
                                CreditEntry(
                                    "Shizuku",
                                    "Optional silent-install path for updates, skipping the manual \"Install anyway\" prompt.",
                                    "https://github.com/RikkaApps/Shizuku",
                                    Icons.Filled.AdminPanelSettings,
                                ),
                            )
                        }
                        credits.forEachIndexed { index, entry ->
                            CreditRow(entry)
                            if (index != credits.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
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
          Spacer(Modifier.height(SettingsGapRow))
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

/** One entry in the Credits card -- see [CreditRow]. */
private data class CreditEntry(
    val name: String,
    val description: String,
    val url: String,
    val icon: ImageVector,
)

/**
 * One row of the Credits card: a small icon chip, the project's name/description,
 * and its actual URL as a tappable link (opened via [openUrl] in a Custom Tab) --
 * replacing what used to be three plain, uncoloured, unclickable Text lines with no
 * visual distinction between them at all. Reported directly as wanting this whole
 * section "beefed out" with real links, not a flat wall of tiny grey text.
 */
@Composable
private fun CreditRow(entry: CreditEntry) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(entry.icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            // The actual link, styled and tappable -- not a caption-coloured, inert
            // copy of the URL. clip+clickable (not the whole Row, which would make
            // the icon/name/description look tappable too when only the link is)
            // sized to just this Row's own content via wrapContentWidth, so the tap
            // target doesn't stretch across empty space to the card's far edge.
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { openUrl(context, entry.url) }
                    .wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    entry.url.removePrefix("https://").removePrefix("http://"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                )
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open link",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
