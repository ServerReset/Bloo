package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bloo.bluelink.data.SettingsStore
import com.bloo.uicommon.BlooSlider
import kotlin.math.roundToInt

/**
 * How clear the liquid glass is, from heavily frosted (an opaque, soft backing) to nearly clear
 * (the refraction shows almost undiluted). One slider shared by the Display card and Settings
 * search.
 */
@Composable
internal fun GlassClaritySlider(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    // The last, Ultra, turns every glass effect up past maximum on EVERY surface; the rest set how
    // transparent the floating glass is.
    BlooSlider(
        label = "Glass",
        value = (if (appearance.ultraGlass) GlassStops.lastIndex else nearestGlassStop(appearance.glassClarity)).toFloat(),
        valueRange = 0f..GlassStops.lastIndex.toFloat(),
        steps = GlassStops.size - 2,
        readout = { GlassStops[it.roundToInt().coerceIn(0, GlassStops.lastIndex)].name },
        snap = { it.roundToInt().coerceIn(0, GlassStops.lastIndex).toFloat() },
        onValueSettled = {
            val picked = GlassStops[it.roundToInt()]
            vm.setGlassClaritySoon(picked.transparency)
            vm.setUltraGlass(picked.ultra)
        },
    )
    BodySmallText("Ultra puts glass on every card. Heavy on older phones.")
}

internal class GlassStop(val name: String, val transparency: Float, val ultra: Boolean = false)

internal val GlassStops = listOf(
    GlassStop("Frosted", 0.25f),
    GlassStop("Medium", 0.7f),
    GlassStop("Soft clear", 0.8f),
    GlassStop("Clear", 0.9f),
    GlassStop("Crystal", 1f),
    GlassStop("Ultra", 1f, ultra = true),
)

/** The stop closest to a stored [transparency] (older versions stored free values). */
internal fun nearestGlassStop(transparency: Float): Int =
    GlassStops.indices.filter { !GlassStops[it].ultra }.minByOrNull { kotlin.math.abs(GlassStops[it].transparency - transparency) } ?: 1
