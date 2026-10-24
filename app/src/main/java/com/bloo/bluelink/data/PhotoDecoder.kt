package com.bloo.bluelink.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.awxkee.jxlcoder.JxlCoder
import com.radzivon.bartoshyk.avif.coder.Coder as AvifCoder

/**
 * Decodes the photos the app accepts: JPEG, PNG and WebP (the platform reads these), AVIF and JPEG XL (the platform does
 * not read them everywhere this app runs, so two decoders do). Large photos come back scaled down to [maxSide]. Returns
 * null when no decoder can read the bytes.
 */
internal object PhotoDecoder {
    fun decode(bytes: ByteArray, maxSide: Int): Bitmap? {
        val sample = sampleFor(bytes, maxSide)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.let { return it }
        // The platform could not read it: AVIF, then JPEG XL. Each throws on bytes it does not understand, so a miss is simply null.
        val full = runCatching { AvifCoder().decode(bytes) }.getOrNull()
            ?: runCatching { JxlCoder.decode(bytes) }.getOrNull()
            ?: return null
        return scaleDown(full, maxSide)
    }

    private fun sampleFor(bytes: ByteArray, maxSide: Int): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        return sample
    }

    private fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val largest = maxOf(bmp.width, bmp.height)
        if (largest <= maxSide) return bmp
        val k = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * k).toInt().coerceAtLeast(1),
            (bmp.height * k).toInt().coerceAtLeast(1),
            true,
        )
    }
}
