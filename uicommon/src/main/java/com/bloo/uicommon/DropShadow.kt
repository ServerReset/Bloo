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
    // Prefer drawRoundRect/drawRect over drawPath wherever the outline allows it.
    // Skia has an ANALYTIC blurred-round-rect routine that computes the blur in
    // closed form -- no mask texture, no mask cache, matrix-independent. Handing
    // it an SkPath instead forces the general path: rasterize a mask, blur it,
    // then cache it keyed by the CURRENT MATRIX. The car pager scales each page a
    // fraction every frame (see pagerDepth), so that cache missed on every shadow
    // on every frame -- ~30 full blur rasterizations per frame across the live
    // pages. Same geometry and same paint either way, so this is pixel-identical.
    val rr = (outline as? Outline.Rounded)?.roundRect?.takeIf {
        it.topLeftCornerRadius == it.topRightCornerRadius &&
            it.topLeftCornerRadius == it.bottomLeftCornerRadius &&
            it.topLeftCornerRadius == it.bottomRightCornerRadius &&
            it.topLeftCornerRadius.x == it.topLeftCornerRadius.y
    }
    val rect = (outline as? Outline.Rectangle)?.rect
    // Only built for the shapes the fast paths can't express (non-uniform corners,
    // Generic paths) -- everything the app actually uses hits one of the two above.
    val path = if (rr != null || rect != null) {
        null
    } else {
        when (outline) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
        }.asAndroidPath()
    }
    val offXPx = offsetX.toPx()
    val offYPx = offsetY.toPx()
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.translate(offXPx, offYPx)
            val native = canvas.nativeCanvas
            when {
                rr != null -> native.drawRoundRect(
                    rr.left, rr.top, rr.right, rr.bottom,
                    rr.topLeftCornerRadius.x, rr.topLeftCornerRadius.y, paint,
                )
                rect != null -> native.drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)
                else -> native.drawPath(path!!, paint)
            }
            canvas.restore()
        }
    }
}
