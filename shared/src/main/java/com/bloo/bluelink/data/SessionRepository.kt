package com.bloo.bluelink.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * The session handling every signed-in brand shares. A call loads the stored session, and one that fails because the session has died is
 * retried once after the session is renewed ([withReauthOnce]). Logout forgets the session. A brand supplies its own session type, which
 * failures count as a dead session, how the session is renewed, and what else it keeps in memory ([clearCaches]).
 *
 * Renewal is one at a time per brand, for the whole process: when several calls (the screen's status, location and trips, or the screen and
 * a background worker) all find the session dead at once, the first renews it and the rest pick up its renewed session. On the brands whose
 * refresh tokens rotate (Kia, Canada, Europe), a second renewal racing the first could spend a token the first had already replaced, or
 * trip the brand's sign-in rate limit.
 */
abstract class SessionRepository<S : Any>(
    protected val store: SessionStore,
    protected val brand: Brand,
) : VehicleRepository {
    /** The stored session as this brand's own session type. */
    protected abstract fun SessionStore.Session.toSession(): S

    /** Whether [e], from a call made with [session], means the session has died and should be renewed, as this brand reports it. */
    protected abstract fun isSessionFailure(e: BlueLinkException, session: SessionStore.Session): Boolean

    /** A renewed session in place of the dead [stored] one; throws when it cannot be renewed. */
    protected abstract suspend fun renewSession(stored: SessionStore.Session, e: BlueLinkException): S

    /**
     * Takes up [fresh], a session another call renewed while this one waited, in place of the dead one. A brand that keeps anything bound
     * to the old session in memory overrides this to drop or replace it.
     */
    protected open suspend fun adoptRenewedSession(fresh: SessionStore.Session): S = fresh.toSession()

    /** The stored session for a call. A brand that keeps the session in memory overrides this. */
    protected open suspend fun loadSession(): SessionStore.Session =
        store.load(brand) ?: throw BlueLinkException("Not logged in")

    /** Forgets whatever this repository keeps in memory. A brand with such a cache overrides it. */
    protected open fun clearCaches() {}

    override suspend fun logout() {
        clearCaches()
        store.clear(brand)
    }

    /** Runs [block] with the session; a dead one is renewed once and [block] runs again with the renewed one. */
    protected suspend fun <T> withSession(block: suspend (S) -> T): T {
        val stored = loadSession()
        return withReauthOnce(
            stored.toSession(),
            isAuthFailure = { isSessionFailure(it, stored) },
            reauthenticate = { e -> renewOnce(stored, e) },
            block = block,
        )
    }

    /**
     * Renews the dead [stored] session, one renewal at a time for this brand. A call that waited behind another's renewal finds the stored
     * session already replaced and takes that one up instead of renewing again.
     */
    private suspend fun renewOnce(stored: SessionStore.Session, e: BlueLinkException): S =
        renewLockFor(brand).withLock {
            val current = store.load(brand) ?: throw e
            if (current.accessToken != stored.accessToken) {
                AppLog.log("${brand.label}: session already renewed by another call; reusing it")
                adoptRenewedSession(current)
            } else {
                renewSession(stored, e)
            }
        }

    private companion object {
        private val renewLocks = ConcurrentHashMap<Brand, Mutex>()

        fun renewLockFor(brand: Brand): Mutex = renewLocks.getOrPut(brand) { Mutex() }
    }
}
