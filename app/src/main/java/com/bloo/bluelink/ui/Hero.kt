@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

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
 * The car's hero card: photo/visual on top, [ChargeFuelBar] below. Corner
 * radius eases between 24dp and 40dp (animateDpAsState) when `charging`
 * flips, as a subtle "something is happening" cue distinct from any text or
 * icon change. Fades and slides up 16dp on first composition
 * (`heroAlpha`/`heroOffset`, both [Animatable]s driven once in
 * `LaunchedEffect(Unit)`) so it enters in step with the rest of the
 * per-car stack rather than popping in instantly.
 */
@Composable
internal fun HeroHeader(
    v: Vehicle,
    status: VehicleStatus?,
    imageUrl: String?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    vm: AppViewModel,
    drivingLabel: String? = null,
    dragHandle: Modifier = Modifier,
    height: Dp = 150.dp,
    metric: Boolean = false,
    /** Whether the photo box is showing. Passed IN rather than collected from the
     *  view model here: this composable already has `vm`, but subscribing to state
     *  inside it would recompose the whole hero on every unrelated state change, and
     *  both call sites already hold the UiState they would read it from. */
    photoExpanded: Boolean = true,
) {
    val charging = hasBattery && status?.evStatus?.batteryCharge == true
    // Play the fade/slide-up entrance only ONCE per car per session, gated on the
    // same coldStartIntroPlayed set the pebble stagger uses. Previously this was an
    // unconditional LaunchedEffect(Unit) that replayed on EVERY (re)composition of
    // this hero — including when a swiped-away page is disposed and later recomposed
    // (or, now that the car pager pre-composes a neighbour via
    // beyondViewportPageCount=1, when that neighbour composes off-screen). Replaying
    // the fade on each enter added animation frames on top of the page's compose
    // burst mid-swipe. Once-per-VIN means a page that re-enters snaps straight to
    // rest instead of re-animating.
    val playIntro = remember(v.vin) { coldStartIntroPlayed.add("hero:${v.vin}") }
    val heroAlpha = remember { Animatable(if (playIntro) 0f else 1f) }
    val heroOffset = remember { Animatable(if (playIntro) 16f else 0f) }
    LaunchedEffect(v.vin) {
        if (!playIntro) return@LaunchedEffect
        launch { heroAlpha.animateTo(1f, tween(400)) }
        launch { heroOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    }
    val corner by animateDpAsState(
        targetValue = if (charging) 40.dp else 24.dp,
        animationSpec = spring(
            dampingRatio = SoftDamping,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "heroCorner",
    )
    // On the PHONE, match the EXPANDED pebble corner (PebbleCornerExpanded = 20dp) —
    // the hero reads as an always-expanded card, so it should share the tighter
    // expanded radius, not the rounder collapsed one. The old hardcoded charging 40dp /
    // idle 24dp both mismatched. The COVER keeps its own animated corner (full-height tile).
    val heroShape = RoundedCornerShape(if (LocalForceExpanded.current) corner else PebbleCornerExpanded)
    val heroOutline = LocalAppearance.current
    // On the flip cover this hero is one full-screen tile. Unlike every other
    // pebble it rolls its own Card and never went through PebbleShell, so it never
    // got the cover's fill-height treatment — it wrapped its content and left a
    // dead gradient box (no photo) plus a black void below. When on the cover, fill
    // the tile height, centre the content, and drop the empty photo box entirely.
    val cover = LocalForceExpanded.current
    if (!cover) {
        // On the phone the hero IS a pebble now, built on the same PebbleShell as every
        // other one: header with icon, title, summary and the standard chevron, and a body
        // that collapses with the shared collapseEnter/collapseExit transition.
        //
        // This replaces a bespoke Card with a MorphExpandButton bolted beside the charge
        // bar. That version worked, but it was a card that looked like a pebble and
        // collapsed like a pebble while sharing none of the mechanism -- so it
        // re-implemented the shadow, outline, corner, drag-handle plumbing and toggle
        // placement, and would have drifted from the real pebbles the first time any of
        // those changed.
        //
        // The photo needs no collapse logic of its own any more either: PebbleShell hides
        // the whole body when collapsed, so "no image when collapsed" falls out of the
        // shared component instead of being a rule this function enforces.
        //
        // Derived ONCE, here, and handed to both densities. The collapsed line and the
        // expanded block used to work the percentage, the range and the charging state out
        // separately -- same inputs, two derivations, and therefore two things to keep in
        // step by hand.
        val readout = chargeReadoutOf(status, hasBattery, hasFuel, drivingLabel, metric)
        // 0 collapsed, 1 expanded. The ONE value the readout's morph runs on: type sizes,
        // gaps, paddings and the header's reservation all lerp on it, so they cannot get out
        // of step with each other the way separate transitions did.
        //
        // Critically damped and terminating on a real threshold, for the same reason the
        // discarded bounds spring needed it: this drives a SIZE, and the theme's spatial
        // spring is under-damped by design, so type would overshoot past its target size and
        // spring back. Text that overshoots reads as a wobble, not as liveliness.
        val heroT by animateFloatAsState(
            targetValue = if (photoExpanded) 1f else 0f,
            animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
            label = "heroMorph",
        )

        // The status line's ("Parked"/"Charging...") own fade, on its OWN clock rather than
        // heroT: originally delayed a full 500ms (requested as "half a second longer
        // before it fades in" -- the line was reading as arriving too eagerly, at the
        // same moment the card itself starts opening) and then reported as too long a
        // wait once that shipped, so trimmed to 250ms -- still a real, deliberate beat
        // after the card starts opening rather than simultaneous with it, just not a
        // hang. Delayed only going IN (photoExpanded true); collapsing fades it out
        // immediately, so the card doesn't look like it's still finishing an entrance
        // while it closes. This is a real wall-clock delay (tween + delayMillis), not a
        // fraction of heroT, because heroT is spring-driven with no fixed duration to
        // carve a fraction out of. By the time it starts, the height reveal (still on
        // heroT) has long since finished even at this shorter delay, so there's no
        // repeat of the clip-vs-alpha mismatch fixed just before this -- the slot is
        // already fully sized and the text just fades into it cleanly.
        // durationMillis raised from 200 to 350: reported as not fading in at all after
        // the delay was trimmed, and 200ms is short enough on a real device's frame
        // pacing to read as a snap rather than a fade, especially right after a 250ms
        // wait primes the eye to expect a discrete change. 350ms is closer to what the
        // original 500ms-delay version's own fadeIn spec would have taken to settle,
        // just without the long wait in front of it.
        // ONE reveal curve for the whole expanded readout -- the travelling
        // numbers, the status line and the fuel row all fade in AROUND THE
        // SAME WINDOW (a single smoothstep on heroT, with a soft head start
        // so the card's own open bounce has begun before the content lands),
        // instead of three pieces each skipping in on their own threshold.
        // Same clock = no "one part pops in, the rest follows later" stagger
        // (reported: fade in gracefully, and in lockstep). Pieces that ride
        // the card's photo (numbers/status/fuel) share this alpha; the bar
        // itself stays persistent because it is the "what the card shows you"
        // element, not a detail of it.
        val statusAlpha = run {
            val t = ((heroT - 0.15f) / 0.5f).coerceIn(0f, 1f)
            t * t * (3f - 2f * t)
        }

        // ---- The travelling numbers -------------------------------------------
        //
        // ONE instance of the percentage and range, drawn by the overlay below and
        // positioned by MEASUREMENT rather than arithmetic.
        //
        // The two ends are laid out by the things that already know where they go:
        // the title Row puts the collapsed numbers beside the car name, and the
        // readout puts the expanded ones at the card's lower-left. Both keep doing
        // exactly that -- they simply stop painting and report their position
        // instead. Six earlier attempts computed the collapsed position by hand (a
        // bottom anchor, a derived lift, the measured title width, a type-step
        // ratio) and each landed slightly off, the last of them printing the
        // numbers above the name. A Row places its children correctly by
        // construction; the trick is to read that placement rather than reproduce it.
        //
        // Both anchors report in the CARD's coordinate space, so the overlay's
        // offset is a plain lerp between two points in the same space.
        val cardCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
        // Position AND width: the overlay needs the width to know how far apart to
        // push the percentage and the range. Collapsed that width is the natural
        // content width, so nothing moves; expanded it is the readout's full span,
        // which is what puts the range on the right.
        val collapsedNumbers = remember { mutableStateOf<Rect?>(null) }
        val expandedNumbers = remember { mutableStateOf<Rect?>(null) }
        // Until BOTH ends have been measured there is nothing to interpolate
        // between, so the two anchors paint themselves and the card looks exactly
        // as it did before. That makes the first frame correct rather than blank,
        // and a measurement that never arrives degrade to the old crossfade instead
        // of losing the numbers entirely.
        val hoisted = cardCoords.value != null &&
            collapsedNumbers.value != null && expandedNumbers.value != null
        fun report(into: androidx.compose.runtime.MutableState<Rect?>) =
            { coords: LayoutCoordinates ->
                val card = cardCoords.value
                if (card != null && coords.isAttached) {
                    val origin = card.localPositionOf(coords, Offset.Zero)
                    into.value = Rect(
                        origin,
                        androidx.compose.ui.geometry.Size(
                            coords.size.width.toFloat(),
                            coords.size.height.toFloat(),
                        ),
                    )
                }
            }

        // Reports this title's own real, measured position (and colour) to a
        // VehicleDetailContent ancestor's TitleFlightOverlay, if one is
        // providing it (null everywhere else -- ExpandedCar's own pebble
        // list excludes "summary" entirely, so this only ever applies
        // here). This slot is drawn permanently invisible below -- see
        // TitleFlightOverlay's own doc for why: it's a position anchor only,
        // the actual visible Text lives entirely in that overlay now.
        val heroTitleFlight = LocalHeroTitleFlight.current
        // The ambient flight instance itself changes the moment this page
        // becomes the hoisted/settled one (GarageScreen switches which
        // HeroTitleFlight it hands down) -- but onGloballyPositioned below
        // only fires on an actual RELAYOUT of this node, not merely because
        // the target it reports to changed. If this page's title happened
        // not to move on screen at that exact moment (the ordinary case: it
        // was already laid out as the pre-composed pager neighbour), nothing
        // would ever re-trigger a report to the NEW flight, leaving its
        // reportGeneration stuck and its caller's "ready" gate unresolved --
        // not just for a frame, but indefinitely, until some unrelated
        // relayout (a scroll) happened to occur. Caching the last known
        // coordinates and re-pushing them the instant the flight identity
        // changes closes that gap: the new flight gets a real report in the
        // very same frame it becomes current, with no dependency on layout
        // happening to be dirty at that moment.
        val lastHeroCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
        // Runs SYNCHRONOUSLY, during composition -- NOT inside a
        // LaunchedEffect, which is what this was until a real bug traced it
        // here. A coroutine only starts running after this composition pass
        // COMMITS, which is strictly AFTER TitleFlightOverlay's own
        // synchronous `val docked by flight.docked` read (and its cold-mount
        // `progress.snapTo()` branch, and its `settled`/onSettledChanged
        // computation) have already consumed whatever STALE docked/position
        // state the newly-adopted flight was left holding by whichever page
        // or moment last drove it -- one whole recomposition too late to
        // prevent a visible pop-to-corner/pop-to-hero flash (and, since a
        // stale-true `docked` can make `settled` spuriously true on that
        // same first frame, potentially a spurious re-hoist/re-unhoist
        // oscillation right after). A remembered "last corrected identity"
        // guard -- mirroring TitleFlightOverlay's own `lastDocked` latch
        // just below -- fires this exactly once per real identity change,
        // synchronously, before any sibling composable in this SAME pass
        // (including TitleFlightOverlay) gets a chance to read the flight.
        var lastCorrectedFlight by remember { mutableStateOf<HeroTitleFlight?>(null) }
        if (lastCorrectedFlight !== heroTitleFlight) {
            // onSettled, not onPositioned -- this is the FIRST report the
            // (possibly newly-current) flight gets from this page becoming
            // settled, not a continuous scroll update. The shared hoisted
            // flight is reused across every page switch now (see its own
            // doc), so its hysteresis baseline can be left over from
            // whichever DIFFERENT page was settled before this one -- biasing
            // this page's own first read through the wrong branch of that
            // hysteresis and rendering it docked (or undocked) purely
            // because of where the previous, unrelated page happened to
            // leave the flag. onSettled bypasses that bias for this one
            // report; onPositioned (below) still carries it correctly for
            // this page's OWN later, continuous scroll updates.
            lastHeroCoords.value?.let { heroTitleFlight?.onSettled(it.positionInRoot(), it.size) }
            lastCorrectedFlight = heroTitleFlight
        }
        // Follows the morph rather than switching: the photo fades in over the same
        // t, so the name has to travel from the surface's own colour to the light one
        // the scrim is built for. Snapping at a threshold would flash a white name
        // onto a still-white card for the frames before the photo arrives.
        val heroTitleColorNow = lerp(MaterialTheme.colorScheme.onSurface, HeroOnPhoto, heroT)
        if (heroTitleFlight != null) heroTitleFlight.color = heroTitleColorNow
        // Mirrors PebbleShell's own hero title grow/shrink spring EXACTLY
        // (same damping/stiffness, same collapsed/expanded type-step ratio)
        // -- see that spring's own doc for why these particular numbers.
        // PebbleShell's copy of this spring only ever drives the invisible
        // anchor Text underneath; without a second copy here, the one Text
        // that's actually PAINTED (TitleFlightOverlay's) never grew or
        // shrank with the pebble at all.
        val headerT by animateFloatAsState(
            targetValue = if (photoExpanded) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessVeryLow),
            label = "heroTitleFlightScale",
        )
        val collapsedTitleScale = with(LocalDensity.current) {
            MaterialTheme.typography.titleMedium.fontSize.toPx() /
                MaterialTheme.typography.headlineSmall.fontSize.toPx()
        }
        if (heroTitleFlight != null) {
            heroTitleFlight.titleScale = collapsedTitleScale + (1f - collapsedTitleScale) * headerT
        }
        PebbleShell(
            expanded = photoExpanded,
            onToggle = { vm.togglePebble(v, com.bloo.bluelink.data.HERO_PHOTO_SECTION) },
            icon = Icons.Filled.DirectionsCar,
            title = v.name,
            vm = vm,
            dragHandle = dragHandle,
            titleColor = heroTitleColorNow,
            titleModifier = if (heroTitleFlight != null) {
                // Permanently invisible -- this slot exists to hold the real,
                // measured layout position for TitleFlightOverlay's flying
                // Text to land on; that Text is the only thing that actually
                // PAINTS the name any more. See TitleFlightOverlay's own doc.
                Modifier
                    .onGloballyPositioned {
                        lastHeroCoords.value = it
                        heroTitleFlight.onPositioned(it.positionInRoot(), it.size)
                    }
                    .alpha(0f)
                    // Position anchor only -- see TitleFlightOverlay's matching
                    // measuring-copy comment for why this can't stay in the
                    // accessibility tree: it would announce the car's name a
                    // second time, on top of the one real flying Text.
                    .clearAndSetSemantics {}
            } else {
                Modifier
            },
            // The ONLY pebble that grows its title. Here the title is the car's NAME and the
            // card becomes a photo of that car, so the name scaling up reads as the card taking
            // over. On "Location" or "Diagnostics" it is a heading resizing for no reason.
            growTitleOnExpand = true,
            // No `summary` string. The bar below IS the summary now, and it is the real
            // one -- restating "82% - 241 mi" as header text beside a bar showing the same
            // thing is how the same numbers get rendered twice and then drift, which is a
            // bug I already had to fix on the widget's MEDIUM tiers.
            // The photo is the card's BACKGROUND now, not a body child, so it runs up
            // behind the header row and the title and chevron overlay its top. Collapsing
            // it is the same shared transition as before -- the only change is which layer
            // it lives on.
            background = {
                // The card's own coordinate space, captured once. Both anchors
                // convert into this, so the overlay's lerp is between two points
                // in one space rather than a mix of window and local offsets --
                // which is the way this goes wrong silently, by landing the
                // numbers off the card entirely.
                Spacer(
                    Modifier
                        .matchParentSize()
                        .onGloballyPositioned { cardCoords.value = it },
                )
                // Captured here (composable context) rather than inside the slide
                // transitions' offset lambdas below, which run outside composition.
                val heroPhotoDensity = LocalDensity.current
                AnimatedVisibility(
                    visible = photoExpanded,
                    // The shared collapse spec (fade + the container's own height reveal)
                    // PLUS a slide-and-settle for the photo itself. This used to be a
                    // scaleIn/Out from 92%/94% on the same non-bouncy spec the container's
                    // own height uses -- an 8% scale change finishing at the same rate as
                    // the reveal it rides inside reads as the photo simply FILLING IN as
                    // the card grows, not as an object arriving on its own. Reported as
                    // "pops in" from a real device.
                    //
                    // The entrance spring is deliberately UNDER-damped
                    // (Spring.DampingRatioLowBouncy < 1): it overshoots its target and
                    // settles back, which is what makes this a bounce and not just a
                    // faster ease. The exit stays on the non-bouncy default spec --
                    // a bounce reads as arrival, not departure; overshooting on the
                    // way OUT would look like the photo hesitating before it leaves.
                    // slideInVertically travels a real distance (HeroPhotoSlideDistance)
                    // rather than a subtle scale nudge, so the photo visibly arrives FROM
                    // somewhere instead of blooming in place. Scale rides the same spring
                    // as the slide on each side, so the two read as one physical motion
                    // rather than two differently-timed effects layered on top of each
                    // other.
                    //
                    // Only the hero does this. A pebble body sliding open is content
                    // appearing; a photo is an object, and objects arrive and settle.
                    //
                    // slideInVertically's offset lambda runs outside composition (it's
                    // called by the animation, not composed), so the px distance is
                    // converted with a plain captured Density rather than
                    // LocalDensity.current inside the lambda.
                    enter = collapseEnter() +
                        slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) { with(heroPhotoDensity) { -HeroPhotoSlideDistance.roundToPx() } } +
                        scaleIn(
                            initialScale = 0.85f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ),
                    exit = collapseExit() +
                        slideOutVertically(
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        ) { with(heroPhotoDensity) { -HeroPhotoSlideDistance.roundToPx() } } +
                        scaleOut(
                            targetScale = 0.9f,
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        ),
                ) {
                    HeroPhotoBackdrop(v, imageUrl, height, aspectRatio = 16f / 9f)
                }
                // The expanded readout, at the BOTTOM of the card.
                //
                // A SIBLING of the photo, not a child of it. As a child it inherited the
                // photo's scaleIn/scaleOut settle, so the numbers and the bar zoomed with the
                // image -- wrong for text, which should arrive rather than being flown in.
                // Split, the photo settles as an object and the readout just closes with it.
                //
                // Aligned within the card's own Box rather than placed in the pebble body:
                // the body is top-aligned in its Column, so a bar there sits under the
                // header, and pushing it down would need the Column to fillMaxHeight inside a
                // Box whose own height comes from a sibling -- which in a scrollable parent
                // (maxHeight = Infinity) is exactly how you get a bad measure. Aligning has
                // no such dependency.
                // THE readout. One instance, both states, morphing between them.
                //
                // Bottom-anchored and deliberately NOT wrapped in an AnimatedVisibility,
                // because there is nothing to show or hide any more -- this node exists in
                // both states. That also retires the footprint bug this slot used to have: a
                // fade-only AnimatedVisibility held its full ~142dp for the whole fade and
                // then dropped it in one frame, which was the "hangs at the wrong size, then
                // snaps". A node that never leaves cannot strand a footprint.
                //
                // The TRAVEL is free. The photo above is already animating the card's height,
                // so anchoring here rides that change from the header down to the base of the
                // photo with no bounds animation at all. `heroT` drives only the SIZE morph.
                // That is what three attempts with `sharedBounds` were doing the hard way --
                // see HeroMorphReadout.
                //
                // The paddings lerp, which is what widens the bar: collapsed it stops short
                // of the chevron, expanded it runs the card's full width.
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        // These three insets are DERIVED from the header's own geometry, not
                        // picked. Collapsed, this node has to land exactly in the slot the
                        // header reserved for it, and my first numbers did not -- the
                        // percentage sat on top of the car icon and clipped the title's
                        // descenders, because the readout is positioned against the CARD while
                        // the reserve lives inside the header's TEXT COLUMN. Two coordinate
                        // systems, and I had not made them agree.
                        //
                        // The header (PebbleShell) is: padding(horizontal = 16, vertical = 6),
                        // Icon(20), Spacer(10), then the weighted text column. So:
                        //
                        //  start  16 + 20 + 10 = 46dp -- the text column's left edge, so the
                        //         percentage lines up under the car NAME instead of over the
                        //         icon. Expanded there is no icon to clear, so 16dp.
                        //  end    the chevron is ~48dp inside the row's own 16dp padding, so
                        //         76dp leaves it clear with a small optical gap. This was 64dp,
                        //         which is why the bar ran under the chevron.
                        //  bottom  Derived, not tuned. The readout is bottom-anchored in the
                        //          card's Box, and the header reserves
                        //          collapsedReadoutHeight + HeroReadoutBottomInset for it, so
                        //          the two line up by construction rather than by a pixel budget
                        //          that has to be re-checked whenever the type changes.
                        //
                        //          The comment removed from here did a hand arithmetic proof
                        //          ("title occupies y 6..30 and the reserve y 30..70, this node
                        //          is 40dp tall") against a 40dp reserve. The code beside it
                        //          reserved 4.dp + ChargeBarHeight = 22dp. Whichever was once
                        //          true, they had stopped agreeing, which is exactly the failure
                        //          a derived value removes.
                        .padding(
                            // Clears the car icon, and NOTHING more. Putting the name's width in
                            // here pushed the whole Column across -- including the BAR, which
                            // then started under the percentage instead of spanning the card.
                            // The name-clearing offset belongs to the numbers Row alone; it is
                            // passed to HeroMorphReadout as `numbersStart` below.
                            start = lerp(46.dp, 16.dp, heroT),
                            end = lerp(76.dp, 16.dp, heroT),
                            bottom = lerp(HeroReadoutBottomInset, 16.dp, heroT),
                        ),
                ) {
                    // Same travel as the title: this readout sits ON the photo once the
                    // card is open, and it reads LocalContentColor, so without this the
                    // percentage, range and state line were near-black on a dark image
                    // exactly as the name was. One provider covers all three.
                    CompositionLocalProvider(
                        LocalContentColor provides
                            lerp(MaterialTheme.colorScheme.onSurface, HeroOnPhoto, heroT),
                    ) {
                        HeroMorphReadout(
                            readout,
                            heroT,
                            onNumbersPositioned = report(expandedNumbers),
                            numbersHoisted = hoisted,
                            statusAlpha = statusAlpha,
                            // Collapsed, the numbers start after the name; expanded, they own the
                            // left edge. Only this Row shifts -- the bar underneath does not.
                            // Zero: this copy only ever shows EXPANDED, where it owns the card's
                            // lower-left. The collapsed numbers are the header's, so nothing here has
                            // to be offset past the car name any more -- which also retires the
                            // measured-title-width plumbing that offset needed.
                            numbersStart = 0.dp,
                        )
                    }
                }
                // THE numbers. One instance, travelling between the two anchors --
                // and the travel is a plain lerp because both anchors are points in
                // this same Box's space.
                //
                // A two-phase easing (height drops first, width/x held then released on
                // a curve that overshoots past its target) was tried here and reverted:
                // width is what HeroNumbers uses to size its Row, but the ROW's own text
                // size scales with heroT directly, not with width's easing. Holding width
                // at the narrow collapsed value while heroT (and so the type size) kept
                // advancing meant the range text grew past what its still-small width
                // could fit for part of the transition -- "mi" briefly shrank to "m..."
                // before width caught up and it reflowed back. A plain lerp keeps width
                // and type size moving together in lockstep, which is what avoids that.
                val from = collapsedNumbers.value
                val to = expandedNumbers.value
                if (hoisted && from != null && to != null) {
                    val x = androidx.compose.ui.util.lerp(from.left, to.left, heroT)
                    val y = androidx.compose.ui.util.lerp(from.top, to.top, heroT)
                    val w = androidx.compose.ui.util.lerp(from.width, to.width, heroT)
                    Box(
                        Modifier
                            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                            .graphicsLayer { alpha = statusAlpha },
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides
                                lerp(MaterialTheme.colorScheme.onSurface, HeroOnPhoto, heroT),
                        ) {
                            HeroNumbers(
                                readout, heroT,
                                width = with(LocalDensity.current) { w.toDp() },
                                statusAlpha = statusAlpha,
                            )
                        }
                    }
                }
            },
            // Collapsed: name, percentage and range on ONE row, with the bar directly under
            // it. Two rows reads as a status line with a gauge under it, which is what it is.
            //
            // Collapsed: name, percentage and range on ONE row, with the bar under it.
            //
            // NOT a shared element, and this time the reason is measured rather than
            // guessed. `sharedBounds` requires a `SharedTransitionLayout`, which is a
            // LookaheadScope, so the hero's subtree runs an extra lookahead measure/place
            // pass every time it is placed -- and a pager drag re-places every page on every
            // frame, on all three pages beyondViewportPageCount keeps live. (Verified in the
            // resolved artifact: SharedBoundsNode implements ApproachLayoutModifierNode.)
            //
            // That is exactly why the flip cover's swipe was smooth while the phone's was
            // not: PebbleShell returns through CoverTile BEFORE it ever creates the scope, so
            // the cover path never pays for this at all. A travelling charge bar is not worth
            // the one gesture the user makes most.
            //
            // The original ask -- the percentage and range rendered twice -- stays fixed, and
            // at the level that actually mattered: ONE [ChargeReadout] derivation feeds both
            // densities, so they cannot drift and only one is ever on screen.
            // Both collapsed slots stay NON-NULL and gate with AnimatedVisibility inside.
            // `if (photoExpanded) null else { … }` deletes the node on the frame the pebble
            // opens, so there is nothing left to play an exit -- which is why these two
            // popped in and out with no animation at all after the shared element came out.
            //
            // No shared element here, deliberately. The travel needed a
            // SharedTransitionLayout, which is a LookaheadScope, and that is what cost the
            // car-swipe frames (see 3cc327a). An animated collapse does not need one: these
            // are ordinary enter/exit transitions on the two nodes, which participate in
            // layout exactly once per frame like everything else.
            // ROW 1 of the collapsed card: the percentage and range, on the car name's own line.
            //
            // In the header's own Row rather than positioned by me. Six attempts to compute this
            // inset -- a bottom anchor, a derived lift, the measured title width, the type-step
            // ratio -- each landed slightly off, the last of them printing the numbers ABOVE the
            // name. A Row aligns its children by construction, which is the whole reason this slot
            // exists; its KDoc named the hero as the user while nothing used it.
            //
            // Yes, this means the NUMBERS have two instances (this one and the expanded copy in
            // [HeroMorphReadout]) -- the honest cost of layout-instead-of-arithmetic. The charge
            // BAR is still a single instance, which was the part worth protecting. And the
            // roughness the two-copy version originally had came from both being visible at
            // similar opacity: this one is gone by t = 0.35 and the expanded copy starts appearing
            // there, so they never overlap.
            // Kept alive past 0.35 once hoisted: it is the collapsed ANCHOR then, and
            // an anchor that is removed stops reporting, which would strand the
            // overlay at its last known point.
            titleTrailing = if (heroT > 0.35f && !hoisted) null else {
                {
                    HeroCollapsedNumbers(
                        readout, heroT,
                        onPositioned = report(collapsedNumbers),
                        hoisted = hoisted,
                    )
                }
            },
            summary = null,
            headerContent = {
                // A RESERVATION, not content. The bar itself lives in the one readout at the
                // bottom of the card; this only stops the header's text column from sitting on
                // top of it while the card is short.
                //
                // Derived from the same tokens the readout composes with, not picked: its
                // collapsed height is the pct line (titleMedium) plus the inter-row gap plus
                // the bar. Choosing a number here instead of deriving it is how this slot
                // produced a mismatch every time it was a constant -- the deleted
                // heroReadoutReserve() was exactly that, and the tombstone above says so.
                // TextUnit.toDp() THROWS on an Unspecified or Em value, so this depends on
                // titleMedium keeping an sp lineHeight. It does: expressiveTypography() builds
                // from Typography() and `.copy(fontFamily, fontWeight)` only, so the default
                // 24.sp survives. Checked rather than assumed, because the failure would be a
                // crash in the hero rather than a layout being a few dp out. If a future
                // typography ever sets lineHeight = TextUnit.Unspecified, guard this.
                // The BAR only -- deliberately NOT the numbers row above it.
                //
                // Reserving the readout's whole height pushed it clear of the title and the
                // collapsed pill became THREE rows: name / numbers / bar. It must be two: name
                // and numbers sharing one row, bar underneath. The numbers row is the same
                // height as the title (both titleMedium), so reserving only what sits BELOW it
                // lets the bottom-anchored readout land its numbers on the title's own row.
                val collapsedReadoutHeight = 2.dp + ChargeBarHeight
                // + the readout's own bottom inset. The readout occupies
                // collapsedReadoutHeight of CONTENT and then sits HeroReadoutBottomInset above
                // the card's edge, so reserving only the content left the reservation one gap
                // short and the readout's top edge crossed into the title's row.
                val h = lerp(collapsedReadoutHeight + HeroReadoutBottomInset, 0.dp, heroT)
                // No graphicsLayer: there is nothing here to fade any more. An alpha on an
                // empty Box is a layer allocation per frame for no pixels.
                Spacer(Modifier.fillMaxWidth().height(h))
            },
        ) {
            // Empty by design. Everything the expanded state adds -- the photo and the
            // readout over its lower edge -- is in `background`, because both need to be
            // positioned against the IMAGE rather than stacked under the header.
            Spacer(Modifier.height(0.dp))
        }
        return
    }
    // Unreachable from here down: `cover` (LocalForceExpanded) is true only on the
    // flip cover, and the cover's home page no longer calls HeroHeader at all --
    // CoverMainTile replaced it, including this branch's own photo-as-background
    // treatment (see CoverMainTile's own doc for where that logic lives now). Left
    // as a `return` above rather than restructuring this already-long function to
    // drop the `if`, so the diff that orphaned this branch stays easy to find in
    // history if that's ever in question.
}
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
                Surface(
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
                    Surface(
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

/** The tonal primary→tertiary→secondary gradient used as the fallback fill
 *  behind car photos across the garage/settings surfaces. Callers apply their
 *  own `.alpha(...)` where they want it dimmed -- this returns only the brush. */
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
internal fun carTonalBrush(scheme: ColorScheme): Brush =
    Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary))

/** The clipped square thumbnail used for a car: the set photo if there is one,
 *  else the [carTonalBrush] fallback with a centered car icon. [cornerRadius]
 *  and [iconSize] vary per caller (the settings card vs. the tiles header). */
@Composable
internal fun CarThumb(img: String?, size: Dp, cornerRadius: Dp, iconSize: Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (!img.isNullOrBlank()) {
            AsyncImage(
                model = rememberPhotoModel(img),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(carTonalBrush(scheme)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(iconSize))
            }
        }
    }
}

/** The Coil model for a stored car photo: a [java.io.File] for a locally-cropped
 *  absolute path, or the raw URL string for a pasted one. */
@Composable
internal fun rememberPhotoModel(url: String): Any =
    remember(url) { if (url.startsWith("/")) java.io.File(url) else url }

// collapseEnter / collapseExit -- the app's one collapse spec -- now live in UiTokens.kt,
// with the reasoning that goes with them. 14 call sites in this file still use them.

/**
 * The car photo plus the contrast scrim that makes text on top of it legible. ONE
 * definition, used by the phone hero's expanded background and by the flip cover's tile.
 *
 * Contrast, not decoration. Every element overlaid on the hero -- title, chevron, the whole
 * charge readout -- sits on an arbitrary car photo, and against a light car they all
 * disappear. The widget hit the same problem and solved it with a luminance check on the
 * resolved accent; a scrim is the cheap version and is what the hero does.
 *
 * The gradient covers the FULL height and never reaches transparent. An earlier version
 * scrimmed only the top strip and faded to clear by 45%, on the assumption that only the
 * header row was overlaid -- it is not, the readout is over the image too. Heaviest at the
 * top and bottom because those are the two bands that carry content (title and chevron up
 * top, the charge readout along the bottom); the middle can afford to be clear because
 * nothing sits there, which is what lets the photo still read as a photo.
 *
 * remember-ed: Brush.verticalGradient allocates a stop list, and this sits inside a card
 * that recomposes on every status change.
 *
 * [aspectRatio] null means size by [height] -- the flip cover, whose tile height is given.
 */
@Composable
internal fun HeroPhotoBackdrop(
    v: Vehicle,
    imageUrl: String?,
    height: Dp,
    aspectRatio: Float? = null,
    corner: Dp = PebbleCornerExpanded,
    /** See [HeroVisual.fill] -- the flip cover fills its tile. */
    fill: Boolean = false,
) {
    Box(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        HeroVisual(v, imageUrl, height, corner, aspectRatio = aspectRatio, fill = fill)
        val scrim = remember {
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.55f),
                0.30f to Color.Black.copy(alpha = 0.22f),
                0.62f to Color.Black.copy(alpha = 0.28f),
                1f to Color.Black.copy(alpha = 0.62f),
            )
        }
        Spacer(Modifier.matchParentSize().background(scrim))
    }
}

/** Default = a clean brand gradient. If the user set a photo, show that instead. */
@Composable
internal fun HeroVisual(
    v: Vehicle,
    imageUrl: String?,
    height: Dp,
    corner: Dp = 18.dp,
    /** When set, size by aspect ratio instead of [height] -- 16:9 for the phone hero, so
     *  the image keeps its shape at any screen width instead of being letterboxed or
     *  cropped by a fixed dp height. */
    aspectRatio: Float? = null,
    /** Fill the parent in BOTH axes, ignoring [height] and [aspectRatio] -- the flip cover,
     *  whose tile height is the frame, so cropping to fill it is what a full-screen glance
     *  wants. Requires a bounded parent, which the cover tile is (its Card fills height). */
    fill: Boolean = false,
) {
    val sizeModifier = when {
        fill -> Modifier.fillMaxSize()
        aspectRatio != null -> Modifier.fillMaxWidth().aspectRatio(aspectRatio)
        else -> Modifier.fillMaxWidth().height(height)
    }
    if (imageUrl.isNullOrBlank()) {
        val scheme = MaterialTheme.colorScheme
        Box(
            sizeModifier
                .clip(RoundedCornerShape(corner))
                .background(carTonalBrush(scheme)),
        )
    } else {
        // A locally-cropped photo is an absolute path; a pasted one is a URL.
        val model: Any = rememberPhotoModel(imageUrl)
        // A transparent PNG renders edge-to-edge with no opaque box, so it blends
        // seamlessly into the pebble (fit, not crop, so the whole subject shows).
        val transparent = imageUrl.endsWith(".png", ignoreCase = true)
        // crossfade, so the car photo ARRIVES instead of popping. This is the one hero
        // element that had no animation of any kind: the pebble's collapse animates, the
        // readout's numbers roll, the bar's fill springs -- and then the photo itself
        // appeared between two frames. The map tiles below already did this; the hero,
        // the largest image in the app and the one the eye lands on first, did not.
        //
        // Coil skips the fade for memory-cache hits by design, which is exactly right
        // here: a first load fades in, but scrolling back to an already-decoded photo
        // does not re-fade, so this cannot turn into a flicker on the car pager.
        // Memoized like the map tiles: creating a fresh ImageRequest every recomposition
        // would trigger unnecessary reloads and cause visible flicker/jank.
        val context = LocalContext.current
        val imageRequest = remember(model) {
            ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = v.model,
            contentScale = if (transparent) ContentScale.Fit else ContentScale.Crop,
            modifier = sizeModifier
                .then(if (transparent) Modifier else Modifier.clip(RoundedCornerShape(corner))),
        )
    }
}

/**
 * The battery/fuel percentage readout: headline percent + range, a status
 * line beneath (charging details > driving/parked > plain "Battery"/"Fuel"
 * label, in that priority order), and a gradient progress bar. The bar's
 * fill animates via a spring (`animatedFrac`) rather than snapping to the
 * new percentage, and -- when plugged in -- a small dot marks the
 * charge-limit target percentage on the track so the user can see at a
 * glance how much further it'll charge.
 */
@Composable
internal fun ChargeFuelBar(
    status: VehicleStatus?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    drivingLabel: String? = null,
    metric: Boolean = false,
) {
    // Now literally [HeroMorphReadout] held at its expanded end. There is ONE readout
    // implementation in the app, and every surface that shows this -- the hero on the phone,
    // the flip cover's tile, the EV Charge pebble -- renders that same one.
    //
    // This function had grown a near-duplicate of it: a ChargeStatsBlock with the same Row,
    // the same weighted spacer, the same two RollingNumbers at the same two type steps, then
    // the same fuel row and the same bar. Two implementations of one readout is how the
    // collapsed bar ended up silently dropping the charge-limit marker the expanded one drew,
    // and how the morph pass dropped the fuel icon this file had always had. `t = 1f` is a
    // constant, so nothing here animates -- the morph is inert at its endpoint.
    HeroMorphReadout(chargeReadoutOf(status, hasBattery, hasFuel, drivingLabel, metric), t = 1f)
}

/**
 * Everything the charge/fuel readout says, derived ONCE.
 *
 * The hero renders this readout at two densities — one line in the collapsed header,
 * the full block at the bottom of the expanded card — and until now those were two
 * independent derivations of the same numbers: two answers to "battery percentage or
 * fuel percentage", two copies of the charging > driving > plain priority order for
 * the state line, two charging-colour rules. That is this codebase's recurring class
 * of bug (a rule that exists in one place and is re-typed in another), and here it
 * had already produced a visible one — both copies on screen simultaneously,
 * disagreeing about whether to mention charging.
 *
 * Now both densities render from one of these, and only the LAYOUT differs.
 */

// Colours, sizes and motion specs shared across screens live in UiTokens.kt.

/**
 * The shared floating/card edge: the app's default frosted rim ([frostedRim]).
 * Call sites keep their normal [glassContainerAlpha] frosted fill. The [tint]
 * param is retained for call-site compatibility but is no longer used.
 */
@Composable
internal fun Modifier.appGlassRim(
    shape: Shape,
    @Suppress("UNUSED_PARAMETER") tint: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier = this.frostedRim(shape)
