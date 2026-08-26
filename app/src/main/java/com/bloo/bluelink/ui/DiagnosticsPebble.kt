@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Diagnostics pebble: DiagRow, DiagnosticsPebble, warn/yesNo/onOff --
 * extracted from Pebbles.kt to keep the UI file focused.
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


// --- Diagnostics ----------------------------------------------------------

internal data class DiagRow(val label: String, val value: String, val indent: Boolean = false)

@Composable
internal fun DiagnosticsPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val a = LocalAppearance.current
    val fahrenheit = a.useFahrenheit
    val metric = a.unitSystem == "metric"
    val rows = remember(status, fahrenheit, metric) { buildList {
        status?.tirePressureLamp?.let { tp ->
            // No psi suffix. `TirePressure.all` was only ever populated FROM the warning lamp --
            // Kia read `tirePressure.all` (a 0/1 indicator) and Canada read
            // `tirePressureLamp.tirePressureLampAll` outright -- so this rendered "Warning · 1
            // psi" and "OK · 0 psi". No producer in this app has ever supplied a real
            // pressure, and both assignments are now deleted, so there is nothing to suffix.
            add(DiagRow("Tire pressure", if (tp.hasWarning) "Warning" else "OK"))
            tp.frontLeft?.let { add(DiagRow("Front left", warn(it), indent = true)) }
            tp.frontRight?.let { add(DiagRow("Front right", warn(it), indent = true)) }
            tp.rearLeft?.let { add(DiagRow("Rear left", warn(it), indent = true)) }
            tp.rearRight?.let { add(DiagRow("Rear right", warn(it), indent = true)) }
        }
        status?.battery?.let { b ->
            b.batSoc?.let { soc ->
                add(DiagRow("12V battery", "$soc%"))
            }
        }
        status?.evStatus?.batteryStatus?.let { add(DiagRow("Drive battery", "$it%")) }
        status?.rangeMiFor(state.hasBattery(v))?.let { add(DiagRow("Range", formatDistance(it, metric))) }
        status?.airTemp?.let { t ->
            t.value?.let { add(DiagRow("Climate setpoint", degLabel(it, fahrenheit, t.unit))) }
        }
        status?.fuelLevel?.let { add(DiagRow("Fuel level", "$it%")) }
        status?.lowFuelLight?.let { add(DiagRow("Low fuel", yesNo(it))) }
        status?.washerFluidStatus?.let { add(DiagRow("Washer fluid", if (it) "Low" else "OK")) }
        status?.breakOilStatus?.let { add(DiagRow("Brake fluid", if (it) "Check" else "OK")) }
        status?.smartKeyBatteryWarning?.let { add(DiagRow("Key fob battery", if (it) "Low" else "OK")) }
        status?.steerWheelHeat?.let { add(DiagRow("Steering wheel heat", onOff(it))) }
        status?.sideBackWindowHeat?.let { add(DiagRow("Rear defroster", onOff(it))) }
        status?.sideMirrorHeat?.let { add(DiagRow("Mirror heat", onOff(it))) }
        status?.seatHeaterVentState?.let { s ->
            val seats = listOfNotNull(
                s.flSeatHeatState?.takeIf { it != 0 }?.let { "Driver" },
                s.frSeatHeatState?.takeIf { it != 0 }?.let { "Passenger" },
                s.rlSeatHeatState?.takeIf { it != 0 }?.let { "Rear-left" },
                s.rrSeatHeatState?.takeIf { it != 0 }?.let { "Rear-right" },
            )
            if (seats.isNotEmpty()) add(DiagRow("Seat heat/vent active", seats.joinToString(", ")))
        }
        status?.evStatus?.pluggedInLabel?.let { add(DiagRow("Plug", it)) }
        // fmtMinutes, not "$it min" -- the charge pebble's own "Time to full" row a
        // few hundred lines up already used it, so a 95-minute estimate read
        // "1h 35m" there and "95 min" here, in the same app on the same screen.
        status?.evStatus?.minutesToFull?.let { add(DiagRow("Time to full", fmtMinutes(it))) }
        status?.doorOpen?.openLabels()?.takeIf { it.isNotEmpty() }
            ?.let { add(DiagRow("Doors open", it.joinToString(", "))) }
        if (status?.trunkOpen == true) add(DiagRow("Trunk", "Open"))
        if (status?.hoodOpen == true) add(DiagRow("Hood", "Open"))
        if (status?.doorLock == false && status.engine != true) add(DiagRow("Lock", "Car is unlocked while parked"))
    } }
    val diagSummary = remember(rows) { if (rows.isEmpty()) "No data" else "${rows.count { !it.indent }} checks" }
    // The count of actual problems, for the cover health-verdict hero below. diagSummary is a
    // *checks* count, not an issue count. The warning affordance is then just "any problem at
    // all" -- issueCount > 0 -- rather than a second hand-kept copy of these five predicates,
    // which is what this used to be (a parallel `hasWarning` ||-chain that had to stay in sync
    // with this list by hand). One source now; they can't drift.
    val issueCount = remember(status) {
        listOf(
            status?.tirePressureLamp?.hasWarning == true,
            status?.lowFuelLight == true,
            status?.washerFluidStatus == true,
            status?.breakOilStatus == true,
            status?.smartKeyBatteryWarning == true,
        ).count { it }
    }
    val hasWarning = issueCount > 0
    Pebble(
        v, "diagnostics", "Diagnostics", Icons.Filled.ErrorOutline, state, vm, dragHandle,
        summary = diagSummary,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        headerAction = if (hasWarning) PebbleHeaderAction(
            label = "",
            icon = Icons.Filled.Warning,
            onClick = { vm.togglePebble(v, "diagnostics") },
            isWarning = true,
            contentDescription = "Diagnostics warning",
        ) else null,
        alwaysExpandedInSimpleMode = true,
    ) {
        // COVER SCREEN only: a health-verdict hero — green check + "All systems OK",
        // or an error warning + "N issues" — so the tile reads at a glance instead of
        // as a flat list of ~12 rows. Gated on LocalForceExpanded (phone untouched).
        if (LocalForceExpanded.current && status != null) {
            CoverHero(
                icon = if (hasWarning) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                value = if (hasWarning) (if (issueCount == 1) "1 issue" else "$issueCount issues") else "All systems OK",
                iconTint = if (hasWarning) MaterialTheme.colorScheme.error else ChargeGreen,
                valueColor = if (hasWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (rows.isEmpty()) {
            Text(
                "No diagnostics yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rows.forEach { row ->
            if (row.indent) {
                // DiagnosticsPebble has no cover-vs-phone split of its own -- this whole
                // ~12-row list renders on the cover too, below the health-verdict hero.
                // The label stayed at the fixed dim onSurfaceVariant role there, same
                // class of issue StatusRow's label had; boosted on the cover the same
                // way. The value used to be entirely unstyled (inheriting the pebble's
                // own ambient onSurfaceVariant content color) rather than pinned to a
                // legible tone the way StatusRow's own value already is -- an indented
                // sub-row's VALUE is still the thing a user is actually checking.
                // onSurfaceVariant is already full-alpha as a raw theme color -- its
                // dimness is the ROLE itself (a lower-contrast RGB against the
                // surface), not an alpha multiply, so unlike StatusRow/CoverHero this
                // needed a color swap, not an alpha bump, to actually read stronger.
                val indentLabelColor = if (LocalForceExpanded.current) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        row.label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = indentLabelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            } else {
                StatusRow(row.label, row.value)
            }
        }
    }
}

internal fun warn(v: Int) = if (v == 0) "OK" else "Warning"
internal fun yesNo(v: Boolean) = if (v) "Yes" else "No"
internal fun onOff(v: Int) = if (v == 0) "Off" else "On"
