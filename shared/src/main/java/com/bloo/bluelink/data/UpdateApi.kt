package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One published asset on a GitHub release (an APK, here). */
data class ReleaseAsset(val name: String, val downloadUrl: String)

/** A GitHub release, normalised down to what the update flow needs. */
data class Release(
    val tagName: String,
    val name: String,
    val notes: String,
    val htmlUrl: String,
    val assets: List<ReleaseAsset>,
) {
    /** The tag is the release's versionCode ("7" or "v7") — see UpdateChecker. */
    val versionCode: Int? get() = tagName.removePrefix("v").toIntOrNull()

    fun asset(fileName: String): ReleaseAsset? = assets.firstOrNull { it.name == fileName }
}

/**
 * Fetches the latest published GitHub release for this repo. Bloo isn't on the
 * Play Store, so this is the app's own update channel — key-less, public REST
 * endpoint, no auth needed.
 */
object UpdateApi {

    private const val OWNER = "ServerReset"
    private const val REPO = "Bloo"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class ReleaseResponse(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        @SerialName("html_url") val htmlUrl: String = "",
        val assets: List<AssetResponse> = emptyList(),
    )

    @Serializable
    private data class AssetResponse(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    )

    /** Fetch the latest non-draft, non-prerelease release, or null on any failure
     *  (including "no releases exist yet" — a 404 from GitHub). */
    suspend fun fetchLatestRelease(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val parsed = json.decodeFromString(ReleaseResponse.serializer(), body)
                if (parsed.draft || parsed.prerelease || parsed.tagName.isBlank()) return@use null
                // Belt-and-suspenders: only ever hand off a github.com download URL,
                // regardless of what a compromised/malformed response might contain.
                val assets = parsed.assets
                    .filter { it.browserDownloadUrl.startsWith("https://github.com/$OWNER/$REPO/releases/download/") }
                    .map { ReleaseAsset(it.name, it.browserDownloadUrl) }
                Release(
                    tagName = parsed.tagName,
                    name = parsed.name.ifBlank { parsed.tagName },
                    notes = parsed.body,
                    htmlUrl = parsed.htmlUrl,
                    assets = assets,
                )
            }
        }.getOrNull()
    }
}
