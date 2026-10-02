package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView

/**
 * Whether the liquid-glass widget can run on this device: the lens pipeline needs Android 13 (AGSL),
 * the widget ships native code for arm only (an x86 emulator would crash loading it), and it is only
 * worth its cost when backdrop effects are on at all. Anything else keeps the plain blur.
 */
@Composable
internal fun rememberLiquidGlassSupported(): Boolean = remember {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        // The PRIMARY abi, not any: an x86 emulator lists arm as a translated secondary one.
        Build.SUPPORTED_ABIS.firstOrNull()?.startsWith("arm") == true &&
        !Build.FINGERPRINT.contains("generic") && !Build.HARDWARE.contains("ranchu") &&
        runCatching { Class.forName("com.example.liquidglass.LiquidGlassView") }.isSuccess
} && canBlurBackdrops()

/**
 * Real liquid glass for a floating element: an SDF lens that bends and disperses whatever is drawn
 * behind it at the rim, lit by a specular glint that follows the tilt of the phone. Draws only the
 * glass, behind whatever Compose content the caller lays over it, so it never takes a touch and the
 * caller's own click and content are untouched. The widget samples its parent, which is the window's
 * view, so cards and the aurora behind a floating chip are what get refracted.
 */
@Composable
internal fun LiquidGlassLayer(shape: Shape, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var radiusPx by remember { mutableFloatStateOf(999f) }
    AndroidView(
        factory = { context ->
            LiquidGlassView(context).apply {
                material = GlassMaterial.REGULAR
                enableDynamicBackground = true
                enableSensorHighlight = true
                enableAdaptiveTint = true
                enablePressEffect = false
                // Bendy at the rim: a deep, tall bevel whose bending falls off gently so it is read
                // across the whole edge band rather than as a thin line, with a visible colour split.
                refractionHeight = 240f
                bevelWidth = 72f
                refractionFalloff = 1.2f
                dispersionStrength = 0.2f
                isClickable = false
                isFocusable = false
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        },
        update = { it.cornerRadius = radiusPx },
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                radiusPx = (shape as? RoundedCornerShape)
                    ?.topStart?.toPx(Size(size.width.toFloat(), size.height.toFloat()), density)
                    ?: 999f
            },
    )
}
