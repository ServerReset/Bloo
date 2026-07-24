package com.bloo.bluelink.data

import android.content.Context
import kotlinx.coroutines.sync.withLock

/**
 * Runs a Quick-Settings-tile command with the stored session and folds the
 * expected result into the [SnapshotStore] so the tile (and widgets) reflect the
 * new state immediately. Toggle commands flip against the last-known snapshot;
 * the climate command honours the tile's chosen target (a saved preset, smart
 * climate, or basic). Shared by the tile (background mode) and the transparent
 * tile-action activity (open-and-close mode).
 */
object TileCommandRunner {

    /** Outcome of a single tile command: whether it succeeded, plus a short
     *  human-readable status/error message suitable for a toast or log line. */
    data class Result(val ok: Boolean, val message: String)

    /**
     * Executes one Quick-Settings-tile command end to end and returns a
     * user-facing [Result]. Called from both the tile's background handler and
     * the transparent tile-action activity, so this is the single place that
     * mechanism (locking, optimistic snapshot update, error formatting) lives --
     * neither caller reimplements any of it.
     *
     * Order of operations (all inside the [BlueLinkGate.statusMutex] critical
     * section, so the read->decide->dispatch->optimistic-write sequence is
     * atomic and can't be interleaved with another command or a status poll):
     * 1. Acquire [BlueLinkGate.statusMutex] via [withLock] *before* reading any
     *    state or dispatching the command. This is the same app-wide mutex that
     *    [WearCommandRunner.execute] and the phone UI's own command path already
     *    take before talking to the car's backend. BlueLink's API returns 502s
     *    when it receives overlapping requests for the same account, so every
     *    command-issuing call site in the app serializes through this one lock
     *    to guarantee at most one in-flight request at a time. Before this
     *    change, tile taps were the one path that skipped the lock entirely --
     *    a tile tap racing a background status poll (or another command already
     *    in flight) had no protection and could trigger exactly that 502. Note
     *    this reuses the *status* mutex (not a separate command mutex): status
     *    refreshes and commands are treated as needing the same exclusion,
     *    since both are requests against the same BlueLink account session.
     * 2. Look up the car's last-known [VehicleSnapshot] from [SnapshotStore]. If
     *    it's not there (car removed, snapshot not yet populated), bail out with
     *    a "Car not found" failure before touching the network at all -- there's
     *    no vehicle to convert and no repo worth building. This read is done
     *    *inside* the lock so the toggle-direction decision it feeds can't race
     *    a concurrent flip: on a double-tap the second command sees the first
     *    command's optimistic write and toggles the correct direction.
     * 3. Build a fresh [VehicleRepository] for the car's brand, using whatever
     *    session/credentials are currently stored -- one repo per call, not
     *    cached, so it always reflects the latest signed-in session.
     * 4. Dispatch on [cmd]:
     *    - "doors" toggles lock state based on the *last-known* snapshot's
     *      `locked` flag (optimistic -- there's no fresh status fetch here), not
     *      a live re-check, since the point of a tile tap is a fast, cheap
     *      command dispatch, not a fresh network round-trip first.
     *    - "lock"/"unlock" are explicit, non-toggling variants of the same.
     *    - "charge" similarly toggles off the last-known `charging` flag;
     *      "charge_on"/"charge_off" are explicit, non-toggling variants that
     *      force the direction the user's words asked for (used by the settings
     *      search's "start/stop charging" phrasing) rather than re-deriving it
     *      from the snapshot.
     *    - "climate" delegates to [runClimate], which does its own extra checks
     *      (see below); "climate_on"/"climate_off" force start/stop directly
     *      ("climate_on" still honours the target + isDriving gate via
     *      [runClimateStart]), again for explicit start/stop phrasing.
     *    - Anything else silently no-ops to "Done" (defensive default; the tile
     *      vocabulary is a small closed set in practice).
     *    The whole `when` is wrapped in [runCatching] so a thrown exception
     *    (network failure, "can't start climate while driving", etc.) becomes a
     *    typed failure below rather than propagating out from inside the lock.
     * 5. On success: logs the resulting message, then -- best-effort via
     *    [runCatching] -- writes an optimistic snapshot update (see [optimistic])
     *    so the tile/widgets flip to the new state immediately rather than
     *    waiting for the next real status refresh to confirm it. This write is
     *    inside the lock too, so it's atomic with the read in step 2.
     * 6. On failure: formats a message (falling back to a generic "Command
     *    failed" if the exception carries none), logs it with a ⚠ marker
     *    including which command and car it was, and returns it as a failed
     *    [Result].
     *
     * The mutex is released automatically when [withLock]'s block returns, i.e.
     * after the success/failure post-processing in step 5/6 has run -- so the
     * snapshot read, the network dispatch, and the optimistic snapshot write all
     * happen inside the one critical section.
     */
    suspend fun run(ctx: Context, vin: String, cmd: String, climateTarget: String): Result {
        // Same lock WearCommandRunner.execute()/the phone UI's own command path
        // already take -- BlueLink 502s on overlapping requests for the same
        // account, and this was the one command-executing path (Quick Settings
        // tile taps) that skipped it, so a tile tap racing a background status
        // refresh or another in-flight command had no protection at all. The
        // snapshot read and optimistic write live inside the lock too, so the
        // toggle direction and the flip are atomic with the network dispatch.
        return BlueLinkGate.statusMutex.withLock {
            val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
                ?: return@withLock Result(false, "Car not found")
            val v = snap.toVehicle()
            val repo = repositoryFor(Brand.fromIndicator(v.brandIndicator), SessionStore(ctx), CredentialStore(ctx))
            runCatching {
                when (cmd) {
                    "doors" ->
                        if (snap.locked == true) { repo.unlock(v); "Unlocking ${v.name}" }
                        else { repo.lock(v); "Locking ${v.name}" }
                    "lock" -> { repo.lock(v); "Locking ${v.name}" }
                    "unlock" -> { repo.unlock(v); "Unlocking ${v.name}" }
                    "charge" ->
                        if (snap.charging == true) { repo.stopCharge(v); "Stopping charge" }
                        else { repo.startCharge(v); "Starting charge" }
                    "charge_on" -> { repo.startCharge(v); "Starting charge" }
                    "charge_off" -> { repo.stopCharge(v); "Stopping charge" }
                    "climate" -> runClimate(ctx, repo, v, snap, climateTarget)
                    "climate_on" -> runClimateStart(ctx, repo, v, snap, climateTarget)
                    "climate_off" -> { repo.stopClimate(v); "Stopping climate" }
                    else -> "Done"
                }
            }.fold(
                onSuccess = { msg ->
                    AppLog.log(msg)
                    // Best-effort: if this write fails, the real status poll will
                    // eventually correct the snapshot anyway, so it's not worth
                    // failing the whole command over.
                    runCatching { SnapshotStore(ctx).updateVehicle(optimistic(snap, cmd)) }
                    Result(true, msg)
                },
                onFailure = { e ->
                    val err = e.message ?: "Command failed"
                    AppLog.log("⚠ $err (${cmd} → ${v.name})")
                    Result(false, err)
                },
            )
        }
    }

    /**
     * Start/stop climate; when starting, resolve the tile's chosen target.
     * Called from inside [run]'s [BlueLinkGate.statusMutex] critical section, so
     * this itself does not (and must not) take the lock again.
     *
     * Mechanism:
     * 1. If the last-known snapshot says climate is already on, this is a stop
     *    request regardless of `target` -- stop and return immediately.
     * 2. Otherwise this is a start request, so first check `snap.isDriving`: the
     *    car's backend rejects remote climate-start commands while the vehicle
     *    is in motion, mirroring the same driving-gate check the main phone UI's
     *    `AppViewModel.isDriving()` already applies before its own in-app Start
     *    button. Before this check was added here, a Quick Settings tile tap
     *    while driving would dispatch the command anyway, have it silently
     *    rejected by the car, and surface no explanation to the user -- this
     *    throws a descriptive error instead, which `run()`'s `runCatching`/
     *    `fold` turns into a proper failure [Result] message.
     * 3. Resolves what climate request to actually send, based on the tile's
     *    configured `target`:
     *    - "smart": needs the car's last-known lat/lon (fails if either is
     *      missing) plus a live weather fetch for that location (fails if the
     *      fetch fails) to compute a target temperature via
     *      [smartClimateTargetF]/[ambientFahrenheit] against current ambient
     *      conditions, with defrost off and the app's default duration.
     *    - any other id that isn't "default": looks up that saved preset by id
     *      in [SettingsStore.climatePresets] for this VIN and uses its stored
     *      [ClimateRequest] verbatim; fails if the preset no longer exists
     *      (e.g. deleted since the tile was configured).
     *    - "default" (or anything falling through): a fixed default request
     *      (default temp, no defrost, default duration).
     * 4. Dispatches the resolved request via [repo.startClimate] and reports
     *    "Starting climate".
     */
    private suspend fun runClimate(
        ctx: Context,
        repo: VehicleRepository,
        v: Vehicle,
        snap: VehicleSnapshot,
        target: String,
    ): String {
        if (snap.climateOn == true) { repo.stopClimate(v); return "Stopping climate" }
        return runClimateStart(ctx, repo, v, snap, target)
    }

    /**
     * Force-start climate for the "climate_on" command (explicit start phrasing
     * from settings search). This is the start half of [runClimate] with the
     * on/off toggle removed -- it still applies the same `isDriving` gate and
     * target resolution, but never falls back to stopping, since the caller has
     * already committed to starting regardless of the last-known on/off flag.
     * Also called by [runClimate] once it has decided this is a start request.
     */
    private suspend fun runClimateStart(
        ctx: Context,
        repo: VehicleRepository,
        v: Vehicle,
        snap: VehicleSnapshot,
        target: String,
    ): String {
        // The car rejects remote climate commands while it's moving (same
        // gate the main phone UI's AppViewModel.isDriving() already applies
        // to its own Start button) -- this was the one climate-starting path
        // with no such check at all, so a Quick Settings tile tap while
        // driving used to just silently fail against the car with no
        // explanation surfaced to the user.
        if (snap.isDriving) error("Can't start climate while driving")
        val req = when {
            target == "smart" -> {
                val lat = snap.lat
                val lon = snap.lon
                if (lat == null || lon == null) error("No location for smart climate")
                val w = WeatherApi.fetch(lat, lon) ?: error("No weather for smart climate")
                ClimateRequest(
                    tempF = smartClimateTargetF(ambientFahrenheit(w.tempC)),
                    defrost = false,
                    durationMinutes = DEFAULT_CLIMATE_DURATION_MIN,
                )
            }
            target != "default" -> {
                val preset = SettingsStore(ctx).climatePresets(v.vin).firstOrNull { it.id == target }
                    ?: error("Preset unavailable")
                preset.request
            }
            else -> ClimateRequest(tempF = DEFAULT_CLIMATE_TEMP_F, defrost = false, durationMinutes = DEFAULT_CLIMATE_DURATION_MIN)
        }
        repo.startClimate(v, req)
        return "Starting climate"
    }

    /**
     * Short "doing it" toast text for a tap, based on the last-known state.
     * This is purely a UI-feedback label shown immediately on tap (before
     * [run]'s network call even starts), computed from the same "toggle against
     * last-known snapshot" logic [run] itself uses -- so the toast text and the
     * command actually dispatched agree on which direction ("locking" vs.
     * "unlocking", etc.) is about to happen. `snap` may be null (e.g. no
     * snapshot yet cached), in which case every toggle defaults to its "starting"
     * phrasing via Kotlin's `== true` short-circuiting to false for a null flag.
     */
    fun ackText(cmd: String, snap: VehicleSnapshot?): String = when (cmd) {
        "doors" -> if (snap?.locked == true) "Unlocking…" else "Locking…"
        "lock" -> "Locking…"
        "unlock" -> "Unlocking…"
        "climate" -> if (snap?.climateOn == true) "Stopping climate…" else "Starting climate…"
        "climate_on" -> "Starting climate…"
        "climate_off" -> "Stopping climate…"
        "charge" -> if (snap?.charging == true) "Stopping charge…" else "Starting charge…"
        "charge_on" -> "Starting charge…"
        "charge_off" -> "Stopping charge…"
        else -> "Sending…"
    }

    /** The snapshot a tile command is expected to produce, for instant feedback.
     *  Delegates to [WearCommandRunner.optimistic] (mapping the tile's own
     *  "doors"/"lock"/"unlock"/"charge"/"charge_on"/"charge_off"/"climate"/
     *  "climate_on"/"climate_off" vocabulary onto [WearAction]) instead of
     *  re-deriving the same lock/charge/climate flips independently -- this used
     *  to be a byte-for-byte duplicate of that function. */
    fun optimistic(snap: VehicleSnapshot, cmd: String): VehicleSnapshot {
        val action = when (cmd) {
            "doors" -> WearAction.TOGGLE_LOCK
            "lock" -> WearAction.LOCK
            "unlock" -> WearAction.UNLOCK
            "charge" -> WearAction.TOGGLE_CHARGE
            "charge_on" -> WearAction.CHARGE_ON
            "charge_off" -> WearAction.CHARGE_OFF
            "climate" -> WearAction.TOGGLE_CLIMATE
            "climate_on" -> WearAction.CLIMATE_ON
            "climate_off" -> WearAction.CLIMATE_OFF
            else -> return snap
        }
        return WearCommandRunner.optimistic(snap, action)
    }
}
