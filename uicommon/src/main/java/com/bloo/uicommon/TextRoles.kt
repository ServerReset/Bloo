package com.bloo.uicommon

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier

// THE two text roles, shared by the phone and the watch: every line of text is one of these, so a reader always knows what it is.

/**
 * The app's one style for "small text that is just there" -- captions, labels, muted one-liners.
 * Bigger and Medium-weight so it reads without squinting, full-strength onSurface so it clears the
 * contrast bar on glass, and a soft blurred halo in the surface colour behind the glyphs so it holds
 * its own over the aurora instead of melting into it. Every secondary-text helper routes through
 * here, so the treatment is identical everywhere.
 */
@Composable
fun secondaryTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    fontWeight = FontWeight.SemiBold,
    shadow = secondaryTextShadow(),
)

/**
 * THE two text roles of the app. Every line of text is one of these, so a reader always knows what it is:
 * - PRIMARY: what the line says (a name, a value, a title, a row's label). Body large, medium weight, full contrast.
 * - SECONDARY: what it is about (a caption, a status, a unit, a hint, an explanation). Body medium, semi-bold, muted,
 *   with the soft halo that lifts it off a busy backdrop.
 * Figures (the big percentage, the range) are a third thing, drawn by their own components, not text in either role.
 */
@Composable
fun PrimaryTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)

@Composable
fun SecondaryTextStyle(): TextStyle = secondaryTextStyle()

/** Secondary text's colour: onSurface, barely eased -- contrast, not a grey wash. */
@Composable
fun secondaryTextColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)

/**
 * The halo behind secondary text: a blurred copy of the surface colour the text sits on, not a hard
 * shadow, so glyph edges lift off a busy background without a visible drop shadow.
 */
@Composable
fun secondaryTextShadow(): Shadow = Shadow(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
    blurRadius = SecondaryHaloBlurPx,
)

/** A decently strong halo, in device px (text shadows are not density-scaled). */
internal const val SecondaryHaloBlurPx = 9f

/** The PRIMARY role as text: what a line says, in the full text colour. */
@Composable
fun PrimaryText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = RevealText(
    text,
    style = PrimaryTextStyle(),
    modifier = modifier,
    color = MaterialTheme.colorScheme.onSurface,
    maxLines = maxLines,
    overflow = overflow,
)

/** The SECONDARY role as text: what a line is about. Same as [BodySmallText]. */
@Composable
fun SecondaryText(text: String, modifier: Modifier = Modifier) = RevealText(text, style = SecondaryTextStyle(), modifier = modifier, color = secondaryTextColor())
