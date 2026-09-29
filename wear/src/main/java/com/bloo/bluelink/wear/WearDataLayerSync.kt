package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.SnapshotStore

/**
 * Placeholder for the Wearable Data Layer bridge that keeps the watch's snapshot in step
 * with the phone's when they are separate devices.
 *
 * Today both processes already read the SAME [SnapshotStore] file, which is what makes the
 * watch "live sync" in the common same-device/emulator case (a status the phone's
 * background workers mirror into shows up on the watch with no extra plumbing). For a real
 * paired watch the phone must push each snapshot over the Data Layer
 * (`com.google.android.gms:play-services-wearable`, `DataClient.putDataItem`) and the watch
 * mirrors it back into its own [SnapshotStore].
 *
 * Left as an explicit seam rather than a half-wired implementation: adding the play-services
 * dependency and the putDataItem/listener pair is the whole task, and it belongs on top of
 * this class so [WearSnapshotRepository] and the UI never change. See the module README.
 */
object WearDataLayerSync {

    /** Wearable Data Layer path the phone would write each snapshot payload to. */
    const val SNAPSHOT_PATH = "/bloo/snapshot"

    /**
     * Begin mirroring the phone's pushed snapshot into this device's [SnapshotStore].
     * No-op until the Data Layer client is wired in; kept so the call site exists.
     */
    fun start(context: Context) {
        // TODO(data-layer): register a DataClient.OnDataChangedListener for [SNAPSHOT_PATH]
        // and, on each push, decode the payload and write it through
        // SnapshotStore(context).saveVehicles(...)/updateVehicles(...).
        SnapshotStore(context) // touched so the import is real and the seam is obvious.
    }
}
