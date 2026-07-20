package com.bloo.uicommon

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
 *
 * drawWithCache, not drawBehind: the Paint/BlurMaskFilter/Path used to get
 * rebuilt from scratch on every single draw call, static pebble or not. For
 * anything that redraws every frame of an animation -- exactly what a
 * floating button fading in via AnimatedVisibility does -- that meant
 * reallocating a native BlurMaskFilter every frame, which is exactly the
 * kind of per-frame allocation that shows up as the shadow visibly glitching/
 * popping in rather than fading smoothly with the rest of the content.
 * drawWithCache rebuilds the cached block only when [size] (or anything else
 * it reads) actually changes, reusing the same Paint/Path on every redraw
 * in between -- including every frame of a fade.
 */
fun Modifier.dropShadow(
    shape: Shape,
    color: Color = Color.Black.copy(alpha = 0.38f),
    blurRadius: Dp = 14.dp,
    offsetY: Dp = 5.dp,
    offsetX: Dp = 0.dp,
): Modifier = this.drawWithCache {
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
    }.asAndroidPath()
    val offXPx = offsetX.toPx()
    val offYPx = offsetY.toPx()
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.translate(offXPx, offYPx)
            canvas.nativeCanvas.drawPath(path, paint)
            canvas.restore()
        }
    }
}
