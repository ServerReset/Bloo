package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bloo.bluelink.data.SettingsStore
import kotlin.math.roundToInt

/**
 * How clear the liquid glass is, from heavily frosted (an opaque, soft backing) to nearly clear (the
 * refraction shows almost undiluted). One slider shared by the Display card and Settings search.
 */
@Composable
internal fun GlassClaritySlider(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    // Six fixed stops, never a free value. The last, Ultra, turns every glass effect up past maximum on
    // EVERY surface; the rest set how transparent the floating glass is.
    var index by remember(appearance.glassClarity, appearance.ultraGlass) {
        mutableFloatStateOf((if (appearance.ultraGlass) GlassStops.lastIndex else nearestGlassStop(appearance.glassClarity)).toFloat())
    }
    val stop = GlassStops[index.roundToInt().coerceIn(0, GlassStops.lastIndex)]
    StepRow("Glass", if (stop.ultra) "Ultra · everything turned up past maximum" else "${stop.name} · ${(stop.transparency * 100).roundToInt()}% transparent")
    AnimatedSlider(
        value = index,
        onValueChange = { index = it },
        valueRange = 0f..GlassStops.lastIndex.toFloat(),
        steps = GlassStops.size - 2,
        onValueSettled = {
            val i = it.roundToInt().coerceIn(0, GlassStops.lastIndex)
            index = i.toFloat()
            val picked = GlassStops[i]
            vm.setGlassClaritySoon(picked.transparency)
            vm.setUltraGlass(picked.ultra)
        },
    )
    BodySmallText("How see-through floating glass is, from solid to nothing but the bending edge. Ultra puts glass on every card and cranks the edge refraction, colour fringe and shine as far as they go. Heavy on older devices.")
}

/** One fixed stop on the glass slider: its name and how transparent the backing is (0 = solid, 1 = none). */
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
