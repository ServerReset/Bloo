package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A completed GitHub Actions build of the app, normalised to what the
 *  update flow needs. */
data class WorkflowRun(val runNumber: Int, val htmlUrl: String)

/**
 * Checks GitHub Actions for the latest successful build. Bloo isn't on the
 * Play Store and doesn't reliably cut tagged Releases (that job in
 * android.yml only fires on a manual "vN" tag push), so this is the app's
 * real update channel: every ordinary push already builds and uploads APK
 * artifacts, and BuildConfig.BUILD_RUN_NUMBER (baked in at CI build time)
 * says which one is currently installed. Key-less, public REST endpoint, no
 * auth needed to read run metadata (only artifact *downloads* need a token,
 * which is why the update prompt opens the run's page in a browser instead
 * of fetching the APK itself).
 */
object UpdateApi {

    private const val OWNER = "ServerReset"
    private const val REPO = "Bloo"
    private const val WORKFLOW_FILE = "android.yml"

    /** The branch new builds land on — see UpdateChecker/WearViewModel. */
    const val DEFAULT_BRANCH = "claude/great-faraday-QuX3x"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class WorkflowRunsResponse(
        @SerialName("workflow_runs") val workflowRuns: List<WorkflowRunResponse> = emptyList(),
    )

    @Serializable
    private data class WorkflowRunResponse(
        @SerialName("run_number") val runNumber: Int = 0,
        @SerialName("html_url") val htmlUrl: String = "",
    )

    /** The latest successful build (from an ordinary push, not a PR) on
     *  [branch], or null on any failure. */
    suspend fun fetchLatestSuccessfulRun(branch: String): WorkflowRun? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$WORKFLOW_FILE/runs"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("branch", branch)
                .addQueryParameter("status", "success")
                .addQueryParameter("event", "push")
                .addQueryParameter("per_page", "1")
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
                val parsed = json.decodeFromString(WorkflowRunsResponse.serializer(), body)
                val run = parsed.workflowRuns.firstOrNull() ?: return@use null
                if (run.runNumber <= 0 || run.htmlUrl.isBlank()) return@use null
                WorkflowRun(runNumber = run.runNumber, htmlUrl = run.htmlUrl)
            }
        }.getOrNull()
    }
}
