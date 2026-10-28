package com.bloo.bluelink.data

import android.content.Context
import java.io.File
import java.io.FileWriter

/**
 * An append-only text log in the app's private files: it survives a restart and can be read back whole, for saving. It rotates once it
 * passes [maxBytes], keeping one previous generation beside it, so the newest [maxBytes] to twice that are always there. Appends are
 * quiet on failure: a log must never be the thing that breaks the app.
 */
internal class RotatingLog(
    private val fileName: String,
    private val folder: String,
    private val maxBytes: Long = 4L * 1024 * 1024,
) {
    @Volatile private var file: File? = null

    fun init(context: Context) {
        file = File(File(context.filesDir, folder).apply { mkdirs() }, fileName)
    }

    /** Where the current generation lives, for a message that says where to find it; null before [init]. */
    fun path(): String? = file?.absolutePath

    @Synchronized fun append(line: String) {
        val f = file ?: return
        runCatching {
            if (f.exists() && f.length() > maxBytes) {
                val old = File(f.parentFile, f.name + ".1")
                old.delete()
                f.renameTo(old)
            }
            FileWriter(f, true).use { it.append(line).append('\n') }
        }
    }

    /** Every line still kept, oldest first: the previous generation, then the current one. */
    @Synchronized fun readAll(): String {
        val f = file ?: return ""
        val old = File(f.parentFile, f.name + ".1")
        return buildString {
            if (old.exists()) append(runCatching { old.readText() }.getOrDefault(""))
            if (f.exists()) append(runCatching { f.readText() }.getOrDefault(""))
        }
    }
}
