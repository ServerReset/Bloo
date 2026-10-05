package com.bloo.bluelink.ui

import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.CanadaAuth
import com.bloo.bluelink.data.KiaAuth
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.EuRepository
import com.bloo.bluelink.data.maskEmail
import kotlinx.coroutines.flow.update

// --- Sign-in: brand logins, one-time-code steps, add-account (extracted from AppViewModel) --

/** Sign in (or add another account). Multiple brands can be active at once. */
fun AppViewModel.login(username: String, password: String, pin: String, brand: Brand) {
    if (username.isBlank() || password.isBlank() || (pin.isBlank() && brand.pinRequiredToSignIn)) {
        _state.update {
            it.copy(
                message = if (brand.pinRequiredToSignIn) "Email, password and PIN are all required" else "Email and password are required",
                messageType = "error",
            )
        }
        return
    }
    if (brand == Brand.KIA) {
        loginKia(username.trim(), password, pin.trim())
        return
    }
    if (brand.isCanada) {
        loginCanada(username.trim(), password, pin.trim(), brand)
        return
    }
    if (brand.isEurope) {
        loginEurope(username.trim(), password, pin.trim(), brand)
        return
    }
    launchBusy {
        (repoFor(brand) as BlueLinkRepository).login(username.trim(), password, pin.trim())
        credentialStore.save(Credentials(username.trim(), password, pin.trim(), brand))
        AppLog.log("Signed in as ${maskEmail(username.trim())} (${brand.label})")
        _state.update { it.copy(accounts = credentialStore.loadAll(), addingAccount = false) }
        loadGarageInternal()
    }
}

/**
 * Europe (Hyundai Bluelink EU) sign-in. Single-step like the Hyundai/Genesis US branch — no OTP —
 * just against [EuRepository] instead of [BlueLinkRepository]; same post-login bookkeeping (persist
 * credentials, reload the garage).
 */
private fun AppViewModel.loginEurope(username: String, password: String, pin: String, brand: Brand) {
    launchBusy {
        (repoFor(brand) as EuRepository).login(username, password, pin)
        credentialStore.save(Credentials(username, password, pin, brand))
        AppLog.log("Signed in as ${maskEmail(username)} (${brand.label})")
        _state.update { it.copy(accounts = credentialStore.loadAll(), addingAccount = false) }
        loadGarageInternal()
    }
}

/** Step 1 of Kia login: attempt sign-in with just username/password/PIN. */
private fun AppViewModel.loginKia(username: String, password: String, pin: String) {
    launchBusy {
        when (val auth = kiaRepo().startLogin(username, password, pin)) {
            is KiaAuth.LoggedIn -> {
                kiaPending = null
                finishKiaLogin(Credentials(username, password, pin, Brand.KIA))
            }
            is KiaAuth.OtpRequired -> {
                kiaPending = Credentials(username, password, pin, Brand.KIA)
                AppLog.log("Kia requires a one-time code (email: ${auth.hasEmail}, sms: ${auth.hasSms})")
                _state.update { it.copy(kiaOtp = KiaOtpUi(auth)) }
            }
        }
    }
}

/** Send the Kia one-time code to the chosen destination ("EMAIL"/"SMS"). */
fun AppViewModel.kiaSendOtp(notifyType: String) {
    val otp = _state.value.kiaOtp ?: return
    launchBusy {
        kiaRepo().sendOtp(otp.challenge, notifyType)
        AppLog.log("Kia one-time code sent via $notifyType")
        _state.update { it.copy(kiaOtp = otp.copy(sentTo = notifyType)) }
    }
}

/** Verify the Kia one-time code and finish signing in. */
fun AppViewModel.kiaVerifyOtp(code: String) {
    val otp = _state.value.kiaOtp ?: return
    val creds = kiaPending ?: return
    if (code.isBlank()) {
        _state.update { it.copy(message = "Enter the code you received", messageType = "error") }
        return
    }
    launchBusy {
        kiaRepo().verifyOtp(creds.email, creds.password, creds.pin, code.trim(), otp.challenge)
        kiaPending = null
        _state.update { it.copy(kiaOtp = null) }
        finishKiaLogin(creds)
    }
}

fun AppViewModel.kiaCancelOtp() {
    kiaPending = null
    _state.update { it.copy(kiaOtp = null) }
}

private suspend fun AppViewModel.finishKiaLogin(creds: Credentials) {
    credentialStore.save(creds)
    AppLog.log("Signed in as ${maskEmail(creds.email)} (Kia)")
    _state.update { it.copy(accounts = credentialStore.loadAll(), addingAccount = false) }
    loadGarageInternal()
}

/** Step 1 of Canada login: attempt sign-in with username/password. */
private fun AppViewModel.loginCanada(username: String, password: String, pin: String, brand: Brand) {
    launchBusy {
        when (val auth = canadaRepo(brand).startLogin(username, password, pin)) {
            is CanadaAuth.LoggedIn -> {
                canadaPending = null
                finishCanadaLogin(Credentials(username, password, pin, brand))
            }
            is CanadaAuth.OtpRequired -> {
                canadaPending = Credentials(username, password, pin, brand)
                canadaRepo(brand).sendOtp(auth)
                AppLog.log("${brand.label} requires a one-time code (email)")
                _state.update { it.copy(canadaOtp = CanadaOtpUi(auth, brand)) }
            }
        }
    }
}

/** Verify the Canada one-time code and finish signing in. */
fun AppViewModel.canadaVerifyOtp(code: String) {
    val otp = _state.value.canadaOtp ?: return
    val creds = canadaPending ?: return
    if (code.isBlank()) {
        _state.update { it.copy(message = "Enter the code you received", messageType = "error") }
        return
    }
    launchBusy {
        canadaRepo(otp.brand).verifyOtp(creds.email, creds.pin, code.trim(), otp.challenge)
        canadaPending = null
        _state.update { it.copy(canadaOtp = null) }
        finishCanadaLogin(creds)
    }
}

fun AppViewModel.canadaCancelOtp() {
    canadaPending = null
    _state.update { it.copy(canadaOtp = null) }
}

/**
 * Finish a fully-authenticated Canada login (reached from either the direct [CanadaAuth.LoggedIn]
 * branch or after [canadaVerifyOtp] succeeds) -- same shape as [finishKiaLogin].
 */
private suspend fun AppViewModel.finishCanadaLogin(creds: Credentials) {
    credentialStore.save(creds)
    AppLog.log("Signed in as ${maskEmail(creds.email)} (${creds.brand.label})")
    _state.update { it.copy(accounts = credentialStore.loadAll(), addingAccount = false) }
    loadGarageInternal()
}

/**
 * Show the login form again on top of an already-loaded garage, so the user can sign into a second
 * (or third) brand without losing the first.
 */
fun AppViewModel.beginAddAccount() = _state.update { it.copy(addingAccount = true) }

fun AppViewModel.cancelAddAccount() = _state.update { s ->
    // If the user arrived here by backing out of the biometric prompt, "Cancel" must re-lock the
    // app — not silently return them to the already-loaded garage.
    if (s.lockedToLogin) s.copy(addingAccount = false, locked = true, lockedToLogin = false)
    else s.copy(addingAccount = false)
}
