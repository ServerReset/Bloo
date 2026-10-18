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
        SecureVault(f, w, pinIterations = 10).edit { put("pw", "hunter2-secret"); put("pin", "4321") }
        val text = f.readText()
        assertFalse("hunter2-secret" in text); assertFalse("4321" in text)
        val again = SecureVault(f, w, pinIterations = 10)
        assertEquals("hunter2-secret", again.getString("pw"))
        assertEquals("4321", again.getString("pin"))
    }

    @Test fun tamperedFileIsRejected() {
        val f = file(); val w = FakeWrapper()
        SecureVault(f, w, pinIterations = 10).edit { put("pw", "secret") }
        f.writeText(f.readText().replace("\"data\":\"", "\"data\":\"AAAA"))
        assertNull(SecureVault(f, w, pinIterations = 10).getString("pw"))
    }

    @Test fun lostDeviceKey_isRecoveredByThePin() {
        val f = file(); val w = FakeWrapper()
        val v = SecureVault(f, w, pinIterations = 10)
        v.edit { put("pw", "secret") }
        v.bindPin("246810")
        w.key = ByteArray(32) { 7 } // the device key is gone
        val lost = SecureVault(f, w, pinIterations = 10)
        assertNull(lost.getString("pw")); assertTrue(lost.needsRecovery)
        assertFalse(lost.recoverWithPin("000000"))
        assertTrue(lost.recoverWithPin("246810"))
        assertEquals("secret", lost.getString("pw"))
        assertEquals("secret", SecureVault(f, w, pinIterations = 10).getString("pw")) // re-sealed to the new key
    }

    @Test fun removingThePin_dropsTheSlot() {
        val f = file(); val w = FakeWrapper()
        val v = SecureVault(f, w, pinIterations = 10)
        v.edit { put("a", "b") }; v.bindPin("1234"); v.unbindPin()
        w.key = ByteArray(32) { 9 }
        assertFalse(SecureVault(f, w, pinIterations = 10).recoverWithPin("1234"))
    }

    @Test fun sealedVault_hidesEverythingButTheAppLockRecords() {
        val f = file(); val w = FakeWrapper()
        var sealed = false
        val v = SecureVault(f, w, pinIterations = 10, sealed = { sealed })
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
        SecureVault(f, w, pinIterations = 10).edit { put("pw", "secret") }
        val good = w.key
        w.key = ByteArray(32) { 5 } // the key is briefly unavailable
        val v = SecureVault(f, w, pinIterations = 10)
        assertNull(v.getString("pw"))
        w.key = good // and comes back
        assertEquals("secret", v.getString("pw"))
    }

    @Test fun writingOverAVaultTheDeviceCannotOpen_keepsTheOldFile() {
        val f = file(); val w = FakeWrapper()
        val first = SecureVault(f, w, pinIterations = 10)
        first.edit { put("pw", "secret") }
        first.bindPin("246810")
        w.key = ByteArray(32) { 7 } // the device key is gone for good
        SecureVault(f, w, pinIterations = 10).edit { put("new", "value") }
        assertTrue(File(f.parentFile, f.name + ".lost").exists(), "the unopenable vault was kept, not destroyed")
    }
}
