package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.WatchCredentialBundle
import com.bloo.bluelink.data.WatchCredentialTransfer
import com.bloo.bluelink.data.WatchSyncProtocol
import com.bloo.bluelink.ioScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The phone end of signing the watch in so it can work on its own (see [WearCredentialSync] for
 * the watch end and the security design).
 *
 * offer → the watch makes a Keystore key and answers with its public half → the user confirms the
 * code the watch shows matches the one here → the accounts are sealed to that key and sent. Only
 * the watch can open them, and nothing readable crosses the link.
 */
object WatchSignIn {
    sealed interface State {
        data object Idle : State
        data object WaitingForWatch : State
        data class Confirm(val code: String, val nodeId: String, val publicKey: ByteArray) : State
        data object Sending : State
        data object Done : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val scope = ioScope()

    /** Ask the connected watch to start a sign-in. */
    fun offer(context: Context) {
        val app = context.applicationContext
        _state.value = State.WaitingForWatch
        scope.launch {
            val sent = runCatching {
                val nodes = Wearable.getNodeClient(app).connectedNodes.await()
                nodes.forEach { Wearable.getMessageClient(app).sendMessage(it.id, WatchSyncProtocol.PATH_CRED_OFFER, ByteArray(0)).await() }
                nodes.isNotEmpty()
            }.getOrDefault(false)
            if (!sent) return@launch fail("No watch is connected")
            // The watch answers within a moment or not at all (the app may be closed on it).
            delay(WAIT_MS)
            if (_state.value == State.WaitingForWatch) fail("The watch didn't answer. Open Bloo on it and try again.")
        }
    }

    /** The watch's public key arrived. */
    fun onKey(nodeId: String, publicKey: ByteArray) {
        if (_state.value != State.WaitingForWatch) return
        _state.value = State.Confirm(WatchCredentialTransfer.confirmationCode(publicKey), nodeId, publicKey)
    }

    /** The user confirmed the codes match: seal every signed-in account to the watch's key and send it. */
    fun confirm(context: Context) {
        val pending = _state.value as? State.Confirm ?: return
        val app = context.applicationContext
        _state.value = State.Sending
        scope.launch {
            runCatching {
                val envelope = WatchCredentialTransfer.seal(pending.publicKey, bundle(app))
                Wearable.getMessageClient(app).sendMessage(pending.nodeId, WatchSyncProtocol.PATH_CRED_PAYLOAD, envelope).await()
            }.onFailure {
                AppLog.log("WatchSync: sign-in send failed (${it.javaClass.simpleName})")
                fail("Couldn't reach the watch")
            }
            delay(WAIT_MS)
            if (_state.value == State.Sending) fail("The watch didn't confirm")
        }
    }

    /** The watch opened the envelope and saved the accounts. */
    fun onAck() {
        if (_state.value == State.Sending) _state.value = State.Done
    }

    fun dismiss() {
        _state.value = State.Idle
    }

    private fun fail(message: String) {
        _state.value = State.Failed(message)
    }

    private suspend fun bundle(app: Context): WatchCredentialBundle {
        val accounts = CredentialStore(app).loadAll().map {
            WatchCredentialBundle.Account(it.brand.name, it.email, it.password, it.pin)
        }
        val store = SessionStore(app)
        val sessions = store.loggedInBrands().mapNotNull { brand: Brand ->
            store.load(brand)?.let {
                WatchCredentialBundle.SessionEntry(brand.name, it.accessToken, it.refreshToken, it.username, it.pin, it.deviceId)
            }
        }
        return WatchCredentialBundle(accounts, sessions)
    }

    private const val WAIT_MS = 45_000L
}
