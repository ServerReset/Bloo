package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [UpdateGate], the side-effect-free decisions shared by the phone's
 * UpdateChecker and the watch's WearViewModel.
 *
 * Worth pinning because both failure directions are invisible in normal use. Skip too
 * eagerly and updates silently never appear -- there is no error, just a banner that
 * never shows. Skip too rarely and every launch hits the GitHub API. Neither shows up
 * without either reading the arithmetic or waiting hours on a device, which is exactly
 * the kind of thing a unit test should own.
 *
 * The clock-skew cases below are regression tests, not hypotheticals: these windows
 * compare stored wall-clock timestamps against the current wall clock, and wall clocks
 * do move backwards (a watch that has drifted, a device whose date was set wrong and
 * later corrected). Before this was fixed, a timestamp left in the future suppressed
 * every non-forced check for as long as the skew lasted.
 */
class UpdateGateTest {

    private val hour = 60L * 60 * 1000
    private val now = 1_700_000_000_000L

    private fun skip(
        buildRunNumber: Int = 100,
        force: Boolean = false,
        lastCheckedAt: Long = 0L,
        snoozeUntil: Long = 0L,
        minIntervalMs: Long = 12 * hour,
    ) = UpdateGate.shouldSkipCheck(
        buildRunNumber = buildRunNumber,
        force = force,
        now = now,
        lastCheckedAt = lastCheckedAt,
        snoozeUntil = snoozeUntil,
        minIntervalMs = minIntervalMs,
    )

    // ---- unstamped builds ---------------------------------------------------

    @Test
    fun `a build with no CI run number never checks`() {
        // A locally-built APK has BUILD_RUN_NUMBER 0, so there is no installed version
        // to compare a release against. Deliberately true even when forced.
        assertTrue(skip(buildRunNumber = 0))
        assertTrue(skip(buildRunNumber = 0, force = true))
        assertTrue(skip(buildRunNumber = -1))
    }

    // ---- the debounce window ------------------------------------------------

    @Test
    fun `a first check is never debounced`() {
        // lastCheckedAt 0 is "never checked", not "checked at the epoch".
        assertFalse(skip(lastCheckedAt = 0L))
    }

    @Test
    fun `a check inside the interval is skipped and one at or past it is not`() {
        assertTrue(skip(lastCheckedAt = now - hour))
        assertTrue(skip(lastCheckedAt = now - (12 * hour - 1)))
        // Exactly at the interval is due: the window is half-open.
        assertFalse(skip(lastCheckedAt = now - 12 * hour))
        assertFalse(skip(lastCheckedAt = now - 13 * hour))
    }

    @Test
    fun `force ignores the debounce`() {
        assertFalse(skip(force = true, lastCheckedAt = now))
    }

    @Test
    fun `a lastCheckedAt in the future does not suppress the check`() {
        // Regression: `now - lastCheckedAt < minIntervalMs` is true for ANY negative
        // difference, so a clock corrected backwards used to block every non-forced
        // check until real time caught up -- potentially for months.
        assertFalse(skip(lastCheckedAt = now + hour))
        assertFalse(skip(lastCheckedAt = now + 365L * 24 * hour))
    }

    // ---- the snooze window --------------------------------------------------

    @Test
    fun `an active snooze is honoured and an expired one is not`() {
        assertTrue(skip(snoozeUntil = now + hour))
        assertTrue(skip(snoozeUntil = now + UPDATE_SNOOZE_MS))
        assertFalse(skip(snoozeUntil = now))
        assertFalse(skip(snoozeUntil = now - 1))
    }

    @Test
    fun `force ignores an active snooze`() {
        assertFalse(skip(force = true, snoozeUntil = now + hour))
    }

    @Test
    fun `a snooze further out than any real snooze is ignored`() {
        // Regression, same root cause as the debounce one: nothing can legitimately
        // snooze past UPDATE_SNOOZE_MS (the update tile's "Remind me" uses one day,
        // the default is three), so a deadline beyond that came from a skewed clock
        // and must not suppress updates for the length of the skew.
        assertFalse(skip(snoozeUntil = now + UPDATE_SNOOZE_MS + 1))
        assertFalse(skip(snoozeUntil = now + 365L * 24 * hour))
    }

    @Test
    fun `debounce and snooze are independent reasons to skip`() {
        // Neither window active -> check runs, even though both fields are populated.
        assertFalse(skip(lastCheckedAt = now - 13 * hour, snoozeUntil = now - hour))
        // Either one alone is enough to skip.
        assertTrue(skip(lastCheckedAt = now - hour, snoozeUntil = now - hour))
        assertTrue(skip(lastCheckedAt = now - 13 * hour, snoozeUntil = now + hour))
    }

    // ---- the shared window rule --------------------------------------------

    @Test
    fun `withinWindow treats a future stamp as due, not as recent`() {
        val window = 12 * hour
        // Ordinary recent stamp.
        assertTrue(withinWindow(now, now - hour, window))
        assertTrue(withinWindow(now, now, window))
        // Half-open: exactly one window old is due.
        assertFalse(withinWindow(now, now - window, window))
        assertFalse(withinWindow(now, now - window - 1, window))
        // The whole point: a stamp ahead of the clock is a broken stamp, and the safe
        // reading of a broken throttle is to do the work. `now - stamp < window` would
        // return true for every one of these.
        assertFalse(withinWindow(now, now + 1, window))
        assertFalse(withinWindow(now, now + hour, window))
        assertFalse(withinWindow(now, now + 365L * 24 * hour, window))
    }

    @Test
    fun `withinWindow with a zero window is never within`() {
        // A zero-length window means "never throttle" -- half-open makes that fall out
        // rather than needing a special case at the call sites.
        assertFalse(withinWindow(now, now, 0))
        assertFalse(withinWindow(now, now - 1, 0))
    }

    // ---- isNewer / resolveBranch -------------------------------------------

    private fun run(n: Int) = WorkflowRun(runNumber = n, htmlUrl = "https://example.test/$n")

    @Test
    fun `only a strictly higher run number counts as newer`() {
        assertTrue(UpdateGate.isNewer(run(101), 100))
        // Equal is the installed build itself -- offering it as an update would loop.
        assertFalse(UpdateGate.isNewer(run(100), 100))
        // Older happens on a rollback; it must not be offered as an "update".
        assertFalse(UpdateGate.isNewer(run(99), 100))
    }

    @Test
    fun `an unstamped branch falls back to the default`() {
        assertEquals(UpdateApi.DEFAULT_BRANCH, UpdateGate.resolveBranch(""))
        assertEquals(UpdateApi.DEFAULT_BRANCH, UpdateGate.resolveBranch("   "))
        assertEquals("some-branch", UpdateGate.resolveBranch("some-branch"))
    }
}
