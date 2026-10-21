package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.WatchAccent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's palette, as the watch wears it. The phone sends its accent colours with every sync ([WatchAccent]); they are kept
 * here (and on disk, so a cold start paints the right colours before the phone has said anything) and read by [WearTheme]. Until
 * a phone has sent any, the watch keeps the brand's own.
 */
internal object WearAccent {
    private const val PREFS = "bloo_wear_accent"
    private val _accent = MutableStateFlow<WatchAccent?>(null)
    val accent: StateFlow<WatchAccent?> = _accent

    /** Reads what was stored last time, once; later calls change nothing. */
    fun load(context: Context) {
        if (_accent.value != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("primary")) return
        _accent.value = WatchAccent(
            primary = prefs.getInt("primary", 0),
            onPrimary = prefs.getInt("onPrimary", 0),
            secondary = prefs.getInt("secondary", 0),
            tertiary = prefs.getInt("tertiary", 0),
        )
    }

    /** The phone's colours arrived: worn at once and kept. Null (an older phone) or the same colours change nothing. */
    fun apply(context: Context, accent: WatchAccent?) {
        if (accent == null || accent == _accent.value) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("primary", accent.primary)
            .putInt("onPrimary", accent.onPrimary)
            .putInt("secondary", accent.secondary)
            .putInt("tertiary", accent.tertiary)
            .apply()
        _accent.value = accent
        // The tile draws in these colours too: ask it to draw again.
        WearSurfaces.refresh(context)
    }
}
