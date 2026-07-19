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
 */
object WearComms {

    /** The id of a connected phone node, or null when none is reachable. */
    suspend fun phoneNodeId(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 10, TimeUnit.SECONDS)
            nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id
        }.getOrNull()
    }

    /** Run a command: optimistic local flip, then relay to the phone (or execute
     *  it standalone). */
    suspend fun send(context: Context, command: WearCommand) {
        val resolved = applyOptimistic(context, command)
        relayCommand(context, resolved)
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
    suspend fun relayCommand(context: Context, resolved: WearCommand) {
        withContext(Dispatchers.IO) {
            val node = phoneNodeId(context)
            if (node != null) {
                runCatching {
                        Tasks.await(
                            Wearable.getMessageClient(context).sendMessage(
                                node, WearSync.PATH_COMMAND, WearSync.encodeCommand(resolved).toByteArray(),
                            ), 10, TimeUnit.SECONDS,
                        )
                }.onFailure {
                    // Phone dropped mid-send — fall back to standalone.
                    runStandalone(context, resolved)
                }
            } else {
                runStandalone(context, resolved)
            }
        }
    }

    /** Execute a command on the watch's own connection and, on failure, post a
     *  native watch notification — the phone isn't there to report the outcome. */
    private suspend fun runStandalone(context: Context, command: WearCommand) {
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

    /** Ask for fresh data: relay a refresh to the phone, or refresh standalone. */
    /** @return true if the phone actually got (or, for [refresh], a standalone
     *  fallback compensated for) this request. [refresh] == false (used by
     *  [WearViewModel.resync] to just ask the phone to push whatever it
     *  already has) has no standalone fallback -- a send failure there used
     *  to do nothing at all, silently dropping the sync request with no
     *  signal back to the caller that "resync finished" didn't mean "resync
     *  worked". */
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
                if (!sent && refresh) WearCommandRunner.refresh(context, vin)
                sent || refresh
            } else if (refresh) {
                WearCommandRunner.refresh(context, vin)
                true
            } else {
                false
            }
        }

    /** Publish the watch's live climate draft so the phone mirrors it. Written as
     *  a DataItem on the shared [WearSync.PATH_CLIMATE] channel. */
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
                        val raw = DataMapItem.fromDataItem(item).dataMap.getString(WearSync.KEY_PAYLOAD)
                            ?: return@forEach
                        when (item.uri.path) {
                            WearSync.PATH_STATE -> WearStateWriter.persistState(context, raw)
                            WearSync.PATH_AUTH -> WearStateWriter.persistAuth(context, raw)
                            WearSync.PATH_SETTINGS -> WearStateWriter.persistSettings(context, raw)
                            WearSync.PATH_PRESETS -> WearStateWriter.persistPresets(context, raw)
                            WearSync.PATH_CLIMATE -> WearStateWriter.persistClimate(context, raw)
                            WearSync.PATH_EXTRAS -> WearStateWriter.persistExtras(context, raw)
                        }
                    }
                } finally {
                    items.release()
                }
            }
        }
    }
}
