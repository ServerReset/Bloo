@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A pill track + circular thumb, spring-timed like [MorphButton] instead of
 * the stock Material [Switch]. Purely visual -- [ToggleRow]'s own toggleable()
 * modifier owns the real click target and semantics, so this clears its own.
 */
@Composable
internal fun MorphToggleTrack(checked: Boolean) {
    val trackColor by androidx.compose.animation.animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else buttonContainer(),
        animationSpec = lowPowerAwareSpring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "toggleTrackBg",
    )
    val thumbColor by androidx.compose.animation.animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = lowPowerAwareSpring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "toggleThumbFg",
    )
    val trackWidth = 44.dp
    val trackHeight = 26.dp
    val inset = 3.dp
    val thumbSize by animateDpAsState(
        if (checked) 20.dp else 16.dp,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
        label = "toggleThumbSize",
    )
    val thumbOffset by animateDpAsState(
        if (checked) trackWidth - thumbSize - inset else inset,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
        label = "toggleThumbOffset",
    )
    // Un-clipped wrapper so the sparkles that fly out when it switches ON can leave the pill.
    Box(Modifier.sparkleBurst(checked)) {
    Box(
        Modifier
            .size(trackWidth, trackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)), RoundedCornerShape(50))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
    }
}


/** One seat's heat + cool capability toggles, shown as two compact filter chips. */
@Composable
internal fun SeatConfigRow(
    label: String,
    heat: Boolean,
    cool: Boolean,
    onHeat: (Boolean) -> Unit,
    onCool: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            // The two chips are fixed-width; cap the label so a long seat name at a
            // large font size wraps at spaces rather than being crushed mid-word.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(GapRow))
        // A group, so pressing Heat takes width from Cool rather than shoving it -- the pair is
        // exactly the "several buttons in one space" case, and it was a plain Row.
        ExpressiveButtonRow(spacing = GapRow) {
            MorphChip(selected = heat, onClick = { onHeat(!heat) }, label = "Heat")
            MorphChip(selected = cool, onClick = { onCool(!cool) }, label = "Cool")
        }
    }
}
