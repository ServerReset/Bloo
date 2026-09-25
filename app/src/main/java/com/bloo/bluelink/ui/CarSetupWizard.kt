@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.PinCrypto
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.VehiclePlatform
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.max

/**
 * The per-car feature setup wizard (heated seats, powertrain, steering wheel heat,
 * etc.) shown when a new car needs configuring, plus its small fireworks-on-finish
 * effect. Split out of Onboarding.kt to separate the first-run onboarding flow from
 * this per-car wizard flow.
 */


/**
 * Standalone wizard shown when a new car is detected after first-run onboarding.
 * Mandatory — navigates to the garage only when every car in [vins] is configured.
 */
@Composable
internal fun CarSetupWizardScreen(vm: AppViewModel, vins: List<String>) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler {}
    val vehicles = remember(state.vehicles, vins) { state.vehicles.filter { it.vin in vins } }
    val pages = remember(vehicles) { buildSetupPages(vehicles) }
    CarFeatureWizard(
        vm = vm,
        pages = pages,
        onComplete = { vm.finishCarSetup(vins) },
    )
}

/**
 * Renders a linear, swipe-free (button-driven) wizard over [pages]: a top
 * progress bar, the current page's content cross-faded/slid in via
 * [AnimatedContent] keyed on [pageIndex], and a Back/Next footer. Only
 * [pageIndex] is local state -- advancing or retreating just mutates that
 * int, which drives both the progress bar's target and which page content
 * is shown. Reaching "Next" on the last page calls [onComplete] instead of
 * advancing further.
 */
@Composable
internal fun CarFeatureWizard(
    vm: AppViewModel,
    pages: List<WizardPage>,
    onComplete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val state by vm.state.collectAsStateWithLifecycle()

    var pageIndex by remember { mutableIntStateOf(0) }

    if (pages.isEmpty()) return
    fun goNext() {
        if (pageIndex < pages.lastIndex) {
            pageIndex++
        } else {
            onComplete()
        }
    }
    fun goBack() {
        if (pageIndex > 0) pageIndex--
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.surfaceContainerHigh, scheme.surface))),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            // Progress bar across the top.
            val progress = if (pages.size > 1) pageIndex.toFloat() / (pages.lastIndex.toFloat()) else 1f
            val animatedProgress by animateFloatAsState(progress, tween(WizardProgressDurationMs), label = "wizProgress")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(scheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(4.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(scheme.primary, scheme.tertiary),
                            ),
                        ),
                )
            }

            // Slide-animated page content.
            AnimatedContent(
                targetState = pageIndex,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it * dir } + fadeIn(tween(WizardStepFadeInDurationMs))) togetherWith
                        (slideOutHorizontally { -it * dir } + fadeOut(tween(180)))
                },
                modifier = Modifier.weight(1f),
                label = "wizPage",
            ) { idx ->
                val pg = pages.getOrNull(idx) ?: return@AnimatedContent
                val veh = pg.vin?.let { vin -> state.vehicles.firstOrNull { it.vin == vin } }
                val sc = veh?.let { state.seatConfigs[it.vin] } ?: com.bloo.bluelink.data.SeatConfig()
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding24()) {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        when (pg.kind) {
                            WizardStepKind.POWERTRAIN -> WizardPowertrainPage(veh, state, vm)
                            WizardStepKind.PLATFORM -> WizardPlatformPage(veh, state, vm)
                            WizardStepKind.SEATS -> WizardSeatsPage(veh, sc, vm)
                            WizardStepKind.STEERING -> WizardSteeringPage(veh, sc, vm)
                        }
                    }
                }
            }

            // Back / Next navigation strip.
            Row(
                Modifier
                    .fillMaxWidth()
                    .paddingHorizontal24Vertical16(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = pageIndex > 0,
                    modifier = Modifier.weight(1f),
                    enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(120)),
                ) {
                    // MorphButton, not OutlinedCard -- same fix as the main
                    // OnboardingScreen's own Back button (this wizard is the
                    // near-identical "a car showed up after first run" cousin
                    // of that flow, and had copied the same stock-chrome
                    // button along with everything else). Picks up MorphButton's
                    // own haptic click for free too, which this Back button
                    // was missing outright -- unlike goNext below, whose
                    // MorphButton already had it.
                    //
                    // Wrapped in SafeExpansiveButton, matching goNext beside it and the main
                    // OnboardingScreen's own Back/Next -- it was missing the press-growth
                    // affordance every other button pair in the app gets.
                    val backSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(interactionSource = backSource, enabled = true) {
                        MorphButton(
                            onClick = ::goBack,
                            interactionSource = backSource,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            border = BorderStroke(1.dp, scheme.outlineVariant),
                        ) {
                            Text("Back", style = ButtonLabelStyle)
                        }
                    }
                }
                val nextSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = nextSource,
                    enabled = true,
                    modifier = Modifier.weight(if (pageIndex > 0) 2f else 1f),
                ) {
                    MorphButton(
                        onClick = ::goNext,
                        active = true,
                        interactionSource = nextSource,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        val isLast = pageIndex == pages.lastIndex
                        // MorphButtonLabel, not a hand-rolled Icon+Spacer+Text.
                        val wizardNextIcon: ImageVector = if (isLast) AppIcons.CheckCircle else AppIcons.Check
                        val wizardNextText: String = if (isLast) "Done" else "Next"
                        MorphButtonLabel(
                            wizardNextIcon,
                            wizardNextText,
                            pending = false,
                        )
                    }
                }
            }
        }

    }
}

/**
 * One wizard page: a static list of the four [Powertrain] options, each a
 * selectable [Surface] row. Selecting a row calls [AppViewModel.setPowertrain]
 * directly (there's no local "pending" selection) -- the row's highlighted
 * state is driven straight off `state.powertrainOf(vehicle)`, so the whole
 * row list recomposes the instant the view model's state updates.
 */
@Composable
internal fun WizardPowertrainPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    state: UiState,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    WizardPageHeader(
        "Powertrain",
        "What powers the ${vehicle.name}?",
        "Bloo uses this to show the right status tiles: battery percentage for EVs, " +
            "fuel level for gas, or both for plug-in hybrids.",
    )
    val current = state.powertrainOf(vehicle)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.bloo.bluelink.data.Powertrain.entries.forEach { pt ->
            val selected = current == pt
            val (icon, label, desc) = when (pt) {
                com.bloo.bluelink.data.Powertrain.GAS -> Triple("⛽", "Gasoline", "Combustion engine only")
                com.bloo.bluelink.data.Powertrain.HYBRID -> Triple("🔋", "Hybrid", "Gas + small electric motor (no plug)")
                com.bloo.bluelink.data.Powertrain.PHEV -> Triple("🔌", "Plug-in Hybrid", "Gas + large battery you can charge")
                com.bloo.bluelink.data.Powertrain.EV -> Triple("", "Electric", "Battery-only, no fuel tank")
            }
            // Same MorphButton as every other selector: pill at rest, fills
            // primaryContainer as a rounded square once chosen.
            MorphButton(
                onClick = { vm.setPowertrain(vehicle, pt) },
                modifier = Modifier.fillMaxWidth(),
                active = selected,
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                activeContainerColor = scheme.primaryContainer,
                activeContentColor = scheme.onPrimaryContainer,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                contentPadding = PaddingValues(16.dp),
                minHeight = 0.dp,
            ) {
                Text(icon, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.7f) else scheme.onSurfaceVariant)
                }
                if (selected) Icon(AppIcons.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

/**
 * One wizard page: which head-unit generation this Hyundai/Genesis US car
 * has, Gen5W or ccNC -- only ever reached for a vehicle where
 * [platformOverridable] is true (see [buildSetupPages]), same two-option
 * shape as [WizardPowertrainPage] otherwise: a selectable [Surface] row per
 * option, driven straight off `state.platformOf(vehicle)`, no local
 * "pending" selection.
 */
@Composable
internal fun WizardPlatformPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    state: UiState,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    WizardPageHeader(
        "Head-unit generation",
        "Which generation is the ${vehicle.name}?",
        "Bloo can't always tell these apart from the API alone. Confirm it here " +
            "so features like Trips only show up when they're actually available.",
    )
    val current = state.platformOf(vehicle)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VehiclePlatform.entries.forEach { pt ->
            val selected = current == pt
            val (label, desc) = when (pt) {
                VehiclePlatform.GEN5W -> "Gen5W" to "Older head unit -- no Trips, no connected-car store"
                VehiclePlatform.CCNC -> "ccNC" to "Newer head unit -- Trips and the connected-car store, where the backend supports them"
            }
            // Same MorphButton as the powertrain page: pill at rest, fills
            // primaryContainer as a rounded square once chosen.
            MorphButton(
                onClick = { vm.setPlatform(vehicle, pt) },
                modifier = Modifier.fillMaxWidth(),
                active = selected,
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                activeContainerColor = scheme.primaryContainer,
                activeContentColor = scheme.onPrimaryContainer,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                contentPadding = PaddingValues(16.dp),
                minHeight = 0.dp,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.7f) else scheme.onSurfaceVariant)
                }
                if (selected) Icon(AppIcons.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * One wizard page: renders a heat/cool toggle-chip row per [SeatPositions]
 * entry, each row wired straight to its own persisted flag via
 * [AppViewModel.setSeatFlag] -- no local staging state, so a tap is reflected
 * immediately once the view model emits the updated [SeatConfig].
 */
@Composable
internal fun WizardSeatsPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    seats: com.bloo.bluelink.data.SeatConfig,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    WizardPageHeader(
        "Seat comfort",
        "What does the ${vehicle.name} have?",
        "Bloo shows only the controls your car actually supports. Skip any seats you don't have.",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh)
            .padding16(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SeatPositions.forEachIndexed { i: Int, pos: SeatPosition ->
            if (i > 0) SectionDivider(alpha = 0.5f)
            // SeatConfigRow (Rows.kt), as on the car page above -- see its comment there
            // for why the wizard no longer keeps its own copy of this row.
            SeatConfigRow(
                pos.label,
                pos.heat(seats),
                pos.cool(seats),
                onHeat = { enabled: Boolean -> vm.setSeatFlag(vehicle, pos.heatKey, enabled) },
                onCool = { enabled: Boolean -> vm.setSeatFlag(vehicle, pos.coolKey, enabled) },
            )
        }
    }
    BodySmallText(
                "You can change these any time in Settings under your car card.",
        color = scheme.onSurfaceVariant,
    )
}

/** The four seat positions, each pairing its persisted heat/cool flag keys with
 *  the matching [SeatConfig] fields — the seat matrix lives here once instead of
 *  being hand-written at each of the three places seats are configured. */
internal data class SeatPosition(
    val label: String,
    val heatKey: String,
    val coolKey: String,
    val heat: (SeatConfig) -> Boolean,
    val cool: (SeatConfig) -> Boolean,
)

internal val SeatPositions = listOf(
    SeatPosition("Driver", "dh", "dc", { it.driverHeat }, { it.driverCool }),
    SeatPosition("Front passenger", "ph", "pc", { it.passHeat }, { it.passCool }),
    SeatPosition("Rear left", "rlh", "rlc", { it.rearLeftHeat }, { it.rearLeftCool }),
    SeatPosition("Rear right", "rrh", "rrc", { it.rearRightHeat }, { it.rearRightCool }),
)

/** One wizard page for the single "heated steering wheel" flag; same
 *  direct-to-view-model wiring as the other wizard pages, just one row. */
@Composable
internal fun WizardSteeringPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    seats: com.bloo.bluelink.data.SeatConfig,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    WizardPageHeader(
        "Climate features",
        "Any extras on the ${vehicle.name}?",
        "Enable what the car actually has. These control which options appear in the climate command.",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh)
            // The horizontal inset used to live on the hand-rolled row itself; ToggleRow
            // (like every settings control) leaves its container to supply it.
            .paddingHorizontal16Vertical12(),
    ) {
        // ToggleRow with its own `description`, not the hand-rolled WizardFeatureToggle
        // that used to live below this: that row already drew MorphToggleTrack and
        // ToggleRow's own toggleable/haptics, so all it really contributed was a second
        // label+caption type scale (bodyLarge + MutedText vs ToggleRow's bodyMedium +
        // SettingsCaption) for the same setting the Settings card shows as a ToggleRow.
        ToggleRow(
            "Heated steering wheel",
            seats.steeringWheel,
            description = "Warm the steering wheel via the remote climate command",
        ) { vm.setSeatFlag(vehicle, "sw", it) }
    }
    BodySmallText(
        "That's it for ${vehicle.name}. Tap Next to continue.",
        color = scheme.onSurfaceVariant,
    )
}

internal class Burst(val x: Float, val y: Float, val start: Float, val life: Float, val hue: Float, val count: Int, val maxR: Float)
/**
 * A short, lightweight particle-burst fireworks animation drawn on a Canvas.
 *
 * Seven [Burst]s are generated once (`remember`) with randomized position,
 * start-delay, lifetime, hue, particle count, and max radius. A single
 * [Animatable] `t` is driven from 0 to 1 over 2.6s and is the *only* thing
 * that changes over time; each burst reads its own local progress
 * `(t - start) / life` from that shared clock and is invisible outside
 * [0, 1]. For a visible burst, particles are placed evenly around a circle
 * of growing radius `local * maxR`, faded out via `alpha = 1 - local`, and
 * given a small downward drift (`local² * height * 0.06`) to mimic gravity.
 * Nothing here loops -- once `t` reaches 1 all bursts are permanently done.
 */
@Composable
internal fun FireworksOverlay(modifier: Modifier = Modifier) {
    val bursts = remember {
        val r = kotlin.random.Random(System.nanoTime())
        List(7) {
            Burst(
                x = r.nextFloat() * 0.8f + 0.1f,
                y = r.nextFloat() * 0.5f + 0.12f,
                start = r.nextFloat() * 0.55f,
                life = r.nextFloat() * 0.25f + 0.35f,
                hue = r.nextFloat() * 360f,
                count = 18 + r.nextInt(14),
                maxR = r.nextFloat() * 0.12f + 0.14f,
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2600)) }
    Canvas(modifier) {
        bursts.forEach { b ->
            val local = ((t.value - b.start) / b.life)
            if (local <= 0f || local >= 1f) return@forEach
            val cx = b.x * size.width
            val cy = b.y * size.height
            val r = local * b.maxR * size.height
            val alpha = (1f - local).coerceIn(0f, 1f)
            val color = Color.hsv(b.hue, 0.85f, 1f).copy(alpha = alpha)
            for (k in 0 until b.count) {
                val ang = (k.toFloat() / b.count) * (2f * Math.PI.toFloat())
                val px = cx + kotlin.math.cos(ang) * r
                val py = cy + kotlin.math.sin(ang) * r + local * local * size.height * 0.06f
                drawCircle(color, radius = 5f * alpha + 1.5f, center = Offset(px, py))
            }
        }
    }
}
