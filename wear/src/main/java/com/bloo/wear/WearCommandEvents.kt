package com.bloo.wear

import com.bloo.bluelink.data.WearCommandResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process bridge from [WearListenerService] (which can receive a phone's
 * relayed-command result even with no UI open) to a live [WearViewModel].
 * Mirrors [WearSyncEvents]/[WearAiEvents] -- before this existed, a relayed
 * lock/climate/charge command's optimistic UI update was never reverted or
 * explained on failure: [WearViewModel.command]'s relay branch treated the
 * message *send* succeeding as "done" and had no ack channel back for
 * whether the phone's own execution actually worked, so a real failure (a
 * BlueLink 502, say) left the watch showing the wrong toggle state with no
 * error message until some unrelated PATH_STATE push happened to self-correct
 * it -- possibly never.
 */
object WearCommandEvents {
    private val _results = MutableSharedFlow<WearCommandResult>(extraBufferCapacity = 1)
    val results = _results.asSharedFlow()

    suspend fun emit(result: WearCommandResult) = _results.emit(result)
}
