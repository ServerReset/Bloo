package com.bloo.wear

import android.content.Context
import coil.ImageLoader
import okhttp3.OkHttpClient

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
            .build()
    }
}
