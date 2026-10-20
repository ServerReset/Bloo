package com.bloo.bluelink.wear

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.RevealText

/** The value where the four quarters meet: the charge or fuel (or the lock state), and the car's name under it. */
@Composable
fun WearFaceHub(value: String, caption: String, tint: Color, size: Dp, status: FaceStatus? = null, onClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The hub squeezes under a finger and springs back, like the tiles round it.
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.45f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "hubPress")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(CircleShape)
            .background(scheme.background)
            .then(
                if (onClick != null) {
                    Modifier
                        .semantics { contentDescription = "Change face controls" }
                        .clickable(interactionSource = interaction, indication = null) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            onClick()
                        }
                } else {
                    Modifier
                },
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.bloo.uicommon.AnimatedValue(
                value = value,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    textAlign = TextAlign.Center,
                    // In dp-sized text: the hub is a fixed circle, so the value must not grow with the font setting.
                    // A long value ("Unlocked" for a car with no charge to show) shrinks to fit the hub instead of breaking mid-word.
                    fontSize = with(androidx.compose.ui.platform.LocalDensity.current) {
                        val base = (size.value * 0.26f).coerceIn(14f, 26f)
                        (if (value.length > 4) (base * 4.5f / value.length).coerceAtLeast(11f) else base).dp.toSp()
                    },
                ),
                maxLines = 1,
                reduceMotion = false,
            )
            RevealText(
                status?.text ?: caption,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (size.value * 0.15f).coerceIn(9f, 12f).dp.toSp() },
                ),
                color = when {
                    status == null -> scheme.onSurfaceVariant
                    status.error -> scheme.error
                    else -> scheme.primary
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(size * 0.8f),
            )
        }
    }
}

/** A moment's line in the hub, in place of the car's name: what just happened, or why to doubt the reading. */
data class FaceStatus(val text: String, val error: Boolean = false)

/**
 * The ring's colour, from the level: red when low, amber when getting there, green otherwise, and the
 * charging blue while it charges. A car with no level reads in [fallback].
 */
fun faceTint(percent: Int?, charging: Boolean, fallback: Color): Color =
    Color(levelColorArgb(percent, charging, fallback.toArgb()))

/** [faceTint] as a plain ARGB int, so the Tile (which draws without Compose) colours its ring the same way. */
fun levelColorArgb(percent: Int?, charging: Boolean, fallback: Int): Int =
    com.bloo.bluelink.data.BlooColors.levelColor(percent, charging, fallback)

/** Levels below which the ring warns (amber) and then alarms (red). */
const val LowLevel = com.bloo.bluelink.data.BlooColors.LOW_LEVEL
const val WarnLevel = com.bloo.bluelink.data.BlooColors.WARN_LEVEL
