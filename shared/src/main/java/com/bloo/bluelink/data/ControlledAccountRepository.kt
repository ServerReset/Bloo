package com.bloo.bluelink.data

import java.util.concurrent.ConcurrentHashMap

/** A session that carries the PIN its control tokens are minted with. */
interface PinnedSession {
    val pin: String?
}

/**
 * The plumbing the Canada and EU repositories share. The account's vehicle list is cached by VIN. Each car has a PIN-derived control
 * token, cached per VIN and minted again once if a command 401s ([controlTokenAuth]). A dead session is renewed once and the call runs
 * again with the renewed session ([withReauthOnce]). A brand supplies its own session type, its API calls, how a control token is minted,
 * which failures count as a dead session, and how the session is renewed. Everything else is here, once.
 */
abstract class ControlledAccountRepository<S : PinnedSession, Sum : AccountVehicle>(
    protected val store: SessionStore,
    protected val brand: Brand,
) : VehicleRepository {
    private val summaries = SummaryCache<Sum>()
    private val controlTokens = ConcurrentHashMap<String, String>()

    /** The stored session, as this brand's own session type. */
    protected abstract fun SessionStore.Session.toSession(): S

    /** Every vehicle on the account, from the brand's API. */
    protected abstract suspend fun fetchVehicles(s: S): List<Sum>

    /**
     * A control token for [summary]'s car, minted from the session and its PIN. Only a brand whose commands need one overrides this; for
     * the others, [withCommandAuth] is never reached.
     */
    protected open suspend fun mintControlToken(s: S, summary: Sum, pin: String): String =
        throw BlueLinkException("This brand's commands do not use a control token")

    /** Whether [e] means the session has died and should be renewed, as this brand reports it. */
    protected abstract fun isSessionFailure(e: BlueLinkException): Boolean

    /** A renewed session in place of the dead [stored] one; throws when it cannot be renewed. */
    protected abstract suspend fun renewSession(stored: SessionStore.Session, e: BlueLinkException): S

    /** Drops every cached control token, for when the session they were minted for is gone. */
    protected fun clearControlTokens() = controlTokens.clear()

    override suspend fun vehicles(): List<Vehicle> = withSession { s -> fetchSummaries(s).map { it.asVehicle() } }

    /** The cross-brand [Vehicle] for a summary. A brand whose cars identify themselves differently overrides it. */
    protected open fun Sum.asVehicle(): Vehicle = toVehicle(brand)

    override suspend fun logout() {
        summaries.clear()
        controlTokens.clear()
        store.clear(brand)
    }

    /** Runs [block] with the saved session; a dead one is renewed once and [block] runs again with the renewed one. */
    protected suspend fun <T> withSession(block: suspend (S) -> T): T {
        val stored = store.load(brand) ?: throw BlueLinkException("Not logged in")
        return withReauthOnce(
            stored.toSession(),
            isAuthFailure = { isSessionFailure(it) },
            reauthenticate = { e -> renewSession(stored, e) },
            block = block,
        )
    }

    /** Runs [block] with a control token for [v]'s car: minted once and cached, and minted again if the command itself 401s. */
    protected suspend fun <T> withCommandAuth(v: Vehicle, block: suspend (S, Sum, String) -> T): T = withSession { s ->
        val summary = summaryFor(s, v)
        controlTokenAuth(controlTokens, v.vin, mint = { mintControlToken(s, summary, s.pin.orEmpty()) }) { token ->
            block(s, summary, token)
        }
    }

    protected suspend fun fetchSummaries(s: S): List<Sum> = summaries.refresh { fetchVehicles(s) }

    protected suspend fun summaryFor(s: S, v: Vehicle): Sum = summaries.require(v.vin) { fetchVehicles(s) }
}
