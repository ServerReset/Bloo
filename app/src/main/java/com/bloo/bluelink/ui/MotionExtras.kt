package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One place, so every surface that uses them moves the same way. */

/**
 * Text that fades in when [text] changes, instead of swapping in a frame. One plain Text and one
 * alpha (no cross-fade, which composes both strings at once): this sits inside every button label,
 * so it has to be close to free when nothing is changing.
 */
@Composable
internal fun AnimatedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    // Long / wrapping / digit-free text keeps this fade, which is the right shape for wholesale
    // changes.
    if (maxLines == 1 && text.length <= 24 && text.any { it.isDigit() } && !text.contains('\n')) {
        RollingNumber(
            text,
            style = style,
            fontWeight = fontWeight ?: FontWeight.Normal,
            color = color,
            modifier = modifier,
        )
        return
    }
    val fade = remember { Animatable(1f) }
    var first by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(text) {
        if (first) { first = false; return@LaunchedEffect }
        fade.snapTo(0.2f)
        fade.animateTo(1f, tween(MotionShort))
    }
    Text(
        text,
        modifier = modifier.graphicsLayer { alpha = fade.value },
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
    )
}

/** Names of the things that have already played their entrance this session. */
private val enteredThisSession = mutableSetOf<String>()

/**
 * Fades and lifts a surface in the first time it appears (once per [key] per session), so cards
 * arrive instead of being there. Read in the draw phase, so it costs no recomposition, and a card
 * scrolled away and back does not replay it.
 */
@Composable
internal fun Modifier.entrance(key: String): Modifier {
    val play = remember(key) { enteredThisSession.add(key) }
    val progress = remember(key) { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(key) {
        if (play) progress.animateTo(1f, tween(MotionLong))
    }
    return this
        .graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = (1f - p) * 14.dp.toPx()
        }
}

/**
 * Fades everything this modifier's content draws to nothing at the bottom edge, so the effect gets
 * weaker the further down it goes (the status bar's glass). Place it BEFORE the effect it should
 * fade.
 */
internal fun Modifier.fadeOutBottom(): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.55f), Color.Transparent)),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
