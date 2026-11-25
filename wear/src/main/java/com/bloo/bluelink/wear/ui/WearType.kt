package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.FontChoice
import com.bloo.bluelink.data.WatchType
import com.bloo.bluelink.data.PrefsMirror
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.compositionLocalOf

/** Whether the big hero text (the charge in the middle of the face, the car's name on its ring) is set in the classical serif: the phone's Classic headings. */
internal val LocalHeroSerif = compositionLocalOf { false }

/**
 * The phone's typeface, as the watch sets its text. The phone sends its choice with every sync ([WatchType]); it is kept here and on disk,
 * so a cold start sets the text in the phone's font before the phone has said anything. Until a phone has sent one, the watch uses the
 * system font, as a new phone does.
 */
internal object WearType {
    private val mirror = PrefsMirror("bloo_wear_type", WatchType.serializer(), WatchType()) { p ->
        if (!p.contains("font")) null
        else WatchType(
            runCatching { FontChoice.valueOf(p.getString("font", "").orEmpty()) }.getOrDefault(FontChoice.SYSTEM),
            p.getBoolean("classicHeadings", false),
        )
    }
    val type: StateFlow<WatchType> = mirror.value

    /** Reads what was stored last time, once; later calls change nothing. */
    fun load(context: Context) = mirror.load(context)

    /** The phone's typeface arrived: set at once and kept. Null (an older phone) or the same choice changes nothing. */
    fun apply(context: Context, type: WatchType?) {
        mirror.apply(context, type)
    }
}
