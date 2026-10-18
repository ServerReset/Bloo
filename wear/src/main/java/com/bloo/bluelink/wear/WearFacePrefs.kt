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

    fun load(context: Context): List<String> = parse(prefs(context).getString("slots", null))

    /** The four stored choices, or the defaults when nothing is stored or what is stored is not four known keys. */
    internal fun parse(saved: String?): List<String> {
        val slots = saved?.split(",")
        val valid = slots != null && slots.size == 4 && slots.all { it == WearFaceControls.NONE || WearFaceControls.byKey(it) != null }
        return if (valid) slots!! else WearFaceControls.defaults
    }

    fun save(context: Context, slots: List<String>) {
        prefs(context).edit { putString("slots", slots.joinToString(",")) }
    }
}
