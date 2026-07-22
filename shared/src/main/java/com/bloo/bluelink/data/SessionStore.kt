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
// exception out of every read — every surface (app, widget, tiles, watch
// bridge) reads this at some point, and a crash loop is worse than a forced
// re-login.
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
    )

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
     * Reads back one brand's session, running [migrateLegacy] first in case this is
     * the first access since an upgrade from the old single-session layout. Returns
     * null (meaning "not logged in for this brand") if any of the three required
     * fields — access token, username, pin — is missing, rather than returning a
     * half-populated [Session].
     */
    suspend fun load(brand: Brand): Session? {
        migrateLegacy()
        val p = context.dataStore.data.first()
        val access = p[key(brand, "access")] ?: return null
        val username = p[key(brand, "username")] ?: return null
        val pin = p[key(brand, "pin")] ?: return null
        return Session(access, p[key(brand, "refresh")], username, pin, brand, p[key(brand, "device")])
    }

    /**
     * Parses the CSV [brandsKey] value back into [Brand] enum values, dropping (via
     * `mapNotNull` + `runCatching`) any stored name that no longer maps to a known
     * [Brand] constant instead of throwing.
     */
    suspend fun loggedInBrands(): List<Brand> {
        migrateLegacy()
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

    /** Migrate the old single-session keys into the per-brand layout (one-shot). */
    private suspend fun migrateLegacy() {
        // Quick short-circuit so a call with nothing to migrate skips opening a
        // transaction at all. The actual migration re-reads everything from
        // the transaction's own state (`e`), not this outer snapshot -- it
        // used to copy the remaining legacy fields from this stale `p` read
        // even inside edit{}, so a second migrateLegacy() racing a concurrent
        // updateAccessToken() (SessionStore is constructed fresh in several
        // places that can run at once) could re-write a stale legacy-derived
        // token over one that was just updated.
        //
        // Mechanism: first does a cheap outer read to check whether the legacy
        // unprefixed "access_token" key still exists at all; if not, this device has
        // already been migrated (or was never on the old scheme) and nothing further
        // happens. If it does exist, opens one `edit` transaction, re-reads the legacy
        // fields from that transaction's own snapshot `e` (not the outer `p`, to avoid
        // the stale-overwrite race described above), copies each present legacy field
        // to its brand-namespaced key, seeds `brandsKey` with just that one brand
        // (single-account legacy sessions only ever had one brand), and finally
        // removes all five legacy keys so `access_token` is gone and this method
        // becomes a no-op the next time it runs.
        if (context.dataStore.data.first()[stringPreferencesKey("access_token")] == null) return
        context.dataStore.edit { e ->
            val legacyAccess = e[stringPreferencesKey("access_token")] ?: return@edit
            val brand = Brand.fromName(e[stringPreferencesKey("brand")])
            e[key(brand, "access")] = legacyAccess
            e[stringPreferencesKey("refresh_token")]?.let { e[key(brand, "refresh")] = it }
            e[stringPreferencesKey("username")]?.let { e[key(brand, "username")] = it }
            e[stringPreferencesKey("pin")]?.let { e[key(brand, "pin")] = it }
            e[brandsKey] = brand.name
            listOf("access_token", "refresh_token", "username", "pin", "brand").forEach {
                e.remove(stringPreferencesKey(it))
            }
        }
    }
}
