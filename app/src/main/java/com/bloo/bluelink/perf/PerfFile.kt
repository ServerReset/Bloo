package com.bloo.bluelink.perf

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter

/**
 * The performance log as a file on the device, so every line survives the activity log's ring buffer and can be pulled off the phone
 * with `adb pull` (or copied from the Debug card). It is append-only, rotates at [MaxBytes] (one previous generation is kept), and every
 * line is stamped with the wall clock and the monitor's own uptime.
 */
internal object PerfFile {
    private const val MaxBytes = 4L * 1024 * 1024

    @Volatile private var file: File? = null

    /** Where the log lives: app files, so it is private to the app and survives a restart. */
    fun init(context: Context) {
        val dir = File(context.filesDir, "perf").apply { mkdirs() }
        file = File(dir, "bloo-perf.log")
    }

    /** The current log's path, for a message that says where to find it; null before [init]. */
    fun path(): String? = file?.absolutePath

    /** Appends one line, stamped. Quiet on failure: the log must never be the thing that breaks the app. */
    @Synchronized fun append(line: String) {
        val f = file ?: return
        runCatching {
            if (f.exists() && f.length() > MaxBytes) {
                val old = File(f.parentFile, f.name + ".1")
                old.delete()
                f.renameTo(old)
            }
            FileWriter(f, true).use { it.append(stamp()).append(' ').append(line).append('\n') }
        }
    }

    /**
     * A share sheet that sends the log file itself (mail, Drive, a message), so it can be read off the phone without a computer. Null
     * before the log has started. The file goes through the app's own FileProvider, which grants the receiver read access to that one file.
     */
    fun shareIntent(context: Context): Intent? {
        val f = file?.takeIf { it.exists() } ?: return null
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Bloo performance log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun stamp(): String = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
}
