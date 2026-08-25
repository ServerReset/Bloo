@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Cover.kt's camera-bump-beside cluster, peeled out of Cover.kt (which kept the
 * tile/tile-face chrome): MAX_CUTOUT_FRACTION, EdgeDp and cutoutClearanceDp, the
 * CoverBand data class with COVER_BAND_MIN_W/H and coverCutoutBand, the
 * CoverBandSearchDock footprint, and the adaptive CoverScaffold itself.
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
import com.bloo.uicommon.ReorderColumn
import com.bloo.uicommon.LocalReorderActive
import com.bloo.uicommon.coldStartIntroPlayed
import com.bloo.uicommon.animatePlacement

/** Most of one dimension a display cutout may ever claim as clearance. A real
 *  notch or lens is a few percent; anything demanding more than this is a
 *  camera island being measured as though it were a punch-hole, and honouring
 *  it costs more screen than it protects. See cutoutClearanceDp. */
internal const val MAX_CUTOUT_FRACTION = 0.22f

/**
 * Per-edge camera-bump clearance in dp, computed from the display cutout rects for
 * ANY bump position. Returns how much each edge must be reserved so content flows
 * AROUND the punch-hole/bump instead of under it: (start, top, end, bottom) in dp,
 * zeros pre-API-28 or with no cutout.
 *
 * Why this exists alongside the native WindowInsets.displayCutout padding: on
 * Samsung flip COVER displays the OS frequently reports the front camera via
 * displayCutout.boundingRects (which is why the decorative ring positions
 * correctly) but exposes ZERO safeInset/displayCutout WINDOW insets for it — so
 * windowInsetsPadding(displayCutout) alone reserves nothing and content sits under
 * the bump (observed on the user's device). This reads the rects directly (each
 * call, not a remember(view) snapshot, so it reflects insets once dispatched).
 *
 * CRITICAL for a CORNER bump: PaddingValues insets a WHOLE edge, so reserving both
 * edges a corner bump touches removes an L-shaped chunk from two full sides — for a
 * bottom-right bump that's a full-HEIGHT right strip ~45% of the width, which
 * crushed every tile's content into the left half (observed: values wrapping
 * "Locke/d"/"Runnin/g", range clipped to "26…"). A corner bump only occludes its
 * corner, so this reserves only the edge with the SMALLER intrusion — for a
 * bottom-right bump that's the bump's HEIGHT (small), pushing content up just
 * enough to clear it while reclaiming the full width. A true single-edge cutout
 * still pads that one edge. Only a bump within [edgeBandPx] of an edge counts.
 */
internal data class EdgeDp(val start: Float, val top: Float, val end: Float, val bottom: Float)

@Composable
internal fun cutoutClearanceDp(): EdgeDp {
    val view = LocalView.current
    val density = LocalDensity.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return EdgeDp(0f, 0f, 0f, 0f)
    val cutout = view.rootWindowInsets?.displayCutout ?: return EdgeDp(0f, 0f, 0f, 0f)
    val vw = view.width
    val vh = view.height
    if (vw <= 0 || vh <= 0) return EdgeDp(0f, 0f, 0f, 0f)
    val edgeBandPx = with(density) { 24.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
    for (r in cutout.boundingRects) {
        val hIntr: Float? = when {
            r.left <= edgeBandPx -> r.right + margin
            vw - r.right <= edgeBandPx -> (vw - r.left) + margin
            else -> null
        }
        val vIntr: Float? = when {
            r.top <= edgeBandPx -> r.bottom + margin
            vh - r.bottom <= edgeBandPx -> (vh - r.top) + margin
            else -> null
        }
        // Corner bump: reserve only the smaller intrusion so the opposite full
        // dimension is reclaimed. Edge notch: reserve that one edge.
        val hOnly = hIntr != null && (vIntr == null || hIntr <= vIntr)
        val vOnly = vIntr != null && (hIntr == null || vIntr < hIntr)
        if (hOnly && r.left <= edgeBandPx) left = maxOf(left, hIntr!!)
        if (hOnly && vw - r.right <= edgeBandPx) right = maxOf(right, hIntr!!)
        if (vOnly && r.top <= edgeBandPx) top = maxOf(top, vIntr!!)
        if (vOnly && vh - r.bottom <= edgeBandPx) bottom = maxOf(bottom, vIntr!!)
    }
    // Clamp each edge to a fraction of its own dimension.
    //
    // The arithmetic above assumes the cutout is a small punch-hole, so the
    // clearance is measured from the FAR side of the rect: `r.right + margin`,
    // or `(vw - r.left) + margin`. That's right for a lens and catastrophic
    // for a flip cover screen, which reports its whole camera ISLAND as one
    // bounding rect -- an island starting halfway across yields a clearance of
    // half the display, and the content gets squeezed into the strip that's
    // left with the rest sitting empty. Reported from a real device.
    //
    // Past this cap the rect isn't a notch to dodge, it's the panel's shape,
    // and the honest response is to use the space rather than surrender it:
    // anything the hardware genuinely occludes is already excluded from the
    // window the app was given.
    val maxH = vw * MAX_CUTOUT_FRACTION
    val maxV = vh * MAX_CUTOUT_FRACTION
    return with(density) {
        EdgeDp(
            left.coerceAtMost(maxH).toDp().value,
            top.coerceAtMost(maxV).toDp().value,
            right.coerceAtMost(maxH).toDp().value,
            bottom.coerceAtMost(maxV).toDp().value,
        )
    }
}

/**
 * The strip of screen BESIDE the camera island, when there is one worth using.
 *
 * A flip cover reports its whole camera island as one display-cutout rect
 * hugging an edge, and every layout here so far has responded by reserving
 * that entire edge -- the island's height across the full width. But the
 * island only occupies part of that band; the rest of it is real, lit,
 * unoccluded screen that nothing was allowed to use. On a screen this small
 * that is a meaningful fraction of it.
 *
 * Returns the larger of the two free segments (left of the island or right of
 * it) as an absolute rect in dp from the window's top-left, or null when there
 * is no cutout, when the cutout doesn't hug a horizontal edge, or when what's
 * beside it is too small to hold anything worth putting there. Null is the
 * normal answer on a phone; this is a cover-screen affordance.
 */
/** @property nearCameraAtEnd true when this band's END edge (right, in LTR) is
 *  the one touching the camera island -- the island sits at the OTHER end of
 *  the row from this band's own start, so grouping content flush against the
 *  band's end is what actually reads as "next to the camera". False means the
 *  island touches the band's START edge instead, so content should group
 *  there. Threaded through explicitly rather than re-derived by every caller,
 *  since it depends on which of the two free segments (left/right of the
 *  island) [coverCutoutBand] picked. */
internal data class CoverBand(
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val nearCameraAtEnd: Boolean,
)

/** Below these a band is a sliver: too short for a legible line of text, or
 *  too narrow for a name plus a tap target. */
internal const val COVER_BAND_MIN_W = 84f
internal const val COVER_BAND_MIN_H = 26f

@Composable
internal fun coverCutoutBand(): CoverBand? {
    val view = LocalView.current
    val density = LocalDensity.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val cutout = view.rootWindowInsets?.displayCutout ?: return null
    val vw = view.width
    val vh = view.height
    if (vw <= 0 || vh <= 0) return null
    val edgeBandPx = with(density) { 24.dp.toPx() }
    var best: CoverBand? = null
    for (r in cutout.boundingRects) {
        // Only a rect hugging the TOP or BOTTOM edge leaves a band beside it
        // that runs the other way. One hugging a side edge leaves a tall thin
        // column, which is not a place to put a name and a button.
        val hugsTop = r.top <= edgeBandPx
        val hugsBottom = vh - r.bottom <= edgeBandPx
        if (!hugsTop && !hugsBottom) continue
        val leftFree = r.left.toFloat()
        val rightFree = (vw - r.right).toFloat()
        val useLeft = leftFree >= rightFree
        val widthPx = if (useLeft) leftFree else rightFree
        val xPx = if (useLeft) 0f else r.right.toFloat()
        val band = with(density) {
            CoverBand(
                xDp = xPx.toDp().value,
                yDp = r.top.toFloat().toDp().value,
                widthDp = widthPx.toDp().value,
                heightDp = (r.bottom - r.top).toFloat().toDp().value,
                // useLeft picked the segment left of the island, so the
                // island -- and therefore "near camera" -- is at this band's
                // END (right); otherwise the island sits at its START.
                nearCameraAtEnd = useLeft,
            )
        }
        if (band.widthDp < COVER_BAND_MIN_W || band.heightDp < COVER_BAND_MIN_H) continue
        // Widest wins, on the theory that whatever we put there wants room.
        if (best == null || band.widthDp > best!!.widthDp) best = band
    }
    return best
}

/** Fixed footprint of the search dock this band reserves next to the camera
 *  island (see [CompactGarage]'s band Row and [SearchLayer]'s band-docked
 *  bubble) -- shared between the two files so the space one reserves is
 *  exactly the space the other draws into. */
internal val CoverBandSearchDock = 30.dp

/**
 * The adaptive cover-screen scaffold. Measures the REAL available space with
 * BoxWithConstraints and merges every inset source (nav bar, display cutout,
 * corner-safe camera-bump clearance, a small base gutter) into ONE contentPadding
 * per edge via max() — never additively — so a device that reports the bump both
 * as a window inset AND a boundingRect reserves it exactly once (this was the
 * "crammed into the left half" bug). Exposes [CoverMetrics] via [LocalCoverMetrics]
 * and clamps the subtree font scale so a huge system font can't overflow the tiny
 * face. The scaffold itself does NOT apply the padding — the tile region reads
 * metrics.contentPadding — so full-bleed siblings (rings, rail) stay full-bleed.
 */
@Composable
internal fun CoverScaffold(
    reserveRailGutter: Boolean,
    content: @Composable BoxWithConstraintsScope.(CoverMetrics) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val layoutDir = LocalLayoutDirection.current
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        // Gentle base gutter off the shorter side so a small cover doesn't lose a
        // fixed chunk; extra end gutter when the tile-scrubber rail is shown.
        val gutterScale = (minOf(wDp, hDp) / 300f).coerceIn(0.8f, 1.2f)
        val baseSide = 10f * gutterScale
        val baseEnd = (if (reserveRailGutter) 22f else 10f) * gutterScale
        val cut = cutoutClearanceDp()
        val sys = WindowInsets.navigationBars.union(WindowInsets.displayCutout).asPaddingValues()
        val sysStart = sys.calculateStartPadding(layoutDir).value
        val sysTop = sys.calculateTopPadding().value
        val sysEnd = sys.calculateEndPadding(layoutDir).value
        val sysBottom = sys.calculateBottomPadding().value
        // Single merged inset per edge — the whole point: max(), not sum.
        val padStart = maxOf(baseSide, cut.start, sysStart)
        val padTop = maxOf(baseSide, cut.top, sysTop)
        val padEnd = maxOf(baseEnd, cut.end, sysEnd)
        val padBottom = maxOf(12f * gutterScale, cut.bottom, sysBottom)
        val usableW = (wDp - padStart - padEnd).coerceAtLeast(0f)
        val usableH = (hDp - padTop - padBottom).coerceAtLeast(0f)
        val isTiny = minOf(usableW, usableH) < COVER_TINY_DP
        val metrics = CoverMetrics(
            widthDp = usableW,
            heightDp = usableH,
            isTiny = isTiny,
            contentPadding = PaddingValues(start = padStart.dp, top = padTop.dp, end = padEnd.dp, bottom = padBottom.dp),
        )
        // Coarse font-scale clamp for the whole cover subtree so a large system font
        // can't blow past the measured region (FittedText is the fine guard on top).
        val cappedFont = density.fontScale.coerceAtMost(if (isTiny) 1.15f else 1.3f)
        CompositionLocalProvider(
            LocalCoverMetrics provides metrics,
            LocalDensity provides Density(density.density, cappedFont),
        ) {
            content(metrics)
        }
    }
}
