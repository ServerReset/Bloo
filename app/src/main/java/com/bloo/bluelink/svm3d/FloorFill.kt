package com.bloo.bluelink.svm3d

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** An image inpainting model (LaMa): paints the masked part of a [SIZE] x [SIZE] picture. */
interface Inpainter {
    /** [rgb] is SIZE*SIZE*3 bytes, [hole] marks what to paint; returns the painted picture, same layout. */
    fun inpaint(rgb: ByteArray, hole: BooleanArray): ByteArray

    companion object { const val SIZE = 512 }
}

/**
 * Paints the floor no camera saw. The floor is flat, so this is an ordinary picture-inpainting job
 * on the top-down floor image. It grows outwards from what was seen a couple of metres at a time:
 * inpainting models are good at holes they can see around, and poor at inventing most of a picture.
 */
object FloorFill {
    private val REACH = Backdrop.FLOOR_REACH + 0.4  // paint out to this radius, metres
    private const val RING = 2.5        // metres per pass
    private const val MAX_PASSES = 5

    fun fill(scene: SurroundScene, model: Inpainter, progress: (Float) -> Unit = {}): IntArray {
        val n = scene.groundSize
        val res = 2 * scene.groundExtent / n
        val total = n * n
        if (scene.groundSeen.size != total || scene.groundPhoto.size != total) return scene.ground
        val inside = BooleanArray(total)
        val radius = FloatArray(total)
        for (k in 0 until total) {
            val gx = -scene.groundExtent + (k % n + 0.5) * res; val gy = scene.groundExtent - (k / n + 0.5) * res
            radius[k] = hypot(gx, gy).toFloat()
            inside[k] = radius[k] < REACH
        }
        // The rims of what was seen are smeared (the camera barely saw them): don't trust them.
        val have = Fisheye.erode(scene.groundSeen, n, n, 2)
        val known = have.copyOf()
        if (known.count { it } < total / 50) return scene.ground
        val r = FloatArray(total); val g = FloatArray(total); val b = FloatArray(total)
        for (k in 0 until total) if (known[k]) {
            val p = scene.groundPhoto[k]
            r[k] = ((p shr 16) and 0xFF).toFloat(); g[k] = ((p shr 8) and 0xFF).toFloat(); b[k] = (p and 0xFF).toFloat()
        }
        val step = (RING / res).toFloat()
        for (pass in 0 until MAX_PASSES) {
            if ((0 until total).none { inside[it] && !have[it] }) break
            progress(pass.toFloat() / MAX_PASSES)
            val dist = distanceTo(have, n)
            // Last pass: everything that's left.
            val ring = BooleanArray(total) { !have[it] && inside[it] && (pass == MAX_PASSES - 1 || dist[it] <= step) }
            val painted = paint(model, r, g, b, have, n)
            // Blend each ring a little over the last one so the joins don't show.
            val soft = blur(ring, n, 0.15 / res)
            for (k in 0 until total) {
                if (known[k]) continue
                val a = if (ring[k]) 1f else soft[k]
                if (a <= 0f) continue
                r[k] = r[k] * (1 - a) + painted[0][k] * a
                g[k] = g[k] * (1 - a) + painted[1][k] * a
                b[k] = b[k] * (1 - a) + painted[2][k] * a
            }
            for (k in 0 until total) if (ring[k]) have[k] = true
        }
        progress(1f)
        // Feather the photo into the painting by a few pixels, then fade at the edge of the world.
        val alpha = blur(known, n, 3.0)
        val out = IntArray(total)
        for (k in 0 until total) {
            val a = if (known[k]) max(alpha[k] * 2 - 1, 0f) else 0f
            val fade = SurroundBuilder.edgeFade(radius[k].toDouble()).toFloat()
            val p = scene.groundPhoto[k]
            fun ch(photo: Int, paint: Float) = ((photo * a + paint * (1 - a)) * fade).toInt().coerceIn(0, 255)
            out[k] = if (!have[k]) 0 else
                (ch((p shr 16) and 0xFF, r[k]) shl 16) or (ch((p shr 8) and 0xFF, g[k]) shl 8) or ch(p and 0xFF, b[k])
        }
        return out
    }

    /** One inpainting run at the model's size over the whole floor; returns the painting at floor size. */
    private fun paint(model: Inpainter, r: FloatArray, g: FloatArray, b: FloatArray, have: BooleanArray, n: Int): Array<FloatArray> {
        val s = Inpainter.SIZE
        // Unknown parts get the average colour (a neutral start); the model only looks at the rest.
        var mr = 0.0; var mg = 0.0; var mb = 0.0; var c = 0
        for (k in have.indices) if (have[k]) { mr += r[k]; mg += g[k]; mb += b[k]; c++ }
        if (c == 0) c = 1
        val rgb = ByteArray(s * s * 3); val hole = BooleanArray(s * s)
        val scale = n.toDouble() / s
        for (y in 0 until s) for (x in 0 until s) {
            // Area average over the floor pixels this model pixel covers.
            val y0 = (y * scale).toInt(); val y1 = max(y0 + 1, min(n, ((y + 1) * scale).toInt()))
            val x0 = (x * scale).toInt(); val x1 = max(x0 + 1, min(n, ((x + 1) * scale).toInt()))
            var sr = 0f; var sg = 0f; var sb = 0f; var cnt = 0; var miss = false
            for (yy in y0 until y1) for (xx in x0 until x1) {
                val k = yy * n + xx
                if (!have[k]) { miss = true; continue }
                sr += r[k]; sg += g[k]; sb += b[k]; cnt++
            }
            val o = y * s + x
            hole[o] = miss
            if (miss || cnt == 0) {
                rgb[3 * o] = (mr / c).toInt().toByte(); rgb[3 * o + 1] = (mg / c).toInt().toByte(); rgb[3 * o + 2] = (mb / c).toInt().toByte()
            } else {
                rgb[3 * o] = (sr / cnt).toInt().toByte(); rgb[3 * o + 1] = (sg / cnt).toInt().toByte(); rgb[3 * o + 2] = (sb / cnt).toInt().toByte()
            }
        }
        val out = model.inpaint(rgb, hole)
        // Back up to floor size, bilinear.
        val res = Array(3) { FloatArray(n * n) }
        for (y in 0 until n) for (x in 0 until n) {
            val fy = ((y + 0.5) / scale - 0.5).coerceIn(0.0, s - 1.0); val fx = ((x + 0.5) / scale - 0.5).coerceIn(0.0, s - 1.0)
            val ya = fy.toInt(); val xa = fx.toInt(); val yb = min(s - 1, ya + 1); val xb = min(s - 1, xa + 1)
            val wy = (fy - ya).toFloat(); val wx = (fx - xa).toFloat()
            for (ch in 0..2) {
                fun at(yy: Int, xx: Int) = (out[3 * (yy * s + xx) + ch].toInt() and 0xFF).toFloat()
                val top = at(ya, xa) * (1 - wx) + at(ya, xb) * wx
                val bot = at(yb, xa) * (1 - wx) + at(yb, xb) * wx
                res[ch][y * n + x] = top * (1 - wy) + bot * wy
            }
        }
        return res
    }

    internal fun distanceTo(m: BooleanArray, n: Int): FloatArray = distanceTo(m, n, n)

    /** Distance in pixels from each pixel to the nearest true one (two-pass chamfer), [n] wide, [rows] high. */
    internal fun distanceTo(m: BooleanArray, n: Int, rows: Int): FloatArray {
        val inf = 1e9f
        val d = FloatArray(n * rows) { if (m[it]) 0f else inf }
        val a = 1f; val dg = sqrt(2f)
        for (y in 0 until rows) for (x in 0 until n) {
            val k = y * n + x
            var v = d[k]
            if (x > 0) v = min(v, d[k - 1] + a)
            if (y > 0) {
                v = min(v, d[k - n] + a)
                if (x > 0) v = min(v, d[k - n - 1] + dg)
                if (x < n - 1) v = min(v, d[k - n + 1] + dg)
            }
            d[k] = v
        }
        for (y in rows - 1 downTo 0) for (x in n - 1 downTo 0) {
            val k = y * n + x
            var v = d[k]
            if (x < n - 1) v = min(v, d[k + 1] + a)
            if (y < rows - 1) {
                v = min(v, d[k + n] + a)
                if (x < n - 1) v = min(v, d[k + n + 1] + dg)
                if (x > 0) v = min(v, d[k + n - 1] + dg)
            }
            d[k] = v
        }
        return d
    }

    /** Gaussian blur of a mask (separable), sigma in pixels. */
    private fun blur(m: BooleanArray, n: Int, sigma: Double): FloatArray {
        val rad = max(1, (sigma * 3).toInt())
        val w = FloatArray(2 * rad + 1) { exp(-((it - rad) * (it - rad)) / (2 * sigma * sigma)).toFloat() }
        val ws = w.sum(); for (i in w.indices) w[i] /= ws
        val t = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            var s = 0f
            for (o in -rad..rad) { val xx = (x + o).coerceIn(0, n - 1); if (m[y * n + xx]) s += w[o + rad] }
            t[y * n + x] = s
        }
        val out = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            var s = 0f
            for (o in -rad..rad) { val yy = (y + o).coerceIn(0, n - 1); s += t[yy * n + x] * w[o + rad] }
            out[y * n + x] = s
        }
        return out
    }
}
