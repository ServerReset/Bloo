package com.bloo.bluelink.svm3d

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot
import kotlin.math.min

/**
 * Writes the surroundings as a binary glTF, ready for Filament's glTF loader: the floor, the simple
 * traced walls (node "walls") and, if there is one, the painted surface (node "painted"); the viewer
 * shows one or the other. Vehicles aren't in it: the renderer places copies of the car model for those.
 *
 * glTF axes are Y-up; we map the car frame (X right, Y forward, Z up) to glTF (-X, Z, Y), which
 * matches the car model (+X = car's left, +Z = front).
 */
object SceneGlb {
    /** [backdropImage]: the [Backdrop] panorama as a JPEG, drawn on a dome (node "backdrop"). */
    fun write(scene: SurroundScene, groundImage: ByteArray, groundMime: String = "image/jpeg", backdropImage: ByteArray? = null): ByteArray {
        val bin = ByteArrayOutputStream()
        val views = StringBuilder()
        val accessors = StringBuilder()
        var nViews = 0; var nAcc = 0

        fun align() { while (bin.size() % 4 != 0) bin.write(0) }
        fun addView(bytes: ByteArray, target: Int?): Int {
            align()
            val off = bin.size()
            bin.write(bytes)
            if (nViews > 0) views.append(',')
            views.append("{\"buffer\":0,\"byteOffset\":$off,\"byteLength\":${bytes.size}")
            if (target != null) views.append(",\"target\":$target")
            views.append('}')
            return nViews++
        }
        fun floats(v: FloatArray) = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(v) }.array()
        fun ints(v: IntArray) = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asIntBuffer().put(v) }.array()
        fun addAccessor(view: Int, componentType: Int, count: Int, type: String, minMax: Pair<FloatArray, FloatArray>? = null): Int {
            if (nAcc > 0) accessors.append(',')
            accessors.append("{\"bufferView\":$view,\"componentType\":$componentType,\"count\":$count,\"type\":\"$type\"")
            if (minMax != null) {
                accessors.append(",\"min\":[${minMax.first.joinToString(",")}],\"max\":[${minMax.second.joinToString(",")}]")
            }
            accessors.append('}')
            return nAcc++
        }
        fun bounds(p: FloatArray): Pair<FloatArray, FloatArray> {
            val lo = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            val hi = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (i in p.indices) { val a = i % 3; lo[a] = min(lo[a], p[i]); hi[a] = maxOf(hi[a], p[i]) }
            return lo to hi
        }

        // Floor quad, a hair below zero so the car's own shadow sits on top.
        val e = scene.groundExtent.toFloat()
        val y0 = -0.005f
        // car-frame corners (-e,+e), (+e,+e), (+e,-e), (-e,-e) -> glTF (-X, Z, Y)
        val gp = floatArrayOf(e, y0, e, -e, y0, e, -e, y0, -e, e, y0, -e)
        val guv = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val gidx = intArrayOf(0, 2, 1, 0, 3, 2)
        val gpA = addAccessor(addView(floats(gp), 34962), 5126, 4, "VEC3", bounds(gp))
        val guvA = addAccessor(addView(floats(guv), 34962), 5126, 4, "VEC2")
        val gnA = addAccessor(addView(floats(floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f)), 34962), 5126, 4, "VEC3")
        val giA = addAccessor(addView(ints(gidx), 34963), 5125, 6, "SCALAR")
        val imgView = addView(groundImage, null)

        // Walls: one vertical quad per step along each run, coloured lightly by what was seen.
        val pos = ArrayList<Float>(); val nrm = ArrayList<Float>(); val col = ArrayList<Float>(); val idx = ArrayList<Int>()
        for (w in scene.walls) {
            val m = w.xs.size
            if (m < 2) continue
            val hs = DoubleArray(m) { i ->
                var s = 0.0
                for (o in -2..2) s += w.heights[(i + o).coerceIn(0, m - 1)]
                s / 5
            }
            val r = ((w.color shr 16) and 0xFF) / 255f; val g = ((w.color shr 8) and 0xFF) / 255f; val b = (w.color and 0xFF) / 255f
            fun tint(c: Float) = 0.78f * 0.65f + min(1f, c * 1.4f) * 0.35f
            for (i in 0 until m - 1) {
                val x0 = w.xs[i]; val yy0 = w.ys[i]; val x1 = w.xs[i + 1]; val yy1 = w.ys[i + 1]
                val len = hypot(x1 - x0, yy1 - yy0)
                if (len < 1e-3 || len > 1.5) continue
                // normal in car frame (perpendicular, horizontal), then to glTF
                val nx = -(yy1 - yy0) / len; val ny = (x1 - x0) / len
                val base = pos.size / 3
                val pts = listOf(
                    Triple(x0, yy0, 0.0), Triple(x0, yy0, hs[i]), Triple(x1, yy1, hs[i + 1]), Triple(x1, yy1, 0.0),
                )
                for ((x, y, z) in pts) {
                    pos += (-x).toFloat(); pos += z.toFloat(); pos += y.toFloat()
                    nrm += (-nx).toFloat(); nrm += 0f; nrm += ny.toFloat()
                    col += tint(r); col += tint(g); col += tint(b)
                }
                idx += listOf(base, base + 1, base + 2, base, base + 2, base + 3)
            }
        }
        val meshes = ArrayList<String>(); val nodes = ArrayList<String>()
        meshes += "{\"name\":\"floor\",\"primitives\":[{\"attributes\":{\"POSITION\":$gpA,\"NORMAL\":$gnA,\"TEXCOORD_0\":$guvA},\"indices\":$giA,\"material\":0}]}"
        nodes += "{\"mesh\":0,\"name\":\"floor\"}"
        if (idx.isNotEmpty()) {
            val p = pos.toFloatArray()
            val pA = addAccessor(addView(floats(p), 34962), 5126, p.size / 3, "VEC3", bounds(p))
            val nA = addAccessor(addView(floats(nrm.toFloatArray()), 34962), 5126, p.size / 3, "VEC3")
            val cA = addAccessor(addView(floats(col.toFloatArray()), 34962), 5126, p.size / 3, "VEC3")
            val iA = addAccessor(addView(ints(idx.toIntArray()), 34963), 5125, idx.size, "SCALAR")
            nodes += "{\"mesh\":${meshes.size},\"name\":\"walls\"}"
            meshes += "{\"name\":\"walls\",\"primitives\":[{\"attributes\":{\"POSITION\":$pA,\"NORMAL\":$nA,\"COLOR_0\":$cA},\"indices\":$iA,\"material\":1}]}"
        }
        val painted = scene.painted
        if (painted != null && painted.triangleCount > 0) {
            // Car frame -> glTF (-X, Z, Y).
            val src = painted.positions
            val p = FloatArray(src.size)
            for (v in 0 until src.size / 3) { p[3 * v] = -src[3 * v]; p[3 * v + 1] = src[3 * v + 2]; p[3 * v + 2] = src[3 * v + 1] }
            val pA = addAccessor(addView(floats(p), 34962), 5126, p.size / 3, "VEC3", bounds(p))
            val cA = addAccessor(addView(floats(painted.colors), 34962), 5126, p.size / 3, "VEC3")
            val iA = addAccessor(addView(ints(painted.indices), 34963), 5125, painted.indices.size, "SCALAR")
            nodes += "{\"mesh\":${meshes.size},\"name\":\"painted\"}"
            meshes += "{\"name\":\"painted\",\"primitives\":[{\"attributes\":{\"POSITION\":$pA,\"COLOR_0\":$cA},\"indices\":$iA,\"material\":2}]}"
        }
        var backdropView = -1
        if (backdropImage != null) {
            val (p, uv, ix) = dome()
            val pA = addAccessor(addView(floats(p), 34962), 5126, p.size / 3, "VEC3", bounds(p))
            val tA = addAccessor(addView(floats(uv), 34962), 5126, uv.size / 2, "VEC2")
            val iA = addAccessor(addView(ints(ix), 34963), 5125, ix.size, "SCALAR")
            backdropView = addView(backdropImage, null)
            nodes += "{\"mesh\":${meshes.size},\"name\":\"backdrop\"}"
            meshes += "{\"name\":\"backdrop\",\"primitives\":[{\"attributes\":{\"POSITION\":$pA,\"TEXCOORD_0\":$tA},\"indices\":$iA,\"material\":3}]}"
        }
        align()
        val sceneNodes = nodes.indices.joinToString(",", "[", "]")
        val images = StringBuilder("{\"bufferView\":$imgView,\"mimeType\":\"$groundMime\"}")
        val textures = StringBuilder("{\"sampler\":0,\"source\":0}")
        if (backdropView >= 0) {
            images.append(",{\"bufferView\":$backdropView,\"mimeType\":\"image/jpeg\"}")
            textures.append(",{\"sampler\":1,\"source\":1}")
        }
        val json = """
            {"asset":{"version":"2.0","generator":"Open Blue"},
             "extensionsUsed":["KHR_materials_unlit"],
             "scene":0,"scenes":[{"nodes":$sceneNodes}],
             "nodes":${nodes.joinToString(",", "[", "]")},
             "meshes":${meshes.joinToString(",", "[", "]")},
             "materials":[
               {"name":"floor","doubleSided":true,"extensions":{"KHR_materials_unlit":{}},
                "pbrMetallicRoughness":{"baseColorTexture":{"index":0},"metallicFactor":0,"roughnessFactor":1}},
               {"name":"wall","doubleSided":true,
                "pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":0.85}},
               {"name":"painted","doubleSided":true,"extensions":{"KHR_materials_unlit":{}},
                "pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1],"metallicFactor":0,"roughnessFactor":1}},
               {"name":"backdrop","extensions":{"KHR_materials_unlit":{}},
                "pbrMetallicRoughness":{"baseColorTexture":{"index":${if (backdropView >= 0) 1 else 0}},"metallicFactor":0,"roughnessFactor":1}}
             ],
             "textures":[$textures],
             "samplers":[{"magFilter":9729,"minFilter":9987,"wrapS":33071,"wrapT":33071},
                         {"magFilter":9729,"minFilter":9729,"wrapS":10497,"wrapT":33071}],
             "images":[$images],
             "accessors":[$accessors],
             "bufferViews":[$views],
             "buffers":[{"byteLength":${bin.size()}}]}
        """.trimIndent().replace("\n", "")
        val jsonBytes = json.toByteArray(Charsets.UTF_8).let { j ->
            val pad = (4 - j.size % 4) % 4
            j + ByteArray(pad) { ' '.code.toByte() }
        }
        val binBytes = bin.toByteArray()
        val total = 12 + 8 + jsonBytes.size + 8 + binBytes.size
        val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67); out.putInt(2); out.putInt(total)
        out.putInt(jsonBytes.size); out.putInt(0x4E4F534A); out.put(jsonBytes)
        out.putInt(binBytes.size); out.putInt(0x004E4942); out.put(binBytes)
        return out.array()
    }

    /**
     * The backdrop dome in glTF axes: a sphere of [Backdrop.RADIUS] round the eye point, from where
     * it meets the floor up to the top, facing inwards (so from outside only its far side shows).
     */
    internal fun dome(): Triple<FloatArray, FloatArray, IntArray> {
        val na = 96; val ne = 28
        val eBottom = Backdrop.DOME_BOTTOM_DEG; val eTop = 90.0
        val pos = FloatArray((na + 1) * (ne + 1) * 3); val uv = FloatArray((na + 1) * (ne + 1) * 2)
        for (j in 0..ne) {
            val eDeg = eBottom + (eTop - eBottom) * j / ne
            val e = Math.toRadians(eDeg)
            val v = ((Backdrop.rowOfElevation(eDeg) + 0.5) / Backdrop.HEIGHT).coerceIn(0.0, 1.0)
            for (i in 0..na) {
                val az = Math.toRadians(360.0 * i / na)
                val x = Backdrop.RADIUS * kotlin.math.sin(az) * kotlin.math.cos(e)
                val y = Backdrop.RADIUS * kotlin.math.cos(az) * kotlin.math.cos(e)
                val z = Backdrop.EYE_Z + Backdrop.RADIUS * kotlin.math.sin(e)
                val k = j * (na + 1) + i
                pos[3 * k] = (-x).toFloat(); pos[3 * k + 1] = z.toFloat(); pos[3 * k + 2] = y.toFloat()
                uv[2 * k] = (i.toDouble() / na).toFloat(); uv[2 * k + 1] = v.toFloat()
            }
        }
        val idx = IntArray(na * ne * 6)
        var q = 0
        for (j in 0 until ne) for (i in 0 until na) {
            val a = j * (na + 1) + i; val b = a + 1; val c = a + na + 1; val d = c + 1
            idx[q++] = a; idx[q++] = b; idx[q++] = c; idx[q++] = b; idx[q++] = d; idx[q++] = c
        }
        // Make the triangles face the centre (counter-clockwise seen from inside).
        val t = 3 * (ne / 2 * na)
        fun p(k: Int, c: Int) = pos[3 * idx[t + k] + c].toDouble()
        val ux = p(1, 0) - p(0, 0); val uy = p(1, 1) - p(0, 1); val uz = p(1, 2) - p(0, 2)
        val vx = p(2, 0) - p(0, 0); val vy = p(2, 1) - p(0, 1); val vz = p(2, 2) - p(0, 2)
        val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
        val inward = nx * -p(0, 0) + ny * (Backdrop.EYE_Z - p(0, 1)) + nz * -p(0, 2)
        if (inward < 0) for (k in idx.indices step 3) { val s = idx[k + 1]; idx[k + 1] = idx[k + 2]; idx[k + 2] = s }
        return Triple(pos, uv, idx)
    }
}
