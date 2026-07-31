package com.bloo.wear

import com.bloo.bluelink.data.WearAiResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * AI-summary result bus.
 *
 * A "Summarize" tap is relayed to the phone, which generates the summary and
 * pushes back a [WearAiResult] on
 * [com.bloo.bluelink.data.WearSync.PATH_AI_RESULT]. [WearListenerService]
 * receives it — even with no UI attached — and forwards it here so a live
 * [WearViewModel] resolves the request to a real busy → success / failure state
 * instead of leaving the spinner stuck when generation is disabled,
 * unsupported, or fails.
 *
 * Design notes (must survive rewrites):
 * - Emitter and collector share one process (no `android:process` override), so
 *   a plain in-memory [MutableSharedFlow] is enough — see [WearSyncEvents].
 * - `extraBufferCapacity = 4` (deliberately larger than the other buses' 1):
 *   AI results can arrive in bursts across multiple vehicles, so extra slack
 *   avoids a suspending [emit] having to wait on a slow collector and keeps
 *   per-VIN results from stepping on each other.
 *
 * Mirrors [WearSyncEvents] / [WearCommandEvents] / [WearAuthEvents].
 */
object WearAiEvents {
    private val _results = MutableSharedFlow<WearAiResult>(extraBufferCapacity = 4)

    /** Read-only stream the [WearViewModel] collects for AI-summary results. */
    val results: SharedFlow<WearAiResult> = _results.asSharedFlow()

    /** Forward a phone-relayed AI-summary result to the live ViewModel. */
    suspend fun emit(result: WearAiResult) = _results.emit(result)
}
