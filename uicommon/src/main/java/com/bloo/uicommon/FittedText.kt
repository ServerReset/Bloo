package com.bloo.uicommon

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Shrink-to-fit text for tight, unpredictable spaces (the flip-phone cover screen).
 * The value is rendered as large as fits the available bounds and shrinks — never
 * wrapping mid-word — down to a floor, only ellipsizing if it still doesn't fit at
 * the floor. This is the single mechanism that kills the cover's "Locke/d",
 * "Runnin/g" and "26…" clipping, regardless of content length or system font scale.
 *
 * Built on Foundation's [BasicText] `autoSize` ([TextAutoSize.StepBased]) — the
 * stable autosize path (Material3 `Text` autosize is unreliable at this project's
 * M3 alpha). Two important consequences of using BasicText directly:
 *   1. It IGNORES LocalContentColor — the color MUST be baked into [style]
 *      (matching how WiggleText/AnimatedValue already work in this module).
 *   2. `softWrap` is derived from [maxLines]: a single line (the default) can never
 *      wrap mid-word — autoSize shrinks it instead. Multi-line callers get wrap.
 *
 * @param style the MAXIMUM style; its fontSize is the ceiling autoSize grows to.
 * @param minFontSize floor the text may shrink to before ellipsizing; when
 *   unspecified, derived as 60% of the ceiling (clamped to >= 11sp).
 */
@Composable
fun FittedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minFontSize: TextUnit = TextUnit.Unspecified,
    stepGranularity: TextUnit = 0.5.sp,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    // Ceiling = the style's own size (fall back to a sane 24sp if the style left it
    // unspecified, which would otherwise make StepBased's max meaningless).
    val ceiling: TextUnit = if (style.fontSize.isSpecified) style.fontSize else 24.sp
    val floor: TextUnit = when {
        minFontSize.isSpecified -> minFontSize
        // 60% of the ceiling, but never below 11sp so a hero can shrink hard on a
        // tiny cover yet stay legible.
        else -> (ceiling.value * 0.6f).coerceAtLeast(11f).sp
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        overflow = overflow,
        // One line => softWrap off => cannot break a word across lines; autoSize
        // shrinks to fit instead. Multi-line callers keep normal wrapping.
        softWrap = maxLines > 1,
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(
            minFontSize = floor,
            maxFontSize = ceiling,
            stepSize = stepGranularity,
        ),
    )
}
