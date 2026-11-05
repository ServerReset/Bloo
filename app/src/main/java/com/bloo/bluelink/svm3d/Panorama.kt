package com.bloo.bluelink.svm3d

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * A full 360° x 180° equirectangular panorama of the surroundings, seen from [Backdrop.EYE_Z]
 * above the middle of the car: straight from the cameras wherever one saw that way (nearby things
 * included, as a photo would show them), else the backdrop (sky) above and the painted-in floor
 * below. This is what a generative "3D world" service takes as its picture of the place. The car
 * itself isn't in it.
 *
 * Columns run from straight ahead clockwise round to straight ahead, as in [Backdrop]; rows from
 * straight up to straight down.
 */
object Panorama {
    const val WIDTH = 2048
    const val HEIGHT = 1024

    /**
     * [ground] is the floor picture ([groundSize] square, [groundExtent] metres from the middle to
     * each edge, +y ahead at the top), RGB in the low 24 bits.
     */
    fun compose(frames: List<Fisheye>, backdrop: Backdrop, ground: IntArray, groundSize: Int, groundExtent: Double): IntArray {
        val w = WIDTH; val h = HEIGHT
        val look = Backdrop.Companion.Looker(frames)
        val photo = IntArray(w * h); val fallback = IntArray(w * h)
        val seenMask = FloatArray(w * h)
        val n = groundSize
        val res = 2 * groundExtent / n
        for (y in 0 until h) {
            val e = 90.0 - (y + 0.5) / h * 180.0
            for (x in 0 until w) {
                val azDeg = (x + 0.5) / w * 360.0
                val azR = Math.toRadians(azDeg); val eR = Math.toRadians(e)
                val k = y * w + x
                val seen = look.at(sin(azR) * cos(eR), cos(azR) * cos(eR), sin(eR))
                if (seen >= 0) { photo[k] = seen; seenMask[k] = 1f }
                fallback[k] = if (e >= Backdrop.DOME_BOTTOM_DEG) {
                    // Above the top of the backdrop: its top row carries on (it's sky up there).
                    val row = Backdrop.rowOfElevation(min(e, Backdrop.TOP_DEG)).coerceIn(0.0, backdrop.height - 1.0)
                    bilinear(backdrop.rgb, backdrop.width, backdrop.height, azDeg / 360.0 * backdrop.width - 0.5, row, wrapX = true)
                } else {
                    // Where this direction meets the floor.
                    val d = Backdrop.EYE_Z / tan(Math.toRadians(-e))
                    val gx = sin(azR) * d; val gy = cos(azR) * d
                    val c = (gx + groundExtent) / res - 0.5
                    val r = (groundExtent - gy) / res - 0.5
                    bilinear(ground, n, n, c, r, wrapX = false)
                }
            }
        }
        // Fade the photo into the fill over a few pixels inside its edge, so the join doesn't show.
        val a = boxBlur(seenMask, w, h, FEATHER)
        val out = IntArray(w * h)
        for (k in 0 until w * h) {
            val t = if (seenMask[k] > 0f) (2 * a[k] - 1).coerceIn(0f, 1f) else 0f
            val p = photo[k]; val f = fallback[k]
            fun ch(s: Int) = (((p shr s) and 0xFF) * t + ((f shr s) and 0xFF) * (1 - t)).toInt().coerceIn(0, 255)
            out[k] = (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        return out
    }

    private const val FEATHER = 14

    /** Mean over a (2r+1)² box, wrapping sideways (the panorama goes all the way round). */
    private fun boxBlur(m: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        val span = 2 * r + 1
        for (y in 0 until h) {
            var acc = 0f
            for (dx in -r..r) acc += m[y * w + ((dx % w) + w) % w]
            for (x in 0 until w) {
                tmp[y * w + x] = acc / span
                acc += m[y * w + (x + r + 1) % w] - m[y * w + ((x - r) % w + w) % w]
            }
        }
        for (x in 0 until w) {
            var acc = 0f
            for (dy in -r..r) acc += tmp[dy.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = acc / span
                acc += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }

    private fun bilinear(px: IntArray, w: Int, h: Int, fx: Double, fy: Double, wrapX: Boolean): Int {
        val x0f = floor(fx); val y0f = floor(fy)
        val ax = (fx - x0f).toFloat(); val ay = (fy - y0f).toFloat()
        fun xi(x: Int) = if (wrapX) ((x % w) + w) % w else x.coerceIn(0, w - 1)
        val x0 = xi(x0f.toInt()); val x1 = xi(x0f.toInt() + 1)
        val y0 = y0f.toInt().coerceIn(0, h - 1); val y1 = min(h - 1, max(0, y0f.toInt() + 1))
        val p00 = px[y0 * w + x0]; val p10 = px[y0 * w + x1]; val p01 = px[y1 * w + x0]; val p11 = px[y1 * w + x1]
        fun ch(s: Int): Int {
            val top = ((p00 shr s) and 0xFF) * (1 - ax) + ((p10 shr s) and 0xFF) * ax
            val bot = ((p01 shr s) and 0xFF) * (1 - ax) + ((p11 shr s) and 0xFF) * ax
            return (top * (1 - ay) + bot * ay).toInt().coerceIn(0, 255)
        }
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
