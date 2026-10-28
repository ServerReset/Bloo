package com.bloo.uicommon

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** An [Icon] at the standard in-button size ([ButtonIconSize]); [description] null marks it decorative. */
@Composable
fun ButtonIcon(icon: ImageVector, description: String? = null, tint: Color = LocalContentColor.current) {
    Icon(icon, contentDescription = description, modifier = Modifier.size(ButtonIconSize), tint = tint)
}
