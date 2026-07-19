package com.bloo.bluelink.ui

/**
 * The alpha floating chrome's own solid tint should use for its base fill --
 * a flat, frosted semi-transparent look used everywhere in the app (search
 * bar, floating buttons, pebble backgrounds, dialogs, widget). Liquid/Ultra
 * glass (a real hardware-blurred, refractive material) was removed in favor
 * of this simpler, consistent look across every device and surface.
 */
fun glassContainerAlpha(frosted: Float = 0.62f): Float = frosted
