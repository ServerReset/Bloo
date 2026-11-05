package com.bloo.bluelink.svm3d

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The AI models the 3D view needs, downloaded once (too big to ship in the app): Depth Anything V2
 * Small (Apache-2.0) and YOLO11n (AGPL-3.0) from their GitHub releases, and LaMa (Apache-2.0) from
 * OpenCV's model zoo on GitHub.
 */
class ModelStore(private val dir: File) {
    class Model(val file: String, val url: String, val approxBytes: Long)

    companion object {
        val DEPTH = Model(
            "depth_anything_v2_vits.onnx",
            "https://github.com/fabio-sim/Depth-Anything-ONNX/releases/download/v2.0.0/depth_anything_v2_vits.onnx",
            99_373_606,
        )
        val DETECTOR = Model(
            "yolo11n.onnx",
            "https://github.com/ultralytics/assets/releases/download/v8.3.0/yolo11n.onnx",
            10_930_182,
        )
        val INPAINT = Model(
            "inpainting_lama_2025jan.onnx",
            "https://media.githubusercontent.com/media/opencv/opencv_zoo/main/models/inpainting_lama/inpainting_lama_2025jan.onnx",
            92_591_623,
        )
        val ALL = listOf(DEPTH, DETECTOR, INPAINT)
        val TOTAL_BYTES = ALL.sumOf { it.approxBytes }
    }

    fun file(m: Model) = File(dir, m.file)

    fun has(m: Model) = file(m).length() > m.approxBytes / 2

    val ready: Boolean get() = ALL.all { has(it) }

    /** How much is still to download. */
    val missingBytes: Long get() = ALL.filterNot { has(it) }.sumOf { it.approxBytes }

    /** Downloads whatever is missing. [progress] gets 0..1 across what's missing. */
    fun download(progress: (Float) -> Unit) {
        dir.mkdirs()
        val client = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).followRedirects(true).build()
        val todo = missingBytes.coerceAtLeast(1)
        var done = 0L
        for (m in ALL) {
            val dest = file(m)
            if (has(m)) continue
            val tmp = File(dir, m.file + ".part")
            client.newCall(Request.Builder().url(m.url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Download failed (${resp.code}) for ${m.file}")
                val body = resp.body ?: throw IOException("Empty download for ${m.file}")
                tmp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(1 shl 16)
                        var got = 0L
                        while (true) {
                            val r = input.read(buf)
                            if (r < 0) break
                            out.write(buf, 0, r)
                            got += r
                            progress(((done + got).toFloat() / todo).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (!tmp.renameTo(dest)) throw IOException("Couldn't save ${m.file}")
            done += m.approxBytes
        }
        progress(1f)
    }
}
