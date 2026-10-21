package com.bloo.uicommon

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * One setting laid out the way both apps draw it: the label (or whatever the caller puts in its place) takes the row's
 * spare width, and an optional [trailing] control (a switch) sits on the end after a [gap]. The caller owns the look of
 * both pieces; this owns only the arrangement, so the phone and the watch lay a setting out the same way.
 */
@Composable
fun SettingRow(
    modifier: Modifier = Modifier,
    gap: Dp,
    label: @Composable (Modifier) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        label(Modifier.weight(1f))
        if (trailing != null) {
            Spacer(Modifier.width(gap))
            trailing()
        }
    }
}
