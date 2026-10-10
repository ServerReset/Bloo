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
 * Stores credentials at rest using AES-256 via Jetpack Security (EncryptedSharedPreferences) — one
 * set per brand, so multiple accounts can be remembered and re-authenticated after a token expires.
 */
class CredentialStore(context: Context) {

    private val appContext: Context = context.applicationContext

    // ONE EncryptedSharedPreferences per process, shared by every CredentialStore instance.
    private val prefs: SharedPreferences get() = sharedPrefs(appContext)

    /**
     * Called from the startup warm-up thread in BlooApplication, which starts well over a second
     * before the cold-start auto-login first touches credentials. That first real access otherwise
     * pays this setup on the critical path -- directly ahead of the app-lock check and the garage
     * load -- and it measures ~470ms on the API 34 emulator.
     */
    fun warmUp() {
        runCatching { cachedAccounts = loadAllUncached() }
    }

    /**
     * Persists [credentials] under brand-prefixed keys (e.g. "HYUNDAI_email") so multiple brands'
     * accounts coexist in the same prefs file.
     */
    fun save(credentials: Credentials) {
        val b = credentials.brand.name
        val brands = brandSet().toMutableSet().apply { add(b) }
        prefs.edit()
            .putString("${b}_email", credentials.email)
            .putString("${b}_password", credentials.password)
            .putString("${b}_pin", credentials.pin)
            .putStringSet(KEY_BRANDS, brands)
            .apply()
        cachedAccounts = null
    }

    /**
     * Loads the stored credentials for one [brand]. Returns null if any of the three required
     * fields (email/password/pin) is missing, treating a partially-written or never-saved account
     * as "not logged in" rather than returning a broken [Credentials] with blank fields.
     */
    fun load(brand: Brand): Credentials? {
        val b = brand.name
        val email = prefs.getString("${b}_email", null) ?: return null
        val password = prefs.getString("${b}_password", null) ?: return null
        val pin = prefs.getString("${b}_pin", null) ?: return null
        return Credentials(email, password, pin, brand)
    }

    /**
     * Loads every brand that has an entry in [KEY_BRANDS], mapping each stored brand name back to a
     * [Brand] enum value and then to its [Credentials] via [load].
     */
    fun loadAll(): List<Credentials> = cachedAccounts ?: loadAllUncached().also { cachedAccounts = it }

    private fun loadAllUncached(): List<Credentials> {
        return brandSet().mapNotNull { name ->
            runCatching { Brand.valueOf(name) }.getOrNull()?.let { load(it) }
        }
    }

    /** Overwrites just the stored PIN for [brand], leaving email/password untouched. */
    fun updatePin(brand: Brand, pin: String) {
        prefs.edit().putString("${brand.name}_pin", pin).apply()
        cachedAccounts = null
    }

    /** Removes one brand's stored credentials and drops it from the [KEY_BRANDS] set. */
    fun clear(brand: Brand) {
        val b = brand.name
        val brands = brandSet().toMutableSet().apply { remove(b) }
        prefs.edit()
            .remove("${b}_email").remove("${b}_password").remove("${b}_pin")
            .putStringSet(KEY_BRANDS, brands)
            .apply()
        cachedAccounts = null
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

    /**
     * Sets (or, with null, clears) the stored PIN record. Clearing also wipes the failure counter
     * -- there is nothing left to protect.
     */
    fun setPinRecord(record: String?) {
        if (record == null) {
            prefs.edit()
                .remove(KEY_PIN_RECORD)
                .remove(KEY_PIN_FAILURES)
                .remove(KEY_PIN_LOCKED_UNTIL)
                .remove(KEY_PIN_LOCKED_UNTIL_ELAPSED)
                .apply()
        } else {
            prefs.edit().putString(KEY_PIN_RECORD, record).apply()
        }
    }

    /** Consecutive PIN failures since the last successful unlock. */
    fun getPinFailures(): Int = prefs.getInt(KEY_PIN_FAILURES, 0)

    /**
     * Whether the PIN is currently in its rejection window, and for how long -- wall-clock epoch ms
     * until the next attempt may proceed.
     */
    fun getPinLockedUntil(): Long = prefs.getLong(KEY_PIN_LOCKED_UNTIL, 0L)

    fun getPinLockedUntilElapsed(): Long = prefs.getLong(KEY_PIN_LOCKED_UNTIL_ELAPSED, 0L)

    /** Persists the whole [PinLockout] state atomically. */
    fun setPinLockout(lockout: PinLockout) {
        prefs.edit()
            .putInt(KEY_PIN_FAILURES, lockout.failures)
            .putLong(KEY_PIN_LOCKED_UNTIL, lockout.lockedUntilEpochMs)
            .putLong(KEY_PIN_LOCKED_UNTIL_ELAPSED, lockout.lockedUntilElapsedMs)
            .apply()
    }

    // Falls back to emptySet() because getStringSet can return null if the key was never written.
    private fun brandSet(): Set<String> = prefs.getStringSet(KEY_BRANDS, emptySet()) ?: emptySet()

    private companion object {
        // Prefs key holding the Set<String> of brand names ("HYUNDAI", "KIA", ...) that currently
        // have credentials saved.
        const val KEY_BRANDS = "brands"
        const val KEY_PIN_RECORD = "app_pin_record"
        const val KEY_PIN_FAILURES = "app_pin_failures"
        const val KEY_PIN_LOCKED_UNTIL = "app_pin_locked_until"
        const val KEY_PIN_LOCKED_UNTIL_ELAPSED = "app_pin_locked_until_elapsed"

        @Volatile
        private var cachedPrefs: SharedPreferences? = null

        /**
         * Process-wide cache of the decrypted account list, populated by [warmUp] and invalidated
         * by every write, so the read-only cold-start load is a plain cache hit.
         */
        @Volatile
        private var cachedAccounts: List<Credentials>? = null

        /** The process-wide [EncryptedSharedPreferences], built once on first access. */
        fun sharedPrefs(context: Context): SharedPreferences =
            cachedPrefs ?: synchronized(this) {
                cachedPrefs ?: buildPrefs(context).also { cachedPrefs = it }
            }

        /** The (relatively expensive) master-key generation/lookup + setup itself. */
        fun buildPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "bloo_credentials",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

}
