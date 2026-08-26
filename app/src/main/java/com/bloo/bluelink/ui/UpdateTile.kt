@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Update surfaces split out of Hero.kt: [UpdateAvailableTile], the standalone
 * update tile pinned below the hero tile, and [UpdateStatusLine], the shared
 * live-status row used by the tile and the Settings Updates card.
 */

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Bitmap
import android.os.Build
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.formatLockoutSeconds
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.PinCrypto
import com.bloo.bluelink.data.PinLockout
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.DEFAULT_AC_CHARGE_LIMIT_PCT
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.DEFAULT_DC_CHARGE_LIMIT_PCT
import com.bloo.bluelink.data.STALE_STATUS_MS
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.EvTrip
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.VehiclePlatform
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.degValue
import com.bloo.bluelink.data.MapTiles
import com.bloo.bluelink.data.smartClimateTargetF
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.MorphButtonCore
import com.bloo.uicommon.connectedGroupShape
import com.bloo.uicommon.splitPillShapes
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherCode
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.supportsHornLights
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.formatSpeed
import com.bloo.bluelink.data.formatSpeedMph
import com.bloo.bluelink.data.formatTripDistance
import com.bloo.bluelink.data.displayChargeLimit
import com.bloo.bluelink.data.targetForCurrentPlug
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.serviceDue
import com.bloo.bluelink.data.nextServiceMiles
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.smartClimateIsCooling
import com.bloo.bluelink.data.CLIMATE_DURATION_RANGE
import com.bloo.bluelink.data.CLIMATE_EXTENDED_DURATION_RANGE
import com.bloo.bluelink.data.climateChunks
import com.bloo.bluelink.data.isPluggedOrCharging
import com.bloo.uicommon.rememberConfirmArm
import com.bloo.uicommon.coldStartIntroPlayed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.UUID
import androidx.compose.ui.graphics.toArgb

/**
 * Bloo isn't on the Play Store, so this is its own update surface: a
 * standalone tile pinned directly below the hero tile whenever the checker
 * has found a newer build, animating in/out instead of interrupting with a
 * popup. Collapse/expand reuses the exact same [PebbleShell] every other
 * pebble is built on (this isn't tied to a car/section, hence PebbleShell
 * directly rather than the [Pebble] wrapper) -- collapsed, the header action
 * button doubles as the primary control and shows live download state
 * (Update / downloading % / Install); expanded, it adds install steps, this
 * build's release notes, and Remind-me/Not-now. Every push publishes a
 * rolling GitHub Release (see android.yml) with the raw phone/watch APKs
 * attached as plain public assets, so the primary action can download the
 * APK directly instead of opening a browser page.
 */
@Composable
internal fun UpdateAvailableTile(state: UiState, vm: AppViewModel, dragHandle: Modifier = Modifier) {
    val info = state.updateAvailable
    // Stays visible during the pending-dismiss (undo) window — only the committed
    // updateTileDismissed truly hides it.
    AnimatedVisibility(
        visible = info != null && !state.updateTileDismissed,
        enter = collapseEnter(Alignment.Bottom),
        exit = collapseExit(Alignment.Bottom),
    ) {
        if (info == null) return@AnimatedVisibility
        val context = LocalContext.current
        // Download progress is collected from its own StateFlow rather than read off UiState,
        // so a per-chunk tick invalidates only this tile's bar/percent, not every pebble on the
        // live pager pages. state.updateDownloading (the boolean that gates the display below)
        // stays on UiState -- it changes twice per download, not hundreds of times.
        val downloadProgress by vm.updateDownloadProgress.collectAsState()
        val hasDirectDownload = info.run.phoneApkUrl != null
        val current = vm.currentBuildNumber
        // Build delta: "build 812 → build 828" when we know the installed build,
        // else just the target. buildLabel is the one canonical version formatter.
        val newLabel = com.bloo.bluelink.data.buildLabel(info.run.runNumber)
        val deltaLabel = if (current > 0) {
            "${com.bloo.bluelink.data.buildLabel(current)} → $newLabel"
        } else {
            newLabel
        }
        val seamless = LocalAppearance.current.seamlessInstallShizuku && state.shizukuAvailable
        // Keyed on the build number so a genuinely different build (see
        // checkForUpdate's sameBuild check) starts collapsed again rather
        // than inheriting whatever expand state an earlier build was left in.
        var expanded by rememberSaveable(info.run.runNumber) { mutableStateOf(false) }
        PebbleShell(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            dragHandle = dragHandle,
            icon = Icons.Filled.SystemUpdate,
            title = "Update available",
            vm = vm,
            summary = info.run.displayTitle?.takeIf { it.isNotBlank() } ?: deltaLabel,
            // No containerColor override -- PebbleShell's own default
            // (surfaceVariant) is what every ordinary pebble uses too
            // (Climate, Charge, Info, ...); this used primaryContainer,
            // which read as a special/different-looking tile instead of
            // fitting in with the rest of the per-car stack. AI's pebble is
            // the one deliberate exception (tertiaryContainer) -- this
            // wasn't meant to be another one.
            headerAction = PebbleHeaderAction(
                label = when {
                    state.updateInstalling -> "Installing…"
                    state.updateDownloading -> downloadProgress?.let { "${(it * 100).roundToInt()}%" } ?: "Downloading…"
                    state.updateApkReady -> if (seamless) "Install now" else "Install"
                    hasDirectDownload -> "Update"
                    else -> "Open"
                },
                icon = if (state.updateApkReady) Icons.Filled.SystemUpdate else Icons.Filled.Download,
                pending = state.updateDownloading || state.updateInstalling,
                enabled = !state.updateInstalling,
                // Same ChargeGreen/white pairing ChargePebble's own headerAction
                // uses for its "charging" active state -- this button used to stay
                // the same neutral, low-contrast default container/text regardless
                // of state, so the one moment this tile has a real "tap this now"
                // call to action (the download finished, install is one tap away)
                // looked identical to every other, less urgent state.
                active = state.updateApkReady,
                activeContainer = ChargeGreen,
                activeContent = Color.White,
                onClick = {
                    when {
                        state.updateApkReady -> vm.installDownloadedUpdate()
                        hasDirectDownload -> vm.downloadUpdateInBackground()
                        else -> {
                            // Dismiss ONLY if the page really opened. Swallowing an
                            // ActivityNotFoundException and dismissing anyway meant a
                            // tap did visibly nothing AND cost the user the tile.
                            val opened = runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.run.htmlUrl)))
                            }.isSuccess
                            if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
                        }
                    }
                },
            ),
        ) {
            val scheme = MaterialTheme.colorScheme
            // ONE state-driven status line (icon + text), replacing the old duplicated
            // delta row + scattered downloading/seamless/installing rows. The build
            // delta already lives in the header summary; here we say what's happening
            // NOW. Ready uses ChargeGreen as a success tick; everything else stays
            // neutral (no charging-green Bolt cross-metaphor).
            //
            // statusKind, not the rendered string, is what drives the AnimatedContent below --
            // it stays "downloading" for the WHOLE download instead of becoming a new string
            // on every percentage tick, which is what used to make "Downloading 45%" slide/fade
            // out and "Downloading 46%" slide/fade in as if they were two different states:
            // the static word was animating right along with the number that actually changed.
            // Only the percent itself is a moving target now (rendered with its own
            // AnimatedValue below), and the sentence around it stays put.
        UpdateStatusLine(deltaLabel, seamless, state, vm)
            // Release notes ("What's new"), capped, with a "Full notes" link to the
            // release page when there's more than we show.
            PopVisible(visible = info.run.releaseNotes != null) {
                val notes = info.run.releaseNotes.orEmpty()
                // Its own tonal card, matching the install-help disclosure just below it --
                // bare text here made the release notes read as an unstyled afterthought
                // next to that block's Surface treatment.
                // fillMaxWidth() required: this Surface is a PopVisible/AnimatedVisibility
                // descendant with a weight()-bearing Text further down ("What's new" row) --
                // without an explicit fillMaxWidth somewhere in this chain, that Text
                // collapses to a near-zero width and wraps one character per line. See
                // SettingsScreen.kt's Weather-card place-name Row for the confirmed case.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.surfaceContainerHighest,
                    contentColor = scheme.onSurface,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // "Full notes" rides in the section header rather than taking a
                        // whole row of its own below the excerpt — one less stacked block
                        // in a tile that already carries status, notes and two dismissals.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "What's new",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            MorphTextButton("Full notes", onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.run.htmlUrl))) }
                            })
                        }
                        Text(
                            notes.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Progressive install help: only in the tap-through (non-seamless) path, and
            // only as an opt-in disclosure — the Play-Protect steps are scaffolding, not
            // something to shout before the user has even tapped Update.
            if (!seamless) {
                var showHelp by rememberSaveable(info.run.runNumber) { mutableStateOf(false) }
                MorphTextButton(if (showHelp) "Hide install help" else "Trouble installing?", onClick = { showHelp = !showHelp })
                PopVisible(visible = showHelp) {
                    // fillMaxWidth() to match the release-notes Surface right above --
                    // without it this panel wrap-contents to its widest line instead of
                    // matching its sibling's full-card width.
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainerHighest,
                        contentColor = scheme.onSurface,
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (hasDirectDownload) "1. Tap \"Update\", then \"Install\" once it downloads" else "1. Download the APK, then open it",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            // Play Protect flags any non-Play-Store APK; without this tip,
                            // "Blocked by Play Protect" reads like a real failure.
                            Text(
                                "2. If you see \"Blocked by Play Protect\", tap \"More details\" → \"Install anyway\"",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            // A body-level action for the two moments the header button alone can
            // be missed -- the idle "Download" state (the header just says
            // "Update") and the ready "Install" state. Same state logic as the
            // header action, spelled out here so the body reads as one complete
            // flow whether or not the pill is spotted.
            if (!state.updateDownloading && !state.updateInstalling) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphButton(
                        onClick = {
                            when {
                                state.updateApkReady -> vm.installDownloadedUpdate()
                                hasDirectDownload -> vm.downloadUpdateInBackground()
                                else -> {
                                    val opened = runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.run.htmlUrl)))
                                    }.isSuccess
                                    if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
                                }
                            }
                        },
                        active = state.updateApkReady,
                        activeContainerColor = ChargeGreen,
                        activeContentColor = Color.White,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            if (state.updateApkReady) Icons.Filled.CheckCircle else Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                state.updateApkReady -> if (seamless) "Install now" else "Install"
                                hasDirectDownload -> "Download now"
                                else -> "Open release page"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (state.updatePendingDismiss) {
                        MorphTextButton("Keep it", onClick = vm::undoDismissUpdate)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            // Dismiss / undo / remind — hierarchy: during the undo window "Keep it" is
            // the recoverable emphasis; otherwise "Remind me" (deferral) is emphasized
            // over the plainer "Not now".
            if (state.updatePendingDismiss) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Dismissing…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    MorphButton(
                        onClick = { vm.undoDismissUpdate() },
                        enabled = !state.updateDownloading,
                        active = true,
                    ) { Text("Keep it", fontWeight = FontWeight.SemiBold) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphButton(
                        onClick = { vm.snoozeUpdate() },
                        modifier = Modifier.weight(1f),
                        enabled = !state.updateDownloading,
                    ) { Text("Remind me") }
                    MorphTextButton("Not now", onClick = vm::dismissUpdate, enabled = !state.updateDownloading, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * The live status half of the update flow: the tonal icon badge, the
 * animated one-line status ("Downloading 46%", "Downloaded · tap Install",
 * "Installing silently via Shizuku…") and the download progress bar.
 *
 * Shared by the update pebble's body and the Settings Updates card, so the
 * two can never drift apart -- this is the same state machine rendered the
 * same way in both places, exactly the "one implementation" rule the
 * Settings card's remake is about.
 *
 * [deltaLabel] ("build 812 → 828") is what the idle state says; [seamless]
 * selects the Shizuku phrasing and the "installs silently" hint.
 */
@Composable
internal fun UpdateStatusLine(
    deltaLabel: String,
    seamless: Boolean,
    state: UiState,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    val downloadProgress by vm.updateDownloadProgress.collectAsState()
    // ONE state-driven status line -- see the tile's own long comment (git
    // history) on why statusKind, not the rendered string, drives the
    // AnimatedContent: the static word must stay put while the percent moves.
    val (statusIcon, statusKind, statusTint) = when {
        state.updateInstalling -> Triple(Icons.Filled.SystemUpdate, "installing", scheme.onSurfaceVariant)
        state.updateDownloading -> Triple(Icons.Filled.Download, "downloading", scheme.onSurfaceVariant)
        state.updateApkReady && seamless -> Triple(Icons.Filled.CheckCircle, "ready_seamless", ChargeGreen)
        state.updateApkReady -> Triple(Icons.Filled.CheckCircle, "ready", ChargeGreen)
        seamless -> Triple(Icons.Filled.Bolt, "seamless", scheme.onSurfaceVariant)
        else -> Triple(Icons.Filled.SystemUpdate, "update", scheme.primary)
    }
    // Sprung, not a snap -- the tint is what carries "this got a step further along"
    // (neutral -> ChargeGreen once the APK is ready), so it gets the same treatment
    // the charge bar's own fill-colour spring does rather than cutting on one frame.
    val animatedStatusTint by androidx.compose.animation.animateColorAsState(
        targetValue = statusTint,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "updateStatusTint",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // A tonal badge behind the icon, not a bare glyph -- the same "icon gets its
        // own coloured circle" weight CoverHero gives every stat it leads with.
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(animatedStatusTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            // AnimatedContent, not a bare Icon swap -- installing -> downloading ->
            // ready is a real sequence of distinct states, and a plain `when` cut
            // between their icons on one frame while everything else on this card is
            // now springing and cascading into place.
            AnimatedContent(
                targetState = statusIcon,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) },
                label = "updateStatusIcon",
            ) { icon ->
                Icon(icon, contentDescription = null, tint = animatedStatusTint, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedContent(
            targetState = statusKind,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically { -it / 3 })
            },
            label = "updateStatusText",
            modifier = Modifier.weight(1f),
        ) { kind ->
            // color resolved explicitly, not left Color.Unspecified -- the
            // "Downloading X%" AnimatedValue below renders through BasicText,
            // which (unlike Text) does NOT fall back to LocalContentColor for
            // an unspecified color; it fell back to Android's own paint
            // default (black) instead.
            val textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = LocalContentColor.current,
            )
            when (kind) {
                "installing" -> Text("Installing silently via Shizuku…", style = textStyle)
                "downloading" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Downloading", style = textStyle)
                    downloadProgress?.let { p ->
                        Text(" ", style = textStyle)
                        // Its own AnimatedValue, not part of this AnimatedContent's own
                        // string -- this is the one piece of the line that legitimately
                        // changes every tick, so it's the only piece that should move.
                        // fontFeatureSettings = "tnum" enables tabular figures so only
                        // the changing digit animates up without the whole number
                        // shifting left/right.
                        com.bloo.uicommon.AnimatedValue(
                            "${(p * 100).roundToInt()}%",
                            style = textStyle.copy(fontFeatureSettings = "tnum"),
                            reduceMotion = LocalReduceMotion.current,
                        )
                    } ?: Text("…", style = textStyle)
                }
                "ready_seamless" -> Text("Downloaded · installs silently via Shizuku", style = textStyle)
                "ready" -> Text("Downloaded · tap Install", style = textStyle)
                "seamless" -> Text("Installs silently via Shizuku, no prompts", style = textStyle)
                else -> Text(deltaLabel, style = textStyle)
            }
        }
    }
    // Live download progress bar. Own PopVisible rather than a bare `if` --
    // this bar arrives and leaves while the card is already open (download
    // starts, download finishes).
    PopVisible(visible = state.updateDownloading) {
        // fillMaxWidth() is required here, not optional: a Row that's a DIRECT child of
        // PopVisible (AnimatedVisibility) and relies on weight() to size a child (the
        // progress bar below) collapses to a near-zero width without it -- AnimatedVisibility
        // measures its content's "natural" size before it has anything but the weighted
        // child to size against, unlike a Row inside an already-bounded parent. See
        // SettingsScreen.kt's Weather-card place-name Row for the same bug, confirmed by
        // screenshot (text wrapped one character per line).
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val p = downloadProgress
            Surface(
                modifier = Modifier.weight(1f).height(8.dp),
                shape = CircleShape,
                color = scheme.onSurface.copy(alpha = 0.12f),
            ) {
                if (p != null) {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxSize(), trackColor = Color.Transparent)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxSize(), trackColor = Color.Transparent)
                }
            }
            if (p != null) {
                com.bloo.uicommon.AnimatedValue(
                    "${(p * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = LocalContentColor.current,
                        fontFeatureSettings = "tnum",
                    ),
                    reduceMotion = LocalReduceMotion.current,
                )
            }
        }
    }
}
