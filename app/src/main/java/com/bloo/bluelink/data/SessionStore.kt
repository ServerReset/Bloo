package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "bloo_session")

/**
 * Persists the Blue Link session (tokens + credentials needed for commands).
 * The service PIN is required as a header on every remote command, so it is
 * stored locally on-device only.
 */
class SessionStore(private val context: Context) {

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val USERNAME = stringPreferencesKey("username")
        val PIN = stringPreferencesKey("pin")
        val BRAND = stringPreferencesKey("brand")
    }

    data class Session(
        val accessToken: String,
        val refreshToken: String?,
        val username: String,
        val pin: String,
        val brand: Brand = Brand.HYUNDAI,
    )

    suspend fun save(session: Session) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS] = session.accessToken
            session.refreshToken?.let { prefs[Keys.REFRESH] = it }
            prefs[Keys.USERNAME] = session.username
            prefs[Keys.PIN] = session.pin
            prefs[Keys.BRAND] = session.brand.name
        }
    }

    suspend fun load(): Session? {
        val prefs = context.dataStore.data.first()
        val access = prefs[Keys.ACCESS] ?: return null
        val username = prefs[Keys.USERNAME] ?: return null
        val pin = prefs[Keys.PIN] ?: return null
        return Session(access, prefs[Keys.REFRESH], username, pin, Brand.fromName(prefs[Keys.BRAND]))
    }

    suspend fun updateAccessToken(access: String, refresh: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS] = access
            refresh?.let { prefs[Keys.REFRESH] = it }
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    // --- Per-vehicle preferences ----------------------------------------
    // The US API exposes no flag for whether seats can cool (ventilate), only
    // whether a seat heater/vent exists at all. Cooling capability is therefore
    // stored as a per-car preference the owner can set.

    suspend fun ventilatedSeats(vin: String): Boolean =
        context.dataStore.data.first()[booleanPreferencesKey("vent_$vin")] ?: false

    suspend fun setVentilatedSeats(vin: String, value: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("vent_$vin")] = value }
    }

    val isLoggedIn = context.dataStore.data.map { it[Keys.ACCESS] != null }
}
