package com.bloo.bluelink.data

import java.util.concurrent.ConcurrentHashMap

/** A session that carries the PIN its control tokens are minted with. */
interface PinnedSession {
    val pin: String?
}

/**
 * A brand session for a controlled account: its access and refresh tokens, the device it is bound to, and the PIN that unlocks control.
 * Canada and Kia US both hold exactly this; the wire names stay in each brand's own headers. Canada's and Kia's session names are aliases of it.
 */
data class ControlSession(
    val token: String,
    val refresh: String?,
    val deviceId: String,
    override val pin: String?,
) : PinnedSession

typealias CanadaSession = ControlSession
typealias KiaSession = ControlSession

/**
 * A car on a controlled account. Every brand has these fields. The one number or key a brand needs to command its car is here too, and the
 * others leave it at its default: [year] for Canada (it picks the temperature table), [ccs2] for Europe (its protocol version) and [key] for
 * Kia US (the vinkey its commands are keyed by). [vin] defaults to [id] for brands that key cars by their id.
 */
data class ControlVehicle(
    override val id: String,
    override val name: String,
    override val model: String,
    override val vin: String = id,
    override val isEv: Boolean,
    override val colour: String? = null,
    override val trim: String? = null,
    val year: Int? = null,
    val ccs2: Int = 0,
    val key: String = "",
) : AccountVehicle

typealias CanadaVehicleSummary = ControlVehicle
typealias EuVehicleSummary = ControlVehicle
typealias KiaVehicleSummary = ControlVehicle

/**
 * The plumbing the Canada, EU and Kia repositories share, on top of [SessionRepository]. The account's vehicle list is cached by VIN. A
 * car may have a PIN-derived control token, cached per VIN and minted again once if a command 401s ([controlTokenAuth]). A brand supplies
 * its own session type, API calls, how a control token is minted, and how a session is renewed; everything else is here, once.
 */
abstract class ControlledAccountRepository<S : PinnedSession, Sum : AccountVehicle>(
    store: SessionStore,
    brand: Brand,
) : SessionRepository<S>(store, brand) {
    private val summaries = SummaryCache<Sum>()
    private val controlTokens = ConcurrentHashMap<String, String>()

    /** Every vehicle on the account, from the brand's API. */
    protected abstract suspend fun fetchVehicles(s: S): List<Sum>

    /**
     * A control token for [summary]'s car, minted from the session and its PIN. Only a brand whose commands need one overrides this; for
     * the others, [withCommandAuth] is never reached.
     */
    protected open suspend fun mintControlToken(s: S, summary: Sum, pin: String): String =
        throw BlueLinkException("This brand's commands do not use a control token")

    /** The cross-brand [Vehicle] for a summary. A brand whose cars identify themselves differently overrides it. */
    protected open fun Sum.asVehicle(): Vehicle = toVehicle(brand)

    /** Drops every cached control token, for when the session they were minted for is gone. */
    protected fun clearControlTokens() = controlTokens.clear()

    override fun clearCaches() {
        summaries.clear()
        controlTokens.clear()
    }

    override suspend fun vehicles(): List<Vehicle> = withSession { s -> fetchSummaries(s).map { it.asVehicle() } }

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
