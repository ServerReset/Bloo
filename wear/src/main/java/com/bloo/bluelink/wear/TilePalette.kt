package com.bloo.bluelink.wear

/**
 * The colours every watch tile is drawn with, written once. The watch's own tiles (the hub and the climate tile) take these, so the two
 * never drift into different whites or mutes.
 */
internal object TilePalette {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val MUTED = 0xFFB8B8C4.toInt()
    const val RIM = 0x33FFFFFF
    const val BUTTON = 0xFF2A2A33.toInt()
}
