package com.bloo.bluelink.ui

/**
 * The pure reshapings between the in-memory [UiState] and the persisted / external
 * [VehicleSnapshot] form -- extracted from AppViewModel.kt so the mapping rules are pinnable on the
 * JVM and stable for every consumer.
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

    internal fun snapshotOf(v: Vehicle, status: VehicleStatus?, state: UiState): VehicleSnapshot {
        // Use the effective powertrain (a PHEV reads battery %, not fuel %).
        val hasBattery = state.hasBattery(v)
        // Same idea for generation: write the EFFECTIVE (override-applied) number, not the raw API
        // one, so every consumer that only ever sees this snapshot, never the live in-memory
        // Vehicle, agrees with the user's own correction via the exact same isGen5W numeric check
        // they already run on whatever Vehicle they rebuild from it.
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
        // `locate()` prefers the GPS carried by a status refresh, but falls back to
        // `repoFor(v).location(v)` (findMyCar) and stores that in `_state.locations` only -- and
        // Canada's repo has no GPS on its status at all, so that fallback is its ONLY source.
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
            // A non-null status is freshly-fetched data; null means we're building a placeholder
            // snapshot with no live status yet (leave fetchedAt unknown).
            fetchedAt = if (status != null) System.currentTimeMillis() else 0L,
            odometer = v.odometer,
            licensePlate = state.licensePlates[v.vin],
            lastServiceMiles = state.lastServiceMiles[v.vin],
            serviceIntervalMiles = state.serviceIntervalMiles[v.vin],
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
