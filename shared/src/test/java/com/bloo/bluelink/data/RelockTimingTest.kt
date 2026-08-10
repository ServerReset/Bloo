package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [shouldRelockAfter], the app-lock re-lock timing rule shared by the
 * phone's biometric lock and the watch's PIN lock.
 *
 * This is security-adjacent: the whole point of a timed lock is that it re-engages after the
 * configured delay, so the threshold boundaries are the contract worth pinning. A regression
 * here (an off-by-one on a threshold, or a wrong constant) would either lock the user out
 * early or leave the app unlocked past when they asked -- neither is visible by inspection,
 * and the phone reaches this via a wire-key mapping (LockTiming.wireKey) that a test can't see.
 * Both callers only ever pass the five known keys; the `else` fail-safe is pinned too so a
 * future typo'd key locks rather than silently stays open.
 */
class RelockTimingTest {

    @Test
    fun offNeverLocks() {
        // Elapsed is irrelevant: "off" means the app never auto-relocks.
        assertFalse(shouldRelockAfter(0L, "off"))
        assertFalse(shouldRelockAfter(Long.MAX_VALUE, "off"))
    }

    @Test
    fun immediateAlwaysLocks() {
        // Even zero elapsed locks -- "immediate" is the moment the app is backgrounded.
        assertTrue(shouldRelockAfter(0L, "immediate"))
        assertTrue(shouldRelockAfter(Long.MAX_VALUE, "immediate"))
    }

    @Test
    fun oneMinuteBoundary() {
        assertFalse(shouldRelockAfter(59_999L, "1min"), "must not lock one ms before the delay")
        assertTrue(shouldRelockAfter(60_000L, "1min"), "must lock exactly at the delay")
        assertTrue(shouldRelockAfter(60_001L, "1min"))
    }

    @Test
    fun fiveMinuteBoundary() {
        assertFalse(shouldRelockAfter(299_999L, "5min"))
        assertTrue(shouldRelockAfter(300_000L, "5min"))
    }

    @Test
    fun tenMinuteBoundary() {
        assertFalse(shouldRelockAfter(599_999L, "10min"))
        assertTrue(shouldRelockAfter(600_000L, "10min"))
    }

    @Test
    fun unknownKeyFailsSafeToLocked() {
        // An unrecognised key (a future typo, or a value from a newer peer) locks rather than
        // silently staying open -- the safe direction for a security control.
        assertTrue(shouldRelockAfter(0L, "42min"))
        assertTrue(shouldRelockAfter(0L, ""))
    }

    @Test
    fun everyLockTimingWireKeyIsHandledNotFailSafe() {
        // Guards the LockTiming.wireKey <-> shouldRelockAfter contract at the value level (the
        // phone maps its enum to these keys): each mapped key must hit a REAL branch, never the
        // `else` fail-safe. If someone adds a LockTiming value and a "Ns" key here without a
        // matching branch, "immediate"-like always-lock behaviour would slip in silently.
        // "off" and "immediate" are covered above; the timed keys must respect their delay,
        // which a fail-safe `else` (always true) would not at elapsed 0.
        assertFalse(shouldRelockAfter(0L, "1min"))
        assertFalse(shouldRelockAfter(0L, "5min"))
        assertFalse(shouldRelockAfter(0L, "10min"))
    }
}
