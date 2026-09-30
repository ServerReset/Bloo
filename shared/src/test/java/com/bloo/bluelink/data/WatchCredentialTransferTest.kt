package com.bloo.bluelink.data

import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class WatchCredentialTransferTest {
    private val bundle = WatchCredentialBundle(
        accounts = listOf(WatchCredentialBundle.Account("HYUNDAI", "a@b.c", "hunter2", "1234")),
        sessions = listOf(WatchCredentialBundle.SessionEntry("HYUNDAI", "acc", "ref", "a@b.c", "1234", null)),
    )

    private fun keys() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    @Test
    fun sealedBundleOpensWithTheMatchingKey() {
        val kp = keys()
        val sealed = WatchCredentialTransfer.seal(kp.public.encoded, bundle)
        assertEquals(bundle, WatchCredentialTransfer.open(kp.private, kp.public.encoded, sealed))
    }

    @Test
    fun envelopeDoesNotContainThePlaintext() {
        val kp = keys()
        val sealed = WatchCredentialTransfer.seal(kp.public.encoded, bundle)
        assertEquals(false, sealed.decodeToString().contains("hunter2"))
    }

    @Test
    fun anotherKeyCannotOpenIt() {
        val sealed = WatchCredentialTransfer.seal(keys().public.encoded, bundle)
        val other = keys()
        assertNull(WatchCredentialTransfer.open(other.private, other.public.encoded, sealed))
    }

    @Test
    fun tamperedEnvelopeIsRejected() {
        val kp = keys()
        val sealed = WatchCredentialTransfer.seal(kp.public.encoded, bundle)
        sealed[sealed.size - 3] = (sealed[sealed.size - 3].toInt() xor 1).toByte()
        assertNull(WatchCredentialTransfer.open(kp.private, kp.public.encoded, sealed))
    }

    @Test
    fun confirmationCodeIsFourDigitsAndKeyDependent() {
        val a = keys().public.encoded
        val b = keys().public.encoded
        val code = WatchCredentialTransfer.confirmationCode(a)
        assertEquals(4, code.length)
        assertEquals(code, WatchCredentialTransfer.confirmationCode(a))
        assertNotEquals(a.toList(), b.toList())
    }
}
