package com.bloo.bluelink.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/*
 * Text that is never unreadable, as one hierarchy. Every layer that text can sit on describes what is behind it as a [BandLuma]
 * (its brightness range, not just an average):
 *
 *   page surface  ->  glass card / photo band (its own tint or scrim laid over the layer below)  ->  chip or status line
 *
 * A container publishes its band through [LocalBackdropBand] (a child that tints further publishes the blended band), and any text
 * colour drawn inside it asks [legible] -- or [readableOn] with an explicit band -- to be nudged, keeping its hue, until it clears
 * WCAG AA against the WORST part of that band. So a green "Charging" on a pale photo goes deep green, a white title on a washed
 * out sky goes dark, and a colour crossing from the plain card to the photo mid-morph never lands on an unreadable grey.
 */

/** The brightness range behind the content being drawn; null where nothing describes it (text then keeps its own colour). */
internal val LocalBackdropBand = compositionLocalOf<BandLuma?> { null }

/** A flat colour as a band of zero spread. */
internal fun surfaceBand(color: Color): BandLuma = color.luminance().let { BandLuma(it, it, it) }

/** The band part-way from [a] to [b], for a backdrop that morphs from one layer to another. */
internal fun lerpBand(a: BandLuma, b: BandLuma, t: Float): BandLuma = BandLuma(
    mean = a.mean + (b.mean - a.mean) * t,
    low = a.low + (b.low - a.low) * t,
    high = a.high + (b.high - a.high) * t,
)

/** The worst WCAG contrast a text of luminance [textLuma] reaches anywhere in [bg]; 1 when the text's own brightness lies inside the band. */
internal fun worstContrast(textLuma: Float, bg: BandLuma): Float {
    if (textLuma in bg.low..bg.high) return 1f
    return minOf(contrastRatio(textLuma, bg.low), contrastRatio(textLuma, bg.high))
}

/**
 * This colour, kept as close to itself as it can be while reaching [target] contrast against the worst part of [bg]: unchanged if it
 * already does, otherwise blended toward white or black (whichever the band allows more contrast with) just far enough, or all the
 * way if that is the best there is. Hue is kept; only lightness moves.
 */
internal fun Color.readableOn(bg: BandLuma, target: Float = 4.5f): Color {
    if (worstContrast(luminance(), bg) >= target) return this
    val towardWhite = contrastRatio(1f, bg.high) >= contrastRatio(0f, bg.low)
    val end = if (towardWhite) Color.White else Color.Black
    if (worstContrast(end.luminance(), bg) < target) return end
    var lo = 0f
    var hi = 1f
    repeat(10) {
        val mid = (lo + hi) / 2f
        if (worstContrast(lerp(this, end, mid).luminance(), bg) >= target) hi = mid else lo = mid
    }
    return lerp(this, end, hi)
}

/** This colour, made readable on whatever [LocalBackdropBand] says is behind it; unchanged where nothing says. */
@Composable
internal fun Color.legible(): Color = LocalBackdropBand.current?.let { readableOn(it) } ?: this

/**
 * The hero title's colour at morph step [t] (0 plain card, 1 photo): its on-photo tone, kept readable against the band the card
 * shows at that step -- the plain surface blending into the top of the photo under its scrim.
 */
@Composable
internal fun heroTitleColor(tone: HeroPhotoTone, t: Float): Color {
    val scheme = MaterialTheme.colorScheme
    val base = lerp(scheme.onSurface, heroTitleOnPhoto(tone), t)
    val top = tone.top ?: return base
    return base.readableOn(lerpBand(surfaceBand(scheme.surface), top.behind, t))
}

/** The backdrop the hero's readout sits on at morph step [t]: the plain surface blending into the bottom of the photo under its scrim; null before the photo is measured. */
@Composable
internal fun heroReadoutBand(tone: HeroPhotoTone, t: Float): BandLuma? =
    tone.bottom?.let { lerpBand(surfaceBand(MaterialTheme.colorScheme.surface), it.behind, t) }

/** The hero readout's colour at morph step [t], kept readable on its band. */
@Composable
internal fun heroReadoutColor(tone: HeroPhotoTone, t: Float): Color {
    val base = lerp(MaterialTheme.colorScheme.onSurface, heroReadoutOnPhoto(tone), t)
    return heroReadoutBand(tone, t)?.let { base.readableOn(it) } ?: base
}
