package com.bloo.wear

import android.content.Context
import com.bloo.bluelink.data.WearSync
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The watch's local copy of the car photos the phone sends as Data Layer
 * Assets.
 *
 * An Asset is a handle, not bytes: the system transfers the payload out of band
 * and only hands over a file descriptor when asked. That descriptor is a
 * one-shot stream on a background thread, so the bytes are pulled once here and
 * written to the watch's own cache, and the UI is given a plain file path it
 * can read as often as it likes.
 *
 * Before this, the phone published its own file PATHS in the extras JSON --
 * strings that cannot resolve on a different device with a different
 * filesystem. Car photos therefore never reached the watch at all, and nothing
 * on the watch even read the field.
 */
object WearPhotoCache {

    /** How long to wait for one photo's bytes. Generous, because the transfer
     *  is over Bluetooth and the phone may be busy, but finite. */
    private const val ASSET_TIMEOUT_SECONDS = 20L

    private fun dir(context: Context): File =
        File(context.filesDir, "car_photos").also { it.mkdirs() }

    /** Where [vin]'s photo lives on THIS device, or null if none has arrived. */
    fun pathFor(context: Context, vin: String): String? =
        dir(context).resolve("$vin.jpg").takeIf { it.isFile && it.length() > 0 }?.path

    /**
     * Pulls every photo Asset out of one extras DataMap and writes it to the
     * cache. Returns the VINs whose file actually changed, so a caller can
     * avoid waking the UI when the phone re-published unchanged photos --
     * which it does on every extras publish, i.e. often.
     *
     * Failures are per-car and swallowed: one unreadable asset must not cost
     * the others, and a photo is never worth failing a sync over.
     */
    suspend fun ingest(context: Context, vins: Collection<String>, map: DataMap): List<String> =
        withContext(Dispatchers.IO) {
            val changed = mutableListOf<String>()
            for (vin in vins) {
                val asset: Asset = map.getAsset(WearSync.assetKeyFor(vin)) ?: continue
                runCatching {
                    // Bounded, like every other Data Layer call in this app
                    // (WearBridge.putItem uses the same shape). An asset fetch
                    // waits on a transfer from the phone over Bluetooth: if the
                    // phone walks out of range mid-pull, an unbounded await
                    // parks an IO thread until it comes back. A photo is the
                    // least important thing here and must not be the thing that
                    // holds a thread.
                    val fd = Wearable.getDataClient(context)
                        .getFdForAsset(asset)
                        .let {
                            com.google.android.gms.tasks.Tasks.await(
                                it, ASSET_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS,
                            )
                        }
                    val bytes = fd.inputStream.use { it.readBytes() }
                    if (bytes.isEmpty()) return@runCatching
                    val target = dir(context).resolve("$vin.jpg")
                    // Compare before writing: the file is what the UI keys off,
                    // so rewriting identical bytes would invalidate an image
                    // that did not change.
                    val existing = if (target.isFile) target.readBytes() else null
                    if (existing == null || !existing.contentEquals(bytes)) {
                        target.writeBytes(bytes)
                        changed += vin
                    }
                }
            }
            changed
        }

    /** Drops photos for cars that are no longer paired, so removing a car from
     *  the phone eventually reclaims the space here too. */
    fun prune(context: Context, keep: Collection<String>) {
        runCatching {
            dir(context).listFiles()?.forEach { f ->
                if (f.nameWithoutExtension !in keep) f.delete()
            }
        }
    }
}
