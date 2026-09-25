@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Climate controls: ClimatePebble, SeatControl, seatTint, preset section,
 * PresetPill, ChargeLimitPill -- extracted from Pebbles.kt.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.WheelHeatLevel
import com.bloo.bluelink.data.degValue
import com.bloo.bluelink.data.smartClimateTargetF
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.splitPillShapes
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.smartClimateIsCooling
import com.bloo.bluelink.data.CLIMATE_DURATION_RANGE
import com.bloo.bluelink.data.CLIMATE_EXTENDED_DURATION_RANGE
import com.bloo.uicommon.rememberConfirmArm
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.bloo.uicommon.ReorderColumn

/**
 * Reusable climate-control pieces: seat/wheel heat controls, preset pills, and the
 * charge-limit pill (also used by EnergyPebble). Split out of ClimatePebble.kt to
 * separate the main pebble from these smaller, independently reusable components.
 */

@Composable
internal fun SeatControl(
    label: String,
    level: SeatLevel,
    canCool: Boolean,
    canHeat: Boolean,
    onChange: (SeatLevel) -> Unit,
) {
    val range = SeatLevel.rangeFor(canCool, canHeat)
    if (range.size <= 1) return
    val index = range.indexOf(level).let { if (it < 0) range.indexOf(SeatLevel.OFF) else it }
    val current = range.getOrNull(index) ?: range.firstOrNull() ?: return
    // Deeper colour the stronger the setting; smoothly cross-fades as you slide
    // through neutral between cooling (blues) and heating (reds).
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = seatTint(current),
        animationSpec = lowPowerAwareSpring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "seatTint",
    )
    Column {
        // The level text (e.g. "High cool") wears the slider's colour, so OFF is
        // neutral, cooling reads blue and heating reads red - no caption needed.
        StepRow(label, current.label, valueColor = tint)
        AnimatedSlider(
            value = index.toFloat(),
            onValueChange = { onChange(range[it.roundToInt().coerceIn(0, range.lastIndex)]) },
            valueRange = 0f..range.lastIndex.toFloat(),
            steps = (range.size - 2).coerceAtLeast(0),
            accent = tint,
        )
    }
}

/**
 * Steering wheel heat, as a real Low/High level control rather than a plain on/off
 * toggle -- see [WheelHeatLevel]'s own doc for why most brands still only ever get a
 * boolean out of this at the network layer, while Kia's request gets a real second
 * step. Same slider/tint shape as [SeatControl] just below, minus the cool half (the
 * steering wheel has no cooling mode).
 */
@Composable
internal fun WheelHeatControl(level: WheelHeatLevel, onChange: (WheelHeatLevel) -> Unit) {
    val range = WheelHeatLevel.entries.toList()
    val index = range.indexOf(level).coerceAtLeast(0)
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = wheelHeatTint(level),
        animationSpec = lowPowerAwareSpring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "wheelHeatTint",
    )
    Column {
        StepRow("Steering wheel heat", level.label, valueColor = tint)
        AnimatedSlider(
            value = index.toFloat(),
            onValueChange = { onChange(range[it.roundToInt().coerceIn(0, range.lastIndex)]) },
            valueRange = 0f..range.lastIndex.toFloat(),
            steps = (range.size - 2).coerceAtLeast(0),
            accent = tint,
        )
    }
}

/** Steering wheel heat tint by intensity -- same light->dark red ramp [seatTint] uses
 *  for a seat's own heat half. */
@Composable
internal fun wheelHeatTint(level: WheelHeatLevel): Color = when (level) {
    WheelHeatLevel.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    WheelHeatLevel.LOW -> Color(0xFFFF8A80)
    WheelHeatLevel.HIGH -> Color(0xFFC62828)
}

/** Seat colour by intensity: light->dark blue for cool, light->dark red for heat. */
@Composable
internal fun seatTint(level: SeatLevel): Color = when {
    level.isCool -> androidx.compose.ui.graphics.lerp(
        Color(0xFF82B1FF), Color(0xFF1A45C0), ((level.apiValue - 3) / 2f).coerceIn(0f, 1f),
    )
    level.isHeat -> androidx.compose.ui.graphics.lerp(
        Color(0xFFFF8A80), Color(0xFFC62828), ((level.apiValue - 6) / 2f).coerceIn(0f, 1f),
    )
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// --- Climate presets section ----------------------------------------------

@Composable
internal fun ClimatePresetSection(
    presets: List<ClimatePreset>,
    activeId: String?,
    fahrenheit: Boolean,
    onStart: (ClimatePreset) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<ClimatePreset>) -> Unit,
) {
    // Track IDs mid-exit so the item stays visible until its shrink animation ends.
    var deletingIds by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = presets.isNotEmpty(),
        enter = expandEnterSized(Alignment.Bottom),
        exit = expandExitSized(Alignment.Bottom),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // The heading lives INSIDE the visibility gate. Outside it, a user with no saved
            // presets got a "Presets" heading over nothing at all -- a whole line of a cover
            // screen spent announcing an empty section. It also means the label animates away
            // with the last preset instead of being left behind.
            SectionLabel("Presets")
            Spacer(Modifier.height(SettingsGapHairline))
            // Full-width reorderable rows: drag handle to re-rank, tap to apply.
            ReorderColumn(
                items = presets,
                keyOf = { it.id },
                onReorder = onReorder,
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { preset, dragHandle, _ ->
                AnimatedVisibility(
                    visible = preset.id !in deletingIds,
                    enter = scaleIn(tween(240, easing = LinearOutSlowInEasing), initialScale = 0.88f) +
                        expandVertically(tween(260)) + fadeIn(tween(200)),
                    exit = scaleOut(tween(180, easing = FastOutLinearInEasing), targetScale = 0.88f) +
                        shrinkVertically(tween(220)) + fadeOut(tween(160)),
                ) {
                    PresetPill(
                        name = preset.name,
                        detail = presetDetail(preset.request, fahrenheit),
                        active = preset.id == activeId,
                        onStart = { onStart(preset) },
                        onDelete = {
                            val id = preset.id
                            scope.launch {
                                deletingIds = deletingIds + id
                                delay(240)
                                onDelete(id)
                                deletingIds = deletingIds - id
                            }
                        },
                        dragHandle = dragHandle,
                    )
                }
            }
            Spacer(Modifier.height(SettingsGapHairline))
        }
    }
}

/** A compact "79° · Defrost · Heat" summary of what a preset will set. */
internal fun presetDetail(req: ClimateRequest, fahrenheit: Boolean): String {
    val parts = mutableListOf<String>()
    // Bare "°" rather than degLabel's "°F"/"°C": this is a compact one-line summary
    // where the unit is already established by everything around it. The CONVERSION
    // is shared now though -- this used to re-inline the °F-to-°C arithmetic, so the
    // rounding rule lived here as well as in degLabel and could drift from it.
    parts += "${degValue(req.tempF.toDouble(), fahrenheit)}°"
    if (req.defrost) parts += "Defrost"
    val seats = listOf(req.seatFrontLeft, req.seatFrontRight, req.seatRearLeft, req.seatRearRight)
    if (seats.any { it.isHeat }) parts += "Heat"
    if (seats.any { it.isCool }) parts += "Cool"
    if (req.steeringWheelHeat.isOn) parts += "Wheel"
    return parts.joinToString(" · ")
}


/**
 * A two-segment split button for a saved preset, styled after M3 Expressive
 * connected-button group #5: a wider "start" half and a narrow "delete" half,
 * each a pill on its outer edge with a smaller radius on the inner edge. The two
 * are separated by a real gap (not a drawn line) so the pebble background shows
 * through and they read as distinct buttons.
 *
 * Tapping the start half loads the preset into the climate controls and fires it;
 * while it is the [active] (currently applied) preset, that half morphs from a
 * pill into a rounded rectangle and fills with the running-climate highlight,
 * exactly like the Start button when climate is on. The delete half removes it.
 */
@Composable
internal fun PresetPill(
    name: String,
    detail: String,
    active: Boolean,
    onStart: () -> Unit,
    onDelete: () -> Unit,
    dragHandle: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    // Delete was a single un-confirmable tap right beside the much larger,
    // frequently-tapped Apply half -- a slightly mis-aimed tap silently and
    // irreversibly dropped a saved preset. Now requires a second tap, same
    // "tap again to confirm" pattern (with the same 4s auto-reset) used for
    // Sign out.
    val confirm = rememberConfirmArm()
    // No measured row height any more. These shapes used to be derived from one -- the row was
    // measured with onSizeChanged, the height written to state, and the whole row recomposed to
    // rebuild the shapes, a measure -> state -> recompose loop that ran whenever the height
    // changed. All it was computing was "16dp, expressed as a percent of this row", which
    // CornerSize(Dp) states directly. The morphed OUTER corner goes back to the app-wide
    // MorphedCornerPercent every other button uses, which is one less thing these two pills do
    // differently from everything around them.
    // Remembered, not rebuilt every recomposition: this composable is one item's body inside
    // a ReorderColumn (one call per preset, per recomposition of ANY preset's row), and these
    // two capture nothing that ever changes -- splitPillShapes is a pure function of its own
    // arguments -- so a fresh lambda pair here bought nothing but per-item allocation on every
    // reorder-driven recomposition of the list.
    val leftShapeForCorner: (Float, Int) -> Shape = remember {
        { morph, cp -> splitPillShapes(morph, cp).first }
    }
    val rightShapeForCorner: (Float, Int) -> Shape = remember {
        { morph, cp -> splitPillShapes(morph, cp).second }
    }

    // The drag handle wraps the whole pill so long-press anywhere reorders.
    // A real button group, not a Row of separately-wrapped buttons. groupWeight on the Apply
    // half is what makes it span the row (Modifier.weight cannot reach a group member), and
    // being members is what lets pressing either half take width from the other instead of
    // shoving it.
    ExpressiveButtonRow(
        modifier = dragHandle.fillMaxWidth().height(IntrinsicSize.Min),
        spacing = 3.dp,
        verticalAlignment = Alignment.CenterVertically,
        // One split pill, not two buttons that happen to be adjacent -- see `wrap`.
        wrap = false,
    ) {
        // Apply half — snowflake icon plus the preset name. The shared
        // MorphButton: pill when idle, rounded rectangle + primary fill when
        // this preset is the applied one. With expansion animation.
        val applySource = remember { MutableInteractionSource() }
        MorphButton(
            onClick = { onStart() },
            onClickHaptic = { haptics?.click() },
            active = active,
            interactionSource = applySource,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
            shapeForCorner = leftShapeForCorner,
            pillCornerPercent = 50f,
            morphedCornerPercent = MorphedCornerPercent,
            minHeight = 0.dp,
            groupWeight = 1f,
            modifier = Modifier.fillMaxHeight(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AcUnit, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
                Spacer(Modifier.width(ButtonIconGap))
                Column {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // Delete nub — inner (left) corners match the gap, outer (right) corners
        // are pill-rounded; same MorphButton as the Apply half, just mirrored
        // corners and error colours while armed. With expansion animation.
        val deleteSource = remember { MutableInteractionSource() }
        MorphButton(
            onClick = {
                haptics?.tick()
                if (confirm.armed) onDelete() else confirm.arm()
            },
            interactionSource = deleteSource,
            containerColor = if (confirm.armed) MaterialTheme.colorScheme.error else buttonContainer(),
            contentColor = if (confirm.armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
            contentPadding = PaddingValues(horizontal = 14.dp),
            shapeForCorner = rightShapeForCorner,
            pillCornerPercent = 50f,
            morphedCornerPercent = MorphedCornerPercent,
            minHeight = 0.dp,
            modifier = Modifier.fillMaxHeight(),
        ) {
            Icon(
                AppIcons.Close,
                contentDescription = if (confirm.armed) "Confirm delete $name" else "Delete $name",
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

// --- Charge limits --------------------------------------------------------

/**
 * Two-segment split pill for the charge-limit control, styled like the climate
 * presets: wide left half shows the current value and hosts the inline slider;
 * narrow right half ("Set ⚡") sends the command. Morphs from pill to rounded
 * rectangle when pressed, identical motion to [PresetPill].
 */
@Composable
internal fun ChargeLimitPill(
    label: String,
    limit: Int,
    pending: Boolean,
    enabled: Boolean,
    icon: ImageVector = AppIcons.Bolt,
    onValueChange: (Int) -> Unit,
    onApply: () -> Unit,
) {
    val haptics = LocalHaptics.current
    // Same split-pill geometry as the preset pill above, and the same reason there is no
    // measured row height here any more -- see that one's note. Remembered for the same
    // reason too: this pill recomposes on every slider-drag tick (onValueChange), so an
    // unremembered lambda pair here was rebuilt on every single drag frame, not just once
    // per reorder like PresetPill's.
    val leftShapeForCorner: (Float, Int) -> Shape = remember {
        { morph, cp -> splitPillShapes(morph, cp).first }
    }
    val rightShapeForCorner: (Float, Int) -> Shape = remember {
        { morph, cp -> splitPillShapes(morph, cp).second }
    }

    Column(Modifier.fillMaxWidth()) {
        // Same group conversion as PresetPill above -- see its note.
        ExpressiveButtonRow(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            spacing = 3.dp,
            wrap = false,
        ) {
            // Left half — label. Tapping bumps the limit up by one step, wrapping
            // back to 50% after 100%, for quick keyboard-free adjustment. With expansion.
            val incrementSource = remember { MutableInteractionSource() }
            MorphButton(
                onClick = { onValueChange(if (limit >= 100) 50 else limit + 10) },
                onClickHaptic = { haptics?.tick() },
                enabled = enabled,
                interactionSource = incrementSource,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                shapeForCorner = leftShapeForCorner,
                pillCornerPercent = 50f,
                morphedCornerPercent = MorphedCornerPercent,
                minHeight = 0.dp,
                // Both the current value and what tapping actually does (bump
                // by 10%, wrapping at 100%) were purely visual -- TalkBack
                // announced only the label text with no indication this half
                // was itself a stepper, distinct from "Set" on the right.
                groupWeight = 1f,
                modifier = Modifier.fillMaxHeight()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$label, $limit percent"
                        onClick(label = "Increase by 10 percent") {
                            onValueChange(if (limit >= 100) 50 else limit + 10)
                            true
                        }
                    },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
                    Spacer(Modifier.width(ButtonIconGap))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    RollingNumber(
                        text = "$limit%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Right half — "Set" nub. Inner (left) corners match the gap; outer
            // (right) are pill-rounded. Active while the command is in flight.
            // With expansion animation.
            val applySource = remember { MutableInteractionSource() }
            MorphButton(
                onClick = { onApply() },
                onClickHaptic = { haptics?.heavy() },
                enabled = enabled && !pending,
                active = pending,
                interactionSource = applySource,
                contentPadding = PaddingValues(horizontal = 18.dp),
                shapeForCorner = rightShapeForCorner,
                pillCornerPercent = 50f,
                morphedCornerPercent = MorphedCornerPercent,
                minHeight = 0.dp,
                // The pending spinner must not fade with the disabled content
                // (Surface didn't dim it before), so pin the full tone.
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxHeight(),
            ) {
                if (pending) {
                    LoadingIndicator(Modifier.size(18.dp))
                } else {
                    Text("Set", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AnimatedSlider(
            value = limit.toFloat(),
            onValueChange = { onValueChange((it / 10f).roundToInt() * 10) },
            valueRange = CHARGE_LIMIT_RANGE.first.toFloat()..CHARGE_LIMIT_RANGE.last.toFloat(),
            steps = 4,
        )
        Spacer(Modifier.height(6.dp))
    }
}
