package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/** How long the chevron's one-shot easter-egg spin runs. */
private const val ChevronSpinMs = 500

/** The chevron's flip: 0 when collapsed, 180 when expanded, springing between. [label] shows in the animation inspector. */
@Composable
internal fun rememberChevronRotation(expanded: Boolean, label: String): State<Float> =
    animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = label,
    )

/**
 * The chevron's two animations: the 0/180 flip following `expanded`, and a one-shot spin for
 * [ChevronSpin.spin] (the long-press easter egg). The flip is a plain [State] so both pebble header
 * controls share one definition.
 */
internal class ChevronSpin(
    val rotation: State<Float>,
    val eggSpin: State<Float>,
    private val onSpin: () -> Unit,
) {
    /** Kicks the one-shot easter-egg spin (a long-press on the chevron). */
    fun spin() = onSpin()
}

/**
 * Remembers the chevron's flip plus the easter-egg spin: a long-press spins it two full turns over
 * half a second, eased in and out so it winds up and settles rather than snapping to a stop.
 */
@Composable
internal fun rememberChevronSpin(expanded: Boolean, label: String): ChevronSpin {
    val rotation = rememberChevronRotation(expanded, label)
    val egg = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    return remember(rotation) {
        ChevronSpin(rotation, egg.asState()) {
            scope.launch {
                egg.snapTo(0f)
                egg.animateTo(720f, tween(durationMillis = ChevronSpinMs, easing = FastOutSlowInEasing))
                egg.snapTo(0f)
            }
        }
    }
}

/** The chevron glyph itself: a down-arrow that reads as "expand", flipped 180 when open, with the easter-egg spin riding on top. */
@Composable
internal fun ExpandChevronIcon(expanded: Boolean, spin: ChevronSpin) {
    Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = if (expanded) "Collapse" else "Expand",
        modifier = Modifier.size(ButtonIconOnlySize).graphicsLayer {
            rotationZ = spin.rotation.value + spin.eggSpin.value
        },
    )
}
