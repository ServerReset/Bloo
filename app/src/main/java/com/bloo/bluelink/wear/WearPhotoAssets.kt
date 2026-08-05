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
     * Cache of the last encode per CAR, keyed on VIN rather than path.
     *
     * publishExtrasNow runs on every extras change -- a weather refresh, an AI
     * summary landing, a status poll -- and each one used to re-decode,
     * re-scale and re-compress every car's photo from scratch. That is real
     * work (a multi-megapixel decode per car) repeated for a result that is
     * bit-identical until the user actually picks a new photo, which is
     * approximately never.
     *
     * Keyed on VIN, not on the path, and this is the part that matters: the
     * photo picker (CropScreen, in the main UI file) writes every new photo to a
     * FRESH timestamped file -- car_$vin_$timestamp.jpg -- rather than
     * overwriting the old one. A cache keyed on path took that as a brand new
     * key on every photo change and never dropped the old one, so the
     * compressed bytes of every photo a car had ever had stayed reachable for
     * the life of the process. Keying on VIN means a new photo naturally
     * replaces the old cache entry instead of joining it. The stamp (path +
     * lastModified + length) is still what decides whether the cached bytes
     * are still current, so a genuinely-changed photo at the same VIN still
     * re-encodes.
     */
    private val cache = HashMap<String, PhotoEntry>()

    private data class PhotoEntry(val stamp: String, val asset: Asset)

    private fun stampFor(path: String, f: File) = "$path:${f.lastModified()}:${f.length()}"

    /**
     * Decodes, downscales and compresses [vin]'s photo at [path], or null if
     * there is nothing usable there or nothing has changed since the last
     * call (see the cache doc above).
     *
     * Two-pass decode: the bounds-only pass reads the header to pick an integer
     * `inSampleSize`, so the full-resolution bitmap is never allocated. A phone
     * photo is tens of megabytes decoded, and this runs for every car on every
     * extras publish -- decoding them at full size first would be the kind of
     * allocation that shows up as a stutter with no obvious cause.
     */
    fun encode(vin: String, path: String): Asset? = runCatching {
        val f = File(path)
        if (!f.isFile || f.length() == 0L) return null

        val stamp = stampFor(path, f)
        synchronized(cache) { cache[vin]?.takeIf { it.stamp == stamp }?.asset }?.let { return it }

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
        val asset = Asset.createFromBytes(stream.toByteArray())
        // Stamped AFTER the work, with the value read before it: if the file
        // changed while this was decoding, the stamp no longer matches and the
        // next call redoes it rather than caching a photo that is already
        // stale. Storing under `vin` is what makes this a REPLACE rather than
        // an addition -- see the class doc.
        synchronized(cache) { cache[vin] = PhotoEntry(stamp, asset) }
        asset
    }.getOrNull()

    /** Drops cached encodes for cars that are no longer paired, mirroring
     *  [com.bloo.wear.WearPhotoCache.prune] on the watch side -- otherwise a
     *  removed car's LAST photo stays cached (harmlessly small, but pointless)
     *  for the life of the process. */
    fun prune(keep: Collection<String>) {
        synchronized(cache) { cache.keys.retainAll(keep.toSet()) }
    }
}
