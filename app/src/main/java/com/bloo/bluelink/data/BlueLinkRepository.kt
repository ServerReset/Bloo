package com.bloo.bluelink.data

/**
 * One signed-in brand's vehicle operations, however that brand's backend works
 * underneath (Hyundai/Genesis telematics, or Kia Connect via [KiaRepository]).
 * Sign-in is brand-specific (Kia needs an OTP round-trip) so it lives on the
 * concrete types, not here.
 */
interface VehicleRepository {
    suspend fun logout()
    suspend fun vehicles(): List<Vehicle>
    suspend fun status(v: Vehicle, refresh: Boolean): VehicleStatus?
    suspend fun location(v: Vehicle): GeoLocation?
    suspend fun lock(v: Vehicle)
    suspend fun unlock(v: Vehicle)
    suspend fun startClimate(v: Vehicle, req: ClimateRequest)
    suspend fun stopClimate(v: Vehicle)
    suspend fun setChargeTargets(v: Vehicle, acPercent: Int, dcPercent: Int)
    suspend fun startCharge(v: Vehicle)
    suspend fun stopCharge(v: Vehicle)
}

/**
 * Build the right repository for a brand. Kia US rides a different backend
 * ([KiaRepository]); Hyundai/Genesis share the Hyundai-shaped [BlueLinkApi].
 */
fun repositoryFor(brand: Brand, store: SessionStore, credentials: CredentialStore): VehicleRepository =
    if (brand == Brand.KIA) KiaRepository(KiaUsaApi(), store, credentials)
    else BlueLinkRepository(BlueLinkApi(brand), store, brand)

/**
 * Coordinates one brand's API client with its persisted session, retrying once
 * on an auth failure by refreshing the access token. All data is live.
 */
class BlueLinkRepository(
    private val api: BlueLinkApi,
    private val store: SessionStore,
    private val brand: Brand,
) : VehicleRepository {

    suspend fun login(username: String, password: String, pin: String) {
        val token = api.login(username, password)
        store.save(
            SessionStore.Session(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                username = username,
                pin = pin,
                brand = brand,
            )
        )
    }

    override suspend fun logout() = store.clear(brand)

    override suspend fun vehicles(): List<Vehicle> = withSession { s ->
        api.vehicles(s.accessToken, s.username).map { it.copy(brandIndicator = brand.code) }
    }

    override suspend fun status(v: Vehicle, refresh: Boolean): VehicleStatus? = withSession { s ->
        api.status(s.accessToken, s.username, s.pin, v, refresh)
    }

    override suspend fun location(v: Vehicle): GeoLocation? = withSession { s ->
        api.location(s.accessToken, s.username, s.pin, v)
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

    override suspend fun stopCharge(v: Vehicle) {
        withSession { s -> api.stopCharge(s.accessToken, s.username, s.pin, v) }
    }

    /** Runs [block] with this brand's session, refreshing the token once on 401/403. */
    private suspend fun <T> withSession(block: suspend (SessionStore.Session) -> T): T {
        val session = store.load(brand) ?: throw BlueLinkException("Not logged in")
        return try {
            block(session)
        } catch (e: BlueLinkException) {
            val refreshToken = session.refreshToken
            val isAuth = e.code == 401 || e.code == 403
            if (isAuth && refreshToken != null) {
                val refreshed = api.refresh(refreshToken)
                store.updateAccessToken(brand, refreshed.accessToken, refreshed.refreshToken)
                val updated = store.load(brand) ?: throw e
                block(updated)
            } else {
                throw e
            }
        }
    }
}
