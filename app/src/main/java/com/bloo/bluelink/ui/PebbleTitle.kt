package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.bloo.uicommon.lowPowerCalm
import com.bloo.uicommon.lowPowerBounce
import com.bloo.uicommon.PrimaryTextStyle
import kotlin.math.roundToInt
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.MotionShort
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.lowPowerAwareSpring

/**
 * The pebble header's title line: the title itself (which can swell on expand for the hero) with an
 * optional trailing stat, either beside the name or pushed to the far end. Split out of
 * [PebbleShell], which only decides where this row goes; it needs nothing from the header's size or
 * press state.
 */
@Composable
internal fun PebbleTitleRow(
    title: String,
    expanded: Boolean,
    growTitleOnExpand: Boolean,
    titleColor: Color,
    titleTrailing: (@Composable () -> Unit)?,
    titleTrailingAtEnd: Boolean,
    /** A floating card's title is a size up, so it reads as the card's name. */
    largeTitle: Boolean = false,
) {
    // The hero title grows on expand via a slow, lightly bouncy spring (damping 0.62,
    // StiffnessVeryLow) so intermediate sizes read as motion.
    val expandingTitle = expanded && growTitleOnExpand
    val headerTState = animateFloatAsState(
        targetValue = if (expandingTitle) 1f else 0f,
        animationSpec = if (expandingTitle) {
            lowPowerBounce(stiffness = Spring.StiffnessLow)
        } else {
            lowPowerCalm(Spring.StiffnessLow)
        },
        label = "pebbleHeaderGrow",
    )
    // Drawn at the larger size and scaled down (draw phase only): animating the font size would
    // relayout the text every frame (single-slot ParagraphLayoutCache). Scaling down keeps glyphs
    // crisp.
    // One style for every state: the size change to a floating card is a scale, animated with the rest of the header, never a swap
    // of styles (which made the name jump).
    val titleStyle = MaterialTheme.typography.headlineSmall
    val largeT by animateFloatAsState(
        targetValue = if (largeTitle) 1f else 0f,
        animationSpec = lowPowerCalm(Spring.StiffnessLow),
        label = "pebbleTitleLarge",
    )
    // Ratio of the real type steps, so the collapsed size equals titleMedium exactly.
    val collapsedTitleScale = with(LocalDensity.current) {
        MaterialTheme.typography.titleMedium.fontSize.toPx() /
            titleStyle.fontSize.toPx()
    }
    // How much bigger the floating size is than the standard one: headlineLarge over headlineSmall, animated in with largeT.
    val largeStep = with(LocalDensity.current) {
        MaterialTheme.typography.headlineLarge.fontSize.toPx() / titleStyle.fontSize.toPx()
    }
    // Plain arithmetic: the Float `lerp` overload is not imported here. A lambda so each reader
    // pulls the current value in its own phase.
    val titleScale: () -> Float = if (!growTitleOnExpand) {
        { collapsedTitleScale * (1f + (largeStep - 1f) * largeT) }
    } else {
        {
            (collapsedTitleScale + (1f - collapsedTitleScale) * headerTState.value) * (1f + (largeStep - 1f) * largeT)
        }
    }
    // Only a genuinely LARGE display/font scale may wrap the title (and the summary below), so the
    // header grows instead of cutting. At normal zoom a long title ellipsizes cleanly on one line --
    // wrapping there made "AI summary" a broken "AI / sum…" and turned ordinary pebbles into tall,
    // odd blocks.
    val allowWrap = LocalDensity.current.fontScale >= 1.3f
    Row(
        // Only stretched when the trailing slot is being pushed to the end -- a row that merely
        // holds a name and a stat must stay shrink-wrapped, or the stat drifts away from the name.
        modifier = if (titleTrailingAtEnd) Modifier.fillMaxWidth() else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        // SpaceBetween (not a filled title) pushes the trailing slot to the far end without
        // measuring the scaled title wider than its own content.
        horizontalArrangement = if (titleTrailingAtEnd) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.Start
        },
    ) {
    // Title modifier chain shared by both branches below.
    val titleBaseModifier = Modifier
        // fill = false: weight caps the max width so long titles ellipsize, without forcing a wide
        // box.
        .weight(1f, fill = false)
        // Minimum gap before titleTrailing, on the title so SpaceBetween still sees two children.
        .then(if (titleTrailingAtEnd) Modifier.padding(end = GapGroup) else Modifier)
    if (growTitleOnExpand) {
        // One title, drawn once at headline size and scaled down to its rest size. It used to swap between two copies (a native one at
        // rest, a scaled one while open) in a Crossfade, and for a beat the copies drew in different states, which read as a flicker when
        // the name collapsed.
        RevealText(
            title,
            modifier = titleBaseModifier
                .layout { measurable, constraints ->
                    // Measured against constraints widened by 1/titleScale, so the text ellipsizes at the width it is drawn at.
                    val scale = titleScale()
                    val room = if (constraints.hasBoundedWidth && scale > 0f) {
                        constraints.copy(maxWidth = (constraints.maxWidth / scale).roundToInt().coerceAtLeast(constraints.maxWidth))
                    } else {
                        constraints
                    }
                    val placeable = measurable.measure(room)
                    val w = (placeable.width * scale).roundToInt()
                    val h = (placeable.height * scale).roundToInt()
                    val yOffset = (h - placeable.height) / 2
                    layout(w, h) { placeable.place(0, yOffset) }
                }
                .graphicsLayer {
                    val s = titleScale()
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
            style = titleStyle,
            color = titleColor,
            fontWeight = FontWeight.Bold,
            maxLines = if (allowWrap) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        // The common case: no grow/shrink, no rest-scale swap ever, so no Crossfade wrapper either
        // -- always native titleMedium.
        RevealText(
            title,
            style = PrimaryTextStyle(),
            modifier = titleBaseModifier,
            color = titleColor,
            fontWeight = FontWeight.Bold,
            maxLines = if (allowWrap) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // Trailing slot on the title row. No Spacer or styling here; the slot owns both. A plain Box:
    // child baselines are not forwarded, so alignByBaseline falls back to the box bottom.
    var lastTitleTrailing by remember { mutableStateOf(titleTrailing) }
    if (titleTrailing != null) lastTitleTrailing = titleTrailing
    AnimatedVisibility(
        visible = titleTrailing != null,
        enter = fadeIn(tween(MotionShort)) + scaleIn(tween(MotionShort), initialScale = 0.85f),
        exit = fadeOut(tween(MotionFast)) + scaleOut(tween(MotionFast), targetScale = 0.85f),
    ) {
        lastTitleTrailing?.let { Box { it() } }
    }
    }
}
