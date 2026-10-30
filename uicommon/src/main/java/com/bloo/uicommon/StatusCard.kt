package com.bloo.uicommon

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A status line as a card: a tonal icon badge, a bold title, and an optional muted line under it saying what the status means. [panel]
 * draws the card's surface around the content (the app passes its glass panel), so the card itself stays free of the app's styling.
 */
@Composable
fun StatusCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String?,
    panel: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    panel {
        Row(
            Modifier.padding(GapGroup),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GapRow),
        ) {
            IconBadgeContainer(containerColor = tint.copy(alpha = 0.15f), size = 36.dp) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GapHairline)) {
                RevealText(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = LocalContentColor.current),
                )
                if (subtitle != null) {
                    RevealText(subtitle, style = SecondaryTextStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
