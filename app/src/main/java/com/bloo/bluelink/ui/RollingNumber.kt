package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.bloo.uicommon.AnimatedValue

/**
 * The big-headline alias of [com.bloo.uicommon.AnimatedValue], the app's one
 * rolling component: compact values roll digit runs vertically, directionally
 * (up when the number grew, down when it shrank) between static text that
 * crossfades; long or wrapping values fall back to a whole-string slide.
 *
 * Kept as its own name because the headline call sites ("84%", the charge
 * readouts, the build number) read as a different role from a status value and
 * want to grep separately -- but the behaviour is the SHARED one, so headline
 * and status values roll in the same language by construction.
 */
@Composable
internal fun RollingNumber(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    AnimatedValue(
        value = text,
        style = style.merge(TextStyle(fontWeight = fontWeight)).takeIf { color == Color.Unspecified } ?: style.merge(
            TextStyle(fontWeight = fontWeight, color = color),
        ),
        maxLines = 1,
        reduceMotion = LocalReduceMotion.current,
        modifier = modifier,
    )
}
