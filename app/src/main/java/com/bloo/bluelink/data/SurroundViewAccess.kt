package com.bloo.bluelink.data

/**
 * Who sees the surround-view features (the car's photos, and the 3D surroundings built from them), chosen per car.
 * [AUTO] shows them on limited-trim cars; [ON] shows them on every car whose brand has them; [OFF] hides them.
 * Either way the car needs a ccNC head unit, and a car that Bluelink has refused is hidden whatever the choice.
 */
enum class SurroundViewAccess { AUTO, ON, OFF }

/**
 * Whether a surround-view feature shows for a car.
 * - [hasFeature]: the brand has the feature at all.
 * - [platform]: the head unit must be ccNC; Gen5W head units do not take the photos.
 * - [refused]: Bluelink has already said this car does not take them (the car's own answer about its hardware).
 * Pure, so the rule is pinned by a plain JVM test.
 */
fun surroundViewShown(
    hasFeature: Boolean,
    platform: VehiclePlatform,
    trim: String?,
    access: SurroundViewAccess?,
    refused: Boolean,
): Boolean {
    if (!hasFeature || refused || platform != VehiclePlatform.CCNC) return false
    return when (access ?: SurroundViewAccess.AUTO) {
        SurroundViewAccess.AUTO -> trim?.contains("LIMITED", ignoreCase = true) == true
        SurroundViewAccess.ON -> true
        SurroundViewAccess.OFF -> false
    }
}
