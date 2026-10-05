package com.bloo.bluelink.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** The one expand/collapse chevron in the app. */
internal class ChevronSpin internal constructor(
    val rotation: Float,
    val easterEggSpin: Float,
    private val onSpin: () -> Unit,
) {
    /** Kicks the one-shot easter-egg spin (a long-press on the chevron). */
    fun spin() = onSpin()
}

/**
 * Remembers the chevron's two animations: a 0/180 flip following [expanded], and a one-shot 360
 * spin for [ChevronSpin.spin]. [label] is the only thing callers vary -- it shows up in Compose's
 * animation inspector, and keeping the two callers' labels distinct is what made them traceable
 * there in the first place, so it stays a parameter rather than being unified away.
 */
@Composable
internal fun rememberChevronSpin(expanded: Boolean, label: String): ChevronSpin {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = label,
    )
    // Easter egg: HOLD the chevron (long-press) to trigger a one-shot spin, with the vibration
    // fired by the caller. A long press never toggles the pebble -- only a tap does.
    var triggered by remember { mutableStateOf(false) }
    val easterEggSpin by animateFloatAsState(
        targetValue = if (triggered) 360f else 0f,
        animationSpec = if (triggered) {
            lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow)
        } else {
            snap()
        },
        label = "${label}EggSpin",
        finishedListener = { if (triggered) triggered = false },
    )
    return ChevronSpin(rotation, easterEggSpin) { triggered = true }
}

/**
 * The chevron glyph itself: a down-arrow that reads as "expand", flipped 180 when open, with the
 * easter-egg spin riding on top.
 */
@Composable
internal fun ExpandChevronIcon(expanded: Boolean, spin: ChevronSpin) {
    Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = if (expanded) "Collapse" else "Expand",
        modifier = Modifier.size(ButtonIconOnlySize).graphicsLayer {
            rotationZ = spin.rotation + spin.easterEggSpin
        },
    )
}
