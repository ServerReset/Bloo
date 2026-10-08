package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.sin

/**
 * The onboarding backdrop: a deep vertical gradient under big, soft blobs of the app's accent
 * palette, all in the current step's colour.
 *
 * Deliberately STATIC. It is a Haze source, so every frame it redraws is also a frame every glass
 * card and the bottom bar has to re-frost; a drifting backdrop made the whole flow janky. Drawn
 * once, the frost renders once and the deck stays smooth.
 */
@Composable
internal fun OnboardingAurora(hazeState: HazeState, accent: Color, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = appIsDarkTheme()
    val tertiary = scheme.tertiary
    val secondary = scheme.secondary
    val base = if (dark) 0.70f else 0.62f
    Box(
        modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(scheme.surfaceContainerHigh, scheme.surface, scheme.surfaceContainerLow),
                    ),
                )
                fun blob(c: Color, fx: Float, fy: Float, r: Float) {
                    val center = Offset(size.width * fx, size.height * fy)
                    val radius = size.minDimension * r
                    drawCircle(
                        brush = Brush.radialGradient(listOf(c, Color.Transparent), center = center, radius = radius),
                        radius = radius,
                        center = center,
                    )
                }
                blob(accent.copy(alpha = base), 0.28f, 0.20f, 0.80f)
                blob(tertiary.copy(alpha = base * 0.9f), 0.82f, 0.30f, 0.68f)
                blob(secondary.copy(alpha = base * 0.85f), 0.50f, 0.80f, 0.78f)
                // A brighter core behind the top card, and a counter-blob low so the deck never sits flat.
                blob(accent.copy(alpha = base * 0.7f), 0.24f, 0.22f, 0.40f)
                blob(tertiary.copy(alpha = base * 0.55f), 0.10f, 0.92f, 0.45f)
                // A slow diagonal sheen, fixed.
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.05f else 0.09f), Color.Transparent),
                        start = Offset(size.width * -0.05f, 0f),
                        end = Offset(size.width * 0.65f, size.height),
                    ),
                )
                // A scattered field of motes, in fixed positions.
                val moteAlpha = if (dark) 0.55f else 0.40f
                for (i in 0 until 20) {
                    val seed = i * 0.61803398875f
                    val fx = ((seed * 7.13f) % 1f + 1f) % 1f
                    val fy = ((seed * 3.71f) % 1f + 1f) % 1f
                    val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(i * 1.7f))
                    drawCircle(
                        color = Color.White.copy(alpha = moteAlpha * twinkle),
                        radius = (1.1f + (i % 3) * 0.9f).dp.toPx(),
                        center = Offset(size.width * fx, size.height * fy),
                    )
                }
                // A faint vignette so the chrome reads at the edges.
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, scheme.scrim.copy(alpha = if (dark) 0.25f else 0.06f)),
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            },
    )
}
