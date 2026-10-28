package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// The OLD plain-text session file: read once to move its contents into the encrypted vault, then
// emptied. A corruption handler so a damaged file resets instead of crash-looping every reader.
private val Context.dataStore by safePreferencesDataStore("bloo_session")

/**
 * Persists Blue Link sessions - one per brand, so a Hyundai and a Genesis account can be signed in
 * at the same time. The service PIN is required as a header on every remote command, so it is
 * stored locally on-device only, in the encrypted [SecureVault] with the credentials.
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
        val cci: CciTokens = CciTokens(),
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

    // Namespaces every stored field by brand, e.g. "session/KIA_access", in the shared vault.
    private fun key(brand: Brand, field: String) = "session/${brand.name}_$field"
    // Comma-joined list of brand names that currently have a saved session.
    private val brandsKey = "session/brands"

    /**
     * The encrypted vault, after the one-time move of the old plain-text session file into it (the
     * old file is emptied only once its contents are safely written).
     */
    private suspend fun vault(): SecureVault = withContext(Dispatchers.IO) {
        val v = CredentialStore.vaultFor(context)
        if (v.getString(IMPORTED) == null) {
            val old = runCatching { context.dataStore.data.first() }.getOrNull()
            v.edit {
                old?.asMap()?.forEach { (k, value) ->
                    (value as? String)?.let { put("session/${k.name}", it) }
                }
                put(IMPORTED, "1")
            }
            runCatching { context.dataStore.edit { it.clear() } }
        }
        v
    }

    /** Writes all of [session]'s fields under that brand's namespaced keys in one vault write. */
    suspend fun save(session: Session) {
        val v = vault()
        withContext(Dispatchers.IO) {
            v.edit {
                put(key(session.brand, "access"), session.accessToken)
                session.refreshToken?.let { put(key(session.brand, "refresh"), it) }
                put(key(session.brand, "username"), session.username)
                put(key(session.brand, "pin"), session.pin)
                session.deviceId?.let { put(key(session.brand, "device"), it) }
                // CCI token set (EU only) -- written only when present, so a non-EU session never
                // touches these keys and an EU refresh that clears one leaves the others intact.
                session.cci.cciAccessToken?.let { put(key(session.brand, "cciAccess"), it) }
                session.cci.exchangeableToken?.let { put(key(session.brand, "cciExchangeable"), it) }
                session.cci.exchangeableRefreshToken?.let { put(key(session.brand, "cciExchangeableRefresh"), it) }
                session.cci.nonCcsToken?.let { put(key(session.brand, "cciNonCcs"), it) }
                session.cci.nonCcsRefreshToken?.let { put(key(session.brand, "cciNonCcsRefresh"), it) }
                session.cci.idToken?.let { put(key(session.brand, "cciId"), it) }
                put(brandsKey, (brandNames(v) + session.brand.name).joinToString(","))
            }
        }
    }

    private fun brandNames(v: SecureVault): Set<String> =
        v.getString(brandsKey)?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

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
            accessToken = accessToken, refreshToken = refreshToken, deviceId = deviceId, username = username,
            pin = pin, brand = brand,
        ),
    )

    /**
     * Reads back one brand's session. Returns null (meaning "not logged in for this brand") if any
     * of the three required fields - access token, username, pin - is missing, rather than
     * returning a half-populated [Session].
     */
    suspend fun load(brand: Brand): Session? {
        StartupTrace.markIfStarting("SessionStore.load(${brand.name}): begin")
        val v = vault()
        val access = v.getString(key(brand, "access")) ?: return null
        val username = v.getString(key(brand, "username")) ?: return null
        val pin = v.getString(key(brand, "pin")) ?: return null
        return Session(
            access, v.getString(key(brand, "refresh")), username, pin, brand, v.getString(key(brand, "device")),
            cci = CciTokens(
                cciAccessToken = v.getString(key(brand, "cciAccess")),
                exchangeableToken = v.getString(key(brand, "cciExchangeable")),
                exchangeableRefreshToken = v.getString(key(brand, "cciExchangeableRefresh")),
                nonCcsToken = v.getString(key(brand, "cciNonCcs")),
                nonCcsRefreshToken = v.getString(key(brand, "cciNonCcsRefresh")),
                idToken = v.getString(key(brand, "cciId")),
            ),
        )
    }

    suspend fun warmUp() {
        runCatching { vault() }
    }

    suspend fun loggedInBrands(): List<Brand> =
        brandNames(vault()).mapNotNull { enumOrNull<Brand>(it) }

    /** Rewrites just the access/refresh tokens after a successful refresh, in one write. */
    suspend fun updateAccessToken(brand: Brand, access: String, refresh: String?) {
        val v = vault()
        withContext(Dispatchers.IO) {
            v.edit {
                put(key(brand, "access"), access)
                refresh?.let { put(key(brand, "refresh"), it) }
            }
        }
    }

    /** Rewrites just the stored service PIN for [brand]. */
    suspend fun updatePin(brand: Brand, pin: String) {
        val v = vault()
        withContext(Dispatchers.IO) { v.edit { put(key(brand, "pin"), pin) } }
    }

    /** Removes every namespaced field for [brand] and drops it from the brand list. */
    suspend fun clear(brand: Brand) {
        val v = vault()
        withContext(Dispatchers.IO) {
            v.edit {
                listOf(
                    "access", "refresh", "username", "pin", "device",
                    "cciAccess", "cciExchangeable", "cciExchangeableRefresh",
                    "cciNonCcs", "cciNonCcsRefresh", "cciId",
                ).forEach { remove(key(brand, it)) }
                val rest = brandNames(v) - brand.name
                if (rest.isEmpty()) remove(brandsKey) else put(brandsKey, rest.joinToString(","))
            }
        }
    }

    private companion object {
        const val IMPORTED = "legacy_session_imported"
    }
}
