package com.bloo.bluelink.ui

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
import com.bloo.uicommon.dropShadow
import dev.chrisbanes.haze.HazeState

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
                    Modifier
                        .clip(shape)
                        .clickable(
                            interactionSource = interaction,
                            indication = ripple(),
                            onClickLabel = contentDescription,
                            onClick = onClick,
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
            Modifier
                .matchParentSize()
                .clip(shape)
                // Floating glass refracts what is behind it; a nested panel or card keeps the flat
                // blur.
                .then(
                    when {
                        !canBlur -> Modifier
                        liquid && hazeState != null -> Modifier.appGlassEffect(hazeState, shape, clear)
                        else -> Modifier.hazeWhenAble(hazeState)
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

/**
 * A plain `Float` parameter would still type-check, but passing one computed from
 * `someAnimatable.value` at the CALL SITE reads that value at composition time to build the
 * argument -- which is exactly the mistake both original copies of this code were once fixed for
 * (see either call site's own history): it recomposed the entire sheet -- the map's tile loop,
 * every button -- on every single frame of the open/close spring.
 */
@Composable
internal fun ScrimBlur(hazeState: HazeState?, progress: () -> Float, modifier: Modifier = Modifier) {
    // Both call sites always pass a real, non-null HazeState (each screen builds one
    // unconditionally via `remember { HazeState() }`), so `hazeState != null` alone was never
    // actually gating anything -- the blur ran unconditionally, battery saver or not.
    // canBlurBackdrops() is the real gate, same as every other blur site in the app.
    val canBlur = hazeState != null && canBlurBackdrops()
    // Full-screen scrim needs strong dimming in both themes: black with alpha.
    val tint = if (canBlur) Color.Black.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.22f)
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(tint, alpha = progress().coerceIn(0f, 1f))
            },
    )
    if (canBlur) {
        Box(
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
                .hazeWhenAble(hazeState, progressive = true),
        )
    }
}
