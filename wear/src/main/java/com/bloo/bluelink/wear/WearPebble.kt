package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.MorphButtonCore

/**
 * One watch "pebble": a titled card with a body, mirroring the phone's pebble
 * vocabulary at watch scale. Kept local to :wear for the moment -- the phone's
 * PebbleShell lives in :app and pulls in the whole app theme, so a watch copy that
 * matches the phone's on-screen rhythm (rounded card, title row with a leading glyph,
 * body below) is the honest first step rather than coupling the two.
 */
@Composable
fun WearPebble(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            }
            body()
        }
    }
}

/**
 * A single full-width action inside a [WearPebble] -- the shared phone
 * [MorphButtonCore] from :uicommon, so the watch's one tap target morphs and presses
 * exactly like every other Bloo button instead of a watch-only look. The foundation
 * core takes its colours as parameters (it cannot reach Material's LocalContentColor),
 * so the watch theme supplies them here.
 */
@Composable
fun WearActionRow(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    MorphButtonCore(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        activeContainerColor = MaterialTheme.colorScheme.primary,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
    }
}
