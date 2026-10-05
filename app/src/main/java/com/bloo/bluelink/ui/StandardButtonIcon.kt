package com.bloo.bluelink.ui

import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.unit.dp

/**
 * The standard glyph for a button label, keyed on the user-facing string so one decision ("Copy
 * takes the copy glyph") lives in one place.
 */
fun standardButtonIcon(label: String): ImageVector? = when (label) {
    // Dismissive
    "Cancel", "Not now", "Dismiss" -> Icons.Filled.Close
    // Destructive: Close, since the app has no delete glyph and only reuses existing icons.
    "Clear", "Remove", "Delete", "Remove PIN" -> Icons.Filled.Close
    "Sign out" -> Icons.AutoMirrored.Filled.Logout
    "Unpin" -> Icons.Filled.PushPin
    "Done" -> Icons.Filled.Check
    // Confirmation: the second tap of a two-step action takes the affirmative glyph.
    "Tap again to confirm", "Tap again to reset", "Keep it", "Keep PIN" -> Icons.Filled.Check
    // Content. "Choose photo" opens the system picker; a label with no glyph can't compact its row.
    "Choose photo" -> Icons.Filled.FileOpen
    "Choose device" -> Icons.Filled.Bluetooth
    "Copy" -> Icons.Filled.ContentCopy
    "Export" -> Icons.Filled.Download
    "Restore" -> Icons.Filled.CloudDone
    "Save" -> Icons.Filled.Check
    "Save as preset" -> Icons.Filled.Star
    // Disclosure: one chevron for both directions.
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
 * Standard leading slot for a [MorphButton]: shows the [icon], or a same-sized spinner while
 * [pending], so the button width never changes just from loading.
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
        val angle = rememberSpinAngle(spinning)
        // The glyph cross-fades when it changes (lock to unlock, play to stop); the spin is on the
        // wrapper, in the draw phase: `angle` loops `while (true)` while pending, so reading it
        // through Modifier.rotate() recomposed the Icon on EVERY FRAME for as long as it ran.
        androidx.compose.animation.Crossfade(
            targetState = icon,
            modifier = Modifier.size(iconSize).graphicsLayer { rotationZ = angle.value },
            animationSpec = tween(MotionShort),
            label = "buttonGlyph",
        ) { glyph ->
            Icon(
                glyph,
                contentDescription = null,
                // Unspecified means "whatever the content colour is", which is Icon's own default.
                // It cannot simply be passed through: Icon treats Unspecified as "draw the vector's
                // own colours", which is a different thing entirely.
                tint = if (tint.isSpecified) tint else LocalContentColor.current,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** The gap between a button's glyph and its label. */
val ButtonIconGap = 8.dp

/**
 * A button's glyph and label as one layout that drops the label when it lacks room, the app's fit
 * rule. Intrinsics carry the contract: maxIntrinsicWidth is glyph + gap + label, minIntrinsicWidth
 * the glyph alone.
 */
@Composable
fun MorphButtonLabel(
    icon: ImageVector,
    label: String,
    pending: Boolean,
    iconSize: Dp = ButtonIconSize,
    spinning: Boolean = false,
    /**
     * An accent for the glyph alone, where it carries state the label doesn't; Unspecified uses the
     * content colour.
     */
    iconTint: Color = Color.Unspecified,
) {
    // Icon only: skip the Layout so no gap is reserved and the glyph stays centred.
    if (label.isEmpty()) {
        MorphButtonGlyph(icon, pending, iconSize, spinning, iconTint)
        return
    }
    val gap = ButtonIconGap
    // Lets the enclosing MorphButton learn the label and whether it shrank to its symbol (for the
    // long-press hint).
    val hint = LocalLabelHint.current
    SideEffect { hint?.describe(label, icon) }
    Layout(
        content = {
            MorphButtonGlyph(icon, pending, iconSize, spinning, iconTint)
            // The one button label style, shared with MorphTextButton.
            AnimatedText(
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
                    // Measure the label unbounded under the same `free` constraints so Compose
                    // caches its layout; maxIntrinsicWidth() re-laid it out every frame of a
                    // button-group press.
                    val text = measurables[1].measure(free)
                    // Whole label or no label -- anything in between is a truncated word. (Holding
                    // a symbol-only button shows its name in a bubble above it instead, see
                    // LabelHint.)
                    if (hint?.glyphOnly == true || constraints.maxWidth < glyph.width + gapPx + text.width) {
                        val w = glyph.width.coerceAtMost(constraints.maxWidth)
                        // Height still accounts for the undrawn label.
                        val h = maxOf(glyph.height, text.height)
                        return layout(w, h) {
                            hint?.collapsed = true
                            glyph.place((w - glyph.width) / 2, (h - glyph.height) / 2)
                        }
                    }
                    val w = glyph.width + gapPx + text.width
                    val h = maxOf(glyph.height, text.height)
                    return layout(w, h) {
                        hint?.collapsed = false
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
