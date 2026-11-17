package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.WatchGlass
import com.bloo.bluelink.data.privatePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's Glass setting, as the watch wears it. The phone sends it with every sync ([WatchGlass]); it is kept here and on disk, so a
 * cold start draws the glass the phone chose before the phone has said anything. Until a phone has sent one, the watch uses the phone's
 * default.
 */
internal object WearGlass {
    private const val PREFS = "bloo_wear_glass"
    private val _glass = MutableStateFlow(WatchGlass())
    val glass: StateFlow<WatchGlass> = _glass

    /** Reads what was stored last time, once; later calls change nothing. */
    fun load(context: Context) {
        val prefs = context.privatePrefs(PREFS)
        if (!prefs.contains("clarity")) return
        _glass.value = WatchGlass(clarity = prefs.getFloat("clarity", 0.8f), ultra = prefs.getBoolean("ultra", false))
    }

    /** The phone's Glass setting arrived: worn at once and kept. Null (an older phone) or the same setting changes nothing. */
    fun apply(context: Context, glass: WatchGlass?) {
        if (glass == null || glass == _glass.value) return
        context.privatePrefs(PREFS).edit()
            .putFloat("clarity", glass.clarity)
            .putBoolean("ultra", glass.ultra)
            .apply()
        _glass.value = glass
    }
}
