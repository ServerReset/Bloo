package com.bloo.bluelink.data

import android.content.Context

/** Blue Link sign-in credentials, persisted so the user need not retype them. */
data class Credentials(
    val email: String,
    val password: String,
    val pin: String,
    val brand: Brand = Brand.HYUNDAI,
)

/**
 * Credentials, car service PINs and the app-lock PIN record, kept in the app's single encrypted
 * [SecureVault] (AES-256-GCM, Keystore-backed) -- one set per brand, so multiple accounts can be
 * remembered and re-authenticated after a token expires.
 */
class CredentialStore(context: Context) {
    private val appContext: Context = context.applicationContext

    private val vault: SecureVault get() = vaultFor(appContext)

    /**
     * Called from the startup warm-up thread in BlooApplication, which starts well over a second
     * before the cold-start auto-login first touches credentials: opening the vault (Keystore) and
     * the one-time import otherwise land on the critical path.
     */
    fun warmUp() {
        // Sealed after a restart (see BootGate): the list would come back empty and be cached as such.
        if (!BootGate.isLocked(appContext)) runCatching { cachedAccounts = loadAllUncached() }
    }

    /** Persists [credentials] under brand-prefixed keys (e.g. "HYUNDAI_email"). */
    fun save(credentials: Credentials) {
        val b = credentials.brand.name
        val brands = brandSet().toMutableSet().apply { add(b) }
        vault.edit {
            put("${b}_email", credentials.email)
            put("${b}_password", credentials.password)
            put("${b}_pin", credentials.pin)
            put(KEY_BRANDS, brands.joinToString(","))
        }
        cachedAccounts = null
    }

    /**
     * Loads the stored credentials for one [brand]. Returns null if any of the three required
     * fields (email/password/pin) is missing, treating a partially-written or never-saved account
     * as "not logged in" rather than returning a broken [Credentials] with blank fields.
     */
    fun load(brand: Brand): Credentials? {
        val b = brand.name
        val email = vault.getString("${b}_email") ?: return null
        val password = vault.getString("${b}_password") ?: return null
        val pin = vault.getString("${b}_pin") ?: return null
        return Credentials(email, password, pin, brand)
    }

    /** Loads every brand that has an entry in [KEY_BRANDS]. */
    fun loadAll(): List<Credentials> = cachedAccounts ?: loadAllUncached().also {
        if (!BootGate.isLocked(appContext)) cachedAccounts = it
    }

    private fun loadAllUncached(): List<Credentials> {
        return brandSet().mapNotNull { name ->
            enumOrNull<Brand>(name)?.let { load(it) }
        }
    }

    /** Overwrites just the stored PIN for [brand], leaving email/password untouched. */
    fun updatePin(brand: Brand, pin: String) {
        vault.edit { put("${brand.name}_pin", pin) }
        cachedAccounts = null
    }

    /** Removes one brand's stored credentials and drops it from the [KEY_BRANDS] set. */
    fun clear(brand: Brand) {
        val b = brand.name
        val brands = brandSet().toMutableSet().apply { remove(b) }
        vault.edit {
            remove("${b}_email"); remove("${b}_password"); remove("${b}_pin")
            put(KEY_BRANDS, brands.joinToString(","))
        }
        cachedAccounts = null
    }

    // --- App PIN (device app-lock, unrelated to any car's service PIN) ----
    //
    // Same vault, its own flat keys so it cannot collide with the per-brand "%s_email" scheme.

    /** The encoded [PinRecord] (or null when no PIN is set). */
    fun getPinRecord(): String? = vault.getString(KEY_PIN_RECORD)

    /**
     * Sets (or, with null, clears) the stored PIN record. Clearing also wipes the failure counter
     * -- there is nothing left to protect.
     */
    fun setPinRecord(record: String?) {
        if (record == null) {
            vault.edit {
                remove(KEY_PIN_RECORD); remove(KEY_PIN_FAILURES)
                remove(KEY_PIN_LOCKED_UNTIL); remove(KEY_PIN_LOCKED_UNTIL_ELAPSED)
            }
        } else {
            vault.edit { put(KEY_PIN_RECORD, record) }
        }
    }

    /** True when stored accounts exist but this device's key cannot open them. */
    val vaultNeedsRecovery: Boolean get() = vault.needsRecovery

    /**
     * The sign-in step after a restart: whether [email] and [password] (and the service [pin]) match
     * a stored account of [brand]. Reads past the vault's post-restart seal, so it is the only way
     * to get a yes/no out of a sealed vault; it returns nothing else.
     */
    fun matchesStoredAccount(brand: Brand, email: String, password: String, pin: String): Boolean {
        val b = brand.name
        fun same(a: String?, typed: String, ignoreCase: Boolean = false): Boolean {
            if (a == null) return false
            val x = a.toByteArray(); val y = typed.toByteArray()
            if (ignoreCase) return a.equals(typed, ignoreCase = true)
            return java.security.MessageDigest.isEqual(x, y)
        }
        return same(vault.getStringUnsealed("${b}_email"), email.trim(), ignoreCase = true) &&
            same(vault.getStringUnsealed("${b}_password"), password) &&
            same(vault.getStringUnsealed("${b}_pin"), pin.trim())
    }

    /** Consecutive PIN failures since the last successful unlock. */
    fun getPinFailures(): Int = vault.getString(KEY_PIN_FAILURES)?.toIntOrNull() ?: 0

    /**
     * Whether the PIN is currently in its rejection window, and for how long -- wall-clock epoch ms
     * until the next attempt may proceed.
     */
    fun getPinLockedUntil(): Long = vault.getString(KEY_PIN_LOCKED_UNTIL)?.toLongOrNull() ?: 0L

    fun getPinLockedUntilElapsed(): Long = vault.getString(KEY_PIN_LOCKED_UNTIL_ELAPSED)?.toLongOrNull() ?: 0L

    /** The whole stored [PinLockout] state: the counterpart of [setPinLockout]. */
    fun pinLockout(): PinLockout = PinLockout(getPinFailures(), getPinLockedUntil(), getPinLockedUntilElapsed())

    /** Persists the whole [PinLockout] state atomically. */
    fun setPinLockout(lockout: PinLockout) {
        vault.edit {
            put(KEY_PIN_FAILURES, lockout.failures.toString())
            put(KEY_PIN_LOCKED_UNTIL, lockout.lockedUntilEpochMs.toString())
            put(KEY_PIN_LOCKED_UNTIL_ELAPSED, lockout.lockedUntilElapsedMs.toString())
        }
    }

    private fun brandSet(): Set<String> =
        vault.getString(KEY_BRANDS)?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    internal companion object {
        // Holds the comma-joined brand names ("HYUNDAI,KIA") that currently have credentials saved.
        const val KEY_BRANDS = "brands"
        const val KEY_PIN_RECORD = "app_pin_record"
        const val KEY_PIN_FAILURES = "app_pin_failures"
        const val KEY_PIN_LOCKED_UNTIL = "app_pin_locked_until"
        const val KEY_PIN_LOCKED_UNTIL_ELAPSED = "app_pin_locked_until_elapsed"
        private const val KEY_IMPORTED = "legacy_credentials_imported"
        private const val LEGACY_PREFS = "bloo_credentials"

        /**
         * Process-wide cache of the decrypted account list, populated by [warmUp] and invalidated
         * by every write, so the read-only cold-start load is a plain cache hit.
         */
        @Volatile
        private var cachedAccounts: List<Credentials>? = null

        /** The vault, with the one-time import of the old EncryptedSharedPreferences file done. */
        fun vaultFor(context: Context): SecureVault {
            val vault = SecureVault.of(context)
            if (vault.getString(KEY_IMPORTED) == null) synchronized(this) {
                if (vault.getString(KEY_IMPORTED) == null) importLegacy(context, vault)
            }
            return vault
        }

        /**
         * Copies the old encrypted prefs into the vault, then deletes them. Skipped (and retried next
         * launch) if the old file cannot be read yet, so nothing is lost to a transient failure.
         */
        @Suppress("DEPRECATION")
        private fun importLegacy(context: Context, vault: SecureVault) {
            // A fresh install has no old file: skip building the old Keystore master key entirely.
            val legacyFile = java.io.File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFS.xml")
            if (!legacyFile.exists()) {
                vault.edit { put(KEY_IMPORTED, "1") }
                return
            }
            val old = runCatching {
                val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build()
                androidx.security.crypto.EncryptedSharedPreferences.create(
                    context, LEGACY_PREFS, masterKey,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrNull()
            val all = runCatching { old?.all }.getOrNull() ?: return
            vault.edit {
                all.forEach { (k, v) ->
                    when (v) {
                        is String -> put(k, v)
                        is Set<*> -> put(k, v.joinToString(","))
                        is Int, is Long, is Boolean -> put(k, v.toString())
                    }
                }
                put(KEY_IMPORTED, "1")
            }
            runCatching { context.deleteSharedPreferences(LEGACY_PREFS) }
        }
    }
}
