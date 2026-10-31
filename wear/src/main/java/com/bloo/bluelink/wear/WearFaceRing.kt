package com.bloo.bluelink.wear

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The charge (or fuel) as one thin ring round the display's edge: a flat track, the filled part in the car's colour, and a short mark where
 * the car's charge limit sits. No light or gradient on it.
 */
@Composable
fun WearEdgeRing(progress: Float?, limitPct: Int?, tint: Color, stroke: Dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.onSurface.copy(alpha = 0.16f)
    val mark = scheme.onSurface
    Canvas(modifier) {
        val s = stroke.toPx()
        val inset = s / 2f
        val arc = Size(size.width - s, size.height - s)
        val topLeft = Offset(inset, inset)
        drawArc(track, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(s))
        val fraction = progress?.coerceIn(0f, 1f) ?: 0f
        if (fraction > 0f) {
            drawArc(tint, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        limitPct?.takeIf { it in 1..99 }?.let { pct ->
            val angle = Math.toRadians(-90.0 + 360.0 * pct / 100.0)
            val r = arc.width / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val reach = s * 0.8f
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                mark,
                start = Offset(c.x + (r - reach) * dx, c.y + (r - reach) * dy),
                end = Offset(c.x + (r + reach) * dx, c.y + (r + reach) * dy),
                strokeWidth = s * 0.35f,
            )
        }
    }
}
