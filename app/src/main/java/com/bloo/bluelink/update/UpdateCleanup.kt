package com.bloo.bluelink.update

import android.content.Context
import com.bloo.bluelink.data.AppLog
import java.io.File

/**
 * Housekeeping for the update download cache.
 *
 * A downloaded update APK is staged in `cacheDir/apk/Bloo.apk` and handed to the
 * installer; nothing else in the app ever reads it back, and the next update simply
 * overwrites it. Left alone it lingers as a multi-MB file for the life of the install
 * (and a failed download can leave a stray `Bloo.apk.tmp` beside it), so it is cleared
 * on every app start -- the update checker re-downloads on demand, so there is nothing
 * to keep between sessions. Best-effort: a file the OS has locked is skipped, not fatal.
 */
object UpdateCleanup {
    /** The one directory every staged update file lives in ([BlooApplication], [UpdateApi]). */
    private fun apkDir(context: Context): File = File(context.cacheDir, "apk")

    /**
     * Delete every staged update download. Safe to call any time -- the ONLY consumer,
     * the installer hand-off, reads the file immediately after a download in the same
     * session, so nothing depends on it surviving a process restart.
     */
    fun clearStagedDownloads(context: Context) {
        val dir = apkDir(context.applicationContext)
        if (!dir.exists()) return
        runCatching {
            val files = dir.listFiles().orEmpty()
            if (files.isEmpty()) return
            var freed = 0L
            files.forEach { f ->
                val len = f.length()
                if (f.delete()) freed += len
            }
            if (freed > 0) AppLog.log("Update cleanup: removed ${freed / 1024}KB of staged downloads.")
        }.onFailure { AppLog.log("Update cleanup failed: ${it.javaClass.simpleName}") }
    }
}
