package com.bloo.bluelink.data

/**
 * The session handling every signed-in brand shares. A call loads the stored session, and one that fails because the session has died is
 * retried once after the session is renewed ([withReauthOnce]). Logout forgets the session. A brand supplies its own session type, which
 * failures count as a dead session, how the session is renewed, and what else it keeps in memory ([clearCaches]).
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
            reauthenticate = { e -> renewSession(stored, e) },
            block = block,
        )
    }
}
