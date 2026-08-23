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

    // Lazily built so the (relatively expensive) master-key generation/lookup and
    // EncryptedSharedPreferences setup only happen the first time credentials are
    // actually touched, not at CredentialStore construction time.
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

    /**
     * Persists [credentials] under brand-prefixed keys (e.g. "HYUNDAI_email") so
     * multiple brands' accounts coexist in the same prefs file. Runs [migrateLegacy]
     * first in case this is the first write since an app upgrade from the old
     * single-account key scheme. Adds the brand's name to the [KEY_BRANDS] set (used
     * by [loadAll]/[brandSet] to know which brands have stored credentials) and writes
     * everything in one `apply()` batch.
     */
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

    /**
     * Loads the stored credentials for one [brand]. Returns null if any of the three
     * required fields (email/password/pin) is missing, treating a partially-written
     * or never-saved account as "not logged in" rather than returning a broken
     * [Credentials] with blank fields.
     */
    fun load(brand: Brand): Credentials? {
        migrateLegacy()
        val b = brand.name
        val email = prefs.getString("${b}_email", null) ?: return null
        val password = prefs.getString("${b}_password", null) ?: return null
        val pin = prefs.getString("${b}_pin", null) ?: return null
        return Credentials(email, password, pin, brand)
    }

    /**
     * Loads every brand that has an entry in [KEY_BRANDS], mapping each stored brand
     * name back to a [Brand] enum value and then to its [Credentials] via [load].
     * Uses `runCatching { Brand.valueOf(name) }.getOrNull()` so a stale/unknown brand
     * name left over from a removed enum constant is silently skipped instead of
     * throwing and losing every other account.
     */
    fun loadAll(): List<Credentials> {
        migrateLegacy()
        return brandSet().mapNotNull { name ->
            runCatching { Brand.valueOf(name) }.getOrNull()?.let { load(it) }
        }
    }

    /** Overwrites just the stored PIN for [brand], leaving email/password untouched. */
    fun updatePin(brand: Brand, pin: String) {
        prefs.edit().putString("${brand.name}_pin", pin).apply()
    }

    /** Removes one brand's stored credentials and drops it from the [KEY_BRANDS] set. */
    fun clear(brand: Brand) {
        val b = brand.name
        val brands = brandSet().toMutableSet().apply { remove(b) }
        prefs.edit()
            .remove("${b}_email").remove("${b}_password").remove("${b}_pin")
            .putStringSet(KEY_BRANDS, brands)
            .apply()
    }

    // --- App PIN (device app-lock, unrelated to any car's service PIN) ----
    //
    // The app PIN is NOT a brand credential, but it shares this store
    // deliberately: it is a secret that must never leave the device, and this
    // is the file that already guarantees exactly that (AES-256-GCM via
    // Android Keystore, see the class doc). It lives under its own flat keys
    // so it cannot collide with the per-brand "%s_email" scheme.

    /** The encoded [PinRecord] (or null when no PIN is set). */
    fun getPinRecord(): String? = prefs.getString(KEY_PIN_RECORD, null)

    /** Sets (or, with null, clears) the stored PIN record. Clearing also
     *  wipes the failure counter -- there is nothing left to protect. */
    fun setPinRecord(record: String?) {
        if (record == null) {
            prefs.edit()
                .remove(KEY_PIN_RECORD)
                .remove(KEY_PIN_FAILURES)
                .remove(KEY_PIN_LOCKED_UNTIL)
                .apply()
        } else {
            prefs.edit().putString(KEY_PIN_RECORD, record).apply()
        }
    }

    /** Consecutive PIN failures since the last successful unlock. */
    fun getPinFailures(): Int = prefs.getInt(KEY_PIN_FAILURES, 0)

    /** Whether the PIN is currently in its rejection window, and for how
     *  long -- wall-clock epoch ms until the next attempt may proceed. */
    fun getPinLockedUntil(): Long = prefs.getLong(KEY_PIN_LOCKED_UNTIL, 0L)

    /** Persists the whole [PinLockout] state atomically. */
    fun setPinLockout(lockout: PinLockout) {
        prefs.edit()
            .putInt(KEY_PIN_FAILURES, lockout.failures)
            .putLong(KEY_PIN_LOCKED_UNTIL, lockout.lockedUntilEpochMs)
            .apply()
    }

    /** Wipes the entire encrypted prefs file — all brands, all accounts. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    // The set of brand names that currently have credentials stored, used to drive
    // loadAll()/save()/clear() bookkeeping. Falls back to emptySet() because
    // getStringSet can return null if the key was never written.
    private fun brandSet(): Set<String> = prefs.getStringSet(KEY_BRANDS, emptySet()) ?: emptySet()

    /**
     * Migrate the old single-account keys into the per-brand layout (one-shot).
     * Detects the legacy scheme by checking for a bare "email" key (no brand prefix);
     * if absent, this is either a fresh install or an already-migrated install, so it
     * returns immediately. Otherwise it reads the legacy "brand" key (defaulting via
     * [Brand.fromName] if missing/unrecognized), rewrites the three legacy fields
     * under the new brand-prefixed keys, seeds [KEY_BRANDS] with that one brand, and
     * removes the old unprefixed keys so this block becomes a no-op on future calls.
     */
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
        // Prefs key holding the Set<String> of brand names ("HYUNDAI", "KIA", ...)
        // that currently have credentials saved.
        const val KEY_BRANDS = "brands"
        const val KEY_PIN_RECORD = "app_pin_record"
        const val KEY_PIN_FAILURES = "app_pin_failures"
        const val KEY_PIN_LOCKED_UNTIL = "app_pin_locked_until"
    }
}
