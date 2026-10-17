package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.bloo.uicommon.AnimatedValue
import com.bloo.uicommon.LocalReduceMotion

/**
 * The big-headline alias of [com.bloo.uicommon.AnimatedValue], the app's one rolling component:
 * compact values roll digit runs vertically, directionally (up when the number grew, down when it
 * shrank) between static text that crossfades; long or wrapping values fall back to a whole-string
 * slide.
 */
@Composable
internal fun RollingNumber(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    /** How many lines the value may take. Pebble summaries pass >1 so a big font wraps, not cuts. */
    maxLines: Int = 1,
) {
    // The rolling digits are drawn by uicommon (no Text, so no LocalContentColor lookup there): an
    // unspecified colour has to be resolved HERE or it paints black on dark surfaces.
    val resolved = when {
        color != Color.Unspecified -> color
        style.color != Color.Unspecified -> style.color
        else -> androidx.compose.material3.LocalContentColor.current
    }
    AnimatedValue(
        value = text,
        style = style.merge(TextStyle(fontWeight = fontWeight, color = resolved)),
        maxLines = maxLines,
        reduceMotion = LocalReduceMotion.current,
        modifier = modifier,
    )
}
