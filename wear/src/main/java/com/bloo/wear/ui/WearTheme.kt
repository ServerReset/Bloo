package com.bloo.wear.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.WearColorRoles
import com.bloo.bluelink.data.WearSettingsPayload

// ─────────────────────────────────────────────────────────────────────────────
// Bloo Wear theme layer
//
// One place that answers three questions for the whole watch UI:
//   1. What COLOURS? Either the phone's exact resolved palette (25 M3 roles synced
//      over the wire) or, before the first sync lands, the framework's default
//      expressive watch scheme.
//   2. What TYPE? A single deliberate scale where SIZE (not just weight/colour)
//      carries hierarchy — see [blooWearTypography].
//   3. Should we ANIMATE? Gated on the OS accessibility "remove animations" flag,
//      exposed as [LocalReduceMotion] so motion-heavy composables can go still.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * True when the user has turned animations OFF in the system Accessibility
 * settings (animator duration scale == 0). Motion-heavy composables (aurora
 * breathing, pager squeeze, rolling numerals) read this and render a static
 * frame instead. Defaults to `false` (motion allowed) outside a themed tree.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Brand accents that sit OUTSIDE the M3 role system — they mean the same thing
 * regardless of the synced palette (green == charging/good, red == heat,
 * blue == cool). Sourced from the shared [BlooColors] longs so the phone and
 * watch draw the identical hues; wrapped in Compose [Color] once here so call
 * sites use them directly.
 */
object WearColors {
    val chargeGreen = Color(BlooColors.chargeGreen)
    /** The charge ring's fill once the pack is AT its own limit -- "topped up," not
     *  "still filling." See ChargeRing and the phone's ChargeReadout.stuckAtLimit. */
    val chargeBlue  = Color(BlooColors.chargeBlue)
    val heat        = Color(BlooColors.heat)
    val cool        = Color(BlooColors.cool)
}

/**
 * Letter-spacing for the uppercase card / section "eyebrow" labels (used by
 * `SectionCard`'s title and `SettingGroupLabel`). Kept as a shared constant —
 * NOT baked into a [Typography] token — so both eyebrows track identically
 * while the plain caption tier ([Typography.labelSmall]) stays quiet. Baking
 * the tracking into `labelSmall` would widen every ordinary helper line too,
 * which is exactly the regression this constant exists to avoid.
 */
val EyebrowLetterSpacing = 0.8.sp

/**
 * Build a Wear Material 3 [ColorScheme] from the phone's resolved role colours.
 *
 * The phone owns colour resolution (dynamic/dark/per-car overrides), packs the
 * result into [WearColorRoles] — a flat bag of 25 ARGB ints — and syncs it. We
 * unpack all 25 here so the watch matches the phone exactly rather than
 * re-deriving a near-miss palette. Every role in [WearColorRoles] maps 1:1 onto
 * a Wear [ColorScheme] slot; keep them in lockstep if the DTO ever grows.
 */
fun schemeFrom(c: WearColorRoles): ColorScheme = ColorScheme(
    primary                 = Color(c.primary),
    onPrimary               = Color(c.onPrimary),
    primaryContainer        = Color(c.primaryContainer),
    onPrimaryContainer      = Color(c.onPrimaryContainer),
    secondary               = Color(c.secondary),
    onSecondary             = Color(c.onSecondary),
    secondaryContainer      = Color(c.secondaryContainer),
    onSecondaryContainer    = Color(c.onSecondaryContainer),
    tertiary                = Color(c.tertiary),
    onTertiary              = Color(c.onTertiary),
    tertiaryContainer       = Color(c.tertiaryContainer),
    onTertiaryContainer     = Color(c.onTertiaryContainer),
    background              = Color(c.background),
    onBackground            = Color(c.onBackground),
    onSurface               = Color(c.onSurface),
    onSurfaceVariant        = Color(c.onSurfaceVariant),
    surfaceContainerLow     = Color(c.surfaceContainerLow),
    surfaceContainer        = Color(c.surfaceContainer),
    surfaceContainerHigh    = Color(c.surfaceContainerHigh),
    outline                 = Color(c.outline),
    outlineVariant          = Color(c.outlineVariant),
    error                   = Color(c.error),
    onError                 = Color(c.onError),
    errorContainer          = Color(c.errorContainer),
    onErrorContainer        = Color(c.onErrorContainer),
)

/**
 * The Bloo watch type scale.
 *
 * The app once shipped stock Wear defaults, which collapsed almost everything to
 * ~2 effective sizes — hierarchy then rested on colour and position alone, so
 * headers read as "bold footnotes". This scale puts hierarchy back into SIZE, so
 * a glance readout dominates its card and captions recede, on every screen at
 * once (the app already routes all text through these tokens).
 *
 * Each style is `.copy()`d from the framework default so it KEEPS Wear's tuned
 * font family and tabular-numeral features — only size / weight / line-height
 * change. The scale, loudest to quietest:
 *   • numeralMedium / numeralSmall — hero glance numbers (range, battery %,
 *     temp, setpoint). Biggest lift: these are what should own a watch face.
 *   • titleMedium / titleSmall — card VALUES (the right-hand side of a
 *     StatusRow); a clear step above their labels.
 *   • bodyMedium / bodySmall — detail / reading text at a comfortable size.
 *   • labelMedium / labelSmall — captions and helper text; the quietest tier.
 *
 * Note the caption tier stays PLAIN weight on purpose: `labelSmall` is the token
 * for ordinary lowercase helper copy everywhere ("Pick your own temperature",
 * Settings descriptions), not only the card eyebrow. The eyebrow's Bold + wide
 * tracking lives at its two call sites via [EyebrowLetterSpacing]; if it were
 * folded in here, every helper line would render bold and wide.
 */
private fun blooWearTypography(): Typography {
    val d = Typography() // framework defaults — the source of each style's font + features
    return d.copy(
        // Hero glance numbers — big and tight so the value dominates its card.
        numeralMedium = d.numeralMedium.copy(fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        numeralSmall  = d.numeralSmall.copy(fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        // Card VALUES (StatusRow right-hand side) — a real step above the label.
        titleMedium   = d.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        titleSmall    = d.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        // Detail / body — comfortable reading size.
        bodyMedium    = d.bodyMedium.copy(fontSize = 14.sp),
        bodySmall     = d.bodySmall.copy(fontSize = 13.sp),
        // Captions / helper text — clearly the quietest tier. Plain weight; the
        // eyebrow's emphasis is applied at call sites, not baked in here.
        labelMedium   = d.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall    = d.labelSmall.copy(fontSize = 11.sp),
    )
}

/**
 * Root Wear OS Material 3 (Expressive) theme for the whole watch app.
 *
 * Colour source: when the phone has synced its resolved palette
 * ([WearSettingsPayload.colors] non-null) we paint with those exact roles so the
 * watch matches the phone; otherwise we fall back to the framework's default
 * expressive watch scheme (first launch / not yet paired). The 25-role scheme is
 * rebuilt ONLY when the synced roles actually change — `remember(c)` keys the
 * conversion on the [WearColorRoles] instance, so an unrelated recomposition
 * (a snapshot tick, a pending-command flip) does not re-allocate 25 Colors.
 *
 * LocalContentColor fix — DO NOT REMOVE, and note the import: this uses
 * `androidx.wear.compose.material3.LocalContentColor`, NOT the
 * `androidx.compose.material3` one.
 *
 * Wear Compose Material3's own `MaterialTheme` does NOT provide
 * [LocalContentColor] (verified against its source — it sets
 * LocalColorScheme / Shapes / Typography / MotionScheme and a couple of
 * swipe-dismiss scrim colours, but never content colour), and that
 * CompositionLocal's own undocumented default is [Color.Black]. `AppScaffold`
 * makes this land hard: WatchApp sets its containerColor to [Color.Transparent]
 * whenever Aurora is on (so the animated background shows through), and
 * `contentColorFor(Color.Transparent)` resolves to `Color.Unspecified` because
 * Transparent isn't one of the theme's ~15 known roles — which leaves
 * [LocalContentColor] sitting at that Black default for every bare `Text()` in
 * the app that doesn't explicitly pass its own colour (section labels,
 * descriptions, list headers, …). On the dark aurora background that renders as
 * black-on-black — invisible text (build 855). Providing it explicitly here,
 * once, at the true theme root fixes every screen regardless of which
 * scaffold / card wraps it, and is a no-op wherever something downstream already
 * sets its own content colour.
 *
 * We provide the scheme's [onBackground] (the synced scheme's when present, else
 * the framework default's, read before the inner MaterialTheme so it reflects the
 * ambient scheme at this point in the tree).
 */
@Composable
fun BlooWearTheme(settings: WearSettingsPayload?, content: @Composable () -> Unit) {
    val colors = settings?.colors
    val context = LocalContext.current

    // Honour the OS "remove animations" accessibility switch. Read once — the
    // process is recreated when the setting changes, so no observer is needed.
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    // Rebuild the 25-colour scheme only when the synced roles actually change.
    val scheme = colors?.let { c -> remember(c) { schemeFrom(c) } }
    val typography = remember { blooWearTypography() }

    // The 855 black-text fix (see the doc comment above). Prefer the synced
    // scheme's onBackground; fall back to the framework scheme's onBackground
    // when the phone hasn't synced colours yet.
    val onBackground = scheme?.onBackground ?: MaterialTheme.colorScheme.onBackground

    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalContentColor provides onBackground,
    ) {
        if (scheme != null) {
            MaterialTheme(colorScheme = scheme, typography = typography, content = content)
        } else {
            MaterialTheme(typography = typography, content = content)
        }
    }
}
