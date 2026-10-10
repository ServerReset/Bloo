package com.bloo.bluelink.data

import com.bloo.bluelink.data.EuApi.Companion.sharedClient
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.serialization.json.JsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Hyundai EU's OneApp/CCI sign-in, the flow that replaces the legacy IDPConnect one. */
private const val MOZILLA_UA =
    "Mozilla/5.0 (Linux; Android 4.1.1; Galaxy Nexus Build/JRO03C) AppleWebKit/535.19 " +
        "(KHTML, like Gecko) Chrome/18.0.1025.166 Mobile Safari/535.19"

/** CCI/OneApp sign-in (Hyundai EU): the flow that bypasses the IDPConnect WAF. */
internal suspend fun EuApi.loginCci(
    username: String,
    password: String,
    deviceId: String,
    pin: String?,
): EuSession {
    // One cookie jar across the handshake; two clients over it that differ only in
    // redirect-following (signin must NOT follow, so its 302 Location carrying the code is
    // readable).
    val store = mutableListOf<Cookie>()
    val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            store.removeAll { e -> cookies.any { it.name == e.name } }
            store.addAll(cookies)
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = store.toList()
    }
    val follow = sharedClient.newBuilder().cookieJar(jar).followRedirects(true).build()
    val noFollow = sharedClient.newBuilder().cookieJar(jar).followRedirects(false).build()

    // The OneApp authorize expects the mobile UA with the _CCS_APP_AOS suffix.
    val mobileUa = MOZILLA_UA + "_CCS_APP_AOS"
    fun idp(url: String) = Request.Builder().url(url).header("User-Agent", mobileUa)

    // 1. authorize with the OneApp client_id (passes the WAF).
    val authorizeUrl = "$loginFormHost/auth/api/v2/user/oauth2/authorize" +
        "?response_type=code&client_id=$oneAppClientId&redirect_uri=$oneAppRedirectUri" +
        "&lang=en&state=ccsp&country=${euLoginCountry()}"
    follow.newCall(idp(authorizeUrl).get().build()).execute().use { resp ->
        val body = runCatching { resp.body.string() }.getOrDefault("")
        if (body.contains("abusing", ignoreCase = true)) {
            throw BlueLinkException(
                "Europe sign-in was blocked by Hyundai's WAF. This is a server-side block, " +
                    "not your credentials — try again later.",
            )
        }
    }

    // 2. RSA public key (JWK), same as the legacy flow.
    val certRoot = call(idp("$loginFormHost/auth/api/v1/accounts/certs").get().build(), follow)
    val jwk = certRoot.path("retValue") as? JsonObject
        ?: throw BlueLinkException("Europe sign-in: could not fetch the login key")
    val kid = jwk.path("kid").str().orEmpty()
    val encryptedPw = rsaEncryptHex(
        password,
        jwk.path("n").str() ?: throw BlueLinkException("Europe sign-in: bad login key"),
        jwk.path("e").str() ?: throw BlueLinkException("Europe sign-in: bad login key"),
    )

    // 3. signin (form POST, do NOT follow) — pull `code` from the 302 Location.
    val signinForm = FormBody.Builder()
        .add("client_id", oneAppClientId)
        .add("encryptedPassword", "true")
        .add("password", encryptedPw)
        .add("redirect_uri", oneAppRedirectUri)
        .add("scope", "")
        .add("nonce", "")
        .add("state", "ccsp")
        .add("username", username)
        .add("connector_session_key", "")
        .add("kid", kid)
        .add("_csrf", "")
        .build()
    val location = noFollow.newCall(
        idp("$loginFormHost/auth/account/signin").post(signinForm).build(),
    ).execute().use { resp ->
        if (resp.code != 302) {
            throw BlueLinkException(
                "Europe sign-in failed (HTTP ${resp.code}) — check your Bluelink email and password",
                code = resp.code,
            )
        }
        resp.header("location").orEmpty()
    }
    val code = Regex("[?&]code=([^&]+)").find(location)?.groupValues?.get(1)
        ?: throw BlueLinkException(
            if (location.contains("authorization", true)) {
                "Bluelink needs a one-time consent in the official app/website first, then try again."
            } else {
                "Europe sign-in was rejected — check your Bluelink email and password."
            },
        )

    // 4. exchange the code at the CCI API for the CCI token set.
    val cci = cciPost(
        service = "v1/auth/token",
        body = null,
        query = "code=$code",
        deviceId = deviceId,
    )
    val cciAccess = cci.path("accessToken").str().orEmpty()
    val nonCcs = cci.path("nonCcsToken").str().orEmpty()
    val exchangeable = cci.path("exchangeableAccessToken").str().orEmpty()
    if (cciAccess.isBlank()) {
        throw BlueLinkException("Europe sign-in: CCI token exchange returned no access token")
    }

    // 5. exchange the CCI token for a CCS token (usable on the legacy ccapi:8080 endpoints).
    val ccs = ccsExchange(deviceId, cciAccess, nonCcs, exchangeable)

    return EuSession(
        // Bare token: EuApi.authHeaders / controlToken prepend "Bearer " themselves. Storing
        // "Bearer $ccs" here made every authenticated header "Bearer Bearer ...", which the EU API
        // rejects with a 403 (the vehicle list was the first call to hit it).
        accessToken = ccs.removePrefix("Bearer ").trim(),
        refreshToken = cci.path("refreshToken").str(),
        deviceId = deviceId,
        pin = pin,
        cciAccessToken = cciAccess,
        exchangeableToken = exchangeable,
        exchangeableRefreshToken = cci.path("exchangeableRefreshToken").str(),
        nonCcsToken = nonCcs,
        nonCcsRefreshToken = cci.path("nonCcsRefreshToken").str(),
        idToken = cci.path("idToken").str(),
    )
}

/**
 * CCS exchange: CCI access token -> CCS token the legacy endpoints accept
 * (`token-exchange?serviceType=CCS`). Returns the bare CCS token string.
 */
internal suspend fun EuApi.ccsExchange(
    deviceId: String,
    cciAccessToken: String,
    nonCcsToken: String,
    exchangeableToken: String,
): String {
    val resp = cciPost(
        service = "v1/auth/token-exchange",
        body = null,
        query = "serviceType=CCS",
        deviceId = deviceId,
        cciAccessToken = cciAccessToken,
        nonCcsToken = nonCcsToken,
        exchangeableToken = exchangeableToken,
    )
    val token = resp.path("accessToken").str() ?: resp.path("ccsAccessToken").str()
    return token?.takeIf { it.isNotBlank() }
        ?: throw BlueLinkException("Europe sign-in: CCS token exchange returned no access token")
}

/**
 * Refresh the CCI token set and re-exchange the CCS token (`v2/auth/token-refresh`). The CCS token
 * can't be refreshed on its own; the full CCI set has to be replayed. Returns a new [EuSession]
 * with every token field updated.
 */
internal suspend fun EuApi.refreshCci(session: EuSession): EuSession {
    val deviceId = session.deviceId
    val body = buildString {
        append("{")
        append("\"accessToken\":\"").append(session.cciAccessToken.orEmpty().removePrefix("Bearer ").trim()).append("\",")
        append("\"refreshToken\":\"").append(session.refreshToken.orEmpty()).append("\",")
        append("\"exchangeableAccessToken\":\"").append(session.exchangeableToken.orEmpty()).append("\",")
        append("\"exchangeableRefreshToken\":\"").append(session.exchangeableRefreshToken.orEmpty()).append("\",")
        append("\"nonCcsToken\":\"").append(session.nonCcsToken.orEmpty()).append("\",")
        append("\"nonCcsRefreshToken\":\"").append(session.nonCcsRefreshToken.orEmpty()).append("\",")
        append("\"idToken\":\"").append(session.idToken.orEmpty()).append("\"")
        append("}")
    }
    val data = cciPost(
        service = "v2/auth/token-refresh",
        body = body,
        query = "",
        deviceId = deviceId,
        cciAccessToken = session.cciAccessToken,
        nonCcsToken = session.nonCcsToken,
        exchangeableToken = session.exchangeableToken,
        jsonBody = true,
    )
    val cciAccess = data.path("accessToken").str() ?: session.cciAccessToken.orEmpty()
    val nonCcs = data.path("nonCcsToken").str() ?: session.nonCcsToken.orEmpty()
    val exchangeable = data.path("exchangeableAccessToken").str() ?: session.exchangeableToken.orEmpty()
    val ccs = ccsExchange(deviceId, cciAccess, nonCcs, exchangeable)
    return session.copy(
        // Bare token: EuApi.authHeaders / controlToken prepend "Bearer " themselves. Storing
        // "Bearer $ccs" here made every authenticated header "Bearer Bearer ...", which the EU API
        // rejects with a 403 (the vehicle list was the first call to hit it).
        accessToken = ccs.removePrefix("Bearer ").trim(),
        refreshToken = data.path("refreshToken").str() ?: session.refreshToken,
        cciAccessToken = cciAccess,
        exchangeableToken = exchangeable,
        exchangeableRefreshToken = data.path("exchangeableRefreshToken").str() ?: session.exchangeableRefreshToken,
        nonCcsToken = nonCcs,
        nonCcsRefreshToken = data.path("nonCcsRefreshToken").str() ?: session.nonCcsRefreshToken,
        idToken = data.path("idToken").str() ?: session.idToken,
    )
}

/**
 * POST to the CCI API with the OneApp headers. [service] is the path under `/domain/api/` (e.g.
 * "v1/auth/token"). [query] is appended after '?'. [body] null sends an empty body; [jsonBody]
 * marks the body as JSON (the refresh call) rather than an empty form.
 */
internal suspend fun EuApi.cciPost(
    service: String,
    body: String?,
    query: String,
    deviceId: String,
    cciAccessToken: String? = null,
    nonCcsToken: String? = null,
    exchangeableToken: String? = null,
    jsonBody: Boolean = false,
): JsonObject {
    val builder = Request.Builder()
        .url("$cciApiUrl/domain/api/$service" + if (query.isNotBlank()) "?$query" else "")
        .post((body ?: "").toRequestBody(if (jsonBody) "application/json".toMediaType() else null))
        .header("client-id", cciPackageId)
        .header("client-name", cciClientName)
        .header("client-version", cciClientVersion)
        .header("client-os-code", "ios")
        .header("client-os-version", cciClientOsVersion)
        .header("client-device-id", deviceId)
        .header("client-device-model", "iPhone")
        .header("client-notification-provider-type", cciNotificationProvider)
        .header("locale", "EN")
        .header("timezone", cciTimezoneOffset())
        .header("Accept", "application/json")
        .header("Accept-Language", "en")
        .header("User-Agent", "okhttp/3.12.0")
    if (nonCcsToken != null) builder.header("Authentication", nonCcsToken)
    if (cciAccessToken != null) {
        builder.header("authorization", "Bearer " + cciAccessToken.removePrefix("Bearer ").trim())
    }
    if (exchangeableToken != null) {
        builder.header("exchangeable-token", exchangeableToken)
        builder.header("non-ccs-token", nonCcsToken ?: "")
    }
    return call(builder.build()) as? JsonObject
        ?: throw BlueLinkException("Europe sign-in: CCI returned a non-object response")
}

/** The current UTC offset of Europe/Berlin as "+HH:MM", which the CCI API expects. */
private fun cciTimezoneOffset(): String {
    val offset = OffsetDateTime.now(ZoneId.of("Europe/Berlin")).offset
    val id = offset.id
    return when {
        id == "Z" -> "+00:00"
        id.length == 6 -> id          // already +HH:MM
        else -> id                    // e.g. +02:00
    }
}
