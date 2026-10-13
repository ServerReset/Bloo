package com.bloo.bluelink.data

/**
 * The decisions behind the foreground poll that makes sync near-real-time, kept as pure functions so
 * they are unit-tested without a device or a Drive file: when has another device changed the file, and
 * when is it time to look at it again.
 */
object SyncPoll {
    /** How often the app asks whether the file changed while it is on screen. */
    const val POLL_INTERVAL_MS = 10_000L

    /** The least time between content downloads for a provider that reports no modified time. */
    const val CONTENT_PEEK_MIN_INTERVAL_MS = 30_000L

    /**
     * Whether the file changed, judged by its modified time in the provider's own clock against what
     * this device last wrote or read there. Null means "cannot tell" (the provider reports no time),
     * so the caller falls back to the content hash.
     */
    fun changedByModifiedTime(providerModifiedMs: Long?, lastSyncMs: Long): Boolean? =
        providerModifiedMs?.let { it > lastSyncMs }

    /** Whether enough time has passed to download the file again for a hash check. */
    fun contentPeekDue(nowMs: Long, lastPeekMs: Long): Boolean =
        nowMs - lastPeekMs >= CONTENT_PEEK_MIN_INTERVAL_MS

    /**
     * Whether the file changed, judged by the content hash it carries against the hash this device last
     * synced. A file with no hash (an older client wrote it, or it is empty) is not a change we can see.
     */
    fun changedByHash(remoteHash: String?, lastSyncedHash: String?): Boolean =
        remoteHash != null && remoteHash != lastSyncedHash

    /** The ordering the poll checks in: the cheap metadata test first, then the hash only when it must. */
    fun changed(
        providerModifiedMs: Long?,
        lastSyncMs: Long,
        peekDue: Boolean,
        remoteHash: () -> String?,
        lastSyncedHash: String?,
    ): Boolean {
        changedByModifiedTime(providerModifiedMs, lastSyncMs)?.let { return it }
        if (!peekDue) return false
        return changedByHash(remoteHash(), lastSyncedHash)
    }

    /**
     * Whether a pass that FOUND the file changed should actually write it.
     *
     * A pass with nothing of its own to say -- no dirty keys, no device-registry or primary change,
     * not a first-ever sync, and the file already carrying our content hash -- must not. The
     * foreground poll runs every [POLL_INTERVAL_MS], and our own upload advances the file's modified
     * time, which the next poll reads as a remote change: an unconditional upload is therefore a
     * Drive write every ~10 seconds that feeds itself. Pure, so it is unit-tested.
     */
    fun shouldUpload(
        dirtyKeys: Collection<String>,
        registryChanged: Boolean,
        primaryChanged: Boolean,
        fullAdopt: Boolean,
        fileAlreadyCarriesUs: Boolean,
    ): Boolean =
        dirtyKeys.isNotEmpty() || registryChanged || primaryChanged || fullAdopt || !fileAlreadyCarriesUs
}
