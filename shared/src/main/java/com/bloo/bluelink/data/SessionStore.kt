package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// A corruption handler so a file damaged by an interrupted write/power loss
// resets to empty prefs (signed out) instead of rethrowing an uncaught
// exception out of every read — every surface (the app UI, the background
// workers, the command runners) reads this at some point, and a crash loop is
// worse than a forced re-login.
private val Context.dataStore by preferencesDataStore(
    name = "bloo_session",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Persists Blue Link sessions — one per brand, so a Hyundai and a Genesis
 * account can be signed in at the same time. The service PIN is required as a
 * header on every remote command, so it is stored locally on-device only.
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
    ) {
        companion object {
            /**
             * Build a [Session] from a brand API's own logged-in session.
             *
             * Every brand API (Blue Link, Canada, Europe, Kia US) exposes a session type
             * carrying the same access/refresh/device fields, and each repository used to
             * hand-build this six-field [Session] inline -- byte-identical copies, one per
             * brand's `save(...)`. Taking those fields plus the login `username`/`pin`/`brand`
             * here keeps the mapping in one place.
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

    // Namespaces every stored field by brand, e.g. key(KIA, "access") -> "KIA_access",
    // so each brand's session fields live under distinct DataStore keys in the same file.
    private fun key(brand: Brand, field: String) = stringPreferencesKey("${brand.name}_$field")
    // Comma-joined list of brand names that currently have a saved session; DataStore
    // Preferences has no native Set<String> support here so it's hand-rolled as CSV
    // (contrast with CredentialStore, which uses putStringSet on plain SharedPreferences).
    private val brandsKey = stringPreferencesKey("brands")

    /**
     * Writes all of [session]'s fields under that brand's namespaced keys in one
     * DataStore transaction. Optional fields (refreshToken, deviceId) are only written
     * if non-null, so an update that doesn't carry a new refresh token/device id leaves
     * the previously-stored value untouched rather than clobbering it with null. Also
     * adds the brand to the CSV [brandsKey] set (via a Set to dedupe) so this brand
     * shows up in [loggedInBrands].
     */
    suspend fun save(session: Session) {
        context.dataStore.edit { p ->
            p[key(session.brand, "access")] = session.accessToken
            session.refreshToken?.let { p[key(session.brand, "refresh")] = it }
            p[key(session.brand, "username")] = session.username
            p[key(session.brand, "pin")] = session.pin
            session.deviceId?.let { p[key(session.brand, "device")] = it }
            val set = (p[brandsKey]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()).toMutableSet()
            set.add(session.brand.name)
            p[brandsKey] = set.joinToString(",")
        }
    }

    /**
     * Reads back one brand's session. Returns
     * null (meaning "not logged in for this brand") if any of the three required
     * fields — access token, username, pin — is missing, rather than returning a
     * half-populated [Session].
     */
    suspend fun load(brand: Brand): Session? {
        StartupTrace.markIfStarting("SessionStore.load(${brand.name}): begin")
        val p = context.dataStore.data.first()
        val access = p[key(brand, "access")] ?: return null
        val username = p[key(brand, "username")] ?: return null
        val pin = p[key(brand, "pin")] ?: return null
        return Session(access, p[key(brand, "refresh")], username, pin, brand, p[key(brand, "device")])
    }

    /**
     * Force this store's first DataStore read now -- same file-open + protobuf-parse cost as
     * [SettingsStore.warmUp], moved onto the startup warm-up thread. [load] is the first thing
     * the cold-start auto-login does after the cached garage, so paying the file-open here takes
     * that disk read out of the garage path entirely.
     */
    suspend fun warmUp() {
        runCatching { context.dataStore.data.first() }
    }

    /**
     * Parses the CSV [brandsKey] value back into [Brand] enum values, dropping (via
     * `mapNotNull` + `runCatching`) any stored name that no longer maps to a known
     * [Brand] constant instead of throwing.
     */
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
     * Removes every namespaced field for [brand] and drops it from the CSV
     * [brandsKey] set; if that leaves the set empty, removes the key entirely rather
     * than storing an empty string.
     */
    suspend fun clear(brand: Brand) {
        context.dataStore.edit { p ->
            listOf("access", "refresh", "username", "pin", "device").forEach { p.remove(key(brand, it)) }
            val set = p[brandsKey]?.split(",")?.filter { it.isNotBlank() && it != brand.name } ?: emptyList()
            if (set.isEmpty()) p.remove(brandsKey) else p[brandsKey] = set.joinToString(",")
        }
    }

    /** Wipes the entire session DataStore — every brand signed out. */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
