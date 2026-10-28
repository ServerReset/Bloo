package com.bloo.bluelink.perf

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.bloo.bluelink.data.RotatingLog
import java.io.File

/**
 * The performance log as a file on the device, so every line survives the activity log's ring buffer and can be pulled off the phone
 * with `adb pull`, saved from the Debug card, or shared. Every line is stamped with the wall clock.
 */
internal object PerfFile {
    private val log = RotatingLog("bloo-perf.log", folder = "perf")

    fun init(context: Context) = log.init(context)

    /** The current log's path, for a message that says where to find it; null before [init]. */
    fun path(): String? = log.path()

    /** Appends one stamped line. Quiet on failure: the log must never be the thing that breaks the app. */
    fun append(line: String) = log.append("${stamp()} $line")

    /** Every line still kept, oldest first. */
    fun readAll(): String = log.readAll()

    /**
     * A share sheet that sends the log file itself (mail, Drive, a message). Null before the log has started. The file goes through the
     * app's own FileProvider, which grants the receiver read access to that one file.
     */
    fun shareIntent(context: Context): Intent? {
        val f = path()?.let(::File)?.takeIf { it.exists() } ?: return null
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
