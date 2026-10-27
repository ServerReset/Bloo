package com.bloo.bluelink.data

import android.content.Context
import android.net.Uri

/**
 * Reads a document the user picked (a settings file, a photo) through the content resolver. Null when the document cannot be opened or read; the
 * caller decides what that means. Every read of a picked file goes through here, so none of them forgets to close its stream.
 */
fun Context.readUriText(uri: Uri): String? =
    runCatching { contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()

/** [readUriText] for a document that is named by a string, as the sync file is. */
fun Context.readUriText(uri: String): String? = readUriText(Uri.parse(uri))
