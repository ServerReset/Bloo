package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.LocalSize
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle

/**
 * Third slice out of CarWidget.kt: the styling LEAF -- how widget text and
 * inner surfaces look, with no knowledge of what's being drawn.
 *
 * Pulled out third on purpose. [WidgetText]'s fit chain and [WidgetValues]'
 * formatters were safe to move first because nothing else depended on them;
 * these are the opposite end of the same graph -- almost every composable in
 * the render layer calls one of these, but they call nothing that is still
 * moving. That makes them the one slice that can't break a later one, so
 * taking them now un-blocks every remaining extraction (a gauge or an info
 * row can't leave CarWidget.kt while the styles it draws with are still
 * private inside it).
 *
 * The four text styles are [Composable] rather than plain functions purely so
 * each can read [LocalSize] itself. Every call site is already inside
 * composition, so font size scales continuously with the widget's exact
 * measured size (see [Scale]) without threading a size parameter through every
 * caller -- which, in a file with eighteen tier layouts, is the difference
 * between one line per style and one extra argument on a few hundred calls.
 */

@Composable
internal fun titleStyle(theme: WidgetTheme) = TextStyle(
    color = theme.onSurface,
    fontSize = (Scale.titleSp(LocalSize.current).value * theme.textScale).sp,
    fontWeight = FontWeight.Bold,
)

@Composable
internal fun subtitleStyle(theme: WidgetTheme) = TextStyle(
    color = theme.onSurfaceVariant,
    fontSize = (Scale.subtitleSp(LocalSize.current).value * theme.textScale).sp,
)

@Composable
internal fun valueStyle(theme: WidgetTheme) = TextStyle(
    color = theme.onSurface,
    fontSize = (Scale.valueSp(LocalSize.current).value * theme.textScale).sp,
    fontWeight = FontWeight.Medium,
)

/**
 * The label style on an action button.
 *
 * A named style rather than one built at each of its two use sites, because
 * those two sites are the fit TEST and the RENDER: the button row builds this
 * style to ask whether every label fits its slot (via [wouldOverflow], which
 * measures character count against font size) and the button itself draws with
 * it. Two independent copies of the input to a measurement and the thing being
 * measured is the one duplication here that can't merely look wrong -- drift
 * either shows labels that don't fit, or hides labels that would have.
 */
@Composable
internal fun buttonLabelStyle(theme: WidgetTheme) = TextStyle(
    color = theme.onAccent,
    fontSize = (Scale.subtitleSp(LocalSize.current).value * theme.textScale).sp,
    fontWeight = FontWeight.Medium,
)

/**
 * The corner radius for surfaces INSIDE the widget -- action buttons, the map
 * thumbnail -- derived from the same [WidgetConfig.corner] choice as the outer
 * container so the whole widget speaks one shape language.
 *
 * Deliberately not the container's own radius: at 32dp a button only ~40dp
 * tall is already a pill, so the inner scale is its own gentler ramp rather
 * than the same numbers reused.
 */
internal fun innerCorner(config: WidgetConfig): Dp = when (config.effectiveCorner) {
    WidgetConfig.CORNER_SHARP -> 0.dp
    WidgetConfig.CORNER_ROUND -> 18.dp
    WidgetConfig.CORNER_PILL -> 999.dp
    else -> 14.dp
}
