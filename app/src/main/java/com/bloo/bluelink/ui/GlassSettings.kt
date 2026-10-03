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
    // Five fixed stops, never a free value: how TRANSPARENT the glass is.
    var index by remember(appearance.glassClarity) { mutableFloatStateOf(nearestGlassStop(appearance.glassClarity).toFloat()) }
    val stop = GlassStops[index.roundToInt().coerceIn(0, GlassStops.lastIndex)]
    StepRow("Glass", "${stop.name} · ${(stop.transparency * 100).roundToInt()}% transparent")
    AnimatedSlider(
        value = index,
        onValueChange = { index = it },
        valueRange = 0f..GlassStops.lastIndex.toFloat(),
        steps = GlassStops.size - 2,
        onValueSettled = {
            val i = it.roundToInt().coerceIn(0, GlassStops.lastIndex)
            index = i.toFloat()
            vm.setGlassClaritySoon(GlassStops[i].transparency)
        },
    )
    BodySmallText("How see-through the backing of floating glass is, from solid to nothing but the bending edge.")
}

/** One fixed stop on the glass slider: its name and how transparent the backing is (0 = solid, 1 = none). */
internal class GlassStop(val name: String, val transparency: Float)

internal val GlassStops = listOf(
    GlassStop("Solid", 0f),
    GlassStop("Frosted", 0.25f),
    GlassStop("Misted", 0.70f),
    GlassStop("Clear", 0.90f),
    GlassStop("Crystal", 1f),
)

/** The stop closest to a stored [transparency] (older versions stored free values). */
internal fun nearestGlassStop(transparency: Float): Int =
    GlassStops.indices.minByOrNull { kotlin.math.abs(GlassStops[it].transparency - transparency) } ?: 2
