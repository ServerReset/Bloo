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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The app's one inert (disabled) treatment: soften and fade whatever is inert, leaving its own
 * (morphing) shape alone. Disabled buttons, sliders and locked-out control groups all wear this, so
 * "disabled" looks identical everywhere instead of each deriving its own blur.
 */
@Composable
internal fun Modifier.inertButtonFrost(inert: Boolean): Modifier {
    val amount by animateFloatAsState(if (inert) 1f else 0f, tween(MotionLong), label = "inertFrost")
    if (amount <= 0.001f) return this
    // A real backdrop blur is expensive; without it (older API, battery saver) the fade carries the
    // whole effect. Same gate as every other blur in the app.
    val canBlur = canBlurBackdrops()
    return this
        .then(if (canBlur) Modifier.blur(InertBlurRadius * amount, BlurredEdgeTreatment.Unbounded) else Modifier)
        .graphicsLayer { alpha = 1f - (if (canBlur) 0.08f else 0.4f) * amount }
}

/** The small blur an inert control wears: enough to read as "off", not enough to be unreadable. */
private val InertBlurRadius = 1.2.dp

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
            RevealText(
                message,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
