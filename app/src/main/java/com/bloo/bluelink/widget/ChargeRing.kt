package com.bloo.bluelink.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the widget's charge / fuel status ring to a [Bitmap].
 *
 * Glance has no Canvas or arc primitive, so the ring — the widget's signature
 * glanceable — is drawn to a plain android.graphics.Bitmap and shown via
 * `Image(ImageProvider(bitmap))`. Kept dependency-free (pure android.graphics) so
 * it runs fine in the widget's remote process.
 *
 * The ring is a thick rounded arc over a faint track, with an optional centre
 * label (usually the percent). Colours are passed in as ARGB ints by the caller
 * so the widget can theme the ring (charge-green while charging, accent otherwise,
 * amber when low) without this file knowing anything about the theme.
 */
object ChargeRing {

    /**
     * @param sizePx    the square bitmap edge in pixels (caller converts dp→px)
     * @param fraction  0f..1f fill amount (percent/100)
     * @param arcColor  ARGB of the filled arc
     * @param trackColor ARGB of the unfilled track
     * @param centerText optional text drawn in the middle (e.g. "82%"); null = none
     * @param centerColor ARGB of the centre text
     * @param strokeFraction ring thickness as a fraction of radius (default 0.16)
     * @param limitFraction optional 0f..1f charge-limit position, drawn as a
     *        dot sitting in a small gap cut through the ring at that angle --
     *        the same "mark on the gauge, not a division of it" treatment every
     *        other surface uses for this value. Null = no limit known.
     */
    fun render(
        sizePx: Int,
        fraction: Float,
        arcColor: Int,
        trackColor: Int,
        centerText: String? = null,
        centerColor: Int = arcColor,
        strokeFraction: Float = 0.16f,
        limitFraction: Float? = null,
    ): Bitmap {
        val edge = sizePx.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val stroke = (edge / 2f) * strokeFraction
        val pad = stroke / 2f + edge * 0.02f
        val rect = RectF(pad, pad, edge - pad, edge - pad)
        val clamped = fraction.coerceIn(0f, 1f)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = trackColor
        }
        // Full track first, then the filled arc on top.
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)

        if (clamped > 0f) {
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                color = arcColor
            }
            // Start at 12 o'clock (-90°), sweep clockwise by the fill amount.
            canvas.drawArc(rect, -90f, 360f * clamped, false, arcPaint)
        }

        // The charge limit: a DOT on the ring at that angle, matching the dot
        // on the bar everywhere else this value is drawn (the phone hero, the
        // widget's own bar hero, the watch, the live notification). It used to
        // be a gap cut clean through the ring, which read as a break in the
        // gauge rather than a mark on it -- the ring's own sweep already means
        // something, and a second division can't mean a second thing without
        // the reader working out which is which.
        //
        // Two circles: the outer one clears a little room in the ring so the
        // marker sits ON it rather than in it, the inner one is the mark.
        //
        // The same 1..99 window every other surface applies to this value (see
        // ChargeBar, ChargeSegmentBar, the watch ring, LiveCharge): 0 and 100
        // aren't markers, they're the ends of the gauge. This used to be
        // `> 0.02f`, which silently drew nothing for a 1% or 2% limit while the
        // bar next to it drew a dot.
        //
        // Bounds sit on the midpoints rather than on 0.01/0.99 exactly: the
        // caller hands us an Int percent divided by 100f, so the only reachable
        // values near either end are 0.00, 0.01, 0.99 and 1.00, and testing
        // against midpoints keeps this correct without comparing floats for
        // equality at a boundary.
        val limit = limitFraction?.takeIf { it > 0.005f && it < 0.995f }
        if (limit != null) {
            val radius = (rect.width() / 2f).coerceAtLeast(1f)
            val angle = Math.toRadians((-90f + 360f * limit).toDouble())
            val cx = rect.centerX() + (cos(angle) * radius).toFloat()
            val cy = rect.centerY() + (sin(angle) * radius).toFloat()
            val outer = stroke * 0.92f
            val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            canvas.drawCircle(cx, cy, outer, clear)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // FIXED colour, never flipped by which side of the fill the mark
                // lands on. Flipping it to trackColor once the charge passed the
                // limit made the mark disappear at exactly the state this gauge
                // is in most often -- a car set to 80% and charged to 80% -- the
                // same defect the phone's drawChargeLimitDot, the widget's own
                // ChargeBar and the watch ring each fixed by pinning their
                // colours (see drawChargeLimitDot for the full reasoning).
                //
                // arcColor rather than a theme role because this file is
                // deliberately theme-agnostic (plain android.graphics, ARGB ints
                // in). It is opaque and saturated -- WidgetTheme.forPhoto leaves
                // accentArgb alone precisely so bitmap arcs stay legible over a
                // photo -- so the mark reads wherever the arc itself does.
                // Matching the other surfaces exactly (background halo +
                // onSurface core) would need those two roles passed in as ints.
                color = arcColor
            }
            canvas.drawCircle(cx, cy, outer * 0.62f, dot)
        }

        if (!centerText.isNullOrBlank()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = centerColor
                textAlign = Paint.Align.CENTER
                // Size the label to comfortably fit inside the ring's inner circle.
                textSize = edge * 0.30f
                isFakeBoldText = true
            }
            // Shrink-to-fit: at this default size a 3-4 character label
            // ("100%") can measure wider than the inner circle on the
            // smallest rings. Measure and scale the font down proportionally
            // rather than ever letting it draw past the ring/get clipped --
            // this is a raw Canvas draw, not a TextView, so nothing else
            // would catch an overflow for us.
            val maxTextWidth = (edge - stroke * 2.4f).coerceAtLeast(edge * 0.4f)
            val measured = textPaint.measureText(centerText)
            if (measured > maxTextWidth) {
                textPaint.textSize *= maxTextWidth / measured
            }
            val cx = edge / 2f
            // Vertically centre using the font metrics baseline.
            val fm = textPaint.fontMetrics
            val cy = edge / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText(centerText, cx, cy, textPaint)
        }

        return bmp
    }

    /**
     * The arc colour for a charge level: this widget's palette applied to the shared
     * bands in [com.bloo.bluelink.data.chargeTier], which the watch's ring and tile
     * now use too -- the three used to carry three different sets of thresholds.
     *
     * Takes a percent rather than the fraction it used to, so the null case reaches
     * the tier function instead of being flattened to 0 by the caller. An unknown
     * level was previously `(percent ?: 0) / 100f`, i.e. 0, i.e. CRITICAL -- painting
     * a confident red arc for a car that had not reported a charge at all.
     */
    fun arcColorFor(percent: Int?, charging: Boolean, accent: Int): Int =
        when (com.bloo.bluelink.data.chargeTier(percent, charging)) {
            com.bloo.bluelink.data.ChargeTier.CHARGING -> com.bloo.bluelink.data.BlooColors.chargeGreen
            com.bloo.bluelink.data.ChargeTier.CRITICAL -> com.bloo.bluelink.data.BlooColors.heat
            com.bloo.bluelink.data.ChargeTier.LOW -> com.bloo.bluelink.data.BlooColors.warn
            com.bloo.bluelink.data.ChargeTier.NORMAL,
            com.bloo.bluelink.data.ChargeTier.UNKNOWN -> accent
        }
}
