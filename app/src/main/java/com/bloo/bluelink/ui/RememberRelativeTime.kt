package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

/**
 * Holds the LABEL in state rather than a clock, which is the whole efficiency of it. Under a minute
 * the text really does change every few seconds, so tick at 10s; under an hour it can only change
 * once a minute; past that it cannot change more than every quarter of an hour.
 */
@Composable
internal fun rememberRelativeTime(millis: Long?): String? {
    if (millis == null) return null
    var label by remember(millis) {
        mutableStateOf(com.bloo.bluelink.data.relativeLabel(millis))
    }
    LaunchedEffect(millis) {
        while (true) {
            val age = System.currentTimeMillis() - millis
            delay(
                when {
                    age < 60_000L -> 10_000L
                    age < 3_600_000L -> 60_000L
                    else -> 900_000L
                },
            )
            label = com.bloo.bluelink.data.relativeLabel(millis)
        }
    }
    return label
}

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
    // Fired once, with the final value, when the drag/tap settles — for callers whose real commit
    // is expensive (see the Vibrancy/UI-scale sliders, which otherwise call onValueChange on every
    // drag tick and each one recomposes the whole app since they feed BlooTheme's
    // colorScheme/LocalDensity).
    onValueSettled: ((Float) -> Unit)? = null,
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
        onStepTick = { haptics?.tick() },
        onSettle = { haptics?.click(); onValueSettled?.invoke(latestValue) },
    )
}

@Composable
internal fun WiggleText(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight,
    color: Color = Color.Unspecified,
) {
    val resolvedColor = if (color == Color.Unspecified) LocalContentColor.current else color
    // Memoize the style copy to avoid recreating it when color/fontWeight don't change.
    val resolvedStyle = remember(style, fontWeight, resolvedColor) {
        style.copy(fontWeight = fontWeight, color = resolvedColor)
    }
    com.bloo.uicommon.WiggleText(
        text = text,
        style = resolvedStyle,
        reduceMotion = LocalReduceMotion.current,
    )
}

/** Shared state for dragging a pebble onto (or off) the dual-column hot spot. */
internal class HotSeatDrag {
    var section by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var slotTopLeft by mutableStateOf(Offset.Zero)
    var slotSize by mutableStateOf(IntSize.Zero)
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
            pointer.y in slotTopLeft.y..(slotTopLeft.y + slotSize.height)
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
