package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.FontChoice
import com.bloo.bluelink.data.WatchType
import com.bloo.bluelink.data.privatePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's typeface, as the watch sets its text. The phone sends its choice with every sync ([WatchType]); it is kept here and on disk,
 * so a cold start sets the text in the phone's font before the phone has said anything. Until a phone has sent one, the watch uses the
 * system font, as a new phone does.
 */
internal object WearType {
    private const val PREFS = "bloo_wear_type"
    private val _type = MutableStateFlow(WatchType())
    val type: StateFlow<WatchType> = _type

    /** Reads what was stored last time, once; later calls change nothing. */
    fun load(context: Context) {
        val prefs = context.privatePrefs(PREFS)
        if (!prefs.contains("font")) return
        val font = runCatching { FontChoice.valueOf(prefs.getString("font", "").orEmpty()) }.getOrDefault(FontChoice.SYSTEM)
        _type.value = WatchType(font, prefs.getBoolean("classicHeadings", false))
    }

    /** The phone's typeface arrived: set at once and kept. Null (an older phone) or the same choice changes nothing. */
    fun apply(context: Context, type: WatchType?) {
        if (type == null || type == _type.value) return
        context.privatePrefs(PREFS).edit()
            .putString("font", type.font.name)
            .putBoolean("classicHeadings", type.classicHeadings)
            .apply()
        _type.value = type
    }
}
