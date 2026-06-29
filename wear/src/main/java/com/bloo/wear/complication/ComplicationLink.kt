package com.bloo.wear.complication

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/**
 * Small helper to nudge the watch-face complication to re-read the latest
 * snapshot after a command or sync, so the charge/fuel arc stays fresh without
 * waiting for its periodic update window.
 */
object ComplicationLink {
    fun requestUpdate(context: Context) {
        listOf(
            ChargeComplication::class.java,
            LockComplication::class.java,
            ClimateComplication::class.java,
        ).forEach { cls ->
            runCatching {
                ComplicationDataSourceUpdateRequester
                    .create(context, ComponentName(context, cls))
                    .requestUpdateAll()
            }
        }
    }
}
