package com.bloo.bluelink.data

/**
 * [VehicleRepository] for Kia US (Kia Connect). Differences from the
 * Hyundai-shaped [BlueLinkRepository] it mirrors:
 *  - Sign-in may require a one-time code (email/SMS); see [startLogin] /
 *    [sendOtp] / [verifyOtp]. A successful login stores the session id plus an
 *    "rmtoken" bound to a stable device id, which lets us re-authenticate
 *    silently when the session expires.
 *  - Commands are keyed by a per-session "vinkey" rather than a service PIN,
 *    so vinkeys are re-fetched after every re-authentication.
 */
class KiaRepository(
    private val api: KiaUsaApi,
    private val store: SessionStore,
    private val credentialStore: CredentialStore,
) : VehicleRepository {

    /** Session-specific vinkeys + EV flags, keyed by vehicle id (our [Vehicle.vin]).
     *  This one repository instance is cached and reused across the app's
     *  lifetime (see AppViewModel/WearViewModel's `repos` map), and not every
     *  call path that reads/mutates it runs under the same lock (e.g. a
     *  background garage load can race a user-triggered command) -- a plain
     *  HashMap risked a ConcurrentModificationException under that race. */
    private val summaries = java.util.concurrent.ConcurrentHashMap<String, KiaVehicleSummary>()

    /** Device id carried between the password step and the OTP steps. */
    private var pendingDeviceId: String? = null

    // --- Sign-in (OTP) -----------------------------------------------------

    /**
     * Step 1: authenticate with username/password. Returns [KiaAuth.LoggedIn]
     * (session saved, done) or [KiaAuth.OtpRequired] (caller must pick a
     * destination, then [sendOtp] and [verifyOtp]).
     */
    suspend fun startLogin(username: String, password: String, pin: String): KiaAuth {
        // Reuse the stored device id so a previously-issued rmtoken stays valid.
        val deviceId = store.load(Brand.KIA)?.deviceId ?: KiaUsaApi.newDeviceId()
        pendingDeviceId = deviceId
        val rmtoken = store.load(Brand.KIA)?.refreshToken
        val auth = api.authUser(username, password, deviceId, rmtoken, pin.ifBlank { null })
        if (auth is KiaAuth.LoggedIn) save(auth.session, username, pin)
        return auth
    }

    /** Step 2a: deliver the one-time code ("EMAIL" or "SMS"). */
    suspend fun sendOtp(challenge: KiaAuth.OtpRequired, notifyType: String) {
        val deviceId = pendingDeviceId ?: KiaUsaApi.newDeviceId().also { pendingDeviceId = it }
        api.sendOtp(challenge.otpKey, notifyType, challenge.xid, deviceId)
    }

    /** Step 2b: verify the code, complete the login and save the session. */
    suspend fun verifyOtp(username: String, password: String, pin: String, code: String, challenge: KiaAuth.OtpRequired) {
        val deviceId = pendingDeviceId ?: KiaUsaApi.newDeviceId()
        val session = api.verifyOtpAndComplete(
            username, password, code, challenge.otpKey, challenge.xid, deviceId, pin.ifBlank { null },
        )
        save(session, username, pin)
        pendingDeviceId = null
    }

    // Persists a freshly-obtained Kia session (sid + rmtoken + deviceId) as a
    // generic SessionStore.Session tagged with Brand.KIA, so it round-trips through
    // the same store used by every other brand; toKia() below does the reverse
    // conversion when reading it back out.
    private suspend fun save(session: KiaSession, username: String, pin: String) {
        store.save(
            SessionStore.Session(
                accessToken = session.sid,
                refreshToken = session.rmtoken,
                username = username,
                pin = pin,
                brand = Brand.KIA,
                deviceId = session.deviceId,
            )
        )
    }

    // Drops the in-memory vinkey cache (it's meaningless without a session) before
    // clearing the persisted session, so a subsequent login starts with a clean slate.
    override suspend fun logout() {
        summaries.clear()
        store.clear(Brand.KIA)
    }

    // --- Vehicles / status ---------------------------------------------------

    // Every vehicles() call re-fetches summaries from the API (via fetchSummaries,
    // which also repopulates the `summaries` cache) rather than reading the cache,
    // so the returned list always reflects the account's current garage.
    override suspend fun vehicles(): List<Vehicle> = withSession { s ->
        fetchSummaries(s).map { it.toVehicle() }
    }

    // When `refresh` is requested, wakes the car with forceRefresh() first (a
    // separate API call that tells the car to phone home with live data) and only
    // then reads status(); without `refresh`, just reads whatever the server last
    // cached, avoiding the extra wake call for lightweight/background polls.
    override suspend fun status(v: Vehicle, refresh: Boolean): VehicleStatus? = withSession { s ->
        val summary = summaryFor(s, v)
        if (refresh) api.forceRefresh(s, summary)
        api.status(s, summary)
    }

    override suspend fun location(v: Vehicle): GeoLocation? = withSession { s ->
        // Kia has no separate location endpoint; GPS rides along with status.
        val coord = api.status(s, summaryFor(s, v))?.vehicleLocation?.coord
        val lat = coord?.lat
        val lon = coord?.lon
        if (lat != null && lon != null) GeoLocation(lat, lon) else null
    }

    // --- Commands ------------------------------------------------------------

    override suspend fun lock(v: Vehicle) = withSession { s -> api.lock(s, summaryFor(s, v)) }

    override suspend fun unlock(v: Vehicle) = withSession { s -> api.unlock(s, summaryFor(s, v)) }

    override suspend fun startClimate(v: Vehicle, req: ClimateRequest) =
        withSession { s -> api.startClimate(s, summaryFor(s, v), req) }

    override suspend fun stopClimate(v: Vehicle) = withSession { s -> api.stopClimate(s, summaryFor(s, v)) }

    override suspend fun setChargeTargets(v: Vehicle, acPercent: Int, dcPercent: Int) =
        withSession { s -> api.setChargeTargets(s, summaryFor(s, v), acPercent, dcPercent) }

    override suspend fun startCharge(v: Vehicle) = withSession { s -> api.startCharge(s, summaryFor(s, v)) }

    override suspend fun stopCharge(v: Vehicle) = withSession { s -> api.stopCharge(s, summaryFor(s, v)) }

    // --- Plumbing --------------------------------------------------------

    // Maps a Kia-specific vehicle summary onto the shared cross-brand Vehicle model;
    // `generation` is left blank since Kia's API doesn't expose a head-unit
    // generation (see Vehicle.supportsConnectedStore in Brand.kt, which treats a
    // blank generation for Kia as always-eligible rather than "unknown/old").
    private fun KiaVehicleSummary.toVehicle() = Vehicle(
        vin = id,
        regId = key,
        name = name,
        model = model,
        generation = "",
        brandIndicator = Brand.KIA.code,
        isEv = isEv,
    )

    // Fetches the account's full vehicle-summary list from the API and replaces the
    // entire `summaries` cache with it (clear, then repopulate keyed by vehicle id),
    // rather than merging — so a car removed from the account also disappears from
    // the cache instead of lingering with stale data.
    private suspend fun fetchSummaries(s: KiaSession): List<KiaVehicleSummary> =
        api.vehicles(s).also { list ->
            summaries.clear()
            list.forEach { summaries[it.id] = it }
        }

    /** The session-specific summary (vinkey) for this vehicle, fetching if absent. */
    private suspend fun summaryFor(s: KiaSession, v: Vehicle): KiaVehicleSummary =
        summaries[v.vin]
            ?: fetchSummaries(s).firstOrNull { it.id == v.vin }
            ?: throw BlueLinkException("Vehicle not found on this Kia account")

    private fun SessionStore.Session.toKia() =
        KiaSession(sid = accessToken, rmtoken = refreshToken, deviceId = deviceId ?: KiaUsaApi.newDeviceId(), pin = pin)

    /**
     * Runs [block] with the saved Kia session. On an expired session (401/403),
     * re-authenticates once with the stored rmtoken — silently, no OTP — then
     * refreshes the session-specific vinkeys and retries.
     */
    private suspend fun <T> withSession(block: suspend (KiaSession) -> T): T {
        val stored = store.load(Brand.KIA) ?: throw BlueLinkException("Not logged in")
        return try {
            block(stored.toKia())
        } catch (e: BlueLinkException) {
            if (e.code != 401 && e.code != 403) throw e
            val creds = credentialStore.load(Brand.KIA)
                ?: throw BlueLinkException("Kia session expired — please sign in again")
            AppLog.log("Kia session expired; re-authenticating with stored rmtoken")
            val auth = api.authUser(
                creds.email, creds.password, stored.deviceId ?: KiaUsaApi.newDeviceId(),
                stored.refreshToken, stored.pin.ifBlank { null },
            )
            if (auth !is KiaAuth.LoggedIn) {
                // The rmtoken itself expired; a fresh OTP round-trip is needed.
                throw BlueLinkException("Kia session expired — please sign out and sign in again")
            }
            save(auth.session, creds.email, stored.pin)
            val fresh = store.load(Brand.KIA) ?: throw e
            // vinkeys are bound to the session, so refresh them before retrying.
            fetchSummaries(fresh.toKia())
            block(fresh.toKia())
        }
    }
}
