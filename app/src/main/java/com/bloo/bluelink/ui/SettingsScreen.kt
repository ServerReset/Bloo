@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.app.StatusBarManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.degValue
import com.bloo.bluelink.data.TileCommandRunner
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.displayChargeLimit
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.degLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Settings screen, moved verbatim out of Screens.kt.
 *
 * 3,407 lines, 23% of that file. Nothing here was rewritten: this is a relocation, and the
 * two commits before it exist so that it could be. `UiTokens.kt` took the shared design
 * vocabulary, and the commit after it promoted the 42 declarations that cross this boundary
 * from file-`private` to `internal` -- Kotlin's top-level `private` is FILE-scoped, so every
 * one of those would otherwise have become invisible the moment these lines moved.
 *
 * Splitting the file is worth being honest about: the empirical literature does not support
 * doing it to reduce defects (every study controlling for size either reverses the effect or
 * dissolves it, and none performs a refactoring intervention), and the build-speed argument
 * does not survive verification. What it buys is navigability -- and, specifically here, a
 * file an agent can edit by anchored replacement without the accidents a 14.6k-line file
 * invites.
 *
 * The import list is Screens.kt's, copied whole. Unused imports are warnings, not errors
 * (checked: no `allWarningsAsErrors`), and copying them all is what makes this a mechanical
 * move rather than a judgement call about which of 390 imports these 3,407 lines need.
 * Tidying them is a separate, individually-verifiable pass.
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

/**
 * Delays an advanced-only card's own entrance by `index * STAGGER_STEP_MS` once [advanced]
 * flips true, so switching into Advanced mode cascades card by card instead of every
 * advanced-only section overshooting on the exact same frame -- the same "one shared
 * progress, remapped per item" idea [StaggeredRevealColumn] uses for a pebble's rows,
 * adapted here for a handful of independent [AnimatedVisibility] instances rather than one
 * Layout's worth of children (there's no single shared container to run a Layout-based
 * cascade over: these are whole, separately-composed [SettingsCard]s scattered through one
 * long screen, not rows of one component).
 *
 * The flip back to Simple mode is immediate -- no stagger, no delay -- on purpose, not by
 * omission: [StaggeredRevealColumn]'s own close side went through exactly this mistake
 * first. Staggering a HIDE means most items sit fully visible doing nothing while they wait
 * their turn, then disappear abruptly right at the end, which reads as broken rather than
 * polished (see that composable's doc for the fuller account). Revealing in sequence looks
 * deliberate; hiding in sequence looks like a bug, so only the reveal gets one.
 */
private const val STAGGER_STEP_MS = 45L

@Composable
private fun staggeredAdvancedVisible(advanced: Boolean, index: Int): Boolean {
    var visible by remember { mutableStateOf(advanced) }
    LaunchedEffect(advanced) {
        if (advanced) {
            delay(index * STAGGER_STEP_MS)
            visible = true
        } else {
            visible = false
        }
    }
    return visible
}

/** Same idea as [staggeredAdvancedVisible], for [SettingsSearchResults]'s result cards
 *  instead of the Advanced-mode cards: each result gets a small index-based head start
 *  once [resetKey] (the ranked result set) changes, so a fresh search reads as results
 *  arriving one after another rather than the whole list snapping in at once. No stagger
 *  on the way OUT here either -- there is no "way out" to stagger, since a result that's
 *  no longer in the list is simply never composed again; there's nothing to hide in
 *  sequence the way [staggeredAdvancedVisible]'s own doc warns against. */
private const val SEARCH_RESULT_STAGGER_MS = 35L

@Composable
private fun staggeredResultVisible(resetKey: Any, index: Int): Boolean {
    var visible by remember(resetKey) { mutableStateOf(false) }
    LaunchedEffect(resetKey) {
        delay(index * SEARCH_RESULT_STAGGER_MS)
        visible = true
    }
    return visible
}

/**
 * The tonal icon badge + bold title + colour-coded status line used at the top
 * of several SettingsCard bodies (Accounts, AI, Backup & sync, Notifications,
 * Security, Theme) to give an at-a-glance read of the card's current state
 * before it's opened any further.
 *
 * [icon], [tint] and [status] all animate on change -- the same transition
 * PebbleShell's own header summary uses for its `summary` text -- rather than
 * snapping instantly the moment the setting behind them flips. Every other
 * piece of state change in Settings springs or fades; a status line that
 * just jump-cut to "Off" when everything around it animates was the one
 * inconsistency left.
 */
@Composable
private fun StatusHeaderRow(icon: ImageVector, tint: Color, title: String, status: String) {
    val animTint by androidx.compose.animation.animateColorAsState(tint, label = "statusHeaderTint")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(animTint.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(targetState = icon, label = "statusHeaderIcon") { i ->
                Icon(i, contentDescription = null, tint = animTint, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically { -it / 3 })
                },
                label = "statusHeaderText",
            ) { s ->
                Text(s, style = MaterialTheme.typography.labelMedium, color = animTint, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * `SettingsScreen`'s own (non-hoisted) badge state, bundled so it can be
 * built inside an `if (hoisted == null)` branch as one value -- see
 * `VehicleDetailContent`'s identical `LocalNamePillState` for the full
 * reasoning.
 */
private class LocalSettingsPillState(
    val flight: HeroTitleFlight,
)

/**
 * Settings' own in-content header: title + a short context line, using
 * headlineSmall/Bold for the name (bodySmall/onSurfaceVariant subtitle) --
 * matching the base [PebbleShell] scales its own hero title from (see
 * TitleFlightOverlay's content Text sites in Screens.kt for the same fix),
 * not [CarHeaderRow][com.bloo.bluelink.ui]'s own titleLarge name text.
 *
 * Reports its own real, measured position via [LocalHeroTitleFlight] (the
 * same mechanism [HeroHeader]'s car-page title uses) whenever a flight
 * controller is present, and stays permanently INVISIBLE while it does --
 * see `TitleFlightOverlay`'s own doc (Screens.kt) for why: this slot's only
 * job is to report where the real, single, visible Text should sit when
 * undocked.
 */
@Composable
private fun SettingsHeaderRow(state: UiState) {
    val titleFlight = LocalHeroTitleFlight.current
    // Same fix as HeroHeader's own identical block (Screens.kt) -- force a
    // fresh report the instant the ambient flight identity changes (this
    // slot becoming/ceasing to be the hoisted one), instead of waiting on
    // an incidental relayout that might not come. Uses onSettled, not
    // onPositioned, so it doesn't inherit hysteresis left over from
    // whichever DIFFERENT page (a car) was settled on the shared flight
    // before this one -- see HeroTitleFlight.onSettled's own doc.
    //
    // Runs SYNCHRONOUSLY, during composition -- NOT inside a LaunchedEffect.
    // This WAS a LaunchedEffect(titleFlight) until an audit caught that it
    // never actually got the fix its own comment claimed: a coroutine only
    // starts running after the composition pass that adopts the new flight
    // has already committed, which is strictly AFTER TitleFlightOverlay's
    // own synchronous `val docked by flight.docked` read (and its cold-mount
    // snapTo) has already consumed whatever STALE state the newly-adopted
    // flight was left holding by whichever car page drove it last -- one
    // whole recomposition too late, reading as a visible pop/flash right on
    // the Settings-slot hand-off. See HeroHeader's identical
    // `lastCorrectedFlight` latch (Screens.kt) for the proven fix this
    // mirrors.
    val lastCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lastCorrectedFlight by remember { mutableStateOf<HeroTitleFlight?>(null) }
    if (lastCorrectedFlight !== titleFlight) {
        lastCoords.value?.let { titleFlight?.onSettled(it.positionInRoot()) }
        lastCorrectedFlight = titleFlight
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = if (titleFlight != null) {
                Modifier
                    .onGloballyPositioned {
                        lastCoords.value = it
                        titleFlight.onPositioned(it.positionInRoot())
                    }
                    .alpha(0f)
                    // Position anchor only -- see TitleFlightOverlay's matching
                    // measuring-copy comment (Screens.kt) for why this can't stay
                    // in the accessibility tree.
                    .clearAndSetSemantics {}
            } else {
                Modifier
            },
        )
        val carCount = state.vehicles.size
        val modeLabel = if (state.settingsMode == "advanced") "Advanced" else "Simple"
        Text(
            "$carCount car${if (carCount == 1) "" else "s"} · $modeLabel mode",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        CompositionLocalProvider(LocalHeroTitleFlight provides liveFlight) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(minSize = 380.dp),
            state = settingsGridState,
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Content scrolls behind the status bar; clear the floating back-arrow/
            // segmented-toggle bar above. Full-line: this is the grid's own leading
            // spacer, not a card, so it needs the full row rather than being squeezed
            // into one column.
            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(Modifier.height(topInset + 56.dp))
            }
            // Settings' own in-content header -- same visual weight a car page's own
            // CarHeaderRow has, so this reads as another page in the pager instead of
            // a differently-designed screen bolted on.
            item(span = StaggeredGridItemSpan.FullLine) {
                SettingsHeaderRow(state)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (creds.brand.requiresPin) {
                                MorphTextButton(
                                    "Update PIN",
                                    onClick = { vm.updatePin(creds.brand, pin) },
                                    enabled = pin.isNotBlank() && pin != creds.pin,
                                )
                            }
                            MorphTextButton(
                                if (confirmSignOut) "Tap again to confirm" else "Sign out",
                                onClick = {
                                    if (confirmSignOut) { vm.logout(creds.brand); confirmSignOut = false }
                                    else confirmSignOut = true
                                },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MorphTextButton("Add another account", onClick = { vm.beginAddAccount() }, modifier = Modifier.fillMaxWidth())
                Text(
                    "If commands fail with a locked PIN, fix the Service PIN above. Too " +
                        "many wrong-PIN attempts lock it for a few minutes server-side.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            }
            item {

            // On-device AI - only when the device supports Gemini Nano. Always
            // shown (not advanced-only): it's a headline feature, not a power-
            // user knob, and hiding it behind Advanced made it easy to miss.
            if (state.aiSupported) {
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
            item {

            // (The "Updates" card now lives after Notifications — its natural home —
            // ungated so its controls show with or without Shizuku. See below.)

            // App-icon shortcuts (long-press the launcher icon)
            AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 0), enter = collapseEnter(), exit = collapseExit()) {
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
            if (state.vehicles.isNotEmpty()) {
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
                    MorphButton(
                        onClick = { showDriveDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        active = true,
                    ) { MorphButtonLabel(icon = Icons.Filled.CloudSync, label = "Set up auto-sync", pending = false) }
                } else {
                    // Configured: "Sync now" is THE daily control, so it leads —
                    // ahead of the device registry and the setup/teardown pair,
                    // which are both occasional by comparison.
                    MorphButton(
                        onClick = { vm.syncNow() },
                        modifier = Modifier.fillMaxWidth(),
                        active = true,
                    ) { MorphButtonLabel(icon = Icons.Filled.CloudSync, label = "Sync now", pending = false) }
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MorphTextButton(
                            "Change Drive file",
                            modifier = Modifier.weight(1f),
                            onClick = { showDriveDialog = true },
                        )
                        MorphTextButton(
                            "Disable",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.clearSyncUri() },
                        )
                    }
                    // Troubleshooting tools, not daily controls: the last-synced
                    // stamp (already summarised in the header above), the file
                    // fingerprint, and the two repair actions all fold away by
                    // default so the card stops reading as a wall of equal pills.
                    Spacer(Modifier.height(8.dp))
                    var showSyncDiagnostics by rememberSaveable { mutableStateOf(false) }
                    MorphTextButton(
                        if (showSyncDiagnostics) "Hide diagnostics" else "Diagnostics",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showSyncDiagnostics = !showSyncDiagnostics },
                    )
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
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Non-destructive real-provider round-trip so the user can confirm
                                // sync actually works.
                                MorphTextButton("Test sync", modifier = Modifier.weight(1f), onClick = { vm.testSync() })
                                // "Pull from primary now": force this device to adopt the
                                // primary's full settings — only when a primary exists AND it
                                // isn't this device (pulling from yourself is a no-op). When not
                                // shown, Test sync spans the row on its own.
                                if (state.syncPrimaryId != null && state.syncPrimaryId != state.thisDeviceId) {
                                    MorphTextButton("Pull from primary", modifier = Modifier.weight(1f), onClick = { vm.pullFromPrimary() })
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MorphTextButton(
                            "Export",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.exportSettings(context) },
                        )
                        MorphTextButton(
                            "Restore",
                            modifier = Modifier.weight(1f),
                            onClick = { settingsImportLauncher.launch("application/json") },
                        )
                    }
                  }
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
            item {

            // Links
            AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 2), enter = collapseEnter(), exit = collapseExit()) {
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
            item {

            // Logs
            AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 3), enter = collapseEnter(), exit = collapseExit()) {
            SettingsCard("Logs", Icons.Filled.Info, vm) {
                var logsExpanded by remember { mutableStateOf(false) }
                val lineCount = logs.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Activity log  ·  $lineCount lines",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // PopVisible, not a bare AnimatedVisibility -- this is a small row-level
                    // element popping in next to the header, independent of the (much
                    // larger) disclosure body right below it, which is what PopVisible
                    // exists for rather than the whole-block collapseEnter/collapseExit pair.
                    PopVisible(logsExpanded) {
                        Row {
                            MorphTextButton("Copy", onClick = {
                                clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                            })
                            Spacer(Modifier.width(8.dp))
                            MorphTextButton("Clear", onClick = { vm.clearLogs() })
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    // A labelled text button, not a second MorphExpandButton chevron --
                    // this card's own PebbleShell header already has one of those, and
                    // a lookalike chevron right underneath it read as two stacked
                    // controls for the same thing. This one still guards something the
                    // outer chevron doesn't: a potentially long, monospace raw log dump
                    // that shouldn't blast into view every time the card itself opens,
                    // so it keeps its own disclosure -- just spelled out in words
                    // instead of an icon that mimics the outer one.
                    MorphTextButton(
                        if (logsExpanded) "Hide" else "Show",
                        onClick = { logsExpanded = !logsExpanded },
                    )
                }
                AnimatedVisibility(
                    visible = logsExpanded,
                    enter = collapseEnter(Alignment.Bottom),
                    exit = collapseExit(Alignment.Bottom),
                ) {
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
            item {

            // Quick Settings tiles -- per-tile config is power-user territory,
            // same tier as App shortcuts/Cars above.
            AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 4), enter = collapseEnter(), exit = collapseExit()) {
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
            }
            }
            item {

            // Sounds & vibration
            SettingsCard("Sounds & vibration", Icons.Filled.Vibration, vm) {
                ToggleRow("Haptic feedback", appearance.hapticsEnabled) { vm.setHapticsEnabled(it) }
            }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphButton(
                        onClick = { vm.checkForUpdateManually() },
                        modifier = Modifier.weight(1f),
                        enabled = !state.updateChecking,
                        active = true,
                    ) {
                        MorphButtonLabel(
                            icon = Icons.Filled.Refresh,
                            label = if (state.updateChecking) "Checking…" else "Check",
                            pending = state.updateChecking,
                        )
                    }
                    MorphTextButton(
                        "GitHub",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(com.bloo.bluelink.data.UpdateApi.RELEASES_URL))
                                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Shizuku silent-install: the ROW is gated on Shizuku being present, but
                // the card is not — so the update controls above always show.
                if (state.shizukuAvailable) {
                    Spacer(Modifier.height(4.dp))
                    ToggleRow("Install updates seamlessly (Shizuku)", appearance.seamlessInstallShizuku) {
                        vm.setSeamlessInstallShizuku(it)
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
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(appearance.weatherLabel.orEmpty(), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        MorphTextButton("Clear", onClick = { vm.clearWeatherLocation() })
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
                    MorphTextButton(
                        "Set place",
                        modifier = Modifier.weight(1f),
                        enabled = weatherQuery.isNotBlank(),
                        onClick = { vm.setWeatherPlace(weatherQuery); weatherQuery = "" },
                    )
                    MorphButton(
                        onClick = { locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
                        modifier = Modifier.weight(1f),
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
            val cornerX = if (embedded) 16.dp else 60.dp
            val reserveEnd = 192.dp
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            TitleFlightOverlay(
                flight = local.flight,
                // Standalone route's own back arrow already claims the
                // top-left corner (12dp outer padding + 48dp) -- clear it.
                // Embedded has no back arrow, so it sits at the same 16dp
                // the grid's own content uses, matching the car pages.
                cornerX = cornerX,
                cornerY = topInset + HeaderCornerGap,
                // Clears the Simple/Advanced segmented toggle in the
                // top-right (172dp wide, plus its own breathing room).
                reserveEnd = reserveEnd,
                maxWidth = screenWidth - cornerX - reserveEnd - 32.dp,
                // Fixed -- Settings has no hero photo to morph a colour against.
                textColorOverride = MaterialTheme.colorScheme.onSurface,
                onClick = { settingsScope.launch { settingsGridState.animateScrollToItem(0) } },
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

/**
 * Ordered troubleshooting steps covering the two different ways this bar can fail to
 * show correctly: not starting/updating reliably AT ALL (steps 1-2, background
 * execution), and showing but never promoting to a status-bar/lock-screen chip
 * (steps 3-5) -- the second half is a failure mode this app can neither detect nor
 * fix from code past the first two steps, because every cause after that lives
 * outside the documented Android APIs (see [LiveCharge]'s class doc: all nine
 * code-checkable promotion conditions are satisfied unconditionally by
 * [LiveCharge.update]).
 *
 * Step 1 was reported from a real device as "live notifications are not triggering
 * all the time... whenever there is charging happening it should always pull a live
 * notification": the bar is posted/updated by a background WorkManager poll
 * (AlertWorker's 30-minute tick, and the 5-minute chain it kicks off once a car is
 * found charging), and neither one runs at all while the OS considers Bloo
 * battery-restricted -- a car that starts charging while the app hasn't been opened
 * in a while can sit unnoticed well past that 30-minute window, which reads
 * exactly like "not triggering," not like a chip-promotion problem.
 *
 * Step 5 is Samsung-only and was not theoretical: confirmed live on a real Samsung
 * phone running One UI 8.5 (fully patched, well past the general Live Updates
 * rollout) that the chip stayed dark even with every documented condition met AND
 * [LiveCharge.isPromotable] already reporting true, because One UI hides a SECOND
 * gate -- "Live notifications for all apps" -- inside Developer options, off by
 * default, invisible to the standard `canPostPromotedNotifications()` API this app
 * already checks. Flipping it was the fix. Samsung's OWN "put unused apps to sleep"
 * battery feature (step 1's own Samsung note) is a THIRD, separate gate again --
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` only covers the standard Android one.
 * [LiveUpdateTroubleshootDialog] can't detect either OEM state itself (no API
 * exists to query them), only point at where to look.
 */
@Composable
private fun LiveUpdateTroubleshootDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isSamsung = remember { Build.MANUFACTURER.lowercase() == "samsung" }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        icon = Icons.Filled.Info,
        title = "Live update not showing?",
        text = {
            Text("A few things to check, in order:", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            TroubleshootStep(
                1,
                "Not appearing or updating reliably at all -- especially if it takes a while after charging starts? Use the \"Tap to fix\" link above if it's showing: that's Android's battery-optimization exemption, needed for the background check that posts and updates the bar to run on schedule while the app isn't open." +
                    if (isSamsung) " Samsung also has its OWN separate \"sleeping apps\" restriction, not covered by that fix -- check Settings → Battery → Background usage limits → Sleeping apps / Deep sleeping apps and make sure Bloo isn't listed there." else "",
            )
            TroubleshootStep(2, "Make sure \"Live charging updates\" is on above, and the car is actually charging -- the bar only exists while charging is true.")
            TroubleshootStep(3, "Below Android 16, the chip can never appear anywhere -- only the plain progress bar in the shade. That's expected, not a bug.")
            TroubleshootStep(4, "On Android 16+, use the \"Tap to fix\" link above if it's showing for the status bar -- that's the OS's own per-app Live Updates permission.")
            if (isSamsung) {
                TroubleshootStep(
                    5,
                    "Samsung phones have a SECOND, separate switch this app can't see or set: " +
                        "Settings → Developer options → a \"Live notifications\" toggle " +
                        "(exact wording varies by One UI version). If Developer options aren't " +
                        "enabled yet: Settings → About phone → tap \"Build number\" 7 times.",
                )
            }
        },
        buttons = {
            MorphButton(
                onClick = { LiveCharge.requestBackgroundUnrestricted(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow background activity") }
            if (isSamsung) {
                MorphButton(
                    onClick = { LiveCharge.openDeveloperOptions(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open Developer options") }
            }
            MorphTextButton("Close", onDismiss, modifier = Modifier.fillMaxWidth())
        },
    )
}

@Composable
private fun TroubleshootStep(number: Int, text: String) {
    Row(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One reorderable car entry in Settings; tap to expand its setup + photo. */
@Composable
private fun CarSettingsCard(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    expanded: Boolean,
    dragging: Boolean,
    dragHandle: Modifier,
    onToggle: () -> Unit,
    onPickPhoto: () -> Unit,
    collapsible: Boolean = true,
) {
    val seats = state.seatConfigs[v.vin] ?: SeatConfig()
    val cardBg by androidx.compose.animation.animateColorAsState(
        if (dragging) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "carCardBg",
    )
    // The exact same collapsible pebble every car's own pebble list on the
    // garage screen uses -- same bounce-open/calm-close springs, same corner
    // morph, same per-row staggered reveal, same "hold the header to drag"
    // idiom (no separate drag-handle icon; PebbleShell never draws one, and
    // this card used to be the only place in Settings that did). It used to
    // be its own bespoke Card + Row + AnimatedVisibility, a lookalike that
    // drifted from every other pebble's motion any time that shared spec
    // changed, which is what "standard" was pointing at.
    //
    // The collapsed header traded the old car-photo thumbnail for the same
    // icon + title + summary shape every other pebble uses -- the photo
    // itself is unchanged and still front-and-centre in the Photo group
    // below once expanded, so nothing about it is actually lost, only where
    // it first appears.
    PebbleShell(
        expanded = expanded,
        onToggle = onToggle,
        icon = Icons.Filled.DirectionsCar,
        title = v.name,
        vm = vm,
        dragHandle = dragHandle,
        summary = "${v.model} · ${state.powertrainLabel(v)}",
        containerColor = cardBg,
        forceExpanded = !collapsible,
    ) {
        SettingsGroup("Powertrain") {
            PowertrainPicker(current = state.powertrainOf(v)) { pt -> vm.setPowertrain(v, pt) }
        }

        // Only Hyundai/Genesis US vehicles have a real head-unit generation to
        // confirm -- see Vehicle.platformOverridable's own doc. Every other
        // brand/region always resolves the same way regardless, so showing
        // this picker there would be a control with no actual effect.
        if (v.platformOverridable) {
            SettingsGroup("Head-unit generation") {
                Text(
                    "Bloo can't always tell Gen5W and ccNC head units apart from the " +
                        "API alone. Confirm which one this car has so features like " +
                        "Trips only show up when they're actually available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                PlatformPicker(current = state.platformOf(v)) { pt -> vm.setPlatform(v, pt) }
            }
        }

        SettingsGroup("Climate features") {
            Text(
                "The remote climate command controls four seat positions. Enable " +
                    "heating and/or cooling for the seats your car actually has.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SeatPositions.forEach { pos ->
                SeatConfigRow(pos.label, pos.heat(seats), pos.cool(seats),
                    { vm.setSeatFlag(v, pos.heatKey, it) }, { vm.setSeatFlag(v, pos.coolKey, it) })
            }
            ToggleRow("Heated steering wheel", seats.steeringWheel) { vm.setSeatFlag(v, "sw", it) }
        }

        if (state.settingsMode == "advanced") SettingsGroup("Default climate start") {
            val carPresets = state.climatePresets[v.vin].orEmpty()
            val currentDefault = state.defaultClimatePresets[v.vin] ?: "smart"
            Text(
                "When the climate Start button is tapped (collapsed view), " +
                    "the app runs your chosen preset or smart climate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            MorphSegmented(
                options = buildList {
                    add(SegmentOption("smart", "Smart", null))
                    carPresets.forEach { p -> add(SegmentOption(p.id, p.name, null)) }
                },
                selectedKey = currentDefault,
                onSelect = { key -> vm.setDefaultClimatePreset(v.vin, key.takeIf { it != "smart" }) },
            )
        }

        // Per-car palette override: existed in SettingsStore/AppViewModel
        // (setCarPaletteId) with no UI entry point anywhere -- only shown
        // once there's at least one custom palette to actually choose.
        val appearance = LocalAppearance.current
        if (state.settingsMode == "advanced" && appearance.customPalettes.isNotEmpty()) {
            SettingsGroup("Palette override") {
                Text(
                    "Give this car its own colour palette instead of the app-wide theme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    appearance.customPalettes.forEach { palette ->
                        val selected = appearance.carCustomPaletteIds[v.vin] == palette.id
                        CustomPaletteSwatch(
                            palette = palette,
                            selected = selected,
                            onClick = { vm.setCarPaletteId(v.vin, if (selected) null else palette.id) },
                            onEdit = {},
                        )
                    }
                }
            }
        }

        SettingsGroup("Photo") {
            val storedImage = state.imageUrls[v.vin]
            // A live preview instead of just "Custom photo set" as plain
            // text -- there was no way to actually see the effect of a
            // photo change without leaving Settings and finding this car
            // on the garage screen.
            if (!storedImage.isNullOrBlank()) {
                AsyncImage(
                    model = rememberPhotoModel(storedImage),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            }
            if (storedImage != null && storedImage.startsWith("/")) {
                Text(
                    "Custom photo set",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = storedImage ?: "",
                    onValueChange = { vm.setVehicleImage(v.vin, it) },
                    label = { Text("Image URL (blank = gradient)") },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MorphTextButton("Choose photo", onClick = onPickPhoto)
                if (state.imageUrls[v.vin] != null) {
                    MorphTextButton("Clear", onClick = { vm.setVehicleImage(v.vin, "") })
                }
            }
        }

        // Identity & service tracking and pebble visibility are both
        // power-user record-keeping, not something a first-time or
        // casual user needs to see every time they open a car's
        // settings -- Simple mode now only shows what actually changes
        // which controls appear (photo, powertrain, seat/climate
        // features), matching Default climate start/Palette override
        // above.
        if (state.settingsMode == "advanced") {
            SettingsGroup("Identity & service") {
                SelectionContainer { StatusRow("VIN", v.vin) }
                OutlinedTextField(
                    value = state.licensePlates[v.vin] ?: "",
                    onValueChange = { vm.setLicensePlate(v.vin, it) },
                    label = { Text("License plate") },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MilesField(state.lastServiceMiles[v.vin], "Last service (mi)", Modifier.weight(1f)) {
                        vm.setLastServiceMiles(v.vin, it)
                    }
                    MilesField(state.serviceIntervalMiles[v.vin], "Interval (mi)", Modifier.weight(1f)) {
                        vm.setServiceIntervalMiles(v.vin, it)
                    }
                }
            }

            SettingsGroup("Sections shown") {
                val labels = mapOf(
                    "charge" to "Charge / fuel",
                    "climate" to "Climate",
                    "location" to "Location",
                    "weather" to "Weather",
                    "trips" to "Trips",
                    "info" to "Car info",
                    "diagnostics" to "Diagnostics",
                    "ai" to "AI summary",
                )
                com.bloo.bluelink.data.HIDEABLE_SECTIONS
                    // The AI toggle only matters when AI is enabled for this device.
                    .filter { it != "ai" || state.aiEnabled }
                    .forEach { sec ->
                        ToggleRow(labels[sec] ?: sec, !state.isPebbleHidden(v.vin, sec)) { show ->
                            vm.setSectionHidden(v, sec, !show)
                        }
                    }
            }
        }
    }
}

/**
 * A digits-only "minutes" field for the notification-delay settings, clamped to 1..120.
 * It owns the edit buffer: [initial] seeds it and re-seeds whenever the persisted value
 * changes (via `remember(initial)`), while [onSet] fires only for an in-range number, so
 * a half-typed or out-of-range value is shown but never persisted. The three delay fields
 * (door-open, running, unlocked) differ only in seed, label and setter.
 */
@Composable
private fun MinutesField(initial: Int, label: String, onSet: (Int) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it.filter(Char::isDigit)
            text.toIntOrNull()?.takeIf { m -> m in 1..120 }?.let(onSet)
        },
        label = { Text(label) },
        singleLine = true,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

/**
 * A digits-only mileage field. The service card lays two of these side by side (each
 * `Modifier.weight(1f)`) while the search index surfaces the same two one at a time
 * (`Modifier.fillMaxWidth()`), so the width sits with the caller; everything else --
 * the digit filter, number keyboard, single line and [FieldShape] -- is identical and
 * lives here so the four copies can't drift apart.
 */
@Composable
private fun MilesField(value: Int?, label: String, modifier: Modifier, onSet: (Int?) -> Unit) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { onSet(it.filter(Char::isDigit).toIntOrNull()) },
        label = { Text(label) },
        singleLine = true,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** A titled, boxed sub-group inside the per-car settings card, for hierarchy. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

private val SearchStopwords = setOf(
    "for", "the", "of", "show", "me", "what", "whats", "is", "a", "an", "to",
    "car", "cars", "my", "s", "setting", "settings", "get", "in",
)

/**
 * Words people use for things this app calls something else.
 *
 * The index is written in the app's own vocabulary, which is the vocabulary of
 * someone who already knows where everything is. A search box is used by
 * someone who does not: they type "vibrate", not "haptic feedback"; "dark
 * mode", not "theme"; "gps", not "location". Rather than stuff every synonym
 * into every entry's keyword string -- which has to be remembered at each of
 * the ~60 call sites, and silently is not -- each query token expands to
 * itself plus its synonyms, and an entry matching ANY of them counts as
 * matching the token.
 *
 * Written token -> app vocabulary, not the reverse: this maps what a person
 * types onto what the index contains.
 */
private val SearchSynonyms: Map<String, List<String>> = mapOf(
    "vibrate" to listOf("haptic"),
    "vibration" to listOf("haptic"),
    "buzz" to listOf("haptic"),
    "dark" to listOf("theme", "night"),
    "light" to listOf("theme"),
    "night" to listOf("theme", "dark"),
    "colour" to listOf("color", "palette"),
    "colours" to listOf("color", "palette"),
    "gps" to listOf("location"),
    "map" to listOf("location"),
    "where" to listOf("location"),
    "parked" to listOf("location"),
    "font" to listOf("text", "typeface"),
    "size" to listOf("scale", "text"),
    "bigger" to listOf("scale", "text"),
    "smaller" to listOf("scale", "text"),
    "mileage" to listOf("odometer", "miles"),
    "miles" to listOf("odometer"),
    "km" to listOf("odometer", "kilometres"),
    "range" to listOf("battery", "fuel"),
    "charge" to listOf("battery", "charging"),
    "percent" to listOf("battery", "charge"),
    "battery" to listOf("charge"),
    "plug" to listOf("charge", "charging"),
    "ac" to listOf("climate"),
    "heat" to listOf("climate"),
    "heater" to listOf("climate"),
    "cool" to listOf("climate"),
    "aircon" to listOf("climate"),
    "defrost" to listOf("climate", "defog"),
    "warm" to listOf("climate"),
    "preheat" to listOf("climate"),
    "seats" to listOf("seat"),
    "doors" to listOf("lock", "door"),
    "alarm" to listOf("horn"),
    "beep" to listOf("horn"),
    "flash" to listOf("lights"),
    "headlights" to listOf("lights"),
    "watch" to listOf("wear", "wearable"),
    "backup" to listOf("sync", "drive", "google"),
    "cloud" to listOf("sync", "drive"),
    "notify" to listOf("notification", "alert"),
    "notifications" to listOf("notification", "alert"),
    "password" to listOf("pin", "credentials", "login"),
    "signout" to listOf("logout", "sign"),
    "plate" to listOf("license", "registration"),
    "service" to listOf("maintenance"),
    "tyre" to listOf("tire"),
    "update" to listOf("version", "upgrade"),
    "language" to listOf("locale"),
    "units" to listOf("unit", "metric", "imperial"),
    "celsius" to listOf("metric", "unit"),
    "fahrenheit" to listOf("imperial", "unit"),
)

/** A token and every form of it worth matching. */
private fun expandToken(t: String): List<String> {
    val extra = SearchSynonyms[t] ?: return listOf(t)
    return buildList { add(t); addAll(extra) }
}

/** A runner command id as a sentence fragment, for the confirm card. */
private fun aiCommandLabel(cmd: String): String = when (cmd) {
    "lock" -> "Lock"
    "unlock" -> "Unlock"
    "charge_on" -> "Start charging"
    "charge_off" -> "Stop charging"
    "lights" -> "Flash the lights on"
    "horn" -> "Sound the horn on"
    "climate_on" -> "Start climate on"
    "climate_off" -> "Stop climate on"
    else -> "Run on"
}

private class SearchEntry(val title: String, val haystack: String, val content: @Composable () -> Unit)

/**
 * Declarative description of one plain on/off setting, so a new simple toggle
 * needs exactly one entry here to become searchable -- not a hand-written
 * [SearchEntry] closure duplicating the same `ToggleRow(label, checked) { onToggle }`
 * shape every other toggle already uses. This is the "dynamic index" for the
 * subset of settings that fit it: anything that is genuinely just a checked
 * state and a setter reads its search row from this single list instead of a
 * bespoke `add(...)` call. Settings whose search behaviour has to be more than
 * a toggle -- a segmented picker, a confirm-gated biometric prompt, a slider,
 * anything per-vehicle -- still declare themselves explicitly below; forcing
 * those through this shape would be the same regression the biometric entry's
 * own comment warns about (a search shortcut skipping a step the real row
 * enforces).
 */
internal class ToggleSpec(
    val title: String,
    val keywords: String,
    /** Shown on the row itself; defaults to [title] since most toggles read
     *  identically in both places. Only a few (e.g. "Dynamic color (Material
     *  You)") spell the row out more fully than the search title. */
    val label: String = title,
    val visible: (UiState) -> Boolean = { true },
    val checked: (SettingsStore.Appearance, SettingsStore.NotificationPrefs, UiState) -> Boolean,
    val onToggle: (AppViewModel, Boolean) -> Unit,
)

/** Every plain app-wide toggle, in the order it should appear when searched.
 *  Add a new one here -- not a new `add(...)` call in [SettingsSearchResults]
 *  -- and it is searchable with no other change. */
internal val ToggleSettings = listOf(
    ToggleSpec(
        title = "Haptic feedback", keywords = "vibration vibrate buzz sound",
        checked = { a, _, _ -> a.hapticsEnabled }, onToggle = { vm, v -> vm.setHapticsEnabled(v) },
    ),
    ToggleSpec(
        title = "Open links in app", keywords = "browser tab links",
        checked = { a, _, _ -> a.linksInApp }, onToggle = { vm, v -> vm.setLinksInApp(v) },
    ),
    ToggleSpec(
        title = "Live charging updates", keywords = "notification charging live progress ongoing bar ev limit",
        checked = { _, n, _ -> n.charging }, onToggle = { vm, v -> vm.setNotifyCharging(v) },
    ),
    ToggleSpec(
        title = "Service due alerts", keywords = "notification reminder service",
        checked = { _, n, _ -> n.service }, onToggle = { vm, v -> vm.setNotifyService(v) },
    ),
    ToggleSpec(
        title = "Door-left-open alerts", keywords = "notification door open",
        checked = { _, n, _ -> n.doorOpen }, onToggle = { vm, v -> vm.setNotifyDoor(v) },
    ),
    ToggleSpec(
        title = "Car-running alerts", keywords = "notification engine climate running left on",
        checked = { _, n, _ -> n.running }, onToggle = { vm, v -> vm.setNotifyRunning(v) },
    ),
    ToggleSpec(
        title = "Left-unlocked alerts", keywords = "notification unlocked lock left open",
        checked = { _, n, _ -> n.unlocked }, onToggle = { vm, v -> vm.setNotifyUnlocked(v) },
    ),
    ToggleSpec(
        title = "Aurora background", keywords = "gradient animated theme background glow",
        checked = { a, _, _ -> a.auroraBackground }, onToggle = { vm, v -> vm.setAuroraBackground(v) },
    ),
    ToggleSpec(
        title = "Dynamic color", label = "Dynamic color (Material You)", keywords = "material you wallpaper theme color",
        checked = { a, _, _ -> a.dynamicColor }, onToggle = { vm, v -> vm.setDynamicColor(v) },
    ),
    ToggleSpec(
        title = "Pebble outline", keywords = "border rim card theme appearance",
        checked = { a, _, _ -> a.pebbleOutline }, onToggle = { vm, v -> vm.setPebbleOutline(v) },
    ),
    // Same top-level gate the AI card itself uses -- these two only mean
    // anything on a device Gemini Nano actually supports, same reason the
    // card is hidden entirely rather than shown disabled.
    ToggleSpec(
        title = "On-device AI", label = "On-device AI (Gemini Nano)", keywords = "gemini nano ai summary assistant privacy on-device",
        visible = { it.aiSupported }, checked = { _, _, s -> s.aiEnabled }, onToggle = { vm, v -> vm.setAiEnabled(v) },
    ),
    ToggleSpec(
        title = "Summarize automatically", keywords = "ai auto summary refresh",
        visible = { it.aiSupported }, checked = { _, _, s -> s.aiAuto }, onToggle = { vm, v -> vm.setAiAuto(v) },
    ),
    // Same gate as the row itself (Backup & sync): only meaningful with
    // Shizuku actually installed and running.
    ToggleSpec(
        title = "Install updates seamlessly", label = "Install updates seamlessly (Shizuku)", keywords = "shizuku silent install update",
        visible = { it.shizukuAvailable }, checked = { a, _, _ -> a.seamlessInstallShizuku }, onToggle = { vm, v -> vm.setSeamlessInstallShizuku(v) },
    ),
)

/**
 * The per-vehicle counterpart of [ToggleSpec]: a plain on/off setting that
 * exists once PER CAR -- a seat's heat/cool flag, the heated-steering-wheel
 * flag, whether a dashboard section shows for that car -- rather than once
 * for the whole app. [CarSettingsCard] (the real "Cars" settings card) is
 * the source of truth for all of these; this list is what makes them
 * searchable without a hand-written [SearchEntry] closure per car per
 * toggle, the same duplication [ToggleSettings] already removed on the
 * app-wide side.
 */
internal class VehicleToggleSpec(
    val title: (Vehicle) -> String,
    val keywords: (Vehicle) -> String,
    val label: String,
    val visible: (Vehicle, UiState) -> Boolean = { _, _ -> true },
    val checked: (Vehicle, UiState) -> Boolean,
    val onToggle: (AppViewModel, Vehicle, Boolean) -> Unit,
)

/** Every plain per-car toggle: the four seat positions' heat and cool flags,
 *  the heated steering wheel flag, and which of [com.bloo.bluelink.data.HIDEABLE_SECTIONS]
 *  shows on that car's dashboard -- generated once per position/section here
 *  instead of needing its own [SearchEntry] written out by hand. Reuses
 *  [SeatPositions] (Screens.kt), the same list [CarSettingsCard] itself
 *  builds its seat rows from, so the two can't drift out of sync with
 *  each other on label or key. */
internal val VehicleToggleSettings: List<VehicleToggleSpec> = buildList {
    SeatPositions.forEach { pos ->
        add(
            VehicleToggleSpec(
                title = { v -> "${pos.label} seat heat · ${v.name}" },
                keywords = { v -> "seat heat warm climate ${v.name}" },
                label = "${pos.label} seat heat",
                checked = { v, s -> pos.heat(s.seatConfigs[v.vin] ?: SeatConfig()) },
                onToggle = { vm, v, value -> vm.setSeatFlag(v, pos.heatKey, value) },
            ),
        )
        add(
            VehicleToggleSpec(
                title = { v -> "${pos.label} seat cool · ${v.name}" },
                keywords = { v -> "seat cool ventilated climate ${v.name}" },
                label = "${pos.label} seat cool",
                checked = { v, s -> pos.cool(s.seatConfigs[v.vin] ?: SeatConfig()) },
                onToggle = { vm, v, value -> vm.setSeatFlag(v, pos.coolKey, value) },
            ),
        )
    }
    add(
        VehicleToggleSpec(
            title = { v -> "Heated steering wheel · ${v.name}" },
            keywords = { v -> "steering wheel heat climate ${v.name}" },
            label = "Heated steering wheel",
            checked = { v, s -> (s.seatConfigs[v.vin] ?: SeatConfig()).steeringWheel },
            onToggle = { vm, v, value -> vm.setSeatFlag(v, "sw", value) },
        ),
    )
    // Same labels CarSettingsCard's own "Sections shown" group uses -- kept
    // as a second copy rather than hoisted shared, since hoisting a map two
    // functions apart from either of its uses would cost more to find than
    // the eight-line literal costs to duplicate.
    val sectionLabels = mapOf(
        "charge" to "Charge / fuel", "climate" to "Climate", "location" to "Location",
        "weather" to "Weather", "trips" to "Trips", "info" to "Car info",
        "diagnostics" to "Diagnostics", "ai" to "AI summary",
    )
    com.bloo.bluelink.data.HIDEABLE_SECTIONS.forEach { sec ->
        val sectionLabel = sectionLabels[sec] ?: sec
        add(
            VehicleToggleSpec(
                title = { v -> "Show $sectionLabel · ${v.name}" },
                keywords = { v -> "section hide show dashboard card $sectionLabel ${v.name}" },
                label = "Show $sectionLabel",
                // The AI toggle only matters when AI is enabled for this device --
                // same gate CarSettingsCard's own "Sections shown" group uses.
                visible = { _, s -> sec != "ai" || s.aiEnabled },
                checked = { v, s -> !s.isPebbleHidden(v.vin, sec) },
                onToggle = { vm, v, value -> vm.setSectionHidden(v, sec, !value) },
            ),
        )
    }
}

/** True if any WORD in [hay] starts with [prefix] -- "lim" hits "charge limit"
 *  but not "unlimited". Scanning for the boundary beats splitting the string,
 *  which would allocate a list per entry per keystroke. */
private fun hasWordStarting(hay: String, prefix: String): Boolean {
    var i = hay.indexOf(prefix)
    while (i >= 0) {
        if (i == 0 || !hay[i - 1].isLetterOrDigit()) return true
        i = hay.indexOf(prefix, i + 1)
    }
    return false
}

/** Within one insertion, deletion or substitution. Deliberately not a full
 *  Levenshtein: one typo is what people actually make, and bounding it at one
 *  keeps this O(n) and keeps "haptic" from matching "static". */
private fun withinOneEdit(a: String, b: String): Boolean {
    if (a == b) return true
    val (short, long) = if (a.length <= b.length) a to b else b to a
    if (long.length - short.length > 1) return false
    var i = 0
    var j = 0
    var slack = 1
    while (i < short.length && j < long.length) {
        if (short[i] == long[j]) { i++; j++; continue }
        if (slack == 0) return false
        slack = 0
        if (short.length == long.length) { i++; j++ } else j++
    }
    return true
}

/** True if any word of [hay] is within one edit of [token]. */
private fun hasFuzzyWord(hay: String, token: String): Boolean {
    var start = 0
    while (start <= hay.length) {
        var end = start
        while (end < hay.length && hay[end].isLetterOrDigit()) end++
        if (end > start && withinOneEdit(hay.substring(start, end), token)) return true
        start = if (end == start) start + 1 else end + 1
    }
    return false
}

/**
 * How well one entry answers the query, or null for "not at all".
 *
 * The old engine was `tokens.all { it in haystack }` and then showed whatever
 * survived IN DECLARATION ORDER. Two problems, and the second is the one you
 * feel: a bare substring test makes "car" hit "carbon", and with no ranking at
 * all the best match for "charge" was whichever charge-related setting happened
 * to be added to the list first. Ranking is most of what makes a search feel
 * like it understands the question.
 *
 * Every token must still match something ([tokens] are ANDed) -- narrowing by
 * adding a word is the one behaviour people rely on. What changed is WHERE a
 * token matched now counts: the title outranks the keywords, the start of a
 * word outranks the middle of one, and shorter titles win ties, so "charge
 * limit" beats "charge limit notification threshold" for the query "charge
 * limit".
 */
private fun searchScore(tokens: List<String>, e: SearchEntry, fuzzy: Boolean): Int? {
    val title = e.title.lowercase()
    var total = 0
    for (t in tokens) {
        // Best hit across the token and its synonyms. A synonym that lands is
        // worth less than the literal word: someone who typed "haptic" meant
        // the haptics entry more certainly than someone who typed "vibrate".
        var hit = 0
        for ((i, form) in expandToken(t).withIndex()) {
            val penalty = if (i == 0) 0 else 30
            val score = when {
                title == form -> 1000
                title.startsWith(form) -> 500
                hasWordStarting(title, form) -> 320
                form in title -> 160
                hasWordStarting(e.haystack, form) -> 90
                form in e.haystack -> 40
                fuzzy && form.length >= 4 && hasFuzzyWord(e.haystack, form) -> 10
                else -> 0
            }
            if (score > 0) hit = maxOf(hit, score - penalty)
        }
        if (hit == 0) return null
        total += hit
    }
    // Tie-break on brevity: among equally-matched entries the shortest title is
    // the most specific answer, not the least.
    return total * 100 - title.length
}

/** A vehicle command recognised in a free-form search query. [cmd]/[climateTarget]
 *  map directly onto [com.bloo.bluelink.data.TileCommandRunner]'s own command
 *  vocabulary, so search runs commands through the exact same path the Quick
 *  Settings tiles use. */
internal class ParsedVehicleCommand(val cmd: String, val climateTarget: String = "default", val label: String)

/** Recognises a small, deliberately-conservative set of command phrasings --
 *  lock/unlock, start/stop/smart climate, start/stop charging -- rather than
 *  attempting general natural-language command parsing. Order matters:
 *  "unlock" is checked before the bare "lock" pattern so "unlock" doesn't
 *  also match as "lock".
 *
 *  Direction is encoded IN the command itself, never left for the runner to
 *  re-derive from the last-known snapshot. When the phrasing says start / stop
 *  / turn on / turn off / begin, we emit the explicit directional token
 *  (`climate_on`/`climate_off`, `charge_on`/`charge_off`) so the runner forces
 *  that direction. Before this, both "start climate" and "stop climate"
 *  collapsed to the bare `"climate"` toggle and the runner flipped against the
 *  snapshot -- so "stop the climate" while climate was already off would
 *  *start* it on the real car. The bare toggle tokens ("climate"/"charge") are
 *  reserved for genuinely ambiguous phrasing (none currently produced here). */
/**
 * The temperature asked for, in Fahrenheit, or null if the query names none.
 *
 * Superlatives resolve to the ends of [CLIMATE_TEMP_RANGE_F], which is the
 * honest reading of "coldest" -- it means the coldest the car will accept, not
 * absolute zero, and the range is the same one the climate slider offers.
 *
 * A BARE number is deliberately not a temperature. "Ioniq 5", "Model 3" and
 * "EV6 GT" all put digits in a query that is naming a car, so a number only
 * counts when a preposition introduces it ("at 64", "to 64") or a unit follows
 * it ("64 degrees", "64F"). Getting this wrong would start climate at 5 degrees
 * because the car is called an Ioniq 5.
 */
internal fun parseClimateTemperature(q: String, metric: Boolean): Int? {
    if (RxColdest.containsMatchIn(q)) {
        return CLIMATE_TEMP_RANGE_F.first
    }
    if (RxWarmest.containsMatchIn(q)) {
        return CLIMATE_TEMP_RANGE_F.last
    }
    val m = RxTempAtTo.find(q)
        ?: RxTempDegrees.find(q)
        ?: return null
    val n = m.groupValues[1].toIntOrNull() ?: return null
    val unit = m.groupValues.drop(2).firstOrNull { it.isNotBlank() }
    val f = when {
        unit == "c" -> ambientFahrenheit(n.toDouble())
        unit == "f" -> n
        // No unit given: believe the user's own setting rather than assuming
        // Fahrenheit. "start climate at 20" from someone on metric means 20C.
        metric -> ambientFahrenheit(n.toDouble())
        else -> n
    }
    return f.coerceIn(CLIMATE_TEMP_RANGE_F.first, CLIMATE_TEMP_RANGE_F.last)
}

internal fun parseVehicleCommand(query: String, metric: Boolean = false): ParsedVehicleCommand? {
    val q = query.lowercase()
    // Only meaningful for a climate START, and only when the phrasing is not
    // already asking for smart climate (which computes its own target from the
    // weather -- naming a temperature and asking for smart at once is a
    // contradiction, and smart is the more specific request).
    val temp = parseClimateTemperature(q, metric)
    // degLabel owns the F<->C-and-round rule (this was an inline third copy of it). `temp` is an
    // Int °F from parseClimateTemperature, and fahrenheit = !metric, so the two branches map
    // exactly onto degValue's two branches -- verified against FormatUtils.degValue.
    val tempLabel = temp?.let { degLabel(it.toString(), fahrenheit = !metric) }
    // Defrost implies climate at full heat -- "clear the windscreen" is a
    // request about ice, not about a number, so it picks its own temperature
    // unless the query also named one.
    val wantsDefrost = RxDefrost.containsMatchIn(q)
    return when {
        // Unlock before lock: "unlock" contains "lock".
        RxUnlock.containsMatchIn(q) ->
            ParsedVehicleCommand("unlock", label = "Unlocking")
        RxLock.containsMatchIn(q) ->
            ParsedVehicleCommand("lock", label = "Locking")
        RxSmartClimate.containsMatchIn(q) ->
            ParsedVehicleCommand("climate_on", "smart", "Starting smart climate for")
        // Defrost on its own is a start-climate request, so it is matched
        // before the generic stop/start climate patterns below.
        wantsDefrost && !RxNegation.containsMatchIn(q) -> {
            val f = temp ?: CLIMATE_TEMP_RANGE_F.last
            ParsedVehicleCommand(
                "climate_on",
                TileCommandRunner.TEMP_PREFIX + f + TileCommandRunner.DEFROST_SUFFIX,
                "Defrosting",
            )
        }
        RxClimateOff
            .containsMatchIn(q) -> ParsedVehicleCommand("climate_off", label = "Stopping climate for")
        RxClimateStart.containsMatchIn(q) ->
            if (temp != null) {
                ParsedVehicleCommand(
                    "climate_on",
                    TileCommandRunner.TEMP_PREFIX + temp,
                    "Starting climate at $tempLabel for",
                )
            } else {
                ParsedVehicleCommand("climate_on", "default", "Starting climate for")
            }
        // Bare "heat <car> to 80" / "cool <car> to 65" / "warm <car> to 70" --
        // no start/turn-on prefix, no "up" -- the pattern above requires one
        // of those, so a query that's just the verb plus a target temperature
        // fell through to "not a command" entirely. Requiring temp != null is
        // what keeps this safe: it's the same guard that stops "Ioniq 5" from
        // being read as a temperature (see parseClimateTemperature's own doc),
        // so a bare "heat" with no number attached still isn't a command here
        // either -- it needs a real "to/at N" or "N degrees" alongside it.
        temp != null && RxHeatCoolVerb.containsMatchIn(q) ->
            ParsedVehicleCommand("climate_on", TileCommandRunner.TEMP_PREFIX + temp, "Starting climate at $tempLabel for")
        // Charge LIMIT before charge start/stop: "set the charge limit to 80"
        // contains "charg", and the limit is the more specific request.
        RxChargeLimit
            .containsMatchIn(q) -> {
            val pct = RxPercent.find(q)?.groupValues?.get(1)?.toIntOrNull()
            if (pct != null && pct in CHARGE_LIMIT_RANGE) {
                ParsedVehicleCommand("charge_limit", pct.toString(), "Setting charge limit to $pct% on")
            } else {
                null
            }
        }
        RxFlashLights.containsMatchIn(q) ->
            ParsedVehicleCommand("lights", label = "Flashing lights on")
        RxHorn.containsMatchIn(q) ->
            ParsedVehicleCommand("horn", label = "Sounding horn on")
        RxChargeStop.containsMatchIn(q) ->
            ParsedVehicleCommand("charge_off", label = "Stopping charge for")
        RxChargeStart
            .containsMatchIn(q) -> ParsedVehicleCommand("charge_on", label = "Starting charge for")
        else -> null
    }
}

/** The two shapes the one search element takes. It is never two composables
 *  cross-fading: the same Surface changes size, so it can travel between
 *  screens instead of disappearing on one and appearing on the other. */
/**
 * The three shapes the one search element takes.
 *
 * PILL is the collapsed state on Settings: icon plus the word, at a medium
 * width. Settings used to collapse to the FULL-width bar, which is a text
 * field's footprint with no text field in it -- a lot of bottom edge claimed
 * to say one word. The bar is what it becomes when you touch it.
 */
private enum class SearchForm { BUBBLE, PILL, BAR }

/**
 * THE search element, hoisted to the app root so it is a single object that
 * OUTLIVES the screen transition.
 *
 * It used to be instantiated separately by Settings, the garage and the cover,
 * which meant three of them: leaving Settings destroyed one and created
 * another, so the only transition available was a cross-fade between two
 * different things that happened to look alike. Hosted here, above the
 * screen-switching AnimatedContent, there is exactly one -- so moving between
 * the garage and Settings genuinely morphs it, a circle in the corner growing
 * into the bar across the bottom and back, because it is the same Surface the
 * whole way.
 *
 * The shape follows the screen, not the user:
 *  - Settings: the full bar, centred at the bottom. This is a screen you came
 *    to in order to find something.
 *  - Garage: a small circle in the bottom-right corner, icon only. Search is
 *    available, not advertised; the car is what you came to look at.
 *  - Cover: the same circle, smaller, and DRAGGABLE -- on a one-inch screen
 *    anything parked in a corner is covering something, and which corner is
 *    free depends on the tile you are on, so the answer has to be the user's.
 *  - Open, anywhere: the bar, because at that point it is a text field.
 */
@Composable
internal fun SearchLayer(
    vm: AppViewModel,
    state: UiState,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
    onSettings: Boolean,
    compact: Boolean,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var focused by rememberSaveable { mutableStateOf(false) }
    val open = focused || query.isNotEmpty()
    // Where the user has dragged the bubble, in dp from the top-left. NaN =
    // never dragged, so it rests in its default corner. Saved, because having
    // to re-park it after every rotation would make dragging it pointless.
    // mutableStateOf, not mutableFloatStateOf: rememberSaveable needs a Saver
    // for whatever it is handed, and the boxed-Float one is the guaranteed
    // path. This changes twice a gesture, not twice a frame -- the boxing is
    // not worth a runtime "no Saver found" on some Compose version.
    var dragX by rememberSaveable { mutableStateOf(Float.NaN) }
    var dragY by rememberSaveable { mutableStateOf(Float.NaN) }
    // True only between finger-down and finger-up on the bubble. The position
    // animation is BYPASSED while it is true -- see the spec choice below.
    var dragging by remember { mutableStateOf(false) }
    // The app's own tuned vocabulary (Haptics.kt), not the generic platform
    // LocalHapticFeedback this used to reach for -- search is one of the most
    // prominent, most-animated surfaces in the app (it morphs shape, position AND
    // opens a whole panel) and was the one major interaction still running on a
    // borrowed system-default feel instead of the app's own composed effects
    // everything else (pebbles, toggles, buttons) uses.
    val haptics = LocalHaptics.current

    BackHandler(enabled = open) { query = ""; focused = false }
    // A click when it opens, a tick when it closes -- the same asymmetry
    // PebbleShell's own header tap uses (expand is the weightier confirm; collapse
    // is the lighter step), so search reads as one more instance of the app's
    // single expand/collapse feel rather than its own separate gesture language.
    // The morph is the visual half of a state change the user just caused; the
    // haptic is the half they feel, and it lands on the frame the shape starts
    // moving rather than when it arrives, so the gesture reads as having been
    // received immediately.
    // Armed only after the first composition: a LaunchedEffect keyed on a
    // boolean also runs when that boolean is simply born false, so without
    // this the app buzzes once on launch, for nothing happening.
    var hapticArmed by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (hapticArmed) {
            if (open) haptics?.click() else haptics?.tick()
        }
        hapticArmed = true
    }
    // Drop any stale AI answer once the box is cleared, and forget the last
    // submission with it -- otherwise reopening search shows the previous
    // question's answer under an empty field.
    LaunchedEffect(query.isBlank()) {
        if (query.isBlank()) { vm.clearAiReply(); submitted = "" }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bottomInset = WindowInsets.navigationBars.union(WindowInsets.ime)
            .asPaddingValues().calculateBottomPadding()
        // With the keyboard up, the panel and the bar together are competing
        // for the sliver of screen that is left -- on a phone that is a couple
        // of hundred dp, not the 360 the panel would otherwise take. Measure
        // what is actually free rather than guessing: the panel gets what
        // remains above the bar, minus a margin so it never looks wedged.
        val keyboardUp = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 80.dp
        val edge = if (compact) 8.dp else 16.dp
        // On the cover, a camera band beside the island (see coverCutoutBand)
        // is real, unoccluded space with nothing else fixed in it once the
        // name has taken its share -- a better home for search than a corner
        // it would otherwise float over. Docked there it's a fixed CoverBand-
        // SearchDock circle, matching the reservation CompactGarage's own
        // band Row leaves for it; undocked (no band, or not compact) it's the
        // free-floating, draggable circle this always was. "Fixed when the
        // space is there, floating when it isn't."
        val band = if (compact) coverCutoutBand() else null
        val bubble = if (band != null) CoverBandSearchDock else if (compact) 40.dp else 52.dp
        val barW = minOf(maxWidth - edge * 2, 640.dp)
        val barH = 52.dp
        val freeAbovePill = (maxHeight - bottomInset - barH - edge * 2 - 24.dp).coerceAtLeast(96.dp)
        // A medium pill: wide enough for the icon and the word with room
        // around them, and nowhere near the bar's span.
        val pillW = minOf(if (compact) 132.dp else 168.dp, barW)
        val form = when {
            open -> SearchForm.BAR
            onSettings -> SearchForm.PILL
            else -> SearchForm.BUBBLE
        }

        // Resting corner for the bubble, and the drag bounds that keep it on
        // screen no matter where it was left.
        val minX = edge
        val maxX = (maxWidth - bubble - edge).coerceAtLeast(edge)
        val minY = edge
        val maxY = (maxHeight - bubble - edge - bottomInset).coerceAtLeast(edge)
        val restX = maxX
        val restY = maxY
        // This Box sits inside BlooApp's own `.padding(padding)` (the
        // Scaffold's safeDrawing content padding), while coverCutoutBand()
        // reports coordinates in the WINDOW's own space -- the same gap
        // CompactGarage's band Row doesn't have to close because it draws
        // full-bleed, ignoring that padding entirely (see its own comment).
        // Subtracting the same inset back out here is what puts this bubble
        // in the same coordinate space as that Row, so the two agree on
        // where the band actually is.
        val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
        val insetLeftDp = safeDrawing.calculateLeftPadding(LocalLayoutDirection.current).value
        val insetTopDp = safeDrawing.calculateTopPadding().value
        // Flush against whichever end of the band the camera touches --
        // exactly the edge CompactGarage's own Arrangement groups its
        // dockSpacer reservation against -- and vertically centred in it.
        val dockedX = band?.let {
            (if (it.nearCameraAtEnd) it.xDp + it.widthDp - 6f - CoverBandSearchDock.value
            else it.xDp + 6f) - insetLeftDp
        }
        val dockedY = band?.let { (it.yDp + (it.heightDp - CoverBandSearchDock.value) / 2f) - insetTopDp }
        // Restore the user's last-parked spot from durable storage, once -- if this
        // composition doesn't already have one in memory. rememberSaveable's dragX/
        // dragY survive a LIVE session (rotation, a mode switch while the process
        // stays alive), but not a killed-and-restarted process, which is routine for
        // a flip phone that's been closed a while: reported as "drag it, leave flip
        // mode, come back -- it's not where I put it", which a purely in-memory Saver
        // can't fix on its own. Stored and restored as FRACTIONS of the drag range
        // (see SettingsStore's own doc), not raw dp, so this stays correct even if
        // minX/maxX/minY/maxY come out slightly different than when it was saved.
        // Plain arithmetic rather than androidx.compose.ui.unit.lerp -- two
        // multiplies don't need an import.
        LaunchedEffect(Unit) {
            if (dragX.isNaN()) {
                vm.searchBubblePosition()?.let { (xFrac, yFrac) ->
                    dragX = (minX + (maxX - minX) * xFrac).value
                    dragY = (minY + (maxY - minY) * yFrac).value
                }
            }
        }
        // dragX/dragY are ONLY consulted in compact (flip) mode. They are one
        // shared pair of saved floats -- dragging is only possible in flip
        // mode, but before this gate the normal-mode bubble read the exact
        // same state, so once the flip bubble had ever been moved, switching
        // to the normal phone screen showed it wherever flip mode had left it
        // instead of the fixed bottom-right corner. Normal mode now always
        // rests at restX/restY, full stop -- what dragX/dragY hold is purely
        // flip mode's memory, and normal mode has no memory of its own by
        // design (there's nowhere on that screen to remember: one fixed spot
        // is the whole point).
        // Docked beats dragged beats resting: a band, when there's one to dock
        // into, always wins over wherever the bubble was last left -- fixed
        // when the space is there, floating (and rememberable) only when it
        // isn't.
        val bubbleX = when {
            dockedX != null -> dockedX.dp
            compact && !dragX.isNaN() -> dragX.dp.coerceIn(minX, maxX)
            else -> restX
        }
        val bubbleY = when {
            dockedY != null -> dockedY.dp
            compact && !dragY.isNaN() -> dragY.dp.coerceIn(minY, maxY)
            else -> restY
        }

        val targetW = when (form) {
            SearchForm.BAR -> barW
            SearchForm.PILL -> pillW
            SearchForm.BUBBLE -> bubble
        }
        val targetH = if (form == SearchForm.BUBBLE) bubble else barH
        val targetX = if (form == SearchForm.BUBBLE) bubbleX else (maxWidth - targetW) / 2
        val targetY = if (form == SearchForm.BUBBLE) bubbleY else maxHeight - barH - edge - bottomInset

        // Two springs, not one, and this is the part that makes the resize
        // read well: SIZE gets a little overshoot so the pill arrives with
        // some give, while POSITION stays critically damped. Sharing one
        // bouncy spring meant the whole element slid past its resting place
        // and came back -- the wobble that made growing into the bar look
        // loose rather than deliberate. Width and height still share their
        // spring, so the shape stays coherent while it changes.
        //
        // PebbleBounceDamping/PebbleBounceStiffness, not this element's own
        // hand-tuned numbers -- this used to carry its own separately-picked
        // damping ratios (0.62 here, 0.72 below), close to but not actually the
        // same values the pebble bounce settled on, which is exactly what "not
        // standard across every surface" was pointing at: two controls that both
        // bounce but by measurably different amounts read as two different
        // design systems, not one. Reusing the literal shared tokens is what
        // makes this ACTUALLY the same spring, not just a similar-looking one.
        val sizeSpec = spring<Dp>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        // A spring is right for the morph and WRONG for a drag: routing the
        // finger's position through one meant the bubble trailed behind the
        // touch for the whole gesture and then coasted past it on release --
        // it felt like dragging something on elastic, not like moving it.
        // While the finger is down the position snaps (1:1 with touch); the
        // moment it lifts, the spring is back to carry the settle.
        val posSpec = if (dragging) snap<Dp>() else {
            // Bouncier than a critically-damped snap, and deliberately so: this is
            // what plays when the bubble snaps to an edge on release, and a snap
            // with no overshoot reads as the value being SET, not as the bubble
            // landing somewhere. A bit of give past the edge and back is what
            // makes it read as physical contact -- it bounced off the edge --
            // rather than a UI correcting a number. Same shared bounce spring as
            // `sizeSpec` above, for the same "one system" reason.
            spring<Dp>(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        }
        // key(compact) so entering or leaving flip mode RESTARTS these
        // animations at their new target rather than animating to it. The cover
        // screen's resting corner is a different point in a differently-sized
        // box, so without this the ball crawled across the whole screen from
        // wherever the other layout had left it -- a long slide that had
        // nothing to do with anything the user just did. Restarted, it is
        // already home when the mode appears, and the entrance spring inside
        // SearchPill is what you see instead.
        val w = key(compact) { animateDpAsState(targetW, sizeSpec, label = "searchW").value }
        val h = key(compact) { animateDpAsState(targetH, sizeSpec, label = "searchH").value }
        val x = key(compact) { animateDpAsState(targetX, posSpec, label = "searchX").value }
        val y = key(compact) { animateDpAsState(targetY, posSpec, label = "searchY").value }

        // Dismiss scrim. Below the pill in this Box, so it never eats its taps. Same
        // effects spec collapseEnter/collapseExit use for every pebble's own fade,
        // not its own hand-picked tween durations (180ms/140ms) -- one fade curve
        // for "something is fading" across the whole app, not a slightly different
        // one wherever a fade happened to get added separately.
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { query = ""; focused = false },
            )
        }

        // Results / suggestions, anchored to the bottom rather than to the
        // pill: the pill is always at the bottom centre while open, so this
        // never has to chase a bubble around the screen.
        AnimatedVisibility(
            visible = open,
            enter = collapseEnter(Alignment.Bottom),
            exit = collapseExit(Alignment.Bottom),
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = barH + edge + bottomInset + 10.dp),
        ) {
            val panelShape = RoundedCornerShape(if (compact) 20.dp else 28.dp)
            Surface(
                shape = panelShape,
                // Shared default, not its own 0.98 -- see glassContainerAlpha's own
                // doc for why every frosted surface takes the one value now.
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = glassContainerAlpha()),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.width(barW).dropShadow(panelShape, blurRadius = 16.dp, offsetY = 6.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = minOf(if (compact) 180.dp else 360.dp, freeAbovePill))
                        .verticalScroll(rememberScrollState())
                        .padding(if (compact) 10.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                ) {
                    if (query.isNotBlank()) {
                        // Fewer results while the keyboard is up. This is what
                        // the new ranking buys: cutting to the top few is only
                        // honest when the top few really are the best ones, and
                        // a scrollable list you cannot see the bottom of is
                        // worse than a short list you can.
                        SettingsSearchResults(
                            query, submitted, vm, state, appearance, notif,
                            // The cover screen with the keyboard up is the
                            // hard case: a ~260dp square, most of it keyboard.
                            // Two results that are fully visible beat six you
                            // have to scroll blind through.
                            limit = when {
                                compact && keyboardUp -> 2
                                keyboardUp -> 4
                                else -> Int.MAX_VALUE
                            },
                        )
                    } else {
                        SearchSuggestions(state, compact = compact || keyboardUp) { picked ->
                            query = picked
                            submitted = picked
                        }
                    }
                }
            }
        }

        SearchPill(
            query = query,
            focused = focused,
            form = form,
            width = w,
            height = h,
            compact = compact,
            onQueryChange = { query = it },
            onFocusChange = { focused = it },
            onSubmit = { submitted = query },
            // Dragging only exists for the bubble. A bar spans the screen --
            // there is nowhere to move it to -- and while it is a text field
            // a drag would fight the keyboard and the panel above it.
            // Docked into a camera band, there is nowhere to drag it TO --
            // the whole point of the fixed spot is that it's the one place
            // guaranteed not to cover something else.
            onDrag = if (form == SearchForm.BUBBLE && compact && band == null) {
                { dx, dy ->
                    dragX = ((if (dragX.isNaN()) bubbleX else dragX.dp) + dx).coerceIn(minX, maxX).value
                    dragY = ((if (dragY.isNaN()) bubbleY else dragY.dp) + dy).coerceIn(minY, maxY).value
                }
            } else null,
            onDragStart = { dragging = true },
            onDragEnd = {
                // Snaps to the NEAREST of the four edges, not a fixed corner
                // and not wherever the finger happened to be. A version of
                // this once sprang back to one specific corner on every
                // release, which defeated the entire point of dragging it --
                // this is the middle ground: it can be parked anywhere ALONG
                // an edge, freely, but it never rests out in the open middle
                // of the screen, where a floating circle covers whatever a
                // one-inch display was showing there with nothing to be
                // gained by it sitting exactly there rather than at the edge
                // just past it.
                //
                // Only the axis PERPENDICULAR to the chosen edge moves; the
                // position along that edge is whatever the drag ended at, so
                // "somewhere along the left edge, a third of the way down" is
                // a real resting place this remembers, not just the four
                // corners.
                if (!dragX.isNaN() && !dragY.isNaN()) {
                    val cx = dragX.dp
                    val cy = dragY.dp
                    val toLeft = cx - minX
                    val toRight = maxX - cx
                    val toTop = cy - minY
                    val toBottom = maxY - cy
                    // minOf has no 4-argument overload in the stdlib -- nested
                    // 2-argument calls, not a 4-element list, to avoid an
                    // allocation on every drag release for four numbers.
                    val nearest = minOf(minOf(toLeft, toRight), minOf(toTop, toBottom))
                    when {
                        nearest == toLeft -> dragX = minX.value
                        nearest == toRight -> dragX = maxX.value
                        nearest == toTop -> dragY = minY.value
                        else -> dragY = maxY.value
                    }
                    // Persisted durably (see SettingsStore.setSearchBubblePosition),
                    // not just left in rememberSaveable -- once per gesture release,
                    // not per drag frame. Guarded against a zero-width/height range
                    // (a degenerate tiny screen) rather than dividing by it.
                    val spanX = (maxX - minX).value
                    val spanY = (maxY - minY).value
                    if (spanX > 0f && spanY > 0f) {
                        vm.setSearchBubblePosition(
                            ((dragX - minX.value) / spanX).coerceIn(0f, 1f),
                            ((dragY - minY.value) / spanY).coerceIn(0f, 1f),
                        )
                    }
                }
                dragging = false
                // click(), not the generic platform feedback this used to fire --
                // matches the edge-snap spring's own "bounced off the edge" physical
                // read (see posSpec's doc above) with a real confirm-weight landing
                // instead of a borrowed system default.
                haptics?.click()
            },
            modifier = Modifier.align(Alignment.TopStart).offset(x = x, y = y),
        )
    }
}

/**
 * The pill itself: one Surface at whatever [width]/[height] [SearchLayer] has
 * animated it to, with a glow behind it and its content chosen by [form].
 *
 * Sized by the caller rather than by a width FRACTION of its own parent, which
 * is what it used to do. A fraction cannot express "a circle in that corner"
 * and "a bar across the bottom" as the same element, and it is the sameness
 * that makes the screen-to-screen morph possible at all.
 */
@Composable
private fun SearchPill(
    query: String,
    focused: Boolean,
    form: SearchForm,
    width: Dp,
    height: Dp,
    compact: Boolean,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onDrag: ((Dp, Dp) -> Unit)?,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val expanded = focused || query.isNotEmpty()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Focus is DRIVEN by `focused`, both ways. It used to only ever be
    // requested, never released: dismissing the bar -- scrim tap, close
    // button, back -- collapsed the pill and left the keyboard standing over
    // it, because nothing ever told the field to let go. Clearing focus here
    // is safe in a way that listening for blur is not (see the note on the
    // text field's modifier): this reacts to the state that OWNS the bar,
    // not to a transient focus event that arrives before the field is ready.
    LaunchedEffect(focused) {
        if (focused) {
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Springs in on first appearance -- and because SearchLayer keys the
    // animations on the layout mode, "first appearance" includes arriving on
    // the cover screen. The ball lands in its corner rather than sliding to it.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    // DampingRatioMediumBouncy (0.5) on BOTH of these compounded badly: they multiply into
    // the same scaleX/scaleY below, so a press landing anywhere near the entrance pop (or
    // just the two overshoots being visually close together on a small, frequently-tapped
    // control) read as noticeably more bounce than either spring alone would suggest --
    // reported as "overly bouncy," and this is the same lesson the pebble bounce work
    // already paid for: 0.5 reads as a lot on a real device, repeatedly, not occasionally.
    // Entrance keeps some spring (it plays once, arriving) but now on the literal shared
    // PebbleBounceDamping/Stiffness tokens rather than its own separately-tuned numbers --
    // an "arrival" pop is the same kind of event a pebble opening is, so it gets the exact
    // same spring, not a lookalike. Press feedback drops nearly all bounce (PebbleCloseDamping,
    // i.e. no overshoot) and stays on its own fast StiffnessHigh -- a frequent, repeated
    // micro-interaction is exactly where extra bounce stops feeling playful and starts
    // feeling like noise, and it no longer compounds with the entrance spring above it.
    val entrance by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.55f,
        animationSpec = spring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness),
        label = "searchEntrance",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = PebbleCloseDamping, stiffness = Spring.StiffnessHigh),
        label = "searchPress",
    )
    // No ambient glow. This used to carry a travelling-hotspot bloom that
    // swept the pill's rim and breathed continuously the entire time the
    // search element was on screen, plus a separate fade-and-shrink once it
    // went idle. Reported as bad-looking and distracting, and it earned
    // that: a permanent light show on a control that is visible almost all
    // the time competes with everything the user is actually looking at. The
    // pill's affordance is now just its filled shape and border below --
    // legible without needing to move to prove it's there.
    Box(
        modifier.size(width, height).graphicsLayer {
            val k = pressScale * entrance
            scaleX = k
            scaleY = k
        },
    ) {
        Surface(
            onClick = { if (!expanded) onFocusChange(true) },
            shape = RoundedCornerShape(50),
            // Nearly opaque on the cover screen. A translucent 40dp circle over
            // a photo hero picks up whatever is behind it and stops looking
            // like a control at all; at this size there is not enough of it for
            // the glass effect to read as glass.
            //
            // The Settings-screen collapsed pill (form == PILL && !expanded) used to
            // get its own opaque tonal container (secondaryContainer/onSecondaryContainer,
            // no border) instead of this standard frosted fill -- reported as an
            // inconsistent, different-looking search control on that one screen.
            // The original reason for the override no longer applies: it was fixed
            // there because a near-transparent surface with a hairline and
            // onSurfaceVariant text read as "indistinguishable from a disabled
            // control", but the standard treatment below already reads onSurface
            // (not the dimmer onSurfaceVariant) with a visible gradient-lit border --
            // the same legibility fix, just applied consistently instead of as a
            // one-screen special case.
            color = scheme.surfaceContainerHighest.copy(
                alpha = if (compact) glassContainerAlpha(0.97f) else glassContainerAlpha(),
            ),
            contentColor = scheme.onSurface,
            tonalElevation = if (expanded) 10.dp else 6.dp,
            border = BorderStroke(
                if (expanded) 1.5.dp else 1.dp,
                // Static. This is an argument to Surface, i.e. COMPOSITION
                // scope -- it used to multiply in glowPulse, which meant every
                // 33ms tick of the glow clock recomposed this whole composable
                // and the text field inside it, thirty times a second, for the
                // entire time the app was open. The moving light belongs in
                // the drawBehind gradients above, where a tick invalidates
                // draw and nothing else; the rim just needs to be lit.
                Brush.verticalGradient(
                    listOf(
                        scheme.primary.copy(alpha = if (expanded) 0.65f else 0.4f),
                        scheme.primary.copy(alpha = 0.05f),
                    ),
                ),
            ),
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxSize()
                // The cover screen gets neither the drop shadow nor the glass
                // rim. Both are tuned for a 52dp pill or a full-width bar; on a
                // 40dp circle they are a soft dark halo and a bright outline
                // stacked on a shape barely wider than the two of them, which
                // is what made this read as a smudge rather than a button. The
                // border below plus the glow behind carry it there.
                .then(if (compact) Modifier else Modifier.dropShadow(RoundedCornerShape(50)))
                .then(if (compact) Modifier else Modifier.appGlassRim(RoundedCornerShape(50)))
                .then(
                    if (onDrag != null) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                            ) { change, amount ->
                                change.consume()
                                with(density) { onDrag(amount.x.toDp(), amount.y.toDp()) }
                            }
                        }
                    } else Modifier,
                ),
        ) {
            AnimatedContent(
                targetState = form to expanded,
                transitionSpec = {
                    // Cross-fade only, fast, and NOT delayed.
                    //
                    // This used to scale the content in from 0.9 after a 90ms
                    // hold. Both were wrong for what is happening around it:
                    // the container is already springing to a new size, so a
                    // second scale on the content inside it is two different
                    // rates of growth fighting over the same pixels, and the
                    // delay meant the shape arrived somewhere before its
                    // contents admitted they were moving. The old content
                    // leaving quickly and the new one arriving over the top,
                    // while the shape carries the motion, is the whole effect.
                    fadeIn(tween(140)) togetherWith fadeOut(tween(90))
                },
                label = "searchContentMorph",
            ) { (shape, isOpen) ->
                when {
                    isOpen -> Row(
                        Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(if (compact) 18.dp else 20.dp))
                        Spacer(Modifier.width(if (compact) 6.dp else 10.dp))
                        Box(Modifier.weight(1f)) {
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                                cursorBrush = SolidColor(scheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                // Submitting puts the keyboard away. The answer
                                // to what you just asked appears in the panel
                                // directly above this bar, which is exactly
                                // where the keyboard was covering.
                                keyboardActions = KeyboardActions(onSearch = { onSubmit(); keyboard?.hide() }),
                                // No auto-collapse on blur: onFocusChanged fires
                                // with isFocused = false the instant this field
                                // composes, before the requestFocus above lands,
                                // and that false positive used to close the bar in
                                // the same beat it opened.
                                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                decorationBox = { inner ->
                                    if (query.isEmpty()) {
                                        Text(
                                            if (compact) "Search" else "Search settings & car data",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = scheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    inner()
                                },
                            )
                        }
                        MorphIconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onFocusChange(false) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = if (query.isNotEmpty()) "Clear" else "Close",
                            )
                        }
                    }
                    // Closed PILL (Settings): the icon plus the word, centred.
                    shape == SearchForm.PILL -> Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Search", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    }
                    // Closed BUBBLE: the glyph alone. contentDescription is on
                    // the icon rather than the label, since there isn't one.
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = "Search",
                            // 18dp inside a 40dp circle left a ring of empty
                            // surface wider than the glyph; the button read as
                            // a blob with something small in it.
                            modifier = Modifier.size(if (compact) 21.dp else 22.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Example queries shown while the search bar is focused but empty --
 * without these there's no way to discover that search answers data
 * questions ("what's my odometer") and runs commands ("lock my car"), not
 * just finds settings by name.
 */
@Composable
private fun SearchSuggestions(state: UiState, compact: Boolean = false, onPick: (String) -> Unit) {
    // Plain Surface chips, not MorphButton -- so unlike most taps in this app they
    // don't get a click() automatically and needed it wired in by hand.
    val haptics = LocalHaptics.current
    val carName = state.vehicles.firstOrNull()?.name
    // Short forms when the room is short -- on a cover screen with the keyboard
    // up, "odometer for Ioniq 5" wraps to two lines and pushes the next chip
    // off the panel, so a hint about what you can ask costs you the ability to
    // see what else you can ask. The long forms teach the syntax; the short
    // ones just have to fit and still work when tapped.
    val examples = buildList {
        if (compact) {
            add("odometer")
            add("battery")
            add("lock my car")
            add("haptics")
        } else {
            add("odometer" + (carName?.let { " for $it" } ?: ""))
            add("battery level")
            add("lock" + (carName?.let { " my $it" } ?: " my car"))
            // Teaches the temperature syntax, which is not guessable: nothing
            // else on screen says a command can carry a value.
            add("start climate at the coldest temperature" + (carName?.let { " on $it" } ?: ""))
            add("haptic feedback")
            if (state.vehicles.any { state.hasBattery(it) }) add("start smart climate")
        }
    }
    Text(
        "Try asking",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        // Floating directly over the aurora/scrolling content behind it with
        // nothing opaque underneath -- onSurfaceVariant (a deliberately muted
        // secondary-text tone) read as low-contrast there. Full-strength
        // onSurface instead.
        color = MaterialTheme.colorScheme.onSurface,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Same staggered pop as the search RESULT cards (staggeredResultVisible), reused
        // as-is: this list is just as much a "search UI" element as the results below it,
        // and giving one a cascade while the other snaps in flat is exactly the kind of
        // per-surface inconsistency that was reported.
        val examplesKey = examples.joinToString("|")
        examples.forEachIndexed { i, example ->
            PopVisible(visible = staggeredResultVisible(examplesKey, i)) {
                Surface(
                    onClick = { haptics?.click(); onPick(example) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.dropShadow(RoundedCornerShape(50), blurRadius = 8.dp, offsetY = 3.dp),
                ) {
                    Box {
                        Text(
                            example,
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
/**
 * Every CONSTANT search/command pattern, compiled once at class init instead of per call.
 *
 * `Regex(...)` parses its pattern and builds a matcher every time it is CONSTRUCTED, and all
 * of these were constructed inside the functions using them. Two distinct costs:
 *
 *  - The token splitter ran inside SettingsSearchResults, a composable whose `query`
 *    parameter changes on every KEYSTROKE -- a regex compiled per character typed, on the
 *    input path, with the keyboard up. That is the one a user can feel.
 *  - The command-parser vocabulary was ~17 compilations per parse, on every submitted query.
 *
 * File scope rather than `remember`: the patterns are constant, so that is their correct
 * lifetime, and a `remember` would still recompile once per composition that mis-keyed it.
 * Any pattern built from a runtime value (a vehicle's own name) is left where it is, since
 * it genuinely cannot be constant.
 *
 * Generated by extracting the literals from this file rather than by retyping them: doing it
 * by hand through two layers of escaping mangled the degree sign and several `\b` anchors.
 */
private val RxHexColorDraft = Regex("#[0-9A-Fa-f]{0,6}")
private val RxColdest = Regex("coldest|as cold as|max(imum)? (cold|cool)|lowest temp|full (cold|cool)")
private val RxWarmest = Regex("warmest|hottest|as (warm|hot) as|max(imum)? (heat|warm)|highest temp|full heat")
private val RxTempAtTo = Regex("\\b(?:at|to)\\s*(\\d{2,3})\\s*°?\\s*([fc])?\\b")
private val RxTempDegrees = Regex("\\b(\\d{2,3})\\s*°?\\s*(?:degrees?\\b|([fc])\\b)")
private val RxDefrost = Regex("defrost|defog|demist|clear (the )?(wind(screen|shield)|glass|ice)|de-ice")
private val RxUnlock = Regex("\\bunlock\\b|\\bopen (the |my )?(car|doors?)\\b|let me in")
private val RxLock = Regex("\\block\\b|secure (the |my )?car|lock (it|up)\\b")
private val RxSmartClimate = Regex("smart climate|smart (ac|a/c|heat|clim)")
private val RxNegation = Regex("stop|turn off|cancel")
private val RxClimateOff = Regex("(stop|turn off|cancel|kill|end) (the )?(climate|ac|a/c|heat(er)?|aircon|air con|cooling|warming)")
// Was constructed fresh inline at its one call site, unlike every other
// pattern in this block -- missed when the rest were hoisted (see the
// doc above this block for why that hoist mattered: once per submitted
// query, not once per frame, but still worth not re-parsing).
private val RxClimateStart = Regex(
    "(start|turn on|run|fire up|kick on) (the )?(climate|ac|a/c|heat(er)?|aircon|air con)" +
        "|pre.?(heat|cool|condition)|warm (it|the car|my car) up|cool (it|the car|my car) down" +
        "|(warm|cool) up (the|my) car",
)
private val RxChargeLimit = Regex("(charge|charging) (limit|target)|limit .*(charge|charging)|charge to \\d{2,3}")
private val RxPercent = Regex("\\b(\\d{2,3})\\s*%?")
private val RxFlashLights = Regex("(flash|blink) (the )?(lights|headlights)|lights? (on|flash)")
private val RxHorn = Regex("\\bhonk\\b|sound (the )?horn|\\bhorn\\b|beep (the )?(car|horn)|find (my|the) car")
private val RxChargeStop = Regex("(stop|turn off|cancel|halt|end) (the )?charg|unplug")
private val RxChargeStart = Regex("(start|begin|turn on|resume) (the )?charg|charge (it|the car|my car)( now)?|top (it )?up")
// Bare verb, no "start"/"turn on"/"up" needed -- paired with `temp != null` at
// its one call site, which is what stops it from firing on every unrelated
// sentence that happens to contain "heat" or "cool".
private val RxHeatCoolVerb = Regex("\\b(heat|cool|warm)\\b")
private val RxSearchTokens = Regex("[^a-z0-9%]+")


/**
 * Live search over both app settings and per-car data/fields. Tokenises the
 * query (dropping filler words like "for"/"the"), so "odometer for xyz" finds
 * the odometer of the car named xyz, and "plate" lists every car's plate.
 */
@Composable
private fun SettingsSearchResults(
    query: String,
    submittedQuery: String,
    vm: AppViewModel,
    state: UiState,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
    /** Show at most this many, best first. See the call site: with a keyboard
     *  up there is no room for a long list, and ranking is what makes taking
     *  the top few the right answer rather than an arbitrary one. */
    limit: Int = Int.MAX_VALUE,
) {
    val tokens = query.lowercase().split(RxSearchTokens)
        .filter { it.isNotBlank() && it !in SearchStopwords }
    // Same source the main Settings screen uses for its own Security card gate.
    val canBio = remember { vm.canUseBiometrics() }

    val entries = ArrayList<SearchEntry>()
    fun add(title: String, keywords: String, content: @Composable () -> Unit) {
        entries.add(SearchEntry(title, "$title $keywords".lowercase(), content))
    }

    // --- App-wide settings ---
    // The dynamic half of the index: every plain toggle in ToggleSettings
    // renders itself here with no per-toggle code -- see that list's own doc
    // comment for what does and doesn't fit this shape.
    ToggleSettings.forEach { spec ->
        if (!spec.visible(state)) return@forEach
        add(spec.title, spec.keywords) {
            ToggleRow(spec.label, spec.checked(appearance, notif, state)) { spec.onToggle(vm, it) }
        }
    }
    // Two of Security's own controls, missing from here entirely -- "fingerprint"
    // and "lock" are exactly the words someone would type for this. Reproduces
    // the real card's logic verbatim (down to the same confirm-to-disable
    // biometric prompt, not a bare toggle) rather than a simplified stand-in,
    // since a security control is the one place a search shortcut skipping a
    // step the real row enforces would be a genuine regression, not just a
    // visual inconsistency.
    if (canBio) {
        val bioContext = LocalContext.current
        add("Require fingerprint to open", "biometric lock security app unlock") {
            SettingsSegmentedRow(
                label = "Require fingerprint to open",
                options = listOf(
                    SegmentOption("off", "Off", null),
                    SegmentOption("on", "On", null),
                ),
                selectedKey = if (appearance.biometricLock) "on" else "off",
                onSelect = { key ->
                    if (key == "on") {
                        bioContext.findFragmentActivity()?.let { activity ->
                            showBiometricPrompt(
                                activity = activity,
                                title = "Enable fingerprint lock",
                                subtitle = "Confirm to require it on launch",
                                onSuccess = { vm.setBiometricLock(true) },
                                onError = { },
                            )
                        }
                    } else {
                        val activity = bioContext.findFragmentActivity()
                        if (activity == null) {
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
        }
        if (appearance.biometricLock) {
            add("Lock timing", "lock the app grace period timeout re-lock security") {
                SettingsSegmentedRow(
                    label = "Lock the app",
                    options = LockTiming.entries.map { t -> SegmentOption(t.name, t.label, null) },
                    selectedKey = appearance.lockTiming.name,
                    onSelect = { key -> runCatching { vm.setLockTiming(LockTiming.valueOf(key)) } },
                )
            }
        }
    }
    add("Text & layout scale", "display size zoom bigger") {
        var uiScaleDraft by remember(appearance.uiScale) { mutableFloatStateOf(appearance.uiScale) }
        StepRow("Scale", "${(uiScaleDraft * 100).roundToInt()}%")
        AnimatedSlider(
            value = uiScaleDraft,
            onValueChange = { uiScaleDraft = it },
            valueRange = 0.8f..1.3f,
            steps = 4,
            onValueSettled = { uiScaleDraft = (it * 10).roundToInt() / 10f; vm.setUiScaleSoon(uiScaleDraft) },
        )
    }
    add("Colour vibrancy", "color saturation vivid material you monochrome best buy tv") {
        // Deferred-commit, same as the main Appearance card's slider — see there.
        VibrancySlider(appearance, vm)
    }
    add("Search on the car screen", "search bubble car screen cover home garage ask command") {
        ToggleRow("Search on the car screen", appearance.showSearch) { vm.setShowSearch(it) }
    }
    // Unlike every other entry here, this ONE still needs a slice of the
    // Display card's own cross-navigation -- search is reachable from the
    // garage screen too, not just from inside Settings, so toggling this on
    // from a search result was a real, easy-to-hit way to trip the exact
    // "kicked out instead of moved to the right place" bug the Display card's
    // own toggle was fixed for: the preference flipped with no visible
    // navigation, and only the NEXT time Settings was reached did it turn up
    // somewhere unexpected. Turning ON always follows it there now, safe to
    // call from any screen: closeSettings(landOnSettingsPage = true) is a
    // harmless no-op navigation if already on the garage, and the pager's own
    // authoritative landing effect (Screens.kt) snaps onto the new Settings
    // slot regardless of whether this composition is fresh or already
    // mounted.
    //
    // Turning OFF used to be treated as a plain preference change, on the
    // theory that the pager's own drift-correction (LaunchedEffect(totalBlocks)
    // in GarageScreen) would "land back on a car gracefully once the slot
    // disappears" -- that's exactly the bug: reached from a search result
    // while genuinely parked on the embedded slot, nothing ever calls
    // openSettings(), so that drift-correction effect finds state.screen
    // still == Screen.Garage and snaps the pager to whatever car currentIndex
    // resolves to instead of navigating anywhere -- the "turning this off
    // takes you back to the first car, not the real Settings screen" bug.
    // The main Settings card's own copy of this toggle (see ToggleRow above
    // in this same file) already gets this right by checking `embedded`; this
    // one has no such parameter, so it checks state.onSettingsPageSlot
    // instead -- true exactly when the pager is currently settled on the
    // embedded slot, the same signal GarageScreen itself uses.
    add("Settings as a swipeable page", "gear button pager swipe car screen navigation") {
        ToggleRow("Settings as a swipeable page", appearance.settingsAsPage) { turningOn ->
            vm.setSettingsAsPage(turningOn)
            if (turningOn) {
                vm.closeSettings(landOnSettingsPage = true)
            } else if (state.onSettingsPageSlot) {
                vm.openSettings()
            }
        }
    }
    add("Units", "unit system metric imperial temperature distance speed miles km") {
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
    add("Font", "typeface atkinson hyperlegible google sans accessibility low vision") {
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
    add("Display mode", "theme light dark amoled system appearance") {
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
    }
    // --- Per-car ---
    state.vehicles.forEach { v ->
        val st = state.statusFor(v)
        val plate = state.licensePlates[v.vin] ?: ""
        add("License plate · ${v.name}", "plate licence registration ${v.name} $plate") {
            OutlinedTextField(
                value = plate,
                onValueChange = { vm.setLicensePlate(v.vin, it) },
                label = { Text("License plate") },
                singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
            )
        }
        parseOdometerMiles(v.odometer)?.let { odoInt ->
            add("Odometer · ${v.name}", "odometer mileage miles ${v.name}") { StatusRow("Odometer", formatDistance(odoInt, appearance.unitSystem == "metric")) }
        }
        add("VIN · ${v.name}", "vin identification ${v.name} ${v.vin}") {
            SelectionContainer { StatusRow("VIN", v.vin) }
        }
        // VehicleStatus.rangeMiFor -- already imported, and its body was copied here
        // character-for-character (battery-range-else-null ?: dte, then toInt). One source of
        // truth for "what range do we show for this powertrain".
        st?.rangeMiFor(state.hasBattery(v))?.let { r ->
            add("Range · ${v.name}", "range distance dte empty ${v.name}") { StatusRow("Range", formatDistance(r, appearance.unitSystem == "metric")) }
        }
        if (state.hasBattery(v)) {
            st?.evStatus?.batteryStatus?.let { b ->
                add("Battery · ${v.name}", "battery charge soc percent ${v.name}") { StatusRow("Battery", "$b%") }
            }
            // Current-plug target if plugged in, else the configured AC home limit --
            // now the shared EvStatus.displayChargeLimit(), this call site's own fallback
            // generalized so every surface agrees rather than re-deriving it.
            val limit = st?.evStatus?.displayChargeLimit()
            limit?.let { l -> add("Charge limit · ${v.name}", "charge limit target ${v.name}") { StatusRow("Charge limit", "$l%") } }
        } else {
            st?.fuelLevel?.let { f ->
                add("Fuel · ${v.name}", "fuel gas tank percent ${v.name}") { StatusRow("Fuel", "$f%") }
            }
        }
        rememberRelativeTime(state.fetchedAt(v))?.let { rel ->
            add("Last refreshed · ${v.name}", "updated refreshed time ${v.name}") { StatusRow("Last refreshed", rel) }
        }
        (state.placeNames[v.vin] ?: state.locations[v.vin]?.coordString(4))?.let { loc ->
            add("Location · ${v.name}", "location where place gps ${v.name}") { StatusRow("Location", loc) }
        }
        add("Powertrain · ${v.name}", "powertrain ev gas hybrid phev ${v.name}") {
            PowertrainPicker(current = state.powertrainOf(v)) { pt -> vm.setPowertrain(v, pt) }
        }
        // Same gate CarSettingsCard's own group uses -- nothing to confirm for
        // a vehicle where this picker would have no effect either way.
        if (v.platformOverridable) {
            add("Head-unit generation · ${v.name}", "gen5w ccnc platform generation trips ${v.name}") {
                PlatformPicker(current = state.platformOf(v)) { pt -> vm.setPlatform(v, pt) }
            }
        }
        add("Last service · ${v.name}", "service maintenance mileage ${v.name}") {
            MilesField(state.lastServiceMiles[v.vin], "Last service (mi)", Modifier.fillMaxWidth()) {
                vm.setLastServiceMiles(v.vin, it)
            }
        }
        // The interval, which had no search entry while "Last service" above did. The two
        // are only meaningful TOGETHER -- the service pebble's whole output is
        // `last + interval` -- so search let you set one half of a sum and hid the other,
        // leaving a "next due" figure that could not be corrected from here. Both fields
        // sit side by side in the per-car section; only the index had one of them.
        add("Service interval · ${v.name}", "service interval maintenance mileage due ${v.name}") {
            MilesField(state.serviceIntervalMiles[v.vin], "Interval (mi)", Modifier.fillMaxWidth()) {
                vm.setServiceIntervalMiles(v.vin, it)
            }
        }
        // The dynamic half of the per-car index: every seat heat/cool flag,
        // the steering wheel, and every hideable dashboard section for THIS
        // car, from VehicleToggleSettings -- no per-car, per-toggle code.
        VehicleToggleSettings.forEach { spec ->
            if (!spec.visible(v, state)) return@forEach
            add(spec.title(v), spec.keywords(v)) {
                ToggleRow(spec.label, spec.checked(v, state)) { spec.onToggle(vm, v, it) }
            }
        }
    }

    // Matches render FIRST (top of this composable's output), the AI answer
    // LAST -- this composable is placed above the floating search bar, so the
    // resulting stack top-to-bottom is [suggested results] [AI tile]
    // [search bar], matching the requested reading order bottom-up.
    // Ranked, not filtered. The fuzzy pass is a FALLBACK, only reached when the
    // strict one found nothing -- so a real match is never outranked by a
    // one-typo guess, and the cost of scanning every word of every entry is
    // only paid on a query that was going to show "no matches" otherwise.
    val results = if (tokens.isEmpty()) {
        entries
    } else {
        val strict = entries.mapNotNull { e -> searchScore(tokens, e, fuzzy = false)?.let { e to it } }
        val scored = strict.ifEmpty {
            entries.mapNotNull { e -> searchScore(tokens, e, fuzzy = true)?.let { e to it } }
        }
        scored.sortedByDescending { it.second }.map { it.first }
    }.let { if (it.size > limit) it.take(limit) else it }
    // Floating above busy/aurora content needs real separation -- a plain
    // default Card blends into whatever's behind it. Elevated container +
    // actual shadow (not just tonal elevation) so results clearly pop.
    val resultCardShape = RoundedCornerShape(16.dp)
    val resultCardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    // These float over busy/aurora content the same way the search bar and
    // "Try asking" panel above them do, but were left on plain tonal-
    // elevation Cards -- the one inconsistency in an otherwise unified
    // floating-chrome look within this exact panel.
    val resultCardModifier = Modifier.fillMaxWidth().dropShadow(resultCardShape, blurRadius = 10.dp, offsetY = 3.dp).frostedRim(resultCardShape)
    if (results.isEmpty()) {
        Card(resultCardModifier, shape = resultCardShape, colors = resultCardColors) {
            Text(
                "No matches for “$query”",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        // Restarts the stagger whenever the actual SET of results changes -- not on every
        // keystroke, which would re-pop a list that hasn't actually moved just because the
        // user is still typing the same word. Titles joined is cheap and exactly captures
        // "did the ranked list change," which is the only thing that should trigger this.
        val resultsKey = results.joinToString("|") { it.title }
        results.forEachIndexed { i, e ->
            PopVisible(visible = staggeredResultVisible(resultsKey, i)) {
                Card(resultCardModifier, shape = resultCardShape, colors = resultCardColors) {
                    Row(Modifier.padding(16.dp)) {
                        // A small icon badge per result, the same "leading circle" language
                        // the update pebble and settings hero stats use -- these cards used
                        // to open straight on bold text with nothing to distinguish a
                        // toggle-able setting from an informational readout at a glance.
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            e.content()
                        }
                    }
                }
            }
        }
    }

    // A recognised command ("lock my Ioniq", "start smart climate", "stop
    // charging") actually runs -- reuses TileCommandRunner, the same
    // execution path the Quick Settings tiles use, so this isn't a separate,
    // untested way of sending vehicle commands. If the query doesn't name a
    // specific car, this falls back to a single car (unambiguous) or asks
    // the user to be more specific (multiple cars, none named).
    //
    // Gated on submittedQuery, NOT the live query -- this actually sends a
    // command to the car, so it must only run once the user has deliberately
    // submitted (Enter/search key, or a suggestion tap), never mid-typing off
    // a debounce timer. Typing "lock my car" used to run the lock the moment
    // the debounce elapsed, whether or not that's what the user meant to do.
    val metricUnits = appearance.unitSystem == "metric"
    val command = remember(submittedQuery, metricUnits) {
        if (submittedQuery.isBlank()) null else parseVehicleCommand(submittedQuery, metricUnits)
    }
    if (command != null) {
        val ctx = LocalContext.current
        // Whole-word, longest-match car resolution -- NOT a bare substring test.
        // A plain `name in query` lets "Ioniq" match inside "lock my Ioniq 5",
        // so a command meant for the "Ioniq 5" would be sent to the "Ioniq"
        // (list-order-first). Instead require the name to appear as a bounded
        // token sequence, and when several names match prefer the longest. If
        // several still match at that longest length the query is genuinely
        // ambiguous, so refuse to dispatch and ask which car (targetVehicle
        // stays null → the "Which car?" branch below).
        val q = submittedQuery.lowercase()
        val nameMatches = state.vehicles.filter { v ->
            v.name.isNotBlank() &&
                Regex("\\b" + Regex.escape(v.name.lowercase()) + "\\b").containsMatchIn(q)
        }
        val longestMatchLen = nameMatches.maxOfOrNull { it.name.length }
        val namedVehicle = nameMatches.filter { it.name.length == longestMatchLen }.singleOrNull()
        // Only fall back to "the one car" when NO name matched at all; if a name
        // matched but was ambiguous, do not silently pick a car.
        val targetVehicle = namedVehicle ?: if (nameMatches.isEmpty()) state.vehicles.singleOrNull() else null
        var actionResult by remember(submittedQuery) { mutableStateOf<String?>(null) }
        var actionRunning by remember(submittedQuery) { mutableStateOf(false) }
        LaunchedEffect(submittedQuery) {
            if (targetVehicle != null) {
                actionRunning = true
                val result = runCatching { TileCommandRunner.run(ctx, targetVehicle.vin, command.cmd, command.climateTarget) }.getOrNull()
                actionResult = result?.message ?: "Command failed"
                actionRunning = false
                vm.refreshStatus(targetVehicle)
            }
        }
        Card(
            resultCardModifier,
            shape = resultCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Action", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    when {
                        targetVehicle == null -> {
                            val example = state.vehicles.firstOrNull()?.name ?: "car"
                            "Which car? Mention its name, e.g. “${command.label} my $example”."
                        }
                        actionRunning -> "${command.label} ${targetVehicle.name}…"
                        actionResult != null -> actionResult!!
                        else -> "${command.label} ${targetVehicle.name}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    // Free-form command, via the AI, when the deterministic parser did not
    // recognise the phrasing. Data questions already go through askAi below --
    // this is the other half: making the car DO something described in words
    // the parser has no pattern for.
    //
    // It asks before it acts, and that is deliberate rather than timid. The
    // parser runs its commands immediately because a pattern it matched is a
    // phrasing someone wrote down on purpose; a model's reading of an
    // unanticipated sentence is a guess, and the cost of a wrong guess here is
    // a car unlocked on a street somewhere. One tap is a small price for the
    // difference between "the app did what I said" and "the app did what a
    // model thought I said". aiResolveCommand has already thrown out anything
    // that is not a real action on a real car of yours, so what this offers is
    // always executable -- the question is only whether it is what you meant.
    if (command == null && state.aiEnabled && submittedQuery.isNotBlank()) {
        val ctx = LocalContext.current
        var proposal by remember(submittedQuery) { mutableStateOf<Pair<String, String>?>(null) }
        var thinking by remember(submittedQuery) { mutableStateOf(true) }
        var ran by remember(submittedQuery) { mutableStateOf<String?>(null) }
        var running by remember(submittedQuery) { mutableStateOf(false) }
        LaunchedEffect(submittedQuery) {
            proposal = vm.aiResolveCommand(submittedQuery)
            thinking = false
        }
        val p = proposal
        if (p != null) {
            val car = state.vehicles.firstOrNull { it.vin == p.second }
            Card(
                resultCardModifier,
                shape = resultCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Did you mean?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        ran ?: "${aiCommandLabel(p.first)} ${car?.name ?: "your car"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (ran == null && car != null) {
                        val scope = rememberCoroutineScope()
                        // MorphTextButton, not a bare Button: this was the one plain
                        // Material button left in the app, so it was the only standard
                        // button that neither morphed on press nor fired the click
                        // haptic every other button gives. Its label also flips
                        // "Run it" -> "Working…", which is exactly the content-width
                        // spring MorphButton exists to animate.
                        //
                        // primary/onPrimary passed explicitly because they are what
                        // Material's Button defaulted to here. MorphTextButton's own
                        // default is the calmer buttonContainer(), and this is the
                        // card's primary action -- the conversion should change the
                        // FEEL, not quietly demote the emphasis.
                        MorphTextButton(
                            text = if (running) "Working…" else "Run it",
                            onClick = {
                                running = true
                                scope.launch {
                                    val r = runCatching {
                                        TileCommandRunner.run(ctx, car.vin, p.first, "default")
                                    }.getOrNull()
                                    ran = r?.message ?: "Command failed"
                                    running = false
                                    vm.refreshStatus(car)
                                }
                            },
                            enabled = !running,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }

    // On-device AI reply (when enabled): answer the question in natural
    // language -- a fallback/complement for questions with no structured
    // match above, or a plain-language gloss when there is one.
    //
    // Gated on submittedQuery, not the live query -- this fires a real AI
    // request (network/compute cost, and it used to visibly show "Thinking…"
    // while the user was still mid-word), so it must wait for a deliberate
    // submit rather than firing on every keystroke's debounce.
    if (state.aiEnabled) {
        LaunchedEffect(submittedQuery) {
            if (submittedQuery.isNotBlank()) {
                vm.askAi(submittedQuery)
            } else {
                vm.clearAiReply()
            }
        }
        val thinking = "search" in state.aiBusy
        val reply = state.aiSearchReply
        AnimatedVisibility(
            visible = thinking || reply != null,
            enter = collapseEnter(Alignment.Bottom),
            exit = collapseExit(Alignment.Bottom),
        ) {
            Card(
                resultCardModifier,
                shape = resultCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI answer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    if (reply != null) {
                        Text(reply, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private val TileActions = listOf(
    Triple("doors", "Lock / unlock", Icons.Filled.Lock),
    Triple("climate", "Climate", Icons.Filled.Thermostat),
    Triple("charge", "Charge", Icons.Filled.Bolt),
    Triple("open", "Open", Icons.Filled.DirectionsCar),
)

/** Label for a tile action key (falls back to the key). */
private fun tileActionLabel(cmd: String): String =
    TileActions.firstOrNull { it.first == cmd }?.second ?: cmd

/** One option in a [MorphSegmented] control; re-exported from :uicommon. */
typealias SegmentOption = com.bloo.uicommon.SegmentOption

/**
 * A full-width segmented selector built from the app's button vocabulary: a
 * tonal track whose active segment fills with the primary accent and morphs to a
 * rounded-square, the rest staying pill-calm. Thin wrapper over the shared
 * :uicommon [com.bloo.uicommon.MorphSegmented], supplying the phone's Material 3
 * colours, label typography and haptics.
 */
@Composable
fun MorphSegmented(
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    trackHeight: Dp? = null,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    com.bloo.uicommon.MorphSegmented(
        options = options,
        selectedKey = selectedKey,
        onSelect = onSelect,
        containerColor = containerColor ?: buttonContainer(),
        indicatorColor = scheme.primary,
        selectedTextColor = scheme.onPrimary,
        unselectedTextColor = scheme.onSurfaceVariant,
        textStyle = MaterialTheme.typography.labelLarge,
        onTick = { haptics?.tick() },
        modifier = modifier,
        trackHeight = trackHeight ?: (if (options.any { it.icon != null }) 48.dp else 44.dp),
        // Every other interactive surface (Pebble, floating pills, dialogs)
        // got a hairline rim once real glass blur stopped giving flat
        // surfaces a second depth cue; this control was the one left out.
        borderColor = scheme.outline.copy(alpha = 0.18f),
    )
}


/** A car's powertrain (Gas/Hybrid/PHEV/EV) is a fixed 4-way choice between
 *  equal alternatives — one shared MorphSegmented instead of the MorphChip
 *  row this was duplicated as in both CarSettingsCard and its settings-search
 *  mirror. */
@Composable
internal fun PowertrainPicker(current: com.bloo.bluelink.data.Powertrain, onSelect: (com.bloo.bluelink.data.Powertrain) -> Unit) {
    // An icon per option (Gas/Hybrid/PHEV/EV) instead of text-only segments --
    // a quick visual "shape" for each choice, not just a label to read.
    MorphSegmented(
        options = listOf(
            SegmentOption(com.bloo.bluelink.data.Powertrain.GAS.name, "Gas", Icons.Filled.LocalGasStation),
            SegmentOption(com.bloo.bluelink.data.Powertrain.HYBRID.name, "Hybrid", Icons.Filled.Bolt),
            SegmentOption(com.bloo.bluelink.data.Powertrain.PHEV.name, "PHEV", Icons.Filled.Power),
            SegmentOption(com.bloo.bluelink.data.Powertrain.EV.name, "EV", Icons.Filled.FlashOn),
        ),
        selectedKey = current.name,
        onSelect = { key -> onSelect(com.bloo.bluelink.data.Powertrain.valueOf(key)) },
    )
}

/** A car's confirmed head-unit generation (Gen5W / ccNC) -- the same shape
 *  [PowertrainPicker] is, a fixed choice between equal alternatives on one
 *  [MorphSegmented]. Only ever shown for a vehicle where
 *  [com.bloo.bluelink.data.platformOverridable] is true -- see that
 *  property's own doc for why every other vehicle has nothing here to
 *  confirm. */
@Composable
internal fun PlatformPicker(current: com.bloo.bluelink.data.VehiclePlatform, onSelect: (com.bloo.bluelink.data.VehiclePlatform) -> Unit) {
    MorphSegmented(
        options = listOf(
            SegmentOption(com.bloo.bluelink.data.VehiclePlatform.GEN5W.name, "Gen5W", null),
            SegmentOption(com.bloo.bluelink.data.VehiclePlatform.CCNC.name, "ccNC", null),
        ),
        selectedKey = current.name,
        onSelect = { key -> onSelect(com.bloo.bluelink.data.VehiclePlatform.valueOf(key)) },
    )
}

/**
 * A labelled [MorphSegmented]: a small caption above a full-width segmented
 * control. The expressive replacement for a switch when the setting is really a
 * choice between two equal alternatives (°C/°F, in-app/browser) rather than on/off.
 */
@Composable
fun SettingsSegmentedRow(
    label: String,
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        MorphSegmented(options = options, selectedKey = selectedKey, onSelect = onSelect)
    }
}

/**
 * A [MorphSegmented] with a fixed-width caption to its left and an explanatory line
 * beneath -- the layout the Quick-tiles card uses for its "On tap" and "Refresh"
 * choices. Distinct from [SettingsSegmentedRow], which stacks its label above the
 * control and carries no sub-caption; this one keeps the label inline (a 60dp column,
 * so the two rows' controls line up) and always has a hint below.
 */
@Composable
private fun InlineSegmentedRow(
    label: String,
    caption: String,
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp))
        Spacer(Modifier.width(8.dp))
        MorphSegmented(
            modifier = Modifier.weight(1f),
            options = options,
            selectedKey = selectedKey,
            onSelect = onSelect,
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        caption,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

/** Expressive per-car header: a tonal thumbnail/gradient bubble, name, and tile count. */
@Composable
private fun CarTilesHeader(name: String, img: String?, assignedCount: Int, totalTiles: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CarThumb(img = img, size = 44.dp, cornerRadius = 16.dp, iconSize = 22.dp)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (assignedCount == 0) "No tiles yet" else "$assignedCount of $totalTiles tiles used",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            // A slim capacity bar reads the per-car tile budget at a glance,
            // instead of just a count with no sense of how much room is left.
            Spacer(Modifier.height(6.dp))
            val fill by animateFloatAsState(
                targetValue = if (totalTiles > 0) assignedCount / totalTiles.toFloat() else 0f,
                animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                label = "tileCapacityFill",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fill.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(scheme.primary),
                )
            }
        }
    }
}

/** Shared muted hint line for the tile manager's empty/full states. */
@Composable
private fun TileEmptyHint(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/** Per-car Quick Settings tile manager with live previews. Each car gets its
 *  own tonal card (mirroring CarSettingsCard's per-car container elsewhere in
 *  Settings) so two cars' tile groups never read as one continuous list. */
@Composable
private fun QuickTilesManager(state: UiState, vm: AppViewModel) {
    if (state.vehicles.isEmpty()) {
        Text(
            "Add a car to set up quick tiles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val count = com.bloo.bluelink.data.TILE_COUNT
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        state.vehicles.forEach { car ->
            val assigned = (0 until count).filter { state.tileConfigs.getOrNull(it)?.first == car.vin }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
            ) {
                Column(Modifier.padding(12.dp)) {
                    CarTilesHeader(
                        name = car.name,
                        img = state.imageUrls[car.vin],
                        assignedCount = assigned.size,
                        totalTiles = count,
                    )
                    Spacer(Modifier.height(10.dp))
                    assigned.forEach { idx ->
                        key(idx) { QuickTileCard(idx, car.vin, state, vm) }
                    }
                    val free = (0 until count).firstOrNull { state.tileConfigs.getOrNull(it) == null }
                    when {
                        free != null -> AddTilePill(
                            label = if (assigned.isEmpty()) "Add a quick tile" else "Add another",
                            onClick = { vm.setTileAssignment(free, car.vin, if (assigned.isEmpty()) "doors" else "climate") },
                        )
                        assigned.isEmpty() -> TileEmptyHint("All $count tiles are in use. Remove one to add another.")
                    }
                }
            }
        }
    }
}

/**
 * Prompt the OS to add this configured tile straight to the Quick Settings shade.
 * The system dialog previews [label] + the action's icon before adding, so the
 * tile's name/properties are shown up front. On API < 33 (no add-tile API) we
 * guide the user to add it manually instead.
 */
private fun addTileToQuickSettings(context: Context, index: Int, cmd: String, label: String, unlocked: Boolean) {
    val iconRes = com.bloo.bluelink.tiles.BlooTileService.iconResFor(cmd, unlocked)
    val requested = com.bloo.bluelink.tiles.BlooTileService.requestAddToQuickSettings(
        context, index, label, iconRes,
    ) { result ->
        val msg = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "“$label” added to Quick Settings"
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "“$label” is already in Quick Settings"
            else -> null
        }
        msg?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    if (!requested) {
        Toast.makeText(
            context,
            "Open Quick Settings, tap edit, and add “$label” from the tile list.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private fun tileSummary(cmd: String, climateTarget: String, presetName: String?): String = when (cmd) {
    "doors" -> "Lock / unlock"
    "climate" -> when (climateTarget) {
        "smart" -> "Climate · Smart"
        "default" -> "Climate · Basic"
        else -> "Climate · ${presetName ?: "Preset"}"
    }
    "charge" -> "Start / stop charge"
    "open" -> "Opens the app"
    else -> cmd
}

/**
 * One configured tile, built on the exact same [PebbleShell] every car pebble
 * uses (see [UpdateAvailableTile] for the other non-car-scoped caller) instead
 * of a bespoke static-shape split row -- its collapsed header IS the live
 * preview (icon, name, current state), and its [PebbleHeaderAction] doubles as
 * the actual "Add" button so the common case (configure once, add it) never
 * needs to expand at all. Expanding is only for changing the action, custom
 * name, what climate runs, or removing the tile.
 */
@Composable
private fun QuickTileCard(index: Int, vin: String, state: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    val cmd = state.tileConfigs.getOrNull(index)?.second ?: "doors"
    val customName = state.tileLabels.getOrNull(index)?.takeIf { it.isNotBlank() }
    val presets = state.climatePresets[vin].orEmpty()
    val target = state.tileClimateTargets.getOrNull(index) ?: "default"
    val presetName = presets.firstOrNull { it.id == target }?.name
    var expanded by remember { mutableStateOf(false) }

    // Live car state so the preview matches what the tile will actually show.
    val status = state.vehicles.firstOrNull { it.vin == vin }?.let { state.statusFor(it) }
    val active = when (cmd) {
        "doors" -> status?.doorLock == false
        "climate" -> status?.airCtrlOn == true
        "charge" -> status?.evStatus?.batteryCharge == true
        else -> false
    }
    val liveLabel = when (cmd) {
        "doors" -> status?.doorLock?.let { if (it) "Locked" else "Unlocked" }
        "climate" -> if (status?.airCtrlOn == true) "On" else null
        "charge" -> if (status?.evStatus?.batteryCharge == true) "Charging" else null
        else -> null
    }
    val headerIcon = when (cmd) {
        "doors" -> if (status?.doorLock == false) Icons.Filled.LockOpen else Icons.Filled.Lock
        "climate" -> Icons.Filled.Thermostat
        "charge" -> Icons.Filled.Bolt
        else -> Icons.Filled.DirectionsCar
    }
    val title = if (cmd == "open") "Open" else (customName ?: tileActionLabel(cmd))

    PebbleShell(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        icon = headerIcon,
        title = title,
        vm = vm,
        summary = liveLabel ?: tileSummary(cmd, target, presetName),
        headerAction = PebbleHeaderAction(
            label = "Add",
            icon = Icons.Filled.Add,
            active = active,
            onClick = { addTileToQuickSettings(context, index, cmd, title, unlocked = status?.doorLock == false) },
        ),
    ) {
        Text("Action", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        MorphSegmented(
            options = TileActions.map { (key, label, icon) ->
                SegmentOption(key, if (key == "doors") "Lock" else label, icon)
            },
            selectedKey = cmd,
            onSelect = { key -> vm.setTileAssignment(index, vin, key) },
        )

        if (cmd != "open") {
            Spacer(Modifier.height(10.dp))
            var name by remember(state.tileLabels.getOrNull(index)) {
                mutableStateOf(customName.orEmpty())
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; vm.setTileLabel(index, it) },
                label = { Text("Custom name (optional)") },
                singleLine = true,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (cmd == "climate") {
            Spacer(Modifier.height(10.dp))
            Text("Runs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            MorphSegmented(
                options = buildList {
                    add(SegmentOption("default", "Basic", null))
                    add(SegmentOption("smart", "Smart", null))
                    presets.forEach { p -> add(SegmentOption(p.id, p.name, null)) }
                },
                selectedKey = target,
                onSelect = { vm.setTileClimateTarget(index, it) },
            )
        }

        Spacer(Modifier.height(4.dp))
        MorphButton(
            onClick = { vm.setTileAssignment(index, null, null) },
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Remove tile", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** An outlined "add" pill that morphs like the app's other buttons. */
@Composable
private fun AddTilePill(label: String, onClick: () -> Unit) {
    MorphButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Re-architected onto [PebbleShell] -- the exact expandable-card system every garage
 * pebble uses (bounce-open / calm-close springs, the staggered per-row reveal via
 * [StaggeredRevealColumn], the tonal `surfaceVariant` fill, the morphing pill<->square
 * corner radius) -- instead of the bespoke always-expanded `Card` + `animateContentSize`
 * this used to be. Settings was otherwise the one screen in the app whose collapsible
 * surfaces didn't actually collapse and ran on their own separate motion spec (the
 * now-deleted `AdvancedModeStiffness`/[SoftDamping]) rather than the shared bounce
 * tokens ([PebbleBounceDamping]/[PebbleCloseDamping]) every other expandable surface
 * in the app converged on this session.
 *
 * Every card starts EXPANDED (`rememberSaveable` keyed on its own [title], so a
 * rotation or a process restore puts it back where the user left it) -- nothing that
 * was visible before this change is hidden by default. The only real behaviour change
 * is that a card's header is now a genuine toggle: tapping it collapses the card, the
 * same as every pebble in the garage, instead of Settings being the one screen where
 * every section stayed permanently open whether you cared about it or not.
 *
 * [vm] is threaded through purely because [PebbleShell] requires it in its own
 * signature (unused in that function's body today, kept for signature parity with
 * [Pebble]) -- every call site already has it in scope, since every one of them runs
 * inside `SettingsScreen(vm: AppViewModel)`.
 *
 * [icon] stays nullable at the call-site API (unchanged from before) but PebbleShell's
 * own `icon` parameter is not, so a null here falls back to a generic settings glyph --
 * in practice this only ever fires for the single "Car"/"Cars" card, which had no icon
 * of its own to begin with.
 */
/**
 * The Updates card's tonal status chip, split out of the card body so the
 * spring-animated tint (`updateTint`) only recomposes this small Row/Icon/Text
 * scope on every animation frame, instead of the whole card content lambda
 * (which also hosts the RollingNumber hero stat and outer Surface/Row layout).
 */
@Composable
private fun UpdateStatusChip(state: UiState) {
    val updateTint by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            state.updateAvailable != null -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        // Sprung rather than snapped -- "up to date" turning tertiary the instant
        // a check lands is the one moment this card actually has news, and a cut
        // read as flat next to how much of the rest of the app now springs.
        animationSpec = spring(
            dampingRatio = SoftDamping,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "settingsUpdateTint",
    )
    Row(
        Modifier
            .clip(CircleShape)
            .background(updateTint.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = updateTint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            targetState = when {
                state.updateChecking -> "Checking…"
                state.updateAvailable != null -> "Build ${state.updateAvailable!!.run.runNumber} ready"
                else -> "Up to date"
            },
            label = "settingsUpdateChipText",
        ) { text ->
            Text(text, style = MaterialTheme.typography.labelMedium, color = updateTint, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun SettingsCard(title: String, icon: ImageVector? = null, vm: AppViewModel, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    // heading() on the outer wrapper, not inside PebbleShell's own header Text -- PebbleShell
    // doesn't expose a hook into its title's own Modifier, so this is applied one level up
    // instead. PebbleShell's header row is already ONE merged TalkBack stop (tap-to-toggle),
    // so marking that whole stop as a heading preserves the "headings" navigation shortcut
    // across Settings' ~15 cards that the old Card-based header set up explicitly for.
    Box(
        Modifier
            .fillMaxWidth()
            // The inter-card gap lives HERE, inside this wrapper, and not as the parent
            // Column's `Arrangement.spacedBy`. That is not a style preference, it is the
            // fix for the Advanced->Simple collapse leaving gaps behind: `spacedBy` inserts
            // its spacing between EVERY pair of children regardless of their height, so an
            // advanced-only card shrunk to zero by its own outer AnimatedVisibility still
            // contributed a full gap that `spacedBy` held open on its own schedule and then
            // dropped in one frame once the node left composition -- "extra space between
            // the cards, then it snaps". Living on this wrapper instead means the gap sits
            // INSIDE that same outer AnimatedVisibility and shrinks away with the card.
            .padding(bottom = SettingsCardGap)
            .semantics { heading() },
    ) {
        PebbleShell(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            icon = icon ?: Icons.Filled.Settings,
            title = title,
            vm = vm,
            content = { content() },
        )
    }
}

@Composable
private fun SecretRow(label: String, value: String) {
    var show by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // maxLines=1 so a one-word label ("Password") can't wrap character-by-
        // character ("Pass/word") when the shown value + Show/Hide button take the
        // rest of a large-font row. The label keeps its share; the value ellipsizes.
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (show) value else "•".repeat(value.length.coerceIn(4, 10)),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(10.dp))
        MorphTextButton(if (show) "Hide" else "Show", onClick = { show = !show })
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (selected || pressed) 14.dp else 24.dp,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium),
        label = "choiceCorner",
    )
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else buttonContainer(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "choiceBg",
    )
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = { haptics?.click(); onSelect() },
        shape = RoundedCornerShape(corner),
        color = bg,
        contentColor = fg,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

