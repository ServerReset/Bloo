package com.bloo.bluelink.autolock

import android.content.Context
import androidx.core.content.edit
import com.bloo.bluelink.data.privatePrefs

/**
 * The departures still waiting for a walk, kept on disk. An evaluation lives in the app's process, and Android can stop that process
 * between the Bluetooth disconnect and the walk (the app is in the background by then). The walk then arrives in a fresh process with
 * nothing in memory waiting for it, and the car was never locked. With the departure written here, that walk resumes it (see
 * [AutoLockController.onWalkingConfirmedAny]).
 */
internal object AutoLockPending {
    private const val PREFS = "bloo_autolock_pending"

    fun record(context: Context, vin: String, atMs: Long = System.currentTimeMillis()) {
        runCatching { context.privatePrefs(PREFS).edit { putLong(vin, atMs) } }
    }

    fun clear(context: Context, vin: String) {
        runCatching { context.privatePrefs(PREFS).edit { remove(vin) } }
    }

    /** The cars that left within [windowMs] of [nowMs] and have not been settled since, as the pure rule [stillOpen] decides. */
    fun open(context: Context, nowMs: Long, windowMs: Long): List<String> {
        val all = runCatching { context.privatePrefs(PREFS).all }.getOrNull().orEmpty()
        return all.mapNotNull { (vin, at) -> (at as? Long)?.takeIf { stillOpen(it, nowMs, windowMs) }?.let { vin } }
    }

    /** Whether a departure at [atMs] can still be confirmed by a walk at [nowMs]: not from the future, and within [windowMs]. */
    fun stillOpen(atMs: Long, nowMs: Long, windowMs: Long): Boolean = atMs <= nowMs && nowMs - atMs <= windowMs
}
