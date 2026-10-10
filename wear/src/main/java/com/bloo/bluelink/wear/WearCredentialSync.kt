package com.bloo.bluelink.wear

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.CarCommand
import com.bloo.bluelink.data.CarCommandResult
import com.bloo.bluelink.data.CarCommandRunner
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.WatchCredentialTransfer
import com.bloo.bluelink.data.WatchSyncProtocol
import com.google.android.gms.wearable.Wearable
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * The watch end of "sign the watch in so it works on its own".
 *
 * The phone asks ([onOffer]); the watch makes a brand-new RSA key INSIDE its Android Keystore,
 * hands the phone only the public half, and shows a short code derived from it. The user checks
 * the code on the phone, the phone seals the accounts to that key, and [onPayload] opens the
 * envelope with the Keystore key -- which cannot be exported -- and stores the result in the same
 * encrypted stores the phone uses. After that the watch signs in and runs commands itself.
 */
object WearCredentialSync {
    private const val ALIAS = "bloo_watch_transfer"

    private val _signInCode = MutableStateFlow<String?>(null)
    /** The code to show while a sign-in from the phone is waiting for the user's confirmation. */
    val signInCode: StateFlow<String?> = _signInCode

    private val _signedIn = MutableStateFlow(false)
    /** True once this watch holds its own session, so it can run commands without the phone. */
    val signedIn: StateFlow<Boolean> = _signedIn

    /** The phone wants to sign this watch in: make a fresh key and send its public half back. */
    suspend fun onOffer(context: Context, phoneNodeId: String) {
        val publicKey = newTransferKey()
        _signInCode.value = WatchCredentialTransfer.confirmationCode(publicKey)
        runCatching {
            Wearable.getMessageClient(context.applicationContext)
                .sendMessage(phoneNodeId, WatchSyncProtocol.PATH_CRED_KEY, publicKey).await()
        }.onFailure { AppLog.log("WatchSync: couldn't send the transfer key (${it.javaClass.simpleName})") }
    }

    /** The phone's sealed accounts arrived: open them, store them, and confirm. */
    suspend fun onPayload(context: Context, phoneNodeId: String, envelope: ByteArray) {
        val app = context.applicationContext
        val (private, public) = transferKey() ?: return
        val bundle = WatchCredentialTransfer.open(private, public, envelope) ?: run {
            AppLog.log("WatchSync: a credential envelope didn't open")
            return
        }
        val credentials = CredentialStore(app)
        val sessions = SessionStore(app)
        bundle.accounts.forEach { a ->
            brandOf(a.brand)?.let { credentials.save(Credentials(a.email, a.password, a.pin, it)) }
        }
        bundle.sessions.forEach { s ->
            brandOf(s.brand)?.let {
                sessions.save(SessionStore.Session(s.accessToken, s.refreshToken, s.username, s.pin, it, s.deviceId))
            }
        }
        // One use: the key has done its job.
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
        _signInCode.value = null
        _signedIn.value = bundle.sessions.isNotEmpty()
        runCatching {
            Wearable.getMessageClient(app).sendMessage(phoneNodeId, WatchSyncProtocol.PATH_CRED_ACK, ByteArray(0)).await()
        }
        refresh(app)
    }

    /** Whether this watch holds a session of its own (and so can work without the phone). */
    suspend fun hasSession(context: Context): Boolean =
        runCatching { SessionStore(context.applicationContext).loggedInBrands().isNotEmpty() }
            .getOrDefault(false).also { _signedIn.value = it }

    /**
     * Run a command on the watch itself. Returns null when the watch has no session, which is the
     * caller's cue to hand the command to the phone instead.
     */
    suspend fun runLocally(context: Context, vin: String, action: String): CarCommandResult? {
        val app = context.applicationContext
        if (!hasSession(app)) return null
        val result = runCatching { CarCommandRunner.execute(app, CarCommand(vin, action)) }
            .getOrElse { CarCommandResult(vin, action, ok = false, message = it.message) }
        WearDataLayerSync.reloadFromStore(app, result)
        return result
    }

    /** Pull fresh car status straight from the car's service when the watch has its own session. */
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        if (!hasSession(app)) return
        runCatching { CarCommandRunner.refresh(app, vin = "", force = false) }
        WearDataLayerSync.reloadFromStore(app, null)
    }

    private fun brandOf(name: String): Brand? = runCatching { Brand.valueOf(name) }.getOrNull()

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** Replace any earlier key with a new one and return its public half (X.509). */
    private fun newTransferKey(): ByteArray {
        runCatching { keyStore().deleteEntry(ALIAS) }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .build()
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            .apply { initialize(spec) }
            .generateKeyPair().public.encoded
    }

    private fun transferKey(): Pair<PrivateKey, ByteArray>? = runCatching {
        val ks = keyStore()
        val private = ks.getKey(ALIAS, null) as PrivateKey
        private to ks.getCertificate(ALIAS).publicKey.encoded
    }.getOrNull()
}
