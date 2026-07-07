package com.bloo.bluelink.update

import android.content.Context
import com.bloo.bluelink.BuildConfig
import com.bloo.bluelink.data.UpdateApi
import com.bloo.bluelink.data.UpdateStore
import com.bloo.bluelink.data.WorkflowRun
import kotlinx.coroutines.flow.first

/** A newer CI build than what's installed. */
data class UpdateInfo(val run: WorkflowRun)

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

    /** Update info for THIS app (phone), or null if none/not due/snoozed/disabled. */
    suspend fun checkPhone(context: Context, force: Boolean = false): UpdateInfo? {
        // A locally-built (non-CI) install has no run number to compare against.
        if (BuildConfig.BUILD_RUN_NUMBER <= 0) return null
        val store = UpdateStore(context)
        if (!store.checksEnabled.first()) return null
        val now = System.currentTimeMillis()
        if (!force && now - store.lastCheckedAt() < CHECK_INTERVAL_MS) return null
        if (!force && now < store.snoozeUntil()) return null

        // Compare against the branch THIS build came from: run_number increments
        // globally across branches, so a fixed branch both missed newer builds of
        // the installed branch and could offer a higher-numbered build of a
        // different branch (an effective downgrade).
        val branch = BuildConfig.BUILD_BRANCH.ifBlank { UpdateApi.DEFAULT_BRANCH }
        val run = UpdateApi.fetchLatestSuccessfulRun(branch) ?: return null
        // Only consume the 12h window on a successful fetch — a failed check
        // (offline cold start) should retry next launch, not go quiet for 12h.
        store.setLastCheckedAt(now)
        if (run.runNumber <= BuildConfig.BUILD_RUN_NUMBER) return null

        return UpdateInfo(run)
    }

    /** "Remind me in a few days": suppress even a still-due check until then. */
    suspend fun snooze(context: Context) {
        UpdateStore(context).setSnoozeUntil(System.currentTimeMillis() + SNOOZE_MS)
    }
}
