package com.bloo.bluelink.data

/**
 * One signed-in brand's vehicle operations, however that brand's backend works underneath
 * (Hyundai/Genesis telematics, or Kia Connect via [KiaRepository]).
 */
interface VehicleRepository {
    suspend fun logout()
    suspend fun vehicles(): List<Vehicle>
    suspend fun status(v: Vehicle, refresh: Boolean): VehicleStatus?
    suspend fun location(v: Vehicle): GeoLocation?

    /** Recent EV trips; empty where the backend has no equivalent (Kia US). */
    suspend fun trips(v: Vehicle): List<EvTrip> = emptyList()
    suspend fun lock(v: Vehicle)
    suspend fun unlock(v: Vehicle)
    suspend fun startClimate(v: Vehicle, req: ClimateRequest)
    suspend fun stopClimate(v: Vehicle)
    suspend fun setChargeTargets(v: Vehicle, acPercent: Int, dcPercent: Int)
    suspend fun startCharge(v: Vehicle)
    suspend fun stopCharge(v: Vehicle)

    /**
     * True where the backend actually supports [flashLights]/[hornAndLights] (Hyundai/Genesis US
     * telematics only -- Kia's US API has no equivalent).
     */
    val supportsHornLights: Boolean get() = false
    suspend fun flashLights(v: Vehicle) {}
    suspend fun hornAndLights(v: Vehicle) {}
}

/**
 * Build the right repository for a brand. Kia US rides a different backend ([KiaRepository]); the
 * three Canada brands share [CanadaApi]; Europe (CCS2) rides [EuApi]; Hyundai/Genesis US share the
 * Hyundai-shaped [BlueLinkApi].
 */
fun repositoryFor(brand: Brand, store: SessionStore, credentials: CredentialStore): VehicleRepository {
    // Timed as a whole: constructing an Api builds an OkHttpClient (dispatcher, thread pools,
    // interceptors), which is real work and, for the cold-start path, lands on whichever thread
    // first calls a repository -- the main thread for a cold auto-login.
    val started = StartupTrace.begin()
    val repo = when {
        brand == Brand.KIA -> KiaRepository(KiaUsaApi(), store, credentials)
        brand.isCanada -> CanadaRepository(CanadaApi(brand), store, brand, credentials)
        brand.isEurope -> EuRepository(EuApi(brand), store, brand, credentials)
        else -> BlueLinkRepository(BlueLinkApi(brand), store, brand)
    }
    StartupTrace.end("repositoryFor(${brand.name}) [Api+OkHttp+repo]", started)
    return repo
}

/**
 * Runs [block] with a cached PIN-derived control token for [vin], minting it via [mint] if absent
 * and re-minting once if the command itself 401s (an expired control token, distinct from an expired
 * session -- the caller's own withSession handles that one layer down). Shared by the Canada and EU
 * repositories, whose control tokens differ only in how they are minted.
 */
internal suspend fun <T> controlTokenAuth(
    cache: java.util.concurrent.ConcurrentHashMap<String, String>,
    vin: String,
    mint: suspend () -> String,
    block: suspend (String) -> T,
): T {
    val cached = cache[vin]
    try {
        val token = cached ?: mint().also { cache[vin] = it }
        return block(token)
    } catch (e: BlueLinkException) {
        if (e.code != 401 || cached == null) throw e
        val fresh = mint()
        cache[vin] = fresh
        return block(fresh)
    }
}

/**
 * Coordinates one brand's API client with its persisted session, retrying once on an auth failure
 * by refreshing the access token. All data is live.
 */
class BlueLinkRepository(
    private val api: BlueLinkApi,
    private val store: SessionStore,
    private val brand: Brand,
) : VehicleRepository {

    /**
     * In-memory copy of the last [SessionStore.Session] loaded for this brand, alongside
     * [withSession]'s own timing showing store.load() itself taking 1-3 SECONDS -- not the usual
     * "instant, already-warm DataStore read" this class's own comments assumed -- and a single
     * cold-start burst calling it three separate times back to back (once each for the current
     * car's status, another car's status, and trips), paying that cost fully every time for data
     * that cannot have changed between them.
     */
    @Volatile
    private var cachedSession: SessionStore.Session? = null

    /**
     * Authenticates against the brand's API and persists the resulting tokens plus the
     * caller-supplied [pin]/[username] as a new [SessionStore.Session] for this brand. Note the PIN
     * itself is never sent to or validated by the login call — it's only remembered here so it can
     * be attached as a header on later commands.
     */
    suspend fun login(username: String, password: String, pin: String) {
        val token = api.login(username, password)
        val session = SessionStore.Session.of(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            deviceId = null,
            username = username,
            pin = pin,
            brand = brand,
        )
        store.save(session)
        cachedSession = session
    }

    override suspend fun logout() {
        store.clear(brand)
        cachedSession = null
    }

    // Stamps each vehicle returned by the API with this repository's brand code, since the raw API
    // response doesn't distinguish brand and the UI/other layers need it to route subsequent calls
    // (and to disambiguate vehicles across brands).
    override suspend fun vehicles(): List<Vehicle> = withSession { s ->
        api.vehicles(s.accessToken, s.username).map { it.copy(brandIndicator = brand.code) }
    }

    override suspend fun status(v: Vehicle, refresh: Boolean): VehicleStatus? = withSession { s ->
        api.status(s.accessToken, s.username, s.pin, v, refresh)
    }

    override suspend fun location(v: Vehicle): GeoLocation? = withSession { s ->
        api.location(s.accessToken, s.username, s.pin, v)
    }

    override suspend fun trips(v: Vehicle): List<EvTrip> = withSession { s ->
        api.tripDetails(s.accessToken, s.username, s.pin, v)
    }

    override suspend fun lock(v: Vehicle) {
        withSession { s -> api.lock(s.accessToken, s.username, s.pin, v) }
    }

    override suspend fun unlock(v: Vehicle) {
        withSession { s -> api.unlock(s.accessToken, s.username, s.pin, v) }
    }

    override suspend fun startClimate(v: Vehicle, req: ClimateRequest) {
        withSession { s -> api.startClimate(s.accessToken, s.username, s.pin, v, req) }
    }

    override suspend fun stopClimate(v: Vehicle) {
        withSession { s -> api.stopClimate(s.accessToken, s.username, s.pin, v) }
    }

    override suspend fun setChargeTargets(v: Vehicle, acPercent: Int, dcPercent: Int) {
        withSession { s -> api.setChargeTargets(s.accessToken, s.username, s.pin, v, acPercent, dcPercent) }
    }

    override suspend fun startCharge(v: Vehicle) {
        withSession { s -> api.startCharge(s.accessToken, s.username, s.pin, v) }
    }

    override val supportsHornLights: Boolean get() = true

    override suspend fun flashLights(v: Vehicle) {
        withSession { s -> api.flashLights(s.accessToken, s.username, s.pin, v) }
    }

    override suspend fun hornAndLights(v: Vehicle) {
        withSession { s -> api.hornAndLights(s.accessToken, s.username, s.pin, v) }
    }

    override suspend fun stopCharge(v: Vehicle) {
        withSession { s -> api.stopCharge(s.accessToken, s.username, s.pin, v) }
    }

    /**
     * Runs [block] with this brand's session, refreshing the token once on 401/403. Mechanism:
     * loads the persisted [SessionStore.Session] for this brand (throwing if there is none, i.e.
     * not logged in) and invokes [block] with it.
     */
    private suspend fun <T> withSession(block: suspend (SessionStore.Session) -> T): T {
        // cachedSession first -- see that property's own doc for why (three of these paid the full
        // store.load() cost back to back in one cold-start burst, for data that hadn't changed
        // between them).
        val session = cachedSession ?: run {
            val loadStartedAt = System.currentTimeMillis()
            val loaded = store.load(brand) ?: throw BlueLinkException("Not logged in")
            val loadMs = System.currentTimeMillis() - loadStartedAt
            if (loadMs > 500) {
                AppLog.log("BlueLinkRepository: store.load(${brand.label}) took ${loadMs}ms")
            }
            loaded.also { cachedSession = it }
        }
        return try {
            block(session)
        } catch (e: BlueLinkException) {
            val refreshToken = session.refreshToken
            val isAuth = e.code == 401 || e.code == 403
            if (isAuth && refreshToken != null) {
                val refreshed = api.refresh(refreshToken)
                store.updateAccessToken(brand, refreshed.accessToken, refreshed.refreshToken)
                val updated = store.load(brand) ?: throw e
                cachedSession = updated
                block(updated)
            } else {
                throw e
            }
        }
    }
}
