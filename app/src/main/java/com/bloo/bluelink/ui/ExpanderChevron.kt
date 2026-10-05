package com.bloo.bluelink.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** The chevron's flip: 0 when collapsed, 180 when expanded, springing between. [label] shows in the animation inspector. */
@Composable
internal fun rememberChevronRotation(expanded: Boolean, label: String): State<Float> =
    animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = label,
    )

/** The chevron glyph itself: a down-arrow that reads as "expand", flipped 180 when open. */
@Composable
internal fun ExpandChevronIcon(expanded: Boolean, rotation: State<Float>) {
    Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = if (expanded) "Collapse" else "Expand",
        modifier = Modifier.size(ButtonIconOnlySize).graphicsLayer { rotationZ = rotation.value },
    )
}
