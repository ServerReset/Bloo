package com.bloo.bluelink.svm3d

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The far surroundings as a 360° panorama seen from just above the middle of the car, drawn on a
 * dome of radius [RADIUS] round the scene. Whatever no camera saw (mostly sky and tree tops: the
 * cameras look down) is painted in by the inpainting model. Rows run from [TOP_DEG] elevation down
 * to [BOTTOM_DEG]; columns from straight ahead clockwise (to the right) round to straight ahead.
 */
class Backdrop(val width: Int, val height: Int, val rgb: IntArray) {
    companion object {
        const val WIDTH = 2048
        const val HEIGHT = 512
        const val TOP_DEG = 80.0
        const val BOTTOM_DEG = -10.0
        /** Dome radius, metres, round [EYE_Z] above the middle of the car. */
        const val RADIUS = 9.0
        const val EYE_Z = 1.0
        /** Where the dome meets the floor (horizontal distance from the middle of the car). */
        val FLOOR_REACH = sqrt(RADIUS * RADIUS - EYE_Z * EYE_Z)
        /** The lowest elevation the dome needs (where it meets the floor). */
        val DOME_BOTTOM_DEG = Math.toDegrees(-asin(EYE_Z / RADIUS))

        private val COS_LIMIT = cos(Math.toRadians(82.0))

        /**
         * How far off the cameras look for the backdrop, metres. The dome is drawn at [RADIUS], but
         * each camera sits a metre or two from the middle, so if they all looked at the dome itself
         * anything well beyond it (a sign across the lot, a building) would land in a different
         * place in each camera's part of the panorama: twice, where two cameras meet. Looking out
         * this far lines up what's typically in the background to within a degree or two.
         */
        const val LOOKUP = 35.0

        /** Distance from the eye to the point a direction (vertical part [dz]) shows. */
        fun lookupDistance(dz: Double): Double = if (dz < -1e-6) min(LOOKUP, EYE_Z / -dz) else LOOKUP

        fun elevationOfRow(y: Double) = TOP_DEG - (y + 0.5) / HEIGHT * (TOP_DEG - BOTTOM_DEG)
        fun rowOfElevation(e: Double) = (TOP_DEG - e) / (TOP_DEG - BOTTOM_DEG) * HEIGHT - 0.5

        /** Stitches the panorama from the four cameras; [seen] (WIDTH*HEIGHT) gets what a camera saw. */
        fun stitch(frames: List<Fisheye>, seen: BooleanArray, blocked: ByteArray? = null): IntArray {
            val w = WIDTH; val h = HEIGHT
            val out = IntArray(w * h)
            val look = Looker(frames)
            for (y in 0 until h) {
                val e = Math.toRadians(elevationOfRow(y.toDouble()))
                for (x in 0 until w) {
                    val az = Math.toRadians((x + 0.5) / w * 360.0)
                    val k = y * w + x
                    val p = look.at(sin(az) * cos(e), cos(az) * cos(e), sin(e), blocked?.get(k)?.toInt() ?: 0)
                    if (p >= 0) { out[k] = p; seen[k] = true }
                }
            }
            return out
        }

        /** What the cameras show in one direction from the eye, blended from those that see it. */
        class Looker(private val frames: List<Fisheye>) {
            private val uv = DoubleArray(2)
            private val boxes = frames.map { SurroundBuilder.ownCarBox(it.camera) }
            private val d = DoubleArray(3)

            /** The colour (RGB) in direction ([dx], [dy], [dz]), unit length; -1 if no camera sees it. Bit i of [skip] leaves out frame i. */
            fun at(dx: Double, dy: Double, dz: Double, skip: Int = 0): Int {
                // The point each camera looks at for this direction: far off (see [LOOKUP]), or
                // on the ground if the direction reaches it first.
                val t = lookupDistance(dz)
                val px = dx * t; val py = dy * t; val pz = EYE_Z + dz * t
                var sw = 0.0; var r = 0.0; var g = 0.0; var b = 0.0
                for ((fi, fe) in frames.withIndex()) {
                    val cam = fe.camera
                    if ((skip shr fi) and 1 != 0) continue
                    if (!cam.project(px, py, pz, uv)) continue
                    if (uv[0] < 0 || uv[0] >= fe.width - 1 || uv[1] < 0 || uv[1] >= fe.height - 1) continue
                    if (!fe.mask[uv[1].toInt() * fe.width + uv[0].toInt()]) continue
                    d[0] = px - cam.x; d[1] = py - cam.y; d[2] = pz - cam.z
                    val (lo, hi) = boxes[fi]
                    if (SurroundBuilder.hitsBox(cam.position, d, lo, hi)) continue
                    // Favour the camera looking most straight at it (sharpest, least parallax);
                    // the high power keeps it close to one camera per spot, without hard seams.
                    val len = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
                    val cosTh = (cam.r[2] * d[0] + cam.r[5] * d[1] + cam.r[8] * d[2]) / len
                    // The lens's outer rim shows the camera housing, not the world: stop short of it.
                    if (cosTh < COS_LIMIT) continue
                    val wgt = ((cosTh - COS_LIMIT) / (1 - COS_LIMIT)).pow(6)
                    if (wgt <= 1e-9) continue
                    val p = fe.sample(uv[0], uv[1])
                    r += wgt * ((p shr 16) and 0xFF); g += wgt * ((p shr 8) and 0xFF); b += wgt * (p and 0xFF); sw += wgt
                }
                if (sw <= 0) return -1
                return ((r / sw).toInt() shl 16) or ((g / sw).toInt() shl 8) or (b / sw).toInt()
            }
        }

        /**
         * Which cameras see something nearby, rather than the bowl, in each panorama direction (bit
         * per frame): a parked car beside us would otherwise be projected huge across the bowl, a
         * ghost of what the painted surface already shows where it really is. Judged by the depth
         * along each camera's ray; only clearly near things count, since far depth is rough.
         */
        fun occlusion(frames: List<Fisheye>, crops: List<Crop>): ByteArray {
            val w = WIDTH; val h = HEIGHT
            val out = ByteArray(w * h)
            val pix = DoubleArray(2)
            val mine = frames.map { fe -> crops.filter { it.camera === fe.camera && it.depthOk } }
            for (y in 0 until h) {
                val e = Math.toRadians(elevationOfRow(y.toDouble()))
                for (x in 0 until w) {
                    val az = Math.toRadians((x + 0.5) / w * 360.0)
                    val dx = sin(az) * cos(e); val dy = cos(az) * cos(e); val dz = sin(e)
                    val t = lookupDistance(dz)
                    val px = dx * t; val py = dy * t; val pz = EYE_Z + dz * t
                    var bits = 0
                    for ((fi, fe) in frames.withIndex()) {
                        val cam = fe.camera
                        val vx = px - cam.x; val vy = py - cam.y; val vz = pz - cam.z
                        val len = sqrt(vx * vx + vy * vy + vz * vz)
                        val ux = vx / len; val uy = vy / len; val uz = vz / len
                        // The crop that looks most straight that way decides.
                        val azc = Math.toDegrees(Math.atan2(ux, uy))
                        var best = 1e9; var depth = Float.NaN
                        for (c in mine[fi]) {
                            if (!c.view.pixelOf(ux, uy, uz, pix)) continue
                            val off = kotlin.math.abs(((azc - c.view.centerAzimuthDeg + 540) % 360) - 180)
                            if (off >= best) continue
                            val i = pix[0].toInt(); val j = pix[1].toInt()
                            if (i !in 0 until c.n || j !in 0 until c.n) continue
                            val k = i * c.n + j
                            best = off; depth = if (c.valid[k]) c.t[k] else Float.NaN
                        }
                        if (!depth.isNaN() && depth < NEAR && depth < 0.6 * min(len, RADIUS)) bits = bits or (1 shl fi)
                    }
                    out[y * w + x] = bits.toByte()
                }
            }
            return out
        }

        /** Depth (metres along the ray) under which something counts as nearby, see [occlusion]. */
        private const val NEAR = 6f

        /**
         * Stitches the panorama and fills what no camera saw. Above the picture that's sky (the
         * cameras look down), where the inpainting model only paints murk, so the sky is continued
         * straight up from its colour along the top of what was seen, smoothed sideways. Holes lower
         * down, behind nearby things ([blocked]), have the scene all round them: the inpainting
         * model ([model]) paints those.
         */
        fun build(frames: List<Fisheye>, blocked: ByteArray? = null, model: Inpainter? = null, progress: (Float) -> Unit = {}): Backdrop {
            val w = WIDTH; val h = HEIGHT
            val seen = BooleanArray(w * h)
            val pano = stitch(frames, seen, blocked)
            val have = Fisheye.erode(seen, w, h, 1)
            // Each column's sky colour: the median of the first few seen pixels from the top.
            val top = Array(3) { DoubleArray(w) }; val found = BooleanArray(w)
            for (x in 0 until w) {
                val vals = ArrayList<Int>()
                for (y in 0 until h) { if (have[y * w + x]) { vals += pano[y * w + x]; if (vals.size >= 9) break } }
                if (vals.isEmpty()) continue
                found[x] = true
                for (ch in 0..2) top[ch][x] = vals.map { (it shr (16 - 8 * ch)) and 0xFF }.sorted()[vals.size / 2].toDouble()
            }
            if (found.none { it }) return Backdrop(w, h, pano)
            // Smooth round the circle (wrapping), ignoring columns with nothing seen: lightly for the
            // colour right where the picture ends, heavily for the open sky further up (so a roof
            // edge doesn't streak all the way up).
            fun smooth(sigma: Double): Array<DoubleArray> {
                val rad = (3 * sigma).toInt()
                val kern = DoubleArray(2 * rad + 1) { kotlin.math.exp(-((it - rad) * (it - rad)) / (2 * sigma * sigma)) }
                val out = Array(3) { DoubleArray(w) }
                for (x in 0 until w) {
                    var sw = 0.0; val acc = DoubleArray(3)
                    for (o in -rad..rad) {
                        val xx = Math.floorMod(x + o, w)
                        if (!found[xx]) continue
                        val k = kern[o + rad]; sw += k
                        for (ch in 0..2) acc[ch] += k * top[ch][xx]
                    }
                    for (ch in 0..2) out[ch][x] = if (sw > 0) acc[ch] / sw else 0.0
                }
                return out
            }
            val edgeSky = smooth(12.0); val openSky = smooth(220.0)
            val dist = FloorFill.distanceTo(have, w, h)
            // The sky: everything above each column's first seen pixel.
            val firstSeen = IntArray(w) { x -> (0 until h).firstOrNull { have[it * w + x] } ?: h }
            val sky = BooleanArray(w * h) { k -> k / w < firstSeen[k % w] }
            for (y in 0 until h) for (x in 0 until w) {
                val k = y * w + x
                if (have[k] || !sky[k]) continue
                val d = dist[k].toDouble()
                val up = (d / 80.0).coerceIn(0.0, 1.0)   // edge colour -> open sky over ~14 degrees
                val a = (d / 6.0).coerceIn(0.0, 1.0)     // feather into what was seen
                var p = 0
                for (ch in 0..2) {
                    val sky = edgeSky[ch][x] * (1 - up) + openSky[ch][x] * up
                    val near = if (seen[k]) ((pano[k] shr (16 - 8 * ch)) and 0xFF).toDouble() else sky
                    p = p or (((near * (1 - a) + sky * a) + 0.5).toInt().coerceIn(0, 255) shl (16 - 8 * ch))
                }
                pano[k] = p
            }
            for (k in sky.indices) if (sky[k]) have[k] = true
            if (model != null) paintHoles(pano, have, model, progress)
            else {
                // No model: carry the colour down from above.
                for (y in 1 until h) for (x in 0 until w) { val k = y * w + x; if (!have[k]) pano[k] = pano[k - w] }
            }
            return Backdrop(w, h, pano)
        }

        /**
         * Paints the holes with [model] in three overlapping passes, each over half the panorama,
         * keeping the middle third, so each part is painted with what's on both sides of it in view.
         */
        private fun paintHoles(pano: IntArray, have: BooleanArray, model: Inpainter, progress: (Float) -> Unit) {
            val w = WIDTH; val h = HEIGHT
            val s = Inpainter.SIZE
            val sx = (w / 2).toDouble() / s; val sy = h.toDouble() / s
            for (pass in 0 until 3) {
                progress(pass / 3f)
                val keep = Math.floorDiv((2 * pass - 1) * w, 6) until Math.floorDiv((2 * pass + 1) * w, 6)
                if (keep.none { col -> (0 until h).any { !have[it * w + Math.floorMod(col, w)] } }) continue
                val x0 = pass * w / 3 - w / 4
                val rgb = ByteArray(s * s * 3); val hole = BooleanArray(s * s)
                for (ty in 0 until s) for (tx in 0 until s) {
                    var cr = 0; var cg = 0; var cb = 0; var cnt = 0; var miss = false
                    val ya = (ty * sy).toInt(); val yb = max(ya + 1, ((ty + 1) * sy).toInt())
                    val xa = (tx * sx).toInt(); val xb = max(xa + 1, ((tx + 1) * sx).toInt())
                    for (yy in ya until min(h, yb)) for (xx in xa until xb) {
                        val k = yy * w + Math.floorMod(x0 + xx, w)
                        if (!have[k]) { miss = true; continue }
                        val p = pano[k]; cr += (p shr 16) and 0xFF; cg += (p shr 8) and 0xFF; cb += p and 0xFF; cnt++
                    }
                    val o = ty * s + tx
                    hole[o] = miss || cnt == 0
                    if (cnt > 0) { rgb[3 * o] = (cr / cnt).toByte(); rgb[3 * o + 1] = (cg / cnt).toByte(); rgb[3 * o + 2] = (cb / cnt).toByte() }
                }
                val painted = model.inpaint(rgb, hole)
                for (y in 0 until h) for (col in keep) {
                    val k = y * w + Math.floorMod(col, w)
                    if (have[k]) continue
                    val xx = col - x0
                    val fx = ((xx + 0.5) / sx - 0.5).coerceIn(0.0, s - 1.0); val fy = ((y + 0.5) / sy - 0.5).coerceIn(0.0, s - 1.0)
                    val ia = fx.toInt(); val ja = fy.toInt(); val ib = min(s - 1, ia + 1); val jb = min(s - 1, ja + 1)
                    val wx = fx - ia; val wy = fy - ja
                    var p = 0
                    for (ch in 0..2) {
                        fun at(j: Int, i: Int) = (painted[3 * (j * s + i) + ch].toInt() and 0xFF).toDouble()
                        val v = (at(ja, ia) * (1 - wx) + at(ja, ib) * wx) * (1 - wy) + (at(jb, ia) * (1 - wx) + at(jb, ib) * wx) * wy
                        p = p or ((v + 0.5).toInt().coerceIn(0, 255) shl (16 - 8 * ch))
                    }
                    pano[k] = p
                }
                for (y in 0 until h) for (col in keep) have[y * w + Math.floorMod(col, w)] = true
            }
            progress(1f)
        }
    }
}
