package com.bloo.bluelink.data

/**
 * [VehicleRepository] for Hyundai/Genesis/Kia Canada. Differences from
 * [KiaRepository] it mirrors:
 *  - Sign-in always requires an email one-time code (no "just retry with the
 *    password" fast path) unless this device is still within a prior login's
 *    90-day remembered-device grant.
 *  - Every command (lock/unlock/climate/charge/location) needs a PIN-derived
 *    `pAuth` token obtained per-vehicle via [CanadaApi.pinAuth] -- cached here
 *    keyed by VIN and refreshed on a 401, the same shape [summaries] already
 *    uses for vehicle-id caching.
 */
class CanadaRepository(
    private val api: CanadaApi,
    private val store: SessionStore,
    private val brand: Brand,
) : VehicleRepository {

    private val summaries = java.util.concurrent.ConcurrentHashMap<String, CanadaVehicleSummary>()
    private val pAuths = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Device id + pending OTP state carried between the password step and the
     *  OTP steps (mirrors KiaRepository's pendingDeviceId). */
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
        store.save(
            SessionStore.Session(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                username = username,
                pin = pin,
                brand = brand,
                deviceId = session.deviceId,
            ),
        )
    }

    override suspend fun logout() {
        summaries.clear()
        pAuths.clear()
        store.clear(brand)
    }

    // --- Vehicles / status ---------------------------------------------------

    override suspend fun vehicles(): List<Vehicle> = withSession { s -> fetchSummaries(s).map { it.toVehicle() } }

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

    private fun CanadaVehicleSummary.toVehicle() = Vehicle(
        vin = vin,
        regId = id,
        name = name,
        model = model,
        generation = "",
        brandIndicator = brand.code,
        isEv = isEv,
    )

    private suspend fun fetchSummaries(s: CanadaSession): List<CanadaVehicleSummary> =
        api.vehicles(s).also { list ->
            summaries.clear()
            list.forEach { summaries[it.vin] = it }
        }

    private suspend fun summaryFor(s: CanadaSession, v: Vehicle): CanadaVehicleSummary =
        summaries[v.vin]
            ?: fetchSummaries(s).firstOrNull { it.vin == v.vin }
            ?: throw BlueLinkException("Vehicle not found on this account")

    private fun SessionStore.Session.toCanada() =
        CanadaSession(accessToken, refreshToken, deviceId ?: CanadaApi.newDeviceId(), pin)

    /** Runs [block] with a fresh PIN-derived `pAuth` for [v], fetching it via
     *  [CanadaApi.pinAuth] if not already cached, and retrying once with a
     *  freshly-fetched pAuth if the command itself 401s (an expired/rotated
     *  pAuth, distinct from an expired session -- [withSession] handles that
     *  case one layer down). */
    private suspend fun <T> withCommandAuth(
        v: Vehicle, block: suspend (CanadaSession, CanadaVehicleSummary, String) -> T,
    ): T = withSession { s ->
        val summary = summaryFor(s, v)
        val pin = s.pin.orEmpty()
        val cached = pAuths[v.vin]
        try {
            val pAuth = cached ?: api.pinAuth(s, summary.id, pin).also { pAuths[v.vin] = it }
            block(s, summary, pAuth)
        } catch (e: BlueLinkException) {
            if (e.code != 401 || cached == null) throw e
            val fresh = api.pinAuth(s, summary.id, pin)
            pAuths[v.vin] = fresh
            block(s, summary, fresh)
        }
    }

    /**
     * Runs [block] with the saved Canada session. Unlike Kia US there's no
     * silent-reauth refresh token flow documented for this backend, so an
     * expired session (401/403) surfaces as a clear "sign in again" error
     * rather than attempting a retry the reference project doesn't support.
     */
    private suspend fun <T> withSession(block: suspend (CanadaSession) -> T): T {
        val stored = store.load(brand) ?: throw BlueLinkException("Not logged in")
        return try {
            block(stored.toCanada())
        } catch (e: BlueLinkException) {
            if (e.code != 401 && e.code != 403) throw e
            throw BlueLinkException("Session expired — please sign out and sign in again", code = e.code)
        }
    }
}
