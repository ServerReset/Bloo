package com.bloo.bluelink.ui

import com.bloo.uicommon.ArmedDeleteButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.onClick
import com.bloo.bluelink.data.SettingsStore
import com.bloo.uicommon.BlooSlider
import com.bloo.uicommon.PrimaryTextStyle
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.MorphActionButton

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
    // Standardized on the shared GlassAlertDialog shell. No leading icon (the dialog is title-led);
    // the delete affordance rides the shell's titleTrailing slot; the shell already scrolls its
    // body (max 360dp), so the inner verticalScroll is dropped to avoid a nested-scroll conflict.
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = if (editing == null) "New palette" else "Edit \"${editing.name}\"",
        titleTrailing = if (editing != null) {
            {
                ArmedDeleteButton(name = editing.name, onConfirm = { onDelete(paletteId); onDismiss() })
            }
        } else null,
        text = {
                BlooTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { RevealText("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                ColorPickerSection("Primary colour", primaryColor) { primaryColor = it }
                // Optional accents use ToggleRow (not a hand-rolled label + Switch): the same layout, plus the morph track,
                // haptics and single-focus-stop TalkBack semantics every other boolean setting has.
                ToggleRow("Custom secondary", useSecondary) { useSecondary = it }
                ExpandVisibility(useSecondary) {
                    ColorPickerSection("Secondary colour", secondaryColor) { secondaryColor = it }
                }
                ToggleRow("Custom tertiary", useTertiary) { useTertiary = it }
                ExpandVisibility(useTertiary) {
                    ColorPickerSection("Tertiary colour", tertiaryColor) { tertiaryColor = it }
                }
        },
        buttons = {
            MorphActionButton(
                label = "Save",
                icon = AppIcons.Check,
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
                emphasis = ButtonEmphasis.Confirm,
            )
            MorphTextButton(
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
    BlooSlider(
        label = "Vibrancy",
        value = vibrancyIndexFor(appearance.vibrancy).toFloat(),
        valueRange = 0f..2f,
        steps = 1,
        readout = { VibrancyLabels[it.roundToInt().coerceIn(0, 2)] },
        snap = { it.roundToInt().coerceIn(0, 2).toFloat() },
        onValueSettled = { vm.setVibrancySoon(VibrancySteps[it.roundToInt()]) },
    )
}

@Composable
internal fun UiScaleSlider(appearance: SettingsStore.Appearance, vm: AppViewModel, label: String = "Text & layout scale") {
    BlooSlider(
        label = label,
        value = appearance.uiScale,
        valueRange = 0.8f..1.3f,
        steps = 4,
        readout = { "${(it * 100).roundToInt()}%" },
        snap = { (it * 10).roundToInt() / 10f },
        onValueSettled = { vm.setUiScaleSoon(it) },
    )
}

/** A labelled colour picker. */
@Composable
private fun ColorPickerSection(label: String, color: Color, onColor: (Color) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        RevealText(label, style = PrimaryTextStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ColorPickerCanvas(color, onColor)
    }
}
