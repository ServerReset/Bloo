package com.bloo.wear

import com.bloo.bluelink.data.WearSyncResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process bridge from [WearListenerService] (which can receive a Drive-sync
 * result even with no UI open) to a live [WearViewModel], so a "Sync now" tap
 * gets a real busy → success/failure result instead of firing and forgetting.
 * [WearListenerService] runs in the same process as the rest of the watch app
 * (no `android:process` override), so a plain in-memory SharedFlow is enough —
 * no need to round-trip through disk for a one-shot, non-persisted event.
 */
object WearSyncEvents {
    private val _results = MutableSharedFlow<WearSyncResult>(extraBufferCapacity = 1)
    val results = _results.asSharedFlow()

    suspend fun emit(result: WearSyncResult) = _results.emit(result)
}
