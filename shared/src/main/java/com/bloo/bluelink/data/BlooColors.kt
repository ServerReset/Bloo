package com.bloo.bluelink.data

/**
 * Semantic color constants shared across all surfaces: phone app, watch app,
 * widget (Glance), QS tiles, and watch complications.
 *
 * Stored as ARGB Int so callers in non-Compose contexts (Glance, Protolayout)
 * can use them directly. Compose callers wrap with Color(BlooColors.chargeGreen).
 */
object BlooColors {
    const val chargeGreen     = 0xFF2EBD59.toInt()
    const val chargeGreenDark = 0xFF1B8A41.toInt()
    const val heat            = 0xFFE5484D.toInt()
    const val cool            = 0xFF2E78FF.toInt()
    const val tempMid         = 0xFF66BB6A.toInt()
    const val tempHot         = 0xFFFF5722.toInt()
    const val climateTeal     = 0xFF5DA3A3.toInt()
    const val warn            = 0xFFF5A623.toInt()
}
