package com.bloo.bluelink.data

/**
 * Semantic color constants shared across all surfaces: phone app, watch app,
 * widget (Glance), QS tiles, and watch complications.
 *
 * Stored as ARGB Int so callers in non-Compose contexts (Glance, Protolayout)
 * can use them directly. Compose callers wrap with Color(BlooColors.chargeGreen).
 */
object BlooColors {
    // Each constant is a packed 32-bit ARGB value (alpha in the top byte, then
    // red/green/blue), written as an unsigned Long literal and narrowed with
    // .toInt() because Kotlin Int literals can't directly express values above
    // 0x7FFFFFFF. This is the same bit layout android.graphics.Color / Compose
    // Color(Int) expect, so no conversion is needed at the call site.
    const val chargeGreen     = 0xFF2EBD59.toInt() // battery/charge indicator, "good" state
    const val chargeGreenDark = 0xFF1B8A41.toInt() // darker variant for dark backgrounds/contrast
    const val heat            = 0xFFE5484D.toInt() // heating indicator / hot temp warning
    const val cool            = 0xFF2E78FF.toInt() // cooling indicator / cold temp
    const val tempMid         = 0xFF66BB6A.toInt() // mid-range cabin/outside temperature
    const val tempHot         = 0xFFFF5722.toInt() // high temperature alert color
    const val climateTeal     = 0xFF5DA3A3.toInt() // neutral climate-control accent color
    const val warn            = 0xFFF5A623.toInt() // generic warning/caution color
}
