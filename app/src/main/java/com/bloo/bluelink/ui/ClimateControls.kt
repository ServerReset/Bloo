package com.bloo.bluelink.ui

/**
 * Climate controls: ClimatePebble, SeatControl, seatTint, preset section, PresetPill,
 * ChargeLimitPill.
 */

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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.WheelHeatLevel
import com.bloo.bluelink.data.degValue
import com.bloo.uicommon.splitPillShapes
import com.bloo.uicommon.rememberConfirmArm
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.bloo.uicommon.ReorderColumn

/**
 * Reusable climate-control pieces: seat/wheel heat controls, preset pills and the charge-limit pill
 * (also used by EnergyPebble).
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
    // Deeper colour the stronger the setting; cross-fades through neutral between cooling (blues) and heating (reds).
    TintedLevelControl(label, current.label, seatTint(current), "seatTint", range, index, onChange)
}

/**
 * Steering wheel heat as a Low/High level control (see [WheelHeatLevel]; most brands only get a
 * boolean at the network layer). Same slider/tint shape as [SeatControl], minus the cool half.
 */
@Composable
internal fun WheelHeatControl(level: WheelHeatLevel, onChange: (WheelHeatLevel) -> Unit) {
    val range = WheelHeatLevel.entries.toList()
    TintedLevelControl("Steering wheel heat", level.label, wheelHeatTint(level), "wheelHeatTint", range, range.indexOf(level).coerceAtLeast(0), onChange)
}

/** A labelled level slider whose value text and thumb share one animated tint. */
@Composable
private fun <T> TintedLevelControl(
    label: String,
    valueLabel: String,
    targetTint: Color,
    tintLabel: String,
    range: List<T>,
    index: Int,
    onChange: (T) -> Unit,
) {
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = targetTint,
        animationSpec = lowPowerAwareSpring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = tintLabel,
    )
    Column {
        StepRow(label, valueLabel, valueColor = tint)
        LevelSlider(index, range, tint, onChange)
    }
}

/**
 * Steering wheel heat tint by intensity: the same light-to-dark red ramp as [seatTint]'s heat half.
 */
@Composable
internal fun wheelHeatTint(level: WheelHeatLevel): Color = when (level) {
    WheelHeatLevel.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    WheelHeatLevel.LOW -> androidx.compose.ui.graphics.lerp(Heat, MaterialTheme.colorScheme.surface, 0.45f)
    WheelHeatLevel.HIGH -> Heat
}

/**
 * Seat colour by intensity: light-to-dark blue for cool, red for heat, on the canonical
 * [Cool]/[Heat] tokens.
 */
@Composable
internal fun seatTint(level: SeatLevel): Color = when {
    level.isCool -> androidx.compose.ui.graphics.lerp(
        androidx.compose.ui.graphics.lerp(Cool, MaterialTheme.colorScheme.surface, 0.45f),
        Cool,
        ((level.apiValue - 3) / 2f).coerceIn(0f, 1f),
    )
    level.isHeat -> androidx.compose.ui.graphics.lerp(
        androidx.compose.ui.graphics.lerp(Heat, MaterialTheme.colorScheme.surface, 0.45f),
        Heat,
        ((level.apiValue - 6) / 2f).coerceIn(0f, 1f),
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
            // The heading lives inside the visibility gate so an empty section shows no heading and
            // it animates away with the last preset.
            SectionLabel("Presets")
            Spacer(Modifier.height(GapHairline))
            // Full-width reorderable rows: drag handle to re-rank, tap to apply.
            ReorderColumn(
                items = presets,
                keyOf = { it.id },
                onReorder = onReorder,
                spacing = GapRow,
                modifier = Modifier.fillMaxWidth(),
            ) { preset, itemDragHandle, _ ->
                AnimatedVisibility(
                    visible = preset.id !in deletingIds,
                    enter = scaleIn(tween(MotionMedium, easing = LinearOutSlowInEasing), initialScale = 0.88f) +
                        expandVertically(tween(MotionMedium)) + fadeIn(tween(MotionShort)),
                    exit = scaleOut(tween(MotionShort, easing = FastOutLinearInEasing), targetScale = 0.88f) +
                        shrinkVertically(tween(MotionShort)) + fadeOut(tween(MotionShort)),
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
                        modifier = itemDragHandle,
                    )
                }
            }
            Spacer(Modifier.height(GapHairline))
        }
    }
}

/** A compact "79° · Defrost · Heat" summary of what a preset will set. */
internal fun presetDetail(req: ClimateRequest, fahrenheit: Boolean): String {
    val parts = mutableListOf<String>()
    // Bare "°" rather than degLabel's "°F"/"°C": the unit is established by context. Conversion is
    // shared with degLabel.
    parts += "${degValue(req.tempF.toDouble(), fahrenheit)}°"
    if (req.defrost) parts += "Defrost"
    val seats = listOf(req.seatFrontLeft, req.seatFrontRight, req.seatRearLeft, req.seatRearRight)
    if (seats.any { it.isHeat }) parts += "Heat"
    if (seats.any { it.isCool }) parts += "Cool"
    if (req.steeringWheelHeat.isOn) parts += "Wheel"
    return parts.joinToString(" · ")
}

/**
 * A two-segment split button for a saved preset (M3 Expressive connected-button group): a wider
 * "start" half and a narrow "delete" half, pill on the outer edge and smaller radius on the inner,
 * separated by a real gap.
 */
@Composable
internal fun PresetPill(
    name: String,
    detail: String,
    active: Boolean,
    onStart: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    // Delete needs a second tap to confirm (same 4s auto-reset as Sign out); a mis-aimed tap beside
    // Apply dropped a preset irreversibly.
    val confirm = rememberConfirmArm()
    // Corners use CornerSize(Dp) directly, with no measured row height. Remembered because this is
    // one item body per preset in a ReorderColumn and splitPillShapes is a pure function.
    val leftShapeForCorner: (Float, Int) -> Shape = remember {
        { morph, cp -> splitPillShapes(morph, cp).first }
    }
    val rightShapeForCorner: (Float, Int) -> Shape = remember {
        { morph, cp -> splitPillShapes(morph, cp).second }
    }

    // The drag handle wraps the whole pill so long-press anywhere reorders.
    ExpressiveButtonRow(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        spacing = SplitSeam,
        verticalAlignment = Alignment.CenterVertically,
        // One split pill, not two adjacent buttons (see `wrap`).
        wrap = false,
    ) {
        // Apply half: snowflake icon plus preset name; MorphButton, primary fill when applied.
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
                            color = mutedContentColor(),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // Delete nub: inner (left) corners match the gap, outer (right) are pill-rounded; error
        // colours while armed.
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
 * Two-segment split pill for the charge-limit control, styled like the climate presets: wide left
 * half shows the current value and hosts the inline slider; narrow right half ("Set ⚡") sends the
 * command.
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
    // Same split-pill geometry as the preset pill. Remembered because this pill recomposes on every
    // slider-drag tick.
    val leftShapeForCorner: (Float, Int) -> Shape = remember {
        { morph, cp -> splitPillShapes(morph, cp).first }
    }
    val rightShapeForCorner: (Float, Int) -> Shape = remember {
        { morph, cp -> splitPillShapes(morph, cp).second }
    }

    Column(Modifier.fillMaxWidth()) {
        // Same group conversion as PresetPill.
        ExpressiveButtonRow(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            spacing = SplitSeam,
            wrap = false,
        ) {
            // Left half: label. Tapping bumps the limit one step, wrapping to 50% after 100%.
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
                // Announce both the value and that this half is a stepper, distinct from "Set".
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
            // Right half: "Set" nub. Inner (left) corners match the gap, outer are pill-rounded;
            // active while the command is in flight.
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
                // The pending spinner must not fade with the disabled content, so pin the full
                // tone.
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
        Spacer(Modifier.height(GapRow))
        AnimatedSlider(
            value = limit.toFloat(),
            onValueChange = { onValueChange((it / 10f).roundToInt() * 10) },
            valueRange = CHARGE_LIMIT_RANGE.first.toFloat()..CHARGE_LIMIT_RANGE.last.toFloat(),
            steps = 4,
        )
        Spacer(Modifier.height(GapHairline))
    }
}

/**
 * The stepped level slider both [SeatControl] and [WheelHeatControl] drive: a discrete [range], the
 * current [index] into it, and the tint the level (and its label) wears.
 */
@Composable
private fun <T> LevelSlider(index: Int, range: List<T>, tint: Color, onChange: (T) -> Unit) {
    AnimatedSlider(
        value = index.toFloat(),
        onValueChange = { onChange(range[it.roundToInt().coerceIn(0, range.lastIndex)]) },
        valueRange = 0f..range.lastIndex.toFloat(),
        steps = (range.size - 2).coerceAtLeast(0),
        accent = tint,
    )
}
