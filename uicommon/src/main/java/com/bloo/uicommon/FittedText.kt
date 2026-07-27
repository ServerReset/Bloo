package com.bloo.uicommon

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Shrink-to-fit text for tight, unpredictable spaces (the flip-phone cover screen).
 * Renders as large as fits and shrinks — never wrapping mid-word — down to a floor,
 * only ellipsizing if it still doesn't fit at the floor. The single mechanism that
 * kills the cover's "Locke/d", "Runnin/g" and "26…" clipping, regardless of content
 * length or system font scale.
 *
 * Implemented with the rock-stable [BasicText] + `onTextLayout` overflow feedback
 * (NOT the newer `autoSize`/`TextAutoSize` API, to avoid any Compose-version /
 * opt-in dependency): start at the ceiling font size, and each time layout reports
 * the text didn't fit ([TextLayoutResult.hasVisualOverflow]) step the size down by
 * [stepGranularity] until it fits or hits the floor. Converges in a few frames and
 * settles; a size change only re-runs when the text or bounds change.
 *
 * Two consequences of using BasicText directly (as WiggleText/AnimatedValue do):
 *   1. It IGNORES LocalContentColor — colour MUST be baked into [style].
 *   2. `softWrap` is off for one line, so a single-line value can never break a
 *      word across lines; it shrinks instead. Multi-line callers get normal wrap.
 *
 * @param style the MAXIMUM style; its fontSize is the ceiling to shrink from.
 * @param minFontSize floor before ellipsizing; when unspecified, 60% of the
 *   ceiling (clamped to >= 11sp).
 */
@Composable
fun FittedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minFontSize: TextUnit = TextUnit.Unspecified,
    stepGranularity: TextUnit = 1.sp,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val ceiling: TextUnit = if (style.fontSize.isSpecified) style.fontSize else 24.sp
    val floorSp: Float = when {
        minFontSize.isSpecified -> minFontSize.value
        else -> (ceiling.value * 0.6f).coerceAtLeast(11f)
    }
    val step = if (stepGranularity.isSpecified) stepGranularity.value.coerceAtLeast(0.5f) else 1f

    // Current trial size in sp. Reset to the ceiling whenever the text or the max
    // size changes so a new (possibly shorter) value gets a fresh chance to grow.
    var sizeSp by remember(text, ceiling.value, floorSp) { mutableStateOf(ceiling.value) }

    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(fontSize = sizeSp.sp),
        overflow = overflow,
        // One line => no mid-word wrap; shrink instead. Multi-line keeps wrapping.
        softWrap = maxLines > 1,
        maxLines = maxLines,
        onTextLayout = { result ->
            // Shrink one step while it overflows and we're above the floor. This runs
            // post-layout and only mutates state when it must, so it converges (each
            // step re-lays out once) and then stops — no infinite invalidation loop.
            if (result.hasVisualOverflow && sizeSp > floorSp) {
                sizeSp = (sizeSp - step).coerceAtLeast(floorSp)
            }
        },
    )
}
