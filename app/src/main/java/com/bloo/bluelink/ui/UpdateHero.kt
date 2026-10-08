package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The version jump at a glance, shared by the update pebble and the Settings update card: the build
 * you have, an arrow, and the build waiting, the new one in the accent colour and larger. The
 * header summary carries the "build N" wording, so this row stays just the two numbers and the
 * arrow.
 */
@Composable
internal fun UpdateDeltaHero(currentBuild: Int, newBuild: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        if (currentBuild > 0) {
            RollingNumber(
                "$currentBuild",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal,
                color = scheme.onSurface,
            )
            Icon(
                AppIcons.ArrowForward,
                contentDescription = "to",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        RollingNumber(
            "$newBuild",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
    }
}
