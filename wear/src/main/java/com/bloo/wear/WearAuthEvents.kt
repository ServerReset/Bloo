package com.bloo.wear

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Auth-handoff signal bus.
 *
 * [WearListenerService] runs in the watch app's single process (no
 * `android:process` override) and persists a phone-pushed
 * [com.bloo.bluelink.data.WearSync.PATH_AUTH] bundle into the local
 * `SessionStore` even when no UI is attached. This bus lets it nudge a live
 * [WearViewModel] the instant that write lands, so a watch parked on its login
 * screen AUTO-ADVANCES to the signed-in experience — the "Set up on phone"
 * handoff — instead of stranding the user until the next manual resync.
 *
 * Design notes (must survive rewrites):
 * - `SessionStore` exposes only one-shot suspend reads, no Flow, so the
 *   ViewModel has no other reactive way to notice auth arriving.
 * - The event is intentionally **payload-free** ([Unit]): it is a "look again"
 *   ping. The collector re-reads `sessionStore.loggedInBrands()` to decide
 *   whether to leave the login screen — the bus never carries session data.
 * - A plain in-memory [MutableSharedFlow] is sufficient because emitter and
 *   collector share one process; no need to round-trip through disk.
 * - `extraBufferCapacity = 1` lets [emit] deliver without suspending even when
 *   no collector is currently attached (e.g. the handoff arrives a beat before
 *   the ViewModel starts collecting), so the auto-advance is never dropped.
 *
 * Mirrors [WearSyncEvents] / [WearCommandEvents] / [WearAiEvents].
 */
object WearAuthEvents {
    private val _arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Read-only stream the [WearViewModel] collects to trigger auto-advance. */
    val arrivals: SharedFlow<Unit> = _arrivals.asSharedFlow()

    /** Signal that a phone-pushed session bundle has been persisted locally. */
    suspend fun emit() = _arrivals.emit(Unit)
}
