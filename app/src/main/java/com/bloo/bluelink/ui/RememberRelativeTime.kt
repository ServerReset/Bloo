package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.bloo.uicommon.LocalReduceMotion
import com.bloo.uicommon.inertButtonFrost
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.AnimatedSlider
import com.bloo.uicommon.content

/** "5 min ago" for [millis], kept current without a clock in the caller (from :uicommon, shared with the watch). */
@Composable
internal fun rememberRelativeTime(millis: Long?): String? = com.bloo.uicommon.rememberRelativeTime(millis)

/**
 * A clean, fully custom slider: a rounded track with an accent fill, subtle step ticks, and a
 * circular thumb that springs to the nearest step.
 */
@Composable
internal fun AnimatedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    // No default and never zero: every slider in the app picks from fixed values, none are free.
    steps: Int,
    accent: Color = MaterialTheme.colorScheme.primary,
    // Fired once, with the final value, when the drag/tap settles - for callers whose real commit
    // is expensive (see the Vibrancy/UI-scale sliders, which otherwise call onValueChange on every
    // drag tick and each one recomposes the whole app since they feed BlooTheme's
    // colorScheme/LocalDensity).
    onValueSettled: ((Float) -> Unit)? = null,
    /**
     * False greys the slider out (a muted accent) and makes it inert: no drag, tap or screen-reader
     * set, for a control the car will not accept in its current state.
     */
    enabled: Boolean = true,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    var latestValue by remember { mutableFloatStateOf(value) }
    com.bloo.uicommon.AnimatedSlider(
        value = value,
        onValueChange = { latestValue = it; onValueChange(it) },
        valueRange = valueRange,
        steps = steps.coerceAtLeast(1),
        accent = accent,
        inactiveColor = scheme.surfaceContainerHighest,
        dotOnActive = scheme.onPrimary.copy(alpha = 0.7f),
        dotOnInactive = scheme.onSurfaceVariant.copy(alpha = 0.5f),
        reduceMotion = LocalReduceMotion.current,
        // Each notch's strength scales with where the value sits on the track: a light tick down low,
        // a firm click up high, so the haptics say how far along you are, not just that you moved.
        onStepTick = {
            val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
            haptics?.ramp(((latestValue - valueRange.start) / span).coerceIn(0f, 1f))
        },
        onSettle = { haptics?.click(); onValueSettled?.invoke(latestValue) },
        enabled = enabled,
        // An inert slider wears the same standard disabled treatment as every button (the morph
        // frost), instead of a bespoke muted accent, so "disabled" looks one way everywhere.
        modifier = if (enabled) Modifier else Modifier.inertButtonFrost(true),
    )
}

/** Shared state for dragging a pebble onto (or off) the dual-column hot spot. */
internal class HotSeatDrag {
    var section by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var slotTopLeft by mutableStateOf(Offset.Zero)
    var slotSize by mutableStateOf(IntSize.Zero)
    /**
     * The window y of the bottom of the page. The pin target is not just the empty slot but the whole left column from the
     * slot DOWN to here (under the hero and the controls), so a pebble can be dropped anywhere there rather than aimed at a
     * strip the right-hand column makes small.
     */
    var zoneBottom by mutableFloatStateOf(0f)
    /**
     * True while the pinned pebble is being dragged, or flying home: the car's first column is then drawn above the stack, so
     * the pebble stays in front of it on its way out instead of sliding behind.
     */
    var pinnedActive by mutableStateOf(false)
    /** The key released over the slot, awaiting its fly-in before the pin commits. */
    var pendingPin by mutableStateOf<String?>(null)
    /**
     * Window top-left of each section's slot in the reorderable STACK, keyed by section. Registered
     * by every row (even a pinned one, collapsed to zero height), so an unpinned hot-seat pebble has
     * a live target to fly back to -- the mirror of [slotTopLeft] for the pin direction.
     */
    val stackSlot = mutableStateMapOf<String, Offset>()
    /**
     * The pinned section flying home. Its stack row opens (invisibly) for the whole flight, so the
     * list has already made room and the landing is a plain swap: no reflow, no pop.
     */
    var returning by mutableStateOf<String?>(null)
    val overSlot: Boolean
        get() = section != null && slotSize.width > 0 &&
            pointer.x in slotTopLeft.x..(slotTopLeft.x + slotSize.width) &&
            pointer.y in slotTopLeft.y..maxOf(slotTopLeft.y + slotSize.height, zoneBottom)
}

internal val LocalHotSeatDrag = staticCompositionLocalOf<HotSeatDrag?> { null }

/**
 * Trivial full-size [Box] wrapper; exists as a distinct composable purely so the hot-seat drag
 * machinery has a single, stable, named host to reason about/hang [LocalHotSeatDrag] state around
 * rather than an anonymous Box.
 */
@Composable
internal fun BackdropHost(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}
