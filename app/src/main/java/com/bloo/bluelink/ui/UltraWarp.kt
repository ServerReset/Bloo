package com.bloo.bluelink.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Ultra glass bends the WHOLE picture, not only the glass: everything under this modifier (the aurora,
 * the backdrop, every card) is slowly refracted by a travelling liquid ripple, as if the entire app sat
 * behind one sheet of moving glass. Android 13+ (AGSL); a no-op elsewhere. Frozen (still warped, no
 * motion) under reduce-motion or battery saver, so it costs no continuous frames there.
 */
@Composable
internal fun Modifier.ultraWarp(enabled: Boolean): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val shader = remember { RuntimeShader(WARP_AGSL) }
    val animate = !LocalReduceMotion.current && !isBatterySaverOn()
    val phase = if (animate) {
        rememberInfiniteTransition(label = "ultraWarp").animateFloat(
            0f, (2 * Math.PI).toFloat(),
            infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
            label = "ultraWarpPhase",
        )
    } else null
    return this.graphicsLayer {
        shader.setFloatUniform("t", phase?.value ?: 0f)
        // Reads `phase` here, in the layer block: only the layer re-records per frame, not the content.
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

private const val WARP_AGSL = """
uniform shader content;
uniform float t;
half4 main(float2 p) {
    float2 d = float2(
        sin(p.y * 0.010 + t) + 0.6 * sin(p.x * 0.017 - t * 2.0),
        cos(p.x * 0.009 + t) + 0.6 * cos(p.y * 0.015 + t * 3.0)
    );
    return content.eval(p + d * 9.0);
}
"""
