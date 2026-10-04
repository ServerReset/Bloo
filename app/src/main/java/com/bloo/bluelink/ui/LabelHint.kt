package com.bloo.bluelink.ui

import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

/** How long a symbol-only button must be held before it expands to show its name. */
internal const val HOLD_TO_EXPAND_MS = 350L

/**
 * What a button's label says about itself, so a long press can reveal the name.
 *
 * The reveal is now IN-PLACE, not a bubble: while the finger is held on a button that has
 * shrunk to its symbol, [expanded] flips true and the button itself springs wider to show
 * the icon AND the label, then collapses back when the finger lifts -- all one spring, no
 * popup. This used to raise a separate glass bubble above the button on a timed auto-hide;
 * reported directly as wanting the control to grow where it is instead.
 */
internal class LabelHintState {
    var label: String = ""
    var icon: ImageVector? = null
    /** True while the button is showing only its symbol. Written from placement. */
    var collapsed: Boolean = false
    /** True while the button is held and should show its label in place. State, because the
     *  label layout reads it during measure and must re-run when it flips. */
    var expanded by mutableStateOf(false)

    fun describe(label: String, icon: ImageVector) { this.label = label; this.icon = icon }

    /** Hold began on a symbol-only button: eligible to expand once [HOLD_TO_EXPAND_MS] passes. */
    fun onHoldStart() { if (collapsed && label.isNotEmpty()) expanded = true }

    /** Hold ended (lift/cancel): collapse back. */
    fun onHoldEnd() { expanded = false }
}

/** The button whose label is being composed, if any. Null outside a [MorphButton]. */
internal val LocalLabelHint = androidx.compose.runtime.staticCompositionLocalOf<LabelHintState?> { null }
