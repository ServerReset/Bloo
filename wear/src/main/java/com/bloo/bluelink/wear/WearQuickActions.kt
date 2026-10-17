package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.RevealText

/** One round-friendly quick action: a glyph over a short label, filling its share of a row. */
data class WearQuickAction(
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    /** The command is in flight: shows "Sending…" and ignores taps. */
    val pending: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The car's everyday actions as a grid of big tap targets, two to a row so each stays thumb-sized
 * on a small round face, all the shared [WearButton] so they press like every other Bloo button.
 *
 * Two shapes, because the two places it is used want different things: labelled pills where there is
 * room, and icon-only circles inside the full-screen hero ring (see [compact]).
 */
@Composable
fun WearQuickActions(
    actions: List<WearQuickAction>,
    modifier: Modifier = Modifier,
    /**
     * Icon-only circles rather than labelled pills, for use INSIDE the full-screen hero ring.
     *
     * The hero's ring fills the display's bounding square, and a round display only fills the circle
     * inside it. A labelled 2x2 grid sits in that square's lower half, so its outer corners land
     * outside the bezel; the same grid as 48dp circles is 104dp across, which stays within the circle
     * with the value above it. The label is not lost -- it becomes the button's spoken description.
     */
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    if (compact) {
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
                        Surface(
                            onClick = a.onClick,
                            enabled = !a.pending,
                            shape = CircleShape,
                            color = if (a.active) scheme.primary else scheme.secondaryContainer,
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = a.label },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val tint = if (a.active) scheme.onPrimary else scheme.onSecondaryContainer
                                Icon(
                                    a.icon,
                                    contentDescription = null,
                                    tint = tint.copy(alpha = if (a.pending) 0.4f else 1f),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    WearButton(
                        active = a.active,
                        enabled = !a.pending,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        onClick = a.onClick,
                    ) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            val tint = if (a.active) scheme.onPrimary else scheme.onSecondaryContainer
                            Icon(a.icon, contentDescription = null, tint = tint.copy(alpha = if (a.pending) 0.5f else 1f), modifier = Modifier.size(22.dp))
                            RevealText(
                                if (a.pending) "Sending…" else a.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // An odd last action keeps its half width rather than stretching across the row.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
