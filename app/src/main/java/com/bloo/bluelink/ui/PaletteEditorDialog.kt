package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import com.bloo.bluelink.data.SettingsStore
import com.bloo.uicommon.rememberConfirmArm
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.UUID
import androidx.compose.ui.graphics.toArgb

/** Dialog to create or edit a [CustomPaletteData]. */
@Composable
internal fun PaletteEditorDialog(
    editing: CustomPaletteData?,
    onSave: (CustomPaletteData) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val paletteId = remember(editing) { editing?.id ?: UUID.randomUUID().toString() }
    var name by remember(editing) { mutableStateOf(editing?.name ?: "Custom") }
    var primaryColor by remember(editing) {
        mutableStateOf(editing?.primaryArgb?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: ColorPalette.BLUE.swatch)
    }
    var useSecondary by remember(editing) { mutableStateOf(editing?.secondaryArgb != null) }
    var secondaryColor by remember(editing) {
        mutableStateOf(editing?.secondaryArgb?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: ColorPalette.VIOLET.swatch)
    }
    var useTertiary by remember(editing) { mutableStateOf(editing?.tertiaryArgb != null) }
    var tertiaryColor by remember(editing) {
        mutableStateOf(editing?.tertiaryArgb?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: ColorPalette.TEAL.swatch)
    }
    val confirmDeleteArm = rememberConfirmArm()
    // Standardized on the shared GlassAlertDialog shell. No leading icon (the dialog is title-led);
    // the delete affordance rides the shell's titleTrailing slot; the shell already scrolls its
    // body (max 360dp), so the inner verticalScroll is dropped to avoid a nested-scroll conflict.
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = if (editing == null) "New palette" else "Edit \"${editing.name}\"",
        titleTrailing = if (editing != null) {
            {
                MorphIconButton(onClick = {
                    if (confirmDeleteArm.armed) { onDelete(paletteId); onDismiss() } else { confirmDeleteArm.arm() }
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = if (confirmDeleteArm.armed) "Confirm delete palette" else "Delete palette",
                        tint = if (confirmDeleteArm.armed) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    )
                }
            }
        } else null,
        text = {
                BlooTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                // Primary colour picker
                LabelLargeText(
                    "Primary colour",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorPickerCanvas(primaryColor, { primaryColor = it })

                // Secondary colour (optional) ToggleRow, not a hand-rolled label+Switch: identical
                // layout and the same bodyMedium label, but it brings the morph pill track, the
                // toggleOn/toggleOff haptics and the single-focus-stop TalkBack semantics that
                // every other boolean setting in the app has.
                ToggleRow("Custom secondary", useSecondary) { useSecondary = it }
                AnimatedVisibility(useSecondary, enter = expandEnterSized(), exit = expandExitSized()) {
                    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                        LabelLargeText(
                            "Secondary colour",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ColorPickerCanvas(secondaryColor, { secondaryColor = it })
                    }
                }

                // Tertiary colour (optional) ToggleRow, not a hand-rolled label+Switch: identical
                // layout and the same bodyMedium label, but it brings the morph pill track, the
                // toggleOn/toggleOff haptics and the single-focus-stop TalkBack semantics that
                // every other boolean setting in the app has.
                ToggleRow("Custom tertiary", useTertiary) { useTertiary = it }
                AnimatedVisibility(useTertiary, enter = expandEnterSized(), exit = expandExitSized()) {
                    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                        LabelLargeText(
                            "Tertiary colour",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ColorPickerCanvas(tertiaryColor, { tertiaryColor = it })
                    }
                }
        },
        buttons = {
            MorphActionButton(
                label = "Save",
                icon = Icons.Filled.Check,
                onClick = {
                    onSave(
                        CustomPaletteData(
                            id = paletteId,
                            name = name.ifBlank { "Custom" },
                            primaryArgb = primaryColor.toArgb(),
                            secondaryArgb = if (useSecondary) secondaryColor.toArgb() else null,
                            tertiaryArgb = if (useTertiary) tertiaryColor.toArgb() else null,
                        )
                    )
                    onDismiss()
                },
    modifier = Modifier.fillMaxWidth(),
    active = true,
)
            SafeMorphTextButton(
                "Cancel",
                onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        },
    )
}

internal val VibrancySteps = floatArrayOf(0.5f, 1f, 1.6f)

internal val VibrancyLabels = listOf("Muted", "Normal", "Vivid")

internal fun vibrancyIndexFor(v: Float): Int =

    VibrancySteps.indices.minByOrNull { kotlin.math.abs(VibrancySteps[it] - v) } ?: 1

/**
 * Shared by the main Appearance card and the settings-search quick-jump preview so the 3-stop
 * mapping lives in exactly one place.
 */
@Composable
internal fun VibrancySlider(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    var indexDraft by remember(appearance.vibrancy) { mutableFloatStateOf(vibrancyIndexFor(appearance.vibrancy).toFloat()) }
    StepRow("Vibrancy", VibrancyLabels[indexDraft.roundToInt().coerceIn(0, 2)])
    AnimatedSlider(
        value = indexDraft,
        onValueChange = { indexDraft = it },
        valueRange = 0f..2f,
        steps = 1,
        onValueSettled = {
            val idx = it.roundToInt().coerceIn(0, 2)
            indexDraft = idx.toFloat()
            vm.setVibrancySoon(VibrancySteps[idx])
        },
    )
}

@Composable
internal fun UiScaleSlider(appearance: SettingsStore.Appearance, vm: AppViewModel, label: String = "Text & layout scale") {
    var uiScaleDraft by remember(appearance.uiScale) { mutableFloatStateOf(appearance.uiScale) }
    StepRow(label, "${(uiScaleDraft * 100).roundToInt()}%")
    AnimatedSlider(
        value = uiScaleDraft,
        onValueChange = { uiScaleDraft = it },
        valueRange = 0.8f..1.3f,
        steps = 4,
        onValueSettled = { uiScaleDraft = (it * 10).roundToInt() / 10f; vm.setUiScaleSoon(uiScaleDraft) },
    )
}
