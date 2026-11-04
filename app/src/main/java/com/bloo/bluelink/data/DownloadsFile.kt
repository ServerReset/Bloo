package com.bloo.bluelink.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Writes [text] into the device's Downloads folder as [name], without a file picker. Returns where it landed, or null (and logs why)
 * if it could not be written. From Android 10 this goes through MediaStore, which needs no storage permission.
 */
internal fun saveToDownloads(context: Context, name: String, text: String): String? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused the file")
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("could not open the file")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        "Downloads/$name"
    } else {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        File(dir, name).writeText(text)
        File(dir, name).path
    }.also { AppLog.log("Saved to $it") }
} catch (e: Exception) {
    AppLog.log("Save to Downloads failed: ${e.message}")
    null
}
