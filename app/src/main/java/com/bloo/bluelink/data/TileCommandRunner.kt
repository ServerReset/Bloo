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

    /** Marks a climate target that carries an explicit temperature, e.g.
     *  "temp:64". See [runClimateStart]. */
    const val TEMP_PREFIX = "temp:"

    /** Appended to a [TEMP_PREFIX] target to also run the defroster. */
    const val DEFROST_SUFFIX = ":defrost"


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
     * Order of operations. Everything that touches the CAR's backend is inside
     * the [BlueLinkGate.statusMutex] critical section, so the
     * read->decide->dispatch->optimistic-write sequence is atomic and can't be
     * interleaved with another command or a status poll. Exactly one step runs
     * before the lock is taken, and step 0 is where it and its reasoning live:
     * 0. Resolve a "smart" climate target, which is the one command argument whose
     *    resolution needs the NETWORK -- a weather lookup, via
     *    [prepareSmartClimate]. It runs here, outside the lock, because
     *    Open-Meteo is not the car's backend: it shares no session, no account,
     *    and none of the 502-on-overlap behaviour the mutex exists to prevent, so
     *    serializing it against the car's requests buys nothing and costs
     *    everything. Inside the lock it was up to 35s -- [WeatherApi]'s own
     *    connect+read timeouts, generous on purpose -- during which one tile tap
     *    on a car with no signal blocked the phone UI, the watch, and every other
     *    surface in the process. Returns null when this command needs no weather,
     *    and also when
     *    the lookup simply fails -- step 4 raises the user-facing error in both
     *    cases, so nothing here has to distinguish them.
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
        // Step 0, before the lock: the weather lookup a "smart" target needs is a
        // request to Open-Meteo, not to the car, so it has no business inside a
        // mutex that exists to stop overlapping requests to the CAR. See
        // prepareSmartClimate.
        val smart = prepareSmartClimate(ctx, vin, cmd, climateTarget)
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
                    "climate" -> runClimate(ctx, repo, v, snap, climateTarget, smart)
                    "climate_on" -> runClimateStart(ctx, repo, v, snap, climateTarget, smart)
                    "climate_off" -> { repo.stopClimate(v); "Stopping climate" }
                    // No arguments, no state to predict: these two make the car
                    // do something audible/visible and change nothing that any
                    // surface displays, which is why they need no optimistic
                    // write below.
                    "lights" -> { repo.flashLights(v); "Flashing lights on ${v.name}" }
                    "horn" -> { repo.hornAndLights(v); "Sounding horn on ${v.name}" }
                    // The percentage rides in on the same string parameter the
                    // climate target uses -- it is the command's argument slot,
                    // and giving it a second one for the sake of naming would
                    // change every call site for one command's benefit. Both
                    // plug types are set together because setChargeTargets
                    // always sends both, and the API reports exactly these two.
                    "charge_limit" -> {
                        val pct = climateTarget.toIntOrNull()?.coerceIn(CHARGE_LIMIT_RANGE)
                            ?: error("Bad charge limit")
                        repo.setChargeTargets(v, pct, pct)
                        "Charge limit set to $pct% on ${v.name}"
                    }
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
     *    - "smart": a target temperature computed from live weather at the car's
     *      last-known lat/lon via [smartClimateTargetF]/[ambientFahrenheit], with
     *      defrost off and the app's default duration -- see
     *      [smartClimateRequest]. Normally this arrives already built, from step
     *      0's [prepareSmartClimate], because the weather lookup must not happen
     *      in here; the in-lock fallback and the two cases that reach it are
     *      documented at the branch itself. Fails if there is no location to ask
     *      about or no weather to be had.
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
        smart: ClimateRequest?,
    ): String {
        if (snap.climateOn == true) { repo.stopClimate(v); return "Stopping climate" }
        return runClimateStart(ctx, repo, v, snap, target, smart)
    }

    /**
     * The one climate target whose resolution needs the network: build the
     * "smart" [ClimateRequest] BEFORE [run] takes [BlueLinkGate.statusMutex], so
     * the weather lookup doesn't hold an app-wide lock on the car's session
     * hostage. Returns null whenever no such request is needed or one can't be
     * built; [runClimateStart] is what turns null into the user-facing error.
     *
     * The snapshot is read a second time here (the authoritative read is [run]'s,
     * inside the lock) because two of the three questions this has to answer are
     * about vehicle state. Both tolerate a stale answer, which is why this read
     * doesn't need the lock either:
     * - Is this even a climate START? "climate" is a toggle, so on a car whose
     *   last-known state is already on it means STOP, and a stop needs no
     *   temperature. Guessing from the stale snapshot is the point: guessing
     *   "start" when it's really a stop would make every smart-target stop tap
     *   wait out a weather fetch it will throw away, which is a worse bug than
     *   the one being fixed. Guessing "stop" when it's really a start falls back
     *   to fetching inside the lock in [runClimateStart] -- the very thing this
     *   function exists to avoid, but on that one tap rather than every tap. So
     *   this errs toward not fetching.
     *
     *   That mis-guess is not merely theoretical, which is why the fallback has to
     *   stay: the wait between this read and the locked one is the wait for the
     *   mutex, and whoever is holding it is a command or a status poll, i.e.
     *   precisely something that may be about to write `climateOn`.
     * - Where is the car? Only to pick a spot to ask about the weather. A car
     *   that moved between this read and the locked one moved far too little to
     *   change the ambient temperature.
     *
     * `isDriving` is deliberately NOT checked here even though it would let us
     * skip the fetch: it is the gate that decides whether the command is allowed
     * at all, and a stale read of it must never be what a refusal rests on. That
     * check stays where it is, on the authoritative snapshot inside the lock.
     */
    private suspend fun prepareSmartClimate(
        ctx: Context,
        vin: String,
        cmd: String,
        target: String,
    ): ClimateRequest? {
        if (target != "smart") return null
        if (cmd != "climate" && cmd != "climate_on") return null
        val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin } ?: return null
        if (cmd == "climate" && snap.climateOn == true) return null
        val lat = snap.lat ?: return null
        val lon = snap.lon ?: return null
        return WeatherApi.fetch(lat, lon)?.let(::smartClimateRequest)
    }

    /**
     * The smart-climate fallback that runs INSIDE [BlueLinkGate.statusMutex],
     * for the two cases [prepareSmartClimate] couldn't cover: the pre-lock
     * snapshot said this tap was a climate STOP and the authoritative one
     * disagrees, or the pre-lock lookup itself failed and this is where the user
     * finally gets told why. Also raises the "no location" error for a car that
     * has never reported a position, since that check belongs on the
     * authoritative snapshot rather than the stale one.
     *
     * It has no timeout, which is worth stating outright because a timeout is the
     * obvious thing to reach for here and it does not work. [WeatherApi.fetch] is
     * a `withContext` on the IO dispatcher wrapped around OkHttp's BLOCKING
     * `execute`, with no `callTimeout` and no suspension point inside. Structured
     * concurrency will not let `withTimeoutOrNull` resume its caller until that
     * child block has actually finished, and a blocking socket read has no
     * cancellation check at which to finish early -- so the mutex would still be
     * held for the full 35s and the only thing gained would be a comment claiming
     * otherwise. Bounding it for real means putting a `callTimeout` on a client
     * the phone UI and the watch share as well, which is a bigger change than a
     * lock-scope fix should make; and it is a good deal less urgent now that the
     * fetch on every normal tap happens before the lock is taken.
     */
    private suspend fun smartClimateInLock(snap: VehicleSnapshot): ClimateRequest {
        val lat = snap.lat
        val lon = snap.lon
        if (lat == null || lon == null) error("No location for smart climate")
        val w = WeatherApi.fetch(lat, lon) ?: error("No weather for smart climate")
        return smartClimateRequest(w)
    }

    /** The smart-climate request for a given weather reading. Extracted so the
     *  pre-lock path in [prepareSmartClimate] and the in-lock fallback in
     *  [runClimateStart] can't drift into choosing different temperatures for the
     *  same conditions -- see [smartClimateTargetF], which is itself shared with
     *  the phone UI and the watch so all four agree. */
    private fun smartClimateRequest(w: Weather): ClimateRequest = ClimateRequest(
        tempF = smartClimateTargetF(ambientFahrenheit(w.tempC)),
        defrost = false,
        durationMinutes = DEFAULT_CLIMATE_DURATION_MIN,
    )

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
        smart: ClimateRequest?,
    ): String {
        // The car rejects remote climate commands while it's moving (same
        // gate the main phone UI's AppViewModel.isDriving() already applies
        // to its own Start button) -- this was the one climate-starting path
        // with no such check at all, so a Quick Settings tile tap while
        // driving used to just silently fail against the car with no
        // explanation surfaced to the user.
        if (snap.isDriving) error("Can't start climate while driving")
        val req = when {
            // Normally already resolved before the lock was taken, by
            // prepareSmartClimate; see smartClimateInLock for when it wasn't.
            target == "smart" -> smart ?: smartClimateInLock(snap)
            // "temp:64" -- an explicit temperature in Fahrenheit, which is
            // what search produces for "start climate at the coldest
            // temperature on X". Additive to the existing string protocol
            // rather than a new parameter: every other caller (the tiles, the
            // watch) keeps passing what it always did, and a preset id can
            // never collide with this because ids are UUIDs.
            target.startsWith(TEMP_PREFIX) -> {
                // "temp:64" or "temp:82:defrost". Suffix rather than a second
                // prefix so the two can be asked for together, which is what
                // "defrost the windscreen" actually wants: heat AND defrost.
                val body = target.removePrefix(TEMP_PREFIX)
                val defrost = body.endsWith(DEFROST_SUFFIX)
                val f = body.removeSuffix(DEFROST_SUFFIX).toIntOrNull() ?: error("Bad temperature")
                ClimateRequest(
                    tempF = f.coerceIn(CLIMATE_TEMP_RANGE_F.first, CLIMATE_TEMP_RANGE_F.last),
                    defrost = defrost,
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
