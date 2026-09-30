package com.bloo.bluelink.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.graphics.scale
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import coil.imageLoader
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Per-car photos as they travel inside a settings backup: encode local files to downscaled base64
 * JPEGs on the way out, write them back to `filesDir/cars/` on the way in. Split out of
 * [SettingsStore], which only supplies the preferences and calls these two entry points.
 */
internal object SyncPhotos {

    /** Longest edge a synced photo is downscaled to before base64-embedding --
     *  small enough that even several cars' photos keep the whole settings
     *  backup a reasonable size for repeated auto-sync uploads, still sharp
     *  enough for the hero card / cover-screen tile it's actually shown at. */
    private const val SYNCED_PHOTO_MAX_DIM = 640

    /** Reads every `img_$vin` pref that points at a local file (a remote URL
     *  needs no embedding -- it already loads the same way on any device) and
     *  returns `{vin: base64 JPEG}` for [exportSettingsJson]'s "photos" field.
     *  Downscales to [SYNCED_PHOTO_MAX_DIM] first; a corrupt/missing file for
     *  one car is skipped rather than failing the whole export.
     *
     *  Memoized on the file's identity (see [syncPhotoCache]) because this is not the
     *  once-per-manual-export call it looks like: it runs on every Drive sync pass, and
     *  auto-push fires one of those ~2s after ANY tracked pref edit. Dragging pebbles or
     *  nudging a slider therefore paid a full decode + rescale + JPEG re-compress +
     *  base64 for every car, to produce bytes identical to last time. */
    fun encode(prefs: androidx.datastore.preferences.core.Preferences): Map<String, JsonPrimitive> =
        prefs.asMap().keys.mapNotNull { key ->
            if (!key.name.startsWith("img_")) return@mapNotNull null
            val vin = key.name.removePrefix("img_")
            val path = prefs[stringPreferencesKey(key.name)]?.takeIf { it.startsWith("/") } ?: return@mapNotNull null
            // Path plus mtime plus length. The crop screen and applySyncPhotos both write
            // each car to a FIXED per-vin filename (deliberately, so repeated syncs
            // overwrite in place instead of accumulating orphans), so the path alone cannot
            // tell a new photo from the old one -- mtime and length are what move.
            val file = java.io.File(path)
            val stamp = "$path:${file.lastModified()}:${file.length()}"
            syncPhotoCache[vin]?.let { (cachedStamp, cachedB64) ->
                if (cachedStamp == stamp) return@mapNotNull vin to JsonPrimitive(cachedB64)
            }
            val bytes = runCatching { downscaledJpegBytes(path) }.getOrNull() ?: return@mapNotNull null
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            // Bounded by garage size in normal use (one entry per vin, a changed photo
            // replaces its own entry). The clear() is a backstop for a pathological garage,
            // and costs only a re-encode.
            if (syncPhotoCache.size >= MAX_CACHED_SYNC_PHOTOS) syncPhotoCache.clear()
            syncPhotoCache[vin] = stamp to b64
            vin to JsonPrimitive(b64)
        }.toMap()

    /** Downscale-decodes [path] to at most [SYNCED_PHOTO_MAX_DIM] on its longest
     *  edge and re-encodes as a JPEG, without ever fully decoding the original
     *  at full resolution (bounds-only pass picks an `inSampleSize` first). */
    private fun downscaledJpegBytes(path: String): ByteArray? {
        val file = java.io.File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > SYNCED_PHOTO_MAX_DIM * 2) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        try {
            val scale = SYNCED_PHOTO_MAX_DIM.toFloat() / maxOf(decoded.width, decoded.height)
            val resized = if (scale < 1f) {
                decoded.scale((decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1))
            } else decoded
            val out = java.io.ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 78, out)
            return out.toByteArray()
        } finally {
            decoded.recycle()
        }
    }

    /** Writes any embedded per-car photos from a backup's "photos" object to
     *  local storage (same `filesDir/cars/` directory the crop screen itself
     *  saves to), skipping any vin whose `img_$vin` key is in [protect] --
     *  an automatic Drive merge must not clobber a photo changed locally
     *  since the last successful sync, same reasoning as the plain pref
     *  merge in [mergeSettingsJson]. A fixed per-vin filename (not a fresh
     *  timestamped one) so repeated syncs overwrite in place rather than
     *  accumulating orphaned old photos on disk. Returns the vin -> new
     *  local path map for the caller to fold into whichever edit block
     *  (tracked or not) it's already running, so this lands in the SAME
     *  transaction as the rest of that import/merge instead of a separate one. */
    // @OptIn: Coil's diskCache accessor is still @ExperimentalCoilApi. The call is a
    // deliberate blanket clear() (see the comment at its call site), not an API we can
    // avoid -- the annotation records that we knowingly depend on the experimental
    // surface rather than suppressing it file-wide.
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun apply(context: Context, photos: JsonObject?, protect: Set<String> = emptySet()): Map<String, String> {
        if (photos == null) return emptyMap()
        val dir = java.io.File(context.filesDir, "cars").apply { mkdirs() }
        val result = photos.mapNotNull { (vin, element) ->
            if ("img_$vin" in protect) return@mapNotNull null
            val b64 = (element as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            runCatching {
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                val file = java.io.File(dir, "car_${vin}_synced.jpg")
                file.writeBytes(bytes)
                vin to file.absolutePath
            }.getOrNull()
        }.toMap()
        // Every synced photo lands at the SAME fixed path across imports (deliberately -- see
        // this function's own doc on why a fixed name beats a fresh timestamped one), which is
        // exactly the shape Coil's default File-model cache key can't tell apart: a second
        // import that overwrites car_$vin_synced.jpg with genuinely different bytes still hits
        // whatever Coil already decoded and cached for that identical path, in-process, without
        // ever reading the new file. A hand-rolled BitmapFactory decode would hit the same
        // "same path, new content" gap -- this app's actual image
        // loads go through Coil (rememberPhotoModel in Hero.kt) instead of a manual
        // BitmapFactory decode, so the fix here is Coil's own cache, not a produceState key.
        // A blanket clear() rather than targeting just these VINs' keys: constructing Coil's
        // exact internal MemoryCache.Key for a File source is an implementation detail that can
        // change between versions, while every OTHER cached image (weather icons, brand logos)
        // costs nothing to redecode once, on the rare event an import actually runs.
        if (result.isNotEmpty()) {
            runCatching {
                val loader = context.imageLoader
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
        }
        return result
    }
}

/**
 * `vin -> (file stamp, base64 JPEG)` memo for [SyncPhotos.encode].
 *
 * Top-level rather than a field, because [SettingsStore] is CONSTRUCTED AD HOC at a dozen
 * call sites (`SettingsStore(context).appearance.first()` and friends) -- an instance field
 * would be a fresh empty map on most of those calls and would cache nothing. This is a pure
 * memo of a deterministic function of a file's bytes, so process scope is the correct scope,
 * and there is nothing to invalidate on sign-out or car removal: the stamp does that.
 *
 * ConcurrentHashMap because sync runs off the main thread and two passes can overlap. A torn
 * read here would at worst re-encode; a ConcurrentModificationException would fail a sync.
 */
private val syncPhotoCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

/** Entry cap for [syncPhotoCache]. A 640px quality-78 JPEG base64s to roughly 60-110 KB, so
 *  this bounds the memo near a megabyte for a garage far larger than any real one. */
private const val MAX_CACHED_SYNC_PHOTOS = 12
