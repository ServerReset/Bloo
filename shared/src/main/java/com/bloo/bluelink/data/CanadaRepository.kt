package com.bloo.bluelink.data

import com.bloo.bluelink.rethrowIfCancellation

/** [VehicleRepository] for Hyundai/Genesis/Kia Canada. The shared account, control-token and session plumbing is in [ControlledAccountRepository]. */
class CanadaRepository(
    private val api: CanadaApi,
    store: SessionStore,
    brand: Brand,
    private val credentialStore: CredentialStore,
) : ControlledAccountRepository<CanadaSession, CanadaVehicleSummary>(store, brand) {
    /**
     * Device id + pending OTP state carried between the password step and the OTP steps (mirrors
     * KiaRepository's pendingDeviceId).
     */
    private var pendingDeviceId: String? = null
    private var pendingOtpKey: String? = null

    // --- Sign-in (OTP) -----------------------------------------------------

    suspend fun startLogin(username: String, password: String, pin: String): CanadaAuth {
        val deviceId = store.load(brand)?.deviceId ?: ApiHttp.newDeviceId()
        pendingDeviceId = deviceId
        val auth = api.authUser(username, password, deviceId, pin.ifBlank { null })
        if (auth is CanadaAuth.LoggedIn) save(auth.session, username, pin)
        return auth
    }

    /** Step 2a: email the one-time code. */
    suspend fun sendOtp(challenge: CanadaAuth.OtpRequired) {
        val deviceId = pendingDeviceId ?: ApiHttp.newDeviceId().also { pendingDeviceId = it }
        val email = challenge.email ?: throw BlueLinkException("No email on file to send the code to")
        pendingOtpKey = api.sendOtp(challenge.userInfoUuid, email, deviceId)
    }

    /** Step 2b: verify the code, complete the login and save the session. */
    suspend fun verifyOtp(username: String, pin: String, code: String, challenge: CanadaAuth.OtpRequired) {
        val deviceId = pendingDeviceId ?: ApiHttp.newDeviceId()
        val otpKey = pendingOtpKey ?: throw BlueLinkException("Request a new code and try again")
        val email = challenge.email ?: username
        val session = api.verifyOtpAndComplete(username, email, otpKey, code, deviceId, pin.ifBlank { null })
        save(session, username, pin)
        pendingDeviceId = null
        pendingOtpKey = null
    }

    private suspend fun save(session: CanadaSession, username: String, pin: String) {
        store.saveLogin(session.token, session.refresh, session.deviceId, username, pin, brand)
    }

    // --- Vehicles / status ---------------------------------------------------

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

    // --- Brand plumbing ------------------------------------------------------

    override fun SessionStore.Session.toSession() =
        CanadaSession(accessToken, refreshToken, deviceId ?: ApiHttp.newDeviceId(), pin)

    override suspend fun fetchVehicles(s: CanadaSession): List<CanadaVehicleSummary> = api.vehicles(s)

    override suspend fun mintControlToken(s: CanadaSession, summary: CanadaVehicleSummary, pin: String): String =
        api.pinAuth(s, summary.id, pin)

    /** The account's paid charging sessions for this car between [start] and [end] (yyyyMMdd), newest first. */
    suspend fun chargeOrders(v: Vehicle, start: String, end: String): List<ChargeOrder> = withSession { s ->
        val profile = credentialStore.load(brand)?.email
            ?: throw BlueLinkException("Sign in again to see charging history")
        api.chargeOrders(s, summaryFor(s, v).id, profile, start, end)
    }

    /** Public chargers within [radiusKm] of the car's position, nearest first. */
    suspend fun chargingStations(v: Vehicle, lat: Double, lon: Double, radiusKm: Int): List<ChargingStation> = withSession { s ->
        api.chargingStations(s, summaryFor(s, v).id, lat, lon, radiusKm)
    }

    /** The price and payment details for charging at [chargerId] of station [poiId]. Reads only: nothing is charged. */
    suspend fun chargeOffer(v: Vehicle, poiId: String, chargerId: String): ChargeOffer = withSession { s ->
        api.chargeOffer(s, summaryFor(s, v).id, poiId, chargerId)
    }

    /** Starts a paid charge, which spends money. Returns null when Bluelink accepted it, or its reason. */
    suspend fun startCharge(v: Vehicle, paymentId: String, poiId: String, payCardId: String?): String? = withSession { s ->
        api.startCharge(s, summaryFor(s, v).id, paymentId, poiId, payCardId)
    }

    /** Stops a paid charge. Returns null when Bluelink accepted it, or its reason. */
    suspend fun stopCharge(v: Vehicle, paymentId: String, poiId: String): String? = withSession { s ->
        api.stopCharge(s, summaryFor(s, v).id, paymentId, poiId)
    }

    /** The live numbers for a charge in progress. */
    suspend fun chargeStatus(v: Vehicle, paymentId: String): ChargeStatus = withSession { s ->
        val summary = summaryFor(s, v)
        api.chargeStatus(s, summary.id, summary.vin, paymentId)
    }

    /** A route from the car's position to ([toLat], [toLon]) with its charging stops (iac/rp). Reads only: nothing is started. */
    suspend fun tripPlan(v: Vehicle, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, chargerSoc: Int, destinationSoc: Int): TripPlan =
        withSession { s ->
            val summary = summaryFor(s, v)
            val vendor = api.chargeVendorNum(s, summary.id, summary.model, summary.year)
            api.tripPlan(s, summary.id, summary.vin, fromLat, fromLon, toLat, toLon, chargerSoc, destinationSoc, vendor)
        }

    /** The Bluelink Store's token for this car. It needs only the session, not the car's PIN, so it does not go through [withCommandAuth]. */
    suspend fun storeAccess(v: Vehicle): StoreAccess = withSession { s -> api.storeAccess(s, summaryFor(s, v).id) }

    /** A 401 or 403 means the Canada session has expired. */
    override fun isSessionFailure(e: BlueLinkException, session: SessionStore.Session): Boolean = e.isUnauthorized()

    /**
     * A fresh session from the stored credentials, saved in place of the expired one. The control tokens were minted for the dead session,
     * so they go too.
     */
    override suspend fun renewSession(stored: SessionStore.Session, e: BlueLinkException): CanadaSession {
        val creds = credentialStore.load(brand)
            ?: throw BlueLinkException("Session expired - please sign in again", code = e.code)
        AppLog.log("${brand.label} session expired; re-authenticating silently")
        val auth = runCatching {
            api.authUser(creds.email, creds.password, stored.deviceId ?: ApiHttp.newDeviceId(), stored.pin)
        }.onFailure { rethrowIfCancellation(it) }.getOrNull()
        if (auth !is CanadaAuth.LoggedIn) {
            throw BlueLinkException("Session expired - please sign out and sign in again", code = e.code)
        }
        save(auth.session, creds.email, stored.pin)
        val fresh = store.load(brand) ?: throw e
        clearControlTokens()
        return fresh.toSession()
    }
}
