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
import kotlin.math.roundToInt

/** The curved name's text size, in face units (see [LocalFaceUnit]). */
private const val CurvedNameDp = 14.5f

/** How far the name's baseline sits in from the outer edge, in face units: the name sits on the ring's own band at the bottom. */
private const val CurvedNameInsetDp = 2.5f

/** The longest the name may run round the bottom, as a share of the half circle, before it shrinks to fit. */
private const val CurvedNameMaxSpan = 0.9f

/** The dark blur behind the name, in face units, so it reads over whatever is under it on any page. */
private const val CurvedNameHaloDp = 4f

/**
 * The car's name, curved round the bottom of the display just inside the ring, reading left to right and upright. It sits on every page,
 * so it travels with the pages. It shrinks if the name is too long for the half circle. Blank draws nothing.
 */
@Composable
internal fun CurvedName(text: String, color: Color, modifier: Modifier = Modifier) {
    // Called before the blank check, so the fade's state is kept whatever the text is.
    val fade = rememberCanvasTextFade(text)
    if (text.isBlank()) return
    val unit = LocalFaceUnit.current
    val serif = LocalHeroSerif.current
    Canvas(modifier) { fade.draw(text) { shown, a -> drawCurvedName(shown, color, unit, serif, a) } }
}

private fun DrawScope.drawCurvedName(text: String, color: Color, unit: Float, serif: Boolean, alpha: Float) {
    val outer = size.minDimension / 2f
    val baseline = outer - (CurvedNameInsetDp * unit).dp.toPx()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = (CurvedNameDp * unit).dp.toPx()
        // A soft dark halo under the letters: the watch has no backdrop blur, so the shadow does its job for contrast.
        setShadowLayer((CurvedNameHaloDp * unit).dp.toPx(), 0f, 0f, android.graphics.Color.argb(220, 0, 0, 0))
        // The car's name is the face's hero text: in the classical serif when the phone's Classic headings are on.
        typeface = if (serif) Typeface.create(Typeface.SERIF, Typeface.BOLD) else Typeface.DEFAULT_BOLD
        this.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
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
