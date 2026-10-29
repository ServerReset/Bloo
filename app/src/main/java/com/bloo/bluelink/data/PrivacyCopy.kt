package com.bloo.bluelink.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The privacy policy in its three places: the plain HTML page and the styled web page on the project site, and the plain copy the app
 * keeps for reading inside the app.
 *
 * The app reads the plain copy from its own storage, which it refreshes from the site whenever it can reach it. The first copy comes
 * from the one bundled with the app, so the policy reads offline from the first launch.
 */
object PrivacyCopy {
    private const val SITE = "https://serverreset.github.io/Bloo/"

    /** The plain HTML page: the same policy, no styling. */
    const val PLAIN_URL = SITE + "privacy-plain.html"

    /** The styled web page, with the contents list. */
    const val STYLED_URL = SITE + "privacy.html"

    /** The file name of the plain copy, both in the app's storage and in the bundled assets. */
    private const val FILE = "privacy-plain.html"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    /**
     * The copy to show now: the last one pulled from the site, else the one bundled with the app. Never waits on the network.
     */
    fun local(context: Context): String =
        runCatching { File(context.filesDir, FILE).readText() }
            .getOrElse { runCatching { context.assets.open(FILE).bufferedReader().use { it.readText() } }.getOrDefault("") }

    /**
     * Pulls the plain copy from the site and keeps it for offline reading. Returns the new text, or null when the site could not be
     * reached or did not return the policy. A bad answer never replaces a good copy.
     */
    suspend fun pull(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(PLAIN_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body.string()
                if (!text.contains("<title>") || !text.contains("privacy policy", ignoreCase = true)) return@use null
                File(context.filesDir, FILE).writeText(text)
                text
            }
        }.getOrNull()
    }
}
