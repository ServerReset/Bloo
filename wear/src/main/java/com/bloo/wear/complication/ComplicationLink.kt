package com.bloo.wear.complication

import android.content.ComponentName
import android.content.Context
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

/**
 * Nudges every Bloo watch-face complication to re-read the latest snapshot after a
 * command or sync, so the charge arc / lock / climate icon stays fresh without
 * waiting for its next periodic update window. Each requester call is
 * runCatching-guarded so an unregistered or unavailable data source never breaks
 * the others.
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
