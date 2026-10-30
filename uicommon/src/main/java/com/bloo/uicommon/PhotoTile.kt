package com.bloo.uicommon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * A small rounded tile holding a picture, or [glyph] when there is none. The picture is drawn by [photo], which fills the tile, so this
 * stays free of any image loader: the app passes its own. [selected] outlines the tile in [tint]. Used for car markers on the map and
 * anywhere else a thing is shown by its photo.
 */
@Composable
fun PhotoTile(
    hasPhoto: Boolean,
    glyph: ImageVector,
    tint: Color,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: DpSize = DpSize(44.dp, 32.dp),
    selected: Boolean = false,
    glyphSize: Dp = 18.dp,
    photo: @Composable (Modifier) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(if (selected) 2.dp else 1.dp, if (selected) tint else Color.White.copy(alpha = 0.85f), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (hasPhoto) {
            photo(Modifier.size(size))
        } else {
            Icon(glyph, contentDescription = null, tint = tint, modifier = Modifier.size(glyphSize))
        }
    }
}
