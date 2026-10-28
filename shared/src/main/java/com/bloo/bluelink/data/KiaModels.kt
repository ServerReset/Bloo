package com.bloo.bluelink.data

// --- Kia US session, vehicle summary and the auth-result types ---

/**
 * A Kia US session. The [sid] is the session token (sent as the `sid` header);
 * [rmtoken] lets us re-authenticate silently (skipping the OTP step); [deviceId]
 * must stay stable across logins (the rmtoken is bound to it).
 */
data class KiaSession(
    val sid: String,
    val rmtoken: String?,
    val deviceId: String,
    override val pin: String?,
) : PinnedSession

/** A Kia US vehicle. [key] (the "vinkey") is session-specific and refreshed on login. */
data class KiaVehicleSummary(
    override val id: String,
    override val name: String,
    override val model: String,
    val key: String,
    override val isEv: Boolean,
    override val colour: String? = null,
    override val trim: String? = null,
) : AccountVehicle {
    /** Kia keys its cars by vehicle id, which is the VIN. */
    override val vin: String get() = id
}

/** Outcome of a Kia US login: a ready session, or an OTP challenge to solve. */
sealed interface KiaAuth {
    data class LoggedIn(val session: KiaSession) : KiaAuth
    data class OtpRequired(
        val otpKey: String,
        val xid: String,
        val email: String?,
        val sms: String?,
        val hasEmail: Boolean,
        val hasSms: Boolean,
    ) : KiaAuth
}
