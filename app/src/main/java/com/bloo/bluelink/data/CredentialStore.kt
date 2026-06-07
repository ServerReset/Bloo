package com.bloo.bluelink.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Blue Link sign-in credentials, persisted so the user need not retype them. */
data class Credentials(
    val email: String,
    val password: String,
    val pin: String,
)

/**
 * Stores credentials at rest using AES-256 via Jetpack Security
 * (EncryptedSharedPreferences). Used to remember the login and to re-auth
 * after a token expires.
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "bloo_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(credentials: Credentials) {
        prefs.edit()
            .putString(KEY_EMAIL, credentials.email)
            .putString(KEY_PASSWORD, credentials.password)
            .putString(KEY_PIN, credentials.pin)
            .apply()
    }

    fun load(): Credentials? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        val pin = prefs.getString(KEY_PIN, null) ?: return null
        return Credentials(email, password, pin)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
        const val KEY_PIN = "pin"
    }
}
