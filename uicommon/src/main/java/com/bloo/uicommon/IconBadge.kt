package com.bloo.uicommon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The circular tinted container an [IconBadge] draws into. */
@Composable
fun IconBadgeContainer(
    modifier: Modifier = Modifier,
    containerColor: Color,
    size: Dp = 40.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(containerColor),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * A single glyph in a tinted circle: the one badge for the phone's rows and cards and the watch's full-screen states.
 * [containerColor] defaults to a 14%-alpha tint of [tint]; pass a *Container role for the solid tonal look.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = tint.copy(alpha = 0.14f),
    size: Dp = 40.dp,
    iconSize: Dp = size * 0.5f,
) {
    IconBadgeContainer(modifier = modifier, containerColor = containerColor, size = size) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}
