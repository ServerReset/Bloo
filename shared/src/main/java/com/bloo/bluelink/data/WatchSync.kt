package com.bloo.bluelink.data

import kotlinx.serialization.Serializable

/**
 * The wire shape the phone PUSHES to a paired watch over the Wearable Data Layer, and the watch
 * MIRRORS. The watch is an AUXILIARY surface: it never talks to the network and never touches
 * Drive.
 */
@Serializable
data class WatchSyncPayload(
    val vehicles: List<VehicleSnapshot> = emptyList(),
    val selectedVin: String? = null,
    /** The phone's WatchLockTiming wire value ("off"/"open"/"commands"/"both"). */
    val lockTiming: String = "off",
    val pinRecord: String? = null,
    val sentAtMs: Long = 0L,
    /**
     * The newest watch build the phone knows about, and the direct APK URL for it. Null when the
     * phone has not seen a newer watch build than the one the watch runs.
     */
    val watchUpdateRunNumber: Int? = null,
    val watchUpdateApkUrl: String? = null,
    val watchUpdateNotes: String? = null,
    /**
     * The phone's choices for the notifications the watch raises itself, so the two always agree
     * (the watch edits them too, via [WatchSyncProtocol.PATH_NOTIF_PREFS]).
     */
    val notify: WatchNotifyPrefs = WatchNotifyPrefs(),
)

/**
 * Which watch notifications are on. Shared so the phone's settings and the watch's toggles are one
 * thing.
 */
@Serializable
data class WatchNotifyPrefs(
    val charging: Boolean = true,
    val chargeComplete: Boolean = true,
    val lowBattery: Boolean = true,
)

/**
 * The Data Layer paths the sync uses. Kept in :shared so BOTH the phone (writer) and the watch
 * (reader) compile against the same constants and cannot drift.
 */
object WatchSyncProtocol {
    /** Phone → watch: the snapshot + lock config. Written by the phone, read by the watch. */
    const val PATH_SNAPSHOT = "/bloo/snapshot"
    /**
     * Phone → watch: the full WATCH APK bytes for a seamless in-watch update. The phone downloads
     * the watch APK (it has the network; the watch does not) and streams it here as an asset; the
     * watch writes it to a cache file and hands it to the system package installer, so the user
     * never leaves the watch or opens a browser.
     */
    const val PATH_WATCH_APK = "/bloo/watch_apk"
    /** Phone → watch: a command the watch wants run (see [WatchCommandRequest]). */
    const val PATH_COMMAND = "/bloo/command"
    /**
     * Phone → watch: the result of a command the watch asked for, so the watch can clear its
     * pending state and surface failures.
     */
    const val PATH_COMMAND_RESULT = "/bloo/command_result"
    /**
     * Phone → watch message: "I'd like to sign you in" -- the watch answers with [PATH_CRED_KEY].
     */
    /** Watch → phone message: a [WatchNotifyPrefs] (JSON) the user changed on the watch. */
    const val PATH_NOTIF_PREFS = "/bloo/notif_prefs"
    /**
     * Capability the watch app advertises, so the phone can tell a Bloo watch from any other Wear
     * device.
     */
    const val CAPABILITY_WATCH_APP = "bloo_watch_app"
    const val PATH_CRED_OFFER = "/bloo/cred_offer"
    /** Watch → phone message: the public half of the watch's Keystore transfer key. */
    const val PATH_CRED_KEY = "/bloo/cred_key"
    /**
     * Phone → watch message: the sealed [WatchCredentialBundle] (see [WatchCredentialTransfer]).
     */
    const val PATH_CRED_PAYLOAD = "/bloo/cred_payload"
    const val PATH_CRED_ACK = "/bloo/cred_ack"
}

/** A command request the watch sends to the phone to run. */
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
