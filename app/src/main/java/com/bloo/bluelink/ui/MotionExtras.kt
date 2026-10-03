package com.bloo.bluelink.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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

/**
 * The shared motion pieces for things that were static: a piece of text that changes, a row you press,
 * a card that first appears. One place, so every surface that uses them moves the same way.
 */

/** Text that cross-fades when [text] changes, instead of swapping in a frame. */
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
    Crossfade(
        targetState = text,
        modifier = modifier,
        animationSpec = tween(MotionShort),
        label = "animatedText",
    ) { t ->
        Text(t, style = style, color = color, fontWeight = fontWeight, maxLines = maxLines, softWrap = softWrap, overflow = overflow)
    }
}

/**
 * The app's squash and stretch for anything tappable that isn't a button: pressed it flattens and widens
 * a touch; an under-damped spring then carries it past rest (taller, narrower) before it settles. Mild,
 * volume-ish, and the same on every surface that uses it, so the whole app moves like one material.
 */
@Composable
internal fun Modifier.pressScale(source: MutableInteractionSource, amount: Float = 0.04f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val p by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "squashStretch",
    )
    return this.graphicsLayer {
        scaleX = 1f + amount * p
        scaleY = 1f - amount * 1.3f * p
    }
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
 * weaker the further down it goes (the status bar's glass). Place it BEFORE the effect it should fade.
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
