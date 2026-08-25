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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.LockReset
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
import androidx.compose.ui.text.input.ImeAction
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
import com.bloo.uicommon.ReorderColumn
import com.bloo.uicommon.LocalReorderActive
import com.bloo.uicommon.coldStartIntroPlayed
import com.bloo.uicommon.animatePlacement

internal val TileActions = listOf(
    Triple("doors", "Lock / unlock", Icons.Filled.Lock),
    Triple("climate", "Climate", Icons.Filled.Thermostat),
    Triple("charge", "Charge", Icons.Filled.Bolt),
    Triple("open", "Open", Icons.Filled.DirectionsCar),
)

/** Label for a tile action key (falls back to the key). */
internal fun tileActionLabel(cmd: String): String =
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
internal fun InlineSegmentedRow(
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
internal fun CarTilesHeader(name: String, img: String?, assignedCount: Int, totalTiles: Int) {
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
internal fun TileEmptyHint(text: String) {
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
internal fun QuickTilesManager(state: UiState, vm: AppViewModel) {
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
internal fun addTileToQuickSettings(context: Context, index: Int, cmd: String, label: String, unlocked: Boolean) {
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

internal fun tileSummary(cmd: String, climateTarget: String, presetName: String?): String = when (cmd) {
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
internal fun QuickTileCard(index: Int, vin: String, state: UiState, vm: AppViewModel) {
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
internal fun AddTilePill(label: String, onClick: () -> Unit) {
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
internal fun UpdateStatusChip(state: UiState) {
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
    // A soft lift while the card is OPEN: the expanded card scales up ~1.5%
    // and settles back -- the same "the thing that changed just came
    // forward" language the pebble cards' own open bounce already speaks,
    // so an expansion reads as the card arriving rather than the neighbours
    // merely moving out of its way. Pure draw-phase (graphicsLayer), so it
    // never re-measures the grid.
    val lift by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "settingsCardLift",
    )
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
            .graphicsLayer {
                val s = 1f + 0.015f * lift
                scaleX = s
                scaleY = s
            }
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
internal fun SecretRow(label: String, value: String) {
    var show by remember { mutableStateOf(false) }
    // A clean three-part row: label hugs the left, value hugs the button on
    // the right, no midpoint reservation. The old value used
    // `weight(1f, fill = false)`, which still RESERVED half the width and
    // parked the value mid-row with a void behind it -- the "weirdly
    // indented password" report. SpaceBetween on the row does the exact
    // thing that existed for nothing.
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            if (show) value else "•".repeat(value.length.coerceIn(4, 10)),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 168.dp),
        )
        Spacer(Modifier.width(10.dp))
        MorphTextButton(if (show) "Hide" else "Show", onClick = { show = !show })
    }
}

@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    // The same MorphButton every selectable option uses: pill at rest,
    // primaryContainer rounded square once chosen, pressed-state included.
    MorphButton(
        onClick = { onSelect() },
        active = selected,
        containerColor = buttonContainer(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        minHeight = 0.dp,
        modifier = Modifier.fillMaxWidth(),
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


// --- App PIN dialogs ------------------------------------------------------

/**
 * The Security card's PIN dialogs, one [GlassAlertDialog] shell with three
 * stages: verify the CURRENT PIN, then either enter a new one (set/change)
 * or confirm removal. Everything is the standard component set -- the shared
 * pin form, Morph buttons, the glass dialog shell -- so the PIN flow's UI
 * is no more bespoke than any other dialog in the app.
 *
 * The current-PIN stage routes through [AppViewModel.verifyAppPin] so it
 * enjoys the SAME lockout policy the lock screen has (and is not an
 * unguarded oracle for it): wrong PINs here count toward the app's own
 * rejection windows. A rejected attempt lands in [UiState.pinAttemptRejected]
 * (cleared via acknowledgePinRejection once this dialog has shown it); a
 * successful verify bumps [UiState.pinAcceptedTick], which advances the
 * stage.
 */
@Composable
internal fun PinDialogs(
    mode: String?,
    onDismiss: () -> Unit,
    vm: AppViewModel,
    state: UiState,
    canBio: Boolean,
) {
    if (mode == null) return
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    val title = when (mode) {
        "set" -> if (state.appPinSet) "Change PIN" else "Set up PIN"
        else -> "Remove PIN"
    }

    // current-PIN gate -> (new PIN entry | remove confirm)
    // A fresh setup (no PIN installed yet) has no "current PIN" to prove --
    // jump straight to choosing the new one. Change and Remove always gate
    // on knowing the existing PIN first.
    var stage by remember(mode) {
        mutableStateOf(if (mode == "set" && !state.appPinSet) "finish" else "current")
    }
    var currentPin by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }
    // Baselines, so the effects below react to CHANGES only -- the initial
    // composition must not treat a stale flag (left by an earlier lock
    // screen session, say) as a fresh event.
    var seenRejected by remember(mode) { mutableStateOf(state.pinAttemptRejected) }
    var seenTick by remember(mode) { mutableStateOf(state.pinAcceptedTick) }
    // Watch the verify outcome: a wrong PIN flags pinAttemptRejected (shown
    // as an inline error here, then acknowledged), a right one advances.
    LaunchedEffect(state.pinAttemptRejected) {
        if (state.pinAttemptRejected && !seenRejected) {
            seenRejected = true
            rejected = true
            currentPin = ""
            vm.acknowledgePinRejection()
        }
    }
    LaunchedEffect(state.pinAcceptedTick) {
        if (state.pinAcceptedTick != seenTick) {
            seenTick = state.pinAcceptedTick
            if (stage == "current") stage = "finish"
        }
    }
    val sanitize: (String) -> String = { it.take(8).filter { ch -> ch.isDigit() } }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Filled.Lock,
        text = {
            when (stage) {
                "current" -> {
                    Text(
                        "Enter your current PIN to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { currentPin = sanitize(it); rejected = false },
                        placeholder = { Text("Current PIN") },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (currentPin.length >= 4) {
                                haptics?.click()
                                vm.verifyAppPin(currentPin)
                            }
                        }),
                        isError = rejected,
                        supportingText = if (rejected) {
                            {
                                Text(
                                    "Incorrect PIN. Wrong attempts count toward a lockout.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.error,
                                )
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                "finish" -> {
                    when (mode) {
                        "set" -> {
                            Text(
                                "Choose a new 4-8 digit PIN.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            OnboardingPinForm(
                                existing = state.appPinSet,
                                onSet = { pin -> vm.setAppPin(pin) },
                            )
                        }
                        else -> {
                            Text(
                                if (canBio)
                                    "Removing the PIN leaves fingerprints as the only way to lock the app."
                                else
                                    "This device has no fingerprints, so removing the PIN means the app can never lock.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }
            }
        },
        buttons = {
            when (stage) {
                "current" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphTextButton("Cancel", onDismiss, modifier = Modifier.weight(1f))
                    MorphButton(
                        onClick = {
                            haptics?.click()
                            vm.verifyAppPin(currentPin)
                        },
                        enabled = currentPin.length >= 4,
                        modifier = Modifier.weight(1f),
                    ) { Text("Continue", fontWeight = FontWeight.SemiBold) }
                }
                "finish" -> {
                    if (mode == "remove") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MorphTextButton("Keep PIN", onDismiss, modifier = Modifier.weight(1f))
                            MorphButton(
                                onClick = { haptics?.heavy(); vm.removeAppPin(); onDismiss() },
                                containerColor = scheme.error,
                                contentColor = scheme.onError,
                                modifier = Modifier.weight(1f),
                            ) { Text("Remove PIN", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    MorphTextButton(
                        "Done",
                        onClick = { haptics?.click(); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

