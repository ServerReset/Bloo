@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.UUID
import androidx.compose.ui.graphics.toArgb

/**
 * A round colour swatch for the palette picker. Shows the palette's seed colour
 * and a ring + check when selected.
 */
@Composable
internal fun PaletteSwatch(
    palette: ColorPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val ring by animateDpAsState(
        if (selected) 3.dp else 0.dp,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "swatchRing",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline)
                .padding(ring)
                .clip(CircleShape)
                .background(palette.swatch)
                // The colour name was only ever rendered as a sibling Text
                // below, outside this clickable's own semantics -- TalkBack
                // announced an unlabelled "double tap to activate" with no
                // colour name and no sense of which swatch is selected (the
                // ring/check are purely visual). RadioButton matches the
                // "pick exactly one" behaviour of this swatch row.
                .semantics {
                    contentDescription = palette.label
                    role = Role.RadioButton
                    this.selected = selected
                }
                .clickable { haptics?.click(); onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            palette.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Like [PaletteSwatch] but for user-created [CustomPaletteData] entries. */
@Composable
internal fun CustomPaletteSwatch(
    palette: CustomPaletteData,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val ring by animateDpAsState(
        if (selected) 3.dp else 0.dp,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "customSwatchRing",
    )
    val scale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "customSwatchScale",
    )
    val swatchColor = Color(palette.primaryArgb.toLong() and 0xFFFFFFFFL)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Outer container is sized to accommodate the 1.12x scale without clipping.
        Box(
            modifier = Modifier
                .size(58.dp)
                .semantics {
                    contentDescription = palette.name
                    role = Role.RadioButton
                    this.selected = selected
                }
                .clickable { haptics?.click(); onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    // Read at DRAW time, not in composition -- see Widgets.kt's
                    // FloatingIcon for the same change and why it matters.
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(ring)
                    .clip(CircleShape)
                    .background(swatchColor),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        // Standard gap between connected button elements (matches SplitExpandButton's
        // own 3dp gap for visual consistency across all grouped controls).
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                palette.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A bare 10dp clickable Icon (no IconButton) was well under the
            // minimum touch target guideline and had no button semantics --
            // TalkBack announced it with no "double tap to activate" cue and
            // it was genuinely hard to hit with a finger. IconButton gives
            // both a real touch target and the Button role for free.
            // Manual haptics?.click() dropped: MorphIconButton fires it. This was
            // the one bare IconButton in the file that remembered to.
            //
            // 32dp, not the full 48dp guideline: this sits in a caption row
            // (Text + this button) inside a 58dp-wide swatch column, itself one
            // of several in a tight grid -- 48dp here would overflow that
            // column or push its touch target into the neighbouring swatch's.
            // 32dp is a real improvement over the old 28dp that still fits.
            MorphIconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Edit palette",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Canvas-based colour picker: hue bar + saturation/value square.
 *
 * Internal state is plain HSV floats (`hue`/`sat`/`value`), seeded once from
 * the incoming [color] on first composition and never re-synced from it
 * afterward -- each drag on either Canvas computes a new HSV component
 * straight from the touch position (`awaitEachGesture` + a manual
 * down/while-pressed loop, since neither Canvas needs multi-touch or a
 * standard drag-gesture detector) and calls `update()`, which converts back
 * to RGB and reports it via [onColorChange]. `hexInput` is a separate text
 * mirror of the same colour: it's kept in sync from `picked` (but only
 * refreshed when the *canvas* changes the colour, not on every keystroke),
 * so a manually typed hex value only overwrites the canvas state once
 * `commitHex()` runs (on Done/focus-loss), not while the user is still
 * mid-edit.
 */
@Composable
internal fun ColorPickerCanvas(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Internal HSV state initialised from the incoming colour once.
    var hue by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        mutableFloatStateOf(hsv[0])
    }
    var sat by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        mutableFloatStateOf(hsv[1].coerceAtLeast(0.05f))
    }
    var value by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        mutableFloatStateOf(hsv[2].coerceAtLeast(0.3f))
    }

    fun update() {
        onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))))
    }

    val pureHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val picked = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    val hueGradient = remember(Unit) {
        (0..12).map { i -> Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f))) }
    }
    // Both brushes only depend on colours that change far less often than the
    // Canvas redraws while dragging (sat/value redraw on every pointer move):
    // satValueBrush only needs to change when the hue itself changes, and
    // hueBrush's gradient stops never change at all. Hoisting them out of the
    // draw scope avoids allocating a new List + Brush on every drag frame.
    val satValueBrush = remember(pureHue) { Brush.horizontalGradient(listOf(Color.White, pureHue)) }
    val hueBrush = remember(hueGradient) { Brush.horizontalGradient(hueGradient) }
    fun hexOf(c: Color) = String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and c.toArgb())
    // A plain text field is the accessible alternative to the two drag-only
    // Canvases below, which have no TalkBack path at all -- a screen-reader
    // user can name and save a palette but never actually choose or perceive
    // its colour otherwise. Only re-synced when the CANVAS changes the colour
    // (picked), never on every keystroke, so it doesn't fight an in-progress
    // edit -- typing itself only updates `picked` once, on a successful commit.
    var hexInput by remember { mutableStateOf(hexOf(picked)) }
    var hexError by remember { mutableStateOf(false) }
    LaunchedEffect(picked) { if (hexInput != hexOf(picked)) { hexInput = hexOf(picked); hexError = false } }
    fun commitHex() {
        val parsed = runCatching {
            android.graphics.Color.parseColor(if (hexInput.startsWith("#")) hexInput else "#$hexInput")
        }.getOrNull()
        if (parsed == null) {
            hexError = true
            return
        }
        hexError = false
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(parsed, hsv)
        hue = hsv[0]
        sat = hsv[1].coerceAtLeast(0.05f)
        value = hsv[2].coerceAtLeast(0.3f)
        update()
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Saturation × Value square
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics {
                    contentDescription = "Saturation and brightness picker. Use the hex colour field below for exact input."
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        sat = (down.position.x / size.width).coerceIn(0.02f, 1f)
                        value = 1f - (down.position.y / size.height).coerceIn(0f, 0.98f)
                        update()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            sat = (ch.position.x / size.width).coerceIn(0.02f, 1f)
                            value = 1f - (ch.position.y / size.height).coerceIn(0f, 0.98f)
                            update()
                        }
                    }
                }
        ) {
            drawRect(satValueBrush)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val cx = sat * size.width
            val cy = (1f - value) * size.height
            drawCircle(Color.White, 11.dp.toPx(), Offset(cx, cy))
            drawCircle(picked, 8.dp.toPx(), Offset(cx, cy))
        }

        // Hue bar
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(CircleShape)
                .semantics {
                    contentDescription = "Hue picker. Use the hex colour field below for exact input."
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        hue = ((down.position.x / size.width) * 360f).coerceIn(0f, 359.9f)
                        update()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            hue = ((ch.position.x / size.width) * 360f).coerceIn(0f, 359.9f)
                            update()
                        }
                    }
                }
        ) {
            drawRect(hueBrush)
            val tx = (hue / 360f) * size.width
            drawCircle(Color.White, 14.dp.toPx(), Offset(tx, size.height / 2f))
            drawCircle(pureHue, 11.dp.toPx(), Offset(tx, size.height / 2f))
        }

        // Preview swatch
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(picked)
        )

        OutlinedTextField(
            value = hexInput,
            onValueChange = { hexInput = it; hexError = false },
            label = { Text("Hex colour") },
            singleLine = true,
            isError = hexError,
            supportingText = if (hexError) { { Text("Not a valid colour") } } else null,
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commitHex() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) commitHex() },
        )
    }
}

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
        mutableStateOf(editing?.primaryArgb?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: Color(0xFF005AC1))
    }
    var useSecondary by remember(editing) { mutableStateOf(editing?.secondaryArgb != null) }
    var secondaryColor by remember(editing) {
        mutableStateOf(editing?.secondaryArgb?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: Color(0xFF7B4DFF))
    }
    var useTertiary by remember(editing) { mutableStateOf(editing?.tertiaryArgb != null) }
    var tertiaryColor by remember(editing) {
        mutableStateOf(editing?.tertiaryArgb?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: Color(0xFF00696E))
    }
    // Was a single un-confirmed tap that permanently deleted a saved custom
    // palette -- same "tap again to confirm" + 4s auto-reset pattern as the
    // climate preset delete nub, so this destructive action isn't one
    // mis-tap away from losing work either.
    var confirmDelete by remember(editing) { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(4000)
            confirmDelete = false
        }
    }
    // Standardized on the shared GlassAlertDialog shell. No leading icon (the
    // dialog is title-led); the delete affordance rides the shell's titleTrailing
    // slot; the shell already scrolls its body (max 360dp), so the inner
    // verticalScroll is dropped to avoid a nested-scroll conflict.
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = if (editing == null) "New palette" else "Edit \"${editing.name}\"",
        titleTrailing = if (editing != null) {
            {
                MorphIconButton(onClick = {
                    if (confirmDelete) { onDelete(paletteId); onDismiss() } else { confirmDelete = true }
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = if (confirmDelete) "Confirm delete palette" else "Delete palette",
                        tint = if (confirmDelete) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    )
                }
            }
        } else null,
        text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = FieldShape,
                )

                // Primary colour picker
                Text(
                    "Primary colour",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorPickerCanvas(primaryColor, { primaryColor = it })

                // Secondary colour (optional)
                // ToggleRow, not a hand-rolled label+Switch: identical layout and the
                // same bodyMedium label, but it brings the morph pill track, the
                // toggleOn/toggleOff haptics and the single-focus-stop TalkBack
                // semantics that every other boolean setting in the app has.
                ToggleRow("Custom secondary", useSecondary) { useSecondary = it }
                AnimatedVisibility(useSecondary, enter = collapseEnter(), exit = collapseExit()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Secondary colour",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ColorPickerCanvas(secondaryColor, { secondaryColor = it })
                    }
                }

                // Tertiary colour (optional)
                // ToggleRow, not a hand-rolled label+Switch: identical layout and the
                // same bodyMedium label, but it brings the morph pill track, the
                // toggleOn/toggleOff haptics and the single-focus-stop TalkBack
                // semantics that every other boolean setting in the app has.
                ToggleRow("Custom tertiary", useTertiary) { useTertiary = it }
                AnimatedVisibility(useTertiary, enter = collapseEnter(), exit = collapseExit()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Tertiary colour",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ColorPickerCanvas(tertiaryColor, { tertiaryColor = it })
                    }
                }
        },
        buttons = {
            val saveSource = remember { MutableInteractionSource() }
            SafeExpansiveButton(
                interactionSource = saveSource,
                enabled = true,
            ) {
                MorphButton(
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
                    active = true,
                    interactionSource = saveSource,
                    modifier = Modifier.fillMaxWidth(),
                ) { MorphButtonLabel(Icons.Filled.Check, "Save", pending = false) }
            }
            val cancelSource = remember { MutableInteractionSource() }
            SafeExpansiveButton(
                interactionSource = cancelSource,
                enabled = true,
            ) {
                MorphTextButton(
                    "Cancel",
                    onDismiss,
                    interactionSource = cancelSource,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
    )
}

// Five fixed stops instead of a free continuous 0-2 range with 20 snap
// points -- most of those were indistinguishable by eye, and "what number is
// this" isn't a useful question for a saturation slider. Each label names
// what you'd actually see, ending on "Best Buy TV" (a factor of 2.5 -- since
// saturate() coerces the HSV saturation channel to a hard 1.0 ceiling, this
// already pushes virtually every color to maximum saturation, the visual
// definition of "showroom TV wall" oversaturation).
internal val VibrancySteps = floatArrayOf(0f, 0.5f, 1f, 1.6f, 2.5f)
internal val VibrancyLabels = listOf("Monochrome", "A bit of color", "Normal", "Extra", "Best Buy TV")
internal fun vibrancyIndexFor(v: Float): Int =
    VibrancySteps.indices.minByOrNull { kotlin.math.abs(VibrancySteps[it] - v) } ?: 2

/** Shared by the main Appearance card and the settings-search quick-jump
 *  preview so the 5-stop mapping lives in exactly one place. */
@Composable
internal fun VibrancySlider(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    var indexDraft by remember(appearance.vibrancy) { mutableFloatStateOf(vibrancyIndexFor(appearance.vibrancy).toFloat()) }
    StepRow("Vibrancy", VibrancyLabels[indexDraft.roundToInt().coerceIn(0, 4)])
    AnimatedSlider(
        value = indexDraft,
        onValueChange = { indexDraft = it },
        valueRange = 0f..4f,
        steps = 3,
        onValueSettled = {
            val idx = it.roundToInt().coerceIn(0, 4)
            indexDraft = idx.toFloat()
            vm.setVibrancySoon(VibrancySteps[idx])
        },
    )
}
