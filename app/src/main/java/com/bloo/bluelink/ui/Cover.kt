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
