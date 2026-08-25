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

/** One icon-only segment in a connected button group (see [connectedGroupShape]). */
internal data class GroupIconAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)


/**
 * The one button style used across the whole app. It rests as a **pill** and
 * becomes a **rounded rectangle** only while [active] (an on/toggled state) - or
 * momentarily while pressed. When [active], it fills with [activeContainerColor].
 * Its width springs (with a little overshoot) whenever the content width changes,
 * e.g. the label flips Start -> Stop.
 *
 * This IS the shared [MorphButtonCore] from :uicommon -- the same machinery the
 * watch's MorphButton uses -- dressed in this module's Material theme colours,
 * haptics and M3 content padding, plus two phone-wide conventions:
 *
 *  - [minHeight] of 48dp (the M3 touch target the old `Button` enforced
 *    implicitly) unless a caller opts out to keep a shorter pill
 *    (split-button halves, preset pills).
 *  - `selected = [active]` semantics, so TalkBack hears the state, not just
 *    the label ("Unlock" says what happens, not what is).
 *
 * Every other button-looking control in this app -- the split action+chevron
 * pills, the standalone chevron, the preset pills, the cover action bar --
 * is this same component; the ones that look different simply pass different
 * shapes (shapeForCorner) and colours. There are no separate button types.
 */
@Composable
fun MorphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    containerColor: Color = buttonContainer(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    activeContainerColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    /** Overrides the disabled content tone (default: resolved content at 38%
     *  alpha -- "only the label fades"). The cover action button passes its
     *  own full-alpha tone because it dims the WHOLE pill itself. */
    disabledContentColor: Color? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    // An asymmetric shape to use instead of the plain pill<->square morph --
    // for a connected button-group segment (see StateControl), or a split
    // button half, whose inner (seam) corners stay small while the outer
    // corner is the one that morphs. Receives the raw morph progress
    // (0 = pill, 1 = fully morphed) and the animated corner percent, so the
    // shape can derive any corner geometry from the button's own spring.
    shapeForCorner: ((morph: Float, cornerPercent: Int) -> Shape)? = null,
    /** Hold-to-act action (chevron easter egg, cover flash-lights). */
    onLongClick: (() -> Unit)? = null,
    /** Haptic for a plain click; null = the standard click() pulse. The lock
     *  button overrides with heavy(), the chevron with tick()/click() by
     *  direction. */
    onClickHaptic: (() -> Unit)? = null,
    /** The pill's corner-percent when idle (50 = perfect pill) and when
     *  [active]/pressed (default 28 = the app's standard rounded square).
     *  Overridable so a fixed-height square button (cover actions, chevron
     *  nub) can land on its own exact corner radius. */
    pillCornerPercent: Float = PillCornerPercent,
    morphedCornerPercent: Float = MorphedCornerPercent,
    /** 48dp is the minimum touch-target height M3 `Button` enforced implicitly;
     *  pass 0.dp to let a short pill keep its natural height. */
    minHeight: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    val clickHaptic = onClickHaptic ?: { haptics?.click() }
    // The content tone content lambdas inherit, provided the way M3's Button
    // provides it internally (the shared core is foundation-only and cannot
    // reach material3's LocalContentColor).
    val resolvedContent = if (active) activeContentColor else contentColor
    val providedContent = if (enabled) {
        resolvedContent
    } else {
        // Keep the button's full background when disabled (only the label
        // fades) instead of M3's default onSurface@12%, which is invisible
        // against light cards and made disabled buttons look backgroundless.
        disabledContentColor ?: resolvedContent.copy(alpha = 0.38f)
    }
    CompositionLocalProvider(LocalContentColor provides providedContent) {
        MorphButtonCore(
            onClick = { clickHaptic(); onClick() },
            modifier = modifier
                // `active` is otherwise a colour-only change -- most call sites also
                // swap their label text (Lock/Unlock, Start/Stop), which is why this
                // mostly "worked" for TalkBack by accident, but that's caller
                // discipline, not something the shared button guarantees. Setting
                // `selected` here makes every MorphButton correct by construction:
                // the app's one button framework, so this is the single highest-
                // leverage place to fix it.
                .semantics { selected = active }
                .animateContentSize(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                )
                .then(if (minHeight > 0.dp) Modifier.heightIn(min = minHeight) else Modifier),
            enabled = enabled,
            active = active,
            containerColor = containerColor,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentPadding = contentPadding,
            border = if (active) null else border,
            interactionSource = interactionSource,
            onLongClick = onLongClick,
            pillCornerPercent = pillCornerPercent,
            morphedCornerPercent = morphedCornerPercent,
            shapeForCorner = shapeForCorner,
            content = content,
        )
    }
}

/**
 * A text-only [MorphButton] - the app's one button framework, used everywhere a
 * plain labelled button is needed (dialogs, settings, etc.) so they all share
 * the pill-morphs-to-rounded-square press feel.
 */
@Composable
fun MorphTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = buttonContainer(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    MorphButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = containerColor,
        contentColor = contentColor,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The morph family's icon-only member.
 *
 * [MorphButton] is the wrong tool for a bare icon affordance -- a snackbar
 * action, a text-field's clear button, a 28dp edit glyph in a swatch grid -- since
 * it would wrap each one in a filled pill and change the design rather than unify
 * it. So this keeps [IconButton]'s containerless chrome and 40dp target exactly,
 * and adds the two things every other member of the family provides and these
 * were missing:
 *
 *  - **The click haptic.** Of the six bare IconButtons in this file, exactly ONE
 *    remembered to call `haptics?.click()` itself. Every Morph* control fires one;
 *    a containerless icon is no less of a button to the finger.
 *  - **A press response.** With no container there is no corner to morph, so the
 *    equivalent is a scale dip, on the family's own [SoftDamping] spring.
 *
 * Same parameter shape as [IconButton] so converting a call site is mechanical.
 * If you are converting one that already called the haptic by hand, delete that
 * call -- it fires here now, and two in a row is a stutter, not emphasis.
 */
@Composable
fun MorphIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val haptics = LocalHaptics.current
    val pressed by interactionSource.collectIsPressedAsState()
    // Already optimal, and worth a note so nobody "fixes" it: `by` here costs nothing, because
    // what decides the phase of a snapshot read is WHERE the getter runs, not whether the
    // property is delegated. `scale` is referenced only inside the graphicsLayer BLOCK below,
    // so the read happens when Compose invokes that block -- composition and layout are
    // skipped. Google's own guidance shows exactly this shape (`val color by animateColorBetween(...)`
    // read inside `drawBehind { }`).
    //
    // I briefly rewrote this to `val scale = animateFloatAsState(...)` plus `scale.value`,
    // believing the delegated form forced a composition read. It does not; the two are
    // identical here. Reverted, because a comment asserting a difference that does not exist
    // teaches the next reader a false rule.
    //
    // The real audit question for the ~61 `by animate*AsState` sites in this project is not
    // `by` vs `=`. It is whether the value is read in the composable BODY (recomposes every
    // frame -- e.g. passed to `Modifier.padding(...)`, a `TextStyle`, or a size) or inside a
    // lambda modifier like `graphicsLayer {}` / `offset {}` / `drawBehind {}` (already
    // deferred, nothing to do). This site is the second kind.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium),
        label = "morphIconPress",
    )
    IconButton(
        onClick = { haptics?.click(); onClick() },
        // On the button, not the icon: scaling the icon alone shrinks the glyph
        // inside a target that stays put, which reads as a glitch rather than a
        // press.
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Standard leading slot for a [MorphButton]: shows the [icon], or a same-sized
 * spinner while [pending], so the button width never changes just from loading.
 */
@Composable
fun MorphButtonLabel(
    icon: ImageVector,
    label: String,
    pending: Boolean,
    iconSize: Dp = 18.dp,
    spinning: Boolean = false,
) {
    if (pending) {
        LoadingIndicator(Modifier.size(iconSize))
    } else {
        // Always-composed Animatable, but it only runs while spinning - so idle
        // buttons don't each hold a live infinite animation, and we avoid calling
        // remember conditionally.
        val angle = remember { Animatable(0f) }
        LaunchedEffect(spinning) {
            if (spinning) {
                // Ramp up: the first revolution accelerates from rest...
                angle.animateTo(
                    targetValue = angle.value + 360f,
                    animationSpec = tween(durationMillis = 850, easing = FastOutLinearInEasing),
                )
                // ...then hold a steady, fast linear spin.
                while (true) {
                    angle.animateTo(
                        targetValue = angle.value + 360f,
                        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
                    )
                }
            } else if (angle.value != 0f) {
                // Ramp down: decelerate to the next full turn, then reset.
                val target = kotlin.math.ceil(angle.value / 360f) * 360f
                angle.animateTo(target, tween(durationMillis = 700, easing = LinearOutSlowInEasing))
                angle.snapTo(0f)
            }
        }
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize).rotate(angle.value),
        )
    }
    Spacer(Modifier.width(8.dp))
    Text(label, fontWeight = FontWeight.SemiBold)
}

/**
 * A unified selectable chip: a **pill** when unselected, morphing smoothly into a
 * filled **rounded box** when selected. Replaces ad-hoc FilterChips so selection
 * feels the same everywhere.
 */
@Composable
fun MorphChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val haptics = LocalHaptics.current
    val chipSelected = selected
    // The same MorphButton as everywhere: pill when idle, primary fill +
    // rounded box when selected, standard corner-percent animation. The chip's
    // historic 22dp/12dp corners on its ~40dp height are just under the
    // framework's 50/28 defaults, so it uses the shared defaults verbatim.
    MorphButton(
        onClick = { onClick() },
        onClickHaptic = { haptics?.tick() },
        active = selected,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        minHeight = 0.dp,
        // Same gap MorphSegmented had: a selectable pill with no `selected`
        // semantics reaching TalkBack, which announced every chip identically
        // regardless of which one was actually active. Captured into a
        // differently-named local first -- inside semantics{}, `selected` on
        // its own resolves to the SemanticsPropertyReceiver's own property,
        // not this composable's `selected` parameter of the same name.
        modifier = modifier.semantics { this.selected = chipSelected },
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (chipSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}


// --- Pebble (expandable, reorderable section) -----------------------------


/**
 * Right-side expand control for pebbles with no action button — the whole
 * right handle is a pill that morphs to a rounded-square when the section is
 * open, giving a clear visual indicator of state.
 */
@Composable
internal fun MorphExpandButton(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "morphChevron",
    )

    // Easter egg: same hold as SplitExpandButton — long-press spins + vibrates
    var easterEggTriggered by remember { mutableStateOf(false) }
    val easterEggSpin by animateFloatAsState(
        targetValue = if (easterEggTriggered) 360f else 0f,
        animationSpec = if (easterEggTriggered) spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow) else snap(),
        label = "easterEggMorphSpin",
        finishedListener = { if (easterEggTriggered) easterEggTriggered = false },
    )
    // This button is a FIXED 50dp square, so a 50% corner is a true circle and
    // 10dp is exactly 20%. The default 28 (the app's standard rounded square)
    // is deliberately overridden to keep this control's 10dp corners, which
    // the shared percent model expresses cleanly for a fixed-size button.
    MorphButton(
        onClick = { onToggle() },
        onClickHaptic = { if (expanded) haptics?.tick() else haptics?.click() },
        onLongClick = {
            // Easter egg: hold the chevron to spin it + vibrate.
            if (!easterEggTriggered) {
                easterEggTriggered = true
                haptics?.heavy()
            }
        },
        // Expanded highlight = the SAME active state as lock/unlock: primary
        // fill, onPrimary content, straight from MorphButton's defaults.
        active = expanded,
        contentPadding = PaddingValues(0.dp),
        pillCornerPercent = 50f,
        morphedCornerPercent = 20f,
        minHeight = 0.dp,
        // Same as SplitExpandButton's chevron: the icon's contentDescription is
        // the next action, this is the current state -- both together instead
        // of only announcing what tapping does. Tap toggles; holding spins the
        // chevron (easter egg) without toggling.
        modifier = Modifier.size(50.dp).semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
    ) {
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            // Larger chevron icon (24dp to match action button icon size), with
            // easter egg spin animation when the chevron is held.
            modifier = Modifier.size(24.dp).rotate(rotation + easterEggSpin),
        )
    }
}
