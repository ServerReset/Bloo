package com.bloo.bluelink.update

import android.content.Context
import com.bloo.bluelink.BuildConfig
import com.bloo.bluelink.data.UPDATE_SNOOZE_MS
import com.bloo.bluelink.data.UpdateApi
import com.bloo.bluelink.data.UpdateStore
import com.bloo.bluelink.data.WorkflowRun

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

    /**
     * Check for an update. Returns the result — available, up-to-date, or failed.
     *
     * Order of checks, each an early return that skips the network call entirely:
     * 1. If this build wasn't stamped with a CI run number at all
     *    (`BUILD_RUN_NUMBER <= 0`, e.g. a local debug build not built via CI),
     *    there's no meaningful baseline to compare against, so always report
     *    up-to-date rather than ever flagging a local build as outdated.
     * 2. Unless [force] is set, debounce rapid re-checks: if the last check was
     *    under a minute ago, skip hitting the network again. `force` bypasses
     *    this so an explicit user-initiated "check now" always goes through.
     * 3. Unless [force] is set, respect an active snooze window (see [snooze])
     *    -- the user asked to not be reminded until a later time, so a due
     *    background/cold-start check silently no-ops during that window too.
     * 4. Otherwise, actually fetches the latest successful CI run for the
     *    build's own branch (falling back to [UpdateApi.DEFAULT_BRANCH] if this
     *    build didn't record one) via [UpdateApi.fetchLatestSuccessfulRun], and
     *    unconditionally records "checked now" via [UpdateStore.setLastCheckedAt]
     *    regardless of whether the fetch succeeded -- so the 1-minute debounce
     *    above applies even after a failed attempt, rather than retrying every
     *    call in a tight loop.
     * 5. A null result means the fetch itself failed (network/rate limit) and
     *    is reported as [UpdateCheckResult.Failed]. Otherwise the fetched run's
     *    number is compared against this build's own
     *    `BuildConfig.BUILD_RUN_NUMBER`: a run number no greater than the
     *    current build's means we're already on the latest (or a newer/local)
     *    build, otherwise a strictly greater run number is a real update.
     */
    suspend fun checkPhone(context: Context, force: Boolean = false): UpdateCheckResult {
        if (BuildConfig.BUILD_RUN_NUMBER <= 0) return UpdateCheckResult.UpToDate
        val store = UpdateStore(context)
        val now = System.currentTimeMillis()
        // Skip rapid re-checks (1 minute debounce), but always check on cold start.
        if (!force && now - store.lastCheckedAt() < 60_000L) return UpdateCheckResult.UpToDate
        // Respect snooze ("Remind me in 3 days").
        if (!force && now < store.snoozeUntil()) return UpdateCheckResult.UpToDate

        val branch = BuildConfig.BUILD_BRANCH.ifBlank { UpdateApi.DEFAULT_BRANCH }
        val run = UpdateApi.fetchLatestSuccessfulRun(branch)
        store.setLastCheckedAt(now)
        if (run == null) return UpdateCheckResult.Failed("Could not reach GitHub (rate limited or offline)")
        if (run.runNumber <= BuildConfig.BUILD_RUN_NUMBER) return UpdateCheckResult.UpToDate
        return UpdateCheckResult.Available(UpdateInfo(run))
    }

    /**
     * "Remind me in a few days": suppress even a still-due check until then.
     * Simply stamps a future timestamp ([UPDATE_SNOOZE_MS] from now) into
     * [UpdateStore]; [checkPhone] compares `now < snoozeUntil()` on every
     * non-forced call, so this doesn't cancel or reschedule anything by itself
     * -- it just makes future non-forced checks short-circuit to up-to-date
     * until the stamped time passes.
     */
    suspend fun snooze(context: Context) {
        UpdateStore(context).setSnoozeUntil(System.currentTimeMillis() + UPDATE_SNOOZE_MS)
    }
}
