package com.bloo.wear

import android.content.Context
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearSync

/**
 * Folds data the phone publishes into the watch's own on-disk stores, so the UI
 * (which observes [SnapshotStore]) updates and standalone mode has a session.
 */
object WearStateWriter {

    suspend fun persistState(context: Context, raw: String) {
        val payload = WearSync.decodeState(raw)
        // Keep the watch's *own* car selection — saveVehicles preserves it — so a
        // phone sync doesn't yank the watch to whatever car the phone is showing.
        SnapshotStore(context).saveVehicles(payload.vehicles)
    }

    suspend fun persistAuth(context: Context, raw: String) {
        val bundle = WearSync.decodeAuth(raw)
        val store = SessionStore(context)
        bundle.sessions.forEach { s ->
            store.save(
                SessionStore.Session(
                    accessToken = s.accessToken,
                    refreshToken = s.refreshToken,
                    username = s.username,
                    pin = s.pin,
                    brand = Brand.fromName(s.brand),
                    deviceId = s.deviceId,
                )
            )
        }
    }
}
