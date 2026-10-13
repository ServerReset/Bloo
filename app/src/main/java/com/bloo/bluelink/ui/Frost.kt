package com.bloo.bluelink.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The frost a locked-out control group wears: the app's standard glass -- [glassTint] for the veil,
 * [glassSheenBrush] for the sheen, [glassRim] for the hairline -- laid over a soft blur of the
 * controls themselves and faded in by [active]. One glass language, not a file-local ice gradient.
 *
 * [veil] false just softens and fades a single control (an inert button) without re-shaping it;
 * [rim] false drops the hairline.
 */
@Composable
internal fun Modifier.frosted(
    active: Boolean,
    shape: Shape = SmallShape,
    blurRadius: Dp = 5.dp,
    rim: Boolean = true,
    veil: Boolean = true,
): Modifier {
    val amount by animateFloatAsState(if (active) 1f else 0f, tween(MotionLong), label = "frost")
    if (amount <= 0.001f) return this
    val canBlur = canBlurBackdrops()
    // The app's standard glass tint, taken a few steps stronger than a floating surface so a locked
    // sheet genuinely clouds over.
    val veilColor = glassTint(blurred = canBlur)
    return this
        .then(if (veil) Modifier.clip(shape) else Modifier)
        .then(if (canBlur) Modifier.blur(blurRadius * amount, BlurredEdgeTreatment.Unbounded) else Modifier)
        .graphicsLayer { alpha = 1f - (if (canBlur) 0.08f else 0.4f) * amount }
        .drawWithContent {
            drawContent()
            if (!veil) return@drawWithContent
            // The standard glass, over the blurred controls: a neutral veil, then the shared sheen.
            drawRect(veilColor, alpha = (veilColor.alpha * FrostVeilWeight).coerceAtMost(1f) * amount)
            drawRect(glassSheenBrush(), alpha = amount)
        }
        .then(if (rim) Modifier.glassRim(shape) else Modifier)
}

/** How much heavier than a floating surface the locked-control veil is drawn. */
private const val FrostVeilWeight = 5f

/**
 * The app's one treatment for an inert button: soften and fade it with the shared frost, leaving its
 * own (morphing) shape alone -- no veil, no rim. Every disabled button goes through here so one
 * place's inert button looks like another's, instead of each re-deriving the blur.
 */
@Composable
internal fun Modifier.inertButtonFrost(inert: Boolean, shape: Shape): Modifier =
    frosted(inert, shape, blurRadius = 1.2.dp, rim = false, veil = false)

/**
 * The pill that says why something is frozen: a snowflake-cool lock and the reason, in the app's
 * standard [GlassSurface] chrome.
 */
@Composable
internal fun FrostMessage(message: String, modifier: Modifier = Modifier) {
    GlassSurface(
        shape = CircleShape,
        modifier = modifier,
        hazeState = LocalBackdropHaze.current,
    ) {
        Row(
            Modifier.padding(horizontal = GapSection, vertical = GapRow),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(GapRow))
            Text(
                message,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
