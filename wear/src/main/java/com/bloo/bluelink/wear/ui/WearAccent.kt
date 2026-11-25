package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.WatchAccent
import com.bloo.bluelink.data.PrefsMirror
import kotlinx.serialization.builtins.nullable
import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's palette, as the watch wears it. The phone sends its accent colours with every sync ([WatchAccent]); they are kept
 * here (and on disk, so a cold start paints the right colours before the phone has said anything) and read by [WearTheme]. Until
 * a phone has sent any, the watch keeps the brand's own.
 */
internal object WearAccent {
    private val mirror = PrefsMirror("bloo_wear_accent", WatchAccent.serializer().nullable, null) { p ->
        if (!p.contains("primary")) null
        else WatchAccent(
            primary = p.getInt("primary", 0), onPrimary = p.getInt("onPrimary", 0),
            secondary = p.getInt("secondary", 0), tertiary = p.getInt("tertiary", 0),
        )
    }
    val accent: StateFlow<WatchAccent?> = mirror.value

    /** The colour of what is on or in motion on the watch's own surfaces: the phone's accent once it has sent one, else the brand's. */
    val active: Int get() = accent.value?.primary ?: BlooColors.brandAccent

    /** Reads what was stored last time, once; later calls change nothing. */
    fun load(context: Context) = mirror.load(context)

    /** The phone's colours arrived: worn at once and kept. Null (an older phone) or the same colours change nothing. */
    fun apply(context: Context, accent: WatchAccent?) {
        // The tile draws in these colours too: ask it to draw again.
        if (mirror.apply(context, accent)) WearSurfaces.refresh(context)
    }
}
