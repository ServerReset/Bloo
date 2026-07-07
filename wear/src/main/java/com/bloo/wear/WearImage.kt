package com.bloo.wear

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.io.File

/**
 * A Coil image loader that sends a real, identifying User-Agent. OpenStreetMap's
 * tile servers return a "blocked" tile to clients with a default/missing UA
 * (their usage policy), which is what produced the 403 map. A proper UA for
 * low-volume use is the documented fix.
 */
object WearImage {

    @Volatile
    private var instance: ImageLoader? = null

    fun loader(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): ImageLoader {
        val cacheDir = File(context.cacheDir, "coil").also { it.mkdirs() }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Bloo-WearOS/0.1 (https://claude.ai/code)")
                        .build()
                )
            }
            .build()
        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.01)
                    .build()
            }
            .diskCache {
                DiskCache.Builder(context)
                    .directory(cacheDir)
                    .maxSizePercent(0.02)
                    .build()
            }
            .build()
    }
}
