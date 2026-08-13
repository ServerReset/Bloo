package com.bloo.bluelink.update

import android.content.Context
import com.bloo.bluelink.BuildConfig
import com.bloo.bluelink.data.UPDATE_SNOOZE_MS
import com.bloo.bluelink.data.UpdateApi
import com.bloo.bluelink.data.UpdateGate
import com.bloo.bluelink.data.UpdateStore
import com.bloo.bluelink.data.WorkflowRun

/** A newer CI build than what's installed. */
data class UpdateInfo(val run: WorkflowRun)

/** Result of an update check attempt. */
sealed class UpdateCheckResult {
    /** A newer build was found. */
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    /** No newer build found (current is latest) -- a REAL, network-verified answer. */
    data object UpToDate : UpdateCheckResult()
    /**
     * No network call was made at all -- the debounce or an active snooze short-circuited
     * before ever asking GitHub, so this says nothing about whether an update exists.
     *
     * Split out from [UpToDate] because callers were treating the two as the same thing,
     * and they answer different questions: [UpToDate] means "checked, nothing newer";
     * this means "didn't check, don't know." A caller holding a still-valid `Available`
     * from an earlier check must not let a [Skipped] result overwrite it -- that was
     * exactly the bug (see AppViewModel.checkForUpdate's own comment on this case): pull-
     * to-refresh calls [checkPhone] unforced, so any refresh landing inside the previous
     * check's 1-minute debounce window got told "up to date" and cleared a pending update
     * the debounce never actually re-verified.
     */
    data object Skipped : UpdateCheckResult()
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
     *    up-to-date rather than ever flagging a local build as outdated. This one
     *    really is [UpdateCheckResult.UpToDate], not [UpdateCheckResult.Skipped]:
     *    there is no CI baseline to ever check against, on this build, ever --
     *    unlike the debounce/snooze cases below, a later call can't turn this
     *    into a real check, so it isn't "don't know yet," it's "nothing to know."
     * 2. Unless [force] is set, debounce rapid re-checks: if the last check was
     *    under a minute ago, skip hitting the network again and report
     *    [UpdateCheckResult.Skipped] -- NOT [UpdateCheckResult.UpToDate], which
     *    would tell a caller holding a still-valid `Available` from that earlier
     *    check that it's safe to forget it. `force` bypasses this so an explicit
     *    user-initiated "check now" always goes through.
     * 3. Unless [force] is set, respect an active snooze window (see [snooze])
     *    -- the user asked to not be reminded until a later time, so a due
     *    background/cold-start check silently no-ops (also [UpdateCheckResult.Skipped])
     *    during that window too.
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
        // Unstamped local build: bail before touching the store at all (there's no
        // baseline to compare a local build against).
        if (BuildConfig.BUILD_RUN_NUMBER <= 0) return UpdateCheckResult.UpToDate
        val store = UpdateStore(context)
        val now = System.currentTimeMillis()
        // 1-minute debounce or an active snooze -- the shared gate (see UpdateGate).
        // Cold start passes force = false but a fresh lastCheckedAt.
        if (UpdateGate.shouldSkipCheck(
                buildRunNumber = BuildConfig.BUILD_RUN_NUMBER,
                force = force,
                now = now,
                lastCheckedAt = store.lastCheckedAt(),
                snoozeUntil = store.snoozeUntil(),
                minIntervalMs = 60_000L,
            )
        ) return UpdateCheckResult.Skipped

        val run = UpdateApi.fetchLatestSuccessfulRun(UpdateGate.resolveBranch(BuildConfig.BUILD_BRANCH))
        // Stamp unconditionally -- even a failed fetch counts against the debounce, so a
        // rate-limited call doesn't retry in a tight loop.
        store.setLastCheckedAt(now)
        if (run == null) return UpdateCheckResult.Failed("Could not reach GitHub (rate limited or offline)")
        if (!UpdateGate.isNewer(run, BuildConfig.BUILD_RUN_NUMBER)) return UpdateCheckResult.UpToDate
        return UpdateCheckResult.Available(UpdateInfo(run))
    }

    /**
     * "Remind me": suppress even a still-due check until [durationMs] from now.
     * Stamps a future timestamp into [UpdateStore]; [checkPhone] compares
     * `now < snoozeUntil()` on every non-forced call, so this doesn't cancel or
     * reschedule anything by itself -- it just makes future non-forced checks
     * short-circuit to up-to-date until the stamped time passes.
     *
     * [durationMs] defaults to [UPDATE_SNOOZE_MS] for any existing caller, but the
     * update-tile "Remind me" passes 1 day so the snooze window MATCHES its 1-day
     * reminder worker (UpdateReminderWorker). Keeping them aligned means that even
     * if the worker is delayed by Doze, a normal refresh revives the tile at ~1 day
     * too -- rather than the tile staying suppressed for the full default window
     * while only the (possibly-late) worker could bring it back.
     */
    suspend fun snooze(context: Context, durationMs: Long = UPDATE_SNOOZE_MS) {
        UpdateStore(context).setSnoozeUntil(System.currentTimeMillis() + durationMs)
    }
}
