package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * The app's standard text entrance: a block is revealed LINE BY LINE, each line a beat behind the one
 * above it, so a sentence builds itself top to bottom. Every text in the app draws through here
 * (directly or via the shared helpers), so all text enters identically.
 *
 * The reveal is a clip over the already-laid-out text, in the DRAW phase, so it never rebuilds the
 * string and never recomposes the text -- it costs one clip per frame while it runs and nothing once
 * it settles. That also clips each line's own halo, so a shadow can never appear before its line.
 *
 * Off entirely (text simply there) under reduce-motion OR battery saver, and for text too long to
 * animate cheaply.
 */

/** Whether the text entrance may play right now: off under reduce-motion or power saving. */
@Composable
private fun textRevealAllowed(): Boolean =
    !LocalReduceMotion.current && !LocalPowerPolicy.current.batterySaver

/** One line's own beat. Short -- the lines should trip out quickly, not crawl. */
private const val MsPerLine = 110

/** Bounds on a block's reveal, so a single line still eases and a long paragraph still finishes. */
private const val MinRevealMs = 160
private const val MaxRevealMs = 900

/**
 * The longest text the entrance will animate. Past this it draws settled instead: a very long block
 * (the activity log is one string of up to five hundred lines) would otherwise spend its whole
 * reveal budget on a flicker nobody asked for.
 */
private const val MaxRevealChars = 320

/**
 * The shared progress for one block's entrance, 0..1, over [lineCount] lines and scaled to the count
 * so a long paragraph builds at a steady per-line pace. Returns 1 (nothing to animate) when the
 * entrance is off or there is nothing laid out yet.
 */
@Composable
internal fun rememberTextReveal(lineCount: Int): State<Float> {
    val allowed = textRevealAllowed()
    val progress = remember { Animatable(if (allowed) 0f else 1f) }
    LaunchedEffect(lineCount, allowed) {
        if (!allowed || lineCount <= 0 || progress.value >= 1f) return@LaunchedEffect
        progress.animateTo(
            1f,
            tween(
                durationMillis = (lineCount * MsPerLine).coerceIn(MinRevealMs, MaxRevealMs),
                easing = LinearEasing,
            ),
        )
    }
    return progress.asState()
}

/**
 * A drop-in replacement for Material3's `Text` (the `String` overload) that writes itself out line by
 * line, in the draw phase. Every parameter of `Text` is supported, so any bare `Text(...)` in the app
 * can -- and does -- become a `RevealText(...)` with no other change.
 *
 * A settled block (or one with the entrance off) is a plain draw, so there is no per-frame cost once
 * it is done. When [text] changes the new value simply appears (no re-reveal): text that genuinely
 * streams or ticks would otherwise flash, and the components that want an ANIMATED update --
 * [RollingNumber] for values, `AnimatedContent` for a swap -- already provide it around their text.
 */
@Composable
internal fun RevealText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    // Draw settled -- no reveal machinery at all (the layout capture, the draw node, the chained
    // callback) -- when the entrance is off, or the text is too long to animate cheaply. Both cases
    // want it gone entirely.
    if (!textRevealAllowed() || text.length > MaxRevealChars) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style,
        )
        return
    }
    // Set once by the text layout; read in the draw phase so the reveal never recomposes.
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val progress = rememberTextReveal(layout?.lineCount ?: 0)
    // A rare caller-supplied onTextLayout still runs; the reveal's own capture is chained on top.
    val callerOnTextLayout by rememberUpdatedState(onTextLayout)
    Text(
        text = text,
        modifier = modifier.drawWithContent {
            val lr = layout
            val p = progress.value
            if (lr == null || p >= 1f || lr.lineCount <= 1) {
                drawContent()
                return@drawWithContent
            }
            // One full-width band per revealed line (the lines not yet due -- and, because the clip
            // covers the halo too, their shadows -- are simply not drawn).
            val path = Path()
            val n = lr.lineCount
            var i = 0
            while (i < n && p * n > i) {
                path.addRect(Rect(0f, lr.getLineTop(i), size.width, lr.getLineBottom(i)))
                i++
            }
            clipPath(path) { this@drawWithContent.drawContent() }
        },
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = { result ->
            layout = result
            callerOnTextLayout?.invoke(result)
        },
        style = style,
    )
}
