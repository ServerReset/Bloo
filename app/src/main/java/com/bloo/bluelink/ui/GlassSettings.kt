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
    // Five fixed stops, never a free value.
    var draft by remember(appearance.glassClarity) { mutableFloatStateOf((appearance.glassClarity * 4).roundToInt() / 4f) }
    StepRow(
        "Glass clarity",
        GlassClarityLabels[(draft * (GlassClarityLabels.size - 1)).roundToInt().coerceIn(0, GlassClarityLabels.size - 1)],
    )
    AnimatedSlider(
        value = draft,
        onValueChange = { draft = it },
        valueRange = 0f..1f,
        steps = 3,
        onValueSettled = {
            val stop = (it * 4).roundToInt() / 4f
            draft = stop
            vm.setGlassClaritySoon(stop)
        },
    )
    BodySmallText("Floating elements: from a frosted backing to a mostly clear pane that shows the refraction.")
}

private val GlassClarityLabels = listOf("Frosted", "Soft", "Balanced", "Clear", "Crystal")
