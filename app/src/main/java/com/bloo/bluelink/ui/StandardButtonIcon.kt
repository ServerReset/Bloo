@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

/**
 * The standard glyph for a button label.
 *
 * Keyed on the user-facing string, which is unusual and worth justifying: the alternative was
 * editing 45 call sites across a dozen files and adding an icon import to each, for a decision
 * ("Copy takes the copy glyph") that is the same everywhere and belongs in one place. Keeping it
 * central means the app cannot drift into three different icons for Clear, and a new button gets
 * the right one for free.
 *
 * It fails safe: an unrecognised label simply has no icon, and any call site can pass its own.
 * The cost is that renaming a label silently drops its glyph, which is why the map is grouped by
 * meaning rather than alphabetised -- a rename lands next to its neighbours.
 */
fun standardButtonIcon(label: String): ImageVector? = when (label) {
    // Dismissive
    "Cancel", "Not now", "Dismiss" -> Icons.Filled.Close
    // Destructive. Close, not a bin: this app's vocabulary has no delete glyph, and every icon
    // here is one the codebase already uses -- see the note above on why that constraint exists.
    "Clear", "Remove", "Delete", "Remove PIN" -> Icons.Filled.Close
    "Sign out" -> Icons.AutoMirrored.Filled.Logout
    "Unpin" -> Icons.Filled.PushPin
    "Done" -> Icons.Filled.Check
    // Confirmation -- the second tap of a two-step action, so it takes the affirmative glyph
    // rather than the destructive one it is confirming.
    "Tap again to confirm", "Tap again to reset", "Keep it", "Keep PIN" -> Icons.Filled.Check
    // Content. "Choose photo" takes FileOpen rather than a camera or gallery glyph: it opens the
    // system picker, and every icon in this map is one the codebase already uses -- see the note
    // above on why that constraint exists. It was the one button label left with no glyph, which
    // matters more now than it did: a button with no glyph cannot fall back to one, so it is the
    // one thing that can stop a whole row from compacting.
    "Choose photo" -> Icons.Filled.FileOpen
    "Choose device" -> Icons.Filled.Bluetooth
    "Copy" -> Icons.Filled.ContentCopy
    "Export" -> Icons.Filled.Download
    "Restore" -> Icons.Filled.CloudDone
    "Save" -> Icons.Filled.Check
    "Save as preset" -> Icons.Filled.Star
    // Disclosure. KeyboardArrowDown for both directions: the chevron this app already uses for
    // every expand control, rather than a second pair of glyphs meaning the same thing.
    "Show", "Full notes", "Trouble installing?" -> Icons.Filled.KeyboardArrowDown
    "Hide", "Hide install help", "Hide diagnostics" -> Icons.Filled.KeyboardArrowDown
    // Navigation / external
    "GitHub", "Open release page" -> Icons.AutoMirrored.Filled.OpenInNew
    "Simulate leaving" -> Icons.Filled.DirectionsCar
    // Sync / run
    "Test sync", "Pull from primary", "Change Drive file" -> Icons.Filled.CloudSync
    "Run it", "Working…" -> Icons.Filled.Bolt
    "Remind me", "Retry" -> Icons.Filled.Refresh
    // Security
    "Use biometrics" -> Icons.Filled.Fingerprint
    "Use PIN", "Update PIN", "Change PIN", "Set up PIN" -> Icons.Filled.Pin
    // Places / appearance
    "Set place" -> Icons.Filled.Place
    "Reset appearance" -> Icons.Filled.Palette
    else -> null
}


/**
 * Standard leading slot for a [MorphButton]: shows the [icon], or a same-sized
 * spinner while [pending], so the button width never changes just from loading.
 */
@Composable
private fun MorphButtonGlyph(
    icon: ImageVector,
    pending: Boolean,
    iconSize: Dp,
    spinning: Boolean,
    tint: Color,
) {
    if (pending) {
        LoadingIndicator(Modifier.size(iconSize))
    } else {
        // Always-composed Animatable, but it only runs while spinning - so idle
        // buttons don't each hold a live infinite animation, and we avoid calling
        // remember conditionally.
        val angle = remember { Animatable(0f) }
        LaunchedEffect(spinning) {
            if (spinning) {
                // Ramp up: the first revolution accelerates from rest...
                angle.animateTo(
                    targetValue = angle.value + 360f,
                    animationSpec = tween(durationMillis = 850, easing = FastOutLinearInEasing),
                )
                // ...then hold a steady, fast linear spin.
                while (true) {
                    angle.animateTo(
                        targetValue = angle.value + 360f,
                        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
                    )
                }
            } else if (angle.value != 0f) {
                // Ramp down: decelerate to the next full turn, then reset.
                val target = kotlin.math.ceil(angle.value / 360f) * 360f
                angle.animateTo(target, tween(durationMillis = 700, easing = LinearOutSlowInEasing))
                angle.snapTo(0f)
            }
        }
        Icon(
            icon,
            contentDescription = null,
            // Unspecified means "whatever the content colour is", which is Icon's own default.
            // It cannot simply be passed through: Icon treats Unspecified as "draw the vector's
            // own colours", which is a different thing entirely.
            tint = if (tint.isSpecified) tint else LocalContentColor.current,
            // Draw-phase read. This one matters most of the three: `angle` is a
            // continuously-running spin (the pending/refresh indicator loops
            // `while (true)`), so reading it through Modifier.rotate()'s argument
            // recomposed this Icon on EVERY FRAME for as long as the spinner ran.
            modifier = Modifier.size(iconSize).graphicsLayer { rotationZ = angle.value },
        )
    }
}


/** The gap between a button's glyph and its label. */
val ButtonIconGap = 8.dp


/**
 * A button's glyph and label as ONE layout that can drop the label when it is not given the room
 * for it -- the app's fit rule, in the one place every button's content already goes through.
 *
 * The contract is stated through intrinsics, which is what lets the button group act on it
 * without knowing anything about labels: maxIntrinsicWidth is the full glyph + gap + label, and
 * minIntrinsicWidth is the glyph alone. So "how small can this button get and still make sense"
 * is a question the layout above can simply ask, and the answer is icon-only rather than a
 * truncated word.
 *
 * A label is never ellipsized and never wrapped on the way down. It is shown whole or not at
 * all: half a word in a button is worse than a glyph, and a wrapped one turns a one-line button
 * into a two-line one mid-press, which shoves the whole row's height around.
 */
@Composable
fun MorphButtonLabel(
    icon: ImageVector,
    label: String,
    pending: Boolean,
    iconSize: Dp = ButtonIconSize,
    spinning: Boolean = false,
    /**
     * An accent for the glyph alone, where it carries state the label does not -- a charging
     * bolt, a snowflake for climate. Unspecified (the default) keeps every button's glyph on
     * the same content colour as its text, which is what almost all of them want.
     */
    iconTint: Color = Color.Unspecified,
) {
    // Icon only, genuinely -- not icon-plus-an-empty-label. Skipping the whole Layout below
    // when there is no label to place avoids reserving ButtonIconGap for a gap with nothing on
    // the other side of it, which is a real, visible difference: an icon-only header action
    // (DiagnosticsPebble's warning glyph, say) would otherwise sit a gap's width off-centre in
    // its own button.
    if (label.isEmpty()) {
        MorphButtonGlyph(icon, pending, iconSize, spinning, iconTint)
        return
    }
    val gap = ButtonIconGap
    Layout(
        content = {
            MorphButtonGlyph(icon, pending, iconSize, spinning, iconTint)
            // The one button label style, shared with MorphTextButton -- this pair is the
            // reference the rest of the app standardises on, so the size lives in a token
            // rather than being whatever each button happened to inherit.
            Text(
                label,
                style = ButtonLabelStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        },
        measurePolicy = remember(gap) {
            object : MeasurePolicy {
                override fun MeasureScope.measure(
                    measurables: List<Measurable>,
                    constraints: Constraints,
                ): MeasureResult {
                    val gapPx = gap.roundToPx()
                    val free = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0)
                    val glyph = measurables[0].measure(free)
                    // Measure the label at its OWN natural size (unbounded), NOT
                    // maxIntrinsicWidth(). The button GROUP drives a member's width on every
                    // frame of a press, and each width change re-runs this measure; measuring the
                    // label under the SAME `free` constraints every time lets Compose cache the
                    // text layout, where maxIntrinsicWidth() re-laid-out the label on every single
                    // frame of the press -- the actual source of the "button group press is janky"
                    // report on the map toolbar and the accounts card.
                    val text = measurables[1].measure(free)
                    // Whole label or no label. Anything in between is a truncated word.
                    if (constraints.maxWidth < glyph.width + gapPx + text.width) {
                        val w = glyph.width.coerceAtMost(constraints.maxWidth)
                        // Height still accounts for the label that is NOT being drawn (its own
                        // natural height, already measured above).
                        val h = maxOf(glyph.height, text.height)
                        return layout(w, h) {
                            glyph.place((w - glyph.width) / 2, (h - glyph.height) / 2)
                        }
                    }
                    val w = glyph.width + gapPx + text.width
                    val h = maxOf(glyph.height, text.height)
                    return layout(w, h) {
                        glyph.place(0, (h - glyph.height) / 2)
                        text.place(glyph.width + gapPx, (h - text.height) / 2)
                    }
                }

                /** Glyph only: the smallest this content can be and still say something. */
                override fun IntrinsicMeasureScope.minIntrinsicWidth(
                    measurables: List<IntrinsicMeasurable>,
                    height: Int,
                ): Int = measurables[0].maxIntrinsicWidth(height)

                override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                    measurables: List<IntrinsicMeasurable>,
                    height: Int,
                ): Int = measurables[0].maxIntrinsicWidth(height) +
                    gap.roundToPx() +
                    measurables[1].maxIntrinsicWidth(height)

                override fun IntrinsicMeasureScope.minIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int,
                ): Int = measurables.maxOf { it.minIntrinsicHeight(width) }

                override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int,
                ): Int = measurables.maxOf { it.maxIntrinsicHeight(width) }
            }
        },
    )
}


// --- Pebble (expandable, reorderable section) -----------------------------
