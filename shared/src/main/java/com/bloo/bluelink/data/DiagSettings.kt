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
/** The logging choice: nothing (the default), the activity log, or the activity log plus the performance and watch detail. */
enum class LogLevel { OFF, SIMPLE, ADVANCED }

object DiagSettings {
    @Volatile var logging: Boolean = false
        private set

    @Volatile var advanced: Boolean = false
        private set

    /** The current choice, read from the two switches. */
    val level: LogLevel get() = when {
        !logging -> LogLevel.OFF
        advanced -> LogLevel.ADVANCED
        else -> LogLevel.SIMPLE
    }

    private const val PREFS = "bloo_diag"

    /** Reads the saved switches. Call once, early, before anything logs. Off unless the person chose otherwise. */
    fun load(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        logging = p.getBoolean("logging", false)
        advanced = logging && p.getBoolean("advanced", false)
    }

    /** Sets the whole choice at once: Off, Simple, or Advanced. */
    fun setLevel(context: Context, level: LogLevel) {
        logging = level != LogLevel.OFF
        advanced = level == LogLevel.ADVANCED
        save(context)
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
