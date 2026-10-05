package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt

/**
 * The round colour disc shared by every palette swatch: the colour, a ring + check and a small pop
 * when selected.
 */
@Composable
private fun SwatchDisc(color: Color, selected: Boolean, description: String, onClick: () -> Unit) {
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "swatchRing")
    val scale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "swatchScale",
    )
    // The 58dp slot leaves room for the 1.12x pop. Exposed as a RadioButton so TalkBack announces
    // name and state.
    Box(
        Modifier
            .size(58.dp)
            .semantics {
                contentDescription = description
                role = Role.RadioButton
                this.selected = selected
            }
            .hapticClickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(48.dp)
                // Read at draw time, not in composition.
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline)
                .padding(ring)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) SelectedCheck()
        }
    }
}

/** A built-in palette in the picker: its seed colour and name. */
@Composable
internal fun PaletteSwatch(palette: ColorPalette, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SwatchDisc(palette.swatch, selected, palette.label, onClick)
        Spacer(Modifier.height(GapHairline))
        Text(palette.label, style = MaterialTheme.typography.labelSmall, color = swatchLabelColor(selected))
    }
}

/** A user-made palette in the picker, with an edit button beside its name. */
@Composable
internal fun CustomPaletteSwatch(palette: CustomPaletteData, selected: Boolean, onClick: () -> Unit, onEdit: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SwatchDisc(Color(palette.primaryArgb.toLong() and 0xFFFFFFFFL), selected, palette.name, onClick)
        Spacer(Modifier.height(GapHairline))
        Row(horizontalArrangement = Arrangement.spacedBy(SplitSeam), verticalAlignment = Alignment.CenterVertically) {
            Text(palette.name, style = MaterialTheme.typography.labelSmall, color = swatchLabelColor(selected))
            // 32dp, not 48dp: it sits in a caption row inside a 58dp-wide swatch column in a tight
            // grid.
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
 * Canvas-based colour picker: hue bar + saturation/value square. HSV state is seeded once from
 * [color] and never re-synced; drags report via [onColorChange].
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
    // Hoisted out of the draw scope to avoid per-frame List + Brush allocation: satValueBrush
    // changes only with hue.
    val satValueBrush = remember(pureHue) { Brush.horizontalGradient(listOf(Color.White, pureHue)) }
    val hueBrush = remember(hueGradient) { Brush.horizontalGradient(hueGradient) }
    fun hexOf(c: Color) = String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and c.toArgb())
    // A text field is the accessible alternative to the drag-only canvases (no TalkBack path).
    // Re-synced only when the canvas changes the colour, so it never fights an in-progress edit.
    var hexInput by remember { mutableStateOf(hexOf(picked)) }
    var hexError by remember { mutableStateOf(false) }
    LaunchedEffect(picked) { if (hexInput != hexOf(picked)) { hexInput = hexOf(picked); hexError = false } }
    fun commitHex() {
        val parsed = runCatching {
            (if (hexInput.startsWith("#")) hexInput else "#$hexInput").toColorInt()
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

    Column(modifier, verticalArrangement = Arrangement.spacedBy(GapRow)) {
        // Saturation × Value square
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(SmallShape)
                .semantics {
                    contentDescription = "Saturation and brightness. Or type a hex below."
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        sat = snapStep((down.position.x / size.width).coerceIn(0.1f, 1f), 0.1f).coerceIn(0.1f, 1f)
                        value = snapStep(1f - (down.position.y / size.height).coerceIn(0f, 1f), 0.1f).coerceIn(0.3f, 1f)
                        update()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            sat = snapStep((ch.position.x / size.width).coerceIn(0.1f, 1f), 0.1f).coerceIn(0.1f, 1f)
                            value = snapStep(1f - (ch.position.y / size.height).coerceIn(0f, 1f), 0.1f).coerceIn(0.3f, 1f)
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
                    contentDescription = "Hue picker. Use the hex field below for exact values."
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        hue = snapStep((down.position.x / size.width) * 360f, 15f).coerceIn(0f, 345f)
                        update()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            hue = snapStep((ch.position.x / size.width) * 360f, 15f).coerceIn(0f, 345f)
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
                .clip(TinyShape)
                .background(picked)
        )

        BlooTextField(
            value = hexInput,
            onValueChange = { hexInput = it; hexError = false },
            label = { Text("Hex colour") },
            singleLine = true,
            // FieldShape like every other text field (matches the "Name" field above).
            isError = hexError,
            supportingText = if (hexError) { { Text("Not a valid colour") } } else null,
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commitHex() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) commitHex() },
        )
    }
}

private fun snapStep(v: Float, step: Float): Float = Math.round(v / step) * step

/** The check mark a chosen swatch wears, shared by both swatch rows. */
@Composable
private fun SelectedCheck() {
    Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(22.dp),
    )
}

/** A swatch caption's colour: full-strength when chosen, muted otherwise. */
@Composable
private fun swatchLabelColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
