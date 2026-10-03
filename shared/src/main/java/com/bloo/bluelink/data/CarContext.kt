package com.bloo.bluelink.data

import android.content.Context

/**
 * Everything a bare-Context car command needs, resolved once, in one place.
 *
 * Three callers repeat the same five-step preamble today — the tile/command runner, the
 * natural-language command path through [CarCommandRunner], and AutoLock's own status poll:
 *
 * 1. read the VIN's [VehicleSnapshot] from [SnapshotStore],
 * 2. fail over to "car not found" when it is gone,
 * 3. resolve the [Brand] from the snapshot's brand indicator,
 * 4. construct a fresh [SessionStore] + [CredentialStore] and a brand [VehicleRepository],
 * 5. take [BlueLinkGate.statusMutex] so overlapping requests can't 502.
 *
 * Copies of that sequence are exactly how a codebase drifts — a fix to one (the mutex the
 * command path skipped, the driving gate) silently missed the others. Resolving it here means
 * every new background command surface starts from this instead of re-typing the dance, and the
 * "every car request must be inside the gate" invariant is enforced by the helper rather than
 * remembered.
 *
 * [session] and [credentials] are created lazily inside [use] so a caller that ends up without
 * a snapshot (car not found) pays neither constructor — [CredentialStore] alone is a real
 * Keystore/EncryptedSharedPreferences open.
 */
class CarContext internal constructor(
    context: Context,
    val snapshot: VehicleSnapshot,
) {
    private val app = context.applicationContext

    /** The car this context is for, rebuilt as a command-capable [Vehicle]. */
    val vehicle: Vehicle = snapshot.toVehicle()

    val brand: Brand = Brand.fromIndicator(vehicle.brandIndicator)

    /** A fresh repository for this car's brand. Cheap to call more than once in one handler
     *  only because each call is used exactly once; the phone ViewModel keeps its own cache,
     *  which this deliberately does not replicate — bare-Context paths are short-lived. */
    fun repository(): VehicleRepository =
        repositoryFor(brand, SessionStore(app), CredentialStore(app))
}

/**
 * Resolve [vin] to a live [CarContext] — snapshot, vehicle, brand and a [VehicleRepository]
 * builder — or null when no car with that VIN is in the store.
 *
 * Does NOT take [BlueLinkGate.statusMutex]: callers that dispatch network work take the lock
 * around the dispatch themselves. A command runner that must keep its toggle-direction read
 * atomic with the dispatch reads the snapshot INSIDE its lock and passes it to
 * [carContextFrom]; this overload is for callers that only want to READ the snapshot first
 * (to decide whether anything is worth the lock at all) and so should not serialise behind a
 * status poll to get it.
 */
suspend fun carContext(context: Context, vin: String): CarContext? {
    val snap = SnapshotStore(context).current().vehicles.firstOrNull { it.vin == vin }
        ?: return null
    return CarContext(context, snap)
}

/** [carContext] for a snapshot already read (typically inside the caller's own lock, so the
 *  toggle direction stays atomic with the dispatch that follows it). */
fun carContext(context: Context, snapshot: VehicleSnapshot): CarContext = CarContext(context, snapshot)
