package com.bloo.uicommon

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A real drop shadow -- an offset, blurred, dark silhouette of [shape] drawn
 * behind the composable -- rather than Material3 Surface's own tonal
 * `shadowElevation` (or Wear Compose's equivalent), which reads as barely-
 * there against most backgrounds. Shared between phone and watch (pure
 * Compose graphics APIs, no Material dependency) so every piece of floating
 * chrome on both platforms uses the same technique.
 */
fun Modifier.dropShadow(
    shape: Shape,
    color: Color = Color.Black.copy(alpha = 0.38f),
    blurRadius: Dp = 14.dp,
    offsetY: Dp = 5.dp,
    offsetX: Dp = 0.dp,
): Modifier = this.drawBehind {
    val paint = Paint().asFrameworkPaint().apply {
        this.color = color.toArgb()
        isAntiAlias = true
        if (blurRadius > 0.dp) {
            maskFilter = BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
    }
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
    }
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(offsetX.toPx(), offsetY.toPx())
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        canvas.restore()
    }
}
