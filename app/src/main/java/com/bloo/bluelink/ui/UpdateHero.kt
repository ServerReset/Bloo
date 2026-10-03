package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The version jump at a glance, shared by the update pebble and the Settings update card: the build
 * you have, an arrow, and the build waiting, the new one in the accent colour and larger. Replaces a
 * line of "Build 2440 → Build 2441" text.
 */
@Composable
internal fun UpdateDeltaHero(currentBuild: Int, newBuild: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(GapRow),
    ) {
        if (currentBuild > 0) {
            RollingNumber(
                "$currentBuild",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Icon(AppIcons.ArrowForward, contentDescription = "to", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(bottom = 2.dp))
        }
        RollingNumber(
            "$newBuild",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
        Text(
            "build",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/**
 * Download progress as a glossy bar that fills with a spring, shown only while a download runs.
 * [progress] null means "started, no number yet": the bar idles at a sliver rather than vanishing.
 */
@Composable
internal fun UpdateDownloadBar(visible: Boolean, progress: Float?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        val scheme = MaterialTheme.colorScheme
        val fill by animateFloatAsState(
            targetValue = (progress ?: 0.04f).coerceIn(0.04f, 1f),
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
            label = "updateDownloadFill",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(scheme.onSurface.copy(alpha = HairlineAlpha))
                .drawBehind {
                    val w = size.width * fill
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(scheme.primary.copy(alpha = 0.75f), scheme.primary)),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                    // The sheen riding the top of the fill.
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), endY = size.height * 0.6f),
                        topLeft = Offset(0f, 0f),
                        size = Size(w, size.height * 0.6f),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                },
        )
    }
}
