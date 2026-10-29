package com.bloo.uicommon

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The delete button every removable thing in the app uses: a bin, and a second tap to confirm (the first arms it, and it turns
 * the deny red). A mis-aimed tap removes nothing, and the arm resets itself after a moment. [name] is what it deletes, for the
 * spoken label.
 */
@Composable
fun ArmedDeleteButton(
    name: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    glyphSize: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val confirm = rememberConfirmArm()
    MorphIconButton(
        onClick = { if (confirm.armed) onConfirm() else confirm.arm() },
        description = if (confirm.armed) "Confirm delete $name" else "Delete $name",
        modifier = modifier,
        filled = confirm.armed,
        emphasis = ButtonEmphasis.Deny,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = if (confirm.armed) "Confirm delete $name" else "Delete $name",
            tint = if (confirm.armed) MaterialTheme.colorScheme.error else LocalContentColor.current,
            modifier = Modifier.size(glyphSize),
        )
    }
}
