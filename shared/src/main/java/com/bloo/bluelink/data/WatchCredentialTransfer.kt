package com.bloo.bluelink.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import java.security.spec.MGF1ParameterSpec
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Everything the watch needs to run the car on its own: each signed-in account's credentials (so it
 * can sign back in when a token expires) and its current session.
 */
@Serializable
data class WatchCredentialBundle(
    val accounts: List<Account>,
    val sessions: List<SessionEntry>,
) {
    @Serializable
    data class Account(val brand: String, val email: String, val password: String, val pin: String)

    @Serializable
    data class SessionEntry(
        val brand: String,
        val accessToken: String,
        val refreshToken: String?,
        val username: String,
        val pin: String,
        val deviceId: String?,
    )
}

/** The sealed envelope that carries a [WatchCredentialBundle] from phone to watch. */
object WatchCredentialTransfer {
    private const val VERSION: Byte = 1
    private const val IV_BYTES = 12
    private const val OAEP = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private val json = Json { ignoreUnknownKeys = true }
    // Explicit SHA-256 / MGF1-SHA-1 parameters: the combination Android's Keystore implements,
    // spelled out so both ends agree whichever provider each picks.
    private val OAEP_PARAMS = OAEPParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT,
    )

    /**
     * A short code derived from the watch's public key. The watch shows it and the phone asks the
     * user to confirm they match, so credentials are only sent to the key the user is looking at.
     */
    fun confirmationCode(publicKey: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(publicKey)
        val n = ((d[0].toInt() and 0xff) shl 16 or ((d[1].toInt() and 0xff) shl 8) or (d[2].toInt() and 0xff)) % 10_000
        return n.toString().padStart(4, '0')
    }

    fun seal(watchPublicKey: ByteArray, bundle: WatchCredentialBundle): ByteArray {
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(watchPublicKey))
        return seal(key, watchPublicKey, bundle)
    }

    internal fun seal(key: PublicKey, keyBytes: ByteArray, bundle: WatchCredentialBundle): ByteArray {
        val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val body = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, aes, GCMParameterSpec(128, iv))
            updateAAD(aad(keyBytes))
            doFinal(json.encodeToString(WatchCredentialBundle.serializer(), bundle).encodeToByteArray())
        }
        val wrapped = Cipher.getInstance(OAEP).run {
            init(Cipher.ENCRYPT_MODE, key, OAEP_PARAMS)
            doFinal(aes.encoded)
        }
        return ByteBuffer.allocate(1 + 2 + wrapped.size + iv.size + body.size)
            .put(VERSION).putShort(wrapped.size.toShort()).put(wrapped).put(iv).put(body).array()
    }

    /**
     * Open an envelope with the watch's private key; null if it is not a valid envelope for that
     * key.
     */
    fun open(privateKey: PrivateKey, publicKeyBytes: ByteArray, envelope: ByteArray): WatchCredentialBundle? =
        runCatching {
            val buf = ByteBuffer.wrap(envelope)
            require(buf.get() == VERSION)
            val wrapped = ByteArray(buf.short.toInt() and 0xffff).also { buf.get(it) }
            val iv = ByteArray(IV_BYTES).also { buf.get(it) }
            val body = ByteArray(buf.remaining()).also { buf.get(it) }
            // Decrypt rather than unwrap: a Keystore-held key is asked for the raw AES bytes, not
            // to import a key object.
            val aes = SecretKeySpec(
                Cipher.getInstance(OAEP).run {
                    init(Cipher.DECRYPT_MODE, privateKey, OAEP_PARAMS)
                    doFinal(wrapped)
                },
                "AES",
            )
            val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, aes, GCMParameterSpec(128, iv))
                updateAAD(aad(publicKeyBytes))
                doFinal(body)
            }
            json.decodeFromString(WatchCredentialBundle.serializer(), plain.decodeToString())
        }.getOrNull()

    private fun aad(publicKeyBytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
}
