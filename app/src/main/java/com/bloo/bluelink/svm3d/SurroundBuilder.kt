package com.bloo.bluelink.svm3d

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One fisheye frame, as 0xRRGGBB pixels. */
class Fisheye(val camera: SvmCamera, val width: Int, val height: Int, val rgb: IntArray) {
    /**
     * The lens's usable area: everything but the flat border around the picture (found by flooding
     * in from the edges over pixels of the border's colour, so dark things inside the picture stay
     * usable), shrunk past the coloured fringe at the lens's edge. The border is black at night but
     * mid-grey by day, so its colour is read from the frame's outer edge.
     */
    val mask: BooleanArray by lazy {
        val edge = ArrayList<Int>()
        for (x in 0 until width) { edge += rgb[x]; edge += rgb[(height - 1) * width + x] }
        for (y in 0 until height) { edge += rgb[y * width]; edge += rgb[y * width + width - 1] }
        fun med(s: Int) = edge.map { (it shr s) and 0xFF }.sorted()[edge.size / 2]
        val br = med(16); val bg = med(8); val bb = med(0)
        fun like(p: Int, tol: Int) = abs(((p shr 16) and 0xFF) - br) <= tol && abs(((p shr 8) and 0xFF) - bg) <= tol && abs((p and 0xFF) - bb) <= tol
        // Only if most of the edge really is a flat border (the picture itself touches the edge in places).
        val flat = edge.count { like(it, 8) } > edge.size * 0.55
        val dark = BooleanArray(width * height) { i ->
            val p = rgb[i]
            maxChannel(p) <= 14 || (flat && like(p, 10))
        }
        val border = BooleanArray(width * height)
        val queue = IntArray(width * height); var head = 0; var tail = 0
        fun push(i: Int) { if (dark[i] && !border[i]) { border[i] = true; queue[tail++] = i } }
        for (x in 0 until width) { push(x); push((height - 1) * width + x) }
        for (y in 0 until height) { push(y * width); push(y * width + width - 1) }
        while (head < tail) {
            val i = queue[head++]; val x = i % width; val y = i / width
            if (x > 0) push(i - 1); if (x < width - 1) push(i + 1)
            if (y > 0) push(i - width); if (y < height - 1) push(i + width)
        }
        erode(BooleanArray(width * height) { !border[it] }, width, height, 8)
    }

    fun sample(u: Double, v: Double): Int {
        val x0 = u.toInt().coerceIn(0, width - 2); val y0 = v.toInt().coerceIn(0, height - 2)
        val fx = (u - x0).coerceIn(0.0, 1.0); val fy = (v - y0).coerceIn(0.0, 1.0)
        val a = rgb[y0 * width + x0]; val b = rgb[y0 * width + x0 + 1]
        val c = rgb[(y0 + 1) * width + x0]; val d = rgb[(y0 + 1) * width + x0 + 1]
        fun ch(p: Int, s: Int) = (p shr s) and 0xFF
        fun mix(s: Int): Int {
            val top = ch(a, s) * (1 - fx) + ch(b, s) * fx
            val bot = ch(c, s) * (1 - fx) + ch(d, s) * fx
            return (top * (1 - fy) + bot * fy + 0.5).toInt().coerceIn(0, 255)
        }
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    companion object {
        fun maxChannel(p: Int) = max(max((p shr 16) and 0xFF, (p shr 8) and 0xFF), p and 0xFF)

        /** Shrinks the true area of a mask by [r] pixels (square neighbourhood). */
        fun erode(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
            val tmp = BooleanArray(m.size)
            for (y in 0 until h) for (x in 0 until w) {
                var ok = true
                for (k in -r..r) { val xx = x + k; if (xx < 0 || xx >= w || !m[y * w + xx]) { ok = false; break } }
                tmp[y * w + x] = ok
            }
            val out = BooleanArray(m.size)
            for (y in 0 until h) for (x in 0 until w) {
                var ok = true
                for (k in -r..r) { val yy = y + k; if (yy < 0 || yy >= h || !tmp[yy * w + x]) { ok = false; break } }
                out[y * w + x] = ok
            }
            return out
        }
    }
}

/** A pinhole view cut from one camera, with its picture and, once known, metric depth. */
class Crop(val view: CropView, val siblingAzimuths: List<Double>) {
    val camera get() = view.camera
    val n get() = view.n
    /** RGB bytes, row by row: what the depth model and the detector look at. */
    val rgb = ByteArray(view.n * view.n * 3)
    val valid = BooleanArray(view.n * view.n)
    /** Distance along each ray to the floor (infinity for rays that don't go down). */
    val floorT = FloatArray(view.n * view.n)
    /** Distance along each ray to whatever it hits, metres (from the depth model). */
    val t = FloatArray(view.n * view.n) { 1000f }
    var depthOk = false

    fun ray(i: Int, j: Int, out: DoubleArray) = view.ray(i, j, out)
}

data class Detection(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val confidence: Float, val kind: String)

/** A neighbouring vehicle: where it is (car frame), which way it points (0 = our forward, + to the right). */
data class Vehicle(val x: Double, val y: Double, val heading: Double, val length: Double, val height: Double, val kind: String)

/** A wall run: points along the ground (car frame) and the wall's height at each. */
class Wall(val xs: DoubleArray, val ys: DoubleArray, val heights: DoubleArray, val color: Int)

/** Everything the 3D view draws apart from our own car. */
class SurroundScene(
    /** Square floor picture, [groundSize] pixels across [groundExtent]*2 metres, north = forward. */
    val groundSize: Int,
    val groundExtent: Double,
    val ground: IntArray,
    val walls: List<Wall>,
    val vehicles: List<Vehicle>,
    /** Which floor pixels a camera actually saw (the rest of [ground] is filled in). */
    val groundSeen: BooleanArray = BooleanArray(0),
    /** The floor as seen, before any filling or fading (only meaningful where [groundSeen]). */
    val groundPhoto: IntArray = IntArray(0),
    /** Everything that stands up, painted from the photos (null if there was nothing usable). */
    val painted: PaintedMesh? = null,
    /** Per backdrop pixel, which cameras see something nearby there (see [Backdrop.occlusion]). */
    val backdropBlocked: ByteArray? = null,
)

interface DepthModel {
    /** Relative inverse depth (bigger = nearer) for an n x n RGB picture. */
    fun disparity(rgb: ByteArray, n: Int): FloatArray
}

interface VehicleDetector {
    fun detect(rgb: ByteArray, n: Int): List<Detection>
}

/**
 * Builds the 3D surroundings from one set of surround-view photos (tools/svm3d has the original
 * Python version and the calibration):
 *  1. flatten each fisheye into a few ordinary views;
 *  2. estimate depth in each, then scale it to metres using the floor, whose distance we know;
 *  3. find nearby vehicles and place a stand-in car for each;
 *  4. sweep round each camera like a parking sensor for the nearest things that stand up: walls;
 *  5. stitch the floor from all four cameras, leaving out what's hidden behind things.
 */
object SurroundBuilder {
    private const val L = SvmCalibration.CAR_LENGTH
    private const val W = SvmCalibration.CAR_WIDTH
    /** Which way each crop looks: (yaw offset from the camera, pitch), degrees. */
    private val PLAN = mapOf(
        "front" to listOf(-60.0 to -12.0, 0.0 to -12.0, 60.0 to -12.0),
        "rear" to listOf(-60.0 to -22.0, 0.0 to -22.0, 60.0 to -22.0),
        "left" to listOf(-55.0 to -30.0, 0.0 to -38.0, 55.0 to -30.0),
        "right" to listOf(-55.0 to -30.0, 0.0 to -38.0, 55.0 to -30.0),
    )

    fun run(
        frames: List<Fisheye>,
        depth: DepthModel,
        detector: VehicleDetector?,
        progress: (String, Float) -> Unit = { _, _ -> },
    ): SurroundScene {
        progress("Flattening the camera views", 0f)
        val crops = makeCrops(frames)
        crops.forEachIndexed { k, c ->
            progress("Estimating depth (${k + 1} of ${crops.size})", 0.05f + 0.6f * k / crops.size)
            applyDepth(c, depth.disparity(c.rgb, c.n))
        }
        val dets = HashMap<Crop, List<Detection>>()
        if (detector != null) {
            crops.forEachIndexed { k, c ->
                progress("Looking for cars (${k + 1} of ${crops.size})", 0.65f + 0.2f * k / crops.size)
                dets[c] = detector.detect(c.rgb, c.n)
            }
        }
        progress("Putting the scene together", 0.88f)
        return assemble(frames, crops, dets)
    }

    // ------------------------------------------------------------------ 1. crops

    fun makeCrops(frames: List<Fisheye>): List<Crop> {
        val out = ArrayList<Crop>()
        val ray = DoubleArray(3); val uv = DoubleArray(2)
        for (fe in frames) {
            val cam = fe.camera
            val plan = PLAN[cam.name] ?: continue
            val mask = fe.mask
            for ((dy, pt) in plan) {
                val view = CropView(cam, cam.yaw + Math.toRadians(dy), Math.toRadians(pt))
                val siblings = plan.filter { it.first != dy }.map { Math.toDegrees(cam.yaw + Math.toRadians(it.first)) }
                val c = Crop(view, siblings)
                val n = view.n
                val (mx, my) = if (cam.name == "left" || cam.name == "right") 0.08 to 0.08 else 0.08 to 0.22
                val (lo, hi) = ownCarBox(cam)
                var validCount = 0
                for (i in 0 until n) for (j in 0 until n) {
                    val k = i * n + j
                    c.ray(i, j, ray)
                    val inside = cam.project(cam.x + ray[0], cam.y + ray[1], cam.z + ray[2], uv) &&
                        uv[0] >= 0 && uv[0] < fe.width - 1 && uv[1] >= 0 && uv[1] < fe.height - 1
                    val p = if (inside) fe.sample(uv[0], uv[1]) else 0
                    c.rgb[3 * k] = (p shr 16).toByte(); c.rgb[3 * k + 1] = (p shr 8).toByte(); c.rgb[3 * k + 2] = p.toByte()
                    val tg = if (ray[2] < -1e-3) (-cam.z / ray[2]) else Double.POSITIVE_INFINITY
                    c.floorT[k] = tg.toFloat()
                    var ok = inside && mask[uv[1].toInt() * fe.width + uv[0].toInt()]
                    if (ok && tg.isFinite()) {
                        val gx = cam.x + ray[0] * tg; val gy = cam.y + ray[1] * tg
                        if (abs(gx) < W / 2 + mx && abs(gy) < L / 2 + my) ok = false
                    }
                    if (ok && hitsBox(cam.position, ray, lo, hi)) ok = false
                    c.valid[k] = ok
                    if (ok) validCount++
                }
                if (validCount > 0.1 * n * n) out += c
            }
        }
        return out
    }

    /** Our own car's body as a box, trimmed so it stops just short of [cam], for rays that hit it. */
    internal fun ownCarBox(cam: SvmCamera): Pair<DoubleArray, DoubleArray> {
        val lo = doubleArrayOf(-W / 2 - 0.02, -L / 2, 0.0); val hi = doubleArrayOf(W / 2 + 0.02, L / 2, 1.6)
        when (cam.name) {
            "front" -> hi[1] = min(hi[1], cam.y - 0.05)
            "rear" -> lo[1] = max(lo[1], cam.y + 0.05)
            "left" -> lo[0] = max(lo[0], cam.x + 0.08)
            "right" -> hi[0] = min(hi[0], cam.x - 0.08)
        }
        return lo to hi
    }

    internal fun hitsBox(o: DoubleArray, d: DoubleArray, lo: DoubleArray, hi: DoubleArray): Boolean {
        var tmin = Double.NEGATIVE_INFINITY; var tmax = Double.POSITIVE_INFINITY
        for (a in 0..2) {
            if (abs(d[a]) < 1e-12) {
                if (o[a] < lo[a] || o[a] > hi[a]) return false
            } else {
                val t1 = (lo[a] - o[a]) / d[a]; val t2 = (hi[a] - o[a]) / d[a]
                tmin = max(tmin, min(t1, t2)); tmax = min(tmax, max(t1, t2))
            }
        }
        return tmax >= max(tmin, 0.0) && tmax > 0
    }

    // ------------------------------------------------------------------ 2. depth in metres

    /**
     * Scales relative depth to metres: on the floor we know the true distance, so fit
     * disparity = a / depth + offset there (robustly, with an offset that may drift smoothly across
     * the picture), then apply it everywhere.
     */
    fun applyDepth(c: Crop, disp: FloatArray) {
        val n = c.n
        val ray = DoubleArray(3)
        val idx = ArrayList<Int>()
        val cosAx = FloatArray(n * n)
        for (i in 0 until n) for (j in 0 until n) {
            val k = i * n + j
            c.ray(i, j, ray)
            cosAx[k] = (ray[0] * c.view.axis[0] + ray[1] * c.view.axis[1] + ray[2] * c.view.axis[2]).toFloat()
            val tg = c.floorT[k]
            if (!c.valid[k] || !tg.isFinite()) continue
            val horiz = tg * hypot(ray[0], ray[1])
            if (horiz > 0.6 && horiz < 9) idx += k
        }
        if (idx.size < 500) { c.depthOk = false; return }
        val rnd = java.util.Random(7)
        val sample = if (idx.size > 20000) List(20000) { idx[rnd.nextInt(idx.size)] } else idx
        val inv = DoubleArray(sample.size) { 1.0 / (c.floorT[sample[it]] * cosAx[sample[it]]) }
        val dv = DoubleArray(sample.size) { disp[sample[it]].toDouble() }
        val ii = DoubleArray(sample.size) { (sample[it] / n).toDouble() / n - 0.5 }
        val jj = DoubleArray(sample.size) { (sample[it] % n).toDouble() / n - 0.5 }
        val spread = percentile(dv, 95.0) - percentile(dv, 5.0) + 1e-6
        // RANSAC on a / z + b
        var bestCount = -1; var bestA = 0.0; var bestB = 0.0
        val tol = 0.04 * spread
        repeat(400) {
            val p = rnd.nextInt(sample.size); val q = rnd.nextInt(sample.size)
            if (abs(inv[p] - inv[q]) < 1e-3) return@repeat
            val a = (dv[p] - dv[q]) / (inv[p] - inv[q])
            if (a <= 0) return@repeat
            val b = dv[p] - a * inv[p]
            var cnt = 0
            for (s in sample.indices) if (abs(a * inv[s] + b - dv[s]) < tol) cnt++
            if (cnt > bestCount) { bestCount = cnt; bestA = a; bestB = b }
        }
        if (bestCount < 100) { c.depthOk = false; return }
        var inl = BooleanArray(sample.size) { abs(bestA * inv[it] + bestB - dv[it]) < 0.035 * spread }
        // On a flat floor, inverse depth is exactly linear across the picture, so a linear drift
        // can't be told apart from scale: only allow a gentle curved (quadratic) drift.
        var coef = doubleArrayOf(bestA, bestB, 0.0, 0.0, 0.0)
        repeat(3) {
            val rows = ArrayList<DoubleArray>(); val rhs = ArrayList<Double>()
            for (s in sample.indices) if (inl[s]) {
                rows += doubleArrayOf(inv[s], 1.0, ii[s] * jj[s], ii[s] * ii[s], jj[s] * jj[s]); rhs += dv[s]
            }
            if (rows.size < 50) return@repeat
            // Keep the drift small unless the floor clearly asks for it (it has to extrapolate to
            // parts of the picture with no floor).
            val ridge = 0.5 * sqrt(rows.size.toDouble())
            for (q in 2..4) { rows += DoubleArray(5).also { it[q] = ridge }; rhs += 0.0 }
            coef = leastSquares(rows, rhs.toDoubleArray())
            inl = BooleanArray(sample.size) {
                val pred = coef[0] * inv[it] + coef[1] + coef[2] * ii[it] * jj[it] + coef[3] * ii[it] * ii[it] + coef[4] * jj[it] * jj[it]
                abs(pred - dv[it]) < 0.03 * spread
            }
        }
        if (coef[0] <= 0) { c.depthOk = false; return }
        for (i in 0 until n) for (j in 0 until n) {
            val k = i * n + j
            val y = i.toDouble() / n - 0.5; val x = j.toDouble() / n - 0.5
            val off = coef[1] + coef[2] * y * x + coef[3] * y * y + coef[4] * x * x
            val d = disp[k] - off
            val z = if (d > 0) coef[0] / d else 1000.0
            c.t[k] = min(if (z > 0) z / max(cosAx[k].toDouble(), 1e-3) else 1000.0, 60.0).toFloat()
        }
        c.depthOk = true
    }

    // ------------------------------------------------------------------ 3-5. the scene

    private class Pool(var xs: DoubleArray, var ys: DoubleArray, var zs: DoubleArray, var conf: Float, val kind: String, val cam: SvmCamera, var aspect: Double)

    fun assemble(frames: List<Fisheye>, allCrops: List<Crop>, detections: Map<Crop, List<Detection>>): SurroundScene {
        val crops = allCrops.filter { it.depthOk }
        val ray = DoubleArray(3)
        val carPixels = HashMap<Crop, BooleanArray>()

        // Vehicles.
        val found = ArrayList<Pool>()
        for (c in crops) {
            val n = c.n
            val mine = BooleanArray(n * n)
            for (d in detections[c].orEmpty()) {
                val x0 = max(0, d.x0.toInt()); val y0 = max(0, d.y0.toInt())
                val x1 = min(n, d.x1.toInt()); val y1 = min(n, d.y1.toInt())
                if ((x1 - x0) * (y1 - y0) < 400) continue
                val members = ArrayList<Int>()
                for (i in y0 until y1) for (j in x0 until x1) {
                    val k = i * n + j
                    if (!standing(c, k, ray, 0.08, 18.0)) continue
                    members += k
                }
                if (members.size < 150) continue
                // The main depth cluster in the box (the vehicle, not a post in front or the wall behind).
                val hist = IntArray(40)
                val lts = DoubleArray(members.size) { ln(c.t[members[it]].toDouble()) }
                val lo = lts.min(); val hi = lts.max() + 1e-6
                for (v in lts) hist[((v - lo) / (hi - lo) * 40).toInt().coerceIn(0, 39)]++
                var best = 0; var bestV = -1
                for (b in 0 until 40) {
                    val s = 2 * hist[b] + (if (b > 0) hist[b - 1] else 0) + (if (b < 39) hist[b + 1] else 0)
                    if (s > bestV) { bestV = s; best = b }
                }
                val centre = exp(lo + (best + 0.5) / 40 * (hi - lo))
                val kept = members.filter { c.t[it] > centre / 1.45 && c.t[it] < centre * 1.45 }
                if (kept.size < 150 || centre > 10) continue
                val implied = c.view.fp * 1.6 / max(1, y1 - y0)
                if (centre / implied !in 0.45..2.2) continue
                val xs = ArrayList<Double>(); val ys = ArrayList<Double>(); val zs = ArrayList<Double>()
                for (k in kept) {
                    c.ray(k / n, k % n, ray)
                    val t = c.t[k]
                    val px = c.camera.x + ray[0] * t; val py = c.camera.y + ray[1] * t
                    if (!inSector(c.camera.name, px, py, 25.0)) continue
                    xs += px; ys += py; zs += c.camera.z + ray[2] * t
                    mine[k] = true
                }
                if (xs.size < 150) continue
                val p = Pool(xs.toDoubleArray(), ys.toDoubleArray(), zs.toDoubleArray(), d.confidence, d.kind, c.camera, (x1 - x0).toDouble() / max(1, y1 - y0))
                // Pool with what's already been seen of the same vehicle.
                val mx = median(p.xs); val my = median(p.ys)
                val same = found.firstOrNull { hypot(median(it.xs) - mx, median(it.ys) - my) < 2.6 }
                if (same != null) {
                    same.xs += p.xs; same.ys += p.ys; same.zs += p.zs
                    same.conf = max(same.conf, p.conf); same.aspect = max(same.aspect, p.aspect)
                } else found += p
            }
            carPixels[c] = mine
        }
        val vehicles = ArrayList<Vehicle>()
        for (p in found.sortedByDescending { it.xs.size }) {
            val v = place(p) ?: continue
            if (hypot(v.x, v.y) > 10) continue
            if (abs(v.x) < W / 2 + 0.6 && abs(v.y) < L / 2 + 0.6) continue
            val dup = vehicles.any { q ->
                val a = Math.toRadians(q.heading); val ax = doubleArrayOf(kotlin.math.sin(a), kotlin.math.cos(a))
                val dx = v.x - q.x; val dy = v.y - q.y
                abs(dx * ax[0] + dy * ax[1]) < q.length / 2 + 0.8 && abs(dx * ax[1] - dy * ax[0]) < 1.6
            }
            if (!dup) vehicles += v
        }

        val walls = sweepWalls(crops, carPixels, vehicles)
        val seen = BooleanArray(GROUND_N * GROUND_N)
        val photo = IntArray(GROUND_N * GROUND_N)
        val ground = stitchFloor(frames, crops, seen, photo)
        val painted = PaintedSurface.build(crops, vehicles).takeIf { it.triangleCount > 0 }
        return SurroundScene(GROUND_N, GROUND_EXT, ground, walls, vehicles, seen, photo, painted, Backdrop.occlusion(frames, crops))
    }

    private fun standing(c: Crop, k: Int, ray: DoubleArray, minZ: Double, maxT: Double, maxZ: Double = 9.0): Boolean {
        if (!c.valid[k]) return false
        val t = c.t[k]
        if (t >= maxT) return false
        val tg = c.floorT[k]
        if (tg.isFinite() && t > tg * 0.85) return false
        val n = c.n
        c.ray(k / n, k % n, ray)
        val z = c.camera.z + ray[2] * t
        return z > minZ && z < maxZ
    }

    private fun inSector(cam: String, x: Double, y: Double, margin: Double): Boolean {
        val az = Math.toDegrees(atan2(x, y))
        val d = ((az - SvmCalibration.SECTOR.getValue(cam) + 540) % 360) - 180
        return abs(d) < 45 + margin
    }

    /** A vehicle's footprint from the points on its visible surface. */
    private fun place(p: Pool): Vehicle? {
        var sel = p.xs.indices.filter { p.zs[it] > 0.3 && p.zs[it] < 1.6 }
        if (sel.size <= 200) sel = p.xs.indices.toList()
        val xs = DoubleArray(sel.size) { p.xs[sel[it]] }; val ys = DoubleArray(sel.size) { p.ys[sel[it]] }
        var cx = median(xs); var cy = median(ys)
        // The straightest line through the points: a car's flat side. (Depth errors smear points
        // along the viewing rays, which would fool a plain principal axis.)
        val rnd = java.util.Random(1)
        val sub = if (xs.size > 4000) IntArray(4000) { rnd.nextInt(xs.size) } else IntArray(xs.size) { it }
        var best = -1; var mx = 0.0; var my = 1.0
        repeat(300) {
            val a = sub[rnd.nextInt(sub.size)]; val b = sub[rnd.nextInt(sub.size)]
            var dx = xs[b] - xs[a]; var dy = ys[b] - ys[a]
            val nd = hypot(dx, dy)
            if (nd < 0.4) return@repeat
            dx /= nd; dy /= nd
            var cnt = 0
            for (s in sub) if (abs((xs[s] - xs[a]) * -dy + (ys[s] - ys[a]) * dx) < 0.12) cnt++
            if (cnt > best) { best = cnt; mx = dx; my = dy }
        }
        val nx = -my; val ny = mx
        val near = xs.indices.filter { abs((xs[it] - cx) * nx + (ys[it] - cy) * ny) < 0.3 }
        if (near.size > 50) { cx = median(DoubleArray(near.size) { xs[near[it]] }); cy = median(DoubleArray(near.size) { ys[near[it]] }) }
        val onLine = xs.indices.filter { abs((xs[it] - cx) * nx + (ys[it] - cy) * ny) < 0.2 }
        val use = if (onLine.size > 50) onLine else xs.indices.toList()
        val proj = DoubleArray(use.size) { (xs[use[it]] - cx) * mx + (ys[use[it]] - cy) * my }
        val lo = percentile(proj, 0.5); val hi = percentile(proj, 99.5)
        val extent = hi - lo
        var ax = p.cam.x; var ay = p.cam.y
        var awx = cx - ax; var awy = cy - ay
        val an = hypot(awx, awy); awx /= an; awy /= an
        val closeSide = an < 3.0 && abs(mx * awx + my * awy) < 0.4
        val length: Double; val centreX: Double; val centreY: Double; val axX: Double; val axY: Double
        if (extent > 2.1 || p.aspect > 1.5 || closeSide) {
            axX = mx; axY = my
            var nX = -axY; var nY = axX
            if (nX * awx + nY * awy < 0) { nX = -nX; nY = -nY }
            length = extent.coerceIn(4.2, 5.4)
            val midX = cx + mx * (lo + hi) / 2; val midY = cy + my * (lo + hi) / 2
            centreX = midX + nX * 0.92; centreY = midY + nY * 0.92
        } else {
            axX = awx; axY = awy
            length = 4.6
            centreX = cx + awx * length / 2; centreY = cy + awy * length / 2
        }
        val height = percentile(DoubleArray(sel.size) { p.zs[sel[it]] }, 97.0).coerceIn(1.3, 2.1)
        // From the side we can't tell nose from tail; assume it's parked the same way round as us.
        var heading = Math.toDegrees(atan2(axX, axY))
        if (abs(heading) > 90) heading = if (heading > 0) heading - 180 else heading + 180
        return Vehicle(centreX, centreY, heading, length, height, p.kind)
    }

    internal fun inAnyVehicle(vs: List<Vehicle>, x: Double, y: Double): Boolean = vs.any { v ->
        val a = Math.toRadians(v.heading)
        val ax = kotlin.math.sin(a); val ay = kotlin.math.cos(a)
        val dx = x - v.x; val dy = y - v.y
        abs(dx * ax + dy * ay) < v.length / 2 + 0.4 && abs(dx * ay - dy * ax) < 1.25
    }

    /** Walls: for each direction from each camera, the nearest thing that stands up, and the next one behind it. */
    private fun sweepWalls(crops: List<Crop>, carPixels: Map<Crop, BooleanArray>, vehicles: List<Vehicle>): List<Wall> {
        val walls = ArrayList<Wall>()
        val ray = DoubleArray(3)
        for (camName in SvmCalibration.SECTOR.keys) {
            val mine = crops.filter { it.camera.name == camName }
            if (mine.isEmpty()) continue
            val cam = mine[0].camera
            val azs = ArrayList<Double>(); val rs = ArrayList<Double>(); val zs = ArrayList<Double>(); val cols = ArrayList<Int>()
            for (c in mine) {
                val n = c.n
                val skip = carPixels[c]
                for (k in 0 until n * n step 2) {
                    if (skip != null && skip[k]) continue
                    if (!standing(c, k, ray, 0.12, 10.0, 2.3)) continue
                    val t = c.t[k]
                    val px = cam.x + ray[0] * t; val py = cam.y + ray[1] * t; val pz = cam.z + ray[2] * t
                    if (!inSector(camName, px, py, 12.0)) continue
                    if (abs(px) < W / 2 + 0.45 && abs(py) < L / 2 + 0.35) continue
                    if (inAnyVehicle(vehicles, px, py)) continue
                    val dx = px - cam.x; val dy = py - cam.y
                    azs += Math.toDegrees(atan2(dx, dy)); rs += hypot(dx, dy); zs += pz
                    val o = 3 * k
                    cols += ((c.rgb[o].toInt() and 0xFF) shl 16) or ((c.rgb[o + 1].toInt() and 0xFF) shl 8) or (c.rgb[o + 2].toInt() and 0xFF)
                }
            }
            val nb = 240
            val byBin = Array(nb) { ArrayList<Int>() }
            for (i in azs.indices) byBin[((azs[i] + 180) / 1.5).toInt().coerceIn(0, nb - 1)] += i
            class Item(val x: Double, val y: Double, val h: Double, val near: Double, val color: Int, val a: Double)
            val layers = Array(2) { arrayOfNulls<Item>(nb) }
            for (b in 0 until nb) {
                var beyond = 0.0
                for (layer in 0..1) {
                    val m = byBin[b].filter { rs[it] > beyond }
                    if (m.size < 25 / 2) break
                    val near = percentile(DoubleArray(m.size) { rs[m[it]] }, 8.0)
                    val close = m.filter { rs[it] < near + 0.35 }
                    if (close.size < 15 / 2) break
                    val h = percentile(DoubleArray(close.size) { zs[close[it]] }, 92.0).coerceIn(0.15, 2.2)
                    val a = Math.toRadians(-180 + (b + 0.5) * 1.5)
                    var rr = 0L; var gg = 0L; var bb = 0L
                    for (i in close) { rr += (cols[i] shr 16) and 0xFF; gg += (cols[i] shr 8) and 0xFF; bb += cols[i] and 0xFF }
                    val color = ((rr / close.size).toInt() shl 16) or ((gg / close.size).toInt() shl 8) or (bb / close.size).toInt()
                    layers[layer][b] = Item(cam.x + near * kotlin.math.sin(a), cam.y + near * kotlin.math.cos(a), h, near, color, a)
                    beyond = near + 0.9
                }
            }
            for (layer in layers) {
                val run = ArrayList<Item>()
                fun flush() {
                    if (run.size < 3) { run.clear(); return }
                    val r0 = DoubleArray(run.size) { run[it].near }
                    val k = min(7, run.size / 2 * 2 + 1)
                    val med = DoubleArray(run.size) { i -> median(r0.copyOfRange(max(0, i - k / 2), min(run.size, i + k / 2 + 1))) }
                    val sm = DoubleArray(run.size) { i ->
                        var s = 0.0
                        for (o in -2..2) s += med[(i + o).coerceIn(0, run.size - 1)]
                        s / 5
                    }
                    val h0 = DoubleArray(run.size) { run[it].h }
                    val hs = DoubleArray(run.size) { i ->
                        val v = median(h0.copyOfRange(max(0, i - 4), min(run.size, i + 5)))
                        if (v < 0.5) 0.22 else v
                    }
                    val xs = DoubleArray(run.size) { cam.x + sm[it] * kotlin.math.sin(run[it].a) }
                    val ys = DoubleArray(run.size) { cam.y + sm[it] * kotlin.math.cos(run[it].a) }
                    var rr = 0L; var gg = 0L; var bb = 0L
                    for (it in run) { rr += (it.color shr 16) and 0xFF; gg += (it.color shr 8) and 0xFF; bb += it.color and 0xFF }
                    val color = ((rr / run.size).toInt() shl 16) or ((gg / run.size).toInt() shl 8) or (bb / run.size).toInt()
                    walls += Wall(xs, ys, hs, color)
                    run.clear()
                }
                for (item in layer) {
                    if (item == null) { flush(); continue }
                    if (run.isNotEmpty() && abs(item.near - run.last().near) > 0.3 + 0.1 * item.near) flush()
                    run += item
                }
                flush()
            }
        }
        return walls
    }

    const val GROUND_N = 800
    const val GROUND_EXT = 12.0

    /** The floor from all four cameras, each spot taken from whichever camera sees it best. */
    fun stitchFloor(frames: List<Fisheye>, crops: List<Crop>, seenOut: BooleanArray? = null, photoOut: IntArray? = null): IntArray {
        val n = GROUND_N; val res = 2 * GROUND_EXT / n
        val total = n * n
        val camIdx = frames.indices
        val colR = Array(frames.size) { FloatArray(total) }; val colG = Array(frames.size) { FloatArray(total) }; val colB = Array(frames.size) { FloatArray(total) }
        val wt = Array(frames.size) { FloatArray(total) }
        val seen = Array(frames.size) { BooleanArray(total) }
        val uv = DoubleArray(2); val pix = DoubleArray(2)
        for (fi in camIdx) {
            val fe = frames[fi]; val cam = fe.camera
            val mask = fe.mask
            val mine = crops.filter { it.camera === cam }
            for (r in 0 until n) for (cc in 0 until n) {
                val gx = -GROUND_EXT + (cc + 0.5) * res; val gy = GROUND_EXT - (r + 0.5) * res
                if (abs(gx) < W / 2 + 0.05 && abs(gy) < L / 2 + 0.05) continue
                val dist = hypot(gx - cam.x, gy - cam.y)
                if (dist > 14) continue
                if (!cam.project(gx, gy, 0.0, uv)) continue
                if (uv[0] <= 0 || uv[0] >= fe.width - 1 || uv[1] <= 0 || uv[1] >= fe.height - 1) continue
                if (!mask[uv[1].toInt() * fe.width + uv[0].toInt()]) continue
                // Seen only if the camera's depth along that ray reaches the floor (nothing in the way).
                val dx = gx - cam.x; val dy = gy - cam.y; val dz = -cam.z
                val dl = sqrt(dx * dx + dy * dy + dz * dz)
                val ux = dx / dl; val uy = dy / dl; val uz = dz / dl
                val az = Math.toDegrees(atan2(ux, uy))
                var bestOff = 1e9; var visible = false; var decided = false
                for (c in mine) {
                    if (!c.view.pixelOf(ux, uy, uz, pix)) continue
                    val off = abs(((az - c.view.centerAzimuthDeg + 540) % 360) - 180)
                    if (off >= bestOff) continue
                    bestOff = off; decided = true
                    val k = pix[0].toInt() * c.n + pix[1].toInt()
                    visible = c.valid[k] && c.t[k] > dl * 0.88
                }
                if (!decided || !visible) continue
                val k = r * n + cc
                val p = fe.sample(uv[0], uv[1])
                colR[fi][k] = ((p shr 16) and 0xFF).toFloat(); colG[fi][k] = ((p shr 8) and 0xFF).toFloat(); colB[fi][k] = (p and 0xFF).toFloat()
                wt[fi][k] = exp(-dist / 1.2).toFloat()
                seen[fi][k] = true
            }
        }
        // Exposure: one gain per camera so overlapping floor matches (log-brightness least squares).
        val rows = ArrayList<DoubleArray>(); val rhs = ArrayList<Double>()
        for (a in camIdx) for (b in camIdx) if (b > a) {
            var la = 0.0; var lb = 0.0; var cnt = 0
            for (k in 0 until total) if (seen[a][k] && seen[b][k]) {
                la += ln((colR[a][k] + colG[a][k] + colB[a][k]) / 3.0 + 5); lb += ln((colR[b][k] + colG[b][k] + colB[b][k]) / 3.0 + 5); cnt++
            }
            if (cnt < 200) continue
            val row = DoubleArray(frames.size); row[a] = 1.0; row[b] = -1.0
            rows += row; rhs += (lb - la) / cnt
        }
        rows += DoubleArray(frames.size).also { it[0] = 1.0 }; rhs += 0.0
        val g = leastSquares(rows, rhs.toDoubleArray())
        val gain = DoubleArray(frames.size) { exp(g[it]).coerceIn(0.5, 2.5) }

        val outR = FloatArray(total); val outG = FloatArray(total); val outB = FloatArray(total)
        val have = BooleanArray(total)
        for (k in 0 until total) {
            var s = 0.0; var r = 0.0; var gg = 0.0; var b = 0.0
            for (fi in camIdx) if (seen[fi][k]) {
                val w = wt[fi][k] * gain[fi]
                r += colR[fi][k] * w; gg += colG[fi][k] * w; b += colB[fi][k] * w; s += wt[fi][k]
            }
            if (s > 1e-4) { outR[k] = (r / s).toFloat(); outG[k] = (gg / s).toFloat(); outB[k] = (b / s).toFloat(); have[k] = true }
        }
        seenOut?.let { have.copyInto(it) }
        photoOut?.let { o ->
            for (k in 0 until total) if (have[k]) {
                o[k] = (outR[k].toInt().coerceIn(0, 255) shl 16) or (outG[k].toInt().coerceIn(0, 255) shl 8) or outB[k].toInt().coerceIn(0, 255)
            }
        }
        // Blind spots: fill smoothly from around them (push-pull), a little darker.
        val fill = pushPullFill(outR, outG, outB, have, n)
        val alpha = blurMask(have, n, 3)
        val out = IntArray(total)
        for (k in 0 until total) {
            val a = alpha[k]
            val r = (k / n); val cc = k % n
            val gx = -GROUND_EXT + (cc + 0.5) * res; val gy = GROUND_EXT - (r + 0.5) * res
            val fade = edgeFade(hypot(gx, gy))
            fun ch(v: Float, f: Float) = ((v * a + f * 0.82f * (1 - a)) * fade).toInt().coerceIn(0, 255)
            out[k] = (ch(outR[k], fill[0][k]) shl 16) or (ch(outG[k], fill[1][k]) shl 8) or ch(outB[k], fill[2][k])
        }
        return out
    }

    /** The floor runs just past where the backdrop dome meets it (so no dark seam shows), then stops. */
    fun edgeFade(r: Double) = ((Backdrop.FLOOR_REACH + 0.3 - r) / 0.15).coerceIn(0.0, 1.0)

    /** Fills the holes in an image by averaging down a pyramid and back up. */
    private fun pushPullFill(r: FloatArray, g: FloatArray, b: FloatArray, have: BooleanArray, n: Int): Array<FloatArray> {
        if (n <= 1) return arrayOf(r.copyOf(), g.copyOf(), b.copyOf())
        val h = (n + 1) / 2
        val r2 = FloatArray(h * h); val g2 = FloatArray(h * h); val b2 = FloatArray(h * h); val have2 = BooleanArray(h * h)
        for (y in 0 until h) for (x in 0 until h) {
            var s = 0f; var rr = 0f; var gg = 0f; var bb = 0f
            for (dy in 0..1) for (dx in 0..1) {
                val yy = 2 * y + dy; val xx = 2 * x + dx
                if (yy >= n || xx >= n) continue
                val k = yy * n + xx
                if (have[k]) { s++; rr += r[k]; gg += g[k]; bb += b[k] }
            }
            if (s > 0) { val k = y * h + x; r2[k] = rr / s; g2[k] = gg / s; b2[k] = bb / s; have2[k] = true }
        }
        val any = have2.any { it }
        val coarse = if (!any) arrayOf(r2, g2, b2) else if (have2.all { it }) arrayOf(r2, g2, b2) else pushPullFill(r2, g2, b2, have2, h)
        val out = arrayOf(r.copyOf(), g.copyOf(), b.copyOf())
        for (y in 0 until n) for (x in 0 until n) {
            val k = y * n + x
            if (have[k]) continue
            // Bilinear from the coarser level, so the fill is smooth rather than blocky.
            val fy = (y - 0.5) / 2.0; val fx = (x - 0.5) / 2.0
            val y0 = fy.toInt().coerceIn(0, h - 1); val x0 = fx.toInt().coerceIn(0, h - 1)
            val y1 = min(h - 1, y0 + 1); val x1 = min(h - 1, x0 + 1)
            val wy = (fy - y0).coerceIn(0.0, 1.0).toFloat(); val wx = (fx - x0).coerceIn(0.0, 1.0).toFloat()
            for (ch in 0..2) {
                val c = coarse[ch]
                val top = c[y0 * h + x0] * (1 - wx) + c[y0 * h + x1] * wx
                val bot = c[y1 * h + x0] * (1 - wx) + c[y1 * h + x1] * wx
                out[ch][k] = top * (1 - wy) + bot * wy
            }
        }
        return out
    }

    private fun blurMask(m: BooleanArray, n: Int, radius: Int): FloatArray {
        var a = FloatArray(m.size) { if (m[it]) 1f else 0f }
        repeat(2) {
            val t = FloatArray(a.size)
            for (y in 0 until n) for (x in 0 until n) {
                var s = 0f; var c = 0
                for (d in -radius..radius) { val xx = x + d; if (xx in 0 until n) { s += a[y * n + xx]; c++ } }
                t[y * n + x] = s / c
            }
            val u = FloatArray(a.size)
            for (y in 0 until n) for (x in 0 until n) {
                var s = 0f; var c = 0
                for (d in -radius..radius) { val yy = y + d; if (yy in 0 until n) { s += t[yy * n + x]; c++ } }
                u[y * n + x] = s / c
            }
            a = u
        }
        // Keep seen spots fully photo; only soften the edge into the fill.
        for (k in a.indices) if (m[k]) a[k] = max(a[k], 0.6f)
        return a
    }

    // ------------------------------------------------------------------ helpers

    fun percentile(v: DoubleArray, p: Double): Double {
        if (v.isEmpty()) return 0.0
        val s = v.copyOf(); s.sort()
        val pos = (p / 100.0) * (s.size - 1)
        val i = pos.toInt(); val f = pos - i
        return if (i + 1 < s.size) s[i] * (1 - f) + s[i + 1] * f else s[i]
    }

    fun median(v: DoubleArray) = percentile(v, 50.0)

    /** Least squares by normal equations (small problems only). */
    fun leastSquares(rows: List<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val m = rows[0].size
        val a = Array(m) { DoubleArray(m) }; val b = DoubleArray(m)
        for ((r, row) in rows.withIndex()) {
            for (i in 0 until m) {
                b[i] += row[i] * rhs[r]
                for (j in 0 until m) a[i][j] += row[i] * row[j]
            }
        }
        for (i in 0 until m) a[i][i] += 1e-9
        // Gaussian elimination with partial pivoting.
        for (col in 0 until m) {
            var piv = col
            for (r in col + 1 until m) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
            val tmp = a[col]; a[col] = a[piv]; a[piv] = tmp
            val tb = b[col]; b[col] = b[piv]; b[piv] = tb
            if (abs(a[col][col]) < 1e-12) continue
            for (r in col + 1 until m) {
                val f = a[r][col] / a[col][col]
                for (c in col until m) a[r][c] -= f * a[col][c]
                b[r] -= f * b[col]
            }
        }
        val x = DoubleArray(m)
        for (i in m - 1 downTo 0) {
            var s = b[i]
            for (j in i + 1 until m) s -= a[i][j] * x[j]
            x[i] = if (abs(a[i][i]) < 1e-12) 0.0 else s / a[i][i]
        }
        return x
    }
}
