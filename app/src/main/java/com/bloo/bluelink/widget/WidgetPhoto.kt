package com.bloo.bluelink.widget

import android.content.Context
import android.graphics.Bitmap
import coil.imageLoader
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.toBitmap
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.imageUrl
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The car's own photo, softened into a backdrop for the widget. A widget cannot run a live blur, so the
 * photo is shrunk to a few dozen pixels and box-blurred once; the launcher's scaling then stretches that tiny
 * image smoothly across the widget, which reads as a deep, cheap blur. Tiny on purpose: a widget's images
 * travel to the launcher process with every update.
 */
internal object WidgetPhoto {
    /** Blur strengths offered in the settings: light, medium, strong. */
    const val BLUR_LIGHT = 0
    const val BLUR_MEDIUM = 1
    const val BLUR_STRONG = 2

    /** The blurred photo for [vin], or null when the car has no photo (or it cannot be read). */
    /** Whether the phone is in its dark theme: the widget's veil, and so the band baked into the photo, follow it. */
    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    suspend fun load(context: Context, vin: String, blur: Int, night: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val url = SettingsStore(context).imageUrl(vin)?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val source = if (url.startsWith("/")) decodeSmall(File(url)) else fetch(context, url)
            source?.let { soften(it, blur, night) }
        }.getOrNull()
    }

    private fun decodeSmall(file: File): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 256) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private suspend fun fetch(context: Context, url: String): Bitmap? {
        val request = coil.request.ImageRequest.Builder(context).data(url).size(256).allowHardware(false).build()
        // The app's own shared loader (BlooApplication): a fresh one per redraw built a whole new HTTP client and
        // cache every time the widget updated, and shared none of the photo cache the app has already filled.
        val result = context.applicationContext.imageLoader.execute(request)
        return (result as? coil.request.SuccessResult)?.drawable?.toBitmap()
    }

    private fun soften(src: Bitmap, blur: Int, night: Boolean): Bitmap {
        // Larger than before: at a few dozen pixels the launcher's stretch read as mush, and the photo was unrecognisable.
        val w = when (blur) {
            BLUR_LIGHT -> 240
            BLUR_STRONG -> 96
            else -> 160
        }
        val h = (src.height * w / src.width.coerceAtLeast(1)).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        val blurred = when (blur) {
            BLUR_LIGHT -> small
            BLUR_STRONG -> boxBlur(small, radius = 2, passes = 2)
            else -> boxBlur(small, radius = 1, passes = 1)
        }
        return withTextScrim(blurred, night)
    }

    /**
     * A soft band over the lower part of the photo, baked in once: the widget's words sit there, and this keeps them
     * readable without blurring the photo under them. The band is the colour of the widget's veil (white by day, black
     * by night), so it deepens the veil under the text instead of fighting it. A widget cannot re-blur live behind
     * text, so the band is static.
     */
    private fun withTextScrim(bmp: Bitmap, night: Boolean): Bitmap {
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, bmp.height * 0.35f, 0f, bmp.height.toFloat(),
                if (night) intArrayOf(0x00000000, 0x59000000) else intArrayOf(0x00FFFFFF, 0x59FFFFFF),
                null, android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), paint)
        return bmp
    }

    /** A separable box blur on a small bitmap, in place. */
    private fun boxBlur(bmp: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(passes) {
            blurLine(px, tmp, w, h, radius, horizontal = true)
            blurLine(tmp, px, w, h, radius, horizontal = false)
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
        return bmp
    }

    private fun blurLine(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val outer = if (horizontal) h else w
        val inner = if (horizontal) w else h
        for (o in 0 until outer) {
            for (i in 0 until inner) {
                var a = 0; var red = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, inner - 1)
                    val c = if (horizontal) src[o * w + j] else src[j * w + o]
                    a += c ushr 24; red += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                val idx = if (horizontal) o * w + i else i * w + o
                dst[idx] = ((a / n) shl 24) or ((red / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }
}
