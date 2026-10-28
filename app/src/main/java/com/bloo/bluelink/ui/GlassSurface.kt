package com.bloo.bluelink.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import dev.chrisbanes.haze.HazeState
import com.bloo.uicommon.canBlurBackdrops
import com.bloo.uicommon.glassTint
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
    tint: Color = glassTint(hazeState != null && canBlurBackdrops()),
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
    liquid: Boolean = shadow,
    /**
     * A plain, small-radius blur instead of the liquid refraction. For the many SMALL surfaces (the
     * status chips on every car): the refraction's bend is invisible at that size, but it costs a
     * displacement + chromatic pass on top of the blur -- and these are the most numerous glass
     * nodes on screen, so it is the one place the cheap path is worth more than the look.
     */
    cheap: Boolean = false,
    /** Bare glass (see [appGlassEffect]): the caller passes a transparent [tint] too. */
    clear: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val canBlur = hazeState != null && canBlurBackdrops()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .glassEdge(shape, shadow = shadow)
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
        // One node for blur+tint: both draw in the Box's own modifier chain (blur first, tint on
        // top).
        Box(
            Modifier.matchParentSize().clip(shape)
                // Floating glass refracts what is behind it; a nested panel or card keeps the flat
                // blur.
                .then(
                    when {
                        !canBlur -> Modifier
                        liquid -> Modifier.appGlassEffect(hazeState, shape, clear)
                        cheap -> Modifier.appHazeEffect(hazeState, cheap = true)
                        else -> Modifier.appHazeEffect(hazeState)
                    },
                )
                .background(tint)
                .glassSheen(),
        )
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

