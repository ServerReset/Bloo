package com.bloo.wear

import com.bloo.bluelink.data.WearSyncResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Drive-sync result bus.
 *
 * A "Sync now" / drive-sync request is relayed to the phone, which does the work
 * and pushes back a [WearSyncResult] on
 * [com.bloo.bluelink.data.WearSync.PATH_SYNC_RESULT]. [WearListenerService]
 * receives that message — potentially with no UI attached — and forwards it here
 * so a live [WearViewModel] can turn a fire-and-forget request into a real
 * busy → success / failure outcome the user actually sees.
 *
 * Design notes (must survive rewrites):
 * - Emitter ([WearListenerService]) and collector ([WearViewModel]) share one
 *   process (no `android:process` override), so a plain in-memory
 *   [MutableSharedFlow] is enough — no disk round-trip for this one-shot,
 *   non-persisted event.
 * - `extraBufferCapacity = 1` lets [emit] complete without suspending if the
 *   result lands before a collector attaches, so a fast phone reply is never
 *   dropped.
 *
 * Mirrors [WearCommandEvents] / [WearAiEvents] / [WearAuthEvents].
 */
object WearSyncEvents {
    private val _results = MutableSharedFlow<WearSyncResult>(extraBufferCapacity = 1)

    /** Read-only stream the [WearViewModel] collects for drive-sync outcomes. */
    val results: SharedFlow<WearSyncResult> = _results.asSharedFlow()

    /** Forward a phone-relayed drive-sync result to the live ViewModel. */
    suspend fun emit(result: WearSyncResult) = _results.emit(result)
}
