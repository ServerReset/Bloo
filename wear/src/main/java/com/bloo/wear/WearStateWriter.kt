package com.bloo.wear

import android.content.Context
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearSync

/**
 * Inbound-state persister for the watch.
 *
 * The phone publishes each slice of state over the Wearable Data Layer as a raw
 * JSON string on a dedicated `WearSync.PATH_*`. Two call sites route those blobs
 * here, one entrypoint per path:
 *
 *   - [WearListenerService.onDataChanged] — live pushes while the watch is running.
 *   - [WearComms.pullLatest]              — cold-launch backfill of the last blob
 *                                            published on each path.
 *
 * Both dispatch through a `when(path)` block keyed on the frozen paths, so the
 * function names + signatures below are a contract with those callers: exactly
 * `persist{State,Auth,Settings,Presets,Climate,Extras}(context, raw)`.
 *
 * Every raw string is decoded through the FROZEN `:shared` [WearSync] codecs —
 * which are themselves `runCatching`-defensive and fall back to sane defaults on
 * garbage input — then folded into the watch's own L1 stores:
 *
 *   - vehicle snapshots → [SnapshotStore]                (the UI observes this)
 *   - auth sessions     → [SessionStore]                 (standalone auth)
 *   - settings/presets/climate/extras → the [WearSyncedStore] mirrors, which
 *     persist the raw JSON verbatim and re-decode on read, so the on-disk shape
 *     stays byte-identical to what the phone produced.
 *
 * Callers wrap each invocation in `runCatching`, so a single malformed blob can
 * throw here without aborting the rest of a multi-path publish burst; the guard
 * logic below is about *correctness of good data*, not exception safety.
 */
object WearStateWriter {

    /**
     * Vehicle snapshots (`PATH_STATE`).
     *
     * GUARD — empty-payload rejection: an unparseable / schema-broken blob decodes
     * to a payload with no vehicles. A stale car list beats a blank watch, so only
     * overwrite the store when the decode actually yielded vehicles; otherwise leave
     * whatever the watch already had on disk.
     *
     * [SnapshotStore.saveVehicles] preserves the watch's *own* selected VIN, so a
     * phone sync refreshes the data without yanking the watch onto whatever car the
     * phone happens to be showing.
     */
    suspend fun persistState(context: Context, raw: String) {
        val payload = WearSync.decodeState(raw)
        if (payload.vehicles.isNotEmpty()) {
            SnapshotStore(context).saveVehicles(payload.vehicles)
        }
    }

    /**
     * Auth sessions (`PATH_AUTH`).
     *
     * MERGE — the bundle is AUTHORITATIVE: it mirrors the phone's exact set of
     * logged-in brands. First clear any brand the watch still holds that the phone
     * no longer has (so a phone-side logout wipes the matching watch session, and an
     * empty bundle wipes everything), then upsert every brand the bundle carries.
     *
     * [SessionStore.save] only writes non-null optional fields (refreshToken,
     * deviceId), so a bundle that omits them won't clobber previously-stored values.
     */
    suspend fun persistAuth(context: Context, raw: String) {
        val bundle = WearSync.decodeAuth(raw)
        val store = SessionStore(context)

        val bundleBrands = bundle.sessions.map { Brand.fromName(it.brand) }.toSet()
        store.loggedInBrands()
            .filterNot { it in bundleBrands }
            .forEach { store.clear(it) }

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

    /**
     * Theme / units / per-car config (`PATH_SETTINGS`). Stored raw and re-decoded on
     * read via [WearSync.decodeSettings]; a missing/empty payload decodes to `null`.
     */
    suspend fun persistSettings(context: Context, raw: String) {
        WearSettingsStore(context).save(raw)
    }

    /** Per-VIN climate presets (`PATH_PRESETS`). Stored raw; decoded on read. */
    suspend fun persistPresets(context: Context, raw: String) {
        WearPresetsStore(context).save(raw)
    }

    /** Per-VIN active climate state (`PATH_CLIMATE`). Stored raw; decoded on read. */
    suspend fun persistClimate(context: Context, raw: String) {
        WearClimateStore(context).save(raw)
    }

    /** Weather / images / AI blob (`PATH_EXTRAS`). Stored raw; decoded on read. */
    suspend fun persistExtras(context: Context, raw: String) {
        WearExtrasStore(context).save(raw)
    }
}
