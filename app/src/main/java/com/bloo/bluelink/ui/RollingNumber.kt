package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A headline number that rolls when it changes: it slides up when the value
 * grows and down when it shrinks (digits extracted from [text] decide the
 * direction), falling back to a cross-fade when there's no number to compare.
 */
@Composable
internal fun RollingNumber(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight,
    color: Color = Color.Unspecified,
) {
    // Split into the rolling digits and the STATIC suffix ("%"): only the
    // digits roll up/down, the unit glyph rides with them as one unmoved
    // companion -- rolling the whole string including the "%" read as the
    // entire readout lifting off, which is not what a digit roll is.
    val digits = text.takeWhile { it.isDigit() }
    val suffix = text.drop(digits.length)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.wrapContentWidth()) {
        AnimatedContent(
            targetState = digits,
            transitionSpec = {
                // Direction is derived HERE from the transition's own
                // initialState/targetState, not from a separately-tracked
                // "previous value" state: AnimatedContent already knows both
                // ends of the very transition it's composing, so the derived
                // direction can never lag the actual change (a two-step flip
                // landing in the same frame used to compared against a
                // previous value a LaunchedEffect wrote one frame later --
                // rolling UP on a number that had just gone DOWN).
                val dir = if ((targetState.toIntOrNull() ?: 0) >= (initialState.toIntOrNull() ?: 0)) 1 else -1
                (fadeIn(tween(MotionShort)) + slideInVertically { dir * it / 2 }) togetherWith
                    (fadeOut(tween(MotionFast)) + slideOutVertically { -dir * it / 2 })
            },
            label = "num",
        ) { t -> WiggleText(t, style = style, fontWeight = fontWeight, color = color) }
        if (suffix.isNotEmpty()) {
            WiggleText(suffix, style = style, fontWeight = fontWeight, color = color)
        }
    }
}
