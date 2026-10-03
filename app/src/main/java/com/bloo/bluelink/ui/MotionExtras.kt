package com.bloo.bluelink.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    // A small pop when the words change, so a new value lands with a little life.
    val pop = remember { Animatable(1f) }
    var first by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(text) {
        if (first) { first = false; return@LaunchedEffect }
        pop.snapTo(1.07f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium))
    }
    Crossfade(
        targetState = text,
        modifier = modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        animationSpec = tween(MotionShort),
        label = "animatedText",
    ) { t ->
        Text(t, style = style, color = color, fontWeight = fontWeight, maxLines = maxLines, softWrap = softWrap, overflow = overflow)
    }
}

/** A springy dip while [source] is pressed: the standard press feedback for a tappable that isn't a button. */
@Composable
internal fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
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
    // The flare: one soft band of light sweeps across the card just after it lands.
    val sweep = remember(key) { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(key) {
        if (play) {
            launch { progress.animateTo(1f, tween(MotionLong)) }
            sweep.animateTo(1f, tween(900, delayMillis = 220))
        }
    }
    return this
        .graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = (1f - p) * 14.dp.toPx()
        }
        .drawWithContent {
            drawContent()
            val t = sweep.value
            if (t > 0f && t < 1f) {
                val x = size.width * (t * 1.7f - 0.35f)
                val band = size.width * 0.35f
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.20f * sin(PI.toFloat() * t)), Color.Transparent),
                        startX = x - band,
                        endX = x + band,
                    ),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                )
            }
        }
}

/**
 * A ring of sparkles that bursts outward from the surface whenever [on] turns true (not on first
 * composition). Draws outside the node's bounds, so keep it on an un-clipped wrapper.
 */
@Composable
internal fun Modifier.sparkleBurst(on: Boolean, color: Color = MaterialTheme.colorScheme.primary): Modifier {
    val progress = remember { Animatable(1f) }
    var first by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(on) {
        if (first) { first = false; return@LaunchedEffect }
        if (on) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(620, easing = LinearOutSlowInEasing))
        }
    }
    return this.drawWithContent {
        drawContent()
        val t = progress.value
        if (t < 1f) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val reach = size.minDimension * 0.5f + 22.dp.toPx() * t
            repeat(10) { i ->
                val angle = (2.0 * PI * i / 10.0).toFloat() + t * 0.6f
                drawCircle(
                    color.copy(alpha = (1f - t) * 0.9f),
                    radius = (if (i % 2 == 0) 2.6f else 1.8f).dp.toPx() * (1f - 0.5f * t),
                    center = Offset(center.x + cos(angle) * reach * 1.25f, center.y + sin(angle) * reach),
                )
            }
        }
    }
}
