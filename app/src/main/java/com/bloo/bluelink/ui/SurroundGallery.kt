package com.bloo.bluelink.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Saves one surround-view photo to Pictures/Bloo, where the gallery shows it. Android 10 and later write through MediaStore, so no storage
 * permission is needed. Older Android has no such path without one, so the photo goes to the app's own pictures folder instead.
 * Returns where it was saved, or null when it could not be saved.
 */
internal fun saveSurroundPhoto(context: Context, name: String, jpeg: ByteArray): String? = runCatching {
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Bloo")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
        context.contentResolver.openOutputStream(uri)?.use { it.write(jpeg) } ?: return@runCatching null
        "Pictures/Bloo/$name"
    } else {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Bloo").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(jpeg)
        file.path
    }
}.getOrNull()

/** A file name for a surround-view photo, from when the car took it (the time, with only letters and digits). */
internal fun surroundPhotoName(utcTime: String?): String =
    "surround_" + (utcTime.orEmpty().filter { it.isLetterOrDigit() }.ifEmpty { System.currentTimeMillis().toString() }) + ".jpg"
