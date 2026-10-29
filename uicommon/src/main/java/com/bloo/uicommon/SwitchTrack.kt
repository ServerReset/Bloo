package com.bloo.uicommon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp

/**
 * The on/off switch both apps draw: a pill track with a thumb that grows and slides when it turns on. Purely visual: the
 * row it sits in owns the click target and the switch semantics, so this clears its own. The colours are the caller's, since
 * the phone's switch sits on glass and the watch's on a row that fills with the accent when it is on.
 */
@Composable
fun SwitchTrack(
    checked: Boolean,
    trackOn: Color,
    trackOff: Color,
    thumbOn: Color,
    thumbOff: Color,
    modifier: Modifier = Modifier,
) {
    val spec = lowPowerCalm<Color>(Spring.StiffnessMediumLow)
    val trackColor by animateColorAsState(if (checked) trackOn else trackOff, animationSpec = spec, label = "toggleTrackBg")
    val thumbColor by animateColorAsState(if (checked) thumbOn else thumbOff, animationSpec = spec, label = "toggleThumbFg")
    val trackWidth = 44.dp
    val trackHeight = 26.dp
    val inset = 3.dp
    val thumbSize by animateDpAsState(
        if (checked) 20.dp else 16.dp,
        animationSpec = lowPowerSoft(Spring.StiffnessMediumLow),
        label = "toggleThumbSize",
    )
    val thumbOffset by animateDpAsState(
        if (checked) trackWidth - thumbSize - inset else inset,
        animationSpec = lowPowerSoft(Spring.StiffnessMediumLow),
        label = "toggleThumbOffset",
    )
    // The thumb's own move, 0 at rest and 1 when it has arrived, for the moving-part blur: the thumb alone is soft while it slides.
    val moveProgress by animateFloatAsState(
        if (checked) 1f else 0f,
        animationSpec = lowPowerSoft<Float>(Spring.StiffnessMediumLow),
        label = "toggleMove",
    )
    Box(
        modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)), CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(start = thumbOffset).size(thumbSize).clip(CircleShape)
                .movingPartBlur { moveProgress }
                .background(thumbColor),
        )
    }
}

/**
 * The TalkBack meaning of an on/off row: a switch, and whether it is on. Used where a row is drawn as a plain
 * clickable (the watch's buttons), so a screen reader says "switch, on" rather than "button".
 */
fun Modifier.switchSemantics(checked: Boolean): Modifier = semantics {
    role = Role.Switch
    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
}
