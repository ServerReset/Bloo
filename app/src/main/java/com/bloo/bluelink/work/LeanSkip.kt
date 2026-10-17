package com.bloo.bluelink.work

import android.content.Context
import androidx.core.content.edit
import com.bloo.bluelink.data.DeviceStatus
import com.bloo.uicommon.content

/**
 * The periodic workers are scheduled once at a fixed interval, so the device's lean mode (see
 * [com.bloo.bluelink.data.PowerPolicy.pollIntervalMultiplier]) is applied here: when lean, a run that
 * comes sooner than [baseIntervalMs] times the multiplier after the last real one does nothing. Returns
 * true to skip this run.
 */
internal fun skipWhenLean(context: Context, name: String, baseIntervalMs: Long): Boolean {
    val scale = DeviceStatus.policy(context).pollIntervalMultiplier
    val prefs = context.applicationContext.getSharedPreferences("bloo_lean_runs", Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val last = prefs.getLong(name, 0L)
    // A few minutes of slack: WorkManager runs periodic work flexibly, not on the dot.
    if (scale > 1 && now - last < baseIntervalMs * scale - SLACK_MS) return true
    prefs.edit { putLong(name, now) }
    return false
}

private const val SLACK_MS = 5 * 60_000L
