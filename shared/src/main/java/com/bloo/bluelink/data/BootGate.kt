package com.bloo.bluelink.data

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit

/**
 * "Sign in again after the phone restarts". When on (the default), the first launch after every
 * reboot is sealed: the encrypted vault hands out nothing but the app-lock records, and no
 * background work (refreshes, alerts, AutoLock, the watch) runs until the user has re-entered their
 * account credentials AND passed the app lock (fingerprint or PIN). Off, the app behaves as if
 * restarts changed nothing.
 *
 * A reboot is noticed by the system's boot counter; the counter value last opened is remembered in
 * plain prefs (it is not a secret).
 */
object BootGate {
    private const val PREFS = "bloo_boot_gate"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_OPEN_BOOT = "open_boot"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The system's reboot counter, or -1 when this device does not report one. */
    private fun bootCount(context: Context): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    /** Turning it on mid-session does not lock anything: the CURRENT boot counts as opened. */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) markOpen(context)
    }

    /** True while this boot has not been opened yet and the gate is on. */
    fun isLocked(context: Context): Boolean {
        if (!enabled(context)) return false
        val now = bootCount(context)
        if (now < 0) return false
        val p = prefs(context)
        // First run of a build that has the gate (or a fresh install): nothing to protect yet, and
        // locking the user out of an app they just installed would be wrong.
        if (!p.contains(KEY_OPEN_BOOT)) { p.edit { putInt(KEY_OPEN_BOOT, now) }; return false }
        return p.getInt(KEY_OPEN_BOOT, now) != now
    }

    /** The user finished the whole flow: this boot is open. */
    fun markOpen(context: Context) {
        val now = bootCount(context)
        if (now >= 0) prefs(context).edit { putInt(KEY_OPEN_BOOT, now) }
    }
}
