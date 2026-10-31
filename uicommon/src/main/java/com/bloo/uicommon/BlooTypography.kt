package com.bloo.uicommon

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bloo.bluelink.data.FontChoice

/** Map each weight of a variable font via fontVariationSettings (wght axis). */
internal fun variableFont(resId: Int, weight: FontWeight, axis: Int) = Font(
    resId,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
)

/** The typeface a choice names: the system's, or one of the two bundled variable fonts at the weights the app uses. */
fun blooFontFamily(choice: FontChoice): FontFamily = when (choice) {
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

/**
 * Apply the chosen typeface across the type scale, with the app's own defaults: every body, label and
 * title step is a notch bigger than Material's and carries at least Medium weight, so text stands out
 * instead of receding. One place decides this, so the whole app moves together.
 */
/**
 * The app's typography for a choice of font, shared by the phone and the watch so both set text the same way. With [classicHeadings],
 * headings and titles take the classical serif; body text keeps the chosen font.
 */
fun blooTypography(choice: FontChoice, classicHeadings: Boolean = false): Typography {
    val family = blooFontFamily(choice)
    // Headings and titles take a classical serif when asked; body text keeps the chosen font.
    val heading = if (classicHeadings) FontFamily.Serif else family
    val base = Typography()
    // Headings in the classic serif sit a little tighter: the serif reads as a deliberate, modern-classic setting rather than a default.
    fun TextStyle.heading(weight: FontWeight? = null): TextStyle = copy(
        fontFamily = heading,
        fontWeight = weight ?: fontWeight,
        // In sp, like the default: a TextStyle that mixes em and sp cannot be animated, and the hero blends its styles.
        letterSpacing = if (classicHeadings && fontSize.isSpecified) (-0.02f * fontSize.value).sp else letterSpacing,
    )
    fun TextStyle.bigger(scale: Float, weight: FontWeight? = null): TextStyle = copy(
        fontFamily = family,
        fontWeight = weight ?: fontWeight,
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
    )
    return base.copy(
        displayLarge = base.displayLarge.heading(FontWeight.Black),
        displayMedium = base.displayMedium.heading(FontWeight.Black),
        displaySmall = base.displaySmall.heading(FontWeight.ExtraBold),
        headlineLarge = base.headlineLarge.heading(FontWeight.ExtraBold),
        headlineMedium = base.headlineMedium.heading(FontWeight.Bold),
        headlineSmall = base.headlineSmall.heading(FontWeight.Bold),
        titleLarge = base.titleLarge.bigger(1.0f, FontWeight.SemiBold).heading(),
        titleMedium = base.titleMedium.bigger(1.06f, FontWeight.SemiBold),
        titleSmall = base.titleSmall.bigger(1.08f, FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.bigger(1.06f, FontWeight.Medium),
        bodyMedium = base.bodyMedium.bigger(1.08f, FontWeight.Medium),
        bodySmall = base.bodySmall.bigger(1.12f, FontWeight.Medium),
        labelLarge = base.labelLarge.bigger(1.08f, FontWeight.SemiBold),
        labelMedium = base.labelMedium.bigger(1.1f, FontWeight.Medium),
        labelSmall = base.labelSmall.bigger(1.1f, FontWeight.Medium),
    )
}
