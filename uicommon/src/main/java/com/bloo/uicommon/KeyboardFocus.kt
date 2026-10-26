package com.bloo.uicommon

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/** The keys that activate a focused control, as on any button: Enter, the numpad's Enter, and Space. */
private val ActivationKeys = setOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)

/**
 * Makes a custom-drawn control reachable and usable from a keyboard: it takes focus (Tab, arrow keys), shows a [ringColor] ring in
 * [shape] while it has it, and [onActivate] runs on Enter or Space. For controls whose touch handling is a gesture rather than a
 * `clickable` (which already does all of this), so they were silently skipped by the keyboard.
 */
@Composable
fun Modifier.keyboardActivate(shape: Shape, ringColor: Color, enabled: Boolean = true, onActivate: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    return this
        .then(if (focused) Modifier.border(2.dp, ringColor, shape) else Modifier)
        .focusable(enabled, source)
        .onKeyEvent { event ->
            if (enabled && event.type == KeyEventType.KeyDown && event.key in ActivationKeys) {
                onActivate()
                true
            } else {
                false
            }
        }
}
