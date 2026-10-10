package com.bloo.bluelink.ui

/**
 * Climate controls: ClimatePebble, SeatControl, seatTint, preset section, PresetPill,
 * ChargeLimitPill.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.WheelHeatLevel
import com.bloo.bluelink.data.degValue
import com.bloo.uicommon.ReorderColumn
import com.bloo.uicommon.rememberConfirmArm
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

/**
 * Steering wheel heat tint by intensity -- the same light->dark red ramp [seatTint] uses for a
 * seat's own heat half, both derived from the canonical heat red so the two never drift onto
 * different pastels.
 */
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
    // Delete needs a second tap to confirm (auto-resets, like Sign out): a mis-aimed tap beside Apply dropped a preset
    // for good.
    val confirm = rememberConfirmArm()
    ButtonCluster(
        listOf(
            // Apply: snowflake plus the preset's name, primary fill when applied.
            ClusterButton(
                onClick = onStart,
                onClickHaptic = { haptics?.click() },
                active = active,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                weight = 1f,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AcUnit, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
                    Spacer(Modifier.width(ButtonIconGap))
                    Column {
                        Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (detail.isNotBlank()) {
                            Text(detail, style = MaterialTheme.typography.labelSmall, color = mutedContentColor(), maxLines = 1)
                        }
                    }
                }
            },
            // Delete: the deny red while armed.
            ClusterButton(
                onClick = {
                    haptics?.tick()
                    if (confirm.armed) onDelete() else confirm.arm()
                },
                containerColor = if (confirm.armed) denyTone().container else buttonContainer(),
                contentColor = if (confirm.armed) denyTone().content else MaterialTheme.colorScheme.onSurface,
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Icon(
                    AppIcons.Close,
                    contentDescription = if (confirm.armed) "Confirm delete $name" else "Delete $name",
                    modifier = Modifier.size(15.dp),
                )
            },
        ),
        modifier.fillMaxWidth(),
    )
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
    val step = { onValueChange(if (limit >= 100) 50 else limit + 10) }
    Column(Modifier.fillMaxWidth()) {
        ButtonCluster(
            listOf(
                // The value half: tapping bumps the limit one step, wrapping to 50% after 100%.
                ClusterButton(
                    onClick = step,
                    onClickHaptic = { haptics?.tick() },
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                    weight = 1f,
                    // TalkBack hears both the value and that this half is itself a stepper, distinct from "Set".
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "$label, $limit percent"
                        onClick(label = "Increase by 10 percent") { step(); true }
                    },
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
                        Spacer(Modifier.width(ButtonIconGap))
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        RollingNumber(text = "$limit%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                },
                // "Set": sends the command; active while it is in flight.
                ClusterButton(
                    onClick = onApply,
                    onClickHaptic = { haptics?.heavy() },
                    enabled = enabled && !pending,
                    active = pending,
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    // The spinner must not fade with the disabled content.
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    if (pending) LoadingIndicator(Modifier.size(18.dp))
                    else Text("Set", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            ),
            // The same target height as every other button (the 11dp content padding alone left
            // these a few dp short of the standard control height).
            Modifier.fillMaxWidth().heightIn(min = ButtonTargetHeight),
        )
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
