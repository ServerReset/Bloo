package com.bloo.bluelink.ui

import android.content.Context
import com.bloo.bluelink.data.SurroundCapture
import java.io.File

/**
 * Every surround-view set the car has sent, kept on this phone (per car), newest first. Bluelink keeps only the latest, so the photos
 * are saved as they arrive. Each one is a file named by when the car took it.
 */
internal object SurroundHistory {
    private fun dir(context: Context, vin: String): File =
        File(context.filesDir, "surround/" + vin.filter { it.isLetterOrDigit() }).apply { mkdirs() }

    /** Keeps each capture not already saved. */
    fun save(context: Context, vin: String, captures: List<SurroundCapture>) {
        val folder = dir(context, vin)
        captures.forEach { c ->
            val name = surroundPhotoName(c.utcTime)
            val file = File(folder, name)
            if (!file.exists()) file.writeBytes(c.jpeg)
        }
    }

    /** Every saved set for [vin], newest first. */
    fun load(context: Context, vin: String): List<SurroundCapture> {
        val folder = dir(context, vin)
        return folder.listFiles().orEmpty()
            .filter { it.name.endsWith(".jpg") }
            .sortedByDescending { it.name }
            .map { f -> SurroundCapture(utcTime = f.name.removePrefix("surround_").removeSuffix(".jpg"), jpeg = f.readBytes(), imageSize = emptyList()) }
    }
}
