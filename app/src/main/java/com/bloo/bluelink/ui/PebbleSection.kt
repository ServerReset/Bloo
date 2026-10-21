package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.SectionHeading

/**
 * One headed group inside a pebble (Car info, Diagnostics): a heading with its glyph and, optionally, a one-word verdict at the
 * right, then the group's rows on a faint inset panel so groups read as groups and not as one long list. With [panel] false
 * the content (a row of buttons) sits directly under the heading.
 */
@Composable
internal fun PebbleSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    panel: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapRow)) {
        SectionHeading(title, icon, trailing = trailing, trailingColor = trailingColor)
        if (panel) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(LocalContentColor.current.copy(alpha = 0.06f))
                    .padding(horizontal = GapGroup, vertical = GapGroup),
                verticalArrangement = Arrangement.spacedBy(GapGroup),
                content = content,
            )
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapRow), content = content)
        }
    }
}

/** Four wheels as a two-by-two grid (front pair above rear pair), each a label over its state, a warning in the error colour. */
@Composable
internal fun WheelGrid(wheels: List<DiagRow>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapGroup)) {
        wheels.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                pair.forEach { wheel ->
                    Column(Modifier.weight(1f)) {
                        RevealText(
                            wheel.label,
                            style = SecondaryTextStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        RevealText(
                            wheel.value,
                            style = PrimaryTextStyle(),
                            fontWeight = FontWeight.SemiBold,
                            color = if (wheel.value == "OK") MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            maxLines = 1,
                        )
                    }
                }
                // A lone wheel keeps its half of the row.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
