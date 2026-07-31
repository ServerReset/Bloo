package com.bloo.wear

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.io.File

/**
 * The single shared Coil [ImageLoader] for the watch app.
 *
 * Today the only thing it loads is the OpenStreetMap map thumbnail on the
 * Location card, and it is tuned tightly around that one job:
 *
 *  - **OSM User-Agent (the 403 fix).** OpenStreetMap's tile servers refuse
 *    clients that send a default or missing User-Agent — their usage policy —
 *    and hand back a "blocked" tile, which is what produced the broken/403 map.
 *    Sending a real, identifying UA for low-volume use is the documented fix, so
 *    every request through this loader carries one via an OkHttp interceptor.
 *
 *  - **Crossfade.** Tiles fade in when they finish loading rather than popping
 *    from the placeholder icon to the image. Applies to every request through
 *    this loader (only the map thumbnail uses it today).
 *
 *  - **Deliberately tiny caches.** This is a watch: memory and disk are scarce
 *    and we're only ever holding one small map tile at a time, so both caches
 *    are capped to a sliver of the device budget rather than Coil's generous
 *    defaults.
 *
 * The loader is built once, lazily, and shared process-wide.
 */
object WearImage {

    // Double-checked lazy singleton: one loader (and one OkHttp client + cache)
    // for the whole process, built on first use.
    @Volatile
    private var instance: ImageLoader? = null

    /** The shared image loader, built on first call and reused thereafter. */
    fun loader(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): ImageLoader {
        val cacheDir = File(context.cacheDir, "coil").apply { mkdirs() }

        // Every outbound request gets a real identifying User-Agent — the OSM
        // 403 fix. Without it the tile servers return a blocked tile.
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Bloo-WearOS/0.1 (https://claude.ai/code)")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .crossfade(true)
            // Watch-sized caches: we only ever hold a single map tile, so keep
            // both the memory and disk footprints to a sliver of the budget.
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.01)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir)
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }
}
