package com.bloo.bluelink.data

import com.bloo.bluelink.rethrowIfCancellation

/**
 * [VehicleRepository] for Hyundai Bluelink Europe (CCAPI / CCS2), served by [EuApi]. The shared account, control-token and session plumbing is
 * in [ControlledAccountRepository]. A dead session is first refreshed with the stored refresh token and, failing that, renewed by replaying
 * the password login (Europe has no OTP step, so this is a clean single call).
 */
class EuRepository(
    private val api: EuApi,
    store: SessionStore,
    brand: Brand,
    private val credentialStore: CredentialStore,
) : ControlledAccountRepository<EuSession, EuVehicleSummary>(store, brand) {

    // --- Sign-in -------------------------------------------------------------

    /**
     * Europe sign-in: register the device (once, to obtain the stable ccsp-device-id all authenticated calls carry), then OAuth2 with the
     * account email + password, and persist the session + credentials. No OTP.
     */
    suspend fun login(username: String, password: String, pin: String) {
        val deviceId = store.load(brand)?.deviceId ?: api.register()
        val session = api.login(username.trim(), password, deviceId, pin.ifBlank { null })
        save(session, username.trim(), pin)
    }

    private suspend fun save(session: EuSession, username: String, pin: String) {
        store.save(
            SessionStore.Session(
                accessToken = session.accessToken, refreshToken = session.refreshToken, username = username,
                pin = pin, brand = brand, deviceId = session.deviceId, cciAccessToken = session.cciAccessToken,
                exchangeableToken = session.exchangeableToken,
                exchangeableRefreshToken = session.exchangeableRefreshToken, nonCcsToken = session.nonCcsToken,
                nonCcsRefreshToken = session.nonCcsRefreshToken, idToken = session.idToken,
            ),
        )
    }

    // --- Vehicles / status ---------------------------------------------------

    override suspend fun status(v: Vehicle, refresh: Boolean): VehicleStatus? = withSession { s ->
        api.status(s, summaryFor(s, v), refresh)
    }

    override suspend fun location(v: Vehicle): GeoLocation? = withSession { s ->
        api.location(s, summaryFor(s, v))
    }

    // --- Commands ------------------------------------------------------------

    override suspend fun lock(v: Vehicle) = withCommandAuth(v) { s, summary, token -> api.lock(s, summary, token) }

    override suspend fun unlock(v: Vehicle) = withCommandAuth(v) { s, summary, token -> api.unlock(s, summary, token) }

    override suspend fun startClimate(v: Vehicle, req: ClimateRequest) =
        withCommandAuth(v) { s, summary, token -> api.startClimate(s, summary, token, req) }

    override suspend fun stopClimate(v: Vehicle) =
        withCommandAuth(v) { s, summary, token -> api.stopClimate(s, summary, token) }

    // Charge targets authenticate with the access token (not the PIN control token), so this goes through withSession rather than
    // withCommandAuth.
    override suspend fun setChargeTargets(v: Vehicle, acPercent: Int, dcPercent: Int) = withSession { s ->
        api.setChargeTargets(s, summaryFor(s, v), acPercent, dcPercent)
    }

    override suspend fun startCharge(v: Vehicle) =
        withCommandAuth(v) { s, summary, token -> api.startCharge(s, summary, token) }

    override suspend fun stopCharge(v: Vehicle) =
        withCommandAuth(v) { s, summary, token -> api.stopCharge(s, summary, token) }

    // --- Brand plumbing ------------------------------------------------------

    override fun SessionStore.Session.toSession() =
        EuSession(
            accessToken, refreshToken, deviceId ?: EuApi.newDeviceId(), pin,
            // The OneApp/CCI token set a refresh needs. It used to be dropped here (and never saved below), so every
            // token expiry fell through to a full password sign-in through the WAF-prone flow.
            cciAccessToken = cciAccessToken,
            exchangeableToken = exchangeableToken,
            exchangeableRefreshToken = exchangeableRefreshToken,
            nonCcsToken = nonCcsToken,
            nonCcsRefreshToken = nonCcsRefreshToken,
            idToken = idToken,
        )

    override suspend fun fetchVehicles(s: EuSession): List<EuVehicleSummary> = api.vehicles(s)

    override suspend fun mintControlToken(s: EuSession, summary: EuVehicleSummary, pin: String): String =
        api.controlToken(s, pin)

    /**
     * Only a genuine token or device expiry (EuApi maps those to 401) triggers a silent refresh. A bare 403 or a business error like
     * "Duplicate request" is terminal here - retrying it would just refresh or duplicate the command.
     */
    override fun isSessionFailure(e: BlueLinkException): Boolean = e.code == 401

    /** Refreshes the token, falling back to a full re-login, and saves the result in place of the expired session. */
    override suspend fun renewSession(stored: SessionStore.Session, e: BlueLinkException): EuSession {
        AppLog.log("${brand.label} session expired; refreshing")
        val refreshed = runCatching { api.refresh(stored.toSession()) }.onFailure { rethrowIfCancellation(it) }.getOrNull()
        val session = refreshed ?: reLogin() ?: throw BlueLinkException(
            "Session expired - please sign out and sign in again", code = e.code,
        )
        save(session, stored.username, stored.pin)
        clearControlTokens()
        return session
    }

    /** Last-resort silent re-auth: replay the stored-credentials password login. */
    private suspend fun reLogin(): EuSession? {
        val creds = credentialStore.load(brand) ?: return null
        val deviceId = store.load(brand)?.deviceId ?: EuApi.newDeviceId()
        return runCatching {
            api.login(creds.email, creds.password, deviceId, creds.pin)
        }.onFailure { rethrowIfCancellation(it) }.getOrNull()
    }
}
