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

    @Test
    fun aPassWithNothingOfItsOwnToSayDoesNotUpload() {
        // No dirty keys, no registry or primary change, not a first sync, and the file already
        // carries our hash -- so the poll finding "changed" must not become a write.
        assertFalse(
            SyncPoll.shouldUpload(
                dirtyKeys = emptySet(),
                registryChanged = false,
                primaryChanged = false,
                fullAdopt = false,
                fileAlreadyCarriesUs = true,
            ),
        )
    }

    @Test
    fun anythingOfOurOwnStillUploads() {
        // Dirty keys.
        assertTrue(SyncPoll.shouldUpload(setOf("look.theme"), false, false, false, true))
        // A device-registry change.
        assertTrue(SyncPoll.shouldUpload(emptySet(), registryChanged = true, primaryChanged = false, fullAdopt = false, fileAlreadyCarriesUs = true))
        // A primary-device change.
        assertTrue(SyncPoll.shouldUpload(emptySet(), registryChanged = false, primaryChanged = true, fullAdopt = false, fileAlreadyCarriesUs = true))
        // A first-ever sync / adopt.
        assertTrue(SyncPoll.shouldUpload(emptySet(), registryChanged = false, primaryChanged = false, fullAdopt = true, fileAlreadyCarriesUs = true))
        // A file that does not carry us yet.
        assertTrue(SyncPoll.shouldUpload(emptySet(), registryChanged = false, primaryChanged = false, fullAdopt = false, fileAlreadyCarriesUs = false))
    }
}
