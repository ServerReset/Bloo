package com.bloo.bluelink.wear

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.wearable.Asset
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Turns a phone-local car photo into a Data Layer [Asset] the watch can read.
 *
 * The watch used to be sent the phone's file PATH for each car photo, which is
 * a string that can never resolve on another device -- so car photos have never
 * actually reached the watch. Assets are the Data Layer's binary channel: the
 * system moves the bytes out of band and gives the watch a file descriptor.
 */
object WearPhotoAssets {

    /** Longest edge, in px, of what gets sent. A round watch display is roughly
     *  450px across and the photo is shown smaller than that, so anything above
     *  this is bytes over Bluetooth that no one can see. */
    private const val MAX_EDGE = 480

    /** JPEG quality. Car photos are continuous-tone; 80 is where the artefacts
     *  stop being visible at this size and the file stops shrinking much. */
    private const val QUALITY = 80

    /**
     * Decodes, downscales and compresses [path], or null if there is nothing
     * usable there.
     *
     * Two-pass decode: the bounds-only pass reads the header to pick an integer
     * `inSampleSize`, so the full-resolution bitmap is never allocated. A phone
     * photo is tens of megabytes decoded, and this runs for every car on every
     * extras publish -- decoding them at full size first would be the kind of
     * allocation that shows up as a stutter with no obvious cause.
     */
    fun encode(path: String): Asset? = runCatching {
        val f = File(path)
        if (!f.isFile || f.length() == 0L) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null

        var sample = 1
        while (longest / (sample * 2) >= MAX_EDGE) sample *= 2

        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        // inSampleSize only halves, so the result can still be up to 2x over
        // budget; the exact scale finishes the job.
        val scale = MAX_EDGE.toFloat() / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else {
            bmp
        }
        val stream = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, QUALITY, stream)
        if (out !== bmp) out.recycle()
        bmp.recycle()
        Asset.createFromBytes(stream.toByteArray())
    }.getOrNull()
}
