package com.bloo.wear

import android.content.Context
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearAiTogglePayload
import com.bloo.bluelink.data.WearAuroraTogglePayload
import com.bloo.bluelink.data.WearClimateState
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.WearCommandRunner
import com.bloo.bluelink.data.WearLocalPayload
import com.bloo.bluelink.data.WearPebbleOrder
import com.bloo.bluelink.data.WearPresets
import com.bloo.bluelink.data.WearSync
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The watch's link to the car. Prefers relaying through a connected phone (which
 * holds the tested, authenticated session); falls back to running the command
 * itself with the synced session when no phone is reachable — that's the
 * standalone-on-Wi-Fi/cell path.
 *
 * Mechanism: this object talks to the phone through Android's Wearable Data
 * Layer API, which offers two distinct transports, and this file deliberately
 * picks between them per call:
 *  - [Wearable.getMessageClient] ("MessageClient") sends a one-shot byte-array
 *    message directly to a specific, currently-connected node (see
 *    [phoneNodeId]). It requires a live connection right now — if the phone
 *    isn't reachable the send simply fails — but it's fire-and-forget / low
 *    latency and doesn't linger once delivered. Used here for commands
 *    ([relayCommand], [relayToPhone]) and sync requests ([requestSync]), where
 *    "the phone wasn't there" is a meaningful, actionable outcome (the watch
 *    falls back to running standalone instead). See [sendMessage].
 *  - [Wearable.getDataClient] ("DataClient") instead publishes a versioned
 *    "DataItem" keyed by a path (e.g. [WearSync.PATH_CLIMATE]); the system
 *    syncs the latest item to every node whenever they're next connected, and
 *    each node's own listener (a [WearListenerService] on the watch, the
 *    phone's counterpart) is notified of the change. Used for published state
 *    that should always reflect "the latest known value" rather than a single
 *    event — climate drafts, presets, pebble order, local settings, toggles —
 *    and for [pullLatest], which reads back whatever DataItems currently exist
 *    so the UI has something to show immediately on launch, before the next
 *    live DataChanged callback fires. See [publishDataItem].
 *  - Every path constant (PATH_COMMAND, PATH_CLIMATE, PATH_LOCAL, …) lives in
 *    the shared [WearSync] object so both the phone and watch modules agree on
 *    routing; the receiving side switches on `item.uri.path` / `event.path` to
 *    dispatch to the right handler (see [WearListenerService] and the phone's
 *    equivalent listener).
 *
 * Every Data Layer await uses a 10-second timeout so a wedged transport can
 * never block a command coroutine indefinitely — a stuck send just becomes a
 * failure the caller can react to.
 */
object WearComms {

    /** How long any single Data Layer [Tasks.await] is allowed to block. */
    private const val TIMEOUT_SECONDS = 10L

    /** Attempts per Data Layer write, and the first backoff between them
     *  (doubling: 400ms, then 800ms). Mirrors the phone's WearBridge.putItem. */
    private const val PUBLISH_ATTEMPTS = 3
    private const val PUBLISH_RETRY_MS = 400L

    /** Outcome of [send]/[relayCommand] so callers can distinguish how a command
     *  was actually dispatched:
     *  - [RELAYED]: handed off to a reachable phone (which will report the real
     *    car outcome later via a result message) — the caller's optimistic
     *    in-memory patch should stand until then.
     *  - [STANDALONE_OK]: no phone reachable, the watch ran it itself and the
     *    car accepted it — the snapshot store already reflects the change.
     *  - [STANDALONE_FAILED]: ran standalone and the car rejected / was
     *    unreachable — [runStandalone] has already reverted the optimistic
     *    flip, so the caller should surface the failure rather than re-patch. */
    enum class SendResult { RELAYED, STANDALONE_OK, STANDALONE_FAILED }

    // ── Node discovery ──────────────────────────────────────────────────────

    /** The id of a connected phone node, or null when none is reachable.
     *  Queries [Wearable.getNodeClient] for all nodes currently paired with this
     *  watch, then prefers one flagged `isNearby` (in direct Bluetooth range,
     *  lowest latency) over any other reachable node (e.g. reachable only via a
     *  cloud/Internet relay), falling back to whichever node is reported first
     *  if none is nearby. Returns null (rather than throwing) on any failure or
     *  timeout, which every caller treats as "no phone available — go
     *  standalone". */
    suspend fun phoneNodeId(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val nodes = Tasks.await(
                Wearable.getNodeClient(context).connectedNodes,
                TIMEOUT_SECONDS, TimeUnit.SECONDS,
            )
            nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id
        }.getOrNull()
    }

    // ── Commands ────────────────────────────────────────────────────────────

    /** Run a command: optimistic local flip, then relay to the phone (or execute
     *  it standalone). Split into [applyOptimistic] (fast, local, synchronous
     *  from the caller's point of view) and [relayCommand] (slow, network-bound)
     *  so callers that only need the local flip to have landed can await just
     *  that half — see [applyOptimistic]'s own doc comment. */
    suspend fun send(context: Context, command: WearCommand): SendResult {
        val resolved = applyOptimistic(context, command)
        return relayCommand(context, resolved)
    }

    /**
     * What [applyOptimistic] produced: the [command] to actually send, and
     * [previous] -- the snapshot field's value from BEFORE the optimistic flip
     * overwrote it.
     *
     * The two travel together because reverting a failed command needs both, and
     * only the first was being carried. [previous] is deliberately nullable and a
     * null is meaningful: it says the car had never reported that field, so a
     * failure must restore "unknown" rather than invent a definite state. See
     * [WearCommandRunner.stateFor].
     */
    data class Optimistic(val command: WearCommand, val previous: Boolean?)

    /**
     * Just the "resolve TOGGLE_* to an explicit LOCK/UNLOCK etc. and flip the
     * local snapshot" half of [send], split out so a caller that needs the
     * optimistic update to have landed before it does something else (e.g.
     * BlooTileService re-reading the store to render immediately) can await just
     * this part synchronously, then fire the slower network half in the
     * background via [relayCommand] instead of double-applying the update.
     *
     * Resolve TOGGLE_* to an explicit LOCK/UNLOCK etc. from the PRE-flip
     * snapshot. The standalone fallback's executor decides toggle direction by
     * re-reading the same store the optimistic write below lands in, so relaying
     * the raw toggle after flipping made every standalone toggle execute the
     * OPPOSITE action (tap Unlock → car re-locks). Resolving here also means the
     * phone relay carries the direction the user actually saw on the watch.
     */
    suspend fun applyOptimistic(context: Context, command: WearCommand): Optimistic =
        withContext(Dispatchers.IO) {
            var out = Optimistic(command, previous = null)
            runCatching {
                val store = SnapshotStore(context)
                store.current().vehicles.firstOrNull { it.vin == command.vin }?.let { snap ->
                    val resolved = command.copy(action = WearCommandRunner.resolveToggle(snap, command.action))
                    // Captured from `snap`, i.e. BEFORE the flip on the next line
                    // lands. This is the only moment the real value is still
                    // available -- optimistic() writes an absolute, so nothing
                    // afterwards can tell whether the field had been unknown.
                    out = Optimistic(resolved, WearCommandRunner.stateFor(snap, resolved.action))
                    // Optimistic update so the tile reacts the instant it's tapped.
                    store.updateVehicle(WearCommandRunner.optimistic(snap, resolved.action))
                }
            }
            out
        }

    /** The network half of [send] — relay an already-[applyOptimistic]-resolved
     *  command to the phone, or run it standalone if the phone is unreachable or
     *  drops the message mid-send. */
    suspend fun relayCommand(context: Context, resolved: Optimistic): SendResult =
        withContext(Dispatchers.IO) {
            val node = phoneNodeId(context)
            val relayed = node != null &&
                sendMessage(context, node, WearSync.PATH_COMMAND, WearSync.encodeCommand(resolved.command).toByteArray())
            when {
                relayed -> SendResult.RELAYED
                // No phone, or the phone dropped mid-send — fall back to standalone.
                runStandalone(context, resolved) -> SendResult.STANDALONE_OK
                else -> SendResult.STANDALONE_FAILED
            }
        }

    /** Execute a command on the watch's own connection and, on failure, post a
     *  native watch notification — the phone isn't there to report the outcome.
     *  On failure this also reverts the optimistic flip [applyOptimistic] made
     *  earlier, by writing back the value [Optimistic.previous] captured before that
     *  flip -- so a UI that already jumped to "locked" returns to whatever was
     *  actually there once the command is known to have failed, instead of showing a
     *  state that never happened on the car.
     *
     *  It used to revert by applying the inverse verb's optimistic write, which is
     *  an undo only when the flip changed something. On a car that had never
     *  reported its doors the flip wrote `true` over a null and the inverse wrote
     *  `false`, so a failed command left the watch stating that a car it knew
     *  nothing about was unlocked. Restoring the captured value puts the null back. */
    private suspend fun runStandalone(context: Context, sent: Optimistic): Boolean {
        val command = sent.command
        val result = WearCommandRunner.execute(context, command)
        if (!result.ok) {
            AppLog.log("⚠ Watch standalone command failed: ${command.action} → ${result.message}")
            runCatching {
                val store = SnapshotStore(context)
                store.current().vehicles.firstOrNull { it.vin == command.vin }?.let {
                    store.updateVehicle(WearCommandRunner.withState(it, command.action, sent.previous))
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
            sendMessage(context, node, WearSync.PATH_COMMAND, WearSync.encodeCommand(command).toByteArray())
        }

    // ── Setup / sync requests ────────────────────────────────────────────────

    /** Ask the phone to finish setting up sign-in — the "Set up on phone" handoff.
     *  Sends an EMPTY, credential-free trigger on [WearSync.PATH_SETUP_REQUEST];
     *  the phone decides what to do (push auth if signed in, else prompt on the
     *  phone). Returns false when no phone node is reachable (the caller then
     *  keeps the on-watch credential fields available). */
    suspend fun requestSetupOnPhone(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            val node = phoneNodeId(context) ?: return@withContext false
            sendMessage(context, node, WearSync.PATH_SETUP_REQUEST, ByteArray(0))
        }

    /**
     * Ask for fresh data: relay a refresh request to the phone (which has the
     * already-authenticated session), falling back to the watch's own standalone
     * connection whenever the phone can't be reached at all — no paired node, or
     * the message send itself failed/timed out.
     *
     * @return true only if the phone itself actually received the request. This
     * is deliberately NOT "true if we got fresh data by any means" —
     * [WearViewModel.resync] relies on this exact meaning to tell the user
     * "bring your phone nearby to sync" specifically when the phone wasn't
     * reachable, even though the standalone fallback below may well have quietly
     * gotten them a partial update anyway. The fallback itself used to only run
     * when [refresh] was true (a forced live pull); the lighter [refresh] ==
     * false case (just "resend whatever you already have," used by
     * [WearViewModel.resync]) had no fallback at all — a failed send there
     * silently did nothing, dropping the request on the floor with no
     * compensating standalone attempt. `force = refresh` keeps the fallback's
     * own aggressiveness matched to what was actually asked for either way.
     */
    suspend fun requestSync(context: Context, vin: String, refresh: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val node = phoneNodeId(context)
            val command = WearCommand(vin = vin, action = if (refresh) WearAction.REFRESH else WearAction.RESYNC)
            val sent = node != null &&
                sendMessage(context, node, WearSync.PATH_SYNC_REQUEST, WearSync.encodeCommand(command).toByteArray())
            // Standalone fallback whenever the phone didn't receive it — matched
            // in aggressiveness to what was asked for (force = refresh).
            if (!sent) WearCommandRunner.refresh(context, vin, force = refresh)
            sent
        }

    // ── Published DataItems (watch → phone mirror) ───────────────────────────

    /** Publish the watch's live climate draft so the phone mirrors it. Written as
     *  a DataItem on the shared [WearSync.PATH_CLIMATE] channel — climate is a
     *  live draft the user is actively dragging, so [publishDataItem]'s urgent
     *  sync keeps the phone's mirrored slider from visibly lagging. */
    suspend fun publishClimate(context: Context, state: WearClimateState) {
        publishDataItem(context, WearSync.PATH_CLIMATE, WearSync.encodeClimate(state))
    }

    /** Publish presets the watch created/edited so the phone saves + mirrors them. */
    suspend fun publishPresets(context: Context, presets: WearPresets) {
        publishDataItem(context, WearSync.PATH_PRESETS, WearSync.encodePresets(presets))
    }

    /** Publish a car's reordered pebble order so the phone saves it as that car's
     *  section order and mirrors it back to every device. Returns whether the
     *  Data Layer write succeeded, so the caller can drop its optimistic override
     *  when the phone will never see (and thus never echo) this order. */
    suspend fun publishPebbleOrder(context: Context, vin: String, order: List<String>): Boolean =
        publishDataItem(context, WearSync.PATH_PEBBLE_ORDER, WearSync.encodePebbleOrder(WearPebbleOrder(vin, order)))

    /** Push the watch's local display scale (and PIN-lock enabled/timing, for the
     *  phone's settings backup record — see [WearLocalPayload]) back to the phone
     *  so the phone's Settings → Text scale slider stays in sync when changed on
     *  the watch. */
    suspend fun publishLocalSettings(
        context: Context,
        uiScale: Float,
        unitSystem: String? = null,
        pinLockEnabled: Boolean = false,
        pinLockTiming: String = "immediate",
    ) {
        publishDataItem(
            context,
            WearSync.PATH_LOCAL,
            WearSync.encodeLocal(
                WearLocalPayload(
                    uiScale = uiScale,
                    unitSystem = unitSystem,
                    watchPinLockEnabled = pinLockEnabled,
                    watchPinLockTiming = pinLockTiming,
                ),
            ),
        )
    }

    /** Push a "turn AI summaries on/off" toggle back to the phone. Its own path
     *  (not [WearSync.PATH_LOCAL]) so it can never race with that path's uiScale
     *  echo. */
    suspend fun publishAiToggle(context: Context, enabled: Boolean): Boolean =
        publishDataItem(context, WearSync.PATH_AI_TOGGLE, WearSync.encodeAiToggle(WearAiTogglePayload(enabled)))

    /** Push a "turn the aurora background on/off" toggle back to the phone, same
     *  own-path pattern as [publishAiToggle]. [colorMode] additionally sets the
     *  phone's aurora colour mode from the watch when non-null. */
    suspend fun publishAuroraToggle(context: Context, enabled: Boolean, colorMode: String? = null): Boolean =
        publishDataItem(
            context,
            WearSync.PATH_AURORA_TOGGLE,
            WearSync.encodeAuroraToggle(WearAuroraTogglePayload(enabled, colorMode)),
        )

    // ── Cold-launch backfill ─────────────────────────────────────────────────

    /** On launch, pull whatever the phone already published so the UI isn't empty
     *  while waiting for the next DataChanged callback. */
    suspend fun pullLatest(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                val items = Tasks.await(
                    Wearable.getDataClient(context).dataItems,
                    TIMEOUT_SECONDS, TimeUnit.SECONDS,
                )
                try {
                    items.forEach { item ->
                        // Per-item guard (mirrors onDataChanged): one item failing
                        // to persist must not drop the rest of the cold-launch
                        // backfill.
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
                    // DataItemBuffer holds a native Parcel-backed cursor; it must
                    // be released explicitly (it isn't a normal GC'd object) or its
                    // underlying resources leak — the `finally` guarantees this
                    // runs even if persisting one of the items above throws.
                    items.release()
                }
            }
        }
    }

    // ── Private transport helpers ────────────────────────────────────────────

    /** Fire one MessageClient message to [node] on [path] and report whether it
     *  was delivered within [TIMEOUT_SECONDS]. Any failure/timeout is folded to
     *  `false` so callers can branch to a standalone fallback. Caller already
     *  runs on [Dispatchers.IO]. */
    private suspend fun sendMessage(context: Context, node: String, path: String, payload: ByteArray): Boolean =
        runCatching {
            Tasks.await(
                Wearable.getMessageClient(context).sendMessage(node, path, payload),
                TIMEOUT_SECONDS, TimeUnit.SECONDS,
            )
        }.isSuccess

    /** Publish [payload] as an urgent DataItem on [path]. `.setUrgent()` asks the
     *  system to sync this item as soon as possible rather than batching it with
     *  other pending Data Layer traffic — every published channel here (climate
     *  drafts, presets, pebble order, local settings, the AI/aurora toggles) is
     *  something the user just changed and expects the phone to mirror promptly.
     *  A monotonic [WearSync.KEY_TIMESTAMP] is stamped so a byte-identical repeat
     *  still publishes as a *changed* item. Returns whether the write succeeded
     *  (used by [publishPebbleOrder]; ignored by the Unit-returning publishers).
     *  Runs on [Dispatchers.IO]. */
    private suspend fun publishDataItem(context: Context, path: String, payload: String): Boolean =
        withContext(Dispatchers.IO) {
            val request = PutDataMapRequest.create(path).apply {
                dataMap.putString(WearSync.KEY_PAYLOAD, payload)
                dataMap.putLong(WearSync.KEY_TIMESTAMP, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            // Retried, and logged when it still fails -- the phone half does the
            // same (see WearBridge.putItem). This one carries the watch's own
            // edits: a reordered pebble stack, a preset the user just saved, a
            // climate draft. Every one of those has an optimistic override on
            // the watch waiting for the phone to echo it back, so a write that
            // silently didn't happen leaves the watch showing a change the phone
            // will never confirm -- and the caller can't tell, because a single
            // swallowed failure returns exactly what a real one does.
            var lastError: Throwable? = null
            repeat(PUBLISH_ATTEMPTS) { attempt ->
                val outcome = runCatching {
                    Tasks.await(
                        Wearable.getDataClient(context).putDataItem(request),
                        TIMEOUT_SECONDS, TimeUnit.SECONDS,
                    )
                }
                if (outcome.isSuccess) return@withContext true
                lastError = outcome.exceptionOrNull()
                if (attempt < PUBLISH_ATTEMPTS - 1) delay(PUBLISH_RETRY_MS shl attempt)
            }
            AppLog.log("⚠ Watch publish failed ($path): ${lastError?.message}")
            false
        }
}
