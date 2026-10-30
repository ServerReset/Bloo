package com.bloo.bluelink.data

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureVaultTest {
    /** A stand-in for the Keystore key: a plain AES key held in memory (or "lost" on demand). */
    private class FakeWrapper(var key: ByteArray? = ByteArray(32) { it.toByte() }) : VaultKeyWrapper {
        override fun wrap(dek: ByteArray): ByteArray {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key!!, "AES"), GCMParameterSpec(128, iv))
            return iv + c.doFinal(dek)
        }
        override fun unwrap(blob: ByteArray): ByteArray? = runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key!!, "AES"), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            c.doFinal(blob, 12, blob.size - 12)
        }.getOrNull()
    }

    private fun file() = File.createTempFile("vault", ".json").also { it.delete() }

    @Test fun valuesSurviveReopen_andFileHoldsNoPlaintext() {
        val f = file(); val w = FakeWrapper()
        SecureVault(f, w).edit { put("pw", "hunter2-secret"); put("pin", "4321") }
        val text = f.readText()
        assertFalse("hunter2-secret" in text); assertFalse("4321" in text)
        val again = SecureVault(f, w)
        assertEquals("hunter2-secret", again.getString("pw"))
        assertEquals("4321", again.getString("pin"))
    }

    @Test fun tamperedFileIsRejected() {
        val f = file(); val w = FakeWrapper()
        SecureVault(f, w).edit { put("pw", "secret") }
        f.writeText(f.readText().replace("\"data\":\"", "\"data\":\"AAAA"))
        assertNull(SecureVault(f, w).getString("pw"))
    }

    @Test fun sealedVault_hidesEverythingButTheAppLockRecords() {
        val f = file(); val w = FakeWrapper()
        var sealed = false
        val v = SecureVault(f, w, sealed = { sealed })
        v.edit { put("HYUNDAI_password", "secret"); put("app_pin_record", "rec") }
        sealed = true
        assertNull(v.getString("HYUNDAI_password"))
        assertEquals("rec", v.getString("app_pin_record"))
        assertEquals("secret", v.getStringUnsealed("HYUNDAI_password"))
        sealed = false
        assertEquals("secret", v.getString("HYUNDAI_password"))
    }

    @Test fun aKeystoreHiccup_isRetriedRatherThanFrozenForTheProcess() {
        val f = file(); val w = FakeWrapper()
        SecureVault(f, w).edit { put("pw", "secret") }
        val good = w.key
        w.key = ByteArray(32) { 5 } // the key is briefly unavailable
        val v = SecureVault(f, w)
        assertNull(v.getString("pw"))
        w.key = good // and comes back
        assertEquals("secret", v.getString("pw"))
    }

    @Test fun aVaultFromThePinLayout_opensWithTheDeviceKey_andDropsThePinSlot() {
        val f = file(); val w = FakeWrapper()
        SecureVault(f, w).edit { put("pw", "secret") }
        // An old vault also carries a PIN slot. It is ignored, and it is gone once the vault is saved again.
        val root = org.json.JSONObject(f.readText()).put("pin", org.json.JSONObject().put("salt", "AA==").put("iter", 10).put("ct", "AA=="))
        f.writeText(root.toString())
        val v = SecureVault(f, w)
        assertEquals("secret", v.getString("pw"))
        assertFalse(v.needsRecovery)
        v.edit { put("pw", "newer") }
        assertFalse("\"pin\"" in f.readText(), "the PIN slot is dropped on the next save")
        assertEquals("newer", SecureVault(f, w).getString("pw"))
    }
}
