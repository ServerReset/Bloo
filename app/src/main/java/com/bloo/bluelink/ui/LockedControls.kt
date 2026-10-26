package com.bloo.bluelink.ui

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.inertButtonFrost
import com.bloo.uicommon.content

/**
 * A group of controls that can be locked out. The glass swallows taps, so nothing underneath can be
 * pressed, but leaves drags alone, so the page still scrolls when a finger starts on it.
 */
@Composable
internal fun LockedControls(
    locked: Boolean,
    message: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier) {
        Column(Modifier.inertButtonFrost(locked), verticalArrangement = Arrangement.spacedBy(GapRow), content = content)
        AnimatedVisibility(
            visible = locked,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
        ) {
            // Swallows taps (nothing underneath can be pressed) but not drags, so the page still
            // scrolls.
            Box(
                Modifier.fillMaxSize()
                    // A tap swallower, not a button: as a clickable it told a screen reader there was something to activate.
                    .pointerInput(Unit) { detectTapGestures { } },
                contentAlignment = Alignment.Center,
            ) {
                FrostMessage(message)
            }
        }
    }
}
