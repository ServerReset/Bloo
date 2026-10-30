package com.bloo.bluelink.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wraps and unwraps the vault's data key. The default implementation is a hardware-backed Android
 * Keystore key; tests substitute a plain in-memory one.
 */
interface VaultKeyWrapper {
    fun wrap(dek: ByteArray): ByteArray
    /** Null when the wrapping key is gone or the blob does not authenticate. */
    fun unwrap(blob: ByteArray): ByteArray?
    /**
     * True when the key exists but the device must be unlocked before it can be used. The vault is then
     * locked, not empty: nothing may be written over it until the device is unlocked.
     */
    val locked: Boolean get() = false
}

/**
 * The one place the app keeps secrets at rest: sign-in credentials, car service PINs, session
 * tokens, the app-lock PIN record and its lockout counters all live in a single AES-256-GCM
 * encrypted file.
 *
 * One key: a random 256-bit data key (DEK), sealed by a hardware-backed Android Keystore key. It needs no
 * prompt, so background work (status refresh, notifications, AutoLock, the watch) can still read. There is no
 * app PIN: the device's own lock protects the app, and a vault from the old PIN layout opens with the device key and
 * drops its PIN slot the next time it is saved.

 * AES comes in 128, 192 and 256-bit keys only; 256 is the strongest, and it is what is used here.
 */
class SecureVault(
    private val file: File,
    private val wrapper: VaultKeyWrapper,
    private val random: SecureRandom = SecureRandom(),
    /** True while the vault is sealed (see [BootGate]): only the app-lock records may be read. */
    private val sealed: () -> Boolean = { false },
) {
    private val lock = Any()
    private var values = HashMap<String, String>()
    private var dek: ByteArray? = null
    private var deviceSlot: String? = null
    private var loaded = false

    /** True when a vault exists on disk but this device's key can no longer open it (and is not merely locked). */
    val needsRecovery: Boolean get() = synchronized(lock) { ensureLoaded(); dek == null && file.exists() && !wrapper.locked }

    /** True while the vault exists but the device must be unlocked before it can be read or written. */
    val locked: Boolean get() = synchronized(lock) { ensureLoaded(); dek == null && file.exists() && wrapper.locked }

    fun getString(key: String): String? = synchronized(lock) {
        ensureLoaded()
        if (key.startsWith(OPEN_PREFIX) || !sealed()) values[key] else null
    }

    /**
     * Reads past the seal. Only for checking what the user types against what is stored (the
     * sign-in step after a restart); nothing else may use it.
     */
    fun getStringUnsealed(key: String): String? = synchronized(lock) { ensureLoaded(); values[key] }

    /**
     * Applies every change in [block] and writes the vault once. Returns false, and changes nothing, when the write cannot be made
     * (the vault is locked, or its key is unavailable), so a failed save never leaves a half-written vault behind.
     */
    fun edit(block: Editor.() -> Unit): Boolean = synchronized(lock) {
        ensureLoaded()
        val before = values
        val ed = Editor(HashMap(values)).apply(block)
        values = ed.map
        try {
            persist()
            true
        } catch (e: Exception) {
            values = before
            false
        }
    }

    class Editor internal constructor(internal val map: HashMap<String, String>) {
        fun put(key: String, value: String) { map[key] = value }
        fun remove(key: String) { map.remove(key) }
    }

    // --- internals ---

    private fun ensureLoaded() {
        if (loaded) return
        if (!file.exists()) { loaded = true; return }
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        deviceSlot = root.optString("device").takeIf { it.isNotEmpty() }
        val key = deviceSlot?.let { wrapper.unwrap(b64d(it)) }
        if (key != null) {
            val text = runCatching { decryptData(key, root) }.getOrNull()
            // Loaded ONLY once it has opened. A failure (a Keystore hiccup, a read error) is not remembered: it used to
            // freeze an empty vault for the whole process, and the next edit then wrote that empty vault over the file.
            if (text != null) { dek = key; values = text; loaded = true }
        }
    }

    private fun decryptData(key: ByteArray, root: JSONObject? = null): HashMap<String, String> {
        val r = root ?: JSONObject(file.readText())
        val plain = open(key, b64d(r.getString("data")))
            ?: throw IllegalStateException("vault does not authenticate")
        val obj = JSONObject(String(plain, Charsets.UTF_8))
        return HashMap<String, String>().also { m -> obj.keys().forEach { k -> m[k] = obj.getString(k) } }
    }

    private fun persist() {
        var key = dek
        // A vault whose key needs the device unlock is not replaced by a new key: that would destroy what is stored.
        if (key == null && wrapper.locked) throw IllegalStateException("vault is locked until the device is unlocked")
        if (key == null) {
            // About to write a vault this device could not open over the one on disk. Keep the old file once, beside
            // it, so it is not destroyed by this write.
            if (file.exists()) runCatching { file.copyTo(File(file.parentFile, file.name + ".lost"), overwrite = false) }
            key = newKey(); dek = key
        }
        val obj = JSONObject()
        values.forEach { (k, v) -> obj.put(k, v) }
        val root = JSONObject()
            .put("v", 1)
            .put("device", b64(wrapper.wrap(key)))
            .put("data", b64(seal(key, obj.toString().toByteArray(Charsets.UTF_8))))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun newKey(): ByteArray = ByteArray(32).also { random.nextBytes(it) }

    /** AES-256-GCM: a fresh 12-byte IV in front of the ciphertext and tag. */
    private fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(AAD)
        return iv + c.doFinal(plain)
    }

    private fun open(key: ByteArray, blob: ByteArray): ByteArray? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        c.updateAAD(AAD)
        c.doFinal(blob, 12, blob.size - 12)
    }.getOrNull()

    private fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    private fun b64d(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)

    companion object {
        private val AAD = "bloo-vault-v1".toByteArray()

        /** Keys that stay readable while sealed: the app-lock PIN record and its lockout counters. */
        private const val OPEN_PREFIX = "app_pin"

        const val FILE_NAME = "bloo_vault.json"

        @Volatile private var instance: SecureVault? = null
        @Volatile private var secretsInstance: SecureVault? = null

        /** The file the sign-in secrets live in, sealed by [UserAuthKeystoreWrapper]. */
        const val SECRETS_FILE_NAME = "bloo_secrets.json"

        /** The vault for the sign-in secrets: the same format, behind the device unlock. Built on first use. */
        fun secrets(context: Context): SecureVault =
            secretsInstance ?: synchronized(this) {
                val app = context.applicationContext
                secretsInstance ?: SecureVault(File(app.filesDir, SECRETS_FILE_NAME), UserAuthKeystoreWrapper, sealed = { BootGate.isLocked(app) })
                    .also { secretsInstance = it }
            }

        /** The process-wide vault, built on first use. */
        fun of(context: Context): SecureVault =
            instance ?: synchronized(this) {
                val app = context.applicationContext
                instance ?: SecureVault(File(app.filesDir, FILE_NAME), KeystoreWrapper, sealed = { BootGate.isLocked(app) })
                    .also { instance = it }
            }
    }
}

/**
 * The key for the sign-in secrets: AES-256-GCM in the Android Keystore, usable only after the device has been unlocked with its own
 * lock screen (PIN, pattern or password), and then for [WINDOW_SECONDS]. Background work that needs the stored sign-ins therefore
 * keeps working for that window after each unlock, and a fingerprint enrolled later does not invalidate it.
 */
internal object UserAuthKeystoreWrapper : VaultKeyWrapper {
    private const val ALIAS = "bloo_secrets_key_v1"
    /** 30 days: long enough that background refresh works between unlocks, short enough that a stolen, long-idle phone is locked. */
    internal const val WINDOW_SECONDS = 30 * 24 * 60 * 60
    private val lockedFlag = java.util.concurrent.atomic.AtomicBoolean(false)

    override val locked: Boolean get() = lockedFlag.get()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(WINDOW_SECONDS)
                .setInvalidatedByBiometricEnrollment(false)
                .build(),
        )
        return gen.generateKey()
    }

    override fun wrap(dek: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            c.init(Cipher.ENCRYPT_MODE, key())
        } catch (e: android.security.keystore.UserNotAuthenticatedException) {
            lockedFlag.set(true)
            throw e
        }
        lockedFlag.set(false)
        return c.iv + c.doFinal(dek)
    }

    override fun unwrap(blob: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        lockedFlag.set(false)
        c.doFinal(blob, 12, blob.size - 12)
    } catch (e: android.security.keystore.UserNotAuthenticatedException) {
        lockedFlag.set(true)
        null
    } catch (e: Exception) {
        null
    }
}

/** The device slot's key: AES-256-GCM in the Android Keystore, never exportable. */
private object KeystoreWrapper : VaultKeyWrapper {
    private const val ALIAS = "bloo_vault_key_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun wrap(dek: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(dek)
    }

    override fun unwrap(blob: ByteArray): ByteArray? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        c.doFinal(blob, 12, blob.size - 12)
    }.getOrNull()
}
