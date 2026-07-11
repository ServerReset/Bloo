package com.bloo.uicommon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp

/**
 * Shared color utilities between phone and watch. Both apps apply the same
 * M3-expressive treatment to their ColorScheme (push accent hue, blend
 * secondary/tertiary), so the derived colours are consistent.
 */
object BlooColors {
    /**
     * The default button fill colour. Used by [MorphButton] (or its wear twin)
     * so idle buttons read clearly against any surface they sit on, without
     * needing a border. Pushes the highest available surface tone slightly
     * toward `onSurface` — more in dark themes, less in light — so the
     * button always contrasts the surface behind it.
     */
    fun buttonContainer(surface: Color, onSurface: Color): Color {
        val dark = surface.luminance() < 0.5f
        return if (dark) lerp(surface, onSurface, 0.18f)
        else lerp(surface, onSurface, 0.20f)
    }

    /**
     * Determine the foreground colour (text/icon) on top of [accent] — dark on
     * light accents, white on dark. The luminance cutoff at 0.5 matches the
     * way M3 expressive themes typically reason about this.
     */
    fun onAccent(accent: Color): Color =
        if (accent.luminance() > 0.5f) Color(0xFF383838.toInt()) else Color.White

    /** A muted version of [accent] for secondary surfaces (e.g. inactive chips). */
    fun accentMuted(accent: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(accent.toArgbInt(), hsv)
        hsv[1] = (hsv[1] * 0.55f).coerceIn(0.1f, 0.5f)
        hsv[2] = (hsv[2] * 0.55f).coerceAtLeast(0.18f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun Color.toArgbInt(): Int {
        val a = (alpha * 255).toInt()
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
