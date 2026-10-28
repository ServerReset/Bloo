package com.bloo.bluelink.data

// --- Kia US auth-result types. The session and vehicle types are the shared ControlSession and ControlVehicle. ---

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
