package com.bloo.bluelink.svm3d

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

private fun sessionOptions() = OrtSession.SessionOptions().apply {
    setIntraOpNumThreads(max(1, Runtime.getRuntime().availableProcessors() - 1))
    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
}

/** Depth Anything V2 (small): relative inverse depth from one picture. */
class OnnxDepth(file: File) : DepthModel, Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(file.absolutePath, sessionOptions())
    private val input = session.inputNames.first()

    override fun disparity(rgb: ByteArray, n: Int): FloatArray {
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f); val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        val buf = FloatBuffer.allocate(3 * n * n)
        for (ch in 0..2) for (k in 0 until n * n) {
            buf.put(((rgb[3 * k + ch].toInt() and 0xFF) / 255f - mean[ch]) / std[ch])
        }
        buf.rewind()
        OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, n.toLong(), n.toLong())).use { t ->
            session.run(mapOf(input to t)).use { out ->
                val v = (out[0] as OnnxTensor).floatBuffer
                return FloatArray(v.remaining()).also { v.get(it) }
            }
        }
    }

    override fun close() = session.close()
}

/** YOLO11n: finds cars, vans, trucks, buses and motorbikes. */
class OnnxVehicles(file: File) : VehicleDetector, Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(file.absolutePath, sessionOptions())
    private val input = session.inputNames.first()
    private val kinds = mapOf(2 to "car", 3 to "motorcycle", 5 to "bus", 7 to "truck")
    private val size = 640

    override fun detect(rgb: ByteArray, n: Int): List<Detection> {
        // Resize n x n to 640 x 640 (nearest is plenty for detection), CHW floats in 0..1.
        val buf = FloatBuffer.allocate(3 * size * size)
        for (ch in 0..2) for (y in 0 until size) {
            val sy = y * n / size
            for (x in 0 until size) {
                val sx = x * n / size
                buf.put((rgb[3 * (sy * n + sx) + ch].toInt() and 0xFF) / 255f)
            }
        }
        buf.rewind()
        val boxes = ArrayList<Detection>()
        OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, size.toLong(), size.toLong())).use { t ->
            session.run(mapOf(input to t)).use { out ->
                val o = (out[0] as OnnxTensor).floatBuffer
                val all = FloatArray(o.remaining()).also { o.get(it) }
                val anchors = all.size / 84
                val scale = n.toFloat() / size
                for (a in 0 until anchors) {
                    var best = -1; var conf = 0.25f
                    for ((cls, _) in kinds) {
                        val s = all[(4 + cls) * anchors + a]
                        if (s > conf) { conf = s; best = cls }
                    }
                    if (best < 0) continue
                    // Must also be the best class overall, not just best among vehicles.
                    var top = 0f
                    for (c in 0 until 80) top = max(top, all[(4 + c) * anchors + a])
                    if (top > conf + 1e-6f) continue
                    val cx = all[a]; val cy = all[anchors + a]; val w = all[2 * anchors + a]; val h = all[3 * anchors + a]
                    boxes += Detection((cx - w / 2) * scale, (cy - h / 2) * scale, (cx + w / 2) * scale, (cy + h / 2) * scale, conf, kinds.getValue(best))
                }
            }
        }
        // Non-maximum suppression.
        val kept = ArrayList<Detection>()
        for (d in boxes.sortedByDescending { it.confidence }) {
            if (kept.none { iou(it, d) > 0.5f }) kept += d
        }
        return kept
    }

    private fun iou(a: Detection, b: Detection): Float {
        val ix = max(0f, min(a.x1, b.x1) - max(a.x0, b.x0)); val iy = max(0f, min(a.y1, b.y1) - max(a.y0, b.y0))
        val inter = ix * iy
        val union = (a.x1 - a.x0) * (a.y1 - a.y0) + (b.x1 - b.x0) * (b.y1 - b.y0) - inter
        return if (union <= 0) 0f else inter / union
    }

    override fun close() = session.close()
}

/** LaMa (big-mask inpainting): paints in the masked part of a 512 x 512 picture. */
class OnnxInpaint(file: File) : Inpainter, Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(file.absolutePath, sessionOptions())

    override fun inpaint(rgb: ByteArray, hole: BooleanArray): ByteArray {
        val s = Inpainter.SIZE
        val img = FloatBuffer.allocate(3 * s * s)
        for (ch in 0..2) for (k in 0 until s * s) img.put((rgb[3 * k + ch].toInt() and 0xFF) / 255f)
        img.rewind()
        val mask = FloatBuffer.allocate(s * s)
        for (k in 0 until s * s) mask.put(if (hole[k]) 1f else 0f)
        mask.rewind()
        val out = ByteArray(3 * s * s)
        OnnxTensor.createTensor(env, img, longArrayOf(1, 3, s.toLong(), s.toLong())).use { ti ->
            OnnxTensor.createTensor(env, mask, longArrayOf(1, 1, s.toLong(), s.toLong())).use { tm ->
                session.run(mapOf("image" to ti, "mask" to tm)).use { res ->
                    val v = (res[0] as OnnxTensor).floatBuffer
                    val all = FloatArray(v.remaining()).also { v.get(it) }
                    // This export answers in 0..255; allow for one that answers in 0..1.
                    val gain = if (all.max() <= 1.5f) 255f else 1f
                    for (ch in 0..2) for (k in 0 until s * s) {
                        out[3 * k + ch] = (all[ch * s * s + k] * gain + 0.5f).toInt().coerceIn(0, 255).toByte()
                    }
                }
            }
        }
        return out
    }

    override fun close() = session.close()
}
