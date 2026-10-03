package com.bloo.bluelink.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's one "this is switched off right now" look: frost. Not a grey wash. The content goes soft
 * behind a clear, cool veil of ice, brighter at the top and clearing toward the bottom, with a thin
 * frosted rim catching light, so a locked or unavailable thing still reads as something real you
 * can see, just behind glass, rather than something broken or dimmed.
 *
 * [active] animates the frost in and out. The blur needs Android 12; before that (or under battery
 * saver, see [canBlurBackdrops]) the content is faded a little instead, under the same veil.
 * [blurRadius] is how soft the content gets: a button needs less than a panel of sliders. [rim]
 * draws the frosted outline, which a panel wants and a button (already outlined) does not.
 */
@Composable
internal fun Modifier.frosted(
    active: Boolean,
    shape: Shape = SmallShape,
    blurRadius: Dp = 5.dp,
    rim: Boolean = true,
    /** False for a button: just soften and fade it, leaving its own (morphing) shape alone. */
    veil: Boolean = true,
): Modifier {
    val amount by animateFloatAsState(if (active) 1f else 0f, tween(MotionLong), label = "frost")
    if (amount <= 0.001f) return this
    val dark = appIsDarkTheme()
    val canBlur = canBlurBackdrops()
    val veilTop = if (dark) Color(0xFFDCEBFF) else Color.White
    val rimColor = if (dark) Color(0xFFBFE3FF) else Color(0xFFFFFFFF)
    val veilAlpha = if (dark) 0.20f else 0.46f
    return this
        .then(if (veil) Modifier.clip(shape) else Modifier)
        .then(if (canBlur) Modifier.blur(blurRadius * amount, BlurredEdgeTreatment.Unbounded) else Modifier)
        .graphicsLayer { alpha = 1f - (if (canBlur) 0.12f else 0.4f) * amount }
        .drawWithContent {
            drawContent()
            if (!veil) return@drawWithContent
            // The veil: cool white, strongest where the light hits (top-left), clearing across.
            drawRect(
                Brush.linearGradient(
                    0f to veilTop.copy(alpha = veilAlpha * amount),
                    0.55f to veilTop.copy(alpha = veilAlpha * 0.45f * amount),
                    1f to veilTop.copy(alpha = veilAlpha * 0.7f * amount),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            // A faint ice sheen: one soft diagonal band, so the frost has a surface.
            drawRect(
                Brush.linearGradient(
                    0.25f to Color.Transparent,
                    0.42f to Color.White.copy(alpha = 0.10f * amount),
                    0.6f to Color.Transparent,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height * 0.7f),
                ),
            )
        }
        .then(
            if (rim) {
                Modifier.border(
                    1.dp,
                    Brush.linearGradient(
                        0f to rimColor.copy(alpha = 0.55f * amount),
                        0.5f to rimColor.copy(alpha = 0.12f * amount),
                        1f to Color(0xFF8FD8FF).copy(alpha = 0.35f * amount),
                    ),
                    shape,
                )
            } else {
                Modifier
            },
        )
}

/** The pill that says why something is frozen: a snowflake-cool lock and the reason, in clear glass. */
@Composable
internal fun FrostMessage(message: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.78f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Lock, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
    }
}
