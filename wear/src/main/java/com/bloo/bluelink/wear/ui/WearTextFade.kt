package com.bloo.bluelink.wear

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.bloo.uicommon.LocalReduceMotion

/** How long a changed canvas text takes to cross-fade, the same as the Compose text's change fade. */
private const val TextFadeMs = 240

/**
 * Text drawn on a canvas, which the Compose text does not reach. When its string changes, the old text fades out while the new one fades
 * in, the same as [com.bloo.uicommon.RevealText] does for Compose text. Drawn in the draw phase, so the fade is a draw change, not a
 * recomposition.
 */
internal class CanvasTextFade {
    /** The text fading out while a change plays, or null when nothing is changing. */
    internal var from: String? = null
    internal val progress = Animatable(1f)

    /** Draws [text] as it is now: the old text and the new one cross-fading while a change plays, otherwise just [text] at full strength. */
    fun draw(text: String, drawOne: (String, Float) -> Unit) {
        val old = from
        val t = progress.value
        if (old != null && t < 1f) {
            drawOne(old, 1f - t)
            drawOne(text, t)
        } else {
            drawOne(text, 1f)
        }
    }
}

/** Remembers the fade for [text]: a change to it cross-fades on the canvas. The first text, and reduced motion, appear at once. */
@Composable
internal fun rememberCanvasTextFade(text: String): CanvasTextFade {
    val fade = remember { CanvasTextFade() }
    val shown = remember { arrayOf<String?>(null) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(text) {
        val before = shown[0]
        shown[0] = text
        if (before == null || before == text || reduce) {
            fade.from = null
            fade.progress.snapTo(1f)
            return@LaunchedEffect
        }
        fade.from = before
        fade.progress.snapTo(0f)
        fade.progress.animateTo(1f, tween(TextFadeMs))
        fade.from = null
    }
    return fade
}
