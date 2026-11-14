package com.bloo.bluelink.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.HazeState
import com.bloo.uicommon.blurCapable
import androidx.compose.ui.draw.drawWithContent
import com.bloo.uicommon.canBlurBackdrops
import com.bloo.uicommon.glassTint
import com.bloo.uicommon.LocalPowerPolicy
import com.bloo.uicommon.content

/**
 * The app's one floating-glass surface: a Haze backdrop blur of [hazeState] (when given and
 * [canBlurBackdrops] allows: API 31+, battery saver off) under a neutral [tint], inside the shared
 * [dropShadow]/[appGlassRim] edge treatment. [modifier] carries size, position and interaction like
 * a plain `Box`.
 */
@Composable
internal fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    tint: Color = glassTint(hazeState != null && blurCapable()),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    interactionSource: MutableInteractionSource? = null,
    contentAlignment: Alignment = Alignment.Center,
    /** See [glassEdge]; false for a glass panel nested inside an already-elevated card. */
    shadow: Boolean = true,
    /**
     * Floating elements want it; nested panels and large cards want the flat blur, so it follows
     * [shadow] by default.
     */
    liquid: Boolean = !LocalPowerPolicy.current.lean,
    /**
     * A plain, small-radius blur instead of the liquid refraction. For the many SMALL surfaces (the
     * status chips on every car): the refraction's bend is invisible at that size, but it costs a
     * displacement + chromatic pass on top of the blur -- and these are the most numerous glass
     * nodes on screen, so it is the one place the cheap path is worth more than the look.
     */
    cheap: Boolean = LocalPowerPolicy.current.lean,
    /** Bare glass (see [appGlassEffect]): the caller passes a transparent [tint] too. */
    clear: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    // blurCapable, not canBlurBackdrops: the pause while the garage moves is honoured in draw (below), so a swipe starting or ending
    // neither recomposes this surface nor tears down and rebuilds its blur.
    val canBlur = hazeState != null && blurCapable()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    // Whether any of this surface is on screen, from its own place in the window: no column or page arithmetic, so every layout (multi-column,
    // pagers, sheets) is judged the same way. A surface wholly off the window draws no blur, which nobody can see; it changes only when the
    // surface crosses the window's edge, so it does not recompose on every move. The margin keeps the blur on a little past the edge.
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val marginPx = with(density) { 32.dp.toPx() }
    var inView by remember { mutableStateOf(true) }
    Box(
        modifier = modifier
            .glassEdge(shape, shadow = shadow)
            .onGloballyPositioned { c ->
                val p = c.positionInWindow()
                val r = Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
                val window = Rect(-marginPx, -marginPx, windowSize.width + marginPx, windowSize.height + marginPx)
                val now = r.overlaps(window)
                if (now != inView) inView = now
            }
            .then(
                if (onClick != null) {
                    Modifier.clip(shape).clickable(
                            interactionSource = interaction,
                            indication = ripple(),
                            onClickLabel = contentDescription,
                            // A button, and named: an icon-only glass button (refresh, back) had no accessible name at
                            // all -- the label above was only the verb in "double tap to ...", on an unnamed node.
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClick = onClick,
                        )
                        .then(
                            if (contentDescription != null) {
                                val name: String = contentDescription
                                Modifier.semantics { this.contentDescription = name }
                            } else {
                                Modifier
                            },
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = contentAlignment,
    ) {
        // The blur, under the tint. Kept attached while the garage moves and only not DRAWN then (a draw-phase read of the pause), so
        // the glass goes clear for the moment of a swipe, with its tint still on, and is back on the settle frame without being rebuilt.
        if (canBlur && inView) {
            Box(
                Modifier.matchParentSize().clip(shape)
                    // Floating glass refracts what is behind it; a nested panel or card keeps the flat blur.
                    .then(
                        when {
                            liquid -> Modifier.appGlassEffect(hazeState, shape, clear)
                            cheap -> Modifier.appHazeEffect(hazeState, cheap = true)
                            else -> Modifier.appHazeEffect(hazeState)
                        },
                    ),
            )
        }
        // The tint and sheen, on top of the blur.
        Box(
            Modifier.matchParentSize().clip(shape)
                .background(tint)
                .glassSheen(),
        )
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

