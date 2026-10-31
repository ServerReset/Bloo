package com.bloo.bluelink.wear

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How many points the blob's outline is drawn with: enough that it reads as a curve, few enough to redraw every frame cheaply. */
private const val BlobPoints = 48

/**
 * The refresh indicator: an expressive blob that turns and stretches, drawn as liquid glass in the phone's Glass setting. Its shape is
 * three lobes that breathe at different rates, so the outline never settles into a circle; the glass is tinted by the accent, its rim and
 * sheen read the clarity and Ultra. The animation runs only while it is on screen, so a finished refresh costs nothing.
 */
@Composable
internal fun WearRefreshIndicator(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val glass by WearGlass.glass.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "refreshBlob")
    // Read in the draw block, so the animation redraws the blob without recomposing whatever holds it.
    val turn by transition.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "refreshTurn",
    )
    val stretch by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "refreshStretch",
    )
    // Frosted glass is denser; clear glass is thin, and Ultra is thinner still with a brighter edge.
    val clarity = glass.clarity.coerceIn(0f, 1f)
    val fillAlpha = lerp(0.34f, 0.12f, clarity) * if (glass.ultra) 0.7f else 1f
    val rimAlpha = (0.30f + 0.45f * clarity) + if (glass.ultra) 0.25f else 0f
    Canvas(modifier.size(size).semantics { contentDescription = "Refreshing" }) {
        val r = minOf(this.size.width, this.size.height) / 2f * 0.78f
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        val path = blobPath(centre, r, turn, stretch)
        drawPath(path, Brush.radialGradient(listOf(scheme.primary.copy(alpha = fillAlpha * 1.4f), scheme.primary.copy(alpha = fillAlpha)), centre, r))
        drawPath(path, scheme.primary.copy(alpha = rimAlpha), style = Stroke(width = r * 0.08f))
        // The sheen: a small bright glint that travels round with the turn, as light does on a bead of glass.
        val glint = Offset(centre.x + r * 0.45f * cos(turn), centre.y + r * 0.45f * sin(turn))
        drawCircle(Color.White.copy(alpha = 0.22f + 0.18f * clarity), radius = r * 0.16f, center = glint)
    }
}

/** The blob's outline: a circle with three lobes stretched in and out, turned by [turn] and breathing with [stretch]. */
private fun blobPath(centre: Offset, r: Float, turn: Float, stretch: Float): Path = Path().apply {
    for (i in 0..BlobPoints) {
        val a = 2.0 * PI * i / BlobPoints
        val lobes = 0.14f * stretch * sin(3 * a + turn).toFloat()
        val ripple = 0.06f * sin(5 * a - 1.3 * turn).toFloat()
        val rr = r * (1f + lobes + ripple)
        val x = centre.x + rr * cos(a).toFloat()
        val y = centre.y + rr * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
