package com.bloo.bluelink.data

/**
 * [VehicleRepository] for Hyundai Bluelink Europe (CCAPI / CCS2), served by
 * [EuApi]. Structurally identical to [CanadaRepository]:
 *  - Vehicle summaries are cached by VIN and refreshed on demand.
 *  - Every command needs a PIN-derived control token ([EuApi.controlToken]),
 *    cached here per VIN and re-fetched on a 401 (the EU analogue of Canada's
 *    `pAuth`).
 *  - A dead session (401/403) is re-authenticated silently: first with the
 *    stored refresh token, then, if that fails, by replaying the password login
 *    (Europe has no OTP step, so this is a clean single call).
 */
class EuRepository(
    private val api: EuApi,
    private val store: SessionStore,
    private val brand: Brand,
    private val credentialStore: CredentialStore,
) : VehicleRepository {

    private val summaries = java.util.concurrent.ConcurrentHashMap<String, EuVehicleSummary>()
    private val controlTokens = java.util.concurrent.ConcurrentHashMap<String, String>()

    // --- Sign-in -------------------------------------------------------------

    /**
     * Europe sign-in: register the device (once, to obtain the stable
     * ccsp-device-id all authenticated calls carry), then OAuth2 with the
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
        controlTokens.clear()
        store.clear(brand)
    }

    // --- Vehicles / status ---------------------------------------------------

    override suspend fun vehicles(): List<Vehicle> = withSession { s -> fetchSummaries(s).map { it.toVehicle() } }

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

    // Charge targets authenticate with the access token (not the PIN control
    // token), so this goes through withSession rather than withCommandAuth.
    override suspend fun setChargeTargets(v: Vehicle, acPercent: Int, dcPercent: Int) = withSession { s ->
        api.setChargeTargets(s, summaryFor(s, v), acPercent, dcPercent)
    }

    override suspend fun startCharge(v: Vehicle) =
        withCommandAuth(v) { s, summary, token -> api.startCharge(s, summary, token) }

    override suspend fun stopCharge(v: Vehicle) =
        withCommandAuth(v) { s, summary, token -> api.stopCharge(s, summary, token) }

    // --- Plumbing ------------------------------------------------------------

    private fun EuVehicleSummary.toVehicle() = Vehicle(
        vin = vin,
        regId = id,
        name = name,
        model = model,
        // CCS2 EU cars are all modern head units; no Gen5W concept applies (see
        // Brand.isGen5W, which already excludes Europe).
        generation = "",
        brandIndicator = brand.code,
        isEv = isEv,
    )

    private suspend fun fetchSummaries(s: EuSession): List<EuVehicleSummary> =
        api.vehicles(s).also { list ->
            summaries.clear()
            list.forEach { summaries[it.vin] = it }
        }

    private suspend fun summaryFor(s: EuSession, v: Vehicle): EuVehicleSummary =
        summaries[v.vin]
            ?: fetchSummaries(s).firstOrNull { it.vin == v.vin }
            ?: throw BlueLinkException("Vehicle not found on this account")

    private fun SessionStore.Session.toEu() =
        EuSession(accessToken, refreshToken, deviceId ?: EuApi.newDeviceId(), pin)

    /** Runs [block] with a fresh control token for [v], fetching it via
     *  [EuApi.controlToken] if not cached, and retrying once with a freshly
     *  minted token if the command itself 401s (an expired control token,
     *  distinct from an expired session — [withSession] handles that below). */
    private suspend fun <T> withCommandAuth(
        v: Vehicle, block: suspend (EuSession, EuVehicleSummary, String) -> T,
    ): T = withSession { s ->
        val summary = summaryFor(s, v)
        val pin = s.pin.orEmpty()
        val cached = controlTokens[v.vin]
        try {
            val token = cached ?: api.controlToken(s, pin).also { controlTokens[v.vin] = it }
            block(s, summary, token)
        } catch (e: BlueLinkException) {
            if (e.code != 401 || cached == null) throw e
            val fresh = api.controlToken(s, pin)
            controlTokens[v.vin] = fresh
            block(s, summary, fresh)
        }
    }

    /**
     * Runs [block] with the saved Europe session. On a 401/403, refreshes the
     * access token via the stored refresh token (a cheap, password-free exchange
     * the CCAPI supports — unlike Canada) and retries once. If the refresh itself
     * fails, falls back to replaying the stored-credentials password login before
     * giving up with "sign in again". Control tokens are dropped on reauth since
     * they were bound to the now-dead session.
     */
    private suspend fun <T> withSession(block: suspend (EuSession) -> T): T {
        val stored = store.load(brand) ?: throw BlueLinkException("Not logged in")
        return try {
            block(stored.toEu())
        } catch (e: BlueLinkException) {
            // Only a genuine token/device expiry (EuApi maps those to 401) triggers
            // a silent refresh. A bare 403 or a business error like "Duplicate
            // request" is terminal here — retrying it would just refresh/duplicate.
            if (e.code != 401) throw e
            AppLog.log("${brand.label} session expired; refreshing")
            val refreshed = runCatching { api.refresh(stored.toEu()) }.getOrNull()
            val session = refreshed ?: reLogin() ?: throw BlueLinkException(
                "Session expired — please sign out and sign in again", code = e.code,
            )
            save(session, stored.username, stored.pin)
            controlTokens.clear()
            block(session)
        }
    }

    /** Last-resort silent re-auth: replay the stored-credentials password login. */
    private suspend fun reLogin(): EuSession? {
        val creds = credentialStore.load(brand) ?: return null
        val deviceId = store.load(brand)?.deviceId ?: EuApi.newDeviceId()
        return runCatching {
            api.login(creds.email, creds.password, deviceId, creds.pin)
        }.getOrNull()
    }
}
