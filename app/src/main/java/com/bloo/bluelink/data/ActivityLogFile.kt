package com.bloo.bluelink.data

import android.content.Context

/**
 * The activity log on the device: every line [AppLog] takes, not only the newest the panel shows, so a long session can be saved in full.
 */
internal object ActivityLogFile {
    private val log = RotatingLog("bloo-activity.log", folder = "logs")

    fun init(context: Context) = log.init(context)

    fun append(line: String) = log.append(line)

    /** Every line still kept, oldest first. */
    fun readAll(): String = log.readAll()
}
