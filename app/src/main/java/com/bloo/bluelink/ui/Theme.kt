package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.R

/** User-selectable appearance. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/**
 * User-selectable typeface. PRODUCT_SANS uses Poppins (an OFL geometric sans)
 * because Google's Product Sans is proprietary and cannot be redistributed.
 */
enum class FontChoice { SYSTEM, ATKINSON, PRODUCT_SANS }

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

// Expressive shapes: generous, rounded corners for a soft, modern feel.
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private fun fontFamilyFor(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.SYSTEM -> FontFamily.Default
    FontChoice.ATKINSON -> FontFamily(
        Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
        Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
    )
    FontChoice.PRODUCT_SANS -> FontFamily(
        Font(R.font.poppins_regular, FontWeight.Normal),
        Font(R.font.poppins_medium, FontWeight.Medium),
        Font(R.font.poppins_semibold, FontWeight.SemiBold),
        Font(R.font.poppins_bold, FontWeight.Bold),
    )
}

/** Apply the chosen typeface across the type scale and lean into bold display text. */
private fun expressiveTypography(choice: FontChoice): Typography {
    val family = fontFamilyFor(choice)
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
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

@Composable
fun BlooTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    fontChoice: FontChoice = FontChoice.SYSTEM,
    dynamicColor: Boolean = true,
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
        dark -> DarkExpressive
        else -> LightExpressive
    }

    val scheme = if (themeMode == ThemeMode.AMOLED) {
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

    MaterialTheme(
        colorScheme = scheme,
        typography = expressiveTypography(fontChoice),
        shapes = ExpressiveShapes,
        content = content,
    )
}
