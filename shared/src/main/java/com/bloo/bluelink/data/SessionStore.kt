package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

// A corruption handler so a file damaged by an interrupted write/power loss resets to empty prefs
// (signed out) instead of rethrowing an uncaught exception out of every read — every surface (the
// app UI, the background workers, the command runners) reads this at some point, and a crash loop
// is worse than a forced re-login.
private val Context.dataStore by safePreferencesDataStore("bloo_session")

/**
 * Persists Blue Link sessions — one per brand, so a Hyundai and a Genesis account can be signed in
 * at the same time. The service PIN is required as a header on every remote command, so it is
 * stored locally on-device only.
 */
class SessionStore(private val context: Context) {

    data class Session(
        val accessToken: String,
        val refreshToken: String?,
        val username: String,
        val pin: String,
        val brand: Brand = Brand.HYUNDAI,
        /** Kia US only: the rmtoken is bound to this device id, so it must persist. */
        val deviceId: String? = null,
        // --- Hyundai EU OneApp/CCI token set (nullable; only EU populates it) ---
        // The CCI tokens are needed to REFRESH an EU session (the CCS access token can't be
        // refreshed on its own), so they must persist alongside it. See EuApiCci.
        val cciAccessToken: String? = null,
        val exchangeableToken: String? = null,
        val exchangeableRefreshToken: String? = null,
        val nonCcsToken: String? = null,
        val nonCcsRefreshToken: String? = null,
        val idToken: String? = null,
    ) {
        companion object {
            /**
             * Build a [Session] from a brand API's own logged-in session. Taking those fields plus
             * the login `username`/`pin`/`brand` here keeps the mapping in one place.
             */
            fun of(
                accessToken: String,
                refreshToken: String?,
                deviceId: String?,
                username: String,
                pin: String,
                brand: Brand,
            ): Session = Session(
                accessToken = accessToken,
                refreshToken = refreshToken,
                username = username,
                pin = pin,
                brand = brand,
                deviceId = deviceId,
            )
        }
    }

    // Namespaces every stored field by brand, e.g. key(KIA, "access") -> "KIA_access", so each
    // brand's session fields live under distinct DataStore keys in the same file.
    private fun key(brand: Brand, field: String) = stringPreferencesKey("${brand.name}_$field")
    // Comma-joined list of brand names that currently have a saved session; DataStore Preferences
    // has no native Set<String> support here so it's hand-rolled as CSV (contrast with
    // CredentialStore, which uses putStringSet on plain SharedPreferences).
    private val brandsKey = stringPreferencesKey("brands")

    /**
     * Writes all of [session]'s fields under that brand's namespaced keys in one DataStore
     * transaction. Also adds the brand to the CSV [brandsKey] set (via a Set to dedupe) so this
     * brand shows up in [loggedInBrands].
     */
    suspend fun save(session: Session) {
        context.dataStore.edit { p ->
            p[key(session.brand, "access")] = session.accessToken
            session.refreshToken?.let { p[key(session.brand, "refresh")] = it }
            p[key(session.brand, "username")] = session.username
            p[key(session.brand, "pin")] = session.pin
            session.deviceId?.let { p[key(session.brand, "device")] = it }
            // CCI token set (EU only) -- written only when present, so a non-EU session never
            // touches these keys and an EU refresh that clears one leaves the others intact.
            session.cciAccessToken?.let { p[key(session.brand, "cciAccess")] = it }
            session.exchangeableToken?.let { p[key(session.brand, "cciExchangeable")] = it }
            session.exchangeableRefreshToken?.let { p[key(session.brand, "cciExchangeableRefresh")] = it }
            session.nonCcsToken?.let { p[key(session.brand, "cciNonCcs")] = it }
            session.nonCcsRefreshToken?.let { p[key(session.brand, "cciNonCcsRefresh")] = it }
            session.idToken?.let { p[key(session.brand, "cciId")] = it }
            val set = (p[brandsKey]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()).toMutableSet()
            set.add(session.brand.name)
            p[brandsKey] = set.joinToString(",")
        }
    }

    /**
     * Persist a just-established login from its raw fields -- the shared path the Canada and EU
     * repositories both take, since their session types expose the same token trio.
     */
    suspend fun saveLogin(
        accessToken: String,
        refreshToken: String?,
        deviceId: String?,
        username: String,
        pin: String,
        brand: Brand,
    ) = save(
        Session.of(
            accessToken = accessToken,
            refreshToken = refreshToken,
            deviceId = deviceId,
            username = username,
            pin = pin,
            brand = brand,
        ),
    )

    /**
     * Reads back one brand's session. Returns null (meaning "not logged in for this brand") if any
     * of the three required fields — access token, username, pin — is missing, rather than
     * returning a half-populated [Session].
     */
    suspend fun load(brand: Brand): Session? {
        StartupTrace.markIfStarting("SessionStore.load(${brand.name}): begin")
        val p = context.dataStore.data.first()
        val access = p[key(brand, "access")] ?: return null
        val username = p[key(brand, "username")] ?: return null
        val pin = p[key(brand, "pin")] ?: return null
        return Session(
            access, p[key(brand, "refresh")], username, pin, brand, p[key(brand, "device")],
            cciAccessToken = p[key(brand, "cciAccess")],
            exchangeableToken = p[key(brand, "cciExchangeable")],
            exchangeableRefreshToken = p[key(brand, "cciExchangeableRefresh")],
            nonCcsToken = p[key(brand, "cciNonCcs")],
            nonCcsRefreshToken = p[key(brand, "cciNonCcsRefresh")],
            idToken = p[key(brand, "cciId")],
        )
    }

    suspend fun warmUp() {
        runCatching { context.dataStore.data.first() }
    }

    suspend fun loggedInBrands(): List<Brand> {
        return context.dataStore.data.first()[brandsKey]
            ?.split(",")?.mapNotNull { runCatching { Brand.valueOf(it) }.getOrNull() } ?: emptyList()
    }

    /** Rewrites just the access/refresh tokens after a successful refresh, in one transaction. */
    suspend fun updateAccessToken(brand: Brand, access: String, refresh: String?) {
        context.dataStore.edit { p ->
            p[key(brand, "access")] = access
            refresh?.let { p[key(brand, "refresh")] = it }
        }
    }

    /** Rewrites just the stored service PIN for [brand]. */
    suspend fun updatePin(brand: Brand, pin: String) {
        context.dataStore.edit { it[key(brand, "pin")] = pin }
    }

    /**
     * Removes every namespaced field for [brand] and drops it from the CSV [brandsKey] set; if that
     * leaves the set empty, removes the key entirely rather than storing an empty string.
     */
    suspend fun clear(brand: Brand) {
        context.dataStore.edit { p ->
            listOf(
                "access", "refresh", "username", "pin", "device",
                "cciAccess", "cciExchangeable", "cciExchangeableRefresh",
                "cciNonCcs", "cciNonCcsRefresh", "cciId",
            ).forEach { p.remove(key(brand, it)) }
            val set = p[brandsKey]?.split(",")?.filter { it.isNotBlank() && it != brand.name } ?: emptyList()
            if (set.isEmpty()) p.remove(brandsKey) else p[brandsKey] = set.joinToString(",")
        }
    }
}
