package com.bloo.bluelink.data

import android.content.Context

/**
 * The app's two logging switches, kept on this device.
 *
 * - **Logging** (the activity log, on by default): the app's own plain-text log. It is cheap, and it is the first thing to read when
 *   something goes wrong. Off, nothing is written to it.
 * - **Advanced logging** (off by default, needs Logging): the performance monitor's detailed timing (every frame over budget, every
 *   main-thread span, its watchdog and probes) and the watch's own log. It makes the app slower while it is on, so it is only for a
 *   diagnosis, and the phone turns the watch's part on and off with it.
 */
object DiagSettings {
    @Volatile var logging: Boolean = true
        private set

    @Volatile var advanced: Boolean = false
        private set

    private const val PREFS = "bloo_diag"

    /** Reads the saved switches. Call once, early, before anything logs. */
    fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        logging = p.getBoolean("logging", true)
        advanced = logging && p.getBoolean("advanced", false)
    }

    /** Turns normal logging on or off. Turning it off also turns advanced logging off, since advanced logging is built on it. */
    fun setLogging(context: Context, on: Boolean) {
        logging = on
        if (!on) advanced = false
        save(context)
    }

    /** Turns advanced logging on or off. It can only be on while normal logging is. */
    fun setAdvanced(context: Context, on: Boolean) {
        advanced = on && logging
        save(context)
    }

    private fun save(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("logging", logging)
            .putBoolean("advanced", advanced)
            .apply()
    }
}
