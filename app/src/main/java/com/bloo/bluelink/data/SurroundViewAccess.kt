package com.bloo.bluelink.data

/**
 * Who sees the surround-view features (the car's photos, and the 3D surroundings built from them), chosen per car.
 * [AUTO] shows them on limited-trim ccNC cars, the ones whose head unit takes the photos; [ON] shows them on every car
 * whose brand has them; [OFF] hides them.
 */
enum class SurroundViewAccess { AUTO, ON, OFF }

/**
 * Whether a surround-view feature shows for a car. The brand must have it ([hasFeature]); then the person's choice decides.
 * Pure, so the rule is pinned by a plain JVM test.
 */
fun surroundViewShown(hasFeature: Boolean, platform: VehiclePlatform, trim: String?, access: SurroundViewAccess?): Boolean {
    if (!hasFeature) return false
    return when (access ?: SurroundViewAccess.AUTO) {
        SurroundViewAccess.AUTO -> platform == VehiclePlatform.CCNC && trim?.contains("LIMITED", ignoreCase = true) == true
        SurroundViewAccess.ON -> true
        SurroundViewAccess.OFF -> false
    }
}
