package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.MotionLong

/** One place, so every surface that uses them moves the same way. */

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
 * The app's page-turn: a page shrinks, leans away, drops and dims as it leaves the centre of a
 * pager. [offset] is the page's distance from its OWN resting slot as a fraction of a page
 * (-0.5..0.5), read only in the draw phase so a drag never recomposes. Taking the FRACTIONAL distance
 * (not the whole-page distance) is what lets this run on grids too: every page is perfectly flat at
 * rest, including the neighbours that are fully visible in a multi-column layout. [strength] scales
 * the whole effect.
 */
internal fun Modifier.pageTurn(offset: () -> Float, strength: Float = 1f): Modifier = graphicsLayer {
    val o = offset().coerceIn(-0.5f, 0.5f)
    val away = (kotlin.math.abs(o) * 2f).coerceIn(0f, 1f)
    // Everything below is set only while the page is actually TURNED. At rest (away == 0) every
    // layer property is then left at its default, so Compose draws a flat page straight to the frame
    // with no offscreen buffer -- an unconditional compositingStrategy/cameraDistance made each
    // on-screen page allocate one even when it was perfectly still.
    if (away > 0f) {
        // Never an offscreen page buffer while it turns: scaled and tilted, drawn straight to the frame.
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        // No fade: every column sits at the same fractional offset, so a fade dimmed the whole screen
        // mid-swipe (cards over the black backdrop) and lifted again at rest.
        rotationY = o * 2f * 26f * strength
        translationY = away * 12.dp.toPx() * strength
        // A distant camera keeps the tilt a clean foreshortening with no clipped top/bottom corners.
        cameraDistance = 360.dp.toPx()
        val scale = 1f - 0.09f * strength * away
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A pager page's distance from its own resting slot, as a fraction of a page (-0.5..0.5). [raw] is
 * `currentPage - page + offsetFraction`; taking the fractional part is what makes a page flat at rest
 * (see [pageTurn]).
 */
internal fun pageOffsetFraction(currentPage: Int, page: Int, pageOffsetFraction: Float): Float {
    val raw = (currentPage - page) + pageOffsetFraction
    return raw - kotlin.math.round(raw)
}
