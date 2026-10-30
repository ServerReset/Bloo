package com.bloo.bluelink.ui

/**
 * Shared climate controls: [SeatControl], [WheelHeatControl], the seat/wheel tints, the preset
 * section and [ChargeLimitPill].
 */

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import com.bloo.bluelink.data.WheelHeatLevel
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.cToF
import com.bloo.bluelink.data.climatePresetTemp
import com.bloo.bluelink.data.fToC
import com.bloo.uicommon.lowPowerCalm
import com.bloo.uicommon.BlooSlider
import com.bloo.uicommon.ButtonIcon
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.ReorderColumn
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.MorphIconButton
import com.bloo.uicommon.ButtonLabelStyle
import com.bloo.uicommon.SecondaryTextStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ButtonTargetHeight
import com.bloo.uicommon.ButtonIconSize
import com.bloo.uicommon.ButtonIconGap
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.ButtonPaddingH
import com.bloo.uicommon.ChipPaddingH
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.content

/**
 * Reusable climate-control pieces: seat/wheel heat controls, preset pills and the charge-limit pill
 * (also used by EnergyPebble).
 */

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
    onUpdate: (id: String, name: String, request: ClimateRequest) -> Unit,
    seats: SeatConfig,
    showSeats: Boolean,
    steeringLevels: List<WheelHeatLevel> = WheelHeatLevel.entries,
) {
    // Track IDs mid-exit so the item stays visible until its shrink animation ends.
    var deletingIds by remember { mutableStateOf(setOf<String>()) }
    // The preset being edited (the pencil), until it is saved, deleted or the popup is closed.
    var editing by remember { mutableStateOf<ClimatePreset?>(null) }
    val scope = rememberCoroutineScope()
    // Delete with the row's shrink: the id is held mid-exit until the animation ends, then the preset goes.
    val removePreset: (String) -> Unit = { id ->
        scope.launch {
            deletingIds = deletingIds + id
            delay(240)
            onDelete(id)
            deletingIds = deletingIds - id
        }
    }

    ExpandVisibility(presets.isNotEmpty(), from = Alignment.Bottom) {
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
                // The row keeps its height for the whole drag, as a pebble does: a height animation here made the drop read differently
                // from the pebbles. A deleted preset fades out in place, and the list closes up after it.
                val gone = preset.id in deletingIds
                PresetPill(
                    name = preset.name,
                    temp = climatePresetTemp(preset.request, fahrenheit),
                    active = preset.id == activeId,
                    onStart = { onStart(preset) },
                    onEdit = { editing = preset },
                    modifier = itemDragHandle.graphicsLayer { alpha = if (gone) 0f else 1f },
                )
            }
            Spacer(Modifier.height(GapHairline))
        }
    }
    editing?.let { preset ->
        var name by remember(preset.id) { mutableStateOf(preset.name) }
        val form = rememberClimateForm(preset.id, preset.request)
        GlassAlertDialog(
            onDismissRequest = { editing = null },
            icon = Icons.Filled.Edit,
            title = "Edit preset",
            // Delete is the popup's red action, in its title row. The popup is itself the deliberate step, so it does not arm.
            titleTrailing = {
                MorphIconButton(
                    onClick = {
                        editing = null
                        removePreset(preset.id)
                    },
                    description = "Delete ${preset.name}",
                    emphasis = ButtonEmphasis.Deny,
                ) {
                    ButtonIcon(Icons.Filled.Delete, "Delete ${preset.name}")
                }
            },
            text = {
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(GapRow),
                ) {
                    LabeledField(name, { name = it }, "Name")
                    // What the preset does: the same controls the climate section uses, with its save button left out.
                    ClimateControlsSection(
                        form = form, locked = false, fahrenheit = fahrenheit, seats = seats, showSeats = showSeats,
                        onSaveAsPreset = null, steeringLevels = steeringLevels,
                    )
                }
            },
            buttons = {
                ConfirmCancelButtons(
                    "Save",
                    onConfirm = {
                        if (name.isNotBlank()) {
                            onUpdate(preset.id, name.trim(), form.toRequest())
                            editing = null
                        }
                    },
                    onCancel = { editing = null },
                    enabled = name.isNotBlank(),
                )
            },
        )
    }
}

/**
 * One saved climate preset as a cluster of two buttons, each the height of the lock and unlock buttons: the preset's name at the start
 * (cut short with an ellipsis when it is long) and the temperature it sets at the end, with the primary fill while it is the one
 * running, and a pencil beside it that opens the preset's editor. The row is the drag handle, so holding it re-ranks the presets.
 */
@Composable
internal fun PresetPill(
    name: String,
    temp: String,
    active: Boolean,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    ButtonCluster(
        listOf(
            ClusterButton(
                onClick = onStart,
                onClickHaptic = { haptics?.click() },
                active = active,
                weight = 1f,
                // No vertical padding of its own: the button's standard height is the whole of it.
                contentPadding = PaddingValues(horizontal = ChipPaddingH),
                content = {
                    ButtonIcon(Icons.Filled.AcUnit)
                    Spacer(Modifier.width(ButtonIconGap))
                    RevealText(
                        name,
                        style = ButtonLabelStyle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(GapRow))
                    RevealText(temp, style = ButtonLabelStyle, fontWeight = FontWeight.Bold, maxLines = 1)
                },
            ),
            ClusterButton(
                onClick = { haptics?.tick(); onEdit() },
                content = { ButtonIcon(Icons.Filled.Edit, "Edit $name") },
            ),
        ),
        // The same standard button height as the lock and unlock buttons beside it, not the height of its own text.
        modifier = modifier.fillMaxWidth().heightIn(min = ButtonTargetHeight),
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
                    contentPadding = PaddingValues(horizontal = ChipPaddingH, vertical = 11.dp),
                    weight = 1f,
                    // TalkBack hears both the value and that this half is itself a stepper, distinct from "Set".
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "$label, $limit percent"
                        onClick(label = "Increase by 10 percent") { step(); true }
                    },
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ButtonIcon(icon)
                        Spacer(Modifier.width(ButtonIconGap))
                        RevealText(label, style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        RollingNumber(text = "$limit%", style = PrimaryTextStyle(), fontWeight = FontWeight.Bold)
                    }
                },
                // "Set": sends the command; active while it is in flight.
                ClusterButton(
                    onClick = onApply,
                    onClickHaptic = { haptics?.heavy() },
                    enabled = enabled && !pending,
                    active = pending,
                    contentPadding = PaddingValues(horizontal = ButtonPaddingH),
                ) {
                    if (pending) LoadingIndicator(Modifier.size(ButtonIconSize))
                    else RevealText("Set", fontWeight = FontWeight.Bold, style = PrimaryTextStyle())
                }
            ),
            // The same target height as every other button (the 11dp content padding alone left
            // these a few dp short of the standard control height).
            Modifier.fillMaxWidth().heightIn(min = ButtonTargetHeight),
        )
        Spacer(Modifier.height(GapRow))
        BlooSlider(
            value = limit.toFloat(),
            valueRange = CHARGE_LIMIT_RANGE.first.toFloat()..CHARGE_LIMIT_RANGE.last.toFloat(),
            steps = 4,
            onValueChange = { onValueChange((it / 10f).roundToInt() * 10) },
            enabled = enabled,
        )
        Spacer(Modifier.height(GapHairline))
    }
}

// --- The climate layout's pieces ---------------------------------------------

/** One step of a temperature in the unit the car is shown in, kept inside the car's range and never off a whole degree. */
internal fun stepTempF(tempF: Int, step: Int, fahrenheit: Boolean): Int {
    if (fahrenheit) return (tempF + step).coerceIn(CLIMATE_TEMP_RANGE_F.first, CLIMATE_TEMP_RANGE_F.last)
    val c = fToC(tempF.toDouble()).roundToInt() + step
    return cToF(c.toDouble()).roundToInt().coerceIn(CLIMATE_TEMP_RANGE_F.first, CLIMATE_TEMP_RANGE_F.last)
}

/**
 * A climate setting as a big value above the one slider. The value rolls when it changes, and the −/+ at either end step it by
 * [nextValue]. The temperature (big) and the run time both use this.
 */
@Composable
internal fun ClimateStepper(
    value: String,
    caption: String,
    big: Boolean,
    current: Int,
    range: IntRange,
    tint: Color,
    onChange: (Int) -> Unit,
    nextValue: (current: Float, direction: Int) -> Float = { c, d -> c + d },
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        RollingNumber(
            text = value,
            style = if (big) MaterialTheme.typography.displaySmall else SecondaryTextStyle(),
            // Heavier than the rest of the panel's type: the values are the point of the row, and a Bold stroke read as thin on a tinted
            // surface.
            fontWeight = if (big) FontWeight.Black else FontWeight.ExtraBold,
            color = tint,
        )
        BodySmallText(caption, color = mutedContentColor())
        Spacer(Modifier.height(GapRow))
        BlooSlider(
            value = current.toFloat(),
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // One stop per whole value: the stops in between the two ends.
            steps = range.last - range.first - 1,
            nextValue = nextValue,
            onValueChange = { onChange(it.roundToInt()) },
        )
    }
}

/** A feature in the tile grid: what it is, whether it is on, and a line of detail under its name. */
internal class ClimateTile(val label: String, val icon: ImageVector, val active: Boolean, val detail: String?, val onTap: () -> Unit)

/** The feature tiles, two to a row, each the same height; an odd one out takes the row's full width. */
@Composable
internal fun ClimateTileGrid(tiles: List<ClimateTile>) {
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                row.forEach { tile -> ClimateTileCell(tile, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ClimateTileCell(tile: ClimateTile, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = if (tile.active) scheme.primaryContainer else scheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = lowPowerCalm(Spring.StiffnessMediumLow),
        label = "climateTileFill",
    )
    val ink = if (tile.active) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Column(
        modifier
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(fill)
            .clickable(role = Role.Button, onClickLabel = tile.label, onClick = tile.onTap)
            .padding(GapGroup),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tile.icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(GapHairline))
        RevealText(tile.label, style = SecondaryTextStyle(), color = ink, textAlign = TextAlign.Center, maxLines = 2)
        tile.detail?.let { BodySmallText(it, color = ink) }
    }
}

