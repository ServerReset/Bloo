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
    var draft by remember(appearance.glassClarity) { mutableFloatStateOf(appearance.glassClarity) }
    StepRow(
        "Glass clarity",
        when {
            draft < 0.2f -> "Frosted"
            draft < 0.5f -> "Soft"
            draft < 0.8f -> "Clear"
            else -> "Crystal"
        } + " · ${(draft * 100).roundToInt()}%",
    )
    AnimatedSlider(
        value = draft,
        onValueChange = { draft = it },
        valueRange = 0f..1f,
        steps = 0,
        onValueSettled = { vm.setGlassClaritySoon(it) },
    )
    BodySmallText("Floating elements: from a frosted backing to a mostly clear pane that shows the refraction.")
}
