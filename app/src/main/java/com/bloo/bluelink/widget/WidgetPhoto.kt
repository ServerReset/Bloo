package com.bloo.bluelink.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decodes and blurs a car's photo for use as a widget's full-bleed background
 * (the "Photo background" option in [WidgetConfigActivity]).
 *
 * Glance/RemoteViews has no live blur primitive, but a real blur can still be
 * baked into the bitmap once, off the render path -- both steps are memoised
 * so a widget that isn't changing photos doesn't redo this work on every
 * refresh tick.
 */
object WidgetPhoto {

    // Sized by bytes so this can never pin more than a few MB across every
    // placed widget instance sharing the process.
    private val cache = object : android.util.LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Decode a file-backed bitmap downsampled so its longest edge is <= [maxPx],
     *  memoised by path + last-modified. Full-size photos handed to RemoteViews
     *  throw 'exceeds maximum bitmap memory usage' and blank the widget, so
     *  this always scales down first. */
    fun decodeCached(path: String, maxPx: Int = 480): Bitmap? {
        val file = java.io.File(path)
        if (!file.exists()) return null
        val key = "$path:${file.lastModified()}:$maxPx"
        cache.get(key)?.let { return it }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest > 0 && longest / sample > maxPx) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()?.also { cache.put(key, it) }
    }

    /**
     * A soft, real blur of [source] (the already-downsampled photo bitmap),
     * memoised by [path]'s identity so it's computed once per photo change,
     * not on every widget refresh. A fixed *pixel-radius* box blur (two
     * passes, which approximates a Gaussian closely) operating directly on
     * the bitmap's own pixel data -- radius scales with image size but is
     * clamped to a range tuned by eye so a ~480px source lands softened
     * without turning into mush or leaving the source photo's own JPEG
     * block edges showing through.
     */
    fun blurredCached(source: Bitmap, path: String): Bitmap {
        val file = java.io.File(path)
        val key = "blur:$path:${file.lastModified()}"
        cache.get(key)?.let { return it }
        return runCatching {
            val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
            val radius = (maxOf(mutable.width, mutable.height) / 70).coerceIn(2, 6)
            repeat(2) { boxBlurInPlace(mutable, radius) }
            mutable
        }.getOrDefault(source).also { cache.put(key, it) }
    }

    /** One box-blur pass (horizontal then vertical, each an O(w*h) sliding
     *  average via per-row/per-column prefix sums) mutating [bmp] in place. */
    private fun boxBlurInPlace(bmp: Bitmap, radius: Int) {
        if (radius < 1) return
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val horizontal = IntArray(w * h)
        boxBlurPass(pixels, horizontal, w, h, radius, alongRows = true)
        boxBlurPass(horizontal, pixels, w, h, radius, alongRows = false)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /** One directional box-blur pass. [alongRows] = true blurs each row
     *  horizontally; false blurs each column vertically. Edge pixels use a
     *  shrinking window (the average of however many real neighbours exist
     *  near an edge) rather than a wrapped/clamped one, avoiding darkening
     *  or lightening the border. */
    private fun boxBlurPass(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, alongRows: Boolean) {
        val outer = if (alongRows) h else w
        val inner = if (alongRows) w else h
        val prefA = IntArray(inner + 1)
        val prefR = IntArray(inner + 1)
        val prefG = IntArray(inner + 1)
        val prefB = IntArray(inner + 1)
        for (o in 0 until outer) {
            for (i in 0 until inner) {
                val idx = if (alongRows) o * w + i else i * w + o
                val p = src[idx]
                prefA[i + 1] = prefA[i] + ((p ushr 24) and 0xFF)
                prefR[i + 1] = prefR[i] + ((p ushr 16) and 0xFF)
                prefG[i + 1] = prefG[i] + ((p ushr 8) and 0xFF)
                prefB[i + 1] = prefB[i] + (p and 0xFF)
            }
            for (i in 0 until inner) {
                val start = (i - radius).coerceAtLeast(0)
                val end = (i + radius).coerceAtMost(inner - 1)
                val count = end - start + 1
                val a = (prefA[end + 1] - prefA[start]) / count
                val r = (prefR[end + 1] - prefR[start]) / count
                val g = (prefG[end + 1] - prefG[start]) / count
                val b = (prefB[end + 1] - prefB[start]) / count
                val idx = if (alongRows) o * w + i else i * w + o
                dst[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }
}
