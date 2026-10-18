package com.bloo.bluelink.wear

import android.content.Context
import androidx.core.content.edit

/**
 * Which control sits in each quarter of the watch face: top-left, top-right, bottom-left, bottom-right. The
 * choice is the wearer's, kept on the watch (plain prefs: nothing here is secret); the controls themselves are
 * [WearFaceControls]. A control the car cannot do leaves its quarter quiet.
 */
object WearFacePrefs {
    val positions = listOf("Top left", "Top right", "Bottom left", "Bottom right")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("bloo_watch_face", Context.MODE_PRIVATE)

    fun load(context: Context): List<String> {
        val saved = prefs(context).getString("slots", null)?.split(",")
        val valid = saved != null && saved.size == 4 && saved.all { it == WearFaceControls.NONE || WearFaceControls.byKey(it) != null }
        return if (valid) saved!! else WearFaceControls.defaults
    }

    fun save(context: Context, slots: List<String>) {
        prefs(context).edit { putString("slots", slots.joinToString(",")) }
    }
}
