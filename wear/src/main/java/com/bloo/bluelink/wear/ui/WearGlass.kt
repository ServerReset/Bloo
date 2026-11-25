package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.WatchGlass
import com.bloo.bluelink.data.PrefsMirror
import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's Glass setting, as the watch wears it. The phone sends it with every sync ([WatchGlass]); it is kept here and on disk, so a
 * cold start draws the glass the phone chose before the phone has said anything. Until a phone has sent one, the watch uses the phone's
 * default.
 */
internal object WearGlass {
    private val mirror = PrefsMirror("bloo_wear_glass", WatchGlass.serializer(), WatchGlass()) { p ->
        if (!p.contains("clarity")) null else WatchGlass(clarity = p.getFloat("clarity", 0.8f), ultra = p.getBoolean("ultra", false))
    }
    val glass: StateFlow<WatchGlass> = mirror.value

    /** Reads what was stored last time, once; later calls change nothing. */
    fun load(context: Context) = mirror.load(context)

    /** The phone's Glass setting arrived: worn at once and kept. Null (an older phone) or the same setting changes nothing. */
    fun apply(context: Context, glass: WatchGlass?) {
        mirror.apply(context, glass)
    }
}
