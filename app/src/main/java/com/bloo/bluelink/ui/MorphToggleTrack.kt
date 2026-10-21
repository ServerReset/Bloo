package com.bloo.bluelink.ui

import com.bloo.uicommon.MorphChip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.SwitchTrack
import com.bloo.uicommon.GapRow

/**
 * Purely visual -- [ToggleRow]'s own toggleable() modifier owns the real click target and
 * semantics, so this clears its own.
 */
@Composable
internal fun MorphToggleTrack(checked: Boolean) {
    // The shared switch (:uicommon's SwitchTrack), in the phone's colours.
    SwitchTrack(
        checked = checked,
        trackOn = MaterialTheme.colorScheme.primary,
        trackOff = buttonContainer(),
        thumbOn = MaterialTheme.colorScheme.onPrimary,
        thumbOff = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
        RevealText(
            label,
            Modifier.weight(1f),
            style = SecondaryTextStyle(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(GapRow))
        ExpressiveButtonRow(spacing = GapRow) {
            MorphChip(selected = heat, onClick = { onHeat(!heat) }, label = "Heat")
            MorphChip(selected = cool, onClick = { onCool(!cool) }, label = "Cool")
        }
    }
}
