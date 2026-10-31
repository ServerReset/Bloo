package com.bloo.bluelink.wear

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** The curved name's text size, in face units (see [LocalFaceUnit]). */
private const val CurvedNameDp = 11f

/** How far the name's baseline sits in from the outer edge, in face units: clear of the ring, inside the bezel. */
private const val CurvedNameInsetDp = 11f

/** The longest the name may run round the bottom, as a share of the half circle, before it shrinks to fit. */
private const val CurvedNameMaxSpan = 0.9f

/**
 * The car's name, curved round the bottom of the face just inside the ring, reading left to right and upright. It shrinks if the name is
 * too long for the half circle. Blank draws nothing.
 */
@Composable
internal fun CurvedName(text: String, color: Color, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    val unit = LocalFaceUnit.current
    Canvas(modifier) { drawCurvedName(text, color, unit) }
}

private fun DrawScope.drawCurvedName(text: String, color: Color, unit: Float) {
    val outer = size.minDimension / 2f
    val baseline = outer - (CurvedNameInsetDp * unit).dp.toPx()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = (CurvedNameDp * unit).dp.toPx()
        typeface = Typeface.DEFAULT_BOLD
    }
    // Left, round the bottom, to the right: text on this path runs left to right with its tops toward the middle.
    val rect = RectF(center.x - baseline, center.y - baseline, center.x + baseline, center.y + baseline)
    val path = Path().apply { arcTo(rect, 180f, -180f) }
    val length = PathMeasure(path, false).length
    val room = length * CurvedNameMaxSpan
    val width = paint.measureText(text)
    if (width > room) paint.textSize *= room / width
    val start = (length - paint.measureText(text)) / 2f
    drawIntoCanvas { it.nativeCanvas.drawTextOnPath(text, path, start, 0f, paint) }
}
