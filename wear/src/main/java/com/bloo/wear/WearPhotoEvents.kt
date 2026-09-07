package com.bloo.wear

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Photo-overwritten signal bus.
 *
 * [WearPhotoCache] writes every car's synced photo to a FIXED per-vin filename
 * (`car_$vin_synced.jpg`), so replacing one live-pushed photo with another leaves
 * [com.bloo.bluelink.data.WearExtras.images]`[vin]` -- the phone-side path string the UI keys
 * its decode on -- completely unchanged. A photo swap is therefore invisible to anything
 * watching only that string: [WearPhotoCache.ingest] already knows exactly which VINs it just
 * wrote new bytes for and returns that list, but [WearListenerService] used to discard it. This
 * bus is how that fact reaches a live [WearViewModel] instead, so it can bump a per-VIN
 * generation counter the UI's own decode key includes -- see [WearUi.photoGeneration] -- rather
 * than leaving the new photo undiscovered until the app happens to restart.
 *
 * Design notes (must survive rewrites):
 * - Emitter ([WearListenerService]) and collector ([WearViewModel]) share one process (no
 *   `android:process` override), so a plain in-memory [MutableSharedFlow] is enough -- no disk
 *   round-trip for a signal this transient.
 * - `extraBufferCapacity = 1` lets [emit] complete without suspending if a photo lands before a
 *   collector attaches (e.g. mid cold-start), so it is never silently dropped.
 * - Carries the changed VINs themselves, not just a bare ping (contrast [WearAuthEvents]): the
 *   collector needs to know WHICH car's photo moved to bump only that VIN's counter, not force
 *   every visible photo to redecode on every unrelated sync.
 *
 * Mirrors [WearSyncEvents] / [WearCommandEvents] / [WearAiEvents] / [WearAuthEvents].
 */
object WearPhotoEvents {
    private val _changed = MutableSharedFlow<List<String>>(extraBufferCapacity = 1)

    /** Read-only stream the [WearViewModel] collects to bump its per-VIN photo generation. */
    val changed: SharedFlow<List<String>> = _changed.asSharedFlow()

    /** Signal that [vins]' cached photo files were just overwritten with new bytes. */
    suspend fun emit(vins: List<String>) = _changed.emit(vins)
}
