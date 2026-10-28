package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SeatLevel
import com.bloo.uicommon.BlooSlider
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.SecondaryTextStyle
import kotlin.math.roundToInt

/**
 * The seat levels on one scale a slider can hold: negative is cooling, zero is off, positive is heating. Heat and cool therefore share one
 * gesture -- drag left to cool, right to warm -- instead of a mode switch and a strength each.
 */
internal object SeatScale {
    /** The strongest step either way: Low, Med, High. */
    const val MAX = 3

    private val coolSteps = listOf(SeatLevel.LOW_COOL, SeatLevel.MED_COOL, SeatLevel.HIGH_COOL)
    private val heatSteps = listOf(SeatLevel.LOW_HEAT, SeatLevel.MED_HEAT, SeatLevel.HIGH_HEAT)

    /** The level a step of the scale stands for. Steps outside -[MAX]..[MAX] are clamped to the strongest. */
    fun levelOf(step: Int): SeatLevel = when {
        step > 0 -> heatSteps[(step - 1).coerceAtMost(MAX - 1)]
        step < 0 -> coolSteps[(-step - 1).coerceAtMost(MAX - 1)]
        else -> SeatLevel.OFF
    }

    /** The step a level sits at on the scale. Anything that is not a heat or cool level is off. */
    fun stepOf(level: SeatLevel): Int = when {
        level in heatSteps -> heatSteps.indexOf(level) + 1
        level in coolSteps -> -(coolSteps.indexOf(level) + 1)
        else -> 0
    }
}

/**
 * One seat in the quadrant grid. [canHeat] and [canCool] come from the car's own seat configuration, so a seat that can only heat (or
 * only cool) gets a slider that only goes one way.
 */
internal class SeatQuadrant(
    val label: String,
    val level: SeatLevel,
    val canHeat: Boolean,
    val canCool: Boolean,
    val onChange: (SeatLevel) -> Unit,
)

/**
 * The car's seats as quadrants: the front row over the rear row, each seat its own card with a mini slider. A row with no seats in it is
 * not drawn, and a single seat takes the full row, so a two-seat car, a front-only car and a heat-only car all lay out the same way.
 */
@Composable
internal fun SeatQuadrants(front: List<SeatQuadrant>, rear: List<SeatQuadrant>) {
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        // Says how to use the grid before anyone has to guess: the slider is the whole control.
        RevealText(
            "Drag a seat's slider left to cool it, right to warm it. Off sits in the middle.",
            style = SecondaryTextStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(front, rear).filter { it.isNotEmpty() }.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                row.forEach { seat -> SeatQuadrantCard(seat, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SeatQuadrantCard(seat: SeatQuadrant, modifier: Modifier) {
    val low = if (seat.canCool) -SeatScale.MAX else 0
    val high = if (seat.canHeat) SeatScale.MAX else 0
    val step = SeatScale.stepOf(seat.level).coerceIn(low, high)
    val readout = if (seat.level == SeatLevel.OFF) "Off" else seat.level.label
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(GapGroup),
    ) {
        RevealText(seat.label, style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold, maxLines = 1)
        RevealText(readout, style = SecondaryTextStyle(), fontWeight = FontWeight.Bold, color = seatTint(seat.level), maxLines = 1)
        Spacer(Modifier.height(GapHairline))
        BlooSlider(
            value = step.toFloat(),
            valueRange = low.toFloat()..high.toFloat(),
            // One stop per whole step between the two ends, as the climate sliders do.
            steps = high - low - 1,
            readout = null,
            onValueChange = { seat.onChange(SeatScale.levelOf(it.roundToInt())) },
        )
        // Which end of the slider is which, under it: a one-way seat shows only its own end.
        Row(Modifier.fillMaxWidth()) {
            if (seat.canCool) RevealText("Cool", style = SecondaryTextStyle(), color = seatTint(SeatLevel.HIGH_COOL), maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (seat.canHeat) RevealText("Heat", style = SecondaryTextStyle(), color = seatTint(SeatLevel.HIGH_HEAT), maxLines = 1)
        }
    }
}
