package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * A group of controls that can be locked out. While [locked] the controls dim and a frosted glass
 * layer sits over them, blurring what is underneath, with [message] on top -- so it reads as "this
 * is a live setting you can't change right now" rather than as controls that are merely broken.
 *
 * The glass swallows taps, so nothing underneath can be pressed, but leaves drags alone, so the page
 * still scrolls when a finger starts on it.
 */
@Composable
internal fun LockedControls(
    locked: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier) {
        Column(
            Modifier.frosted(locked),
            verticalArrangement = Arrangement.spacedBy(GapRow),
            content = content,
        )
        AnimatedVisibility(
            visible = locked,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
        ) {
            // Swallows taps (nothing underneath can be pressed) but not drags, so the page still scrolls.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                contentAlignment = Alignment.Center,
            ) {
                FrostMessage(message)
            }
        }
    }
}
