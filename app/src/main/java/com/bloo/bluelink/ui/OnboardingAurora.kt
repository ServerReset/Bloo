package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.cos
import kotlin.math.sin

/**
 * The onboarding backdrop: a deep vertical gradient under big, soft, slowly drifting blobs of the
 * app's accent palette, every card's colour easing into the next.
 *
 * The blobs are radial gradients rather than solid circles, so the aurora still reads as a glow
 * where the blur cannot run (software-rendered emulators, battery saver). This whole layer is the
 * Haze source the glass cards frost, so its redraw rate is the rate the frost re-renders: hand
 * ticked at ~15fps, which is plenty for a slow drift and keeps that cost bounded.
 */
@Composable
internal fun OnboardingAurora(hazeState: HazeState, accent: Color, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = appIsDarkTheme()
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            t = (System.currentTimeMillis() - start) / 1000f
            kotlinx.coroutines.delay(66)
        }
    }
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
                // Each blob drifts on its own period, so the field never marches in lockstep.
                blob(accent.copy(alpha = base), 0.28f + 0.08f * sin(t * 0.45f), 0.20f + 0.06f * cos(t * 0.35f), 0.80f)
                blob(tertiary.copy(alpha = base * 0.9f), 0.82f + 0.07f * cos(t * 0.38f), 0.30f + 0.08f * sin(t * 0.50f), 0.68f)
                blob(secondary.copy(alpha = base * 0.85f), 0.50f + 0.10f * sin(t * 0.30f), 0.80f + 0.06f * cos(t * 0.42f), 0.78f)
                // A brighter core behind the hero, and a counter-blob low so the deck never sits flat.
                blob(accent.copy(alpha = base * 0.7f), 0.24f, 0.22f, 0.40f)
                blob(tertiary.copy(alpha = base * 0.55f), 0.10f + 0.05f * cos(t * 0.55f), 0.92f, 0.45f)
                // A slow diagonal sheen drifting across the whole field.
                val sweep = (t * 0.055f) % 1f
                val sx = -0.4f + 1.8f * sweep
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.05f else 0.09f), Color.Transparent),
                        start = Offset(size.width * (sx - 0.35f), 0f),
                        end = Offset(size.width * (sx + 0.35f), size.height),
                    ),
                )
                // Twinkling motes drifting up through the light: stable per index, driven by the same
                // slow tick so they add no extra redraws.
                val moteAlpha = if (dark) 0.55f else 0.40f
                for (i in 0 until 20) {
                    val seed = i * 0.61803398875f
                    val fx = ((seed * 7.13f) % 1f + 1f) % 1f
                    val fy = ((seed * 3.71f) % 1f + 1f) % 1f
                    val speed = 0.018f + (i % 3) * 0.011f
                    val yFrac = ((fy - t * speed) % 1f + 1f) % 1f
                    val xFrac = (fx + 0.03f * sin(t * 0.3f + i)).coerceIn(0f, 1f)
                    val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.8f + (i % 4) * 0.2f) + i))
                    drawCircle(
                        color = Color.White.copy(alpha = moteAlpha * twinkle),
                        radius = (1.1f + (i % 3) * 0.9f).dp.toPx(),
                        center = Offset(size.width * xFrac, size.height * yFrac),
                    )
                }
                // A faint vignette so the glass chrome reads at the edges.
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
