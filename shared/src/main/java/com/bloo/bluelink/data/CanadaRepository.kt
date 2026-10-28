package com.bloo.bluelink.data

import com.bloo.bluelink.rethrowIfCancellation

/** [VehicleRepository] for Hyundai/Genesis/Kia Canada. */
class CanadaRepository(
    private val api: CanadaApi,
    private val store: SessionStore,
    private val brand: Brand,
    private val credentialStore: CredentialStore,
) : VehicleRepository {
    private val summaries = SummaryCache<CanadaVehicleSummary>()
    private val pAuths = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Device id + pending OTP state carried between the password step and the OTP steps (mirrors
     * KiaRepository's pendingDeviceId).
     */
    private var pendingDeviceId: String? = null
    private var pendingOtpKey: String? = null

    // --- Sign-in (OTP) -----------------------------------------------------

    suspend fun startLogin(username: String, password: String, pin: String): CanadaAuth {
        val deviceId = store.load(brand)?.deviceId ?: CanadaApi.newDeviceId()
        pendingDeviceId = deviceId
        val auth = api.authUser(username, password, deviceId, pin.ifBlank { null })
        if (auth is CanadaAuth.LoggedIn) save(auth.session, username, pin)
        return auth
    }

    /** Step 2a: email the one-time code. */
    suspend fun sendOtp(challenge: CanadaAuth.OtpRequired) {
        val deviceId = pendingDeviceId ?: CanadaApi.newDeviceId().also { pendingDeviceId = it }
        val email = challenge.email ?: throw BlueLinkException("No email on file to send the code to")
        pendingOtpKey = api.sendOtp(challenge.userInfoUuid, email, deviceId)
    }

    /** Step 2b: verify the code, complete the login and save the session. */
    suspend fun verifyOtp(username: String, pin: String, code: String, challenge: CanadaAuth.OtpRequired) {
        val deviceId = pendingDeviceId ?: CanadaApi.newDeviceId()
        val otpKey = pendingOtpKey ?: throw BlueLinkException("Request a new code and try again")
        val email = challenge.email ?: username
        val session = api.verifyOtpAndComplete(username, email, otpKey, code, deviceId, pin.ifBlank { null })
        save(session, username, pin)
        pendingDeviceId = null
        pendingOtpKey = null
    }

    private suspend fun save(session: CanadaSession, username: String, pin: String) {
        store.saveLogin(session.accessToken, session.refreshToken, session.deviceId, username, pin, brand)
    }

    override suspend fun logout() {
        summaries.clear()
        pAuths.clear()
        store.clear(brand)
    }

    // --- Vehicles / status ---------------------------------------------------

    override suspend fun vehicles(): List<Vehicle> = withSession { s -> fetchSummaries(s).map { it.toVehicle(brand) } }

    override suspend fun status(v: Vehicle, refresh: Boolean): VehicleStatus? = withSession { s ->
        api.status(s, summaryFor(s, v), refresh)
    }

    override suspend fun location(v: Vehicle): GeoLocation? = withCommandAuth(v) { s, summary, pAuth ->
        api.location(s, summary, pAuth)
    }

    // --- Commands ------------------------------------------------------------

    override suspend fun lock(v: Vehicle) = withCommandAuth(v) { s, summary, pAuth -> api.lock(s, summary, pAuth) }

    override suspend fun unlock(v: Vehicle) = withCommandAuth(v) { s, summary, pAuth -> api.unlock(s, summary, pAuth) }

    override suspend fun startClimate(v: Vehicle, req: ClimateRequest) =
        withCommandAuth(v) { s, summary, pAuth -> api.startClimate(s, summary, pAuth, req) }

    override suspend fun stopClimate(v: Vehicle) =
        withCommandAuth(v) { s, summary, pAuth -> api.stopClimate(s, summary, pAuth) }

    override suspend fun setChargeTargets(v: Vehicle, acPercent: Int, dcPercent: Int) =
        withCommandAuth(v) { s, summary, pAuth -> api.setChargeTargets(s, summary, pAuth, acPercent, dcPercent) }

    override suspend fun startCharge(v: Vehicle) =
        withCommandAuth(v) { s, summary, pAuth -> api.startCharge(s, summary, pAuth) }

    override suspend fun stopCharge(v: Vehicle) =
        withCommandAuth(v) { s, summary, pAuth -> api.stopCharge(s, summary, pAuth) }

    // --- Plumbing --------------------------------------------------------

    private suspend fun fetchSummaries(s: CanadaSession): List<CanadaVehicleSummary> = summaries.refresh { api.vehicles(s) }

    private suspend fun summaryFor(s: CanadaSession, v: Vehicle): CanadaVehicleSummary = summaries.require(v.vin) { api.vehicles(s) }

    private fun SessionStore.Session.toCanada() =
        CanadaSession(accessToken, refreshToken, deviceId ?: CanadaApi.newDeviceId(), pin)

    /**
     * Runs [block] with a fresh PIN-derived `pAuth` for [v], fetching it via [CanadaApi.pinAuth] if
     * not already cached, and retrying once with a freshly-fetched pAuth if the command itself 401s
     * (an expired/rotated pAuth, distinct from an expired session -- [withSession] handles that
     * case one layer down).
     */
    private suspend fun <T> withCommandAuth(
        v: Vehicle, block: suspend (CanadaSession, CanadaVehicleSummary, String) -> T,
    ): T = withSession { s ->
        val summary = summaryFor(s, v)
        val pin = s.pin.orEmpty()
        controlTokenAuth(pAuths, v.vin, mint = { api.pinAuth(s, summary.id, pin) }) { pAuth ->
            block(s, summary, pAuth)
        }
    }

    /** Runs [block] with the saved Canada session, re-logging in once with the stored credentials if it has expired. */
    private suspend fun <T> withSession(block: suspend (CanadaSession) -> T): T {
        val stored = store.load(brand) ?: throw BlueLinkException("Not logged in")
        return withReauthOnce(
            stored.toCanada(),
            isAuthFailure = { it.isUnauthorized() },
            reauthenticate = { e -> renewSession(stored, e) },
            block = block,
        )
    }

    /** A fresh session from the stored credentials, saved in place of the expired one. */
    private suspend fun renewSession(stored: SessionStore.Session, e: BlueLinkException): CanadaSession {
        val creds = credentialStore.load(brand)
            ?: throw BlueLinkException("Session expired - please sign in again", code = e.code)
        AppLog.log("${brand.label} session expired; re-authenticating silently")
        val auth = runCatching {
            api.authUser(creds.email, creds.password, stored.deviceId ?: CanadaApi.newDeviceId(), stored.pin)
        }.onFailure { rethrowIfCancellation(it) }.getOrNull()
        if (auth !is CanadaAuth.LoggedIn) {
            throw BlueLinkException("Session expired - please sign out and sign in again", code = e.code)
        }
        save(auth.session, creds.email, stored.pin)
        val fresh = store.load(brand) ?: throw e
        pAuths.clear()
        return fresh.toCanada()
    }
}
