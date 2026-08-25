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
import com.bloo.uicommon.ReorderColumn
import com.bloo.uicommon.LocalReorderActive
import com.bloo.uicommon.coldStartIntroPlayed
import com.bloo.uicommon.animatePlacement

/**
 * Measured, adaptive metrics for the cover-screen content region, provided by
 * [CoverScaffold] via [LocalCoverMetrics]. Tiles read this instead of guessing:
 * everything is derived from the REAL available space, so the cover adapts to any
 * cover size, aspect, camera-bump position, and font scale rather than cramming
 * against fixed assumptions.
 *
 * @property widthDp / heightDp measured size of the content region (post-inset).
 * @property isTiny true when the shorter usable side is below [COVER_TINY_DP] —
 *   tiles show fewer secondary rows / a tighter type step when tiny.
 * @property contentPadding the single merged inset (nav bar ∪ display cutout ∪
 *   camera-bump clearance ∪ base gutter), applied ONCE by the tile region.
 */
@androidx.compose.runtime.Immutable
data class CoverMetrics(
    val widthDp: Float,
    val heightDp: Float,
    val isTiny: Boolean,
    val contentPadding: PaddingValues,
)

internal val LocalCoverMetrics = staticCompositionLocalOf<CoverMetrics?> { null }

/** Below this (shorter usable side, dp) the cover is "tiny" — trim to essentials. */
internal const val COVER_TINY_DP = 300f

/**
 * Horizontal content inset for cover pebbles.
 *
 * The one real consumer of [CoverMetrics.isTiny] -- [LocalCoverMetrics] was provided by
 * [CoverScaffold] and documented at length ("everything is derived from the REAL
 * available space... rather than cramming against fixed assumptions"), but nothing
 * actually read `isTiny` anywhere; every cover dimension was a flat constant
 * regardless of how small the measured region came out. This trims the inset by 4dp
 * on a tiny cover, which is a real fraction of a screen whose shorter usable side is
 * already under 300dp -- a fixed 16dp on both sides was costing that tile
 * proportionally more room than the same inset costs a larger cover.
 */
@Composable
internal fun coverContentInset(): Dp = if (LocalCoverMetrics.current?.isTiny == true) 12.dp else 16.dp

/** True inside a [CoverTile]'s body, i.e. below a title band that already
 *  shows the page's icon and name. [CoverHero] reads it to avoid drawing that
 *  same glyph a second time, a few dp lower and larger. */
internal val LocalCoverTileTitled = staticCompositionLocalOf { false }

/** The one converged cover-hero icon size. Was drifting 30/48/64 across tiles; a single
 *  scale is what makes the cover read as one system. Device-verify the exact value
 *  (32–36 is the safe window at ~1.15x font scale); 34 is one nudge up from the old majority. */
internal val CoverHeroIcon = 34.dp

/**
 * The one shared glance-hero every cover tile opens with: a shrink-to-fit
 * headline [value] (via [com.bloo.uicommon.FittedText], so it can never
 * clip/wrap), optionally a [trailing] value pushed to the row end (e.g. Climate setpoint)
 * and a [subline] below (e.g. AI status, Location coordinates). Left-aligned, full-width,
 * and — critically — emits NO trailing Spacer: the cover shell's `spacedBy(CenterVertically)`
 * owns the gap to the next child, so Climate/Info/Diagnostics/AI/Fuel/Trips/Location all
 * share the exact same rhythm. Color must be baked into the FittedText style (it ignores
 * LocalContentColor).
 */
@Composable
internal fun CoverHero(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurface,
    subline: String? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The [icon] is drawn ONLY when this hero isn't already inside a
            // CoverTile that named the page with the same glyph. It always is,
            // now that every cover page goes through the template -- so in
            // practice this draws nothing and the value gets the full width,
            // which on a one-inch screen is several characters of headline.
            // The parameter stays because the icon is what a caller reaches
            // for first, and silently ignoring one passed outside a titled
            // tile would be worse than honouring it.
            if (!LocalCoverTileTitled.current) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(CoverHeroIcon))
            }
            com.bloo.uicommon.FittedText(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = valueColor),
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                com.bloo.uicommon.FittedText(
                    text = trailing,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = trailingColor),
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
        }
        if (subline != null) {
            Text(
                subline,
                style = MaterialTheme.typography.bodyMedium,
                // Not MutedContentAlpha (0.7): CoverHero only ever renders inside an
                // already cover-gated branch (FuelPebble, LocationPebble,
                // AiSummaryPebble, ...), where the ambient content color is already
                // the dimmer onSurfaceVariant role (the pebble's default container is
                // surfaceVariant) -- 0.7 on top compounds into the same "overly gray"
                // pattern StatusRow's label had. This is the hero every cover page
                // actually opens with, so it's a bigger legibility cost than a list
                // row's label.
                color = LocalContentColor.current.copy(alpha = 0.92f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * THE cover-screen tile template. Every page on the flip cover is one of
 * these, so they all read as the same object with different contents rather
 * than as a stack of unrelated cards.
 *
 * Three bands, always in this order:
 *  1. TITLE -- a small icon and the tile's name at title size, with an
 *     optional state [subtitle] under it. Cover pebbles used to have no title
 *     at all: the header row is dropped in fill-height mode (it cost ~76dp
 *     before a single line of content) and all that was left was a 30dp icon
 *     badge floating over the body's top-start corner. That badge said which
 *     tile you were on only if you already knew the iconography, and it
 *     overlapped the content it sat on.
 *  2. BODY -- weighted, so it takes everything left over, and centred within
 *     that. Scrolls when it's taller than the space, using the caller's
 *     [scrollState] so the cover pager can tell "scroll the tile" from "page
 *     to the next tile".
 *  3. ACTIONS -- an optional bottom bar pinned outside the scroll area, so a
 *     tile's controls are reachable no matter where its body is scrolled to.
 *
 * The bands are the standard; what goes in them is per-tile. That is the
 * whole point: the home tile's four-button bar and a pebble's single pinned
 * action are the same band in the same place at the same height, so paging
 * between them moves the content and nothing else.
 */
@Composable
internal fun CoverTile(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    scrollState: ScrollState? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    // Drawn BEHIND the title/body/actions, inside the card's own clip -- the same
    // slot PebbleShell's own `background` is for the phone hero, and for the same
    // reason: CoverMainTile uses this for a full-bleed car photo. Whatever's here
    // is responsible for its own legibility (see titleColor/iconTint below); null
    // for every other tile, so nothing else pays for the extra Box.
    background: (@Composable BoxScope.() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    body: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(PebbleCornerExpanded)
    val outline = LocalAppearance.current.pebbleOutline
    Card(
        modifier = modifier
            .fillMaxSize()
            .dropShadow(shape, blurRadius = 12.dp, offsetY = 4.dp)
            .then(
                if (outline) {
                    Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), shape)
                } else Modifier,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColorFor(containerColor),
        ),
    ) {
      Box(Modifier.fillMaxSize()) {
        background?.invoke(this)
        Column(Modifier.fillMaxSize().padding(horizontal = coverContentInset())) {
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
                com.bloo.uicommon.FittedText(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    // Not MutedContentAlpha (0.7) atop the Card's own contentColor: that
                    // color is contentColorFor(containerColor), and the default
                    // containerColor is surfaceVariant, whose paired content tone is
                    // onSurfaceVariant -- already a lower-contrast MD3 role before any
                    // alpha is applied. Muting it further compounds two dimming steps
                    // into text that reported as "overly gray" on several cover pages,
                    // where this subtitle is a full line right under the title (not a
                    // small list-row label, the case MutedContentAlpha was tuned for).
                    // 0.92 keeps it visually secondary to the title without reading as
                    // washed out on a small, quick-glance screen.
                    color = subtitleColor ?: LocalContentColor.current.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // BoxWithConstraints captures the real available height, undisturbed
            // by verticalScroll one level in, so heightIn(min = ...) can force
            // the scrolling Column to at least that tall -- which is what makes
            // a short body centre in the band instead of collapsing to its top
            // with dead space underneath, while a tall one still scrolls.
            val scroll = scrollState ?: rememberScrollState()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val minHeight = maxHeight
                CompositionLocalProvider(LocalCoverTileTitled provides true) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .fadingEdges(scroll)
                            .verticalScroll(scroll)
                            .heightIn(min = minHeight)
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                        content = body,
                    )
                }
            }
            if (actions != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    content = actions,
                )
            } else {
                Spacer(Modifier.height(14.dp))
            }
        }
      }
    }
}

/**
 * The flip cover's home tile: the car, its charge, and its controls on ONE
 * screen.
 *
 * It replaces two separate cover pages that were each mostly empty. The old
 * home tile was the phone's HeroHeader reused verbatim -- a card built around
 * a photo -- but landed on a flat gradient here instead: HeroHeader never went
 * through PebbleShell/CoverTile's fill-height cover treatment, so its own
 * `cover` branch stopped being reachable once this tile replaced it as the
 * cover's actual home page, leaving that photo path built but orphaned. The
 * lock/horn controls then lived on their own second page with the same
 * emptiness under them. Neither page filled a screen; both together do, and
 * merging them means the thing you most want with the phone shut -- lock
 * state and the lock button -- is on the page it opens on rather than one
 * swipe away.
 *
 * The car's photo is now this tile's own background (via CoverTile's
 * `background` slot), full-bleed with the same scrim [HeroPhotoBackdrop]
 * already builds for the phone hero -- reusing that composable rather than a
 * second implementation, so the two can't drift. No photo set -> HeroVisual's
 * own brand-gradient fallback fills the same way, so the tile is never a
 * dead inset rectangle either way.
 *
 * The car's name leads, at headline size. It used to be a labelMedium line in
 * the shared top overlay, sharing that space with the page dots, which on this
 * screen made the single most identifying thing on it the smallest text on it.
 */
@Composable
internal fun CoverMainTile(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val metric = LocalAppearance.current.unitSystem == "metric"
    val imageUrl = state.imageUrls[v.vin]
    val hasPhoto = !imageUrl.isNullOrBlank()
    // The car's own name is this tile's title -- the template's title band is
    // where every other page says what it is, so the home page says which car.
    // Lock leads the subtitle because it is the reason to look at a shut
    // phone; driving/charging state is left to ChargeFuelBar's own status
    // line, which is directly below it and already says both.
    val bits = listOfNotNull(
        status?.doorLock?.let { if (it) "Locked" else "Unlocked" },
        if (status?.airCtrlOn == true) "Climate on" else null,
    )
    // Same trade the phone hero makes over its own photo (HeroPhotoBackdrop's scrim
    // is built for HeroOnPhoto text): a fixed near-white reads correctly against
    // that scrim regardless of the photo's own brightness, where the theme's usual
    // onSurface/error tones would not. Lock's own attention colour (error, an
    // unlocked car) still needs to read as a WARNING over a photo, not just legible
    // -- swapped to a fixed warm red rather than the theme's errorContainer-tuned
    // MaterialTheme.colorScheme.error, which is calibrated against a flat surface.
    val titleColor = if (hasPhoto) HeroOnPhoto else MaterialTheme.colorScheme.onSurface
    val subtitleColor = when {
        status?.doorLock == false -> if (hasPhoto) Color(0xFFFF8A80) else MaterialTheme.colorScheme.error
        hasPhoto -> HeroOnPhoto.copy(alpha = MutedContentAlpha)
        else -> null
    }
    CoverTile(
        title = v.name,
        icon = Icons.Filled.DirectionsCar,
        subtitle = bits.joinToString(" · ").ifBlank { null },
        subtitleColor = subtitleColor,
        iconTint = if (hasPhoto) HeroOnPhoto else MaterialTheme.colorScheme.primary,
        titleColor = titleColor,
        background = {
            // height is inert when fill = true -- HeroVisual only reads it in the
            // non-fill, non-aspectRatio branch (see its own `sizeModifier` when) --
            // so there's no real value to pass; this Box has no BoxWithConstraints
            // scope to measure one from anyway.
            HeroPhotoBackdrop(v, imageUrl, height = 0.dp, corner = PebbleCornerExpanded, fill = true)
        },
        actions = { CoverActionBar(v, state, vm) },
    ) {
        CompositionLocalProvider(LocalContentColor provides titleColor) {
        ChargeFuelBar(
            status,
            state.hasBattery(v),
            state.hasFuel(v),
            state.drivingLabel(v),
            metric = metric,
        )
        }
    }
}

/**
 * The cover screen's bottom control bar: one tap each for the actions that
 * live in the pebble headers on the phone -- lock, climate, charge, horn.
 *
 * Those header actions are the whole point of every pebble; on the cover they
 * were reachable only by swiping to the matching page, and two of them (climate
 * and charge) not at all, because those pages open on a glance hero rather than
 * their header. A shut phone is the surface where "just lock it" matters most,
 * so they get a permanent, full-width, thumb-height row instead -- the
 * [CoverTile] actions band, which every cover page now has.
 *
 * Buttons are sized by weight rather than fixed width, so a car with no
 * horn/lights support or no battery gets three fat buttons rather than four
 * narrow ones with a hole where the fourth was.
 */
@Composable
internal fun RowScope.CoverActionBar(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val ev = status?.evStatus
    val locked = status?.doorLock
    val charging = ev?.batteryCharge == true
    val plugged = ev.isPluggedOrCharging
    val climateOn = status?.airCtrlOn == true
    val enabled = !state.loading
    CoverActionButton(
        icon = if (locked == true) Icons.Filled.LockOpen else Icons.Filled.Lock,
        label = if (locked == true) "Unlock" else "Lock",
        // Attention, not confirmation: an unlocked car is the state worth
        // colouring, matching StateControl's own highlightWhenOff.
        attention = locked == false,
        pending = state.isPending(v.vin, "doors"),
        enabled = enabled,
        onClick = { if (locked == true) vm.unlock(v) else vm.lock(v) },
    )
    CoverActionButton(
        icon = Icons.Filled.Thermostat,
        label = if (climateOn) "Stop" else "Climate",
        active = climateOn,
        pending = state.isPending(v.vin, "climate"),
        enabled = enabled,
        onClick = { vm.toggleClimate(v) },
    )
    if (state.hasBattery(v)) {
        CoverActionButton(
            icon = Icons.Filled.Bolt,
            label = if (charging) "Stop" else "Charge",
            active = charging,
            pending = state.isPending(v.vin, "charge"),
            // The car can't start a charge it isn't plugged into, and the
            // Charge pebble's own header button is gated the same way.
            enabled = enabled && plugged,
            onClick = { if (charging) vm.stopCharge(v) else vm.startCharge(v) },
        )
    }
    if (v.supportsHornLights) {
        // One button doing double duty rather than a fifth icon squeezed into an
        // already-tight row on a ~1-inch cover: tap for the combined "Horn &
        // lights" the main phone UI leads with, long-press for lights-only --
        // silent, useful for finding a car in a dark lot without honking. The
        // main phone screen offers both as separate buttons in a group
        // (PrimaryActions); flashLights had no cover-screen path at all before
        // this, reported as a real feature gap. Long-press is already an
        // established cover gesture (the tile-scrubber rail, the edge-trace
        // refresh), so this isn't a new interaction language for the surface.
        CoverActionButton(
            icon = Icons.Filled.Campaign,
            label = "Horn",
            // Both flashLights and hornAndLights run under the same "hornLights"
            // pending key (AppViewModel), so one check covers either.
            pending = state.isPending(v.vin, "hornLights"),
            enabled = enabled,
            onClick = { vm.hornAndLights(v) },
            onLongClick = { vm.flashLights(v) },
        )
    }
}

/** One button in [CoverActionBar]: icon over a short label, filling its share
 *  of the row. Colour carries state -- [active] for a running command's target
 *  state, [attention] for a state the user probably wants to change. */
@Composable
internal fun RowScope.CoverActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    attention: Boolean = false,
    pending: Boolean = false,
    enabled: Boolean = true,
    // A second action on the same button, reached by holding rather than
    // tapping -- null for every caller but the horn/flash one. Kept optional
    // rather than every button growing a second gesture it has no use for.
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    // Same MorphButton as everywhere: active commands wear the primary
    // highlight, the "worth changing" state wears the error container, and
    // idle is the standard button fill. It simply pins both corner percents
    // to the same square 16dp value so a cover bar button never morphs.
    val squarePct = 100f * 16.dp.value / 56.dp.value
    // The content tone for every state the core reaches: active->onPrimary,
    // attention->onErrorContainer, else onSurface. Passed as BOTH the idle
    // content and the disabled content (full alpha, so the cover button's own
    // 45% whole-pill fade is the ONLY dim when disabled -- the core's default
    // label-only fade would compound on top of it).
    val contentFor = if (active) scheme.onPrimary
        else if (attention) scheme.onErrorContainer
        else scheme.onSurface
    MorphButton(
        onClick = { onClick() },
        onClickHaptic = { haptics?.click() },
        onLongClick = onLongClick?.let { fn -> { haptics?.tick(); fn() } },
        enabled = enabled && !pending,
        active = active,
        containerColor = if (attention) scheme.errorContainer else buttonContainer(),
        contentColor = contentFor,
        disabledContentColor = contentFor,
        pillCornerPercent = squarePct,
        morphedCornerPercent = squarePct,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        minHeight = 0.dp,
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(3.dp))
            com.bloo.uicommon.FittedText(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = LocalContentColor.current,
                ),
            )
        }
    }
}

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

/**
 * Flip-cover Settings: the REAL scrollable SettingsScreen (the settings grid
 * scrolls exactly as it does on the phone -- the old "manage on your phone"
 * gate card locked the whole screen away behind itself, double-blocking
 * everything from the update card onward), introduced once by a polite
 * "this was built for a taller phone" prompt with a persistent "don't show
 * again". The prompt is a doorbell, not a bouncer: after it, settings just
 * scroll on the cover.
 */
@Composable
internal fun CoverSettingsGate(vm: AppViewModel) {
    val appearance = LocalAppearance.current
    var promptOpen by remember { mutableStateOf(true) }
    SettingsScreen(vm, compact = true)
    if (promptOpen && !appearance.coverSettingsHintDismissed) {
        GlassAlertDialog(
            onDismissRequest = { promptOpen = false },
            title = "Settings on the cover",
            icon = Icons.Filled.Smartphone,
            text = {
                Text("This screen is designed for a taller phone display. You can scroll through everything here, but unfolding the phone makes settings much easier to read and tweak.")
            },
            buttons = {
                MorphButton(
                    onClick = { promptOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continue on the cover", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(8.dp))
                MorphTextButton(
                    "Don't show this again",
                    onClick = {
                        promptOpen = false
                        vm.setCoverSettingsHintDismissed(true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

/**
 * Cover-screen layout: swipe left/right for cars, up/down for section tiles.
 *
 * Owns one [HorizontalPager] (`pager`) for switching between cars, using the
 * same "virtual page count = real count * 1000, start in the middle, map
 * back with modulo" trick as the other car pagers in this file to fake
 * infinite wrap-around. Each car's page then hosts its own vertical tile
 * pager/scrubber further down (not shown in this snippet) for swiping
 * between that car's pebbles; `scrubbing` is shared mutable state that, when
 * true, disables `userScrollEnabled` on this horizontal pager so a
 * long-press-drag scrub of the vertical tile indicator can't accidentally
 * also trigger a car-switch swipe underneath it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactGarage(state: UiState, vm: AppViewModel, appearance: SettingsStore.Appearance) {
    val vehicles = state.vehicles
    val count = vehicles.size
    // count - 1 goes negative with zero cars, and coerceIn(0, -1) throws
    // (min > max) before the pager below ever gets a chance to handle an empty
    // list gracefully. Kept as a crash guard rather than an expected state, and
    // labelled that way on purpose: it is currently UNREACHABLE from the one
    // caller -- GarageScreen returns on its first line when vehicles is empty,
    // and a zero-car app routes to Screen.Empty long before Screen.Garage. This
    // comment used to cite a `compact && vehicles.isEmpty()` branch in that caller
    // as proof the state was real; that branch was itself dead for the same
    // reason, and has been deleted. Two lines of guard against a throwing
    // coerceIn is still worth keeping; the claim that something reaches it wasn't.
    if (count == 0) {
        EmptyScreen(vm)
        return
    }
    // Infinite wrap-around, matching every other car-switching pager in the
    // app (the expanded pager, the default grid) and the cover screen's own
    // tile pager, which already looped.
    // Same as GarageScreen: the index is its own flow, collected here.
    val currentIndex by vm.currentIndex.collectAsState()
    val wrap = rememberWrapPager(count, currentIndex.coerceIn(0, count - 1))
    val pager = wrap.pager
    fun realCar(virtualPage: Int) = wrap.real(virtualPage)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { vm.selectIndex(realCar(it)) }
    }
    // Mirror of the default garage pager's own fix: react to currentIndex
    // changing out from under an already-composed pager (e.g. a widget tap
    // selecting a specific car while the cover screen was already showing a
    // different one) by snapping to it, instead of only ever pushing this
    // pager's own settles into currentIndex one-way.
    LaunchedEffect(currentIndex) {
        wrap.snapToReal(currentIndex.coerceIn(0, count - 1))
    }
    // True while the page scrubber is active; suspends car-switching swipes so a
    // scrub gesture can't be hijacked into flipping to the next car.
    val scrubbing = remember { mutableStateOf(false) }
    // Hide the page indicators while a refresh is in flight (pull-to-refresh /
    // manual refresh) so the loading indicator owns the screen. Shared by both
    // dot rows below (car-switch AND per-car tile) instead of each keeping its
    // own separate Animatable of the exact same value.
    // Held as State, not read via `by` — see the same treatment in GarageScreen.
    // Read in composition scope this fade recomposed the whole cover pager (and,
    // as a plain Float parameter, every CompactCar page) once per animation frame.
    val dotsAlphaState = animateFloatAsState(
        targetValue = if (state.refreshing) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "coverDotsFade",
    )
    Box(Modifier.fillMaxSize()) {
        // Which cover tile is showing. The home tile titles ITSELF with the
        // car's name (see CoverMainTile), so the shared overlay saying it too
        // put the same words twice on a one-inch screen -- which is what the
        // overlay was added to fix in the first place, for the OTHER tiles,
        // whose titles name a section rather than a car.
        var visibleTile by remember { mutableStateOf("main") }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !scrubbing.value,
            beyondViewportPageCount = 1,
        ) { page ->
            val v = vehicles[realCar(page)]
            // No blur -- see the other two car pagers' history for why: a plain
            // Modifier.blur(x.dp) reconstructs and re-lays-out its own modifier
            // node on every drag frame (the jitter this exact pattern caused
            // elsewhere), and this cover-screen pager had never actually been
            // updated when that got fixed there. Just the cheap graphicsLayer
            // fade/scale transforms now, consistent with the other pagers.
            Box(Modifier.fillMaxSize().pagerDepth(pager, page)) {
                CarThemeOverride(
                    paletteId = appearance.carCustomPaletteIds[v.vin],
                    customPalettes = appearance.customPalettes,
                    themeMode = appearance.themeMode,
                    vibrancy = appearance.vibrancy,
                ) {
                    CompositionLocalProvider(LocalCoverScrubbing provides scrubbing) {
                        CompactCar(v, state, vm, dotsAlphaState, onTileChange = { visibleTile = it })
                    }
                }
            }
        }
        // Measured once and shared by both readers below (the top-overlay name
        // and the band itself), so they can never disagree about whether the
        // band exists and end up showing the name twice or not at all.
        val band = coverCutoutBand()
        // Car-switching dots, hoisted out of CompactCar (a per-page composable)
        // and up to here -- a sibling of the whole pager, not inside any one
        // page's fade/scale graphicsLayer -- so it doesn't itself fade and
        // shrink along with the outgoing/incoming car during a swipe, exactly
        // like every other car pager's PagerDots already stays put outside
        // the per-page transform.
        // Car name + switching dots, in one TopCenter overlay.
        //
        // The name is here rather than in the tiles because cover pebbles
        // render header-less by design, so nothing on the cover screen said
        // which car you were looking at -- fine on the main tile, genuinely
        // confusing on Charge or Climate after swiping between cars.
        // Reported from a real device.
        //
        // It rides the same overlay the dots already occupied, so it claims
        // no vertical space that wasn't already spoken for, and fades with
        // the same refresh alpha so the loading indicator still owns the
        // screen during a refresh.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = HeaderCornerGap, start = HeaderCornerGap, end = HeaderCornerGap)
                .graphicsLayer { alpha = dotsAlphaState.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Skipped when the camera band is showing the name instead -- see
            // below. Saying it twice on a screen this size is worse than the
            // problem the overlay was added to solve.
            vehicles.getOrNull(currentIndex.coerceIn(0, count - 1))
                ?.takeIf { band == null && visibleTile != "main" }
                ?.let { current ->
                Text(
                    current.name,
                    // Was labelMedium/onSurfaceVariant -- the smallest, dimmest
                    // text on a screen whose whole job is telling you which car
                    // you are looking at. The home tile now says it at headline
                    // size itself; this overlay is what the OTHER tiles have, so
                    // it reads as a title rather than a caption.
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (count > 1 && !LocalReorderActive.current) {
                Spacer(Modifier.height(6.dp))
                PagerDotsFor(
                    pager = pager,
                    real = { realCar(it) },
                    count = count,
                    // No hold-to-refresh here -- the cover screen's own
                    // edge-trace gesture (drag down from the top edge) is
                    // already the refresh affordance in this mode; the dots
                    // are display-only.
                    onRefresh = null,
                )
            }
        }
        if (band != null) {
            // Search is available here whenever it's available on the cover
            // at all -- same gate BlooApp itself uses to decide whether to
            // show SearchLayer for the garage. When it holds, this band
            // reserves CoverBandSearchDock's worth of space at the end
            // nearest the camera; SearchLayer reads the same band and docks
            // its own bubble into exactly that reservation (see there) --
            // one tap target, not a second one duplicated here.
            val searchInBand = appearance.showSearch && !state.locked
            // Same glass chip every other floating chrome in the app wears
            // (the identity pill, FloatingIcon) -- bare text here used to sit
            // directly on whatever the tile underneath happened to be
            // showing, so legibility rode entirely on luck (readable over a
            // dark gauge, gone over a bright photo). A pill-shaped backdrop,
            // sized to the band's own height, gives it the same guaranteed
            // contrast every other piece of floating chrome already has, and
            // reads as one more piece of that chrome rather than loose text.
            val bandShape = RoundedCornerShape((band.heightDp / 2f).dp)
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = band.xDp.dp, y = band.yDp.dp)
                    .width(band.widthDp.dp)
                    .height(band.heightDp.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()), bandShape)
                    .ambientRing(bandShape)
                    .dropShadow(bandShape)
                    .frostedRim(bandShape)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                // Grouped flush against whichever end is next to the camera,
                // not spread across the whole band -- a short name spread
                // full-width by weight(1f) used to leave a dead gap between
                // the text and the island it should read as belonging next
                // to. weight(1f, fill = false) still bounds FittedText enough
                // to shrink-fit inside a narrow band, it just no longer
                // forces the box to fill space the text isn't using.
                horizontalArrangement = Arrangement.spacedBy(
                    4.dp,
                    if (band.nearCameraAtEnd) Alignment.End else Alignment.Start,
                ),
            ) {
                val current = vehicles.getOrNull(currentIndex.coerceIn(0, count - 1))
                // Order follows which end is near the camera, so the dock
                // reservation always lands flush against it regardless of
                // which side of the island this band happens to be on.
                if (!band.nearCameraAtEnd && searchInBand) {
                    Spacer(Modifier.width(CoverBandSearchDock))
                }
                if (current != null) {
                    com.bloo.uicommon.FittedText(
                        text = current.name,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (band.nearCameraAtEnd && searchInBand) {
                    Spacer(Modifier.width(CoverBandSearchDock))
                }
            }
        }
    }
}

/**
 * One car's page inside [CompactGarage]'s pager: a vertical stack of pebble
 * "tiles" (main summary, climate, charge, location, ...), one per screen,
 * navigated with the same infinite-wrap virtual-page trick as the car
 * pager itself. Also owns three independent, cover-screen-only concerns
 * layered into the same [Box]:
 *  - Camera-cutout avoidance: content is padded via native
 *    WindowInsets.displayCutout (corner-safe, recomposition-aware) so it clears
 *    a punch-hole on whichever edge(s) it touches; a decorative ring is drawn
 *    around the hole so it reads as intentional.
 *  - The edge-trace refresh gesture: a long-press-and-hold that fills an
 *    animated ring around the screen edge over 1.2s; completing the hold
 *    (without releasing or moving past touch slop) triggers a refresh. Its
 *    pointerInput lives on the outer parent [Box], deliberately relying on
 *    Compose's leaf-to-root gesture dispatch so [VerticalPager]'s own drag
 *    recognizer (a child, and therefore evaluated first) gets first claim on
 *    any real vertical drag before this handler ever sees it.
 *  - Per-tile scroll position (`tileScrollStates`), keyed by tile name so a
 *    tall tile's scroll offset survives being paged away from and back to,
 *    and survives the user reordering pebbles (unlike keying by index).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactCar(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    // State, not Float: a plain Float parameter changes on every frame of the
    // dots fade, which made this whole page recompose ~15 times per fade. As
    // State the value is read draw-phase only (see its graphicsLayer use below).
    dotsAlphaState: androidx.compose.runtime.State<Float>,
    /** Which tile is centred, reported up so the shared top overlay knows
     *  whether the page below it is already showing the car's name. */
    onTileChange: (String) -> Unit = {},
) {
    // Live source passed to SinglePebble (which takes State<UiState> now).
    val stateSource = rememberUpdatedState(state)
    val status = state.statusFor(v)
    val isGen5W = remember(v.brand, v.generation, state.platforms[v.vin]) { state.isGen5WEffective(v) }
    // Cover-screen tiles follow the same order the user arranged the pebbles in
    // (state.sectionsFor). "summary" maps to the always-present "main" tile;
    // "controls" has no cover tile so it falls away. If summary was somehow
    // dropped, "main" is prepended so the cover screen always has a home tile.
    // Memoized on exactly the state slices the predicate reads, so this mapNotNull +
    // list concat doesn't re-run on every unrelated state emission (CompactCar takes
    // the whole UiState, so it recomposes on any change worth reflecting on one
    // car page (status ticks, pending flags, messages) -- the per-tile memo
    // below keeps that cost proportional to what changed.
    val hasBattery = state.hasBattery(v)
    val tiles = remember(state.sectionOrders[v.vin], hasBattery, state.aiEnabled, isGen5W, state.hiddenPebbles) {
        state.sectionsFor(v).mapNotNull { section ->
            when (section) {
                "summary" -> "main"
                else -> section.takeIf {
                    it in CompactKnownTiles &&
                        // Cover-screen-only gate, and the reason isSectionAvailable
                        // does not carry it: everywhere else SinglePebble falls back to
                        // a FuelPebble for a car with no battery, so "charge" still has
                        // something to render. The cover has no such fallback tile.
                        (it != "charge" || hasBattery) &&
                        state.isSectionAvailable(v, it)
                }
            }
        }.let { ordered -> if ("main" in ordered) ordered else listOf("main") + ordered }
    }
    // Infinite wrap-around: start in the middle of a huge virtual range and map
    // each virtual page back onto a real tile with modulo. FLAT tiles -- unlike
    // the three horizontal car pagers this VerticalPager gets NO pagerDepth and
    // NO beyondViewportPageCount.
    val vWrap = rememberWrapPager(tiles.size)
    val vPager = vWrap.pager
    val current = vWrap.currentReal
    LaunchedEffect(current, tiles) { onTileChange(tiles.getOrElse(current) { "main" }) }
    // Per-tile scroll states, keyed by tile name so position persists across
    // pager recycling AND reordering. Tall tiles scroll their own content; the
    // VerticalPager then nested-scrolls to the next/previous tile once a tile is
    // scrolled to its edge.
    val tileScrollStates = remember { mutableMapOf<String, ScrollState>() }
    // Suspend native tile paging while the right-rail scrubber is driving the
    // pager, so a scrub drag can't also be read as a page swipe.
    val coverScrubbing = LocalCoverScrubbing.current

    val density = LocalDensity.current
    // NOTE: nothing here reads the display cutout's boundingRects any more, which
    // is what this note is actually about -- it used to say "nothing here reads the
    // display cutout", flatly, which is not true and sends anyone chasing a
    // cover-screen bump problem to the wrong place. The hand-rolled per-edge
    // CLEARANCE math went first, and the decorative ring that was the last
    // remaining rects reader has now gone too (see where it used to be drawn).
    // Cutout avoidance is still very much present, just native and declarative:
    // the tile Box below takes the scaffold's merged nav-bar-union-cutout inset,
    // and the scrubber rail takes WindowInsets.displayCutout on its End side only.
    // Both are corner-safe and recomposition-aware, which the rects math was not.

    // ---- Edge-trace refresh gesture ----
    // Long-press anywhere on the cover screen to trace a line around the edge.
    // When the line completes its full circuit, trigger a refresh. This is a
    // cover-screen-only interaction (the normal phone layout doesn't use it).
    val edgeTraceProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var edgeTraceHolding by remember { mutableStateOf(false) }
    // The tile-scrubber dots (VerticalPagerDots) are a sibling inside this same
    // Box, so a press over them still reaches this pointerInput during the
    // normal ancestor dispatch -- without carving out their bounds, holding
    // the dots to scrub also started the edge-trace refresh ring underneath,
    // since edge-trace begins timing on raw down regardless of what else the
    // touch lands on. Populated by the dots' own onGloballyPositioned below.
    var dotsBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    LaunchedEffect(edgeTraceHolding) {
        if (edgeTraceHolding) {
            edgeTraceProgress.snapTo(0f)
            edgeTraceProgress.animateTo(
                1f,
                animationSpec = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            )
            if (edgeTraceHolding) {
                // Only refresh if the user is still holding (didn't release early).
                vm.refreshStatus(v)
            }
            edgeTraceHolding = false
        } else if (edgeTraceProgress.value > 0f) {
            // Released (or cancelled into a swipe) before completing the hold --
            // ease the partial ring back to nothing instead of leaving it frozen
            // at whatever progress it had reached.
            edgeTraceProgress.animateTo(0f, androidx.compose.animation.core.tween(200))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Edge-trace refresh gesture lives here, on the actual PARENT of
            // VerticalPager below, not on a separate sibling Box overlapping
            // it -- sibling dispatch order between two unrelated composables
            // is ambiguous and kept letting this steal the vertical swipe
            // despite two earlier attempts (never consuming; then watching on
            // the Final pass). Parent/child order is NOT ambiguous: the
            // default Main pass runs leaf-to-root, so VerticalPager's own
            // drag recognizer (the child) always gets first crack at a given
            // event, and by the time it bubbles up to this parent's handler,
            // change.isConsumed already reflects whether the pager claimed
            // it. This is the actual textbook nested-gesture-priority
            // pattern, not another guess at pass ordering between siblings.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // A press starting inside the tile-scrubber dots' own hit
                    // area belongs entirely to their long-press-to-scrub
                    // gesture -- don't also start timing an edge-trace hold
                    // for it (see dotsBounds' declaration above).
                    if (dotsBounds?.contains(down.position) == true) return@awaitEachGesture
                    // Only arm the edge-trace when the press starts near a screen
                    // EDGE — that's the whole metaphor ("trace around the rim"). It
                    // used to arm on ANY press anywhere, so a slow/stationary press on
                    // a center control (the DC-limit slider, a climate button) both
                    // flickered the ring on and, if held >1.2s, fired an unintended
                    // vm.refreshStatus. Requiring an edge start makes it intentional
                    // and stops it stealing center interactions.
                    val edgeMarginPx = with(density) { 40.dp.toPx() }
                    val nearEdge = down.position.x <= edgeMarginPx ||
                        down.position.x >= size.width - edgeMarginPx ||
                        down.position.y <= edgeMarginPx ||
                        down.position.y >= size.height - edgeMarginPx
                    if (!nearEdge) return@awaitEachGesture
                    edgeTraceHolding = true
                    val slop = viewConfiguration.touchSlop
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed || change.isConsumed) break
                            val dx = abs(change.position.x - down.position.x)
                            val dy = abs(change.position.y - down.position.y)
                            if (dx > slop || dy > slop) break
                        }
                    } finally { if (edgeTraceHolding) edgeTraceHolding = false }
                }
            },
    ) {
        // Native vertical paging. The pager owns the swipe gesture and pages on
        // any vertical drag; tall tiles scroll their own content first and the
        // pager nested-scrolls to the next/previous tile once a tile is at its
        // edge. The car-switching HorizontalPager is orthogonal, so left/right
        // swipes go to it and up/down swipes go here without any custom gesture
        // arbitration. Paging is suspended while the right-rail scrubber is active.
      CoverScaffold(reserveRailGutter = tiles.size > 1) { metrics ->
        VerticalPager(
            state = vPager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = coverScrubbing?.value != true,
        ) { page ->
            val i = vWrap.real(page)
            val tileScroll = tileScrollStates.getOrPut(tiles[i]) { ScrollState(0) }
            CompositionLocalProvider(
                LocalForceExpanded provides true,
                LocalPebbleFillHeight provides true,
                LocalCoverScrollState provides tileScroll,
            ) {
                // ONE merged inset from the scaffold (nav bar ∪ cutout ∪ corner-safe
                // camera-bump ∪ base gutter, max()'d per edge) — replaces the old
                // three-layer additive stack that double-reserved the bump and
                // crammed content into the left half.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(metrics.contentPadding),
                ) {
                    // The cover reuses the phone's pebble CARDS, rendered under the
                    // LocalForceExpanded/PebbleFillHeight/CoverScrollState providers so
                    // each pebble draws as an always-expanded, header-less, height-
                    // filling scrolling card (its cover glance-hero branch). The tile
                    // list renames "summary" -> "main", so map it back for SinglePebble.
                    // The home tile is the cover's own combined layout, not
                    // the phone's photo-first HeroHeader -- see CoverMainTile.
                    if (tiles[i] == "main") {
                        // Narrowed the same way every SinglePebble branch already is:
                        // CoverMainTile and CoverActionBar (called from inside its
                        // `actions` lambda) together only ever read this fixed set of
                        // UiState fields, but both took the whole UiState directly, so
                        // any unrelated emission (a location update on another car, a
                        // log line) recomposed this tile the whole time the cover
                        // screen was showing. remember(keys) { state } is the same
                        // "same reference back, skip if the keys didn't move" trick
                        // used at every other pebble call site.
                        val mainState = remember(
                            state.statusFor(v), state.imageUrls[v.vin], state.hasBattery(v),
                            state.hasFuel(v), state.drivingLabel(v), state.loading,
                            state.isPending(v.vin, "doors"), state.isPending(v.vin, "climate"),
                            state.isPending(v.vin, "charge"), state.isPending(v.vin, "hornLights"),
                        ) { state }
                        CoverMainTile(v, mainState, vm)
                    } else {
                        SinglePebble(tiles[i], v, stateSource, vm, Modifier)
                    }
                }
            }
        }
      }
        // The decorative camera ring that used to be drawn here has been
        // removed. It assumed the display cutout was a small circular
        // punch-hole and derived its radius from `cutout.width() / 2`, but a
        // flip cover screen reports the whole camera ISLAND as one bounding
        // rect -- so instead of tracing a lens it swept an enormous faint
        // circle across the panel, well outside the cameras it was meant to
        // acknowledge. Reported from a real device.
        //
        // Not re-fitted to the island shape: the rect is a bounding box, not
        // the real outline, so anything drawn from it is a guess at hardware
        // geometry that varies per device. It was purely cosmetic and load-
        // bearing for nothing (content padding comes from the native
        // WindowInsets.displayCutout on the tile Box above), so the honest
        // fix is to stop drawing it rather than to keep guessing.
        // Edge-trace ring: when holding (gesture handler lives on the outer
        // Box now, see above), a line traces the screen edge clockwise from
        // the top-left. Full circuit = refresh. Purely decorative here --
        // this Box has no pointerInput of its own to conflict with anything.
        Box(Modifier.fillMaxSize()) {
            if (edgeTraceProgress.value > 0.001f) {
                val accent = MaterialTheme.colorScheme.primary
                // The rounded-rect perimeter Path + PathMeasure only depend on the
                // Canvas size/density (constant while this composable is on screen),
                // not on edgeTraceProgress -- so they're built once per size/density
                // and cached here instead of being reallocated on every animation
                // frame. Only measure.getSegment(...) needs to re-run per frame, and
                // `traced` is rewound and reused rather than reallocated each time.
                val perimeterCache = remember { EdgeTracePerimeterCache() }
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = with(density) { 3.dp.toPx() }
                    val inset = stroke / 2f
                    if (perimeterCache.size != size) {
                        val rect = androidx.compose.ui.geometry.Rect(
                            inset, inset, size.width - inset, size.height - inset
                        )
                        // Trace the actual RECTANGULAR (rounded) screen perimeter, not an
                        // ellipse. The old code called drawArc on this full-screen rect,
                        // which draws an arc of the ELLIPSE inscribed in it — a huge oval
                        // bulging far past the visible edges (the "giant blue circle" in
                        // the screenshots). Instead, build the rounded-rect perimeter as a
                        // Path and take the first `progress` fraction of its length via
                        // PathMeasure.getSegment, so a thin stroke grows clockwise hugging
                        // the real edge.
                        val corner = with(density) { 28.dp.toPx() }
                        val perimeter = androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    rect,
                                    androidx.compose.ui.geometry.CornerRadius(corner, corner),
                                )
                            )
                        }
                        perimeterCache.measure.setPath(perimeter, false)
                        perimeterCache.size = size
                    }
                    val measure = perimeterCache.measure
                    val traced = perimeterCache.traced
                    traced.rewind()
                    measure.getSegment(
                        0f,
                        measure.length * edgeTraceProgress.value.coerceIn(0f, 1f),
                        traced,
                        true,
                    )
                    drawPath(
                        path = traced,
                        color = accent.copy(alpha = edgeTraceProgress.value.coerceIn(0f, 1f) * 0.85f),
                        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
        }
        // Vertical page dots on the right edge - show which pebble tile is visible.
        // (Car-switching dots are hoisted up to CompactGarage -- see there.)
        if (tiles.size > 1 && !LocalReorderActive.current) {
            VerticalPagerDots(
                current = current,
                count = tiles.size,
                tiles = tiles,
                onPageJump = { targetTile ->
                    vWrap.snapToReal(targetTile)
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // Clear a right-edge / bottom-right-corner camera bump: the
                    // scrubber sits flush to the physical right edge, so on a device
                    // whose cutout intrudes from the right it used to sit under the
                    // bump. Native displayCutout (End side only) floats it inboard;
                    // it's a no-op when the cutout doesn't touch the right edge.
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                    .padding(end = 6.dp)
                    .graphicsLayer { alpha = dotsAlphaState.value }
                    .onGloballyPositioned { dotsBounds = it.boundsInParent() },
            )
        }
    }
}
