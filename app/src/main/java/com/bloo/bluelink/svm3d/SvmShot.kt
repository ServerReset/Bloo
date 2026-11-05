package com.bloo.bluelink.svm3d

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One surround-view set, as the 3D scene reads it: when it was taken, its layout, and its image bytes. */
class SvmShot(val utcTime: String?, val imageSize: List<Int>, private val read: () -> ByteArray) {
    val frameWidth: Int? get() = imageSize.getOrNull(2)
    val frameHeight: Int? get() = imageSize.getOrNull(3)
    val topWidth: Int? get() = imageSize.getOrNull(4)
    val topHeight: Int? get() = imageSize.getOrNull(5)

    fun bytes(): ByteArray = read()

    /** When the car took it, or null if Bluelink didn't say. */
    val takenAt: Date?
        get() = utcTime?.let {
            runCatching { SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(it) }.getOrNull()
        }

    /** The views, top first: ("Top", bitmap), ("Front", …), ("Rear", …), ("Left", …), ("Right", …). */
    fun views(): List<Pair<String, Bitmap>> {
        val data = bytes()
        val full = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return emptyList()
        val w = frameWidth
        val h = frameHeight
        if (w == null || h == null || w <= 0) return listOf("Photo" to full)
        fun crop(x: Int, width: Int, height: Int): Bitmap? {
            if (x >= full.width) return null
            val cw = minOf(width, full.width - x)
            val ch = minOf(height, full.height)
            return if (cw > 0 && ch > 0) Bitmap.createBitmap(full, x, 0, cw, ch) else null
        }
        val out = mutableListOf<Pair<String, Bitmap>>()
        val tw = topWidth
        val th = topHeight
        if (tw != null && th != null) crop(4 * w, tw, th)?.let { out += "Top" to it }
        listOf("Front", "Rear", "Left", "Right").forEachIndexed { i, name -> crop(i * w, w, h)?.let { out += name to it } }
        return out
    }

    /** A small picture of the top view (or the whole strip), for lists. */
    fun thumbnail(maxSide: Int = 240): Bitmap? = runCatching {
        val data = bytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val w = frameWidth
        val tw = topWidth
        val th = topHeight
        val region = if (w != null && tw != null && th != null && 4 * w + tw <= bounds.outWidth) {
            android.graphics.Rect(4 * w, 0, 4 * w + tw, minOf(th, bounds.outHeight))
        } else {
            android.graphics.Rect(0, 0, bounds.outWidth, bounds.outHeight)
        }
        var sample = 1
        while (maxOf(region.width(), region.height()) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        @Suppress("DEPRECATION")
        val decoder = if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(data, 0, data.size) else BitmapRegionDecoder.newInstance(data, 0, data.size, false)
        decoder?.decodeRegion(region, opts).also { decoder?.recycle() }
    }.getOrNull()

    companion object {
        fun of(c: com.bloo.bluelink.data.SurroundCapture) = SvmShot(c.utcTime, c.imageSize) { c.jpeg }
    }
}
