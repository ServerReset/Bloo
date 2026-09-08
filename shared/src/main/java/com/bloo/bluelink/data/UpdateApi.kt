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
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
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
 * The one canonical user-facing build label, so every surface (phone Settings,
 * watch About, update tile, widget) shows the version the same way — based on the
 * GitHub Actions run number baked in at CI build time (BuildConfig.BUILD_RUN_NUMBER).
 * A local/dev build has run number 0 (nothing to compare against) → "dev build".
 * When a non-blank [branch] is supplied and isn't the mainline, it's appended so a
 * side-branch build is distinguishable (e.g. "build 828 · feature-x").
 */
fun buildLabel(runNumber: Int, branch: String = ""): String {
    val base = if (runNumber <= 0) "dev build" else "build $runNumber"
    val b = branch.trim()
    // Suppress the "· branch" suffix for the mainline the app actually builds from
    // (DEFAULT_BRANCH), not just literal main/master — otherwise every ordinary CI
    // build shows its internal branch name as the user-facing version, and disagrees
    // with the update tile (which passes no branch).
    return if (b.isEmpty() || b == "main" || b == "master" || b == UpdateApi.DEFAULT_BRANCH) base else "$base · $b"
}

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

    /** The GitHub Releases page — a manual "second source" the user can open in a
     *  browser to grab an APK directly, independent of the in-app checker. */
    const val RELEASES_URL = "https://github.com/$OWNER/$REPO/releases"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** One file attached to a GitHub Release. [name] is matched against the
     *  known phone/watch asset filenames (see [PHONE_ASSET_NAME]/
     *  [WEAR_ASSET_NAME]) to pick out just the two APKs from whatever else a
     *  release might have attached. */
    @Serializable
    private data class ReleaseAsset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    )

    /** Minimal slice of GitHub's Release API response — only the fields this
     *  update flow actually reads out of what's normally a much larger
     *  payload (ignoreUnknownKeys drops everything else). */
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

    // android.yml's "Generate changelog" step writes a body of its own install
    // steps followed by a "### What's changed" marker, then a real per-commit
    // changelog (git log since the previous "build-N" tag) after that -- it
    // used to rely on GitHub's own generate_release_notes for that second
    // half instead, but that only lists merged PRs, and this repo pushes
    // straight to its branch with no PRs, so it was silently producing an
    // empty section (just a trailing compare-link line) on every build. The
    // update tile already shows its own dedicated install-steps card, so
    // showing the release body verbatim under "What's new" would duplicate
    // those same 3 steps together with a raw, unrendered "###" markdown
    // header -- only the changelog after the marker is what that section
    // actually needs.
    private const val CHANGELOG_MARKER = "### What's changed"

    private fun extractChangelog(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val marker = body.indexOf(CHANGELOG_MARKER)
        val notes = if (marker >= 0) body.substring(marker + CHANGELOG_MARKER.length) else body
        return notes
            .lineSequence()
            // Defensive against a release published before this format (or a
            // manually-edited one) that still carries GitHub's own generated
            // heading/compare-link artifacts.
            .dropWhile { it.isBlank() || it.trim() == "## What's Changed" }
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

/**
 * Hand an already-downloaded APK to the system package installer, returning whether the
 * installer actually launched.
 *
 * A content:// URI through FileProvider, never file://, which modern Android rejects
 * outright with FileUriExposedException. Both apps declare the same
 * `${applicationId}.fileprovider` authority and both expose their cache/apk directory to
 * it, so the one authority string works for either caller.
 *
 * attempt-and-report rather than check-then-act: there is no reliable way to know in
 * advance that an installer activity will resolve the intent, so this tries and says what
 * happened. Callers show their own message on false -- the download succeeded, so "we have
 * it but could not open the installer" is a different thing to say than "download failed".
 *
 * Shared because Bloo is sideloaded on BOTH surfaces -- neither app is on a store, so each
 * installs its own updates -- and the phone and watch copies of this were byte-identical
 * bar how each reached its Context. That is the same duplication UpdateGate and
 * UPDATE_SNOOZE_MS were pulled in here to end.
 */
fun installDownloadedApk(context: Context, apk: File): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}.isSuccess

/**
 * The side-effect-free decisions shared by the phone's UpdateChecker.checkPhone and the
 * watch's WearViewModel.runUpdateCheck. Only the pure predicates live here: each caller
 * keeps its own store reads/writes, its own debounce interval (phone 1 minute, watch
 * 12h), the watch-only "already found one this session" guard, its `setLastCheckedAt`
 * timing (phone stamps even on a failed fetch to debounce retries; the watch stamps only
 * on success), and its own result mapping (sealed UpdateCheckResult vs Boolean + UI
 * update). Those genuinely differ and MUST stay per-caller -- folding them in here would
 * change behaviour. What was duplicated, and drifting (the watch comment literally says
 * it "mirrors UpdateChecker.checkPhone"), is the gate arithmetic below.
 */
object UpdateGate {
    /**
     * Whether an update check should short-circuit without hitting the network:
     * an unstamped local build (nothing to compare against), or -- unless [force] --
     * a check within [minIntervalMs] of the last, or an active snooze window.
     */
    fun shouldSkipCheck(
        buildRunNumber: Int,
        force: Boolean,
        now: Long,
        lastCheckedAt: Long,
        snoozeUntil: Long,
        minIntervalMs: Long,
    ): Boolean {
        // Nothing to compare against: a build not stamped by CI has no run number.
        if (buildRunNumber <= 0) return true
        if (force) return false

        // Both windows below are bounded on BOTH sides. withinWindow carries the full
        // reasoning; in short, a bare `now - lastCheckedAt < minIntervalMs` is also true
        // for a stamp in the FUTURE, which a backwards clock correction produces
        // routinely -- and that suppressed every non-forced update check for the length
        // of the skew.
        if (withinWindow(now, lastCheckedAt, minIntervalMs)) return true

        // Same reasoning for the snooze, with the bound expressed as "no snooze anyone
        // can set reaches further out than this". Callers snooze for UPDATE_SNOOZE_MS
        // or less (the update tile's "Remind me" uses one day), so a stored deadline
        // beyond that maximum cannot have been produced by a real snooze on a sane
        // clock -- and honouring it would suppress updates for as long as the skew.
        if (now < snoozeUntil && snoozeUntil - now <= UPDATE_SNOOZE_MS) return true

        return false
    }

    /** This build's branch, falling back to [UpdateApi.DEFAULT_BRANCH] when unstamped. */
    fun resolveBranch(buildBranch: String): String = buildBranch.ifBlank { UpdateApi.DEFAULT_BRANCH }

    /** Whether [run] is strictly newer than the installed [buildRunNumber]. */
    fun isNewer(run: WorkflowRun, buildRunNumber: Int): Boolean = run.runNumber > buildRunNumber
}
