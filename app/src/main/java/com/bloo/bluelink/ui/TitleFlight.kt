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

/** How far past [HeroTitleFlight]'s dock line the inline title has to
 *  scroll back before `docked` releases again -- see that class's own
 *  `docked` doc for why a bare threshold isn't enough. Shared by every
 *  construction site so the debounce feels the same everywhere. */
internal val TitleDockHysteresis = 8.dp

/** [HeroTitleFlight.color] before [HeroHeader] has reported a real one yet
 *  (this composable's own first frame) is [Color.Unspecified] -- resolving
 *  that straight through as [TitleFlightOverlay]'s `textColor` would let it
 *  fall through to whatever LocalContentColor happened to already be, an
 *  arbitrary colour rather than a deliberate one. One frame's difference at
 *  most (color is set the same composition pass HeroHeader itself runs),
 *  but a real value beats an accidental one either way. */
@Composable
internal fun Color.takeOrElseOnSurface(): Color =
    if (this == Color.Unspecified) MaterialTheme.colorScheme.onSurface else this

/**
 * The read-only shape [TitleFlightOverlay] actually needs from whatever is
 * flying: a live position, a live docked flag, and a live colour. Extracted
 * from [HeroTitleFlight] as its own interface so other implementations are
 * possible (there are none left in this file -- an earlier page-switch
 * design used a frozen, non-reactive implementation for an exiting slot;
 * see git history), but [TitleFlightOverlay] only ever needs to know this
 * much, not the concrete class.
 */
internal interface TitleFlightSource {
    /** Null until the real inline title has reported a genuine position at
     *  least once (see [HeroTitleFlight]'s own doc on why this is nullable
     *  rather than a fabricated off-screen sentinel). [TitleFlightOverlay]
     *  fades itself out while this is null instead of trusting a made-up
     *  coordinate. */
    val inlinePos: androidx.compose.runtime.State<Offset?>
    /** The inline anchor's own measured size (position + size reported
     *  together by the same onGloballyPositioned passes). Size is how
     *  [TitleFlightOverlay] aligns the flying name to the anchor's vertical
     *  CENTRE line instead of its top -- the name's glyphs are centred in
     *  their own (larger, unscaled) text box, so pinning box-tops made the
     *  name sit low next to a shorter collapsed header row (the % / mi&km
     *  numbers), which is what reads as "the floating name is in the wrong
     *  place when the hero tile is collapsed". Centre-to-centre keeps it on
     *  the same line as the collapsed readout at every anchor scale. Zero
     *  until the first real report. */
    val inlineSize: androidx.compose.runtime.State<IntSize>
    val docked: androidx.compose.runtime.State<Boolean>
    val color: Color
    /** Mirrors PebbleShell's own hero-title grow/shrink scale (1f = fully
     *  grown/expanded size, the collapsed ratio when the hero photo pebble
     *  is collapsed) -- so the ONE thing that actually paints the name
     *  visibly grows/shrinks with the pebble the same way the invisible
     *  anchor Text underneath it always has. 1f (no scaling) for every
     *  surface without a hero photo to collapse against (Settings, a
     *  frozen exiting snapshot, ExpandedCar). */
    val titleScale: Float

    /** Optional Y-override for the inline end: see [HeroTitleFlight.inlineYOverride]. */
    val inlineYOverride: () -> Float?
}

/**
 * What a header title -- [HeroHeader]'s own (the car's name, drawn ON the
 * photo card via [PebbleShell]), or `SettingsHeaderRow`'s own -- reports to
 * whichever surface hosts a [TitleFlightOverlay] for it: its live measured
 * position and colour. [docked] is the one derived fact most callers
 * actually need -- has the title's top edge scrolled above the status bar,
 * with hysteresis (see that property's own doc).
 *
 * This used to be a much bigger contract still (font size, photo URL, and a
 * floating CLONE of the title that hid the real one and flew between its
 * live position and a corner). That clone was a standing source of
 * one-frame flashes: any gap between hiding the real title and the copy's
 * own rendering -- a stale position report, a clip outrunning its padding,
 * a crossfade with mismatched durations -- read as the name vanishing, and
 * each fix surfaced the next. [TitleFlightOverlay] never hides the real
 * title at all -- it's the ONLY thing that ever paints the name, so there
 * is no second copy for any of that to happen between.
 */

internal class HeroTitleFlight(topInsetPx: Float, private val hysteresisPx: Float) : TitleFlightSource {
    // Live field, not a constructor `val` captured once -- the OLD shape
    // forced every one of this class's 4 construction sites to
    // `remember(topInsetPx)`, so the only way to ever notice a changed
    // status-bar inset (rotation, fold/unfold, multi-window resize, an
    // IME-driven inset recompute some OEM skins trigger) was to throw the
    // whole object away and build a fresh one -- discarding every bit of
    // accumulated state (dockedNow, color, titleScale, reportGen, the real
    // inlineX/inlineY) right along with it, snapping the badge back to its
    // just-constructed defaults for no reason related to the inset change
    // itself. A plain mutable field means the call site can push a new
    // value in with a cheap `SideEffect` (see the construction sites' own
    // comment) and the very next onPositioned/onSettled call just picks it
    // up -- no rebuild, no reset.
    var topInsetPx: Float = topInsetPx
    // Live source for the SAME scroll offset the real hero card's own
    // Column places itself against (its call site's `ScrollState.value`,
    // wired in via `SideEffect`, mirroring `topInsetPx`'s identical
    // pattern) -- see `inlinePos`'s own doc for why this exists at all.
    // Defaults to a constant 0f (never-scrolled) rather than null so every
    // read site can call it unconditionally; a surface that never wires
    // this in (there is none currently, but a future one could exist)
    // degrades to "no scroll correction", not a crash.
    var scrollValuePx: () -> Float = { 0f }
    private var inlineX by mutableFloatStateOf(0f)
    private val inlineSizeState = mutableStateOf(IntSize.Zero)
    // Null, not a fabricated off-screen sentinel (this used to be
    // Float.MAX_VALUE). The old sentinel relied entirely on an accident of
    // arithmetic: TitleFlightOverlay's `.offset{}` lerp and its chrome
    // Box's `translationY` both feed this straight into `roundToInt()`,
    // which happens to saturate a MAX_VALUE-scaled result to Int.MAX_VALUE
    // -- billions of px below the viewport, i.e. invisible, but only
    // because that particular saturation behaviour holds for that
    // particular lerp. Any future read of this value that didn't go through
    // that exact math (a hit-test, a different interpolation, a debug
    // overlay) would have seen a real, enormous-but-finite number instead
    // of "no value yet". Nullable makes "nothing has ever reported a real
    // position" an explicit state TitleFlightOverlay checks for and fades
    // itself out on, rather than an invisible-by-coincidence magic number.
    private var inlineYState by mutableStateOf<Float?>(null)
    // The SAME scrollValuePx() reading, captured at the exact moment
    // inlineYState was last set -- see `inlinePos`'s own doc for why this
    // exists. Every onPositioned/onSettled report is itself already the
    // ground truth for "where is the anchor right now", scroll included;
    // this baseline is what lets `inlinePos` correct that report for
    // scrolling that's happened SINCE, without needing another report.
    private var baselineScrollPx by mutableFloatStateOf(0f)

    // Bumped on every real onPositioned/onSettled report -- no external
    // reader left (an earlier round's readiness gate that consumed this via
    // a public reportGeneration getter was reworked away; see git history),
    // kept as internal bookkeeping in case a future reader needs to detect
    // "has this shared instance received a report since some earlier point"
    // again without re-deriving it from scratch.
    private var reportGen by mutableIntStateOf(0)

    fun onPositioned(offset: Offset, size: IntSize = IntSize.Zero) {
        inlineX = offset.x
        inlineYState = offset.y
        if (size != IntSize.Zero) {
            inlineSizeState.value = size
        }
        baselineScrollPx = scrollValuePx()
        // Hysteresis computed HERE, not inside `docked`'s derivedStateOf
        // below -- onPositioned is the one real event source for inlineY
        // changes, called exactly once per genuine position report.
        // derivedStateOf's calculation lambda, by contrast, is documented to
        // be re-runnable more than once per real change (speculative
        // recomposition, a discarded/rolled-back snapshot) without that
        // being visible to callers -- fine for a pure read, but `dockedNow`
        // used to be mutated INSIDE that lambda as a side effect, so a
        // discarded/speculative run could still permanently advance the
        // hysteresis baseline a real, committed run never asked for.
        dockedNow = if (dockedNow) offset.y < topInsetPx + hysteresisPx else offset.y < topInsetPx
        reportGen++
    }

    /** Like [onPositioned], but for the FIRST report from a page that just
     *  became the settled one on THIS same shared instance -- bypasses the
     *  hysteresis carried over from whichever page was settled before it.
     *  That hysteresis exists to stop ONE title's own position from
     *  jittering across the threshold on sub-pixel scroll noise; it says
     *  nothing about a totally different page's title, whose own inlineY
     *  can easily land inside the (small, [hysteresisPx]-wide) band without
     *  ever having crossed anything itself. Routed through the biased
     *  `if (dockedNow) ... else ...` branch in [onPositioned], that could
     *  make a freshly-settled page render as docked (or stay undocked)
     *  purely because of where the PREVIOUS page happened to leave the flag
     *  -- a real, reproducible wrong-state bug once this class started
     *  being shared continuously across page switches instead of being
     *  rebuilt fresh per page. */
    fun onSettled(offset: Offset, size: IntSize = IntSize.Zero) {
        inlineX = offset.x
        inlineYState = offset.y
        if (size != IntSize.Zero) {
            inlineSizeState.value = size
        }
        baselineScrollPx = scrollValuePx()
        dockedNow = offset.y < topInsetPx
        reportGen++
    }

    /** The real inline title's live root position -- always laid out, but
     *  drawn invisibly (see [TitleFlightOverlay]'s own doc for why); read
     *  only from deferred draw/layout lambdas so watching it doesn't itself
     *  cause recomposition. Null until the first real [onPositioned]/
     *  [onSettled] report lands -- see [inlineYState]'s own doc for why
     *  that's a deliberate "nothing to show yet" rather than an off-screen
     *  sentinel.
     *
     *  Corrected for scroll that's happened SINCE the last report, not just
     *  the report's own raw Y -- `onPositioned`/`onSettled` only fire once
     *  per real layout pass (Compose's own `onGloballyPositioned` callbacks
     *  are dispatched in a post-layout sweep, strictly after every sibling
     *  in the tree -- including [TitleFlightOverlay]'s own overlay Box --
     *  has ALREADY been placed for that same frame), so a read of the raw
     *  report during continuous fast scrolling was always exactly one
     *  frame's worth of scroll delta stale: the real hero card painted with
     *  THIS frame's true scroll offset, while the flying Text painted with
     *  the offset the anchor had on the frame before. That's what made the
     *  name visibly trail the card it's supposed to be "planted" on rather
     *  than track it 1:1. [scrollValuePx] is instead a live, synchronous
     *  field read (the same `ScrollState.value` the real Column places
     *  itself against) available in the SAME placement phase this is read
     *  from -- so subtracting how much scroll has moved since the last
     *  report closes the gap completely, with no callback/dispatch-order
     *  dependency left in the per-frame scroll path at all. */
    override val inlinePos: androidx.compose.runtime.State<Offset?> =
        derivedStateOf {
            inlineYState?.let { y -> Offset(inlineX, y - (scrollValuePx() - baselineScrollPx)) }
        }

    override val inlineSize: androidx.compose.runtime.State<IntSize> = inlineSizeState

    /** Has the inline title's top edge crossed the status bar -- with
     *  hysteresis, not one bare threshold. A single cutoff meant a scroll
     *  position that happened to settle (a fling's last pixel, a hand
     *  resting mid-drag) exactly at the line could flip `docked` back and
     *  forth on sub-pixel jitter, restarting TitleFlightOverlay's spring
     *  every time -- the flying Text visibly stuttering in place instead of
     *  either committing to dock or staying put. [hysteresisPx] separates
     *  the entering and leaving lines so a real crossing still reacts
     *  immediately, but resting near the boundary can't retrigger it. The
     *  one recomposition-triggering read [TitleFlightOverlay] does. */
    // Plain snapshot state, not derivedStateOf -- dockedNow is now written
    // exactly once per real onPositioned event above, so there is no
    // separate pure "calculation" left to derive; this IS the value.
    private val dockedNowState = mutableStateOf(false)
    private var dockedNow: Boolean
        get() = dockedNowState.value
        set(value) { dockedNowState.value = value }
    override val docked: androidx.compose.runtime.State<Boolean> = dockedNowState

    /** What colour the inline title would be drawing itself in right now,
     *  reported the same way position is -- [HeroHeader]'s title morphs
     *  colour as its hero photo expands/collapses (see `heroTitleColorNow`),
     *  and the flying Text is the only thing actually painting that colour
     *  any more, so it needs the live value, not a fixed guess. Settings/
     *  ExpandedCar report a constant colour here since they have no such
     *  morph. */
    override var color by mutableStateOf(Color.Unspecified)

    /** See [TitleFlightSource.titleScale]'s own doc. Written every frame of
     *  [HeroHeader]'s own grow/shrink spring, the same way [color] tracks
     *  its colour morph. */
    override var titleScale by mutableFloatStateOf(1f)

    /**
     * Optional Y-override for the INLINE (not docked) end of the flight: the
     * hero sets this to the collapsed numbers row's own centre (in the same
     * root coordinates inlinePos reports) while the hero is collapsed, so the
     * flying NAME lands on the numbers' line rather than the title slot's
     * line. Null (every surface that doesn't set it) = current behaviour.
     * Called from the overlay's deferred placement, so the value arrives at
     * draw time with no extra recomposition.
     */
    override var inlineYOverride: () -> Float? = { null }
}

/** Null (the default) everywhere except inside [VehicleDetailContent]. */
internal val LocalHeroTitleFlight = compositionLocalOf<HeroTitleFlight?> { null }

/**
 * What GarageScreen's own single-car-per-page pager needs from whichever
 * page is currently SETTLED, to render ONE shared [TitleFlightOverlay] (name +
 * page count) instead of each page keeping its own independent copy. Passed
 * into [VehicleDetailContent]/`SettingsScreen`'s own `hoisted` param ONLY
 * for the settled page (see the call site's own guard) -- every other
 * in-composition page (the pre-composed neighbour) gets null and renders
 * nothing of its own here, since the one shared badge already covers
 * whichever page just settled.
 */
internal class HoistedIdentityFlight(
    val flight: HeroTitleFlight,
    // Runs the settled page's own "scroll back to top" -- invoked when the
    // hoisted badge itself is tapped.
    val scrollToTop: MutableState<(suspend () -> Unit)?>,
)

/**
 * [VehicleDetailContent]'s own (non-hoisted) badge state, bundled so it can
 * be built inside an `if (hoisted == null)` branch as one value -- see that
 * composable's own `local` var. Just the [HeroTitleFlight] itself -- it now
 * owns everything [TitleFlightOverlay] needs.
 */
internal class LocalNamePillState(
    val flight: HeroTitleFlight,
)

/**
 * The floating name header: the car's name (or "Settings") is drawn exactly
 * ONCE, as one [Text] that lives ONLY here, in this overlay -- never inside
 * the scrolling hero card. What LOOKS like the inline hero title is really
 * this same overlay Text, positioned to sit exactly where the hero card's
 * own (permanently invisible -- see [HeroHeader]'s `titleModifier`) title
 * slot is; when the real slot scrolls its top edge above the status bar
 * ([HeroTitleFlight.docked]), this one Text springs from there to the
 * corner pill instead, with a slight overshoot-and-settle bounce. There is
 * no crossfade anywhere in this path, because there is only ever one Text.
 *
 * This is the third design this feature has had (see git history): a live
 * per-frame CLONE that mirrored the real title's colour/size/position and
 * hid the original (flashed constantly -- two copies kept in sync by hand);
 * then a fully independent fixed-corner pill with no visible connection to
 * the title at all (stable, but looked like two unrelated things handing
 * off); this version keeps the independent-pill design's safety property --
 * nothing here ever duplicates the OTHER title's styling, because there is
 * no other title being drawn -- while still being one continuously
 * traceable object, because it's the literal same Text the whole time.
 *
 * [dockedAnchor] is measured, not computed: an identical, invisible copy of
 * [content] sits inside the real glass pill (same padding/row/alignment the
 * visible pill chrome uses) purely to report where the visible Text should
 * land once docked -- so the landing spot is exactly right for whatever the
 * pill's real padding/icon/font-scale happens to be, with no hardcoded pixel
 * math to keep in sync by hand if that chrome ever changes.
 *
 * Fixed 48dp-min height and the same glass fill/ring/shadow/rim as
 * [FloatingIcon] for the pill chrome, so it reads as one more piece of the
 * app's floating chrome once docked. [maxWidth] bounds the Text so a long
 * name ellipsizes correctly against whichever state (inline or docked) is
 * narrower, instead of running under the buttons or off the edge.
 */
@Composable
internal fun BoxScope.TitleFlightOverlay(
    flight: TitleFlightSource,
    cornerX: Dp,
    cornerY: Dp,
    reserveEnd: Dp,
    maxWidth: Dp,
    /** Forces a fixed colour (Settings/hoisted-on-Settings-slot have no
     *  photo to morph against, so there's nothing to read from [flight]).
     *  Null -- every other surface -- reads [flight]'s own live colour
     *  instead, resolved INSIDE this function rather than by the caller;
     *  see the read site below for why that's load-bearing, not stylistic. */
    textColorOverride: Color? = null,
    onClick: () -> Unit,
    /** Extra content shown ONLY once docked, alongside the flying text
     *  inside the pill (the hoisted badge's page-count label). */
    extraContent: (@Composable RowScope.() -> Unit)? = null,
    /** What the invisible docked-position ANCHOR renders -- must stay a
     *  plain, non-animated composable (defaults to [content] for every
     *  surface but the hoisted one). [content] itself is composed TWICE
     *  (once invisibly for measurement, once visibly for the flight), which
     *  is harmless for a plain `Text` but would run two independent copies
     *  of an `AnimatedContent`'s own internal transition state if [content]
     *  carried one -- exactly the "two things kept in sync by hand can drift
     *  apart" failure mode this whole file exists to avoid. The hoisted
     *  badge (the one surface whose flying text DOES wrap an AnimatedContent,
     *  for its page-switch crossfade) passes a plain, un-animated Text here
     *  instead, so only ONE AnimatedContent instance -- the visible one --
     *  ever exists. */
    measureContent: (@Composable () -> Unit)? = null,
    /** Reports the REAL visible flying Text's current root-coordinate,
     *  post-transform bounds (position AND size, after scale/translation)
     *  on every layout pass -- null once it has nothing to report (before
     *  either anchor exists, mirrors the alpha-gate below). Purely an
     *  outward report for a sibling overlay (the page-dot indicator) to
     *  test for collision against; nothing in here ever reads it back. See
     *  [PagerDots]' own call site for the consumer. */
    onNameBoundsChanged: ((Rect?) -> Unit)? = null,
    /** True ONLY for a perPage>1 grid column -- see `containerOrigin`'s own
     *  doc below for why this must default to false and stay opt-in rather
     *  than running unconditionally for every caller. A grid column's
     *  hosting container is genuinely offset from the composition root by
     *  every prior column's width; the hoisted single-car badge,
     *  VehicleDetailContent's own per-page badge, and ExpandedCar's badge
     *  are not, and paid for (and were destabilized by) a correction they
     *  never needed when this used to run for all of them. */
    containerRelative: Boolean = false,
    /** Reports this overlay's resting dock state (the argument) EVERY time
     *  it genuinely arrives at rest -- i.e. the instant a spring finishes,
     *  in EITHER direction, not just docked-arriving. The ONLY moments
     *  it's actually safe for a caller to swap this badge out for a
     *  different instance, or to stop feeding it live position updates,
     *  without a visible jump.
     *
     *  Docking direction: VehicleDetailContent uses this (not the raw,
     *  unsettled `flight.docked`) to decide when to hand its per-page badge
     *  off to GarageScreen's shared hoisted one -- reporting the instant
     *  `docked` flips true used to fire the hand-off mid-spring, before this
     *  overlay's own animation had actually arrived at the corner, reading
     *  as the transition snapping partway through instead of gliding all
     *  the way in.
     *
     *  Undocking direction: reporting the instant `docked` flips false (the
     *  raw flag) used to fire the hand-off BACK to the per-page local badge
     *  while this (shared, about-to-be-abandoned) instance's own spring was
     *  still mid-flight -- and the moment the hand-off happens, this
     *  instance stops receiving live position reports at all (the ambient
     *  `LocalHeroTitleFlight` switches to the local flight instead), so its
     *  still-running exit spring kept animating toward a now-FROZEN, stale
     *  target while the freshly-visible local badge tracked live, still-
     *  moving scroll coordinates -- the two visibly diverging for the
     *  ~160ms crossfade window, reading as the pill stuttering back toward
     *  the pebble instead of gliding.
     *
     *  Fires unconditionally on both directions (not just "became true")
     *  precisely so callers can react the same way to either -- see the two
     *  call sites' own doc for how each uses it. */
    onSettledChanged: ((Boolean) -> Unit)? = null,
    /** The flying text itself. A plain `Text(name, ...)` for every surface
     *  but the hoisted one, which wraps its own `AnimatedContent` for the
     *  page-switch crossfade -- that crossfade is a SEPARATE, orthogonal
     *  concern (which car's name) from this whole file's dock/undock
     *  concern (where the name sits), so it stays fully inside this slot
     *  rather than this function needing to know about it at all. */
    content: @Composable () -> Unit,
) {
    // Clear any stale bounds report the instant this overlay leaves
    // composition (e.g. the hoisted single-car badge unmounting when its
    // page un-docks, or an ExpandedCar page pager throws away its
    // off-screen neighbour) -- otherwise PagerDots keeps dodging/hiding
    // against a Rect belonging to a name overlay that no longer exists.
    DisposableEffect(onNameBoundsChanged) {
        onDispose { onNameBoundsChanged?.invoke(null) }
    }
    val docked by flight.docked
    val haptics = LocalHaptics.current
    val shape = RoundedCornerShape(50)
    val density = LocalDensity.current
    // 0 = resting inline, 1 = resting docked -- the ONE number driving the
    // flying Text's position (lerp between the two measured anchors), the
    // pill chrome's own alpha, AND (see pillContainer below) the pill
    // chrome's own size and position, so none of them can visibly outrun
    // each other; see this function's own doc for why a spring (not a
    // scroll-tied fraction) is deliberate here.
    val progress = remember { Animatable(0f) }
    // Set true only while a spring is actually running -- see `active` below,
    // which uses it to skip composing the expensive glass chrome (shadow,
    // ring, frosted rim) for the vast majority of the time nothing is
    // docked or mid-transition. That chrome used to be composed
    // unconditionally on every surface hosting a badge, all the time --
    // cheap for any ONE of them, but real cost stacked up across a grid of
    // simultaneously-visible cards, which is what was reading as the whole
    // app dragging.
    var transitioning by remember { mutableStateOf(false) }
    // Page-switch handling (the hoisted badge's own car-to-car swipe) used
    // to live one level up, wrapping this whole function in its own
    // `AnimatedContent` -- several rounds of that never actually converged
    // (see git history for the saga). The hoisted call site now hands this
    // function the SAME shared `flight` object continuously, never swapping
    // it on a page switch, so a dock-state change caused by switching cars
    // and a dock-state change caused by scrolling the current one are no
    // longer different code paths at all -- both just flip `docked` on the
    // one object this function already knows how to spring to. `mounted`
    // still exists for the surfaces that DO get a fresh `flight` object
    // occasionally (a `remember(topInsetPx)` rebuild on a rotation, say):
    // it makes that instance's FIRST composition snap straight to whatever
    // `docked` already is instead of visibly springing to it.
    var mounted by remember(flight) { mutableStateOf(false) }
    // Latches `transitioning` true the INSTANT `docked` is observed to have
    // actually changed (not just "whenever docked is true") -- synchronously,
    // right here in composition, not inside the LaunchedEffect coroutine
    // below. That coroutine only starts running once THIS composition's
    // effects are applied, which is after this recomposition (the one that
    // first sees the new `docked` value) has already committed -- leaving a
    // real window where `docked` has flipped but `transitioning` is still
    // the OLD (false) value, exactly the frame `settled` (see its own doc)
    // is trying to tell callers NOT to treat as arrived. Left as a coroutine-
    // only write, that gap made `settled` spuriously true for one frame the
    // instant docking STARTED, not once it finished -- firing the hand-off
    // before the spring had moved at all, reading as no transition playing
    // whatsoever rather than a mid-flight snap.
    var lastDocked by remember { mutableStateOf(docked) }
    if (lastDocked != docked) {
        transitioning = true
        lastDocked = docked
    }
    LaunchedEffect(docked, flight) {
        if (!mounted) {
            mounted = true
            transitioning = false
            progress.snapTo(if (docked) 1f else 0f)
            return@LaunchedEffect
        }
        transitioning = true
        try {
            progress.animateTo(
                if (docked) 1f else 0f,
                // MediumBouncy arriving -- overshoot-and-settle, like the text is
                // being caught by the corner. Undamped leaving, the same
                // arriving-is-slower-than-leaving asymmetry every other spring
                // pair in this file already uses.
                spring(
                    dampingRatio = if (docked) Spring.DampingRatioMediumBouncy else Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        } finally {
            transitioning = false
        }
    }
    // See `onSettledChanged`'s own doc -- fires the RESTING dock state
    // (`docked`'s own value at the moment `transitioning` clears) every
    // time a spring genuinely finishes, in either direction, not just
    // "became docked". `docked` itself flips the INSTANT the raw scroll
    // threshold crosses, well before this overlay's own spring above has
    // actually arrived anywhere -- this is what actually being at rest
    // means.
    LaunchedEffect(transitioning, docked) {
        if (!transitioning) onSettledChanged?.invoke(docked)
    }
    // Captures this overlay's own hosting container's root-window position,
    // so the root-absolute anchors below (HeroHeader's inline title report
    // at line ~6007, and this composable's own docked-anchor measurer just
    // below -- both via positionInRoot()) can be converted into a delta
    // relative to THIS container before feeding Modifier.offset/graphicsLayer
    // translation. Both of those only ever interpret their argument as a
    // delta from the composable's own already-placed local position, never
    // as an absolute screen coordinate.
    //
    // ONLY built when `containerRelative` is true -- i.e. only for a grid
    // column, whose container really is offset from root by the cumulative
    // width of every prior column, so without this subtraction that offset
    // was being counted TWICE and every column past the first rendered
    // off-screen. This used to run unconditionally for all THREE of this
    // function's calling contexts (the hoisted single-car badge,
    // VehicleDetailContent's own per-page badge, and ExpandedCar), on the
    // assumption the other two always sit at composition root so the
    // subtraction there is a harmless no-op. That assumption was never
    // actually verified for VehicleDetailContent's own badge -- its real
    // container is `Refreshable`'s Box, several layout levels deep inside a
    // HorizontalPager page, not literally the composition root -- and even
    // where it WAS a true no-op, adding a THIRD independently-remembered,
    // independently-updated Offset (this one) that the flying Text's
    // position now depends on meant three separate onGloballyPositioned
    // callbacks had to land in the same recomposition for the position to
    // be fully correct on any given frame, instead of the previous two.
    // That's exactly the kind of one-frame skew that reads as a stutter on
    // a spring-driven transition whose whole point is sub-pixel smoothness
    // -- which is what broke the previously-seamless hero-card-to-pill
    // transition on the two call sites that never needed this correction
    // at all. Scoped back to only the one caller that actually needs it.
    val containerOrigin = remember { mutableStateOf(Offset.Zero) }
    if (containerRelative) {
        Box(Modifier.matchParentSize().onGloballyPositioned { containerOrigin.value = it.positionInRoot() })
    }
    val dockedAnchor = remember { mutableStateOf<Offset?>(null) }
    val dockedSize = remember { mutableStateOf<IntSize?>(null) }
    // The flying Text's own measured size -- its glyphs are centred inside a
    // FULL-SIZE (headline) text box, so pinning the BOX TOP to the anchor's
    // box top made the name sit low the moment the collapsed anchor scaled
    // down to a shorter box (the % / mi&km readout still centred in the same
    // row). Both placement lambdas below centre the flying text against the
    // ANCHOR's vertical centre line instead; this size is the offset between
    // the two boxes' tops that centre alignment has to make up.
    var flyingSize by remember { mutableStateOf(IntSize.Zero) }
    // The one recomposition-triggering read gating the expensive chrome
    // below: composed only while docked, or while a spring is actively
    // carrying it there or back. `dockedAnchor` also has to be known already
    // -- composing the chrome before its own first measurement arrived was
    // the OTHER bug this round: an empty, near-square Row (nothing sizing
    // it yet) got its full pill-shaped clip computed against that wrong,
    // tiny size, then visibly re-clipped into the real pill shape the
    // instant the measurement landed -- reading as the shadow snapping from
    // square to pill partway through, not just fading in late.
    // NOT also gated on `dockedAnchor.value != null` any more -- see the
    // chrome Box's own alpha computation below for why. That extra
    // requirement used to mean the whole chrome subtree stayed UNCOMPOSED
    // (not just invisible) until the first real measurement landed, which
    // was fine for a badge that's continuously alive from idle -- but
    // GarageScreen's shared hoisted badge is a FRESH mount on every
    // page-switch hand-off (see its own doc), discarding this exact
    // `dockedAnchor` along with everything else. On such a remount, `docked`
    // can already be true (read straight off the reused shared flight
    // object) for one or more frames before `dockedAnchor` reports again --
    // during that window the subtree wasn't composed at all, then popped in
    // at full opacity the instant it finally was, reading as the shadow
    // flickering/snapping specifically on a fast page-switch between a
    // static and a floating car, as opposed to the smooth scroll-driven
    // dock case this was originally tuned against.
    val active = docked || transitioning
    val measure = measureContent ?: content
    // A SEPARATE, permanently static (never transformed) measuring copy --
    // exists purely to answer "where does the pill rest, and how big is it"
    // with a real layout pass instead of guessed pixel math, the same reason
    // the original single-Box design measured a docked anchor at all. It has
    // to be a distinct node from the animated pill below: LayoutCoordinates
    // report position/size AFTER ancestor graphicsLayer transforms are
    // applied, so if this measurer lived inside the animated pill (which
    // this round makes scale and slide, not just fade), its own reported
    // numbers would be mid-animation snapshots instead of the true resting
    // values dockedAnchor/dockedSize are supposed to be.
    Box(
        Modifier
            .align(Alignment.TopStart)
            .alpha(0f)
            .padding(start = cornerX, top = cornerY, end = reserveEnd),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .widthIn(max = maxWidth)
                    .onGloballyPositioned {
                        dockedAnchor.value = it.positionInRoot()
                        dockedSize.value = it.size
                    }
                    // Position-measuring only -- alpha alone doesn't remove a node
                    // from the accessibility tree, so without this a screen reader
                    // announced the name here AND on the real flying Text below
                    // (and, before this same fix on the inline slot, a third time
                    // there too): one name, one announcement.
                    .clearAndSetSemantics {},
            ) { measure() }
            extraContent?.invoke(this)
        }
    }
    // The pill's actual glass chrome -- shape, shadow, ring, background --
    // now follows the flying text instead of sitting fully-formed at the
    // corner and just fading in around it: it grows from a small pill near
    // the inline anchor up to full size at the docked position, in lockstep
    // with `progress`, via a `graphicsLayer` transform (translation + scale)
    // rather than a real layout-size animation, specifically so it can't
    // feed back into dockedAnchor/dockedSize above (see that Box's own doc).
    // This is also what fixes the shadow reading like it "snaps in": a
    // shadow around a shape that's ALSO growing into place reads as one
    // continuous arrival; a full-size shadow that only fades its opacity
    // reads as popping in the instant alpha clears whatever the eye's
    // threshold for "there" is, even though the fade itself was smooth.
    //
    // Gated on `active`: skip composing this whole subtree -- the
    // background/ambientRing/dropShadow/frostedRim/clip chain, all real
    // draw work -- entirely while there's nothing docked and nothing
    // transitioning, rather than composing it always and just animating
    // its alpha to 0. One badge paying that cost while idle is nothing; a
    // grid of several simultaneously-visible cards each paying it, all the
    // time, is what was dragging the rest of the app down. `active` no
    // longer ALSO requires `dockedAnchor.value != null` to start composing
    // (see that val's own doc) -- so this Box can now be composed for one or
    // more frames before its first real measurement lands. Kept invisible
    // for exactly that window by the alpha computation just below, rather
    // than by staying unmounted, so a fresh mount (a page-switch hand-off)
    // fades this subtree in once real geometry is known instead of popping
    // it in at full opacity the instant it finally is.
    if (active) {
    Box(
        Modifier
            .align(Alignment.TopStart)
            .graphicsLayer {
                val p = progress.value
                val clamped = p.coerceIn(0f, 1f)
                // 0f, not `clamped`, until dockedAnchor has actually reported
                // -- this is the SAME guarantee the old dockedAnchor-gated
                // `active` used to provide (an empty, wrongly-sized Row never
                // visibly clips into its real pill shape), just expressed as
                // an invisible-but-composed frame instead of an unmounted
                // one, so the eventual reveal is a fade instead of a pop.
                alpha = if (dockedAnchor.value == null) 0f else clamped
                // 0.55 start, not 0f -- a shape growing from nothing looks like
                // it's materializing out of a point; starting partway there
                // reads as "arriving", not "being born".
                val scale = 0.55f + 0.45f * clamped
                scaleX = scale
                scaleY = scale
                // Grown from the LEADING edge (where the text itself sits), not
                // the centre -- a centre-anchored scale would visibly shift the
                // text's own left edge sideways as the pill grows, fighting the
                // text's own independently-lerped position.
                transformOrigin = TransformOrigin(0f, 0.5f)
                // `active` (which gates this whole Box) already requires
                // `docked || transitioning`, and `docked` can only ever
                // have become true via a real onPositioned/onSettled report
                // -- so by the time this composes, flight.inlinePos.value
                // is never actually null. The `?: dockedAnchor.value ?:
                // Offset.Zero` fallback exists only to satisfy the compiler
                // now that inlinePos is nullable (see that property's own
                // doc); it should never be the branch actually taken here.
                // Both anchors are root-absolute (positionInRoot()); subtract this
                // overlay's own container origin (see containerOrigin's own doc
                // above) before using them as an offset delta -- graphicsLayer
                // translation is relative to this Box's own placed position, not
                // an absolute screen coordinate.
                val origin = containerOrigin.value
                val inline = (flight.inlinePos.value ?: dockedAnchor.value ?: origin) - origin
                val target = (dockedAnchor.value ?: (flight.inlinePos.value ?: origin)) - origin
                // Same centre-line alignment the flying text uses (see that
                // Box's offset lambda): the pill chrome grows around the text,
                // so it has to travel along the SAME centred line, not the
                // anchor's box-top line.
                val anchorH = flight.inlineSize.value.height.toFloat()
                val flyH = flyingSize.height.toFloat()
                val inlineYc = flight.inlineYOverride()?.let { it - flyH / 2f }
                    ?: (inline.y + (anchorH - flyH) / 2f)
                val targetYc = target.y + (dockedSize.value?.height ?: if (flyH > 0f) flyH.toInt() else 0) / 2f - flyH / 2f
                // Same start-at-inline, land-at-target lerp the flying text uses,
                // expressed as a delta from this Box's own natural (padding-only)
                // resting position -- unclamped `p`, not `clamped`, so the
                // container can overshoot right along with the bouncy spring
                // instead of the shape looking stiffer than the text it holds.
                translationX = (inline.x - target.x) * (1f - p)
                translationY = (inlineYc - targetYc) * (1f - p)
            }
            .padding(start = cornerX, top = cornerY, end = reserveEnd),
    ) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()), shape)
                .ambientRing(shape)
                .dropShadow(shape)
                // Chrome before clip -- dropShadow's blur bleeds outside the
                // shape by design; clip only bounds the ripple below.
                .frostedRim(shape)
                .clip(shape)
                .clickable(enabled = docked) { haptics?.click(); onClick() }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // An invisible spacer, not a second copy of the text -- sized to
            // match dockedSize (measured above) so the pill's own width is
            // correct for the name it holds without rendering that name a
            // second time here (the one visible copy is the flying Text below,
            // drawn on top of this same region once docked).
            Box(Modifier.size(dockedSize.value?.let { with(density) { DpSize(it.width.toDp(), it.height.toDp()) } } ?: DpSize.Zero))
            extraContent?.invoke(this)
        }
    }
    }
    // The one visible Text. Positioned by lerping between the inline slot's
    // live position and the docked slot's measured position -- at progress
    // 0 this sits exactly on the (invisible) inline title, at progress 1
    // exactly on the docked measuring copy's position, and every value
    // between while the spring is in flight -- same lerp the pill chrome
    // above uses for its own translation, so the text always stays exactly
    // where the pill's growing shape says it should be.
    Box(
        Modifier
            .align(Alignment.TopStart)
            .widthIn(max = maxWidth)
            .onSizeChanged { flyingSize = it }
            .offset {
                // Falls back to the docked anchor, then to the origin, only
                // to give roundToInt() something finite to chew on while
                // `inline` is null -- the alpha gate below (not this
                // fallback) is what actually hides the badge for that
                // window, so which fallback value is used here doesn't
                // matter visually.
                // See the graphicsLayer block above for why containerOrigin has
                // to be subtracted here too -- Modifier.offset{} is the same
                // kind of local delta, not an absolute screen coordinate.
                val origin = containerOrigin.value
                val inline = (flight.inlinePos.value ?: dockedAnchor.value ?: origin) - origin
                val target = (dockedAnchor.value ?: (flight.inlinePos.value ?: origin)) - origin
                val p = progress.value
                // Centred against the anchor's vertical centre line, not pinned
                // box-top-to-box-top: the flying name's glyphs are centred in a
                // FULL-SIZE headline text box, while the collapsed anchor's own
                // box is that height scaled to the row's real height -- top-
                // pinning made the name sit below the % / mi&km numbers that
                // share the collapsed row (reported: "floating name is in the
                // wrong place when the hero tile is collapsed"). Centre-to-
                // centre puts the name on the SAME line as that readout at any
                // anchor scale, and is a no-op once expanded (anchor height
                // equals the flying box's own => offset 0). Docked end is
                // unchanged: the docked measuring copy IS the same text, so its
                // centre line == the flying box's own centre.
                val anchorH = flight.inlineSize.value.height.toFloat()
                val flyH = flyingSize.height.toFloat()
                val inlineYc = flight.inlineYOverride()?.let { it - flyH / 2f }
                    ?: (inline.y + (anchorH - flyH) / 2f)
                val targetYc = target.y + (dockedSize.value?.height ?: 0) / 2f - flyH / 2f
                IntOffset(
                    (inline.x + (target.x - inline.x) * p).roundToInt(),
                    (inlineYc + (targetYc - inlineYc) * p).roundToInt(),
                )
            }
            // Mirrors PebbleShell's own hero title grow/shrink (see
            // flight.titleScale's own doc) -- but only while resting inline;
            // faded back to full size (1f) as `progress` approaches docked,
            // since a docked pill's size is that spring's own job (the
            // chrome Box above already scales 0.55->1 growing into dock),
            // not the now-scrolled-away hero photo's collapse state. Left-
            // anchored origin, same as PebbleShell's own copy, so the name
            // grows from its own start position instead of drifting sideways.
            .graphicsLayer {
                // Spelled out, not lerp() -- this file doesn't have the Float
                // overload of lerp in scope (see PebbleShell's own identical
                // note on its copy of this same interpolation).
                val p = progress.value.coerceIn(0f, 1f)
                val scale = flight.titleScale + (1f - flight.titleScale) * p
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
                // Fade to fully invisible while NEITHER anchor has a real
                // value yet -- the one genuine construction-time/rotation-
                // time window where this badge has nothing real to show
                // (see HeroTitleFlight.inlineYState's own doc on why this
                // replaces a sentinel coordinate instead of layering on top
                // of one). Once either anchor reports, this is 1f and stays
                // that way -- there's no path back to "neither has ever
                // reported" for a live flight object.
                alpha = if (flight.inlinePos.value == null && dockedAnchor.value == null) 0f else 1f
            }
            // Reports this Text's real, post-transform screen bounds for
            // the page-dot collision dodge (see onNameBoundsChanged's own
            // doc). Chained AFTER the graphicsLayer above so the reported
            // bounds include that scale/offset -- callers need where the
            // name is actually PAINTED, not its untransformed layout slot.
            //
            // Gated on `active` (docked or mid-transition, same condition
            // the chrome Box above uses): a name can only ever climb high
            // enough to actually reach the dots' row right around that
            // window, and skipping the report the rest of the time avoids
            // real per-frame cost (a coordinate-space walk, a Rect
            // allocation, a snapshot write) landing on every idle relayout.
            // That cost used to run unconditionally, including on the busy
            // frames a badge is mid-spring INTO the corner -- competing
            // with the spring's own work on the UI thread for exactly the
            // surface (VehicleDetailContent's own per-page badge) whose
            // transition needs to stay smooth, which is what was reading
            // as a stutter/snap rather than a glide.
            .onGloballyPositioned { if (active) onNameBoundsChanged?.invoke(it.boundsInRoot()) },
    ) {
        // Read HERE, inside TitleFlightOverlay's own (small) recompose scope
        // -- not by the caller, as a call-site argument expression, which is
        // what this looked like before. flight.color is a plain mutableStateOf
        // written every frame of HeroHeader's photo-expand spring (see that
        // property's own doc); reading it as an argument to THIS call ties
        // the ENTIRE calling composable (VehicleDetailContent, ExpandedCar,
        // GarageScreen's whole hoisted-badge scope) to recompose on every one
        // of those frames, not just the small text-colour consumer that
        // actually needs the value. Resolving it in this function's own body
        // instead means only this function reruns.
        val resolvedColor = textColorOverride ?: flight.color.takeOrElseOnSurface()
        CompositionLocalProvider(LocalContentColor provides resolvedColor) { content() }
    }
}
