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
 * The app's page-turn: a page shrinks, tilts away and fades as it leaves the centre of a pager.
 * [offset] is that page's signed distance from the centre (0 on it, plus or minus 1 a full page
 * away), read only in the draw phase so a drag never recomposes. [strength] scales the whole
 * effect: the onboarding deck uses 1, ordinary paging a gentler fraction.
 */
internal fun Modifier.pageTurn(offset: () -> Float, strength: Float = 1f): Modifier = graphicsLayer {
    val o = offset()
    val away = kotlin.math.abs(o).coerceIn(0f, 1f)
    val scale = 1f - 0.07f * strength * away
    scaleX = scale
    scaleY = scale
    alpha = 1f - 0.5f * strength * away
    rotationY = o * 7f * strength
    // A wide camera flattens the perspective, so a tilted page's corners do not swing past the top
    // and bottom of the screen (where the pager clips them).
    cameraDistance = 24.dp.toPx()
}
