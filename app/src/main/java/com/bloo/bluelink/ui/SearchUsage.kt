package com.bloo.bluelink.ui

import android.content.Context
import androidx.core.content.edit
import com.bloo.bluelink.data.privatePrefs

/**
 * How often each search result has been picked, kept on this device. Among results that match equally, the ones picked most often rise
 * to the top. Keys name the kind of result, such as "cmd:lock".
 */
internal class SearchUsage(context: Context) {
    private val prefs by lazy { context.privatePrefs("search_usage") }

    fun count(key: String): Int = prefs.getInt(key, 0)

    fun record(key: String) {
        prefs.edit { putInt(key, count(key) + 1) }
    }
}
