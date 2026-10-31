package com.bloo.bluelink.wear

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.RevealText
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle

/**
 * The centre of the face: the value where the four quarters meet (the charge or fuel, or the lock state), what the car is doing
 * under it ([state]: running, parked, locked), and the car's name -- or, for a moment, what just happened ([status]). Holding it
 * (a long press, with a buzz) changes the four quarters.
 */
@Composable
fun WearFaceHub(
    value: String,
    caption: String,
    tint: Color,
    size: Dp,
    state: String = "",
    status: FaceStatus? = null,
    onHold: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The hub gives a little under a finger and springs back: less than it did, so the centre reads as steady.
    val press by animateFloatAsState(if (pressed) 0.98f else 1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "hubPress")
    Box(
        Modifier.size(size)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(CircleShape)
            .background(scheme.background)
            .then(
                if (onHold != null) {
                    Modifier
                        .semantics { contentDescription = "Hold to change the four controls" }
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = {},
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onHold()
                            },
                        )
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
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    textAlign = TextAlign.Center,
                    // In dp-sized text: the hub is a fixed circle, so the value must not grow with the font setting.
                    // A long value ("Unlocked" for a car with no charge to show) shrinks to fit the hub instead of breaking mid-word.
                    fontSize = with(LocalDensity.current) {
                        val base = (size.value * 0.30f).coerceIn(16f, 32f)
                        (if (value.length > 4) (base * 4.5f / value.length).coerceAtLeast(12f) else base).dp.toSp()
                    },
                ),
                reduceMotion = false,
            )
            // The car's state can run to two lines ("Parked · Locked · Climate on"); cut to one it read as "Parked · L…".
            if (state.isNotBlank() && status == null) HubLine(state, scheme.onSurface, size)
            HubLine(
                status?.text ?: caption,
                when {
                    status == null -> scheme.onSurfaceVariant
                    status.error -> scheme.error
                    else -> scheme.primary
                },
                size,
            )
        }
    }
}

/** One small line under the hub's value: a fixed share of the hub's size (never the font setting), one line, within 80% of its width. */
@Composable
private fun HubLine(text: String, color: Color, hubSize: Dp) {
    RevealText(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = with(LocalDensity.current) { (hubSize.value * 0.13f).coerceIn(9f, 12f).dp.toSp() },
        ),
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(hubSize * 0.8f),
    )
}

/** A moment's line in the hub, in place of the car's name: what just happened, or why to doubt the reading. */
data class FaceStatus(val text: String, val error: Boolean = false)

/** The ring's colour from the level: the shared [com.bloo.bluelink.data.BlooColors.levelColor] rule, as a Compose colour. */
fun faceTint(percent: Int?, charging: Boolean, fallback: Color): Color =
    Color(com.bloo.bluelink.data.BlooColors.levelColor(percent, charging, fallback.toArgb()))
