package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.bloo.uicommon.ExpressiveButtonGroup
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.content

/**
 * The standard full-width row of action buttons under a card's or group's content: each button is its own fully
 * round pill, split from its neighbours by [GapRow]; they share a line in proportion to their labels and wrap
 * to the next when they do not fit. (Connected clusters are for pebble header controls: see [ButtonCluster].)
 */
@Composable
fun ActionRow(modifier: Modifier = Modifier, spacing: Dp = GapRow, content: @Composable () -> Unit) =
    ExpressiveButtonGroup(modifier = modifier.fillMaxWidth(), spacing = spacing) {
        content()
    }
