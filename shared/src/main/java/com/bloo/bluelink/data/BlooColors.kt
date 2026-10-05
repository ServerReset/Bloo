package com.bloo.bluelink.data

/**
 * Semantic color constants shared across the phone app's surfaces. Stored as ARGB Int so callers in
 * non-Compose contexts (e.g. the notification builders) can use them directly.
 */
object BlooColors {
    // Each constant is a packed 32-bit ARGB value (alpha in the top byte, then red/green/blue),
    // written as an unsigned Long literal and narrowed with .toInt() because Kotlin Int literals
    // can't directly express values above 0x7FFFFFFF.
    const val chargeGreen     = 0xFF2EBD59.toInt() // battery/charge indicator, "good" state
    const val chargeGreenDark = 0xFF1B8A41.toInt() // darker variant for dark backgrounds/contrast
    const val chargeBlue      = 0xFF0A84FF.toInt()
    const val chargeBlueDark  = 0xFF0A5FBF.toInt() // darker variant for dark backgrounds/contrast
    const val heat            = 0xFFE5484D.toInt() // heating indicator / hot temp warning
    const val cool            = 0xFF2E78FF.toInt() // cooling indicator / cold temp
    const val tempMid         = 0xFF66BB6A.toInt() // mid-range cabin/outside temperature
    const val tempHot         = 0xFFFF5722.toInt() // high temperature alert color
    const val warn            = 0xFFF5A623.toInt() // generic warning/caution color
    const val brandAccent     = 0xFF7B83EB.toInt() // Bloo brand accent: notification tint
}
