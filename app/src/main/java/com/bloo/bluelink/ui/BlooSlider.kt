package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.LocalReduceMotion
import com.bloo.uicommon.MorphIconButton
import com.bloo.uicommon.inertButtonFrost

/**
 * The app's one slider, used everywhere a value is picked: a track with a round −/+ at either end, and optionally a label and a readout
 * above it.
 *
 * [valueRange] is cut into [steps] + 1 parts, so [steps] is the number of stops strictly between the two ends. [value] is the stored
 * setting. The slider follows the finger on a local draft and starts over from [value] whenever it changes. [onValueChange] is called as
 * it moves (raw, unsnapped); [onValueSettled] once, snapped by [snap], when it lets go, or when a −/+ is pressed. [nextValue] is where a
 * −/+ press moves to, by default one part.
 */
@Composable
internal fun BlooSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    readout: ((Float) -> String)? = null,
    snap: (Float) -> Float = { it },
    nextValue: (current: Float, direction: Int) -> Float = { current, direction ->
        current + direction * (valueRange.endInclusive - valueRange.start) / (steps + 1)
    },
    onValueChange: ((Float) -> Unit)? = null,
    onValueSettled: ((Float) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    val start = valueRange.start
    val end = valueRange.endInclusive
    var draft by remember(value) { mutableFloatStateOf(value) }
    val canDecrease = enabled && draft > start
    val canIncrease = enabled && draft < end

    fun settle(v: Float) {
        val settled = snap(v.coerceIn(start, end))
        draft = settled
        onValueSettled?.invoke(settled)
    }
    fun press(direction: Int) {
        haptics?.tick()
        val moved = snap(nextValue(draft, direction).coerceIn(start, end))
        draft = moved
        onValueChange?.invoke(moved)
        onValueSettled?.invoke(moved)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(GapRow)) {
        if (label != null) StepRow(label, readout?.invoke(draft).orEmpty())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MorphIconButton(onClick = { press(-1) }, enabled = canDecrease, filled = true) {
                ButtonIcon(Icons.Filled.Remove, "Lower ${label ?: "value"}")
            }
            val track = Modifier.weight(1f).padding(horizontal = GapRow)
            com.bloo.uicommon.AnimatedSlider(
                value = draft,
                onValueChange = { draft = it; onValueChange?.invoke(it) },
                valueRange = valueRange,
                steps = steps.coerceAtLeast(1),
                accent = scheme.primary,
                inactiveColor = scheme.surfaceContainerHighest,
                dotOnActive = scheme.onPrimary.copy(alpha = 0.7f),
                dotOnInactive = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                reduceMotion = LocalReduceMotion.current,
                // Each notch's strength scales with where the value sits on the track: a light tick down low, a firm click up high.
                onStepTick = { haptics?.ramp(((draft - start) / ((end - start).takeIf { it > 0f } ?: 1f)).coerceIn(0f, 1f)) },
                onSettle = { haptics?.click(); settle(draft) },
                enabled = enabled,
                // An inert slider wears the same standard disabled treatment as every button.
                modifier = if (enabled) track else track.inertButtonFrost(true),
            )
            MorphIconButton(onClick = { press(1) }, enabled = canIncrease, filled = true) {
                ButtonIcon(Icons.Filled.Add, "Raise ${label ?: "value"}")
            }
        }
    }
}
