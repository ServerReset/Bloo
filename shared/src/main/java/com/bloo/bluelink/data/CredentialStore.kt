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
    val brand: Brand = Brand.HYUNDAI,
)

/**
 * Stores credentials at rest using AES-256 via Jetpack Security
 * (EncryptedSharedPreferences) — one set per brand, so multiple accounts can be
 * remembered and re-authenticated after a token expires.
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
        migrateLegacy()
        val b = credentials.brand.name
        val brands = brandSet().toMutableSet().apply { add(b) }
        prefs.edit()
            .putString("${b}_email", credentials.email)
            .putString("${b}_password", credentials.password)
            .putString("${b}_pin", credentials.pin)
            .putStringSet(KEY_BRANDS, brands)
            .apply()
    }

    fun load(brand: Brand): Credentials? {
        migrateLegacy()
        val b = brand.name
        val email = prefs.getString("${b}_email", null) ?: return null
        val password = prefs.getString("${b}_password", null) ?: return null
        val pin = prefs.getString("${b}_pin", null) ?: return null
        return Credentials(email, password, pin, brand)
    }

    fun loadAll(): List<Credentials> {
        migrateLegacy()
        return brandSet().mapNotNull { name ->
            runCatching { Brand.valueOf(name) }.getOrNull()?.let { load(it) }
        }
    }

    fun updatePin(brand: Brand, pin: String) {
        prefs.edit().putString("${brand.name}_pin", pin).apply()
    }

    fun clear(brand: Brand) {
        val b = brand.name
        val brands = brandSet().toMutableSet().apply { remove(b) }
        prefs.edit()
            .remove("${b}_email").remove("${b}_password").remove("${b}_pin")
            .putStringSet(KEY_BRANDS, brands)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun brandSet(): Set<String> = prefs.getStringSet(KEY_BRANDS, emptySet()) ?: emptySet()

    /** Migrate the old single-account keys into the per-brand layout (one-shot). */
    private fun migrateLegacy() {
        val email = prefs.getString("email", null) ?: return
        val brand = Brand.fromName(prefs.getString("brand", null)).name
        prefs.edit()
            .putString("${brand}_email", email)
            .putString("${brand}_password", prefs.getString("password", null))
            .putString("${brand}_pin", prefs.getString("pin", null))
            .putStringSet(KEY_BRANDS, setOf(brand))
            .remove("email").remove("password").remove("pin").remove("brand")
            .apply()
    }

    private companion object {
        const val KEY_BRANDS = "brands"
    }
}
