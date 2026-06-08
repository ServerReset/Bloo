package com.bloo.bluelink.data

/**
 * Coordinates the API client with the persisted session, retrying once on an
 * auth failure by refreshing the access token. All data is live from Hyundai.
 */
class BlueLinkRepository(
    private val api: BlueLinkApi,
    private val store: SessionStore,
) {

    suspend fun login(brand: Brand, username: String, password: String, pin: String) {
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

    suspend fun logout() = store.clear()

    suspend fun vehicles(): List<Vehicle> = withSession { s ->
        api.vehicles(s.accessToken, s.username)
    }

    suspend fun status(v: Vehicle, refresh: Boolean): VehicleStatus? = withSession { s ->
        api.status(s.accessToken, s.username, s.pin, v, refresh)
    }

    suspend fun location(v: Vehicle): GeoLocation? = withSession { s ->
        api.location(s.accessToken, s.username, s.pin, v)
    }

    suspend fun lock(v: Vehicle) = withSession { s -> api.lock(s.accessToken, s.username, s.pin, v) }

    suspend fun unlock(v: Vehicle) = withSession { s -> api.unlock(s.accessToken, s.username, s.pin, v) }

    suspend fun startClimate(v: Vehicle, req: ClimateRequest) =
        withSession { s -> api.startClimate(s.accessToken, s.username, s.pin, v, req) }

    suspend fun stopClimate(v: Vehicle) =
        withSession { s -> api.stopClimate(s.accessToken, s.username, s.pin, v) }

    suspend fun setChargeTargets(v: Vehicle, acPercent: Int, dcPercent: Int) =
        withSession { s -> api.setChargeTargets(s.accessToken, s.username, s.pin, v, acPercent, dcPercent) }

    suspend fun startCharge(v: Vehicle) = withSession { s -> api.startCharge(s.accessToken, s.username, s.pin, v) }

    suspend fun stopCharge(v: Vehicle) = withSession { s -> api.stopCharge(s.accessToken, s.username, s.pin, v) }

    /** Runs [block] with the current session, refreshing the token once on 401/403. */
    private suspend fun <T> withSession(block: suspend (SessionStore.Session) -> T): T {
        val session = store.load() ?: throw BlueLinkException("Not logged in")
        return try {
            block(session)
        } catch (e: BlueLinkException) {
            val refreshToken = session.refreshToken
            val isAuth = e.code == 401 || e.code == 403
            if (isAuth && refreshToken != null) {
                val refreshed = api.refresh(refreshToken)
                store.updateAccessToken(refreshed.accessToken, refreshed.refreshToken)
                val updated = store.load() ?: throw e
                block(updated)
            } else {
                throw e
            }
        }
    }
}
