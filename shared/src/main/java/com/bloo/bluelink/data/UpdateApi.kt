package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** A completed GitHub build of the app, normalised to what the update flow
 *  needs. [phoneApkUrl]/[wearApkUrl] are direct, public, unzipped asset
 *  download links (null only for a stale release published before this
 *  field existed, or if an asset failed to upload). */
data class WorkflowRun(
    val runNumber: Int,
    val htmlUrl: String,
    val displayTitle: String? = null,
    val phoneApkUrl: String? = null,
    val wearApkUrl: String? = null,
    /** The release's markdown body -- install steps + generated changelog
     *  (see android.yml's "Publish build" step), shown as this build's patch
     *  notes in the update tile. Null for a release with no body. */
    val releaseNotes: String? = null,
)

/**
 * Checks GitHub for the latest build. Bloo isn't on the Play Store, so this
 * is the app's real update channel: every ordinary push publishes a rolling
 * pre-release (see android.yml's "Publish build as a GitHub Release" step)
 * tagged "build-<run number>" with the raw phone/watch APKs attached as
 * public release assets — no auth and no zip needed to download them,
 * unlike the Actions artifacts from the same build (which this used to read
 * instead, before that was the actual reason the update flow needed a
 * browser + manual unzip). BuildConfig.BUILD_RUN_NUMBER (baked in at CI
 * build time) says which one is currently installed.
 */
object UpdateApi {

    private const val OWNER = "ServerReset"
    private const val REPO = "Bloo"
    private const val PHONE_ASSET_NAME = "Bloo.apk"
    private const val WEAR_ASSET_NAME = "Bloo-Wear.apk"

    /** The branch new builds land on — see UpdateChecker/WearViewModel. */
    const val DEFAULT_BRANCH = "claude/great-faraday-QuX3x"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class ReleaseAsset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    )

    @Serializable
    private data class ReleaseResponse(
        @SerialName("tag_name") val tagName: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        val name: String? = null,
        val body: String? = null,
        val draft: Boolean = false,
        val assets: List<ReleaseAsset> = emptyList(),
    )

    /** The latest published build release (from an ordinary push), or null
     *  on any failure. Release list is newest-first by creation date, and
     *  includes pre-releases (unlike the /releases/latest endpoint, which
     *  explicitly excludes them) — every build release is a pre-release.
     *
     *  [branch] is unused: GitHub's release list has no server-side branch
     *  filter (a release is tied to a tag, not a source branch), and in
     *  practice this repo only ever pushes to one branch at a time. Kept in
     *  the signature so callers (UpdateChecker, WearViewModel) don't need a
     *  matching change for what would be a no-op today. */
    suspend fun fetchLatestSuccessfulRun(branch: String): WorkflowRun? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/$OWNER/$REPO/releases"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("per_page", "5")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get()
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val releases = json.decodeFromString(ListSerializer(ReleaseResponse.serializer()), body)
                // "build-<N>" tags from the rolling per-push release; skips
                // drafts and anything from the separate tagged "vN" release
                // job, which doesn't follow this naming convention at all.
                val release = releases.firstOrNull { !it.draft && it.tagName.startsWith("build-") } ?: return@use null
                val runNumber = release.tagName.removePrefix("build-").toIntOrNull() ?: return@use null
                if (release.htmlUrl.isBlank()) return@use null
                WorkflowRun(
                    runNumber = runNumber,
                    htmlUrl = release.htmlUrl,
                    displayTitle = release.name,
                    phoneApkUrl = release.assets.firstOrNull { it.name == PHONE_ASSET_NAME }?.browserDownloadUrl,
                    wearApkUrl = release.assets.firstOrNull { it.name == WEAR_ASSET_NAME }?.browserDownloadUrl,
                    releaseNotes = extractChangelog(release.body),
                )
            }
        }.getOrNull()
    }

    // android.yml's release-publish step writes a body of its own install
    // steps followed by a "### What's changed" marker, then (with
    // generate_release_notes: true) GitHub appends its own auto-generated
    // "## What's Changed" changelog after that. The update tile already
    // shows its own dedicated install-steps card, so showing the release
    // body verbatim under "What's new" duplicated those same 3 steps
    // together with raw, unrendered "###"/"##" markdown headers -- only the
    // changelog after the marker (with GitHub's own redundant heading line
    // stripped too) is what that section actually needs.
    private const val CHANGELOG_MARKER = "### What's changed"

    private fun extractChangelog(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val marker = body.indexOf(CHANGELOG_MARKER)
        val notes = if (marker >= 0) body.substring(marker + CHANGELOG_MARKER.length) else body
        return notes
            .lineSequence()
            .dropWhile { it.isBlank() || it.trim() == "## What's Changed" }
            // Direct pushes (no PR) leave GitHub's auto-generated section with no
            // bullet entries at all -- just its own trailing "**Full Changelog**:
            // <compare-url>" line, which the tile's plain Text() renders as raw
            // "**...**" asterisks instead of bold. That link isn't useful patch-note
            // content on its own, so drop it; if nothing else is left, there's
            // nothing worth a "What's new" section for.
            .filterNot { it.trim().startsWith("**Full Changelog**") }
            .joinToString("\n")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    // A multi-MB download needs real headroom -- the 20s readTimeout on the
    // metadata client above is tuned for a small JSON response, not a whole
    // APK over a slow connection.
    private val downloadClient: OkHttpClient = client.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    /** Streams [url] to [destination], reporting 0-1 progress as bytes land
     *  (skipped/left at 0 if the server doesn't report Content-Length).
     *  Returns true only once the file is fully written. */
    suspend fun downloadApk(url: String, destination: File, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).get().build()
                downloadClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val responseBody = resp.body ?: return@use false
                    val total = responseBody.contentLength()
                    destination.parentFile?.mkdirs()
                    // Write to a temp file first -- a failed/cancelled download
                    // overwriting the previous good APK in place would leave a
                    // truncated, uninstallable file behind with no way to tell
                    // it apart from a real one.
                    val tmp = File(destination.parentFile, "${destination.name}.tmp")
                    responseBody.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var written = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                    tmp.renameTo(destination)
                }
            }.getOrDefault(false)
        }
}
