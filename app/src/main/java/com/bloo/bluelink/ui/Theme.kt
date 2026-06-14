package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.R
import kotlinx.serialization.Serializable

/** User-selectable appearance. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/**
 * User-selectable typeface. GOOGLE_SANS uses Google Sans Flex — Google's
 * officially open-sourced (OFL) sibling of the proprietary Product Sans — so it
 * ships legitimately as the geometric option.
 */
enum class FontChoice { SYSTEM, ATKINSON, GOOGLE_SANS }

/**
 * Built-in colour palettes the user can pick from when dynamic colour
 * (Material You) is off. Each is derived from the hand-tuned Expressive scheme
 * by rotating its accent hues, so every palette keeps the same expressive
 * multi-hue structure and tonal balance.
 */
enum class ColorPalette(val label: String, val swatch: Color, internal val hue: Float) {
    BLUE("Bloo", Color(0xFF005AC1), 217f),
    VIOLET("Violet", Color(0xFF7B4DFF), 255f),
    TEAL("Teal", Color(0xFF00696E), 184f),
    GREEN("Forest", Color(0xFF3A6A2E), 107f),
    AMBER("Amber", Color(0xFFB26A00), 36f),
    ROSE("Rose", Color(0xFFB02E55), 338f),
}

/**
 * A user-authored colour palette. Each field stores a packed Android ARGB int
 * (same encoding as [android.graphics.Color]). Only [primaryArgb] is required;
 * secondary and tertiary default to the base scheme's relative offsets from primary.
 */
@Serializable
data class CustomPaletteData(
    val id: String,
    val name: String,
    val primaryArgb: Int,
    val secondaryArgb: Int? = null,
    val tertiaryArgb: Int? = null,
)

/** The Expressive scheme is authored around this primary hue (see [LightExpressive]). */
private const val BasePaletteHue = 217f

/** Rotate a colour's hue (HSV) by [degrees]; preserves saturation/value/alpha. */
private fun Color.rotateHue(degrees: Float): Color {
    if (degrees == 0f) return this
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv))
}

private fun Color.extractHue(): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return hsv[0]
}

/**
 * Recolour a scheme from a [CustomPaletteData]. Primary group rotates by however
 * much the user's primary hue differs from the base palette hue. Secondary and
 * tertiary each rotate independently if the user provided an override; otherwise
 * they follow the same delta as primary (preserving the expressive offset).
 */
internal fun ColorScheme.applyCustomPalette(p: CustomPaletteData): ColorScheme {
    val primaryDelta = Color(p.primaryArgb.toLong() and 0xFFFFFFFFL).extractHue() - BasePaletteHue
    fun Color.rp() = rotateHue(primaryDelta)

    val secDelta = p.secondaryArgb
        ?.let { Color(it.toLong() and 0xFFFFFFFFL).extractHue() - secondary.extractHue() }
        ?: primaryDelta
    fun Color.rs() = rotateHue(secDelta)

    val tertDelta = p.tertiaryArgb
        ?.let { Color(it.toLong() and 0xFFFFFFFFL).extractHue() - tertiary.extractHue() }
        ?: primaryDelta
    fun Color.rt() = rotateHue(tertDelta)

    return copy(
        primary = primary.rp(), onPrimary = onPrimary.rp(),
        primaryContainer = primaryContainer.rp(), onPrimaryContainer = onPrimaryContainer.rp(),
        secondary = secondary.rs(), onSecondary = onSecondary.rs(),
        secondaryContainer = secondaryContainer.rs(), onSecondaryContainer = onSecondaryContainer.rs(),
        tertiary = tertiary.rt(), onTertiary = onTertiary.rt(),
        tertiaryContainer = tertiaryContainer.rt(), onTertiaryContainer = onTertiaryContainer.rt(),
    )
}

/** Recolour the accent roles of a scheme to match [palette] by rotating their hue. */
private fun ColorScheme.applyPalette(palette: ColorPalette): ColorScheme {
    val delta = palette.hue - BasePaletteHue
    if (delta == 0f) return this
    fun Color.r() = rotateHue(delta)
    return copy(
        primary = primary.r(), onPrimary = onPrimary.r(),
        primaryContainer = primaryContainer.r(), onPrimaryContainer = onPrimaryContainer.r(),
        secondary = secondary.r(), onSecondary = onSecondary.r(),
        secondaryContainer = secondaryContainer.r(), onSecondaryContainer = onSecondaryContainer.r(),
        tertiary = tertiary.r(), onTertiary = onTertiary.r(),
        tertiaryContainer = tertiaryContainer.r(), onTertiaryContainer = onTertiaryContainer.r(),
    )
}

// --- Expressive color palettes -------------------------------------------
// A vibrant, high-emphasis Material 3 palette used when dynamic color
// (Material You) is unavailable or disabled.

private val LightExpressive = lightColorScheme(
    primary = Color(0xFF005AC1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF7B4DFF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DDFF),
    onSecondaryContainer = Color(0xFF21005D),
    tertiary = Color(0xFF00696E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF6FF6FF),
    onTertiaryContainer = Color(0xFF002022),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE0E2EC),
    error = Color(0xFFBA1A1A),
)

private val DarkExpressive = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFCFBCFF),
    onSecondary = Color(0xFF381E72),
    secondaryContainer = Color(0xFF4F378A),
    onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = Color(0xFF4CD9E0),
    onTertiary = Color(0xFF00373A),
    tertiaryContainer = Color(0xFF004F53),
    onTertiaryContainer = Color(0xFF6FF6FF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    error = Color(0xFFFFB4AB),
)

// Expressive shapes: generous rounded corners, with a cut-corner accent on the
// smallest slot to create the intentional "visual tension" of mixed geometry.
private val ExpressiveShapes = Shapes(
    extraSmall = CutCornerShape(6.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

/** Map each weight of a variable font via fontVariationSettings (wght axis). */
private fun variableFont(resId: Int, weight: FontWeight, axis: Int) = Font(
    resId,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
)

private fun fontFamilyFor(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.SYSTEM -> FontFamily.Default
    FontChoice.ATKINSON -> FontFamily(
        variableFont(R.font.atkinson_next, FontWeight.Normal, 400),
        variableFont(R.font.atkinson_next, FontWeight.Medium, 500),
        variableFont(R.font.atkinson_next, FontWeight.SemiBold, 600),
        variableFont(R.font.atkinson_next, FontWeight.Bold, 700),
        variableFont(R.font.atkinson_next, FontWeight.ExtraBold, 800),
    )
    FontChoice.GOOGLE_SANS -> FontFamily(
        variableFont(R.font.google_sans_flex, FontWeight.Normal, 400),
        variableFont(R.font.google_sans_flex, FontWeight.Medium, 500),
        variableFont(R.font.google_sans_flex, FontWeight.SemiBold, 600),
        variableFont(R.font.google_sans_flex, FontWeight.Bold, 700),
        variableFont(R.font.google_sans_flex, FontWeight.ExtraBold, 800),
    )
}

/** Apply the chosen typeface across the type scale and lean into bold display text. */
private fun expressiveTypography(choice: FontChoice): Typography {
    val family = fontFamilyFor(choice)
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family, fontWeight = FontWeight.Black),
        displayMedium = base.displayMedium.copy(fontFamily = family, fontWeight = FontWeight.Black),
        displaySmall = base.displaySmall.copy(fontFamily = family, fontWeight = FontWeight.ExtraBold),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.ExtraBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

/**
 * The default button fill. Buttons carry no outline, so the fill alone has to
 * read clearly against every surface they sit on (pebbles use surfaceVariant,
 * cards use surfaceContainer). We push surfaceContainerHighest away from the
 * background — lighter in dark themes, a touch darker in light themes — so an
 * idle button is always visible without a border.
 */
@Composable
fun buttonContainer(): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return if (dark) lerp(scheme.surfaceContainerHighest, scheme.onSurface, 0.18f)
    else lerp(scheme.surfaceContainerHighest, scheme.onSurface, 0.16f)
}

/** Scale a colour's saturation (HSV) by [factor]; 1 = unchanged. */
private fun Color.saturate(factor: Float): Color {
    if (factor == 1f) return this
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BlooTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontChoice: FontChoice = FontChoice.SYSTEM,
    dynamicColor: Boolean = true,
    colorPalette: ColorPalette = ColorPalette.BLUE,
    customPalette: CustomPaletteData? = null,
    uiScale: Float = 1f,
    vibrancy: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val canDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val base = when {
        canDynamic && dark -> dynamicDarkColorScheme(context)
        canDynamic && !dark -> dynamicLightColorScheme(context)
        customPalette != null && dark -> DarkExpressive.applyCustomPalette(customPalette)
        customPalette != null && !dark -> LightExpressive.applyCustomPalette(customPalette)
        dark -> DarkExpressive.applyPalette(colorPalette)
        else -> LightExpressive.applyPalette(colorPalette)
    }

    val amoled = if (themeMode == ThemeMode.AMOLED) {
        val black = Color(0xFF000000)
        base.copy(
            background = black,
            surface = black,
            surfaceContainerLowest = black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF101012),
            surfaceContainerHigh = Color(0xFF161618),
            surfaceContainerHighest = Color(0xFF1D1D1F),
        )
    } else {
        base
    }

    // Vibrancy: scale the saturation of every tinted colour role. This covers
    // accent colours, their on-colours, containers, and the surface-variant /
    // outline roles so pebble backgrounds and borders also respond visibly.
    val scheme = if (vibrancy == 1f) amoled else {
        fun Color.v() = saturate(vibrancy)
        amoled.copy(
            primary = amoled.primary.v(),
            onPrimary = amoled.onPrimary.v(),
            primaryContainer = amoled.primaryContainer.v(),
            onPrimaryContainer = amoled.onPrimaryContainer.v(),
            secondary = amoled.secondary.v(),
            onSecondary = amoled.onSecondary.v(),
            secondaryContainer = amoled.secondaryContainer.v(),
            onSecondaryContainer = amoled.onSecondaryContainer.v(),
            tertiary = amoled.tertiary.v(),
            onTertiary = amoled.onTertiary.v(),
            tertiaryContainer = amoled.tertiaryContainer.v(),
            onTertiaryContainer = amoled.onTertiaryContainer.v(),
            surfaceVariant = amoled.surfaceVariant.v(),
            onSurfaceVariant = amoled.onSurfaceVariant.v(),
            outline = amoled.outline.v(),
            outlineVariant = amoled.outlineVariant.v(),
            error = amoled.error.v(),
            onError = amoled.onError.v(),
            errorContainer = amoled.errorContainer.v(),
            onErrorContainer = amoled.onErrorContainer.v(),
        )
    }

    val density = LocalDensity.current
    val scaledDensity = Density(density.density, density.fontScale * uiScale)

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        typography = expressiveTypography(fontChoice),
        shapes = ExpressiveShapes,
    ) {
        CompositionLocalProvider(LocalDensity provides scaledDensity, content = content)
    }
}

/**
 * Overrides only the colour scheme for a per-car custom palette, inheriting
 * typography, shapes and motion from the surrounding [BlooTheme]. When
 * [paletteId] is null or unknown, [content] renders unchanged so cars without an
 * override keep the global theme.
 */
@Composable
internal fun CarThemeOverride(
    paletteId: String?,
    customPalettes: List<CustomPaletteData>,
    themeMode: ThemeMode,
    vibrancy: Float,
    content: @Composable () -> Unit,
) {
    val palette = paletteId?.let { id -> customPalettes.find { it.id == id } }
    if (palette == null) {
        content()
        return
    }
    val dark = when (themeMode) {
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val base = if (dark) DarkExpressive.applyCustomPalette(palette)
        else LightExpressive.applyCustomPalette(palette)
    val scheme = if (vibrancy == 1f) base else {
        fun Color.v() = saturate(vibrancy)
        base.copy(
            primary = base.primary.v(), onPrimary = base.onPrimary.v(),
            primaryContainer = base.primaryContainer.v(), onPrimaryContainer = base.onPrimaryContainer.v(),
            secondary = base.secondary.v(), onSecondary = base.onSecondary.v(),
            secondaryContainer = base.secondaryContainer.v(), onSecondaryContainer = base.onSecondaryContainer.v(),
            tertiary = base.tertiary.v(), onTertiary = base.onTertiary.v(),
            tertiaryContainer = base.tertiaryContainer.v(), onTertiaryContainer = base.onTertiaryContainer.v(),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
