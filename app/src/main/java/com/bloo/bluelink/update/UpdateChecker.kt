package com.bloo.bluelink.update

import android.content.Context
import com.bloo.bluelink.BuildConfig
import com.bloo.bluelink.data.UpdateApi
import com.bloo.bluelink.data.UpdateStore
import com.bloo.bluelink.data.WorkflowRun
import kotlinx.coroutines.flow.first

/** A newer CI build than what's installed. */
data class UpdateInfo(val run: WorkflowRun)

/** Result of an update check attempt. */
sealed class UpdateCheckResult {
    /** A newer build was found. */
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    /** No newer build found (current is latest). */
    data object UpToDate : UpdateCheckResult()
    /** The check failed (network/API error). */
    data class Failed(val error: String?) : UpdateCheckResult()
}

/**
 * Orchestrates the update-check flow. Bloo isn't on the Play Store and
 * doesn't reliably cut tagged GitHub Releases (see .github/workflows/
 * android.yml - that job only runs on a "vN" tag push, which in practice
 * never happens), so "is there a newer version" means "has a newer CI build
 * landed on the default branch," compared by the Actions run number baked
 * into BuildConfig.BUILD_RUN_NUMBER at CI build time.
 */
object UpdateChecker {

    private const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000L // 12h
    private const val SNOOZE_MS = 3L * 24 * 60 * 60 * 1000L // 3 days

    /** Check for an update. Returns the result — available, up-to-date, or failed. */
    suspend fun checkPhone(context: Context, force: Boolean = false): UpdateCheckResult {
        if (BuildConfig.BUILD_RUN_NUMBER <= 0) return UpdateCheckResult.UpToDate
        val store = UpdateStore(context)
        if (!force && !store.checksEnabled.first()) return UpdateCheckResult.UpToDate
        val now = System.currentTimeMillis()
        // Skip rapid re-checks (1 minute debounce), but always check on cold start.
        if (!force && now - store.lastCheckedAt() < 60_000L) return UpdateCheckResult.UpToDate
        // Respect snooze ("Remind me in 3 days").
        if (!force && now < store.snoozeUntil()) return UpdateCheckResult.UpToDate

        val branch = BuildConfig.BUILD_BRANCH.ifBlank { UpdateApi.DEFAULT_BRANCH }
        val run = UpdateApi.fetchLatestSuccessfulRun(branch)
        store.setLastCheckedAt(now)
        if (run == null) return UpdateCheckResult.Failed("Could not reach GitHub (rate limited or offline)")
        if (run.runNumber <= BuildConfig.BUILD_RUN_NUMBER) {
            store.clearAvailable()
            return UpdateCheckResult.UpToDate
        }
        // Persisted (not just returned) so WearBridge can mirror it to the
        // watch on the next settings publish without AppViewModel having to
        // thread the result through every publishSettings call site.
        store.setAvailable(run.runNumber, run.displayTitle, run.htmlUrl)
        return UpdateCheckResult.Available(UpdateInfo(run))
    }

    /** "Remind me in a few days": suppress even a still-due check until then. */
    suspend fun snooze(context: Context) {
        UpdateStore(context).setSnoozeUntil(System.currentTimeMillis() + SNOOZE_MS)
    }
}
