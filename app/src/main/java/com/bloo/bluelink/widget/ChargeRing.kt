package com.bloo.bluelink.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

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
     * @param limitFraction optional 0f..1f charge-limit position; cuts a notch
     *        clean through the ring there so the stretch the car has been told
     *        not to fill reads as a separate segment. Null = no limit known.
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

        // The charge limit, as the seam between two segments rather than a
        // marker drawn on top of one -- matching the app's hero bar and the
        // live charging notification. CLEAR cuts through both the arc and the
        // track to the transparent bitmap beneath, so the widget's own
        // background shows through the gap and the notch stays legible
        // whichever side the charge has reached. A marker painted ON the ring
        // couldn't: at the limit it would sit exactly where the arc's rounded
        // cap lands.
        val limit = limitFraction?.takeIf { it > 0.02f && it < 0.99f }
        if (limit != null) {
            val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                // Wider than the ring so the cut's own antialiased edges land
                // outside it and the gap reads as a clean break.
                strokeWidth = stroke * 1.6f
                strokeCap = Paint.Cap.BUTT
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            // A fixed angular width would be a hair at 24dp and a chasm at
            // 140dp, so the gap is a constant arc LENGTH instead: ~1.6x the
            // ring's own stroke, converted back to degrees for this radius.
            val radius = (rect.width() / 2f).coerceAtLeast(1f)
            val sweep = Math.toDegrees((stroke * 1.6f / radius).toDouble()).toFloat()
                .coerceIn(3f, 24f)
            canvas.drawArc(rect, -90f + 360f * limit - sweep / 2f, sweep, false, cut)
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

    /** Convenience: pick the arc colour by state (low → amber, charging → green,
     *  else the caller's accent), keeping the widget's colour logic in one place. */
    fun arcColorFor(fraction: Float, charging: Boolean, accent: Int): Int = when {
        charging -> com.bloo.bluelink.data.BlooColors.chargeGreen
        fraction <= 0.15f -> com.bloo.bluelink.data.BlooColors.heat
        fraction <= 0.30f -> com.bloo.bluelink.data.BlooColors.warn
        else -> accent
    }
}
