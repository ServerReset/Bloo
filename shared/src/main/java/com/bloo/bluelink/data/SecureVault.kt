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
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wraps and unwraps the vault's data key. The default implementation is a hardware-backed Android
 * Keystore key; tests substitute a plain in-memory one.
 */
interface VaultKeyWrapper {
    fun wrap(dek: ByteArray): ByteArray
    /** Null when the wrapping key is gone or the blob does not authenticate. */
    fun unwrap(blob: ByteArray): ByteArray?
}

/**
 * The one place the app keeps secrets at rest: sign-in credentials, car service PINs, session
 * tokens, the app-lock PIN record and its lockout counters all live in a single AES-256-GCM
 * encrypted file.
 *
 * Keys, two layers over one random 256-bit data key (DEK):
 *  - **device slot**: the DEK sealed by a hardware-backed Android Keystore key. It needs no prompt,
 *    so background work (status refresh, notifications, AutoLock, the watch) can still read.
 *  - **PIN slot** (when an app PIN is set): the DEK sealed again under a key stretched from the PIN
 *    (PBKDF2-HMAC-SHA256). If the device key is ever lost (restore, Keystore reset), entering the
 *    PIN recovers the vault instead of losing every account.
 *
 * AES comes in 128, 192 and 256-bit keys only; 256 is the strongest, and it is what is used here.
 */
class SecureVault(
    private val file: File,
    private val wrapper: VaultKeyWrapper,
    private val random: SecureRandom = SecureRandom(),
    private val pinIterations: Int = PIN_ITERATIONS,
    /** True while the vault is sealed (see [BootGate]): only the app-lock records may be read. */
    private val sealed: () -> Boolean = { false },
) {
    private val lock = Any()
    private var values = HashMap<String, String>()
    private var dek: ByteArray? = null
    private var deviceSlot: String? = null
    private var pinSlot: JSONObject? = null
    private var loaded = false

    /** True when a vault exists on disk but this device's key can no longer open it. */
    val needsRecovery: Boolean get() = synchronized(lock) { ensureLoaded(); dek == null && file.exists() }

    fun getString(key: String): String? = synchronized(lock) {
        ensureLoaded()
        if (key.startsWith(OPEN_PREFIX) || !sealed()) values[key] else null
    }

    /**
     * Reads past the seal. Only for checking what the user types against what is stored (the
     * sign-in step after a restart); nothing else may use it.
     */
    fun getStringUnsealed(key: String): String? = synchronized(lock) { ensureLoaded(); values[key] }

    /** Applies every change in [block] and writes the vault once. */
    fun edit(block: Editor.() -> Unit) = synchronized(lock) {
        ensureLoaded()
        val ed = Editor(HashMap(values)).apply(block)
        values = ed.map
        persist()
    }

    class Editor internal constructor(internal val map: HashMap<String, String>) {
        fun put(key: String, value: String) { map[key] = value }
        fun remove(key: String) { map.remove(key) }
    }

    /** Seals the data key under [pin] as well, so the PIN can recover the vault. */
    fun bindPin(pin: String) = synchronized(lock) {
        ensureLoaded()
        if (dek == null) { dek = newKey(); values = HashMap() }
        pinSlot = sealPin(dek!!, pin)
        persist()
    }

    /** Drops the PIN slot (the app PIN was removed). */
    fun unbindPin() = synchronized(lock) {
        ensureLoaded()
        if (pinSlot != null) { pinSlot = null; if (dek != null) persist() }
    }

    /** Opens a vault the device key cannot, using the PIN slot, and re-seals it to the device. */
    fun recoverWithPin(pin: String): Boolean = synchronized(lock) {
        ensureLoaded()
        if (dek != null) return true
        val slot = pinSlot ?: return false
        val key = openPin(slot, pin) ?: return false
        val text = runCatching { decryptData(key) }.getOrNull() ?: return false
        dek = key
        values = text
        persist()
        true
    }

    // --- internals ---

    private fun ensureLoaded() {
        if (loaded) return
        if (!file.exists()) { loaded = true; return }
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        deviceSlot = root.optString("device").takeIf { it.isNotEmpty() }
        pinSlot = root.optJSONObject("pin")
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
        if (key == null) {
            // About to write a vault this device could not open over the one on disk. Keep the old file once, beside
            // it, so what the PIN slot could still recover is not destroyed by this write.
            if (file.exists()) runCatching { file.copyTo(File(file.parentFile, file.name + ".lost"), overwrite = false) }
            key = newKey(); dek = key
        }
        val obj = JSONObject()
        values.forEach { (k, v) -> obj.put(k, v) }
        val root = JSONObject()
            .put("v", 1)
            .put("device", b64(wrapper.wrap(key)))
            .put("data", b64(seal(key, obj.toString().toByteArray(Charsets.UTF_8))))
        pinSlot?.let { root.put("pin", it) }
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

    private fun pinKey(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, 256)).encoded

    private fun sealPin(dek: ByteArray, pin: String): JSONObject {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        return JSONObject()
            .put("salt", b64(salt))
            .put("iter", pinIterations)
            .put("ct", b64(seal(pinKey(pin, salt, pinIterations), dek)))
    }

    private fun openPin(slot: JSONObject, pin: String): ByteArray? = runCatching {
        open(pinKey(pin, b64d(slot.getString("salt")), slot.getInt("iter")), b64d(slot.getString("ct")))
    }.getOrNull()

    private fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    private fun b64d(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)

    companion object {
        private val AAD = "bloo-vault-v1".toByteArray()

        /** Keys that stay readable while sealed: the app-lock PIN record and its lockout counters. */
        private const val OPEN_PREFIX = "app_pin"

        const val PIN_ITERATIONS = 210_000
        const val FILE_NAME = "bloo_vault.json"

        @Volatile private var instance: SecureVault? = null

        /** The process-wide vault, built on first use. */
        fun of(context: Context): SecureVault =
            instance ?: synchronized(this) {
                val app = context.applicationContext
                instance ?: SecureVault(File(app.filesDir, FILE_NAME), KeystoreWrapper, sealed = { BootGate.isLocked(app) })
                    .also { instance = it }
            }
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
