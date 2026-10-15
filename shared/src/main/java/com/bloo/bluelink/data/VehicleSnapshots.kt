package com.bloo.bluelink.data

/**
 * The ACCOUNT's vehicle list as a persisted [VehicleSnapshot].
 *
 * Deliberately minimal, and the reason it exists separately from `ui/Snapshots.kt`'s `snapshotOf`: that
 * one also carries live status, the located GPS fix and the user's own per-car overrides, and it needs
 * a `UiState` to do it. This is for the one caller that has no UI state at all and still needs a car in
 * the store -- a background evaluation resolving a VIN that the cached snapshot has never seen.
 *
 * [hasBattery] defaults to [Vehicle.isEv], which is [VehicleSnapshot]'s own default: a snapshot built
 * without the powertrain override behaves exactly as before.
 */
fun Vehicle.toSnapshot(hasBattery: Boolean = isEv): VehicleSnapshot = VehicleSnapshot(
    vin = vin,
    name = name,
    model = model,
    isEv = isEv,
    hasBattery = hasBattery,
    regId = regId,
    generation = generation,
    brandIndicator = brandIndicator,
)
