package com.bloo.wear

import android.content.Context
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.WearClimateState
import com.bloo.bluelink.data.WearLocalPayload
import com.bloo.bluelink.data.WearPresets
import com.bloo.bluelink.data.WearCommandRunner
import com.bloo.bluelink.data.WearSync
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The watch's link to the car. Prefers relaying through a connected phone (which
 * holds the tested, authenticated session); falls back to running the command
 * itself with the synced session when no phone is reachable — that's the
 * standalone-on-Wi-Fi/cell path.
 *
 * Mechanism: this object talks to the phone through Android's Wearable Data
 * Layer API, which offers two distinct transports and this file deliberately
 * picks between them per call:
 *  - [Wearable.getMessageClient] ("MessageClient") sends a one-shot byte-array
 *    message directly to a specific, currently-connected node (see
 *    [phoneNodeId]). It requires a live connection right now -- if the phone
 *    isn't reachable the send simply fails -- but it's fire-and-forget/low
 *    latency and doesn't linger once delivered. Used here for commands
 *    ([relayCommand], [relayToPhone]) and sync requests ([requestSync]),
 *    where "the phone wasn't there" is a meaningful, actionable outcome (the
 *    watch falls back to running standalone instead).
 *  - [Wearable.getDataClient] ("DataClient") instead publishes a versioned
 *    "DataItem" keyed by a path (e.g. [WearSync.PATH_CLIMATE]); the system
 *    syncs the latest item to every node whenever they're next connected, and
 *    each node's own listener (a [WearListenerService] on the watch, the
 *    phone's counterpart) is notified of the change. This is used for
 *    published state that should always reflect "the latest known value"
 *    rather than a single event -- climate drafts, presets, pebble order,
 *    local settings, toggles -- and for [pullLatest], which reads back
 *    whatever DataItems currently exist so the UI has something to show
 *    immediately on launch, before the next live DataChanged callback fires.
 *  - Every path constant (PATH_COMMAND, PATH_CLIMATE, PATH_LOCAL, ...) lives
 *    in the shared [WearSync] object so both the phone and watch modules
 *    agree on routing; the receiving side switches on `item.uri.path` /
 *    `event.path` to dispatch to the right handler (see
 *    [WearListenerService] and the phone's equivalent listener).
 */
object WearComms {

    /** Outcome of [send]/[relayCommand] so callers can distinguish how a command
     *  was actually dispatched:
     *  - [RELAYED]: handed off to a reachable phone (which will report the real
     *    car outcome later via a result message) -- the caller's optimistic
     *    in-memory patch should stand until then.
     *  - [STANDALONE_OK]: no phone reachable, the watch ran it itself and the
     *    car accepted it -- the snapshot store already reflects the change.
     *  - [STANDALONE_FAILED]: ran standalone and the car rejected/was
     *    unreachable -- [runStandalone] has already reverted the optimistic
     *    flip, so the caller should surface the failure rather than re-patch. */
    enum class SendResult { RELAYED, STANDALONE_OK, STANDALONE_FAILED }

    /** The id of a connected phone node, or null when none is reachable.
     *  Queries [Wearable.getNodeClient] for all nodes currently paired with
     *  this watch, then prefers one flagged `isNearby` (in direct Bluetooth
     *  range, lowest latency) over any other reachable node (e.g. reachable
     *  only via cloud/Internet relay), falling back to whichever node is
     *  reported first if none is nearby. Returns null (rather than throwing)
     *  on any failure or timeout, which every caller treats as "no phone
     *  available -- go standalone". */
    suspend fun phoneNodeId(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 10, TimeUnit.SECONDS)
            nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id
        }.getOrNull()
    }

    /** Run a command: optimistic local flip, then relay to the phone (or execute
     *  it standalone). Split into [applyOptimistic] (fast, local, synchronous
     *  from the caller's point of view) and [relayCommand] (slow, network-bound)
     *  so callers that only need the local flip to have landed can await just
     *  that half -- see [applyOptimistic]'s own doc comment. */
    suspend fun send(context: Context, command: WearCommand): SendResult {
        val resolved = applyOptimistic(context, command)
        return relayCommand(context, resolved)
    }

    /**
     * Just the "resolve TOGGLE_* to an explicit LOCK/UNLOCK etc. and flip the
     * local snapshot" half of [send], split out so a caller that needs the
     * optimistic update to have landed before it does something else (e.g.
     * BlooTileService re-reading the store to render immediately) can await
     * just this part synchronously, then fire the slower network half in the
     * background via [relayCommand] instead of double-applying the update.
     *
     * Resolve TOGGLE_* to an explicit LOCK/UNLOCK etc. from the PRE-flip
     * snapshot. The standalone fallback's executor decides toggle direction
     * by re-reading the same store the optimistic write below lands in, so
     * relaying the raw toggle after flipping made every standalone toggle
     * execute the OPPOSITE action (tap Unlock -> car re-locks). Resolving
     * here also means the phone relay carries the direction the user
     * actually saw on the watch.
     */
    suspend fun applyOptimistic(context: Context, command: WearCommand): WearCommand = withContext(Dispatchers.IO) {
        var resolved = command
        runCatching {
            val store = SnapshotStore(context)
            store.current().vehicles.firstOrNull { it.vin == command.vin }?.let { snap ->
                resolved = command.copy(action = WearCommandRunner.resolveToggle(snap, command.action))
                // Optimistic update so the tile reacts the instant it's tapped.
                store.updateVehicle(WearCommandRunner.optimistic(snap, resolved.action))
            }
        }
        resolved
    }

    /** The network half of [send] -- relay an already-[applyOptimistic]-resolved
     *  command to the phone, or run it standalone if unreachable. */
    suspend fun relayCommand(context: Context, resolved: WearCommand): SendResult =
        withContext(Dispatchers.IO) {
            val node = phoneNodeId(context)
            if (node != null) {
                val relayed = runCatching {
                        Tasks.await(
                            Wearable.getMessageClient(context).sendMessage(
                                node, WearSync.PATH_COMMAND, WearSync.encodeCommand(resolved).toByteArray(),
                            ), 10, TimeUnit.SECONDS,
                        )
                }.isSuccess
                if (relayed) {
                    SendResult.RELAYED
                } else {
                    // Phone dropped mid-send — fall back to standalone.
                    if (runStandalone(context, resolved)) SendResult.STANDALONE_OK else SendResult.STANDALONE_FAILED
                }
            } else {
                if (runStandalone(context, resolved)) SendResult.STANDALONE_OK else SendResult.STANDALONE_FAILED
            }
        }

    /** Execute a command on the watch's own connection and, on failure, post a
     *  native watch notification — the phone isn't there to report the outcome.
     *  On failure this also reverts the optimistic flip [applyOptimistic] made
     *  earlier by writing the inverse action's optimistic state back into the
     *  snapshot store, so a UI that already jumped to "locked" because of the
     *  optimistic update flips back to "unlocked" once the real command is
     *  known to have failed, instead of showing a state that never actually
     *  happened on the car. */
    private suspend fun runStandalone(context: Context, command: WearCommand): Boolean {
        val result = WearCommandRunner.execute(context, command)
        if (!result.ok) {
            AppLog.log("⚠ Watch standalone command failed: ${command.action} → ${result.message}")
            runCatching {
                val store = SnapshotStore(context)
                store.current().vehicles.firstOrNull { it.vin == command.vin }?.let {
                    store.updateVehicle(WearCommandRunner.optimistic(it, WearCommandRunner.inverse(command.action)))
                }
            }
            WearNotifications.post(
                context,
                ("cmd" + command.vin + command.action).hashCode(),
                "Command failed",
                result.message ?: "Couldn't reach your car. Try again when your phone is nearby.",
            )
        } else {
            AppLog.log("Watch standalone: ${command.action} → ok")
        }
        return result.ok
    }

    /** Relay a phone-only request (e.g. AI summary) to a connected phone — no
     *  optimistic flip, no standalone fallback, since the watch can't fulfil it
     *  itself. Returns whether the phone received it. */
    suspend fun relayToPhone(context: Context, command: WearCommand): Boolean =
        withContext(Dispatchers.IO) {
            val node = phoneNodeId(context) ?: return@withContext false
            runCatching {
                Tasks.await(
                    Wearable.getMessageClient(context).sendMessage(
                        node, WearSync.PATH_COMMAND, WearSync.encodeCommand(command).toByteArray(),
                    ), 10, TimeUnit.SECONDS,
                )
            }.isSuccess
        }

    /**
     * Ask for fresh data: relay a refresh request to the phone (which has the
     * already-authenticated session), falling back to the watch's own
     * standalone connection whenever the phone can't be reached at all -- no
     * paired node, or the message send itself failed/timed out.
     *
     * @return true only if the phone itself actually received the request.
     * This is deliberately NOT "true if we got fresh data by any means" --
     * [WearViewModel.resync] relies on this exact meaning to tell the user
     * "bring your phone nearby to sync" specifically when the phone wasn't
     * reachable, even though the standalone fallback below may well have
     * quietly gotten them a partial update anyway. The fallback itself used
     * to only run when [refresh] was true (a forced live pull); the lighter
     * [refresh] == false case (just "resend whatever you already have," used
     * by [WearViewModel.resync]) had no fallback at all -- a failed send
     * there silently did nothing, dropping the request on the floor with no
     * compensating standalone attempt. `force = refresh` keeps the fallback's
     * own aggressiveness matched to what was actually asked for either way.
     */
    suspend fun requestSync(context: Context, vin: String, refresh: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val node = phoneNodeId(context)
            val command = WearCommand(vin = vin, action = if (refresh) WearAction.REFRESH else "")
            if (node != null) {
                val sent = runCatching {
                    Tasks.await(
                        Wearable.getMessageClient(context).sendMessage(
                            node, WearSync.PATH_SYNC_REQUEST, WearSync.encodeCommand(command).toByteArray(),
                        ), 10, TimeUnit.SECONDS,
                    )
                }.isSuccess
                if (!sent) WearCommandRunner.refresh(context, vin, force = refresh)
                sent
            } else {
                WearCommandRunner.refresh(context, vin, force = refresh)
                false
            }
        }

    /** Publish the watch's live climate draft so the phone mirrors it. Written as
     *  a DataItem on the shared [WearSync.PATH_CLIMATE] channel. `.setUrgent()`
     *  asks the system to sync this item as soon as possible rather than
     *  batching it with other pending Data Layer traffic -- climate is a live
     *  draft the user is actively dragging, so a delayed sync would make the
     *  phone's mirrored slider visibly lag behind the watch's. */
    suspend fun publishClimate(context: Context, state: WearClimateState) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = PutDataMapRequest.create(WearSync.PATH_CLIMATE).apply {
                    dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodeClimate(state))
                    dataMap.putLong(WearSync.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(context).putDataItem(request), 10, TimeUnit.SECONDS)
            }
        }
    }

    /** Publish presets the watch created/edited so the phone saves + mirrors them. */
    suspend fun publishPresets(context: Context, presets: WearPresets) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = PutDataMapRequest.create(WearSync.PATH_PRESETS).apply {
                    dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodePresets(presets))
                    dataMap.putLong(WearSync.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(context).putDataItem(request), 10, TimeUnit.SECONDS)
            }
        }
    }

    /** Publish a car's reordered pebble order so the phone saves it as that car's
     *  section order and mirrors it back to every device. Returns whether the
     *  Data Layer write succeeded, so the caller can drop its optimistic override
     *  when the phone will never see (and thus never echo) this order. */
    suspend fun publishPebbleOrder(context: Context, vin: String, order: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = com.bloo.bluelink.data.WearPebbleOrder(vin, order)
                val request = PutDataMapRequest.create(WearSync.PATH_PEBBLE_ORDER).apply {
                    dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodePebbleOrder(payload))
                    dataMap.putLong(WearSync.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(context).putDataItem(request), 10, TimeUnit.SECONDS)
            }.isSuccess
        }

    /** Push the watch's local display scale (and PIN-lock enabled/timing, for
     *  the phone's settings backup record -- see [WearLocalPayload]) back to
     *  the phone so the phone's Settings → Text scale slider stays in sync
     *  when changed on the watch. */
    suspend fun publishLocalSettings(
        context: Context,
        uiScale: Float,
        unitSystem: String? = null,
        pinLockEnabled: Boolean = false,
        pinLockTiming: String = "immediate",
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = PutDataMapRequest.create(WearSync.PATH_LOCAL).apply {
                    dataMap.putString(
                        WearSync.KEY_PAYLOAD,
                        WearSync.encodeLocal(
                            WearLocalPayload(
                                uiScale = uiScale,
                                unitSystem = unitSystem,
                                watchPinLockEnabled = pinLockEnabled,
                                watchPinLockTiming = pinLockTiming,
                            ),
                        ),
                    )
                    dataMap.putLong(WearSync.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(context).putDataItem(request), 10, TimeUnit.SECONDS)
            }
        }
    }

    /** Push a "turn AI summaries on/off" toggle back to the phone. Its own path
     *  (not [PATH_LOCAL]) so it can never race with that path's uiScale echo. */
    suspend fun publishAiToggle(context: Context, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = PutDataMapRequest.create(WearSync.PATH_AI_TOGGLE).apply {
                    dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodeAiToggle(com.bloo.bluelink.data.WearAiTogglePayload(enabled)))
                    dataMap.putLong(WearSync.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(context).putDataItem(request), 10, TimeUnit.SECONDS)
            }
        }
    }

    /** Push a "turn the aurora background on/off" toggle back to the phone,
     *  same own-path pattern as [publishAiToggle]. [colorMode] additionally
     *  sets the phone's aurora colour mode from the watch when non-null. */
    suspend fun publishAuroraToggle(context: Context, enabled: Boolean, colorMode: String? = null) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = PutDataMapRequest.create(WearSync.PATH_AURORA_TOGGLE).apply {
                    dataMap.putString(
                        WearSync.KEY_PAYLOAD,
                        WearSync.encodeAuroraToggle(com.bloo.bluelink.data.WearAuroraTogglePayload(enabled, colorMode)),
                    )
                    dataMap.putLong(WearSync.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(context).putDataItem(request), 10, TimeUnit.SECONDS)
            }
        }
    }

    /** On launch, pull whatever the phone already published so the UI isn't empty
     *  while waiting for the next DataChanged callback. */
    suspend fun pullLatest(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                val items = Tasks.await(Wearable.getDataClient(context).dataItems, 10, TimeUnit.SECONDS)
                try {
                    items.forEach { item ->
                        // Per-item guard (mirrors onDataChanged): one item failing to
                        // persist must not drop the rest of the cold-launch backfill.
                        runCatching {
                            val raw = DataMapItem.fromDataItem(item).dataMap.getString(WearSync.KEY_PAYLOAD)
                                ?: return@runCatching
                            when (item.uri.path) {
                                WearSync.PATH_STATE -> WearStateWriter.persistState(context, raw)
                                WearSync.PATH_AUTH -> WearStateWriter.persistAuth(context, raw)
                                WearSync.PATH_SETTINGS -> WearStateWriter.persistSettings(context, raw)
                                WearSync.PATH_PRESETS -> WearStateWriter.persistPresets(context, raw)
                                WearSync.PATH_CLIMATE -> WearStateWriter.persistClimate(context, raw)
                                WearSync.PATH_EXTRAS -> WearStateWriter.persistExtras(context, raw)
                            }
                        }
                    }
                } finally {
                    // DataItemBuffer holds a native Parcel-backed cursor; it must be
                    // released explicitly (it isn't a normal GC'd object) or its
                    // underlying resources leak -- the `finally` guarantees this runs
                    // even if persisting one of the items above throws.
                    items.release()
                }
            }
        }
    }
}
