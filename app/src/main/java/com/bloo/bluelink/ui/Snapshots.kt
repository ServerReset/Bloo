package com.bloo.bluelink.ui

/**
 * The pure reshapings between the in-memory [UiState] and the persisted
 * / external [VehicleSnapshot] form (watch, widget, tile runners) --
 * extracted from AppViewModel.kt so the mapping rules are pinnable on
 * the JVM and stable for every consumer. See snapshotOf's own doc for
 * the effective-powertrain/generation logic: a consumer that only ever
 * sees a snapshot must agree with the user's override.
 */
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.toGeoLocation
import com.bloo.bluelink.data.VehiclePlatform
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.displayChargeLimit
import kotlinx.coroutines.flow.map

    internal fun snapshotOf(v: Vehicle, status: VehicleStatus?, state: UiState): VehicleSnapshot {
        // Use the effective powertrain (a PHEV reads battery %, not fuel %).
        val hasBattery = state.hasBattery(v)
        // Same idea for generation: write the EFFECTIVE (override-applied) number,
        // not the raw API one, so the watch/widget/tile runners -- which only ever
        // see this snapshot, never the live in-memory Vehicle -- agree with the
        // user's own correction via the exact same isGen5W numeric check they
        // already run on whatever Vehicle they rebuild from it. A no-op for a
        // vehicle where platformOverridable is false (Kia US, Canada, Europe):
        // isGen5W ignores the generation number outright for those, so which
        // string ends up here can't change anything either way.
        val effectiveGeneration = if (v.platformOverridable) {
            when (state.platformOf(v)) {
                VehiclePlatform.GEN5W -> "2"
                VehiclePlatform.CCNC -> "3"
            }
        } else {
            v.generation
        }
        val percent = status?.percentFor(hasBattery)
        val range = status?.rangeMiFor(hasBattery)
        // A fix that did NOT ride along on the status. `locate()` prefers the GPS carried
        // by a status refresh, but falls back to `repoFor(v).location(v)` (findMyCar) and
        // stores that in `_state.locations` only -- and Canada's repo has no GPS on its
        // status at all, so that fallback is its ONLY source. Reading `status` alone meant
        // every surface fed from a snapshot -- widget map, watch map, the location info
        // field -- showed no position for a car whose location the phone was displaying on
        // screen at that moment. Status wins when it has a coord (it is same-fetch fresh);
        // this is the fallback, not an override.
        //
        // Chosen as ONE fix rather than per-field `?:`, so a status carrying a lat but no
        // lon cannot combine with a cached lon into coordinates that were never a real
        // position. Same all-or-nothing shape `locate()` uses to build `statusLoc`.
        val fix = status.toGeoLocation() ?: state.locations[v.vin]
        return VehicleSnapshot(
            vin = v.vin,
            name = v.name,
            model = v.model,
            isEv = v.isEv,
            hasBattery = hasBattery,
            regId = v.regId,
            generation = effectiveGeneration,
            brandIndicator = v.brandIndicator,
            percent = percent,
            rangeMi = range,
            locked = status?.doorLock,
            charging = status?.evStatus?.batteryCharge,
            climateOn = status?.airCtrlOn,
            engineOn = status?.engine,
            lat = fix?.latitude,
            lon = fix?.longitude,
            speedMph = fix?.speed,
            updated = status?.dateTime,
            // A non-null status is freshly-fetched data; null means we're building a
            // placeholder snapshot with no live status yet (leave fetchedAt unknown).
            fetchedAt = if (status != null) System.currentTimeMillis() else 0L,
            odometer = v.odometer,
            licensePlate = state.licensePlates[v.vin],
            lastServiceMiles = state.lastServiceMiles[v.vin],
            serviceIntervalMiles = state.serviceIntervalMiles[v.vin],
            // displayChargeLimit, not targetForCurrentPlug directly -- see that
            // function's own doc: the widget/watch both read this field, and it used to
            // go null the instant the car was unplugged, silently dropping their charge
            // bars back to a plain unsplit track for every parked car.
            chargeLimitPct = status?.evStatus?.displayChargeLimit(),
        )
    }

    internal fun applyOrder(vehicles: List<Vehicle>, order: List<String>): List<Vehicle> {
        if (order.isEmpty()) return vehicles
        val byVin = vehicles.associateBy { it.vin }
        val ordered = order.mapNotNull { byVin[it] }
        val rest = vehicles.filter { it.vin !in order }
        return ordered + rest
    }

    internal fun electric(v: Vehicle, state: UiState) =
        if (state.hasBattery(v)) v.copy(isEv = true) else v
