package com.bloo.wear

import com.bloo.bluelink.data.WearCommandResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Relayed-command result bus.
 *
 * When a lock / climate / charge command is relayed to the phone (rather than
 * run standalone), the phone executes it against BlueLink and pushes back a
 * [WearCommandResult] on [com.bloo.bluelink.data.WearSync.PATH_COMMAND_RESULT].
 * [WearListenerService] receives that ack — even with no UI attached — and
 * forwards it here to a live [WearViewModel].
 *
 * Why this bus exists (documents a real, hard-won fix — do NOT regress):
 * Before it, the relay branch treated the message *send* succeeding as "done"
 * and had no ack channel for whether the phone's own execution actually worked.
 * A real failure (e.g. a BlueLink 502) therefore left the watch showing the
 * optimistic toggle state — the wrong state — with no error message, until some
 * unrelated PATH_STATE push happened to self-correct it. Possibly never. This
 * ack lets the ViewModel revert the optimistic flip and surface a message.
 *
 * Design notes (must survive rewrites):
 * - Emitter and collector share one process (no `android:process` override), so
 *   a plain in-memory [MutableSharedFlow] is enough.
 * - `extraBufferCapacity = 1` lets [emit] complete without suspending if the
 *   ack lands before a collector attaches, so the result is never dropped.
 *
 * Mirrors [WearSyncEvents] / [WearAiEvents] / [WearAuthEvents].
 */
object WearCommandEvents {
    private val _results = MutableSharedFlow<WearCommandResult>(extraBufferCapacity = 1)

    /** Read-only stream the [WearViewModel] collects for relayed-command acks. */
    val results: SharedFlow<WearCommandResult> = _results.asSharedFlow()

    /** Forward a phone-relayed command result to the live ViewModel. */
    suspend fun emit(result: WearCommandResult) = _results.emit(result)
}
