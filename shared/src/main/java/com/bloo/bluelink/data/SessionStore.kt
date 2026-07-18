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

    private fun key(brand: Brand, field: String) = stringPreferencesKey("${brand.name}_$field")
    private val brandsKey = stringPreferencesKey("brands")

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

    suspend fun load(brand: Brand): Session? {
        migrateLegacy()
        val p = context.dataStore.data.first()
        val access = p[key(brand, "access")] ?: return null
        val username = p[key(brand, "username")] ?: return null
        val pin = p[key(brand, "pin")] ?: return null
        return Session(access, p[key(brand, "refresh")], username, pin, brand, p[key(brand, "device")])
    }

    suspend fun loggedInBrands(): List<Brand> {
        migrateLegacy()
        return context.dataStore.data.first()[brandsKey]
            ?.split(",")?.mapNotNull { runCatching { Brand.valueOf(it) }.getOrNull() } ?: emptyList()
    }

    suspend fun updateAccessToken(brand: Brand, access: String, refresh: String?) {
        context.dataStore.edit { p ->
            p[key(brand, "access")] = access
            refresh?.let { p[key(brand, "refresh")] = it }
        }
    }

    suspend fun updatePin(brand: Brand, pin: String) {
        context.dataStore.edit { it[key(brand, "pin")] = pin }
    }

    suspend fun clear(brand: Brand) {
        context.dataStore.edit { p ->
            listOf("access", "refresh", "username", "pin", "device").forEach { p.remove(key(brand, it)) }
            val set = p[brandsKey]?.split(",")?.filter { it.isNotBlank() && it != brand.name } ?: emptyList()
            if (set.isEmpty()) p.remove(brandsKey) else p[brandsKey] = set.joinToString(",")
        }
    }

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
