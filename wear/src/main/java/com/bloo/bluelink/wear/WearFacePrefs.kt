package com.bloo.bluelink.wear

import android.content.Context
import androidx.core.content.edit

/**
 * Which control sits in each quarter of the watch face: top-left, top-right, bottom-left, bottom-right. The
 * choice is the wearer's, kept on the watch (plain prefs: nothing here is secret). A control the car cannot
 * do (Charge on a car with no battery, Flash where the brand has no lights) leaves its quarter quiet.
 */
object WearFacePrefs {
    const val LOCK = "lock"
    const val CLIMATE = "climate"
    const val CHARGE = "charge"
    const val FLASH = "flash"
    const val NONE = "none"

    /** Every choice, in the order tapping a quarter's row steps through them. */
    val choices = listOf(LOCK, CLIMATE, CHARGE, FLASH, NONE)
    val defaults = listOf(LOCK, CLIMATE, CHARGE, FLASH)
    val positions = listOf("Top left", "Top right", "Bottom left", "Bottom right")

    fun label(key: String) = when (key) {
        LOCK -> "Lock"
        CLIMATE -> "Climate"
        CHARGE -> "Charge"
        FLASH -> "Flash"
        else -> "Empty"
    }

    /** The next choice after [key], wrapping round. */
    fun next(key: String) = choices[(choices.indexOf(key) + 1) % choices.size]

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("bloo_watch_face", Context.MODE_PRIVATE)

    fun load(context: Context): List<String> {
        val saved = prefs(context).getString("slots", null)?.split(",")
        return if (saved != null && saved.size == 4 && saved.all { it in choices }) saved else defaults
    }

    fun save(context: Context, slots: List<String>) {
        prefs(context).edit { putString("slots", slots.joinToString(",")) }
    }
}
