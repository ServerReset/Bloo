package com.bloo.wear

import com.bloo.bluelink.data.WearAiResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process bridge from [WearListenerService] (which can receive an AI-summary
 * result even with no UI open) to a live [WearViewModel], so a "Summarize" tap
 * gets a real busy → success/failure result instead of leaving the spinner
 * stuck when generation is disabled, unsupported, or fails. See [WearSyncEvents]
 * for why a plain in-memory SharedFlow is enough here.
 */
object WearAiEvents {
    private val _results = MutableSharedFlow<WearAiResult>(extraBufferCapacity = 4)
    val results = _results.asSharedFlow()

    suspend fun emit(result: WearAiResult) = _results.emit(result)
}
