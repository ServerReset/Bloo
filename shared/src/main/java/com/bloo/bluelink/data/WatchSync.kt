package com.bloo.bluelink.data

import kotlinx.serialization.Serializable

/**
 * The wire shape the phone PUSHES to a paired watch over the Wearable Data Layer, and the
 * watch MIRRORS. Deliberately a superset of what the watch UI needs right now, so a new field
 * does not require both apps to ship in lockstep.
 *
 * The watch is an AUXILIARY surface: it never talks to the network and never touches Drive.
 * Everything it shows comes from the phone through this payload, so the phone's own
 * [SnapshotStore] stays the single source of truth and a Drive file is never a watch concern.
 *
 * [pinRecord] is the phone's already-stretched PIN (salt + iterations + hash), not the PIN
 * itself -- the watch verifies locally against the same record without ever holding the
 * secret. Null when the phone has no PIN set.
 */
@Serializable
data class WatchSyncPayload(
    val vehicles: List<VehicleSnapshot> = emptyList(),
    val selectedVin: String? = null,
    /** The phone's WatchLockTiming wire value ("off"/"open"/"commands"/"both"). */
    val lockTiming: String = "off",
    val pinRecord: String? = null,
    /** Monotonic-ish stamp (phone wall clock) of when this payload was built, for "updated
     *  Xs ago" and for the watch to ignore an out-of-order stale push. */
    val sentAtMs: Long = 0L,
    /** The newest watch build the phone knows about, and the direct APK URL for it. Null when
     *  the phone has not seen a newer watch build than the one the watch runs. The watch shows
     *  an "Update" affordance and opens that URL in the system browser / package installer;
     *  the watch itself never checks for updates (no network). */
    val watchUpdateRunNumber: Int? = null,
    val watchUpdateApkUrl: String? = null,
    val watchUpdateNotes: String? = null,
)

/**
 * The Data Layer paths the sync uses. Kept in :shared so BOTH the phone (writer) and the watch
 * (reader) compile against the same constants and cannot drift.
 */
object WatchSyncProtocol {
    /** Phone → watch: the snapshot + lock config. Written by the phone, read by the watch. */
    const val PATH_SNAPSHOT = "/bloo/snapshot"
    /** Phone → watch: the full WATCH APK bytes for a seamless in-watch update. The phone
     *  downloads the watch APK (it has the network; the watch does not) and streams it here as
     *  an asset; the watch writes it to a cache file and hands it to the system package
     *  installer, so the user never leaves the watch or opens a browser. */
    const val PATH_WATCH_APK = "/bloo/watch_apk"
    /** Watch → phone: "please push me the watch APK". The phone then downloads it and pushes
     *  [PATH_WATCH_APK]. A no-op if nothing is newer. */
    const val PATH_REQUEST_APK = "/bloo/request_apk"
    /** Phone → watch: a command the watch wants run (see [WatchCommandRequest]). */
    const val PATH_COMMAND = "/bloo/command"
    /** Phone → watch: the result of a command the watch asked for, so the watch can clear its
     *  pending state and surface failures. */
    const val PATH_COMMAND_RESULT = "/bloo/command_result"
}

/**
 * A command request the watch sends to the phone to run. The watch never executes a car
 * command itself (no network, no session) -- it asks the phone, which runs the command through
 * its normal [CarCommandRunner] and pushes [WatchCommandResult] back.
 *
 * [requestId] is echoed in the result so a stale result cannot clear a newer request's pending
 * state.
 */
@Serializable
data class WatchCommandRequest(
    val requestId: String,
    val command: CarCommand,
)

/** The phone's answer to a [WatchCommandRequest]. */
@Serializable
data class WatchCommandResult(
    val requestId: String,
    val vin: String,
    val action: String,
    val ok: Boolean,
    val message: String? = null,
)
