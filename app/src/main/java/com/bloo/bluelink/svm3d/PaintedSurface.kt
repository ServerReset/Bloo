package com.bloo.bluelink.svm3d

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * The surroundings as the cameras saw them: every pixel's colour put back where its depth says it
 * was, joined to its neighbours so it reads as a surface rather than dots. Car frame (X right,
 * Y forward, Z up), colours linear RGB 0..1.
 */
class PaintedMesh(val positions: FloatArray, val colors: FloatArray, val indices: IntArray) {
    val vertexCount get() = positions.size / 3
    val triangleCount get() = indices.size / 3
}

object PaintedSurface {
    /** Grid step in crop pixels: 2 keeps a point every ~3 cm at 5 m, plenty for a phone screen. */
    private const val STEP = 2
    /** Ignore anything further than this along the ray: depth gets too coarse to be worth drawing. */
    private const val MAX_T = 25f
    /** Only what's within this of the car (metres, along the ground): further out, the bowl. */
    val REACH = Backdrop.FLOOR_REACH - 0.4
    /** A piece of surface counts as standing on the ground if it comes down to this height (metres). */
    private const val GROUNDED = 0.35f
    /** Pieces smaller than this many grid points are crumbs. */
    private const val MIN_PIECE = 40
    /** Grid points either side used to judge a surface's slope. */
    private const val SPAN = 2
    /** A pixel whose depth jumps by more than this (in log) to a neighbour sits on an edge: neither side. */
    private const val EDGE = 0.06

    fun build(crops: List<Crop>, vehicles: List<Vehicle>): PaintedMesh {
        class Grid(val crop: Crop, val m: Int, val x: FloatArray, val y: FloatArray, val z: FloatArray, val keep: BooleanArray, val col: FloatArray, val lt: FloatArray)
        val grids = ArrayList<Grid>()
        val ray = DoubleArray(3)
        val toLin = FloatArray(256) { c -> srgbToLinear(c / 255f) }
        for (c in crops) {
            if (!c.depthOk) continue
            val n = c.n
            val m = (n - 1) / STEP
            val x = FloatArray(m * m); val y = FloatArray(m * m); val z = FloatArray(m * m)
            val keep = BooleanArray(m * m); val col = FloatArray(3 * m * m); val lt = FloatArray(m * m)
            val L = SvmCalibration.CAR_LENGTH; val W = SvmCalibration.CAR_WIDTH
            for (gi in 0 until m) for (gj in 0 until m) {
                val i = gi * STEP + 1; val j = gj * STEP + 1
                val k = i * n + j
                val g = gi * m + gj
                if (!c.valid[k]) continue
                val t = c.t[k]
                if (t >= MAX_T) continue
                lt[g] = ln(max(t, 0.05f))
                // Flying pixels: the depth model blends foreground and background across edges.
                var jump = 0.0
                for (kk in intArrayOf(k - 1, k - n, k + 1, k + n)) {
                    jump = max(jump, abs(lt[g] - ln(max(c.t[kk], 0.05f)).toDouble()))
                }
                if (jump > EDGE) continue
                c.ray(i, j, ray)
                // Each direction belongs to the crop that looks most straight at it.
                val az = Math.toDegrees(atan2(ray[0], ray[1]))
                val mine = angle(az, c.view.centerAzimuthDeg)
                if (c.siblingAzimuths.any { angle(az, it) < mine }) continue
                val px = c.camera.x + ray[0] * t; val py = c.camera.y + ray[1] * t; val pz = c.camera.z + ray[2] * t
                // ...and to the camera whose side of the car it's on.
                if (angle(Math.toDegrees(atan2(px, py)), SvmCalibration.SECTOR.getValue(c.camera.name)) > 50) continue
                if (abs(px) < W / 2 + 0.05 && abs(py) < L / 2 + 0.05 && pz < 1.7) continue
                // Further out the depth is too rough for a surface; the bowl (backdrop) shows it.
                if (hypot(px, py) > REACH) continue
                // The floor is drawn separately (stitched, with the gaps painted in); keep what stands up.
                val tg = c.floorT[k]
                if (tg.isFinite() && t > tg * 0.85) continue
                if (pz < 0.06) continue
                x[g] = px.toFloat(); y[g] = py.toFloat(); z[g] = pz.toFloat()
                keep[g] = true
                // Colour: the average of the pixels this grid point stands for.
                var r = 0f; var gg = 0f; var b = 0f; var cnt = 0
                for (di in 0 until STEP) for (dj in 0 until STEP) {
                    val kk = (i + di) * n + (j + dj)
                    if (i + di >= n || j + dj >= n || !c.valid[kk]) continue
                    r += toLin[c.rgb[3 * kk].toInt() and 0xFF]; gg += toLin[c.rgb[3 * kk + 1].toInt() and 0xFF]; b += toLin[c.rgb[3 * kk + 2].toInt() and 0xFF]
                    cnt++
                }
                if (cnt > 0) { col[3 * g] = r / cnt; col[3 * g + 1] = gg / cnt; col[3 * g + 2] = b / cnt }
            }
            grids += Grid(c, m, x, y, z, keep, col, lt)
        }

        // Level surfaces outside any vehicle aren't real here: floor the depth put a little too near
        // (so it hovers over the real floor, which the floor picture shows anyway), far floor the
        // depth bent upwards, or ceiling. Judge "level" by the surface's slope across nearby grid
        // points, so leaning surfaces (a hedge, a wall seen at an angle) stay.
        for (gd in grids) {
            val m = gd.m
            val was = gd.keep.copyOf()
            fun at(g: Int) = g in 0 until m * m && was[g]
            for (gi in 0 until m) for (gj in 0 until m) {
                val g = gi * m + gj
                if (!was[g]) continue
                val s = SPAN
                val l = if (gj >= s && at(g - s)) g - s else g; val r = if (gj + s < m && at(g + s)) g + s else g
                val u = if (gi >= s && at(g - s * m)) g - s * m else g; val d = if (gi + s < m && at(g + s * m)) g + s * m else g
                if (l == r || u == d) continue
                val ax = gd.x[r] - gd.x[l]; val ay = gd.y[r] - gd.y[l]; val az = gd.z[r] - gd.z[l]
                val bx = gd.x[d] - gd.x[u]; val by = gd.y[d] - gd.y[u]; val bz = gd.z[d] - gd.z[u]
                val nx = ay * bz - az * by; val ny = az * bx - ax * bz; val nz = ax * by - ay * bx
                val nl = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9f
                if (abs(nz) / nl > 0.85f && !SurroundBuilder.inAnyVehicle(vehicles, gd.x[g].toDouble(), gd.y[g].toDouble())) gd.keep[g] = false
            }
        }

        // Keep only pieces of surface that stand on the ground. Things seen over the top of something
        // nearer (a building behind a hedge, a tree canopy) are cut off from the ground by a jump in
        // depth, and that's exactly where the depth model is least sure how far away they are: the
        // bowl (backdrop) shows them instead.
        for (gd in grids) {
            val m = gd.m
            val parent = IntArray(m * m) { it }
            fun find(a: Int): Int { var x = a; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
            fun join(a: Int, b: Int) {
                if (!gd.keep[a] || !gd.keep[b] || abs(gd.lt[a] - gd.lt[b]) >= 2.0 * EDGE) return
                val ra = find(a); val rb = find(b); if (ra != rb) parent[ra] = rb
            }
            for (gi in 0 until m) for (gj in 0 until m) {
                val g = gi * m + gj
                if (!gd.keep[g]) continue
                if (gj + 1 < m) join(g, g + 1)
                if (gi + 1 < m) join(g, g + m)
            }
            val size = IntArray(m * m); val low = FloatArray(m * m) { Float.MAX_VALUE }; val onVehicle = BooleanArray(m * m)
            for (g in 0 until m * m) if (gd.keep[g]) {
                val r = find(g); size[r]++; low[r] = minOf(low[r], gd.z[g])
                // A detected vehicle's body counts as standing on the ground, wheel arches and all.
                if (!onVehicle[r] && SurroundBuilder.inAnyVehicle(vehicles, gd.x[g].toDouble(), gd.y[g].toDouble())) onVehicle[r] = true
            }
            for (g in 0 until m * m) if (gd.keep[g]) {
                val r = find(g)
                if (size[r] < MIN_PIECE || (low[r] > GROUNDED && !onVehicle[r])) gd.keep[g] = false
            }
        }

        // Join neighbours into triangles where nothing jumps between them; drop points left alone.
        val pos = FloatList(); val cols = FloatList(); val idx = IntList()
        for (gd in grids) {
            val m = gd.m
            val vid = IntArray(m * m) { -1 }
            fun vertex(g: Int): Int {
                if (vid[g] < 0) {
                    vid[g] = pos.size / 3
                    pos.add(gd.x[g]); pos.add(gd.y[g]); pos.add(gd.z[g])
                    cols.add(gd.col[3 * g]); cols.add(gd.col[3 * g + 1]); cols.add(gd.col[3 * g + 2])
                }
                return vid[g]
            }
            fun ok(a: Int, b: Int) = abs(gd.lt[a] - gd.lt[b]) < 2.0 * EDGE
            fun tri(a: Int, b: Int, c: Int) {
                if (!gd.keep[a] || !gd.keep[b] || !gd.keep[c]) return
                if (!ok(a, b) || !ok(b, c) || !ok(a, c)) return
                idx.add(vertex(a)); idx.add(vertex(b)); idx.add(vertex(c))
            }
            for (gi in 0 until m - 1) for (gj in 0 until m - 1) {
                val a = gi * m + gj; val b = a + 1; val c = a + m; val d = c + 1
                tri(a, c, b); tri(b, c, d)
            }
        }
        return PaintedMesh(pos.toArray(), cols.toArray(), idx.toArray())
    }

    private fun angle(a: Double, b: Double) = abs(((a - b + 540) % 360) - 180)

    fun srgbToLinear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    private class FloatList {
        private var a = FloatArray(1 shl 16); var size = 0; private set
        fun add(v: Float) { if (size == a.size) a = a.copyOf(a.size * 2); a[size++] = v }
        fun toArray() = a.copyOf(size)
    }

    private class IntList {
        private var a = IntArray(1 shl 16); var size = 0; private set
        fun add(v: Int) { if (size == a.size) a = a.copyOf(a.size * 2); a[size++] = v }
        fun toArray() = a.copyOf(size)
    }
}
