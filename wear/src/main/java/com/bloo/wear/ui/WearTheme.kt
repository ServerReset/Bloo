package com.bloo.wear.ui

import android.provider.Settings
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.WearColorRoles
import com.bloo.bluelink.data.WearSettingsPayload

/** True when the user has disabled animations in Accessibility settings. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Brand accents reused across the watch UI, sourced from the shared BlooColors. */
object WearColors {
    val chargeGreen = Color(BlooColors.chargeGreen)
    val heat        = Color(BlooColors.heat)
    val cool        = Color(BlooColors.cool)
}

/** Build a Wear M3 [ColorScheme] from the phone's resolved role colours. */
fun schemeFrom(c: WearColorRoles): ColorScheme = ColorScheme(
    primary = Color(c.primary),
    onPrimary = Color(c.onPrimary),
    primaryContainer = Color(c.primaryContainer),
    onPrimaryContainer = Color(c.onPrimaryContainer),
    secondary = Color(c.secondary),
    onSecondary = Color(c.onSecondary),
    secondaryContainer = Color(c.secondaryContainer),
    onSecondaryContainer = Color(c.onSecondaryContainer),
    tertiary = Color(c.tertiary),
    onTertiary = Color(c.onTertiary),
    tertiaryContainer = Color(c.tertiaryContainer),
    onTertiaryContainer = Color(c.onTertiaryContainer),
    background = Color(c.background),
    onBackground = Color(c.onBackground),
    onSurface = Color(c.onSurface),
    onSurfaceVariant = Color(c.onSurfaceVariant),
    surfaceContainerLow = Color(c.surfaceContainerLow),
    surfaceContainer = Color(c.surfaceContainer),
    surfaceContainerHigh = Color(c.surfaceContainerHigh),
    outline = Color(c.outline),
    outlineVariant = Color(c.outlineVariant),
    error = Color(c.error),
    onError = Color(c.onError),
    errorContainer = Color(c.errorContainer),
    onErrorContainer = Color(c.onErrorContainer),
)

/**
 * A deliberate type scale (the app previously shipped stock Wear defaults, which
 * left almost everything at ~2 effective sizes, so hierarchy rested on colour and
 * position alone — headers read as "bold footnotes"). Each style is `.copy()`d
 * from the framework default so it KEEPS Wear's tuned font family + tabular
 * numeral features, and only size/weight/letter-spacing change. Encoding
 * hierarchy in SIZE re-tiers every screen at once, since the app already routes
 * through these tokens (titleMedium/titleSmall/bodySmall/labelSmall/label*).
 *
 * Built once from a default [Typography] instance (all params default), then a
 * handful overridden. Numerals get the biggest lift — glanceable readouts
 * (range, battery %, temp, setpoint) are what should dominate a watch face.
 */
private fun blooWearTypography(): Typography {
    val d = Typography() // framework defaults — the source of each style's font/features
    return d.copy(
        // Hero glance numbers — big and tight so a value dominates its card.
        numeralMedium = d.numeralMedium.copy(fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        numeralSmall = d.numeralSmall.copy(fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        // Card VALUES (StatusRow right-hand side) — a real step above the label.
        titleMedium = d.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = d.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        // Detail/body — comfortable reading size.
        bodyMedium = d.bodyMedium.copy(fontSize = 14.sp),
        bodySmall = d.bodySmall.copy(fontSize = 13.sp),
        // Captions/helper text — clearly the quietest tier. Plain weight: this is
        // the token for ordinary lowercase helper text all over the app ("Pick your
        // own temperature", Settings descriptions), NOT just the card eyebrow. The
        // eyebrow's Bold + wide tracking now lives at its two call sites
        // (SectionCard title, SettingGroupLabel) via [EyebrowLetterSpacing] so this
        // caption tier stays quiet instead of rendering every helper line bold.
        labelMedium = d.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall = d.labelSmall.copy(fontSize = 11.sp),
    )
}

/** Letter-spacing for the uppercase card/section "eyebrow" labels (SectionCard
 *  title, SettingGroupLabel). Shared so both eyebrows track identically without
 *  the value leaking into the plain [Typography.labelSmall] caption tier. */
val EyebrowLetterSpacing = 0.8.sp

/**
 * Wear OS Material 3 (Expressive) theme. When the phone has synced its resolved
 * colours, we paint with those so the watch matches the phone exactly; otherwise
 * we fall back to the framework's default expressive watch scheme.
 */
@Composable
fun BlooWearTheme(settings: WearSettingsPayload?, content: @Composable () -> Unit) {
    val colors = settings?.colors
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // Rebuild the 25-colour scheme only when the synced roles actually change.
    val scheme = colors?.let { c -> remember(c) { schemeFrom(c) } }
    val typography = remember { blooWearTypography() }
    // Wear Compose Material3's own MaterialTheme does NOT provide LocalContentColor
    // (verified against its source -- it sets LocalColorScheme/Shapes/Typography/
    // MotionScheme and a couple of swipe-dismiss scrim colors, but never content
    // color). Its Text() falls back to androidx.compose.material3.LocalContentColor
    // when nothing sets it, and THAT CompositionLocal's own undocumented default is
    // Color.Black. AppScaffold makes this land hard: WatchApp.kt sets its
    // containerColor to Color.Transparent whenever Aurora is on (so the animated
    // background shows through), and contentColorFor(Color.Transparent) resolves to
    // Color.Unspecified since Transparent isn't one of the theme's ~15 known roles --
    // which leaves LocalContentColor sitting at that Black default for every bare
    // Text() in the whole app that doesn't explicitly pass its own color (section
    // labels, descriptions, list headers, ...). Providing it explicitly here, once,
    // at the true theme root fixes every screen regardless of which scaffold/card
    // wraps it, and is a no-op wherever something downstream already sets its own.
    val onBackground = scheme?.onBackground ?: MaterialTheme.colorScheme.onBackground
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion, LocalContentColor provides onBackground) {
        if (scheme != null) {
            MaterialTheme(colorScheme = scheme, typography = typography, content = content)
        } else {
            MaterialTheme(typography = typography, content = content)
        }
    }
}
