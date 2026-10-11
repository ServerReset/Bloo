package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncPollTest {
    @Test
    fun aNewerModifiedTimeIsAChangeAndOurOwnWriteIsNot() {
        assertEquals(true, SyncPoll.changedByModifiedTime(2_000L, 1_000L))
        assertEquals(false, SyncPoll.changedByModifiedTime(1_000L, 1_000L)) // exactly what we wrote
        assertEquals(false, SyncPoll.changedByModifiedTime(500L, 1_000L))
        assertNull(SyncPoll.changedByModifiedTime(null, 1_000L))
    }

    @Test
    fun theHashFallbackSeesOnlyARealDifference() {
        assertTrue(SyncPoll.changedByHash("new", "old"))
        assertFalse(SyncPoll.changedByHash("same", "same"))
        assertFalse(SyncPoll.changedByHash(null, "old"))
        assertTrue(SyncPoll.changedByHash("first", null))
    }

    @Test
    fun aMetadatalessProviderIsOnlyDownloadedEveryThirtySeconds() {
        assertFalse(SyncPoll.contentPeekDue(nowMs = 10_000L, lastPeekMs = 0L))
        assertTrue(SyncPoll.contentPeekDue(nowMs = 30_000L, lastPeekMs = 0L))
    }

    @Test
    fun theCheapTestRunsFirstAndTheDownloadOnlyWhenItMust() {
        var downloads = 0
        val hash = { downloads++; "remote" }
        // A provider that reports its time never downloads.
        assertTrue(SyncPoll.changed(2_000L, 1_000L, peekDue = true, remoteHash = hash, lastSyncedHash = "x"))
        assertFalse(SyncPoll.changed(1_000L, 1_000L, peekDue = true, remoteHash = hash, lastSyncedHash = "x"))
        assertEquals(0, downloads)
        // One that does not: skipped until due, then compared by hash.
        assertFalse(SyncPoll.changed(null, 1_000L, peekDue = false, remoteHash = hash, lastSyncedHash = "x"))
        assertEquals(0, downloads)
        assertTrue(SyncPoll.changed(null, 1_000L, peekDue = true, remoteHash = hash, lastSyncedHash = "x"))
        assertEquals(1, downloads)
        assertFalse(SyncPoll.changed(null, 1_000L, peekDue = true, remoteHash = hash, lastSyncedHash = "remote"))
    }

    @Test
    fun theNearRealTimeBudgetIsTenSecondsOfPolling() {
        // A change written on one device is noticed by the other within one poll interval, plus the
        // one-second push delay on the writer: comfortably "near real time".
        assertTrue(SyncPoll.POLL_INTERVAL_MS <= 10_000L)
    }
}
