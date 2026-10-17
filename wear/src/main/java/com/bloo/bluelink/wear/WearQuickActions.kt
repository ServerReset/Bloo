package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.MorphButton
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.standardButtonIcon

/**
 * One quick action inside the hero ring: a glyph in a circle, filling its share of a row.
 *
 * The label is not lost when it is not drawn -- it becomes the button's spoken description, and it is
 * what asks the app's own vocabulary for the glyph when [icon] is null.
 */
data class WearQuickAction(
    val label: String,
    /** Null asks the app's own vocabulary for the glyph this label uses (see [standardButtonIcon]). */
    val icon: ImageVector? = null,
    val active: Boolean = false,
    /** The command is in flight: the glyph becomes the app's shared spinner and taps are ignored. */
    val pending: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The car's everyday actions as a grid of 48dp circles, two to a row, inside the hero ring.
 *
 * Icon-only rather than labelled, and that is geometry rather than taste: the ring fills the
 * display's bounding SQUARE, a round display only fills the CIRCLE inside it, and a labelled 2x2 grid
 * sits in that square's lower half -- which puts its outer corners past the bezel. Four 48dp circles
 * are 104dp across and stay inside with the value above them.
 *
 * It draws through the app's own [MorphButtonLabel], so the glyph, the label layout and the in-flight
 * spinner are the same ones every phone button uses rather than a second set built on the same core.
 * There was a labelled variant here too; nothing called it, and it is gone.
 */
@Composable
fun WearQuickActions(
    actions: List<WearQuickAction>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        actions.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEach { a ->
                    val tint = if (a.active) scheme.onPrimary else scheme.onSecondaryContainer
                    val glyph = a.icon ?: standardButtonIcon(a.label)
                    // The app's own button, with both corner percentages at 50 -- which is a circle
                    // that never morphs. It used to be a raw Material Surface, which is a different
                    // button that happened to look round: it had none of the press feedback, the frost
                    // or the haptic every other button has.
                    MorphButton(
                        onClick = a.onClick,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = a.label },
                        enabled = !a.pending,
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                        activeContainerColor = scheme.primary,
                        activeContentColor = scheme.onPrimary,
                        contentPadding = PaddingValues(0.dp),
                        minHeight = 0.dp,
                        expressive = false,
                        pillCornerPercent = 50f,
                        morphedCornerPercent = 50f,
                        onClickHaptic = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        },
                        content = {
                            glyph?.let {
                                // The shared label draws its glyph from LocalContentColor, so it has to
                                // be told which tone this button is in.
                                CompositionLocalProvider(LocalContentColor provides tint) {
                                    MorphButtonLabel(
                                        icon = it,
                                        // Empty: glyph only, no gap reserved, centred.
                                        label = "",
                                        pending = a.pending,
                                        iconSize = 24.dp,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
