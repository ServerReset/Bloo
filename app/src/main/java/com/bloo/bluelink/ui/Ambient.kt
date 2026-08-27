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

@Composable
internal fun borderlessFieldColors(): androidx.compose.material3.TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = scheme.surface,
        unfocusedContainerColor = scheme.surface,
        disabledContainerColor = scheme.surface,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
    )
}

/**
 * The Screen.Loading bootstrapping placeholder -- see that state's own doc
 * (AppViewModel.kt) for why it exists. Same AuroraBackground + "Bloo"
 * wordmark [LoginScreen] opens with, so if this resolves to Login next
 * there's nothing to visually reconcile: same backdrop, same brand mark,
 * already faded in. No form, no fields, nothing interactive -- this is a
 * "still deciding" placeholder, shown for however long the cold-start
 * auto-login coroutine takes to resolve, not a real destination on its own.
 *
 * The wordmark fades in on its own (not present from frame one) rather than
 * being static: a car-status app booting into a full-strength logo the
 * INSTANT the process starts reads as an abrupt, slightly jarring "already
 * finished loading" claim before anything has actually happened yet; easing
 * it in over a beat reads as the app settling into itself instead.
 */
@Composable
internal fun LoadingScreen(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "loadingWordmarkFade",
    )
    Box(modifier.fillMaxSize()) {
        AuroraBackground(Modifier.matchParentSize())
        Text(
            "Bloo",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center).graphicsLayer { this.alpha = alpha },
        )
    }
}

// A synced device not seen this long is flagged as possibly on a different Drive
// file (the two-files trap) in the sync settings. 2 days is well past any normal
// gap for a device in active use, so it doesn't false-alarm on a phone you simply
// didn't open yesterday.
internal const val STALE_DEVICE_MS = 2L * 24 * 60 * 60 * 1000

/**
 * Sign-in form supporting every brand (US Hyundai/Genesis/Kia plus the three
 * Canada brands) from one screen. All fields
 * (email/password/pin/brand) are local `mutableStateOf` -- nothing is
 * persisted until [onLogin] fires, so switching brands mid-entry doesn't
 * lose the typed email/password. Selecting a brand via [MorphSegmented]
 * only changes copy/labels/validation shape shown here; brand-specific
 * strings (subtitle, email label, forgot-password URL, sign-in button
 * label) are recomputed from `brand` on every recomposition and each swap
 * cross-fades via [AnimatedContent] rather than snapping instantly.
 * The PIN field is only shown for brands that need one (`brand.requiresPin`
 * -- every brand except Kia US); Kia and Canada instead get a one-time-
 * passcode dialog elsewhere ([KiaOtpDialog]/[CanadaOtpDialog]) after
 * submitting -- Canada still shows the PIN field first since its commands
 * are PIN-gated even though sign-in itself goes through OTP. `formVisible`
 * flips true one
 * frame after first composition purely to trigger the initial slide-up-and-
 * fade-in entrance animation.
 */
@Composable
internal fun LoginScreen(
    loading: Boolean,
    onLogin: (String, String, String, Brand) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    // Region gates which 3 brands the segmented picker below offers, rather
    // than cramming all 6 US+Canada entries into one row -- Hyundai/Genesis/
    // Kia Canada run on a completely different backend (see CanadaApi) with
    // its own sign-in shape, so switching region also resets `brand` to that
    // region's first entry.
    var region by remember { mutableStateOf("US") }
    var brand by remember { mutableStateOf(Brand.HYUNDAI) }
    val scheme = MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current
    val shortScreen = cfg.screenHeightDp < 520
    val heroHeight = if (shortScreen) 96.dp else 160.dp
    val context = LocalContext.current

    // Brand-specific copy
    val brandSubtitle = when (brand) {
        Brand.HYUNDAI -> "A better Bluelink · US"
        Brand.GENESIS -> "A better Genesis · US"
        Brand.KIA     -> "A better Kia Connect · US"
        Brand.HYUNDAI_CA -> "A better Bluelink · Canada"
        Brand.GENESIS_CA -> "A better Genesis Connect · Canada"
        Brand.KIA_CA -> "A better Kia Connect · Canada"
        Brand.HYUNDAI_EU -> "A better Bluelink · Europe"
    }
    val emailLabel = when (brand) {
        Brand.HYUNDAI, Brand.HYUNDAI_CA, Brand.HYUNDAI_EU -> "Bluelink email"
        Brand.GENESIS, Brand.GENESIS_CA -> "Genesis account email"
        Brand.KIA, Brand.KIA_CA -> "Kia Connect email"
    }

    // Animate the form in from below on first composition.
    var formVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { formVisible = true }

    if (onCancel != null) BackHandler { onCancel() }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.matchParentSize())
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Wordmark hero — subtitle crossfades when the brand changes.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        "Bloo",
                        style = if (shortScreen) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    AnimatedContent(
                        targetState = brandSubtitle,
                        transitionSpec = {
                            (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 3 }) togetherWith
                                (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 3 })
                        },
                        label = "loginSubtitle",
                    ) { subtitle ->
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Form slides up from below on first composition.
            AnimatedVisibility(
                visible = formVisible,
                enter = slideInVertically(tween(420, easing = LinearOutSlowInEasing)) { it / 3 } +
                    fadeIn(tween(380)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val fieldColors = borderlessFieldColors()

                    Text(
                        "Region",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                    MorphSegmented(
                        options = listOf(
                            SegmentOption("US", "United States", null),
                            SegmentOption("CA", "Canada", null),
                            SegmentOption("EU", "Europe", null),
                        ),
                        selectedKey = region,
                        onSelect = { key ->
                            region = key
                            // Reset to the region's first (only, for EU) brand,
                            // since each region's backend/sign-in shape differs.
                            brand = Brand.brandsForRegion(key).first()
                        },
                    )

                    Text(
                        "Sign in with",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                    // Brand.brandsForRegion, shared with the watch's own picker --
                    // which was a hand-written copy of this list, and had silently
                    // stopped at the three US brands.
                    val brandOptions = Brand.brandsForRegion(region)
                    MorphSegmented(
                        options = brandOptions.map { b ->
                            SegmentOption(b.name, Brand.shortLabel(b), null)
                        },
                        selectedKey = brand.name,
                        onSelect = { key -> brand = Brand.valueOf(key) },
                    )

                    // Email field — label and placeholder animate with brand. Same
                    // fadeIn/fadeOut durations (220/160) as the sign-in button's own
                    // label and the privacy note below -- all three are driven by the
                    // same brand-selection change, so they should settle together.
                    AnimatedContent(
                        targetState = emailLabel,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                        },
                        label = "emailLabel",
                    ) { label ->
                        Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text(emailLabel) },
                        singleLine = true,
                        shape = FieldShape,
                        colors = fieldColors,
                        leadingIcon = { Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Password", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Password") },
                        singleLine = true,
                        shape = FieldShape,
                        colors = fieldColors,
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            MorphIconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // PIN — every brand except Kia US (Kia's own commands need
                    // no PIN at all; Canada still needs one for CanadaApi.pinAuth
                    // even though its sign-in also goes through OTP).
                    AnimatedVisibility(
                        visible = brand.requiresPin,
                        enter = collapseEnter(Alignment.Bottom),
                        exit = collapseExit(Alignment.Bottom),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Service PIN", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it },
                                placeholder = { Text("Service PIN") },
                                singleLine = true,
                                shape = FieldShape,
                                colors = fieldColors,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Sign in CTA — label reflects the chosen brand.
                    val signInSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = signInSource,
                        enabled = !loading,
                    ) {
                        MorphButton(
                            onClick = { onLogin(email, password, pin, brand) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            interactionSource = signInSource,
                            enabled = !loading,
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary,
                        ) {
                            if (loading) {
                                LoadingIndicator()
                            } else {
                                AnimatedContent(
                                    targetState = brand.label,
                                    // Same duration as the email label's own crossfade just
                                    // above -- both are driven by the same brand-selection
                                    // change, so they should settle together instead of at
                                    // three slightly different paces.
                                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                                    label = "signInLabel",
                                ) { label ->
                                    Text("Sign in to $label", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    if (onCancel != null) {
                        val cancelSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = cancelSource,
                            enabled = true,
                        ) {
                            MorphButton(
                                onClick = onCancel,
                                modifier = Modifier.fillMaxWidth(),
                                interactionSource = cancelSource,
                                containerColor = scheme.secondaryContainer,
                                contentColor = scheme.onSecondaryContainer,
                            ) { Text("Cancel", fontWeight = FontWeight.SemiBold) }
                        }
                    }

                    // Forgot password — MorphTextButton that routes to the right brand portal.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val forgotSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = forgotSource,
                            enabled = true,
                        ) {
                            MorphTextButton(
                                text = "Forgot password?",
                                interactionSource = forgotSource,
                            onClick = {
                                val forgotUrl = when (brand) {
                                    Brand.HYUNDAI -> "https://owners.hyundaiusa.com/us/en/forgot-password"
                                    Brand.GENESIS -> "https://owners.genesis.com/us/en/forgot-password.html"
                                    Brand.KIA     -> "https://owners.kia.com/us/en/kia-owner-portal.html"
                                    Brand.HYUNDAI_CA -> "https://www.hyundaicanada.com/en/owners-section"
                                    Brand.GENESIS_CA -> "https://www.genesis.com/ca/en/support/contact-us.html"
                                    Brand.KIA_CA -> "https://www.kia.ca/en/owners"
                                    Brand.HYUNDAI_EU -> "https://www.hyundai.com/eu/en/owners.html"
                                }
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(forgotUrl)))
                            },
                            contentColor = scheme.onSurfaceVariant,
                        )
                        }
                    }

                    AnimatedContent(
                        targetState = brand.label,
                        // Same duration as this form's other two brand-driven crossfades
                        // (the email label and the sign-in button label) -- see there.
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                        label = "privacyNote",
                    ) { label ->
                        Text(
                            "Credentials are sent directly to $label's telematics servers and " +
                                "stored encrypted on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Kia sign-in verification: pick where the one-time code goes (email/text),
 * then enter it. Shown over the login form while a Kia OTP challenge is open.
 */
@Composable
internal fun KiaOtpDialog(otp: KiaOtpUi, loading: Boolean, vm: AppViewModel) {
    var code by remember(otp.sentTo) { mutableStateOf("") }
    // Standardized on the shared GlassAlertDialog shell (frosted card, 28dp
    // corners, stacked full-width buttons) instead of a raw M3 AlertDialog.
    GlassAlertDialog(
        onDismissRequest = { if (!loading) vm.kiaCancelOtp() },
        icon = Icons.Filled.Lock,
        title = if (otp.sentTo == null) "Verify it's you" else "Enter your code",
        text = {
            if (otp.sentTo == null) {
                Text("Kia needs to verify this sign-in with a one-time code. Where should it go?")
                if (otp.challenge.hasEmail) {
                    val emailSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = emailSource,
                        enabled = !loading,
                    ) {
                        MorphButton(
                            onClick = { vm.kiaSendOtp("EMAIL") },
                            interactionSource = emailSource,
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Email" + (otp.challenge.email?.let { " · $it" } ?: "")) }
                    }
                }
                if (otp.challenge.hasSms) {
                    val smsSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = smsSource,
                        enabled = !loading,
                    ) {
                        MorphButton(
                            onClick = { vm.kiaSendOtp("SMS") },
                            interactionSource = smsSource,
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Text message" + (otp.challenge.sms?.let { " · $it" } ?: "")) }
                    }
                }
            } else {
                Text(
                    if (otp.sentTo == "SMS") "We texted you a one-time code."
                    else "We emailed you a one-time code.",
                )
                OtpCodeField(code) { code = it }
            }
        },
        buttons = {
            // Verify shown only once a code's been sent; Cancel always. Stacked
            // full-width (primary on top) per the shell's convention.
            if (otp.sentTo != null) {
                val verifySource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = verifySource,
                    enabled = !loading && code.isNotBlank(),
                ) {
                    MorphButton(
                        onClick = { vm.kiaVerifyOtp(code) },
                        interactionSource = verifySource,
                        enabled = !loading && code.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (loading) LoadingIndicator() else Text("Verify", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            val cancelKiaSource = remember { MutableInteractionSource() }
            SafeExpansiveButton(
                interactionSource = cancelKiaSource,
                enabled = !loading,
            ) {
                MorphTextButton(
                    "Cancel",
                    vm::kiaCancelOtp,
                    interactionSource = cancelKiaSource,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
    )
}

/**
 * Canada sign-in verification: unlike [KiaOtpDialog] there's no destination to
 * pick (email only), and the code is already sent by the time this shows
 * (see AppViewModel.loginCanada), so it goes straight to code entry.
 */
@Composable
internal fun CanadaOtpDialog(otp: CanadaOtpUi, loading: Boolean, vm: AppViewModel) {
    var code by remember(otp.challenge) { mutableStateOf("") }
    GlassAlertDialog(
        onDismissRequest = { if (!loading) vm.canadaCancelOtp() },
        icon = Icons.Filled.Lock,
        title = "Enter your code",
        text = {
            Text(
                "We emailed a one-time code" +
                    (otp.challenge.email?.let { " to $it" } ?: "") + " to verify this sign-in.",
            )
            OtpCodeField(code) { code = it }
        },
        buttons = {
            val canadaVerifySource = remember { MutableInteractionSource() }
            SafeExpansiveButton(
                interactionSource = canadaVerifySource,
                enabled = !loading && code.isNotBlank(),
            ) {
                MorphButton(
                    onClick = { vm.canadaVerifyOtp(code) },
                    interactionSource = canadaVerifySource,
                    enabled = !loading && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loading) LoadingIndicator() else Text("Verify", fontWeight = FontWeight.SemiBold)
                }
            }
            val canadaCancelSource = remember { MutableInteractionSource() }
            SafeExpansiveButton(
                interactionSource = canadaCancelSource,
                enabled = !loading,
            ) {
                MorphTextButton(
                    "Cancel",
                    vm::canadaCancelOtp,
                    interactionSource = canadaCancelSource,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
    )
}

/**
 * The one-time-code entry field shared by [KiaOtpDialog] and [CanadaOtpDialog]. Both
 * hoist their own `code` state (the Verify button reads it), so this takes the value
 * and its setter rather than owning the buffer -- everything else (the "Code" label,
 * single line, number keyboard, [FieldShape] and full width) is identical.
 */
@Composable
internal fun OtpCodeField(code: String, onCodeChange: (String) -> Unit) {
    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text("Code") },
        singleLine = true,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

// UpdatePromptDialog used to live here -- replaced by UpdateAvailableTile
// (see below Screens.kt), a standalone pebble pinned under the hero tile
// instead of an interrupting popup. See its doc comment for why.

/**
 * The app's shared "important pop-up" dialog shell. Update-available and
 * Drive-sync-setup both route through this now instead of two separately
 * hand-rolled AlertDialogs that merely happened to look similar. A single
 * elevated card -- icon in a tonal container, headline, supporting content,
 * stacked actions -- per the Material 3 "basic dialog" layout, rather than
 * routing through AlertDialog's own title/text slots: those render as two
 * independently-clipped boxes with a gap between them, which read as a
 * broken, disconnected stack of panels once each one lost the glass blur
 * that used to visually tie them together.
 */
@Composable
internal fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: @Composable ColumnScope.() -> Unit,
    buttons: @Composable ColumnScope.() -> Unit,
    // Optional leading icon: when non-null it renders in the 48dp primaryContainer
    // circle; when null the circle is skipped entirely (for dialogs like "Save
    // preset" / "Rename device" that have no natural glyph). Defaulted so existing
    // callers that pass an icon are unchanged.
    icon: ImageVector? = null,
    // Optional trailing action in the title row (e.g. PaletteEditorDialog's delete
    // button). Sits to the right of the title, vertically centered.
    titleTrailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismissRequest) {
        // Dialog() opens its own platform Window, which doesn't inherit the
        // app's forceDarkAllowed=false the way the main Activity window does
        // -- on API 29+ Android's automatic Force Dark heuristic was
        // re-inverting already-dark, explicitly-colored text drawn here
        // (this dialog's title and body rendered near-black on a near-black
        // card, while the identical text elsewhere in the app -- inside the
        // Activity's own window -- rendered correctly). Disabling it on this
        // window specifically stops Android from "helpfully" reprocessing
        // colors Compose already resolved correctly.
        val dialogView = LocalView.current
        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val decorView = (dialogView.parent as? DialogWindowProvider)?.window?.decorView
                // Reflection, not a direct call: setForceDarkAllowed isn't
                // exposed as a resolvable View method against every compileSdk
                // stub this project has built against, even though it's a
                // real public API on-device at this API level.
                runCatching {
                    android.view.View::class.java
                        .getMethod("setForceDarkAllowed", Boolean::class.javaPrimitiveType)
                        .invoke(decorView, false)
                }
            }
        }
        // Near-opaque fill -- this card sits over the scrim, framed by the
        // app's frosted edge (appGlassRim). Kept as its own override rather than
        // folded into the shared default the rest of the app's frosted chrome now
        // uses uniformly: a modal dialog is a different category from a pill or a
        // pebble card floating over live content -- it always sits over its own
        // dedicated scrim, never directly over an unpredictable photo, and its
        // job is paragraphs of body text and buttons a user has to read and act
        // on, not a glanceable control. The general "everything shares one
        // transparency" rule is about the floating chrome that DOES sit over
        // content; this is the one deliberate exception, not a leftover.
        Surface(
            shape = shape,
            color = scheme.surfaceContainerHigh.copy(alpha = glassContainerAlpha(0.97f)),
            modifier = Modifier
                .fillMaxWidth()
                .dropShadow(shape, blurRadius = 22.dp, offsetY = 8.dp)
                .appGlassRim(shape),
        ) {
            Column(Modifier.padding(24.dp)) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(scheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (titleTrailing != null) {
                        Spacer(Modifier.width(8.dp))
                        titleTrailing()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = text,
                )
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), content = buttons)
            }
        }
    }
}

/**
 * A softly-blurred, slowly-drifting "aurora" of colour blobs - the animated login
 * backdrop. Three blobs ease back and forth on different periods.
 */
/** Triangle wave in [0,1]: rises for [periodMs], falls for [periodMs], repeats. */
internal fun triangleWave(elapsedMs: Long, periodMs: Long): Float {
    val phase = elapsedMs % (2 * periodMs)
    return if (phase < periodMs) phase.toFloat() / periodMs else 2f - phase.toFloat() / periodMs
}

/**
 * Draws the animated gradient-blob backdrop used behind the login screen,
 * onboarding, and (optionally) the garage. Colors, motion style, and the
 * pull-to-refresh "explosion" pulse are all independent concerns composed
 * together here:
 *  - [colorMode] picks how the blob hues are derived: "material" uses the
 *    theme's primary/secondary/tertiary directly, "custom" derives
 *    complementary/analogous hues from [appearance]'s stored hex color via
 *    HSV rotation, and the default ("complementary") derives a hue from the
 *    surface color rotated 180°.
 *  - [motionMode] picks whether the blobs drift on their own (`static`,
 *    driven by [triangleWave]-based ease loops further below) or track the
 *    phone's tilt via the accelerometer (`motion`). In Motion mode, a fast
 *    exponential-moving-average of the raw sensor reading is compared
 *    against a much slower moving average of the same signal; the
 *    difference isolates *deliberate* tilting from however the phone is
 *    generally being held, so the background doesn't sit permanently
 *    off-center just because the phone rests at an angle.
 *  - `refreshing` drives a one-shot grow/hold/shrink pulse (`explosion`, an
 *    [Animatable]) via [LaunchedEffect], keyed on `refreshing` itself so a
 *    pull-to-refresh that resolves near-instantly still visibly completes a
 *    full grow-then-shrink cycle instead of snapping back before the eye
 *    can register it.
 */
@Composable
internal fun AuroraBackground(
    modifier: Modifier = Modifier,
    appearance: SettingsStore.Appearance? = null,
    refreshing: Boolean = false,
    /** Freezes the ambient drift (and the tilt sensor) while true. The search
     *  panel pauses it so typing/keyboard frames don't contend with a
     *  full-screen blur redraw -- the background's own drift is 12fps of
     *  blur work, exactly the cost a small low-end screen can't afford on
     *  top of an IME animation. */
    paused: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val motionMode = appearance?.auroraMotion ?: "static"
    val colorMode = appearance?.auroraColorMode ?: "complementary"
    val customHex = appearance?.auroraCustomColor

    // "Motion" follows the phone's tilt (like a lock-screen wallpaper
    // parallax); "Static" ignores tilt entirely and instead gets its own
    // slow, small ambient drift (see p1/p2/p3 below) so it still reads as
    // alive when the phone is sitting still, rather than a literally frozen
    // frame. A low exponential-smoothing alpha is what keeps the tilt from
    // jittering on every tiny hand tremor: each sample only nudges the
    // running average a little, so the blobs drift toward wherever you've
    // tilted to over roughly a second, not instantly. The multiplier is
    // deliberately large enough to be unmistakable -- it previously read as
    // "not doing anything" because it was blended in alongside a much
    // bigger automatic drift that swamped it; Motion mode no longer runs
    // that automatic drift at all, so tilt is the only thing moving it.
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    // LIVE pause flag (the coroutines and the sensor callback start once and
    // must keep seeing the newest value, not the first composition's).
    val currentPaused by rememberUpdatedState(paused)
    val motionActive = motionMode == "motion"
    if (motionActive) {
        val ctx = LocalContext.current
        DisposableEffect(ctx) {
            val mgr = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            // Raw accelerometer values bake in however the phone is generally
            // being held, not just active tilting -- held upright to look at
            // (the overwhelmingly common case), gravity alone puts values[1]
            // near +-9.8, a huge constant offset next to the deliberate ~0.5
            // multiplier below. That pinned the blobs off in one direction
            // (reading as "not centered") and saturated well past where any
            // real hand tilt could move them further (reading as "motion does
            // nothing"). rawX/rawY track the sensor directly; baseX/baseY
            // track the same signal on a much slower average -- "how you're
            // generally holding it right now" -- and tilt is only the
            // difference between the two, so genuine movement still shows up
            // small and centred regardless of the phone's constant baseline
            // angle, and re-centres itself if you settle into holding it
            // differently for a while.
            var rawX = 0f; var rawY = 0f
            var baseX = 0f; var baseY = 0f
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // No writes while paused: a tilt sample just stalls (the
                    // sensor keeps delivering; we simply stop turning those
                    // samples into invalidation).
                    if (currentPaused) return
                    val fastAlpha = 0.08f
                    val slowAlpha = 0.01f
                    val x = -event.values[0]
                    val y = event.values[1]
                    rawX = rawX * (1 - fastAlpha) + x * fastAlpha
                    rawY = rawY * (1 - fastAlpha) + y * fastAlpha
                    baseX = baseX * (1 - slowAlpha) + x * slowAlpha
                    baseY = baseY * (1 - slowAlpha) + y * slowAlpha
                    tiltX = (rawX - baseX) * 0.06f
                    tiltY = (rawY - baseY) * 0.06f
                }
                override fun onAccuracyChanged(s: Sensor, acc: Int) {}
            }
            if (sensor != null) mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { mgr.unregisterListener(listener) }
        }
    } else {
        // Not tracking tilt in Static mode -- reset so a mode switch away
        // from Motion doesn't leave the blobs stuck at a stale offset.
        LaunchedEffect(motionActive) { tiltX = 0f; tiltY = 0f }
    }

    // Remembered on the inputs the derivation actually reads, so the HSV round-trips and
    // parseColor calls don't re-run on every frame of the pull-to-refresh explosion animation
    // (this composable recomposes each of those frames because it reads explosion.value below;
    // the blob colours don't depend on the animation, so they shouldn't ride along with it).
    // Keys cover all three branches: material reads scheme.primary/tertiary/secondary, custom
    // reads customHex, complementary reads scheme.surface (+ tertiary/secondary passthrough).
    val (basePrimary, baseTertiary, baseSecondary) = remember(
        colorMode, customHex, scheme.primary, scheme.tertiary, scheme.secondary, scheme.surface,
    ) {
        val primary = when (colorMode) {
            "material" -> scheme.primary
            "custom" -> customHex?.let { hx -> runCatching { Color(android.graphics.Color.parseColor(hx)) }.getOrNull() } ?: scheme.primary
            else -> {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(scheme.surface.toArgb(), hsv)
                hsv[0] = (hsv[0] + 180f) % 360f
                Color(android.graphics.Color.HSVToColor(hsv))
            }
        }
        val tertiary = when (colorMode) {
            "material" -> scheme.tertiary
            "custom" -> customHex?.let { hx -> runCatching {
                val c = android.graphics.Color.parseColor(hx)
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(c, hsv)
                hsv[0] = (hsv[0] + 180f) % 360f
                Color(android.graphics.Color.HSVToColor(hsv))
            }.getOrNull() } ?: scheme.tertiary
            else -> scheme.tertiary
        }
        val secondary = when (colorMode) {
            "material" -> scheme.secondary
            "custom" -> customHex?.let { hx -> runCatching {
                val c = android.graphics.Color.parseColor(hx)
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(c, hsv)
                hsv[0] = (hsv[0] + 90f) % 360f
                Color(android.graphics.Color.HSVToColor(hsv))
            }.getOrNull() } ?: scheme.secondary
            else -> scheme.secondary
        }
        Triple(primary, tertiary, secondary)
    }

    // A guaranteed grow-then-shrink pulse rather than a value that just
    // chases the raw refreshing boolean: a quick refresh (cache hit, or a
    // refresh that resolves in well under a second) flipped refreshing back
    // to false before the spring had visibly moved, which read as the
    // background just snapping to its resting size instead of animating.
    // Holding briefly at the peak guarantees the "grow" half is actually
    // visible before the "shrink" half starts, regardless of how fast the
    // underlying refresh itself completes.
    val explosion = remember { Animatable(0f) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            explosion.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
            delay(220)
        }
        explosion.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    val explosionValue = explosion.value
    val explodeAlpha = 1f + explosionValue * 2.5f
    val explodeSize = 1f + explosionValue * 0.8f
    val explodeSpread = 1f + explosionValue * 0.3f
    // Both modes now run the same ambient drift below; Motion mode adds tilt
    // on top of it instead of replacing it entirely, so a phone that isn't
    // being actively tilted (sitting on a desk, in a stand, or just being
    // looked at) still reads as alive instead of a dead, frozen frame.
    // Hand-ticked at ~12fps instead of riding Compose's animation clock
    // (which recomposes on every display frame, up to 120x/sec). For a slow
    // multi-second drift sitting under a heavy 90dp blur, that clock was
    // forcing a full-screen blur redraw every single vsync for no visible
    // gain over a much coarser update rate -- a real, sustained source of
    // GPU load (and the phone heat it produced) any time this screen was on
    // screen, which is most of the time this background is enabled at all.
    var p1 by remember { mutableFloatStateOf(0.5f) }
    var p2 by remember { mutableFloatStateOf(0.5f) }
    var p3 by remember { mutableFloatStateOf(0.5f) }
    // Runs in BOTH modes now -- Motion previously froze this drift entirely
    // and relied only on tilt, so a phone sitting still (the common case:
    // on a desk, in a stand, or just being looked at without being moved)
    // showed a completely dead background. This is now a smaller ambient
    // drift added underneath tilt in Motion mode, and the sole driver in
    // Static mode -- widened and sped up from the previous ±0.08/9-14s
    // (correct in principle, but under a heavy 90dp blur it read as "not
    // animating" -- too subtle to actually perceive) to something
    // unambiguously visible at a glance.
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            if (currentPaused) {
                delay(120)
                continue
            }
            val elapsed = System.currentTimeMillis() - start
            p1 = 0.32f + (0.68f - 0.32f) * triangleWave(elapsed, 9_000L)
            p2 = 0.68f + (0.32f - 0.68f) * triangleWave(elapsed, 7_000L)
            p3 = 0.35f + (0.65f - 0.35f) * triangleWave(elapsed, 6_000L)
            delay(80)
        }
    }
    fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f
    Box(
        modifier
            .fillMaxSize()
            // Lighter than before (was 120dp): that much blur smoothed three
            // drifting blobs into a wash that barely changed frame to frame,
            // reading as "not animating" even though the drift was running.
            // Blur cut from 90dp to 44dp: the old radius was tuned when the
            // blobs were denser, but a full-screen 90dp blur redraws at every
            // drift tick (~12fps) any time this is on screen -- the single
            // most expensive steady-state draw in the app. 44dp still reads
            // as a soft wash over three large circles and costs a fraction
            // (and the pause hook above means it isn't redrawing at all
            // while the search panel is up with the keyboard animating).
            .blur((44.dp * (1f + explosionValue * 0.5f)), edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .drawBehind {
                drawRect(scheme.surface)
                fun blob(c: Color, fx: Float, fy: Float, r: Float) =
                    drawCircle(c, radius = size.minDimension * r, center = Offset(size.width * fx, size.height * fy))
                blob(basePrimary.copy(alpha = (0.30f * explodeAlpha).coerceIn(0f, 1f)), (mix(0.26f, 0.74f, p1) + tiltX) * explodeSpread, (mix(0.30f, 0.65f, p2) + tiltY) * explodeSpread, 0.45f * explodeSize)
                blob(baseTertiary.copy(alpha = (0.25f * explodeAlpha).coerceIn(0f, 1f)), (mix(0.32f, 0.68f, p2) - tiltX) * explodeSpread, (mix(0.35f, 0.70f, p3) - tiltY) * explodeSpread, 0.40f * explodeSize)
                // fx range was 0.22-0.58 (centred at 0.40, visibly left of the
                // other two blobs' 0.50) -- the whole composite wash read as
                // biased toward one side even before any tilt was applied.
                blob(baseSecondary.copy(alpha = (0.20f * explodeAlpha).coerceIn(0f, 1f)), (mix(0.32f, 0.68f, p3) + tiltX) * explodeSpread, (mix(0.28f, 0.62f, p1) + tiltY) * explodeSpread, 0.38f * explodeSize)
            },
    )
}
