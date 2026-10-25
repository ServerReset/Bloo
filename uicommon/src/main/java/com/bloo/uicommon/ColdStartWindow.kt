package com.bloo.uicommon

/**
 * The app's first moments, when its entrance animations are welcome: the page the user opens on fills its charge bar, staggers in
 * its pebbles and fades in its photo. After the window closes, a page that composes later -- the next car in a swipe -- shows its
 * final state at once instead of replaying the intro as it slides into view. The clock starts the first time anything asks.
 */
object ColdStartWindow {
    private const val WindowMs = 2_500L
    private var openedAtMs = 0L

    fun isOpen(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (openedAtMs == 0L) openedAtMs = now
        return now - openedAtMs < WindowMs
    }
}
