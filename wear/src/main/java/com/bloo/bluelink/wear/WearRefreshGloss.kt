package com.bloo.bluelink.wear

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.uicommon.MotionMedium

/**
 * The gloss over the face while the car is being asked: a sheet of the phone's glass laid over the quarters and the hub, inside the ring,
 * with a glint that sweeps across it. Its strength reads the phone's Glass setting (clarity, and Ultra for the thinner, brighter glass), so
 * the refreshing face looks like the phone's glass rather than a flat dim. It fades in and out, and only animates while it is shown.
 */
@Composable
internal fun BoxScope.WearRefreshGloss(refreshing: Boolean) {
    AnimatedVisibility(
        visible = refreshing,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(MotionMedium)),
        exit = fadeOut(tween(MotionMedium)),
    ) {
        GlossSheet()
    }
}

@Composable
private fun GlossSheet() {
    val glass by WearGlass.glass.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    // The glint travels once across the sheet every second and a half; read in the draw block, so it redraws without recomposing.
    val sweep by rememberInfiniteTransition(label = "refreshGloss").animateFloat(
        -1f, 2f,
        infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "refreshGlint",
    )
    val clarity = glass.clarity.coerceIn(0f, 1f)
    // Frosted glass is denser; clear glass is thin, and Ultra is thinner still, with a brighter rim.
    val fillAlpha = lerp(0.46f, 0.16f, clarity) * if (glass.ultra) 0.7f else 1f
    val rimAlpha = 0.28f + 0.42f * clarity + if (glass.ultra) 0.22f else 0f
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Refreshing" }) {
        val r = minOf(size.width, size.height) / 2f * 0.96f
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(scheme.surface.copy(alpha = fillAlpha), radius = r, center = centre)
        drawCircle(scheme.onSurface.copy(alpha = rimAlpha), radius = r - r * 0.02f, center = centre, style = Stroke(width = r * 0.04f))
        // The glint: a soft band of light crossing the sheet, from one side of the ring to the other.
        val x = centre.x + r * sweep
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.16f + 0.12f * clarity), Color.Transparent),
                startX = x - r * 0.35f, endX = x + r * 0.35f,
            ),
        )
    }
}
