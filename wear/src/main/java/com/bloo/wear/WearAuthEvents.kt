package com.bloo.wear

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process signal from [WearListenerService] (which persists a phone-pushed
 * [com.bloo.bluelink.data.WearSync.PATH_AUTH] bundle into the watch's SessionStore,
 * even with no UI open) to a live [WearViewModel], so a watch sitting on its login
 * screen can AUTO-ADVANCE the instant the phone's session arrives — the "Set up on
 * phone" handoff.
 *
 * Why a signal and not a Flow on SessionStore: SessionStore exposes only one-shot
 * suspend reads (no Flow), so the ViewModel has no reactive way to notice auth
 * landing. This mirrors the existing [WearSyncEvents]/[WearCommandEvents]/
 * [WearAiEvents] pattern — a plain in-memory SharedFlow, since the listener service
 * runs in the same process (no `android:process` override). The event carries no
 * payload; the collector re-reads `sessionStore.loggedInBrands()` to decide whether
 * to leave the login screen.
 */
object WearAuthEvents {
    private val _arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val arrivals = _arrivals.asSharedFlow()

    suspend fun emit() = _arrivals.emit(Unit)
}
